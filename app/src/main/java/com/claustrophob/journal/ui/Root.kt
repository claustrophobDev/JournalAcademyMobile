package com.claustrophob.journal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.claustrophob.journal.container
import com.claustrophob.journal.data.store.SessionState
import com.claustrophob.journal.ui.screens.AboutScreen
import com.claustrophob.journal.ui.screens.AchievementsScreen
import com.claustrophob.journal.ui.screens.ConsentScreen
import com.claustrophob.journal.ui.screens.DashboardScreen
import com.claustrophob.journal.ui.screens.HomeworkScreen
import com.claustrophob.journal.ui.screens.LeadersScreen
import com.claustrophob.journal.ui.screens.LibraryScreen
import com.claustrophob.journal.ui.screens.LoginScreen
import com.claustrophob.journal.ui.screens.MoreScreen
import com.claustrophob.journal.ui.screens.NewsScreen
import com.claustrophob.journal.ui.screens.PaymentsScreen
import com.claustrophob.journal.ui.screens.PortfolioScreen
import com.claustrophob.journal.ui.screens.ProgressScreen
import com.claustrophob.journal.ui.screens.ScheduleScreen
import com.claustrophob.journal.ui.screens.SettingsScreen
import com.claustrophob.journal.ui.theme.extra
import kotlinx.coroutines.launch

object Routes {
    // все пять вкладок живут на одном экране, листаются свайпом
    const val TABS = "tabs"

    const val HOME = "home"
    const val SCHEDULE = "schedule"
    const val PROGRESS = "progress"
    const val HOMEWORK = "homework"
    const val MORE = "more"
    const val NEWS = "news"
    const val LEADERS = "leaders"
    const val ACHIEVEMENTS = "achievements"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val LIBRARY = "library"
    const val PAYMENTS = "payments"
    const val PORTFOLIO = "portfolio"
}

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

// у выбранной вкладки иконка залитая, у остальных контур
private val tabs = listOf(
    Tab(Routes.HOME, "Главная", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.SCHEDULE, "Расписание", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    Tab(Routes.PROGRESS, "Оценки", Icons.Outlined.Insights, Icons.Filled.Insights),
    Tab(
        Routes.HOMEWORK,
        "Задания",
        Icons.AutoMirrored.Outlined.Assignment,
        Icons.AutoMirrored.Filled.Assignment,
    ),
    Tab(Routes.MORE, "Ещё", Icons.Outlined.GridView, Icons.Filled.GridView),
)

@Composable
fun Root(startRoute: String?, onRouteConsumed: () -> Unit) {
    val appContainer = LocalContext.current.container
    val session by appContainer.session.state.collectAsState()
    val agreementAccepted by appContainer.settings.agreementAccepted.collectAsState()

    if (!agreementAccepted) {
        ConsentScreen(onAccept = appContainer.settings::acceptAgreement)
        return
    }

    when (session) {
        SessionState.Loading -> Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )

        SessionState.SignedOut -> LoginScreen(onSignedIn = { /* state flips to SignedIn */ })
        SessionState.SignedIn -> SignedInScaffold(startRoute, onRouteConsumed)
    }
}

private val pushEasing = FastOutSlowInEasing

@Composable
private fun SignedInScaffold(startRoute: String?, onRouteConsumed: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    fun openTab(route: String) {
        val index = tabs.indexOfFirst { it.route == route }
        if (index < 0) return
        if (navController.currentDestination?.route != Routes.TABS) {
            navController.popBackStack(Routes.TABS, inclusive = false)
        }
        scope.launch { pagerState.animateScrollToPage(index) }
    }

    // пришли из уведомления: вкладку листаем, остальное открываем поверх
    LaunchedEffect(startRoute) {
        if (startRoute == null) return@LaunchedEffect
        if (tabs.any { it.route == startRoute }) {
            openTab(startRoute)
        } else {
            navController.navigate(startRoute)
        }
        onRouteConsumed()
    }

    val showBottomBar = currentRoute == null || currentRoute == Routes.TABS

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(tween(260)) { it } + fadeIn(tween(260)),
                exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(220)),
            ) {
                BottomBar(
                    selected = pagerState.currentPage,
                    onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TABS,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            // разделы из "Ещё" заезжают справа, а то что под ними чуть уезжает влево
            enterTransition = { slideInHorizontally(tween(340, easing = pushEasing)) { it } },
            exitTransition = {
                slideOutHorizontally(tween(340, easing = pushEasing)) { -it / 5 } +
                    fadeOut(tween(340), targetAlpha = 0.6f)
            },
            popEnterTransition = {
                slideInHorizontally(tween(340, easing = pushEasing)) { -it / 5 } +
                    fadeIn(tween(340), initialAlpha = 0.6f)
            },
            popExitTransition = { slideOutHorizontally(tween(340, easing = pushEasing)) { it } },
        ) {
            composable(Routes.TABS) {
                TabsPager(
                    pagerState = pagerState,
                    openTab = ::openTab,
                    onOpen = { route -> navController.navigate(route) },
                )
            }
            composable(Routes.NEWS) { NewsScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.LEADERS) { LeadersScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.ACHIEVEMENTS) {
                AchievementsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PAYMENTS) {
                PaymentsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PORTFOLIO) {
                PortfolioScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                )
            }
            composable(Routes.ABOUT) { AboutScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

// Вкладки листаются пальцем влево-вправо, а не только тыком в нижнюю панель.
@Composable
private fun TabsPager(
    pagerState: PagerState,
    openTab: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()

    // "назад" не на главной сначала возвращает на главную, а уже потом выходит
    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        key = { tabs[it].route },
    ) { page ->
        when (tabs[page].route) {
            Routes.HOME -> DashboardScreen(
                onOpenSchedule = { openTab(Routes.SCHEDULE) },
                onOpenHomework = { openTab(Routes.HOMEWORK) },
                onOpenProgress = { openTab(Routes.PROGRESS) },
                onOpenLeaders = { onOpen(Routes.LEADERS) },
            )

            Routes.SCHEDULE -> ScheduleScreen()
            Routes.PROGRESS -> ProgressScreen()
            Routes.HOMEWORK -> HomeworkScreen()
            Routes.MORE -> MoreScreen(
                onOpenNews = { onOpen(Routes.NEWS) },
                onOpenLeaders = { onOpen(Routes.LEADERS) },
                onOpenAchievements = { onOpen(Routes.ACHIEVEMENTS) },
                onOpenSettings = { onOpen(Routes.SETTINGS) },
                onOpenLibrary = { onOpen(Routes.LIBRARY) },
                onOpenPayments = { onOpen(Routes.PAYMENTS) },
                onOpenPortfolio = { onOpen(Routes.PORTFOLIO) },
            )
        }
    }
}

// Панель своя, а не NavigationBar из material: там подписи только у выбранной
// вкладки и пилюля под иконкой, выглядит как пример из документации.
@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val badge by LocalContext.current.container.homeworkBadge.collectAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        HorizontalDivider(thickness = 0.8.dp, color = MaterialTheme.extra.hairline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(62.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selected
                BottomItem(
                    tab = tab,
                    selected = isSelected,
                    badge = if (tab.route == Routes.HOMEWORK) badge else 0,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun BottomItem(
    tab: Tab,
    selected: Boolean,
    badge: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val color by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "tab-color",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 700f),
        label = "tab-scale",
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 32.dp),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(
                imageVector = if (selected) tab.selectedIcon else tab.icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            )
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 11.dp, y = (-6).dp)
                        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                        .border(2.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                        .padding(2.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (badge > 99) "99+" else badge.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = color,
            maxLines = 1,
        )
    }
}
