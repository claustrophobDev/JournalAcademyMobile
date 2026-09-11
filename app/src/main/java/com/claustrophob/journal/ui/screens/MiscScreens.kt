package com.claustrophob.journal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.claustrophob.journal.AppContainer
import com.claustrophob.journal.data.JournalRepository
import com.claustrophob.journal.data.Leaderboards
import com.claustrophob.journal.data.Res
import com.claustrophob.journal.data.net.AchievementDto
import com.claustrophob.journal.data.net.LeaderRowDto
import com.claustrophob.journal.data.net.NewsDetailDto
import com.claustrophob.journal.data.net.NewsDto
import com.claustrophob.journal.data.net.UserInfoDto
import com.claustrophob.journal.ui.LoaderViewModel
import com.claustrophob.journal.ui.components.Avatar
import com.claustrophob.journal.ui.components.EmptyState
import com.claustrophob.journal.ui.components.ErrorState
import com.claustrophob.journal.ui.components.GroupedList
import com.claustrophob.journal.ui.components.IconTile
import com.claustrophob.journal.ui.components.JCard
import com.claustrophob.journal.ui.components.JournalScaffold
import com.claustrophob.journal.ui.components.ListRow
import com.claustrophob.journal.ui.components.ListSkeleton
import com.claustrophob.journal.ui.components.RowDivider
import com.claustrophob.journal.ui.components.Segmented
import com.claustrophob.journal.ui.components.StatusBanner
import com.claustrophob.journal.ui.journalViewModel
import com.claustrophob.journal.ui.theme.extra
import com.claustrophob.journal.ui.theme.tabular
import com.claustrophob.journal.util.Dates
import com.claustrophob.journal.util.pluralRu
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// новости

class NewsViewModel(private val repository: JournalRepository) :
    LoaderViewModel<List<NewsDto>>({ repository.news() }) {

    private val _detail = MutableStateFlow<NewsDetailDto?>(null)
    val detail: StateFlow<NewsDetailDto?> = _detail.asStateFlow()

    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    fun openDetail(id: Int) {
        _detailLoading.value = true
        viewModelScope.launch {
            _detail.value = (repository.newsDetail(id) as? Res.Ok)?.data
            _detailLoading.value = false
        }
    }

    fun closeDetail() {
        _detail.value = null
    }
}

