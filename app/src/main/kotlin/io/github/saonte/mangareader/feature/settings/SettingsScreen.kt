package io.github.saonte.mangareader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import io.github.saonte.mangareader.BuildConfig
import io.github.saonte.mangareader.R
import io.github.saonte.mangareader.model.ReadMode
import io.github.saonte.mangareader.model.TapAction
import io.github.saonte.mangareader.model.TapConfig
import io.github.saonte.mangareader.model.ThemeMode
import io.github.saonte.mangareader.ui.components.ScreenHeader

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenReaderDemo: () -> Unit = {},
    onOpenRules: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenLocal: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val readerMode by viewModel.readerMode.collectAsStateWithLifecycle()
    val readerDoublePage by viewModel.readerDoublePage.collectAsStateWithLifecycle()
    val tapConfig by viewModel.tapConfig.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = stringResource(R.string.nav_me))

        TextButton(
            onClick = onOpenRules,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp),
        ) {
            Text(stringResource(R.string.rules_manage))
        }

        TextButton(
            onClick = onOpenDownloads,
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Text(stringResource(R.string.downloads_title))
        }
        TextButton(
            onClick = onOpenLocal,
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Text(stringResource(R.string.local_title))
        }
        TextButton(
            onClick = onOpenBackup,
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Text(stringResource(R.string.backup_title))
        }

        SectionTitle(stringResource(R.string.settings_appearance))
        ThemeOptions(selected = themeMode, onSelect = viewModel::setThemeMode)

        SectionTitle(stringResource(R.string.settings_reading))
        ReaderModeOptions(selected = readerMode, onSelect = viewModel::setReaderMode)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_double_page), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.settings_double_page_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = readerDoublePage,
                onCheckedChange = viewModel::setReaderDoublePage,
            )
        }

        SectionTitle(stringResource(R.string.settings_tap_zones))
        TapZoneRow(stringResource(R.string.tap_zone_left), tapConfig.left) { viewModel.setTapAction("LEFT", it) }
        TapZoneRow(stringResource(R.string.tap_zone_center), tapConfig.center) { viewModel.setTapAction("CENTER", it) }
        TapZoneRow(stringResource(R.string.tap_zone_right), tapConfig.right) { viewModel.setTapAction("RIGHT", it) }

        if (BuildConfig.DEBUG) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_developer))
            TextButton(
                onClick = onOpenReaderDemo,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Text(stringResource(R.string.settings_reader_demo))
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SectionTitle(stringResource(R.string.settings_about))
        ValueRow(
            label = stringResource(R.string.settings_version),
            value = BuildConfig.VERSION_NAME,
        )
        Text(
            text = stringResource(R.string.settings_legal_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
private fun TapZoneRow(label: String, selected: TapAction, onSelect: (TapAction) -> Unit) {
    val options = listOf(
        TapAction.PREV to stringResource(R.string.tap_action_prev),
        TapAction.NEXT to stringResource(R.string.tap_action_next),
        TapAction.MENU to stringResource(R.string.tap_action_menu),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(0.4f), style = MaterialTheme.typography.bodyLarge)
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            options.forEach { (action, text) ->
                TextButton(
                    onClick = { onSelect(action) },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = if (selected == action) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ),
                ) { Text(text, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun ReaderModeOptions(selected: ReadMode, onSelect: (ReadMode) -> Unit) {
    val options = listOf(
        ReadMode.PAGED_LTR to stringResource(R.string.reader_mode_ltr),
        ReadMode.PAGED_RTL to stringResource(R.string.reader_mode_rtl),
        ReadMode.PAGED_TTB to stringResource(R.string.reader_mode_ttb),
        ReadMode.WEBTOON to stringResource(R.string.reader_mode_webtoon),
    )
    Column(modifier = Modifier.selectableGroup()) {
        options.forEach { (mode, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == mode,
                        onClick = { onSelect(mode) },
                        role = Role.RadioButton,
                    )
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == mode, onClick = null)
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun ThemeOptions(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Column(modifier = Modifier.selectableGroup()) {
        ThemeOption(
            label = stringResource(R.string.theme_system),
            selected = selected == ThemeMode.SYSTEM,
            onClick = { onSelect(ThemeMode.SYSTEM) },
        )
        ThemeOption(
            label = stringResource(R.string.theme_light),
            selected = selected == ThemeMode.LIGHT,
            onClick = { onSelect(ThemeMode.LIGHT) },
        )
        ThemeOption(
            label = stringResource(R.string.theme_dark),
            selected = selected == ThemeMode.DARK,
            onClick = { onSelect(ThemeMode.DARK) },
        )
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        )
        Spacer(modifier = Modifier.padding(end = 4.dp))
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
