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
            Text("下载管理")
        }
        TextButton(
            onClick = onOpenLocal,
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Text("本地漫画")
        }
        TextButton(
            onClick = onOpenBackup,
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Text("备份与恢复")
        }

        SectionTitle(stringResource(R.string.settings_appearance))
        ThemeOptions(selected = themeMode, onSelect = viewModel::setThemeMode)

        SectionTitle("阅读")
        ReaderModeOptions(selected = readerMode, onSelect = viewModel::setReaderMode)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("双页模式", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "仅横向翻页生效",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = readerDoublePage,
                onCheckedChange = viewModel::setReaderDoublePage,
            )
        }

        SectionTitle("点击区动作")
        TapZoneRow("左侧", tapConfig.left) { viewModel.setTapAction("LEFT", it) }
        TapZoneRow("中间", tapConfig.center) { viewModel.setTapAction("CENTER", it) }
        TapZoneRow("右侧", tapConfig.right) { viewModel.setTapAction("RIGHT", it) }

        if (BuildConfig.DEBUG) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("开发者")
            TextButton(
                onClick = onOpenReaderDemo,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Text("阅读器演示（本地占位图）")
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
        TapAction.PREV to "上一页",
        TapAction.NEXT to "下一页",
        TapAction.MENU to "菜单",
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
        ReadMode.PAGED_LTR to "左 → 右",
        ReadMode.PAGED_RTL to "右 → 左",
        ReadMode.PAGED_TTB to "上下翻页",
        ReadMode.WEBTOON to "条漫（连续滚动）",
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
