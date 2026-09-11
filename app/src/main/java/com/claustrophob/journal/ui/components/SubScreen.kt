package com.claustrophob.journal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.claustrophob.journal.ui.theme.extra

// Общая заготовка экрана: большой заголовок, который при прокрутке сжимается,
// под ним фильтры, которые не уезжают, ниже сам экран. Раньше у вкладок и у
// разделов из "Ещё" заголовки были разные, и выглядело как два разных приложения.
// onBack == null — значит это вкладка, стрелки назад нет.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    pinned: (@Composable () -> Unit)? = null,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    content: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val background = MaterialTheme.colorScheme.background

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column(Modifier.background(background)) {
                LargeTopAppBar(
                    title = {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                            }
                        }
                    },
                    actions = actions,
                    expandedHeight = 108.dp,
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = background,
                        scrolledContainerColor = background,
                    ),
                )
                pinned?.invoke()
                // Полоска под заголовком появляется, только когда список прокрутили.
                val lifted = scrollBehavior.state.collapsedFraction > 0.9f ||
                    scrollBehavior.state.overlappedFraction > 0.01f
                HorizontalDivider(
                    thickness = 0.8.dp,
                    color = if (lifted) MaterialTheme.extra.hairline else Color.Transparent,
                )
            }
        },
    ) { padding ->
        val bottom = if (onBack != null) {
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
        } else {
            24.dp
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            val body = @Composable {
                content(PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom))
            }
            if (onRefresh != null) {
                RefreshableScreen(refreshing = refreshing, onRefresh = onRefresh) { body() }
            } else {
                Box(Modifier.fillMaxWidth()) { body() }
            }
            // Где нет нижней панели, список заезжает под системные кнопки.
            // Подложка, чтобы кнопки не висели прямо на тексте.
            if (onBack != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsBottomHeight(WindowInsets.navigationBars)
                        .background(background.copy(alpha = 0.92f)),
                )
            }
        }
    }
}

// Старое имя для разделов из "Ещё".
@Composable
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (contentPadding: PaddingValues) -> Unit,
) = JournalScaffold(title = title, onBack = onBack, modifier = modifier, content = content)
