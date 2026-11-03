# 07 下载 · 本地漫画 · 备份

> 三件事共享一个前提：**文件是用户的，不是我们的**。所以路径可配置、格式通用、删除可控、备份可读。

## 1. 存储布局

### 1.1 默认目录（应用私有）

```
/Android/data/io.github.saonte.mangareader/files/Manga/
├── .nomedia                      # 防止被系统媒体库扫描
├── downloaded/
│   └── <sourceKey>/              # 规则 id 的短哈希，避免路径过长
│       └── <comicTitle>__<cidHash>/
│           ├── 0001-第1话/
│           │   ├── 001.jpg
│           │   ├── 002.webp
│           │   └── ...
│           └── 0002-第2话/
└── local/                        # 可选：用户导入到私有目录的本地漫画
```

### 1.2 自定义目录（SAF）

用户可选择任意目录（SD 卡、网盘同步目录）。通过 `ACTION_OPEN_DOCUMENT_TREE` 获取持久化 `Uri`：

```
<用户目录>/MangaReader/
└── <comicTitle>__<cidHash>/
    └── 0001-第1话/…
```

约束：
- 目录选择后写入 `storage.downloadRoot`，并 `takePersistableUriPermission`。
- 权限丢失（卸载重装、用户移动目录）→ 任务暂停，书架项显示「需要重新授权」。
- SAF 下不做 `rename` 原子操作（部分 provider 不支持），改用「写入 `.part` → 复制/移动 → 删 `.part`」，失败可重试。

### 1.3 命名规则

| 对象 | 规则 | 示例 |
| --- | --- | --- |
| 漫画目录 | `sanitize(title)` + `__` + `cid` 的 CRC32 后 8 位十六进制 | `进击的巨人__a1b2c3d4` |
| 章节目录 | 4 位序号 + `-` + `sanitize(chapter.title)` | `0012-第12话 突袭` |
| 图片 | 3 位序号 + 原扩展名（小写） | `003.png` |
| 临时文件 | 同名 + `.part` | `003.png.part` |
| 下载清单（可选） | `chapter.json`（标题、页数、时间、源） | — |

`sanitize`：替换 `\/:*?"<>|` 与控制字符为 `_`；去首尾点/空格；保留中文；长度截断到 60 字符（按码点，不切断代理对）；空标题 → `untitled`。

## 2. 下载系统

### 2.1 状态机

```
 QUEUED ──▶ RUNNING ──▶ COMPLETED
    ▲           │  ├──▶ FAILED ──(重试)──▶ QUEUED
    │           │  └──▶ PAUSED ──(继续)──▶ QUEUED
    └───────────┴── CANCELLED（清理 .part）
```

- 唯一约束：`chapter_id` 唯一（`download_task` 索引），同一章不会被重复入队。
- WorkManager 名称：`dl:<chapterId>`，`ExistingWorkPolicy.KEEP`；取消用 `cancelUniqueWork`。
- 队列默认并发 2（可配 1–5），按入队顺序 FIFO；同一漫画的章节优先连续。

### 2.2 执行流程

```kotlin
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
    private val engine: SourceEngine,
    private val dao: DownloadDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val task = dao.taskById(inputData.getLong(KEY_TASK_ID))
        val pages = engine.images(task.sourceRef(), task.cid, task.chapterPath).images
        var done = task.done
        for (i in done until pages.size) {
            writeWithRetry(pages[i], target(i))      // Range 续传 + .part → rename
            dao.updateProgress(task.id, ++done, pages.size)
            setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to pages.size))
            if (isStopped) return@withContext Result.retry()   // 取消/暂停
        }
        dao.markDownloaded(task.chapterId)
        Result.success()
    }
}
```

> 依赖 `androidx.work:work-runtime-ktx`；进度写入做 200ms 节流，避免高频事务。

细节：
- **断点续传**：若目标文件已存在且大小 > 0，先 `HEAD`/`Range` 校验（站点不支持 Range 则整页重下）；`.part` 文件存在则从已有字节继续。
- **原子性**：写 `.part` → `fsync`（可选）→ rename；SAF 用 `DocumentsContract.renameDocument`，失败则复制后删源。
- **失败重试**：单页 3 次（1s/4s/10s 退避）；整章失败保留已完成页，任务标记 `FAILED` 并记录 `error_code`；用户点重试从 `done` 继续。
- **通知**：前台服务 `DownloadForegroundService`（type `dataSync`），显示总进度与暂停/取消按钮；Android 14 用 `FOREGROUND_SERVICE_DATA_SYNC` 权限；任务 < 10s 不启动前台服务。
- **约束**：默认 `NetworkType.CONNECTED`；可选「仅 Wi-Fi」「充电时」。
- **存储检查**：入队前估算 `pages * 平均 300KB`，不足则拒绝并提示释放空间。
- **完整性**：完成后校验页数 = 实际文件数；不一致标记 `partial`。