private val NewsOrange = Color(0xFFF0701A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(onBack: () -> Unit) {
    val viewModel: NewsViewModel = journalViewModel { container: AppContainer ->
        NewsViewModel(container.repository)
    }
    val state by viewModel.state.collectAsState()
    val detail by viewModel.detail.collectAsState()

    JournalScaffold(
        title = "Новости",
        onBack = onBack,
        refreshing = state.refreshing,
        onRefresh = { viewModel.load(refresh = true) },
    ) { padding ->
        val items = state.data.orEmpty()
        when {
            state.showSkeleton -> Column(Modifier.padding(padding)) { ListSkeleton(rows = 5) }

            state.data == null -> ErrorState(
                message = state.error ?: "Нет данных",
                onRetry = { viewModel.load(refresh = true) },
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    StatusBanner(
                        message = state.error,
                        offline = true,
                        onRetry = { viewModel.load(refresh = true) },
                        onDismiss = viewModel::dismissError,
                    )
                }
                if (items.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.Campaign,
                            title = "Новостей нет",
                            subtitle = "Объявления академии появятся здесь",
                        )
                    }
                } else {
                    item {
                        GroupedList {
                            items.forEachIndexed { index, news ->
                                NewsRow(news) { viewModel.openDetail(news.id) }
                                if (index != items.lastIndex) RowDivider(inset = 66)
                            }
                        }
                    }
                }
            }
        }
    }

    detail?.let { article ->
        ModalBottomSheet(
            onDismissRequest = viewModel::closeDetail,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp),
            ) {
                IconTile(Icons.Rounded.Campaign, NewsOrange)
                Spacer(Modifier.height(14.dp))
                Text(article.theme, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = article.text.stripHtml(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun NewsRow(news: NewsDto, onClick: () -> Unit) {
    ListRow(
        title = news.theme,
        subtitle = Dates.timestampLabel(news.time),
        leading = {
            Box {
                IconTile(Icons.Rounded.Campaign, NewsOrange)
                // Непрочитанная — с точкой, как в мессенджерах.
                if (!news.viewed) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 3.dp, y = (-3).dp)
                            .size(12.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                            .padding(2.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
            }
        },
        onClick = onClick,
    )
}

private fun String.stripHtml(): String = this
    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&quot;", "\"")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()

// рейтинг

class LeadersViewModel(repository: JournalRepository) :
    LoaderViewModel<Leaderboards>({ repository.leaderboards() }) {

    // Нужен, чтобы подсветить свою строку в рейтинге. Профиль обычно уже в кэше.
    private val _me = MutableStateFlow<UserInfoDto?>(null)
    val me: StateFlow<UserInfoDto?> = _me.asStateFlow()

    init {
        viewModelScope.launch {
            repository.profile().collect { result ->
                if (result is Res.Ok) _me.value = result.data
            }
        }
    }
}

@Composable
fun LeadersScreen(onBack: () -> Unit) {
    val viewModel: LeadersViewModel = journalViewModel { container: AppContainer ->
        LeadersViewModel(container.repository)
    }
    val state by viewModel.state.collectAsState()
    val me by viewModel.me.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    JournalScaffold(
        title = "Рейтинг",
        onBack = onBack,
        pinned = {
            Segmented(
                options = listOf("Группа", "Поток"),
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            )
        },
        refreshing = state.refreshing,
        onRefresh = { viewModel.load(refresh = true) },
    ) { padding ->
        val rows = remember(state.data, tab) {
            val source = if (tab == 0) state.data?.group else state.data?.stream
            source.orEmpty().sortedBy { it.position }
        }
        when {
            state.showSkeleton -> Column(Modifier.padding(padding)) { ListSkeleton(rows = 6) }

            rows.isEmpty() -> LazyColumn(contentPadding = padding) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Leaderboard,
                        title = "Рейтинг пуст",
                        subtitle = "Данные появятся после первых начислений",
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val podium = rows.size >= 3
                if (podium) {
                    item(key = "podium-$tab") { Podium(rows.take(3), me) }
                }
                val rest = if (podium) rows.drop(3) else rows
                if (rest.isNotEmpty()) {
                    item(key = "rest-$tab") {
                        GroupedList {
                            rest.forEachIndexed { index, row ->
                                LeaderRow(row, isMe = row.isMe(me))
                                if (index != rest.lastIndex) RowDivider(inset = 92)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun LeaderRowDto.isMe(me: UserInfoDto?): Boolean {
    if (me == null) return false
    return (me.studentId != 0 && id == me.studentId) ||
        (me.fullName.isNotBlank() && fullName.trim().equals(me.fullName.trim(), ignoreCase = true))
}

private val Gold = Color(0xFFE3A600)
private val Silver = Color(0xFF9AA3AE)
private val Bronze = Color(0xFFC0773A)

// Первые трое на пьедестале, так интереснее, чем просто таблица.
@Composable
private fun Podium(top: List<LeaderRowDto>, me: UserInfoDto?) {
    JCard(contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 0.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            PodiumPlace(top[1], 2, Silver, 56.dp, Modifier.weight(1f), top[1].isMe(me))
            PodiumPlace(top[0], 1, Gold, 84.dp, Modifier.weight(1f), top[0].isMe(me))
            PodiumPlace(top[2], 3, Bronze, 40.dp, Modifier.weight(1f), top[2].isMe(me))
        }
    }
}

@Composable
private fun PodiumPlace(
    row: LeaderRowDto,
    place: Int,
    medal: Color,
    standHeight: Dp,
    modifier: Modifier,
    isMe: Boolean,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (place == 1) {
            Icon(Icons.Rounded.EmojiEvents, null, tint = Gold, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
        }
        Box(
            Modifier
                .border(2.5.dp, medal, CircleShape)
                .padding(3.dp),
        ) {
            Avatar(
                url = row.photoPath.takeIf { it.isNotBlank() },
                name = row.fullName,
                size = if (place == 1) 64 else 52,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = podiumName(row.fullName),
            style = MaterialTheme.typography.labelLarge,
            color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = row.amount.roundToInt().toString(),
            style = MaterialTheme.typography.labelMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .height(standHeight)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(Brush.verticalGradient(listOf(medal.copy(alpha = 0.35f), medal.copy(alpha = 0.06f)))),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = place.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = medal,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

// "Фокин Павел Андреевич" -> "Павел Ф." На пьедестале места мало.
private fun podiumName(full: String): String {
    val parts = full.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[1]} ${parts[0].first()}."
        else -> full.trim()
    }
}

@Composable
private fun LeaderRow(row: LeaderRowDto, isMe: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.07f) else Color.Transparent,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.position.toString(),
            style = MaterialTheme.typography.titleSmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(26.dp),
        )
        Spacer(Modifier.width(12.dp))
        Avatar(url = row.photoPath.takeIf { it.isNotBlank() }, name = row.fullName, size = 38)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.fullName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isMe) {
                Text(
                    text = "это вы",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = row.amount.roundToInt().toString(),
            style = MaterialTheme.typography.titleSmall.tabular(),
            fontWeight = FontWeight.Bold,
        )
    }
}

// достижения

class AchievementsViewModel(repository: JournalRepository) :
    LoaderViewModel<List<AchievementDto>>({ repository.achievements() })

@Composable
fun AchievementsScreen(onBack: () -> Unit) {
    val viewModel: AchievementsViewModel = journalViewModel { container: AppContainer ->
        AchievementsViewModel(container.repository)
    }
    val state by viewModel.state.collectAsState()

    JournalScaffold(
        title = "Достижения",
        onBack = onBack,
        refreshing = state.refreshing,
        onRefresh = { viewModel.load(refresh = true) },
    ) { padding ->
        val items = remember(state.data) { state.data.orEmpty().sortedByDescending { it.isActive } }
        when {
            state.showSkeleton -> Column(Modifier.padding(padding)) { ListSkeleton(rows = 4) }

            items.isEmpty() -> LazyColumn(contentPadding = padding) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.EmojiEvents,
                        title = "Достижений пока нет",
                        subtitle = "Они появятся по мере учёбы и активности",
                    )
                }
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = padding,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AchievementsSummary(earned = items.count { it.isActive }, total = items.size)
                }
                items(items, key = { it.id }) { achievement ->
                    AchievementTile(achievement)
                }
            }
        }
    }
}

@Composable
private fun AchievementsSummary(earned: Int, total: Int) {
    JCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Получено $earned из $total",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(if (total == 0) 0f else earned.toFloat() / total)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(Gold),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            IconTile(Icons.Rounded.EmojiEvents, Gold, size = 48.dp, iconSize = 26.dp)
        }
    }
}

@Composable
private fun AchievementTile(achievement: AchievementDto) {
    val active = achievement.isActive
    JCard(contentPadding = PaddingValues(16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) {
                            Brush.linearGradient(listOf(Gold, MaterialTheme.extra.heroStart))
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (active) Icons.Rounded.EmojiEvents else Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = if (active) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = achievementTitle(achievement.translateKey),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (active) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = if (active) "получено" else "впереди",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// API отдаёт только ключи на английском, типа "5_VISITS_WITHOUT_GAP".
// Те, что видел, перевёл, остальные просто превращаем в слова.
private fun achievementTitle(key: String): String {
    val words = key.removePrefix("ACHIEVEMENT_").replace('_', ' ').lowercase().trim()
    Regex("""(\d+) visits? without (gap|delay)""").matchEntire(words)?.let { match ->
        val count = match.groupValues[1].toInt()
        val tail = if (match.groupValues[2] == "gap") "без пропусков" else "без опозданий"
        return "${pluralRu(count, "пара", "пары", "пар")} $tail"
    }
    return when (words) {
        "fill in profile" -> "Заполнен профиль"
        "survey" -> "Пройден опрос"
        "email confirmation" -> "Почта подтверждена"
        "phone confirmation" -> "Телефон подтверждён"
        "social activity" -> "Активность в соцсетях"
        "photo" -> "Фото в профиле"
        else -> words.replaceFirstChar { it.uppercase() }
    }
}
