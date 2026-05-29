package com.claustrophob.journal.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.claustrophob.journal.AppContainer
import com.claustrophob.journal.BuildConfig
import com.claustrophob.journal.container
import com.claustrophob.journal.data.JournalRepository
import com.claustrophob.journal.data.net.UserInfoDto
import com.claustrophob.journal.ui.LoaderViewModel
import com.claustrophob.journal.ui.components.Avatar
import com.claustrophob.journal.ui.components.GroupedList
import com.claustrophob.journal.ui.components.IconTile
import com.claustrophob.journal.ui.components.JCard
import com.claustrophob.journal.ui.components.JournalScaffold
import com.claustrophob.journal.ui.components.ListRow
import com.claustrophob.journal.ui.components.ListSkeleton
import com.claustrophob.journal.ui.components.RowDivider
import com.claustrophob.journal.ui.components.SectionHeader
import com.claustrophob.journal.ui.components.Segmented
import com.claustrophob.journal.ui.components.StatusBanner
import com.claustrophob.journal.ui.journalViewModel
import com.claustrophob.journal.ui.theme.ThemeMode
import com.claustrophob.journal.ui.theme.extra
import com.claustrophob.journal.ui.theme.tabular
import com.claustrophob.journal.work.Notifier

class ProfileViewModel(repository: JournalRepository) :
    LoaderViewModel<UserInfoDto>({ repository.profile() })

// Цвета плашек в меню. Свой у каждого раздела, как в настройках телефона.
private object MenuTint {
    val Blue = Color(0xFF2F7CF6)
    val Green = Color(0xFF12A36B)
    val Purple = Color(0xFF8B5CF6)
    val Orange = Color(0xFFF0701A)
    val Amber = Color(0xFFDB9A00)
    val Pink = Color(0xFFE5488A)
    val Gray = Color(0xFF8A8A96)
}

@Composable
fun MoreScreen(
    onOpenNews: () -> Unit,
    onOpenLeaders: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPayments: () -> Unit,
    onOpenPortfolio: () -> Unit,
) {
    val viewModel: ProfileViewModel = journalViewModel { container: AppContainer ->
        ProfileViewModel(container.repository)
    }
    val state by viewModel.state.collectAsState()
    val user = state.data

    JournalScaffold(
        title = "Ещё",
        refreshing = state.refreshing,
        onRefresh = { viewModel.load(refresh = true) },
    ) { padding ->
        LazyColumn(
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
                when {
                    state.showSkeleton -> ListSkeleton(rows = 1)
                    user != null -> ProfileCard(user, onOpenAchievements)
                }
            }

            item { SectionHeader("Учёба") }
            item {
                GroupedList {
                    MenuRow(
                        Icons.AutoMirrored.Rounded.MenuBook,
                        MenuTint.Blue,
                        "Библиотека",
                        "Уроки, книги, видео и презентации",
                        onOpenLibrary,
                    )
                    RowDivider(inset = 66)
                    MenuRow(
                        Icons.Rounded.CreditCard,
                        MenuTint.Green,
                        "Оплата обучения",
                        "График и история платежей",
                        onOpenPayments,
                    )
                    RowDivider(inset = 66)
                    MenuRow(
                        Icons.Rounded.Brush,
                        MenuTint.Purple,
                        "Портфолио",
                        "Принятые работы и заявки",
                        onOpenPortfolio,
                    )
                }
            }

            item { SectionHeader("Академия") }
            item {
                GroupedList {
                    MenuRow(Icons.Rounded.Campaign, MenuTint.Orange, "Новости", "Объявления академии", onOpenNews)
                    RowDivider(inset = 66)
                    MenuRow(Icons.Rounded.Leaderboard, MenuTint.Amber, "Рейтинг", "Группа и поток", onOpenLeaders)
                    RowDivider(inset = 66)
                    MenuRow(Icons.Rounded.EmojiEvents, MenuTint.Pink, "Достижения", null, onOpenAchievements)
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                GroupedList {
                    MenuRow(
                        Icons.Rounded.Settings,
                        MenuTint.Gray,
                        "Настройки",
                        "Тема, уведомления, аккаунт",
                        onOpenSettings,
                    )
                }
            }

            item { VersionFooter() }
        }
    }
}

@Composable
private fun ProfileCard(user: UserInfoDto, onOpenAchievements: () -> Unit) {
    JCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                url = user.photo.takeIf { it.isNotBlank() },
                name = user.fullName,
                size = 64,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(user.fullName, style = MaterialTheme.typography.titleMedium)
                val about = listOf(user.streamName, user.studyForm).filter { it.isNotBlank() }
                if (about.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = about.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileFact(
                modifier = Modifier.weight(1f),
                value = user.level.takeIf { it > 0 }?.toString() ?: "—",
                label = "уровень",
            )
            ProfileFact(
                modifier = Modifier.weight(1f),
                value = user.achievesCount.toString(),
                label = "достижений",
                onClick = onOpenAchievements,
            )
            if (user.registrationDate.length >= 4) {
                ProfileFact(
                    modifier = Modifier.weight(1f),
                    value = "с ${user.registrationDate.take(4)}",
                    label = "в академии",
                )
            }
        }
    }
}

