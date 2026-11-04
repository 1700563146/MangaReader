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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.saonte.mangareader.model.ReadMode
import io.github.saonte.mangareader.reader.R
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
                    text = state.chapterTitle.ifBlank { stringResource(R.string.reader_default_title) },
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
                Text(stringResource(R.string.reader_brightness), style = MaterialTheme.typography.labelMedium)
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
                            ReadMode.PAGED_LTR -> stringResource(R.string.reader_mode_ltr)
                            ReadMode.PAGED_RTL -> stringResource(R.string.reader_mode_rtl)
                            ReadMode.PAGED_TTB -> stringResource(R.string.reader_mode_ttb)
                            ReadMode.WEBTOON -> stringResource(R.string.reader_mode_webtoon)
                        },
                        color = Color.White,
                    )
                }
                if (state.mode != ReadMode.WEBTOON && state.mode != ReadMode.PAGED_TTB) {
                    TextButton(onClick = onToggleDoublePage) {
                        Text(
                            text = if (state.doublePage) {
                                stringResource(R.string.reader_double_page_on)
                            } else {
                                stringResource(R.string.reader_double_page_off)
                            },
                            color = Color.White,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onToggleAutoCrop) {
                    Text(
                        text = if (state.autoCrop) {
                            stringResource(R.string.reader_crop_on)
                        } else {
                            stringResource(R.string.reader_crop_off)
                        },
                        color = Color.White,
                    )
                }
                TextButton(onClick = onToggleNightFilter) {
                    Text(
                        text = if (state.nightFilter) {
                            stringResource(R.string.reader_night_on)
                        } else {
                            stringResource(R.string.reader_night_off)
                        },
                        color = Color.White,
                    )
                }
                TextButton(onClick = onExit) { Text(stringResource(R.string.reader_exit), color = Color.White) }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onClose) { Text(stringResource(R.string.reader_close), color = Color.White) }
            }
        }
    }
}
