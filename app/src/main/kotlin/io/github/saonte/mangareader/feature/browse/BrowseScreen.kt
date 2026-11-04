package io.github.saonte.mangareader.feature.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.saonte.mangareader.R
import io.github.saonte.mangareader.designsystem.component.EmptyState
import io.github.saonte.mangareader.ui.components.ScreenHeader

@Composable
fun BrowseScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.nav_browse))
        EmptyState(
            title = stringResource(R.string.browse_empty_title),
            body = stringResource(R.string.browse_empty_body),
        )
    }
}
