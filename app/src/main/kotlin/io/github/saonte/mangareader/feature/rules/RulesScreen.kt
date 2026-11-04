package io.github.saonte.mangareader.feature.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saonte.mangareader.R
import io.github.saonte.mangareader.data.repo.Source
import io.github.saonte.mangareader.designsystem.component.EmptyState
import io.github.saonte.mangareader.model.SourceHealth
import io.github.saonte.mangareader.source.rule.IssueSeverity
import io.github.saonte.mangareader.ui.components.ScreenHeader

@Composable
fun RulesScreen(
    onBack: () -> Unit,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val revoked by viewModel.revoked.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                Text(stringResource(R.string.action_back))
            }
            ScreenHeader(
                title = stringResource(R.string.rules_manage),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::openImport) {
                Text(stringResource(R.string.rules_import))
            }
        }

        state.message?.let { message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::dismissMessage) { Text(stringResource(R.string.action_got_it)) }
            }
        }

        if (state.sources.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.rules_empty_title),
                body = stringResource(R.string.rules_empty_body),
                actionLabel = stringResource(R.string.rules_import),
                onAction = viewModel::openImport,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.sources, key = { it.type }) { source ->
                    SourceRow(
                        source = source,
                        revoked = source.type in revoked,
                        onToggle = { enabled -> viewModel.setEnabled(source, enabled) },
                        onDelete = { viewModel.delete(source) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (state.showImport) {
        ImportDialog(
            importing = state.importing,
            issues = state.issues,
            onDismiss = viewModel::dismissImport,
            onInstall = viewModel::install,
            onPickFile = viewModel::installFromUri,
            onInstallUrl = viewModel::installFromUrl,
        )
    }
}

@Composable
private fun SourceRow(
    source: Source,
    revoked: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = source.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = buildString {
                    append(source.ruleId)
                    append(" · v")
                    append(source.version)
                    if (revoked) {
                        append(stringResource(R.string.rules_revoked_suffix))
                    } else if (source.health != SourceHealth.HEALTHY) {
                        append(" · ")
                        append(if (source.health == SourceHealth.DEGRADED) stringResource(R.string.rules_degraded) else stringResource(R.string.rules_disabled))
                    }
                    if (source.failStreak > 0) {
                        append(stringResource(R.string.rules_fail_streak))
                        append(source.failStreak)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (source.lastErrorCode != null) {
                Text(
                    text = stringResource(R.string.rules_last_error, source.lastErrorCode.orEmpty()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        TextButton(onClick = onDelete) { Text(stringResource(R.string.rules_delete)) }
        Switch(checked = source.enabled && !revoked, onCheckedChange = onToggle, enabled = !revoked)
    }
}

@Composable
private fun ImportDialog(
    importing: Boolean,
    issues: List<io.github.saonte.mangareader.source.rule.RuleIssue>,
    onDismiss: () -> Unit,
    onInstall: (String) -> Unit,
    onPickFile: (android.net.Uri) -> Unit,
    onInstallUrl: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onPickFile)
    }

    AlertDialog(
        onDismissRequest = { if (!importing) onDismiss() },
        title = { Text(stringResource(R.string.rules_import)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    placeholder = { Text(stringResource(R.string.rules_import_hint)) },
                    minLines = 6,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            clipboard.getText()?.text?.let { text = it }
                        },
                    ) { Text(stringResource(R.string.rules_paste)) }
                    TextButton(
                        enabled = !importing,
                        onClick = { filePicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    ) { Text("从文件") }
                    if (importing) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("https://…/rule.json") },
                    )
                    TextButton(
                        enabled = !importing && url.startsWith("https://"),
                        onClick = { onInstallUrl(url) },
                    ) { Text("下载") }
                }
                issues.forEach { issue ->
                    Text(
                        text = "${issue.pointer}：${issue.message}" +
                            (issue.hint?.let { "（$it）" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (issue.severity == IssueSeverity.ERROR) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !importing,
                onClick = { onInstall(text) },
            ) { Text(stringResource(R.string.rules_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !importing) {
                Text(stringResource(R.string.rules_cancel))
            }
        },
    )
}
