package com.claustrophob.journal.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claustrophob.journal.AppContainer
import com.claustrophob.journal.data.HomeworkFeed
import com.claustrophob.journal.data.JournalRepository
import com.claustrophob.journal.data.Res
import com.claustrophob.journal.data.net.HOMEWORK_PAGE_SIZE
import com.claustrophob.journal.data.net.HomeworkDto
import com.claustrophob.journal.data.net.HomeworkStatus
import com.claustrophob.journal.data.net.HomeworkType
import com.claustrophob.journal.ui.components.ChipRow
import com.claustrophob.journal.ui.components.EmptyState
import com.claustrophob.journal.ui.components.GradeBadge
import com.claustrophob.journal.ui.components.JournalScaffold
import com.claustrophob.journal.ui.components.ListSkeleton
import com.claustrophob.journal.ui.components.Pill
import com.claustrophob.journal.ui.components.RowDivider
import com.claustrophob.journal.ui.components.StatusBanner
import com.claustrophob.journal.ui.components.SubjectTile
import com.claustrophob.journal.ui.components.segmentShape
import com.claustrophob.journal.ui.components.shortPersonName
import com.claustrophob.journal.ui.journalViewModel
import com.claustrophob.journal.ui.theme.extra
import com.claustrophob.journal.util.Dates
import com.claustrophob.journal.util.Downloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

private data class HomeworkTab(val title: String, val status: Int)

private val homeworkTabs = listOf(
    HomeworkTab("Актуальные", HomeworkStatus.ACTIVE),
    HomeworkTab("На проверке", HomeworkStatus.ON_REVIEW),
    HomeworkTab("Проверено", HomeworkStatus.DONE),
    HomeworkTab("Просрочено", HomeworkStatus.EXPIRED),
)

data class HomeworkListState(
    val items: List<HomeworkDto> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
)

class HomeworkViewModel(private val repository: JournalRepository) : ViewModel() {

    private val _status = MutableStateFlow(HomeworkStatus.ACTIVE)
    val status: StateFlow<Int> = _status.asStateFlow()

    private val _state = MutableStateFlow(HomeworkListState())
    val state: StateFlow<HomeworkListState> = _state.asStateFlow()

    // Сколько заданий в каждом статусе, для цифр на фильтрах.
    private val _counters = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val counters: StateFlow<Map<Int, Int>> = _counters.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    private val _submitResult = MutableStateFlow<String?>(null)
    val submitResult: StateFlow<String?> = _submitResult.asStateFlow()

    private var feed: HomeworkFeed = repository.homeworkFeed(HomeworkStatus.ACTIVE, TYPE)
    private var job: Job? = null
    private var countersJob: Job? = null

    init {
        load()
        loadCounters()
    }

    fun selectStatus(status: Int) {
        if (_status.value == status) return
        _status.value = status
        feed = repository.homeworkFeed(status, TYPE)
        _state.value = HomeworkListState()
        load()
    }

