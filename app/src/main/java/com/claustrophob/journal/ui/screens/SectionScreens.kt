package com.claustrophob.journal.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claustrophob.journal.AppContainer
import com.claustrophob.journal.data.JournalRepository
import com.claustrophob.journal.data.Payments
import com.claustrophob.journal.data.Portfolio
import com.claustrophob.journal.data.Res
import com.claustrophob.journal.data.net.LibraryMaterialDto
import com.claustrophob.journal.data.net.LibraryType
import com.claustrophob.journal.data.net.PaymentHistoryDto
import com.claustrophob.journal.data.net.PaymentPlanDto
import com.claustrophob.journal.data.net.PortfolioRequestDto
import com.claustrophob.journal.data.net.PortfolioWorkDto
import com.claustrophob.journal.ui.LoaderViewModel
import com.claustrophob.journal.ui.UiState
import com.claustrophob.journal.ui.components.ChipRow
import com.claustrophob.journal.ui.components.EmptyState
import com.claustrophob.journal.ui.components.ErrorState
import com.claustrophob.journal.ui.components.GradeBadge
import com.claustrophob.journal.ui.components.GroupedList
import com.claustrophob.journal.ui.components.IconTile
import com.claustrophob.journal.ui.components.JCard
import com.claustrophob.journal.ui.components.JournalScaffold
import com.claustrophob.journal.ui.components.ListSkeleton
import com.claustrophob.journal.ui.components.Pill
import com.claustrophob.journal.ui.components.RowDivider
import com.claustrophob.journal.ui.components.SectionHeader
import com.claustrophob.journal.ui.components.Segmented
import com.claustrophob.journal.ui.components.StatusBanner
import com.claustrophob.journal.ui.components.SubjectTile
import com.claustrophob.journal.ui.components.segmentShape
import com.claustrophob.journal.ui.components.shortPersonName
import com.claustrophob.journal.ui.journalViewModel
import com.claustrophob.journal.ui.theme.extra
import com.claustrophob.journal.ui.theme.tabular
import com.claustrophob.journal.util.Dates
import com.claustrophob.journal.util.Downloader
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// библиотека

private data class LibraryTab(
    val title: String,
    val type: Int,
    val icon: ImageVector,
    val tint: Color,
)

private val libraryTabs = listOf(
    LibraryTab("Уроки", LibraryType.LESSON, Icons.AutoMirrored.Rounded.MenuBook, Color(0xFF2F7CF6)),
    LibraryTab("Книги", LibraryType.BOOK, Icons.AutoMirrored.Rounded.MenuBook, Color(0xFF12A36B)),
    LibraryTab("Видео", LibraryType.VIDEO, Icons.Rounded.PlayCircle, Color(0xFFE2463B)),
    LibraryTab("Презентации", LibraryType.PRESENTATION, Icons.Rounded.Slideshow, Color(0xFFF0701A)),
    LibraryTab("Лабораторные", LibraryType.LABORATORY, Icons.Rounded.Science, Color(0xFF8B5CF6)),
    LibraryTab("Статьи", LibraryType.ARTICLE, Icons.Rounded.Article, Color(0xFF0EA5C6)),
)

class LibraryViewModel(private val repository: JournalRepository) : ViewModel() {

    private val _type = MutableStateFlow(LibraryType.LESSON)
    val type: StateFlow<Int> = _type.asStateFlow()

    private val _state = MutableStateFlow(UiState<List<LibraryMaterialDto>>(loading = true))
    val state: StateFlow<UiState<List<LibraryMaterialDto>>> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun selectType(type: Int) {
        if (_type.value == type) return
        _type.value = type
        _state.value = UiState(loading = true)
        load()
    }

