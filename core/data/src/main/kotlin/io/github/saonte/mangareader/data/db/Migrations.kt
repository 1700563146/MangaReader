package io.github.saonte.mangareader.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2：加入源、漫画、章节、标签、下载、搜索历史、本地扫描根。
 *
 * SQL 必须与 Room 由实体生成的 schema 完全一致（列类型、非空、索引名、外键动作），
 * 否则 Room 会在打开数据库时抛 IllegalStateException。修改实体时必须同步本文件并补迁移测试。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `source` (
                `type` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `rule_id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `version` TEXT NOT NULL,
                `schema_version` INTEGER NOT NULL,
                `engine_min` INTEGER NOT NULL,
                `base_url` TEXT,
                `json` TEXT NOT NULL,
                `sha256` TEXT NOT NULL,
                `enabled` INTEGER NOT NULL,
                `trust` INTEGER NOT NULL,
                `origin_url` TEXT,
                `installed_at` INTEGER NOT NULL,
                `last_used_at` INTEGER,
                `health` INTEGER NOT NULL,
                `fail_streak` INTEGER NOT NULL,
                `last_fail_at` INTEGER,
                `last_error_code` TEXT,
                `sort` INTEGER NOT NULL
            )""".trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_source_rule_id` ON `source` (`rule_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_enabled_sort` ON `source` (`enabled`, `sort`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `comic` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `source` INTEGER NOT NULL,
                `cid` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `cover` TEXT NOT NULL,
                `author` TEXT,
                `intro` TEXT,
                `update_text` TEXT,
                `update_at` INTEGER,
                `finish` INTEGER,
                `highlight` INTEGER NOT NULL,
                `local` INTEGER NOT NULL,
                `favorite_at` INTEGER,
                `history_at` INTEGER,
                `downloaded_at` INTEGER,
                `last_chapter_path` TEXT,
                `last_chapter_title` TEXT,
                `last_chapter_index` INTEGER NOT NULL,
                `last_page` INTEGER NOT NULL,
                `chapter_count` INTEGER NOT NULL,
                `url` TEXT
            )""".trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_comic_source_cid` ON `comic` (`source`, `cid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_comic_favorite_at` ON `comic` (`favorite_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_comic_history_at` ON `comic` (`history_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_comic_source` ON `comic` (`source`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_comic_highlight` ON `comic` (`highlight`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `chapter` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `comic_id` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `path` TEXT NOT NULL,
                `source_group` TEXT NOT NULL,
                `count` INTEGER NOT NULL,
                `complete` INTEGER NOT NULL,
                `downloaded` INTEGER NOT NULL,
                `last_page` INTEGER NOT NULL,
                `read_at` INTEGER,
                `size_bytes` INTEGER NOT NULL,
                FOREIGN KEY(`comic_id`) REFERENCES `comic`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chapter_comic_id_path` ON `chapter` (`comic_id`, `path`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapter_comic_id_id` ON `chapter` (`comic_id`, `id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapter_comic_id_downloaded` ON `chapter` (`comic_id`, `downloaded`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `tag` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `sort` INTEGER NOT NULL
            )""".trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tag_name` ON `tag` (`name`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `tag_ref` (
                `comic_id` INTEGER NOT NULL,
                `tag_id` INTEGER NOT NULL,
                PRIMARY KEY(`comic_id`, `tag_id`),
                FOREIGN KEY(`comic_id`) REFERENCES `comic`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`tag_id`) REFERENCES `tag`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tag_ref_tag_id` ON `tag_ref` (`tag_id`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `download_task` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `comic_id` INTEGER NOT NULL,
                `chapter_id` INTEGER NOT NULL,
                `source` INTEGER NOT NULL,
                `cid` TEXT NOT NULL,
                `chapter_path` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `state` INTEGER NOT NULL,
                `total` INTEGER NOT NULL,
                `done` INTEGER NOT NULL,
                `failed` INTEGER NOT NULL,
                `target_dir` TEXT NOT NULL,
                `error_code` TEXT,
                `error_msg` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                FOREIGN KEY(`comic_id`) REFERENCES `comic`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`chapter_id`) REFERENCES `chapter`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_download_task_chapter_id` ON `download_task` (`chapter_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_task_state_created_at` ON `download_task` (`state`, `created_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_task_comic_id` ON `download_task` (`comic_id`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `search_history` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `keyword` TEXT NOT NULL,
                `source` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL
            )""".trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_search_history_keyword_source` ON `search_history` (`keyword`, `source`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_search_history_created_at` ON `search_history` (`created_at`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `local_root` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uri` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                `enabled` INTEGER NOT NULL,
                `last_scan` INTEGER,
                `last_count` INTEGER NOT NULL
            )""".trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_local_root_uri` ON `local_root` (`uri`)")
    }
}