### 2.3 暂停 / 取消 / 删除

| 操作 | 行为 |
| --- | --- |
| 暂停 | `cancelUniqueWork` + 状态 `PAUSED`，保留已完成文件与 `.part` |
| 继续 | 重新入队，从已完成页继续 |
| 取消 | 停任务、删 `.part`、按选择删已下载文件、状态 `CANCELLED` |
| 删除章节下载 | 删目录 + 重置 `chapter.downloaded`；若正在下载先取消 |
| 删除整本 | 取消全部任务 + 删漫画目录 + `comic.downloaded_at = null` |
| 清理孤立文件 | 启动后扫描：目录存在但无对应记录 → 移到 `.trash/` 并提示恢复或删除 |

### 2.4 离线阅读路径

阅读器取图顺序：

1. 已下载目录（按 `chapter.path` 查 `download_task.target_dir`）；
2. 本地漫画（`comic.local = 1`）；
3. 在线 `PageSource`（需网络）。

已下载章节的 `ImageRef` 直接指向 `file://`（Coil 用 `FileFetcher`），不注入网络 Header。

## 3. 本地漫画

### 3.1 支持的形态

| 形态 | 结构 | v1 | 说明 |
| --- | --- | --- | --- |
| 图片目录 | 每个子目录 = 一章；直接含图片的目录 = 单章 | ✓ | 封面取首图 |
| ZIP / CBZ | 条目路径的相对目录层级 = 章节 | ✓ | 支持嵌套两层 |
| RAR / CBR | 同 ZIP | P2 | 需 unrar 兼容库，许可证待评估 |
| EPUB | 固定版式 | ✗ | 不属于漫画目标场景 |
| PDF | — | ✗ | 另立阅读内核，不做 |

图片扩展名：`jpg jpeg png webp gif bmp avif heic`（按系统解码能力，解码失败降级占位）。忽略 `*.xml`、`*.txt`、`*.nfo`、隐藏文件、`__MACOSX`。

### 3.2 扫描流程

```
用户选择根目录（SAF tree）
  → 遍历（深度 ≤ 3，条目 ≤ 5000，超时 60s）
  → 识别「章节目录 / 压缩包」
  → 生成 LocalComic（source=0, cid=相对路径的 SHA-1）
  → upsert 到 comic/chapter（不覆盖已有进度）
  → 后台异步补封面缩略图
```

- **增量**：记录根目录 `last_scan` 时间戳与文件 `lastModified`；未变化的跳过。
- **ZIP 索引**：SAF 下无法直接 `new ZipFile(path)`（需要可 seek 的 fd）。v1 策略：
  - 私有目录：`ZipFile` 直接读 central directory；
  - SAF：`ParcelFileDescriptor` + `FileInputStream`，一次性顺序扫描建立「条目名 → 压缩后偏移」索引存入内存/`app_meta`（大文件提示「建议解压」）；
  - 单章阅读：按索引顺序流式跳到目标条目（O(n)，n 为条目数；实测 2000 条目 < 200ms）。
- **编码**：ZIP 条目名可能是 GBK（Windows 压缩）；优先 UTF-8，失败用 `GBK` 兜底（`ZipInputStream` 需自定义解码或使用 `zip4j` 的编码参数，M4 评估）。
- **封面**：取第一章首图，缩放到 512px 存私有缓存，避免每次解码大图。

### 3.3 本地漫画的章节模型

- 目录形态：每个子目录一项 `Chapter`，`path = 相对路径`，`count = 图片数`。
- 压缩包形态：包内每个含图片的目录一项；根目录图片合并为一章 `第1话`。
- 排序：按目录/条目名自然排序（数字感知，`第2话` < `第10话`）。
- 进度：与在线章节共用 `chapter.last_page`，`comic.last_chapter_path` 存相对路径。

### 3.4 导出 CBZ（P2）

- 仅对「图片目录」形态的章节可用：打包为 `<章节标题>.cbz`，条目名 `001.jpg`。
- 可选生成 `ComicInfo.xml`（title/series/number/writer/summary/PageCount）。
- 导出目标：SAF 目录；同名冲突加 ` (2)`。
- 不导出已下载的在线章节（避免形成内容分发能力）。