    fun load(refresh: Boolean = false) {
        job?.cancel()
        val status = _status.value
        val feed = feed
        _state.update { it.copy(refreshing = refresh, loadingMore = false, error = null) }
        if (refresh) loadCounters()

        job = viewModelScope.launch {
            // Пока идёт запрос, показываем то, что видели в прошлый раз.
            if (feed.items.isEmpty()) {
                repository.homeworkSnapshot(status, TYPE)?.let { snapshot ->
                    feed.seed(snapshot.value)
                    _state.update { it.copy(items = feed.items, loading = false) }
                }
            }
            try {
                val items = feed.refresh()
                _state.update {
                    it.copy(
                        items = items,
                        loading = false,
                        refreshing = false,
                        endReached = feed.endReached,
                    )
                }
                repository.saveHomeworkSnapshot(status, TYPE, items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = repository.describeError(e).message,
                    )
                }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.refreshing || current.loadingMore || current.endReached) return
        if (job?.isActive == true) return
        val status = _status.value
        val feed = feed
        _state.update { it.copy(loadingMore = true) }

        job = viewModelScope.launch {
            try {
                val items = feed.loadMore()
                _state.update {
                    it.copy(items = items, loadingMore = false, endReached = feed.endReached)
                }
                repository.saveHomeworkSnapshot(status, TYPE, items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update {
                    it.copy(loadingMore = false, error = repository.describeError(e).message)
                }
            }
        }
    }

    private fun loadCounters() {
        countersJob?.cancel()
        countersJob = viewModelScope.launch {
            repository.homeworkCounters().collect { result ->
                if (result is Res.Ok) {
                    _counters.value = result.data.associate { it.counterType to it.counter }
                }
            }
        }
    }

    fun submit(id: Int, answer: String, file: File?, onDone: () -> Unit) {
        _submitting.value = true
        viewModelScope.launch {
            when (val result = repository.submitHomework(id, answer, file)) {
                is Res.Ok -> {
                    _submitResult.value = "Работа отправлена"
                    onDone()
                    load(refresh = true)
                }

                is Res.Err -> _submitResult.value = result.message
            }
            _submitting.value = false
        }
    }

    fun clearSubmitResult() {
        _submitResult.value = null
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private companion object {
        const val TYPE = HomeworkType.HOMEWORK
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeworkScreen() {
    val viewModel: HomeworkViewModel = journalViewModel { container: AppContainer ->
        HomeworkViewModel(container.repository)
    }
    val state by viewModel.state.collectAsState()
    val status by viewModel.status.collectAsState()
    val counters by viewModel.counters.collectAsState()
    val submitResult by viewModel.submitResult.collectAsState()

    var selected by remember { mutableStateOf<HomeworkDto?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(submitResult) {
        submitResult?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearSubmitResult()
        }
    }

    // До конца списка пара строк — подгружаем следующую страницу.
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, state.items.size) {
        if (nearEnd) viewModel.loadMore()
    }

    LaunchedEffect(status) { listState.scrollToItem(0) }

    JournalScaffold(
        title = "Задания",
        pinned = {
            ChipRow(
                options = homeworkTabs.map { it.title },
                selected = homeworkTabs.indexOfFirst { it.status == status }.coerceAtLeast(0),
                onSelect = { viewModel.selectStatus(homeworkTabs[it].status) },
                counts = homeworkTabs.map { counters[it.status] },
                modifier = Modifier.padding(bottom = 10.dp),
            )
        },
        refreshing = state.refreshing,
        onRefresh = { viewModel.load(refresh = true) },
    ) { padding ->
        val items = state.items
        when {
            state.loading && items.isEmpty() -> Column(Modifier.padding(padding)) {
                ListSkeleton(rows = 4)
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
            ) {
                item(key = "banner") {
                    StatusBanner(
                        message = state.error,
                        offline = true,
                        onRetry = { viewModel.load(refresh = true) },
                        onDismiss = viewModel::dismissError,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }

                if (items.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.AutoMirrored.Rounded.Assignment,
                            title = emptyTitleFor(status),
                            subtitle = emptySubtitleFor(status),
                        )
                    }
                }

                // Каждое задание отдельным item, иначе LazyColumn не ленивый и не
                // понять, когда долистали до конца. На вид всё равно одна карточка.
                itemsIndexed(items, key = { _, homework -> homework.id }) { index, homework ->
                    HomeworkRow(
                        homework = homework,
                        shape = segmentShape(index, items.size),
                        showDivider = index != items.lastIndex,
                        onClick = { selected = homework },
                    )
                }

                item(key = "footer") {
                    when {
                        state.loadingMore -> Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                            )
                        }

                        state.endReached && items.size > HOMEWORK_PAGE_SIZE -> Text(
                            text = "Это все задания",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                        )
                    }
                }
            }
        }
    }

    selected?.let { homework ->
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            HomeworkDetail(
                homework = homework,
                viewModel = viewModel,
                onClose = { selected = null },
            )
        }
    }
}

@Composable
private fun HomeworkRow(
    homework: HomeworkDto,
    shape: Shape,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            SubjectTile(homework.nameSpec)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = homework.nameSpec,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = homework.theme.ifBlank { "Без названия" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DeadlinePill(homework)
                    val mark = homework.studentWork?.mark ?: 0
                    if (mark > 0) {
                        Spacer(Modifier.width(8.dp))
                        GradeBadge(value = mark.toDouble(), size = 26.dp)
                    }
                }
            }
        }
        if (showDivider) RowDivider(inset = 68)
    }
}

// Срок — самое важное, поэтому он в цветной метке. Сегодня-завтра —
// красный или жёлтый, пора делать. Дальше — просто серый.
@Composable
private fun DeadlinePill(homework: HomeworkDto) {
    val deadline = homework.completionTime.take(10)
    val day = if (deadline.isNotBlank()) Dates.humanDayShort(deadline) else ""
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val neutralBack = MaterialTheme.colorScheme.surfaceContainerHigh

    when (homework.status) {
        HomeworkStatus.DONE -> Pill("проверено", MaterialTheme.extra.success)
        HomeworkStatus.ON_REVIEW -> Pill("на проверке", MaterialTheme.extra.info)
        HomeworkStatus.EXPIRED -> Pill(
            text = if (day.isNotBlank()) "срок был $day" else "срок истёк",
            color = MaterialTheme.extra.danger,
        )

        else -> {
            val days = Dates.daysUntil(deadline)
            when {
                deadline.isBlank() || days == null -> Pill("без срока", neutral, container = neutralBack)
                days < 0 -> Pill("просрочено", MaterialTheme.extra.danger, icon = Icons.Rounded.Schedule)
                days == 0 -> Pill("сдать сегодня", MaterialTheme.extra.danger, icon = Icons.Rounded.Schedule)
                days == 1 -> Pill("сдать завтра", MaterialTheme.extra.warning, icon = Icons.Rounded.Schedule)
                days <= 3 -> Pill("до $day", MaterialTheme.extra.warning, icon = Icons.Rounded.Schedule)
                else -> Pill("до $day", neutral, container = neutralBack, icon = Icons.Rounded.Schedule)
            }
        }
    }
}

