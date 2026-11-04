package io.github.saonte.mangareader.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.saonte.mangareader.R
import io.github.saonte.mangareader.designsystem.component.EmptyState
import io.github.saonte.mangareader.model.SourceHealth
import io.github.saonte.mangareader.source.ComicSummary

@Composable
fun SearchScreen(
    initialKeyword: String,
    onBack: () -> Unit,
    onOpenDetail: (sourceType: Long, cid: String) -> Unit,
    onOpenRules: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(initialKeyword) {
        if (initialKeyword.isNotBlank()) {
            viewModel.onKeywordChange(initialKeyword)
            viewModel.search(initialKeyword)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            OutlinedTextField(
                value = state.keyword,
                onValueChange = viewModel::onKeywordChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
            )
            TextButton(onClick = { viewModel.search() }) {
                Text(stringResource(R.string.search_action))
            }
        }

        if (state.searching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        val hasContent = state.results.any { it.items.isNotEmpty() } || state.results.any { it.failure != null }
        if (!hasContent && !state.searching) {
            EmptyState(
                title = stringResource(R.string.search_empty_title),
                body = state.message ?: stringResource(R.string.search_empty_body),
                actionLabel = stringResource(R.string.rules_manage),
                onAction = onOpenRules,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                state.results.forEach { result ->
                    item(key = "header-${result.source.type}") {
                        SourceHeader(result)
                    }
                    if (result.failure != null) {
                        item(key = "fail-${result.source.type}") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = result.failure.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { viewModel.retry(result.source) }) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                        }
                    } else if (result.loading) {
                        item(key = "loading-${result.source.type}") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                        }
                    } else {
                        items(result.items, key = { "${result.source.type}-${it.cid}" }) { item ->
                            SearchResultRow(item, onClick = { onOpenDetail(result.source.type, item.cid) })
                            HorizontalDivider()
                        }
                        if (result.hasNext || result.loadingMore) {
                            item(key = "more-${result.source.type}") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (result.loadingMore) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                    } else {
                                        TextButton(onClick = { viewModel.loadMore(result.source) }) {
                                            Text("加载更多")
                                        }
                                    }
                                }
                            }
                        }
                        if (result.items.isEmpty()) {
                            item(key = "empty-${result.source.type}") {
                                Text(
                                    text = "该源没有结果",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceHeader(result: SourceResult) {
    val subtitle = when {
        result.failure != null -> "失败"
        result.loading -> "搜索中…"
        else -> "${result.items.size} 条" + if (result.hasNext) " · 还有更多" else ""
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = result.source.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = if (result.failure != null || result.source.health != SourceHealth.HEALTHY) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun SearchResultRow(item: ComicSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = item.cover,
            contentDescription = item.title,
            modifier = Modifier
                .size(width = 56.dp, height = 78.dp)
                .clip(MaterialTheme.shapes.small),
            contentScale = ContentScale.Crop,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = listOfNotNull(
                    item.author,
                    item.update,
                    item.finish?.let { if (it) "完结" else "连载" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