    fun load(refresh: Boolean = false) {
        job?.cancel()
        _state.value = _state.value.copy(
            loading = !refresh && _state.value.data == null,
            refreshing = refresh,
            error = null,
        )
        job = viewModelScope.launch {
            repository.library(_type.value).collect { result ->
                _state.value = when (result) {
                    is Res.Ok -> UiState(
                        data = result.data,
                        loading = false,
                        refreshing = false,
                        stale = result.stale,
                        updatedAt = result.savedAt,
                    )

                    is Res.Err -> _state.value.copy(
                        loading = false,
                        refreshing = false,
                        error = result.message,
                    )
                }
            }
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }
}

@Composable
fun LibraryScreen(onBack: () -> Unit) {
    val viewModel: LibraryViewModel = journalViewModel { container: AppContainer ->
        LibraryViewModel(container.repository)
    }
    val state by viewModel.state.collectAsState()
    val type by viewModel.type.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Что сейчас качается — у этой строки вместо стрелки крутилка.
    var busyId by remember { mutableStateOf<Int?>(null) }

    JournalScaffold(
        title = "Библиотека",
        onBack = onBack,
        pinned = {
            ChipRow(
                options = libraryTabs.map { it.title },
                selected = libraryTabs.indexOfFirst { it.type == type }.coerceAtLeast(0),
                onSelect = { viewModel.selectType(libraryTabs[it].type) },
                modifier = Modifier.padding(bottom = 10.dp),
            )
        },
        refreshing = state.refreshing,
        onRefresh = { viewModel.load(refresh = true) },
    ) { padding ->
        val items = state.data.orEmpty()
        when {
            state.showSkeleton -> Column(Modifier.padding(padding)) { ListSkeleton(rows = 4) }

            items.isEmpty() && state.data == null -> ErrorState(
                message = state.error ?: "Нет данных",
                onRetry = { viewModel.load(refresh = true) },
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
            ) {
                item {
                    StatusBanner(
                        message = state.error,
                        offline = true,
                        onRetry = { viewModel.load(refresh = true) },
                        onDismiss = viewModel::dismissError,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                if (items.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.AutoMirrored.Rounded.MenuBook,
                            title = "Здесь пусто",
                            subtitle = "Материалов этого типа пока не выложили",
                        )
                    }
                }
                itemsIndexed(items, key = { _, material -> material.id }) { index, material ->
                    MaterialRow(
                        material = material,
                        busy = busyId == material.id,
                        index = index,
                        count = items.size,
                    ) {
                        if (material.downloadUrl.isNotBlank()) {
                            // Качаем своим клиентом, а если файл на чужом
                            // хосте — отдаём браузеру.
                            busyId = material.id
                            scope.launch {
                                Downloader.fetch(
                                    context,
                                    Downloader.absolute(material.downloadUrl),
                                    material.filename.ifBlank { material.theme },
                                ).onSuccess { Downloader.open(context, it) }
                                    .onFailure { Downloader.openLink(context, material.downloadUrl) }
                                busyId = null
                            }
                        } else {
                            val link = material.url.ifBlank { material.link }
                            if (link.isNotBlank()) Downloader.openLink(context, link)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialRow(
    material: LibraryMaterialDto,
    busy: Boolean,
    index: Int,
    count: Int,
    onOpen: () -> Unit,
) {
    val hasTarget = material.url.isNotBlank() ||
        material.link.isNotBlank() ||
        material.downloadUrl.isNotBlank()
    val tab = libraryTabs.firstOrNull { it.type == material.materialType } ?: libraryTabs.first()

    Column(
        Modifier
            .fillMaxWidth()
            .clip(segmentShape(index, count))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = hasTarget && !busy, onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            IconTile(tab.icon, tab.tint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (material.specName.isNotBlank()) {
                    Text(
                        text = material.specName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(text = material.theme, style = MaterialTheme.typography.titleSmall)
                if (material.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = material.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (material.date.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = Dates.timestampLabel(material.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (busy) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else if (hasTarget) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (material.downloadUrl.isNotBlank()) {
                        Icons.Rounded.Download
                    } else {
                        Icons.AutoMirrored.Rounded.OpenInNew
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (index != count - 1) RowDivider(inset = 66)
    }
}

// оплата

class PaymentsViewModel(repository: JournalRepository) :
    LoaderViewModel<Payments>({ repository.payments() })

@Composable
fun PaymentsScreen(onBack: () -> Unit) {
    val viewModel: PaymentsViewModel = journalViewModel { container: AppContainer ->
        PaymentsViewModel(container.repository)
    }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val payments = state.data

    JournalScaffold(
        title = "Оплата",
        onBack = onBack,
        refreshing = state.refreshing,
        onRefresh = { viewModel.load(refresh = true) },
    ) { padding ->
        when {
            state.showSkeleton -> Column(Modifier.padding(padding)) { ListSkeleton(rows = 3) }

            payments == null -> ErrorState(
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

                item {
                    val link = payments.info.onlineLink
                    BalanceCard(
                        payments = payments,
                        onPay = if (payments.info.onlineLinkHas && link.isNotBlank()) {
                            { Downloader.openLink(context, link) }
                        } else {
                            null
                        },
                    )
                }

                if (payments.plan.isNotEmpty()) {
                    item { SectionHeader("График платежей") }
                    item {
                        GroupedList {
                            payments.plan.forEachIndexed { index, plan ->
                                PlanRow(plan)
                                if (index != payments.plan.lastIndex) RowDivider(inset = 66)
                            }
                        }
                    }
                }

                if (payments.history.isNotEmpty()) {
                    val history = payments.history.sortedByDescending { it.date }
                    item { SectionHeader("История") }
                    item {
                        GroupedList {
                            history.forEachIndexed { index, entry ->
                                HistoryRow(entry)
                                if (index != history.lastIndex) RowDivider(inset = 66)
                            }
                        }
                    }
                }

                val requisites = payments.info.requisites
                if (requisites.settlementAccount.isNotBlank()) {
                    val rows = listOf(
                        "Получатель" to requisites.lawPerson,
                        "Расчётный счёт" to requisites.settlementAccount,
                        "ОКПО" to requisites.okpo,
                        "МФО / БИК" to requisites.mfo,
                        "Договор" to payments.info.contractCode,
                    ).filter { it.second.isNotBlank() }
                    item { SectionHeader("Реквизиты") }
                    item {
                        GroupedList {
                            rows.forEachIndexed { index, (label, value) ->
                                Requisite(label, value)
                                if (index != rows.lastIndex) RowDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(payments: Payments, onPay: (() -> Unit)?) {
    val debt = payments.info.payment.amountDebt
    val next = payments.info.payment.amountNext
    val toPay = payments.info.payment.amountToPay

    JCard(contentPadding = PaddingValues(20.dp)) {
        if (debt > 0) {
            Pill("Есть задолженность", MaterialTheme.extra.danger)
        } else {
            Pill("Задолженности нет", MaterialTheme.extra.success, icon = Icons.Rounded.Check)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = money(if (debt > 0) debt else 0.0),
            style = MaterialTheme.typography.displaySmall.tabular(),
            color = if (debt > 0) MaterialTheme.extra.danger else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "долг на сегодня",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (next > 0 || toPay > 0) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                if (next > 0) MoneyFact("Следующий платёж", money(next))
                if (toPay > 0) MoneyFact("К оплате", money(toPay))
            }
        }
        val payDate = payments.info.payment.payDateStart.take(10)
        if (payDate.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Оплатить до ${Dates.humanDayShortWithYear(payDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onPay != null) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onPay,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text("Оплатить на сайте академии", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun MoneyFact(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.titleMedium.tabular())
    }
}

@Composable
private fun PlanRow(plan: PaymentPlanDto) {
    // Что значит status у платежа, из бандла понять не вышло, так что про
    // "оплачено" ничего не пишем. Дата однозначна, по ней и красим.
    val date = plan.paymentDate.take(10)
    val upcoming = date.isNotBlank() && !Dates.isPast(date)
    val tint = if (upcoming) MaterialTheme.extra.warning else MaterialTheme.colorScheme.outline

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(Icons.Rounded.CalendarMonth, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = plan.description.ifBlank { "Платёж" },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (date.isNotBlank()) {
                val relative = Dates.relativeDayLabel(date)
                Text(
                    text = listOfNotNull(
                        Dates.humanDayShortWithYear(date),
                        relative?.takeIf { upcoming },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(text = money(plan.price), style = MaterialTheme.typography.titleSmall.tabular())
    }
}

@Composable
private fun HistoryRow(entry: PaymentHistoryDto) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(Icons.Rounded.Check, MaterialTheme.extra.success)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.description.ifBlank { "Платёж" },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (entry.date.isNotBlank()) {
                Text(
                    text = Dates.humanDayShortWithYear(entry.date.take(10)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = money(entry.amount),
            style = MaterialTheme.typography.titleSmall.tabular(),
            color = MaterialTheme.extra.success,
        )
    }
}

// Реквизиты копируются тапом — перепечатывать счёт руками никто не хочет.
@Composable
private fun Requisite(label: String, value: String) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { copyToClipboard(context, label, value) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
        Icon(
            Icons.Rounded.ContentCopy,
            contentDescription = "Скопировать",
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    // С Android 13 система сама показывает, что скопировано.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
    }
}

private fun money(value: Double): String {
    val rounded = value.roundToInt()
    val grouped = rounded.toString()
        .reversed()
        .chunked(3)
        .joinToString(" ")
        .reversed()
    return "$grouped ₽"
}

// портфолио

class PortfolioViewModel(repository: JournalRepository) :
    LoaderViewModel<Portfolio>({ repository.portfolio() })

@Composable
fun PortfolioScreen(onBack: () -> Unit) {
    val viewModel: PortfolioViewModel = journalViewModel { container: AppContainer ->
        PortfolioViewModel(container.repository)
    }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val portfolio = state.data

    JournalScaffold(
        title = "Портфолио",
        onBack = onBack,
        pinned = {
            Segmented(
                options = listOf("Работы", "Заявки"),
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            )
        },
        refreshing = state.refreshing,
        onRefresh = { viewModel.load(refresh = true) },
    ) { padding ->
        when {
            state.showSkeleton -> Column(Modifier.padding(padding)) { ListSkeleton(rows = 3) }

            portfolio == null -> ErrorState(
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
                if (tab == 0) {
                    if (portfolio.works.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Rounded.Brush,
                                title = "Работ пока нет",
                                subtitle = "Здесь появятся принятые работы вашего портфолио",
                            )
                        }
                    } else {
                        item {
                            GroupedList {
                                portfolio.works.forEachIndexed { index, work ->
                                    PortfolioWorkRow(work) {
                                        if (work.url.isNotBlank()) Downloader.openLink(context, work.url)
                                    }
                                    if (index != portfolio.works.lastIndex) RowDivider(inset = 68)
                                }
                            }
                        }
                    }
                } else {
                    if (portfolio.requests.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Rounded.Brush,
                                title = "Заявок нет",
                                subtitle = "Заявки на добавление работ подаются в журнале на сайте",
                            )
                        }
                    } else {
                        item {
                            GroupedList {
                                portfolio.requests.forEachIndexed { index, request ->
                                    PortfolioRequestRow(request)
                                    if (index != portfolio.requests.lastIndex) RowDivider(inset = 68)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PortfolioWorkRow(work: PortfolioWorkDto, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = work.url.isNotBlank(), onClick = onOpen)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SubjectTile(work.subjectName.ifBlank { work.title })
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            if (work.subjectName.isNotBlank()) {
                Text(
                    text = work.subjectName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = work.title.ifBlank { "Работа" },
                style = MaterialTheme.typography.titleSmall,
            )
            if (work.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = work.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = listOfNotNull(
                    work.teacherName.takeIf { it.isNotBlank() }?.let(::shortPersonName),
                    work.createdAt.take(10).takeIf { it.isNotBlank() }
                        ?.let { Dates.humanDayShortWithYear(it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (work.mark > 0) {
            Spacer(Modifier.width(10.dp))
            GradeBadge(value = work.mark.toDouble())
        }
    }
}

@Composable
private fun PortfolioRequestRow(request: PortfolioRequestDto) {
    val (label, color) = when (request.approveStatus) {
        1 -> "Одобрено" to MaterialTheme.extra.success
        2 -> "Отклонено" to MaterialTheme.extra.danger
        else -> "На рассмотрении" to MaterialTheme.extra.warning
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SubjectTile(request.subjectName.ifBlank { "Заявка" })
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = request.subjectName.ifBlank { "Заявка" },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (request.teacherName.isNotBlank()) {
                        Text(
                            text = shortPersonName(request.teacherName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Pill(label, color)
            }
            if (request.studentComment.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(text = request.studentComment, style = MaterialTheme.typography.bodySmall)
            }
            if (request.teacherComment.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = request.teacherComment,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            if (request.createdAt.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = Dates.humanDayShortWithYear(request.createdAt.take(10)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