@Composable
private fun HomeworkDetail(
    homework: HomeworkDto,
    viewModel: HomeworkViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val submitting by viewModel.submitting.collectAsState()

    var answer by remember(homework.id) { mutableStateOf(homework.studentWork?.answerText.orEmpty()) }
    var pickedUri by remember(homework.id) { mutableStateOf<Uri?>(null) }
    var pickedName by remember(homework.id) { mutableStateOf("") }
    var downloading by remember(homework.id) { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        pickedUri = uri
        pickedName = uri?.let { fileNameOf(context, it) }.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SubjectTile(homework.nameSpec, size = 30.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = homework.nameSpec,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = homework.theme.ifBlank { "Без названия" },
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DeadlinePill(homework)
            if (homework.teacher.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = shortPersonName(homework.teacher),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (homework.comment.isNotBlank()) {
            SheetSection("Задание")
            Text(text = homework.comment, style = MaterialTheme.typography.bodyMedium)
        }

        if (homework.filePath.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = {
                    downloading = true
                    scope.launch {
                        val result = Downloader.fetch(
                            context,
                            Downloader.absolute(homework.filePath),
                            homework.filename,
                        )
                        downloading = false
                        result
                            .onSuccess { Downloader.open(context, it) }
                            .onFailure {
                                android.widget.Toast.makeText(
                                    context,
                                    "Не удалось скачать файл",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                    }
                },
                enabled = !downloading,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (downloading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = homework.filename.ifBlank { "Скачать материал" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        homework.teacherComment?.let { comment ->
            if (comment.message.isNotBlank() || comment.textComment.isNotBlank()) {
                SheetSection("Комментарий преподавателя")
                Text(
                    text = listOf(comment.message, comment.textComment)
                        .filter { it.isNotBlank() }
                        .joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(14.dp),
                )
            }
        }

        val mark = homework.studentWork?.mark ?: 0
        if (mark > 0) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Оценка",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                GradeBadge(value = mark.toDouble(), size = 42.dp)
            }
        }

        if (homework.status == HomeworkStatus.ACTIVE ||
            homework.status == HomeworkStatus.EXPIRED
        ) {
            SheetSection("Ваш ответ")
            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it },
                placeholder = { Text("Комментарий к работе") },
                shape = RoundedCornerShape(14.dp),
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { picker.launch("*/*") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Icon(Icons.Rounded.AttachFile, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(pickedName.ifBlank { "Прикрепить файл" }, maxLines = 1)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        val file = pickedUri?.let { copyToCache(context, it, pickedName) }
                        viewModel.submit(homework.id, answer, file, onClose)
                    }
                },
                enabled = !submitting && (answer.isNotBlank() || pickedUri != null),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.AutoMirrored.Rounded.Send, null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("Отправить работу", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun SheetSection(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
}

private fun emptyTitleFor(status: Int) = when (status) {
    HomeworkStatus.DONE -> "Проверенных работ нет"
    HomeworkStatus.ON_REVIEW -> "Ничего не на проверке"
    HomeworkStatus.EXPIRED -> "Просроченных нет"
    else -> "Заданий нет"
}

private fun emptySubtitleFor(status: Int) = when (status) {
    HomeworkStatus.EXPIRED -> "Все сроки соблюдены"
    HomeworkStatus.ACTIVE -> "Всё сдано — можно выдохнуть"
    else -> "Здесь появятся работы с этим статусом"
}

private fun fileNameOf(context: android.content.Context, uri: Uri): String {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && it.moveToFirst()) return it.getString(index) ?: "file"
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
}

private suspend fun copyToCache(
    context: android.content.Context,
    uri: Uri,
    name: String,
): File? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    runCatching {
        val directory = File(context.cacheDir, "uploads").apply { mkdirs() }
        val file = File(directory, name.ifBlank { "upload.bin" })
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Не удалось прочитать файл")
        file
    }.getOrNull()
}