## 4. 备份与恢复

### 4.1 本地备份

- 触发：手动 + 可选每日（`WorkManager` 周期任务，`KEEP`）。
- 打包：流式写 ZIP，内容见 [04 §5](04-data-model.md#5-备份文件格式mangareaderzip)；**不包含**图片、Cookie、WebDAV 密码、图片缓存。
- 位置：`ACTION_CREATE_DOCUMENT` 让用户选择（默认文件名 `mangareader-backup-<yyyyMMdd-HHmm>.zip`）。
- 保留策略：若保存到私有目录，保留最近 5 份并按天去重。
- 校验：写完后回读 ZIP 并校验 `manifest.checksum`；失败删除半成品。

### 4.2 恢复

```
选择文件 → 解析 manifest → 版本检查 → 预览（源/漫画/标签数量）
        → 选择模式（合并 / 替换 / 仅规则）→ 事务写入 → 重建可见状态
```

- **事务**：Room `runInTransaction`；规则先写（保证 `comic.source` 外键可解析），再写漫画/章节，最后标签与下载记录。
- **冲突**：合并模式下，`(ruleId, cid)` 命中时：`favoriteAt` 取较新；进度按 `lastReadAt` 取较新；标签并集；章节保留本地已读状态。
- **失败回滚**：任一步失败回滚事务，已解压的临时目录清理；提示具体条目（`JSON Pointer` 或 `cid`）。
- **替换模式**：先完整备份到临时区（以防误操作），成功后删除旧数据，失败可回滚。

### 4.3 WebDAV

| 操作 | 方法/路径 | 说明 |
| --- | --- | --- |
| 测试连接 | `PROPFIND /` Depth:0 | 校验 207/200 |
| 建目录 | `MKCOL /MangaReader/` | 已存在（405）视为成功 |
| 上传 | `PUT /MangaReader/backup-<ts>.zip` | 先传 `.tmp` 再 `MOVE`（若服务器支持），否则直接 PUT |
| 列表 | `PROPFIND /MangaReader/` Depth:1 | 解析 `getlastmodified`/`getcontentlength` |
| 下载 | `GET` | 流式写临时文件后校验 |
| 删除 | `DELETE` | 仅删除超过保留策略的备份 |

- 认证：Basic Auth（用户名 + 应用密码）；凭据用 Keystore AES-GCM 保存，备份文件**不含**凭据。
- 超时 30s，重试 2 次；失败不影响前台，通知栏提示可重试。
- 兼容性：Nextcloud、坚果云、群晖 WebDAV 为目标；对 `MOVE` 不支持者降级。
- 大小限制：备份通常 < 5MB；> 50MB 警告（异常数据）。

### 4.4 自动备份策略

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| 每日自动 | 关 | 仅在 Wi-Fi + 充电时 |
| 保留份数 | 5 | 按时间倒序保留 |
| 仅 Wi-Fi | 是 | 避免流量 |
| 包含规则 | 是 | 多设备同步源配置 |
| 包含设置 | 是 | 不含凭据 |

## 5. 删除与隐私

- 「清除全部数据」：清空数据库 + 下载目录 + 缓存 + 备份记录 + 规则；保留应用本身。二次确认要求输入「清除」。
- 卸载应用：私有目录随卸载删除；SAF 目录中的文件**不删**（属于用户），但在设置页说明。
- 诊断包（P1）：导出 ZIP 含日志（脱敏）、设备信息、规则列表哈希、数据库 schema 版本；不含 URL query、Cookie、漫画内容。
- 备份文件默认不加密（可读、可迁移）；若勾选「包含凭据」，强制要求设置导出密码（AES-GCM + PBKDF2）。

## 6. 测试要点

| 场景 | 断言 |
| --- | --- |
| 下载中断（飞行模式） | 恢复后从断点继续，页数最终一致 |
| 存储空间不足 | 入队前拒绝；下载中失败标记明确 |
| SAF 权限被撤销 | 任务暂停，UI 显示重新授权入口 |
| 目录含非法字符标题 | 文件名合法、可读、不冲突 |
| ZIP 空目录/嵌套/中文 GBK | 章节识别正确，乱码有兜底 |
| 备份 → 卸载重装 → 恢复 | 收藏/进度/标签/规则一致 |
| 旧版本备份在新版本恢复 | 迁移成功或明确拒绝 |
| WebDAV 401/超时 | 提示明确，不阻塞主流程 |
| 删除漫画时仍有下载任务 | 任务先取消，无残留目录 |
