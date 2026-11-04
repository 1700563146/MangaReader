package io.github.saonte.mangareader.feature.downloads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saonte.mangareader.R
import io.github.saonte.mangareader.designsystem.component.EmptyState
import io.github.saonte.mangareader.storage.DownloadState
import io.github.saonte.mangareader.storage.DownloadTask
import io.github.saonte.mangareader.ui.components.ScreenHeader

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.action_back)) }
            ScreenHeader(title = stringResource(R.string.downloads_title), modifier = Modifier.weight(1f))
        }

        if (tasks.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.downloads_empty_title),
                body = stringResource(R.string.downloads_empty_body),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(tasks, key = { it.id }) { task ->
                    DownloadRow(
                        task = task,
                        onPause = { viewModel.pause(task.chapterId) },
                        onResume = { viewModel.resume(task.chapterId) },
                        onCancel = { viewModel.cancel(task.chapterId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(task.title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = when (task.state) {
                DownloadState.QUEUED -> stringResource(R.string.downloads_queued)
                DownloadState.RUNNING -> stringResource(R.string.downloads_running, task.done, task.total)
                DownloadState.PAUSED -> stringResource(R.string.downloads_paused)
                DownloadState.COMPLETED -> stringResource(R.string.downloads_completed)
                DownloadState.FAILED -> stringResource(R.string.downloads_failed, task.errorCode.orEmpty())
                DownloadState.CANCELLED -> stringResource(R.string.downloads_cancelled)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (task.state == DownloadState.RUNNING || task.state == DownloadState.PAUSED) {
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.weight(1f))
            when (task.state) {
                DownloadState.RUNNING, DownloadState.QUEUED ->
                    TextButton(onClick = onPause) { Text(stringResource(R.string.downloads_pause)) }

                DownloadState.PAUSED, DownloadState.FAILED ->
                    TextButton(onClick = onResume) { Text(stringResource(R.string.action_resume)) }

                else -> Unit
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_delete)) }
        }
    }
}
