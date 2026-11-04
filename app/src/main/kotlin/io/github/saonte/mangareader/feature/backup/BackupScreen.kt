package io.github.saonte.mangareader.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saonte.mangareader.data.backup.ImportMode
import io.github.saonte.mangareader.ui.components.ScreenHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val suggestedName = "mangareader-backup-" +
        SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date()) + ".zip"

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::import) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Text("返回") }
            ScreenHeader(title = "备份与恢复", modifier = Modifier.weight(1f))
        }

        Text(
            text = "备份包含：规则包、收藏、阅读进度、章节已读状态、标签与主题设置。\n" +
                "不包含：漫画图片、下载文件、Cookie 与 WebDAV 密码。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(modifier = Modifier.padding(top = 8.dp))
        Button(
            enabled = !state.busy,
            onClick = { exportLauncher.launch(suggestedName) },
            modifier = Modifier.padding(horizontal = 24.dp),
        ) { Text("导出备份") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text(
            text = "导入方式",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        Column(modifier = Modifier.selectableGroup()) {
            ModeRow("合并（推荐）", "按规则 id + cid 合并，进度取较新", ImportMode.MERGE, state.mode, viewModel::setMode)
            ModeRow("替换", "清空收藏与标签后导入（规则保留并合并）", ImportMode.REPLACE, state.mode, viewModel::setMode)
            ModeRow("仅规则", "只恢复规则包，不动书架与进度", ImportMode.RULES_ONLY, state.mode, viewModel::setMode)
        }

        Button(
            enabled = !state.busy,
            onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        ) { Text("导入备份") }

        if (state.busy) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text("处理中…", style = MaterialTheme.typography.bodyMedium)
            }
        }

        state.message?.let { message ->
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.lastSuccess == false) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                TextButton(onClick = viewModel::dismissMessage) { Text("知道了") }
            }
        }
    }
}

@Composable
private fun ModeRow(
    title: String,
    description: String,
    mode: ImportMode,
    selected: ImportMode,
    onSelect: (ImportMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = mode == selected, onClick = { onSelect(mode) }, role = Role.RadioButton)
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = mode == selected, onClick = null)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
