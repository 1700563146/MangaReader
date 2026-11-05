package io.github.saonte.mangareader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saonte.mangareader.BuildConfig
import io.github.saonte.mangareader.designsystem.component.EmptyState
import io.github.saonte.mangareader.source.SourceKind
import io.github.saonte.mangareader.source.index.LibraryEntry
import io.github.saonte.mangareader.ui.components.ScreenHeader

/**
 * 第三方源库：用户自填索引地址，列表来自索引，不预置任何站点。
 */
@Composable
fun SourceLibraryScreen(
    onBack: () -> Unit,
    viewModel: SourceLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Text("返回") }
            ScreenHeader(title = "源库", modifier = Modifier.weight(1f))
        }

        Text(
            text = "填写一个第三方源库索引地址（JSON）。应用不内置任何索引或站点，" +
                "安装前请自行确认来源与合法性。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.indexUrl,
                onValueChange = viewModel::onIndexUrlChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("https://…/index.json") },
            )
            Button(enabled = !state.loading, onClick = viewModel::load) {
                Text(if (state.loading) "拉取中" else "拉取")
            }
        }

        if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.CenterHorizontally),
            )
        }

        state.message?.let { message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::dismissMessage) { Text("知道了") }
            }
        }

        state.warnings.take(3).forEach { warning ->
            Text(
                text = "· $warning",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 1.dp),
            )
        }

        if (BuildConfig.DEBUG) {
            // 仅 Debug：模拟器输入法会把 http 自动补全成 https，且无法访问外网 CDN，
            // 这里提供本地索引快捷入口用于端到端验证。Release 不含此分支。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { viewModel.useLocalIndex() }) { Text("本地索引（Debug）") }
            }
        }

        if (state.entries.isEmpty() && !state.loading) {
            EmptyState(
                title = "还没有加载源库",
                body = "填入索引地址后点「拉取」。索引格式支持 venera 风格的 JS 源库，" +
                    "以及带签名的 JSON 规则索引。",
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.entries, key = { it.id }) { entry ->
                    LibraryRow(
                        entry = entry,
                        installing = state.installingId == entry.id,
                        installed = entry.id in state.installedIds,
                        onInstall = { viewModel.install(entry) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(
    entry: LibraryEntry,
    installing: Boolean,
    installed: Boolean,
    onInstall: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(entry.id)
                    append(" · v")
                    append(entry.version)
                    if (entry.kind == SourceKind.CJS) {
                        append(" · CJS")
                    } else if (entry.sha256 != null) {
                        append(" · 已签名")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.padding(4.dp))
        when {
            installing -> CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            installed -> Text("已安装", style = MaterialTheme.typography.labelMedium)
            else -> TextButton(onClick = onInstall) { Text("安装") }
        }
    }
}
