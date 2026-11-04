package io.github.saonte.mangareader.feature.shelf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.saonte.mangareader.R
import io.github.saonte.mangareader.designsystem.component.EmptyState
import io.github.saonte.mangareader.ui.components.ScreenHeader

@Composable
fun ShelfScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.nav_shelf))
        EmptyState(
            title = stringResource(R.string.shelf_empty_title),
            body = stringResource(R.string.shelf_empty_body),
        )
    }
}