@Composable
private fun ProfileFact(
    modifier: Modifier,
    value: String,
    label: String,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text = value, style = MaterialTheme.typography.titleMedium.tabular())
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        leading = { IconTile(icon, tint) },
        onClick = onClick,
    )
}

@Composable
private fun VersionFooter() {
    Text(
        text = "Журнал ${BuildConfig.VERSION_NAME} · неофициальный клиент",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenAbout: () -> Unit) {
    val context = LocalContext.current
    val appContainer = context.container
    val settings by appContainer.settings.settings.collectAsState()
    var confirmSignOut by remember { mutableStateOf(false) }
    val notificationsAllowed = remember { Notifier.canNotify(context) }

    val openSystemSettings = {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            )
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        Unit
    }

    JournalScaffold(title = "Настройки", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeader("Оформление") }
            item {
                JCard {
                    Text("Тема", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(10.dp))
                    Segmented(
                        options = ThemeMode.entries.map(::themeLabel),
                        selected = ThemeMode.entries.indexOf(settings.themeMode),
                        onSelect = { index ->
                            appContainer.settings.update { it.copy(themeMode = ThemeMode.entries[index]) }
                        },
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    GroupedList {
                        ToggleRow(
                            icon = Icons.Rounded.Palette,
                            tint = MenuTint.Purple,
                            title = "Цвета из обоев",
                            subtitle = "Палитра подстроится под систему",
                            checked = settings.dynamicColor,
                            onChange = { value ->
                                appContainer.settings.update { it.copy(dynamicColor = value) }
                            },
                        )
                    }
                }
            }

            item { SectionHeader("Уведомления") }
            item {
                GroupedList {
                    if (!notificationsAllowed) {
                        ListRow(
                            title = "Уведомления запрещены",
                            subtitle = "Разрешите их в настройках телефона",
                            leading = {
                                IconTile(Icons.Rounded.NotificationsOff, MaterialTheme.extra.danger)
                            },
                            onClick = openSystemSettings,
                        )
                        RowDivider(inset = 66)
                    }
                    ToggleRow(
                        icon = Icons.AutoMirrored.Rounded.Assignment,
                        tint = MenuTint.Blue,
                        title = "Новые задания",
                        subtitle = "Когда преподаватель выдал работу",
                        checked = settings.notifyHomework,
                        onChange = { value ->
                            appContainer.settings.update { it.copy(notifyHomework = value) }
                        },
                    )
                    RowDivider(inset = 66)
                    ToggleRow(
                        icon = Icons.Rounded.Grade,
                        tint = MenuTint.Green,
                        title = "Новые оценки",
                        subtitle = "Только свежие, за последние две недели",
                        checked = settings.notifyMarks,
                        onChange = { value ->
                            appContainer.settings.update { it.copy(notifyMarks = value) }
                        },
                    )
                    RowDivider(inset = 66)
                    ToggleRow(
                        icon = Icons.Rounded.Campaign,
                        tint = MenuTint.Orange,
                        title = "Новости академии",
                        subtitle = "Объявления и анонсы",
                        checked = settings.notifyNews,
                        onChange = { value ->
                            appContainer.settings.update { it.copy(notifyNews = value) }
                        },
                    )
                    RowDivider(inset = 66)
                    ListRow(
                        title = "Звук и вид",
                        subtitle = "Системные настройки для каждого типа",
                        leading = { IconTile(Icons.Rounded.Tune, MenuTint.Gray) },
                        onClick = openSystemSettings,
                    )
                }
            }

            item { SectionHeader("Аккаунт") }
            item {
                GroupedList {
                    ListRow(
                        title = "Выйти из аккаунта",
                        subtitle = "Пока не выйдете, сессия продлевается сама",
                        leading = {
                            IconTile(Icons.AutoMirrored.Rounded.Logout, MaterialTheme.extra.danger)
                        },
                        titleColor = MaterialTheme.extra.danger,
                        showChevron = false,
                        onClick = { confirmSignOut = true },
                    )
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                GroupedList {
                    ListRow(
                        title = "О приложении",
                        subtitle = "Соглашение и конфиденциальность",
                        leading = { IconTile(Icons.Rounded.Info, MenuTint.Gray) },
                        onClick = onOpenAbout,
                    )
                }
            }

            item { VersionFooter() }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Выйти из аккаунта?") },
            text = {
                Text("Сохранённые данные и офлайн-копии будут удалены с устройства.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSignOut = false
                        appContainer.repository.signOut()
                    },
                ) {
                    Text("Выйти", color = MaterialTheme.extra.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Отмена") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    ListRow(
        title = title,
        subtitle = subtitle,
        leading = { IconTile(icon, tint) },
        // Сам переключатель не кликается — жмётся вся строка, так проще попасть.
        trailing = { Switch(checked = checked, onCheckedChange = null) },
        showChevron = false,
        onClick = {
            haptics.performHapticFeedback(
                if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
            )
            onChange(!checked)
        },
    )
}

private fun themeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.System -> "Как в системе"
    ThemeMode.Light -> "Светлая"
    ThemeMode.Dark -> "Тёмная"
}
