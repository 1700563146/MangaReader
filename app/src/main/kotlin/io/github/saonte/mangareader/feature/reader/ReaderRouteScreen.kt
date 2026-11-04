package io.github.saonte.mangareader.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saonte.mangareader.reader.ProgressSink
import io.github.saonte.mangareader.reader.ReaderController
import io.github.saonte.mangareader.reader.ui.ReaderScreen
import androidx.compose.runtime.LaunchedEffect

@Composable
fun ReaderRouteScreen(
    onExit: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    when (val current = state) {
        ReaderSessionState.Loading -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Color.White)
        }

        is ReaderSessionState.Failed -> Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = current.message,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = viewModel::load) { Text("重试", color = Color.White) }
            TextButton(onClick = onExit) { Text("返回", color = Color.White) }
        }

        is ReaderSessionState.Ready -> {
            val session = current.session
            val controller = remember(session) {
                ReaderController(
                    pageSource = session.pageSource,
                    progressSink = ProgressSink { _, _, page, _ -> viewModel.onPageSettled(page) },
                    initialMode = prefs.mode,
                    initialDoublePage = prefs.doublePage,
                )
            }
            LaunchedEffect(prefs.mode) { controller.setMode(prefs.mode) }
            LaunchedEffect(prefs.doublePage) { controller.setDoublePage(prefs.doublePage) }
            ReaderScreen(
                controller = controller,
                pageSource = session.pageSource,
                onExit = onExit,
                onModeChanged = viewModel::setModePreference,
                onDoublePageChanged = viewModel::setDoublePagePreference,
                tapConfig = prefs.tapConfig,
            )
        }
    }
}
