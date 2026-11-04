package io.github.saonte.mangareader.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.saonte.mangareader.model.ReadMode
import io.github.saonte.mangareader.reader.ReaderUiState

@Composable
fun ReaderMenu(
    state: ReaderUiState,
    onToggleMode: () -> Unit,
    onToggleDoublePage: () -> Unit,
    onBrightness: (Float) -> Unit,
    onToggleNightFilter: () -> Unit,
    onToggleAutoCrop: () -> Unit,
    onExit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.86f),
        contentColor = Color.White,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.chapterTitle.ifBlank { "阅读" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (state.pageCount > 0) {
                        "${state.pageIndex + 1} / ${state.pageCount}"
                    } else {
                        "${state.pageIndex + 1} / ?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("亮度", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = state.brightness,
                    onValueChange = onBrightness,
                    valueRange = 0.15f..1f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    ),
                )
            }
            Spacer(modifier = Modifier.padding(top = 2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onToggleMode) {
                    Text(
                        text = when (state.mode) {
                            ReadMode.PAGED_LTR -> "左→右"
                            ReadMode.PAGED_RTL -> "右→左"
                            ReadMode.PAGED_TTB -> "上下"
                            ReadMode.WEBTOON -> "条漫"
                        },
                        color = Color.White,
                    )
                }
                if (state.mode != ReadMode.WEBTOON && state.mode != ReadMode.PAGED_TTB) {
                    TextButton(onClick = onToggleDoublePage) {
                        Text(
                            text = if (state.doublePage) "单页" else "双页",
                            color = Color.White,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onToggleAutoCrop) {
                    Text(
                        text = if (state.autoCrop) "裁剪开" else "裁剪关",
                        color = Color.White,
                    )
                }
                TextButton(onClick = onToggleNightFilter) {
                    Text(
                        text = if (state.nightFilter) "夜间开" else "夜间关",
                        color = Color.White,
                    )
                }
                TextButton(onClick = onExit) { Text("退出", color = Color.White) }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onClose) { Text("收起", color = Color.White) }
            }
        }
    }
}
