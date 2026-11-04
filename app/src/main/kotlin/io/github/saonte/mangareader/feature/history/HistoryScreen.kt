package io.github.saonte.mangareader.feature.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.saonte.mangareader.R
import io.github.saonte.mangareader.designsystem.component.EmptyState
import io.github.saonte.mangareader.ui.components.ScreenHeader

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.nav_history))
        EmptyState(
            title = stringResource(R.string.history_empty_title),
            body = stringResource(R.string.history_empty_body),
        )
    }
}
