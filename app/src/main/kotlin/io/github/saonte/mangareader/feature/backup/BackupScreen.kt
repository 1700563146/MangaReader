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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saonte.mangareader.R
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
            TextButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.action_back)) }
            ScreenHeader(title = stringResource(R.string.backup_title), modifier = Modifier.weight(1f))
        }

        Text(
            text = stringResource(R.string.backup_included) + "\n" +
                stringResource(R.string.backup_excluded),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(modifier = Modifier.padding(top = 8.dp))
        Button(
            enabled = !state.busy,
            onClick = { exportLauncher.launch(suggestedName) },
            modifier = Modifier.padding(horizontal = 24.dp),
        ) { Text(stringResource(R.string.backup_export)) }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text(
            text = stringResource(R.string.backup_import_mode),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        Column(modifier = Modifier.selectableGroup()) {
            ModeRow(stringResource(R.string.backup_mode_merge), stringResource(R.string.backup_mode_merge_desc), ImportMode.MERGE, state.mode, viewModel::setMode)
            ModeRow(stringResource(R.string.backup_mode_replace), stringResource(R.string.backup_mode_replace_desc), ImportMode.REPLACE, state.mode, viewModel::setMode)
            ModeRow(stringResource(R.string.backup_mode_rules), stringResource(R.string.backup_mode_rules_desc), ImportMode.RULES_ONLY, state.mode, viewModel::setMode)
        }

        Button(
            enabled = !state.busy,
            onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        ) { Text(stringResource(R.string.backup_import)) }

        if (state.busy) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.backup_busy), style = MaterialTheme.typography.bodyMedium)
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
                TextButton(onClick = viewModel::dismissMessage) { Text(stringResource(R.string.action_got_it)) }
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
