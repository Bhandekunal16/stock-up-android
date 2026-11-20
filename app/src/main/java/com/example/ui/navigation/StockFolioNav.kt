package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.preferences.ThemeMode
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LiveScreenerScreen
import com.example.ui.screens.PortfolioHistoryScreen
import com.example.ui.screens.PortfolioHoldingsScreen
import com.example.ui.screens.PortfolioSignalsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SnapshotDetailScreen
import com.example.ui.screens.ThemeModeSelectorRow
import com.example.ui.screens.UploadPortfolioScreen
import com.example.ui.theme.GraphiteAccentViolet
import com.example.ui.theme.GraphiteCanvas
import com.example.ui.theme.GraphiteKpiDivider
import com.example.ui.theme.GraphiteNavSurface
import com.example.ui.theme.GraphiteOnAccent
import com.example.ui.theme.GraphiteTextPrimary
import com.example.ui.theme.GraphiteTextSecondary
import com.example.ui.viewmodel.PortfolioViewModel
import com.example.ui.viewmodel.ScreenerViewModel
import com.example.ui.viewmodel.SignalsViewModel
import kotlinx.coroutines.launch

object Routes {
    const val DASHBOARD = "dashboard"
    const val UPLOAD = "upload"
    const val HOLDINGS = "holdings"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history/{date}"
    const val ANALYTICS = "analytics"
    const val PORTFOLIO_SIGNALS = "portfolio_signals"
    const val SCREENER = "screener"
    const val SETTINGS = "settings"

    fun historyDetail(date: String): String = "history/$date"
}

data class NavDestinationItem(
    val route: String,
    val label: String,
    val shortLabel: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val topDestinations = listOf(
    NavDestinationItem(
        route = Routes.DASHBOARD,
        label = "Dashboard",
        shortLabel = "Home",
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard,
    ),
    NavDestinationItem(
        route = Routes.UPLOAD,
        label = "Upload Portfolio",
        shortLabel = "Upload",
        selectedIcon = Icons.Filled.CloudUpload,
        unselectedIcon = Icons.Outlined.CloudUpload,
    ),
    NavDestinationItem(
        route = Routes.HOLDINGS,
        label = "Portfolio Holdings",
        shortLabel = "Holdings",
        selectedIcon = Icons.Filled.Layers,
        unselectedIcon = Icons.Outlined.Layers,
    ),
    NavDestinationItem(
        route = Routes.HISTORY,
        label = "Portfolio History",
        shortLabel = "History",
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
    ),
    NavDestinationItem(
        route = Routes.ANALYTICS,
        label = "Analytics",
        shortLabel = "Analytics",
        selectedIcon = Icons.Filled.Analytics,
        unselectedIcon = Icons.Outlined.Analytics,
    ),
    NavDestinationItem(
        route = Routes.PORTFOLIO_SIGNALS,
        label = "Portfolio Signals",
        shortLabel = "Signals",
        selectedIcon = Icons.Filled.Bolt,
        unselectedIcon = Icons.Outlined.Bolt,
    ),
    NavDestinationItem(
        route = Routes.SCREENER,
        label = "Live Screener",
        shortLabel = "Screener",
        selectedIcon = Icons.AutoMirrored.Outlined.ShowChart,
        unselectedIcon = Icons.AutoMirrored.Outlined.ShowChart,
    ),
    NavDestinationItem(
        route = Routes.SETTINGS,
        label = "Settings",
        shortLabel = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockFolioApp(
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onCycleThemeMode: () -> Unit,
    portfolioViewModel: PortfolioViewModel = viewModel(),
    signalsViewModel: SignalsViewModel = viewModel(),
    screenerViewModel: ScreenerViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.DASHBOARD
    val isDetailRoute = currentRoute == Routes.HISTORY_DETAIL
    val detailDate = backStackEntry?.arguments?.getString("date")

    val portfolioState by portfolioViewModel.uiState.collectAsStateWithLifecycle()
    val signalsState by signalsViewModel.uiState.collectAsStateWithLifecycle()
    val screenerState by screenerViewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        portfolioViewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }
    LaunchedEffect(Unit) {
        screenerViewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val screenTitle = when {
        isDetailRoute -> "Snapshot • ${detailDate ?: ""}"
        else -> topDestinations.firstOrNull { it.route == currentRoute }?.label ?: "StockFolio"
    }

    fun navigateToTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(Routes.DASHBOARD) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val handleRefresh: () -> Unit = {
        when (currentRoute) {
            Routes.SCREENER -> screenerViewModel.fetchSnapshot()
            Routes.PORTFOLIO_SIGNALS -> signalsViewModel.executeSignals()
            Routes.SETTINGS -> portfolioViewModel.checkServerStatus()
            Routes.HISTORY_DETAIL -> detailDate?.let { portfolioViewModel.loadSnapshotByDate(it) }
            else -> portfolioViewModel.loadAllSnapshots(isManualRefresh = true)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(GraphiteCanvas),
    ) {
        val isExpanded = maxWidth >= 840.dp
        val isDesktop = maxWidth >= 1140.dp
        val isMedium = maxWidth >= 600.dp && maxWidth < 840.dp
        val isUltraCompact = maxWidth < 360.dp
        val kpiColumns = when {
            isDesktop -> 5
            isExpanded -> 3
            isMedium -> 3
            isUltraCompact -> 1
            else -> 2
        }

        val appScaffold: @Composable () -> Unit = {
            Scaffold(
                containerColor = GraphiteCanvas,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    Column {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = GraphiteNavSurface,
                                titleContentColor = GraphiteTextPrimary,
                                navigationIconContentColor = GraphiteTextPrimary,
                                actionIconContentColor = GraphiteTextPrimary,
                            ),
                            navigationIcon = {
                                if (isDetailRoute) {
                                    IconButton(
                                        onClick = { navController.popBackStack() },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("top_bar_back"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                            contentDescription = "Navigate back",
                                        )
                                    }
                                } else if (!isExpanded) {
                                    IconButton(
                                        onClick = { scope.launch { drawerState.open() } },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("top_bar_menu"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Menu,
                                            contentDescription = "Open navigation menu",
                                        )
                                    }
                                }
                            },
                            title = {
                                Text(
                                    text = screenTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = GraphiteTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = if (!isExpanded || isDetailRoute) 4.dp else 0.dp),
                                )
                            },
                            actions = {
                                val themeIcon = when (themeMode) {
                                    ThemeMode.DARK -> Icons.Outlined.DarkMode
                                    ThemeMode.LIGHT -> Icons.Outlined.LightMode
                                    ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
                                }
                                IconButton(
                                    onClick = onCycleThemeMode,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("top_bar_theme_toggle"),
                                ) {
                                    Icon(
                                        imageVector = themeIcon,
                                        contentDescription = "Theme: ${themeMode.label}. Tap to switch.",
                                        tint = GraphiteTextSecondary,
                                    )
                                }

                                val showSpinner = (portfolioState.isRefreshing && currentRoute in listOf(
                                    Routes.DASHBOARD, Routes.HOLDINGS, Routes.HISTORY, Routes.ANALYTICS
                                )) || (screenerState.isFetchingSnapshot && currentRoute == Routes.SCREENER)

                                IconButton(
                                    onClick = handleRefresh,
                                    enabled = !showSpinner,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("top_bar_refresh"),
                                ) {
                                    if (showSpinner) {
                                        CircularProgressIndicator(
                                            color = GraphiteAccentViolet,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.Refresh,
                                            contentDescription = "Refresh",
                                            tint = GraphiteAccentViolet,
                                        )
                                    }
                                }
                            },
                        )
                        HorizontalDivider(thickness = 1.dp, color = GraphiteKpiDivider)
                    }
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 1240.dp),
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = Routes.DASHBOARD,
                        ) {
                            composable(Routes.DASHBOARD) {
                                DashboardScreen(
                                    uiState = portfolioState,
                                    kpiColumns = kpiColumns,
                                    onRetry = { portfolioViewModel.loadAllSnapshots(isManualRefresh = false) },
                                    onNavigateToUpload = { navigateToTopLevel(Routes.UPLOAD) },
                                    onNavigateToHoldings = { navigateToTopLevel(Routes.HOLDINGS) },
                                    onNavigateToSnapshot = { date ->
                                        navController.navigate(Routes.historyDetail(date))
                                    },
                                )
                            }
                            composable(Routes.UPLOAD) {
                                UploadPortfolioScreen(
                                    uploadState = portfolioState.uploadState,
                                    kpiColumns = kpiColumns,
                                    onSelectImage = portfolioViewModel::selectUploadImage,
                                    onUpload = portfolioViewModel::uploadSelectedImage,
                                    onClear = portfolioViewModel::clearUpload,
                                    onNavigateToDashboard = { navigateToTopLevel(Routes.DASHBOARD) },
                                )
                            }
                            composable(Routes.HOLDINGS) {
                                PortfolioHoldingsScreen(
                                    uiState = portfolioState,
                                    kpiColumns = kpiColumns,
                                    onRetry = { portfolioViewModel.loadAllSnapshots(isManualRefresh = false) },
                                    onNavigateToUpload = { navigateToTopLevel(Routes.UPLOAD) },
                                )
                            }
                            composable(Routes.HISTORY) {
                                PortfolioHistoryScreen(
                                    uiState = portfolioState,
                                    onRetry = { portfolioViewModel.loadAllSnapshots(isManualRefresh = false) },
                                    onNavigateToUpload = { navigateToTopLevel(Routes.UPLOAD) },
                                    onSelectSnapshot = { date ->
                                        navController.navigate(Routes.historyDetail(date))
                                    },
                                )
                            }
                            composable(
                                route = Routes.HISTORY_DETAIL,
                                arguments = listOf(navArgument("date") { type = NavType.StringType }),
                            ) { entry ->
                                val dateArg = entry.arguments?.getString("date").orEmpty()
                                SnapshotDetailScreen(
                                    date = dateArg,
                                    detailState = portfolioState.detailState,
                                    kpiColumns = kpiColumns,
                                    onLoadSnapshot = portfolioViewModel::loadSnapshotByDate,
                                    onBack = { navController.popBackStack() },
                                )
                            }
                            composable(Routes.ANALYTICS) {
                                AnalyticsScreen(
                                    uiState = portfolioState,
                                    kpiColumns = kpiColumns,
                                    onRetry = { portfolioViewModel.loadAllSnapshots(isManualRefresh = false) },
                                    onNavigateToUpload = { navigateToTopLevel(Routes.UPLOAD) },
                                )
                            }
                            composable(Routes.PORTFOLIO_SIGNALS) {
                                PortfolioSignalsScreen(
                                    uiState = signalsState,
                                    kpiColumns = kpiColumns,
                                    onExecuteSignals = signalsViewModel::executeSignals,
                                    onSelectHistoryEntry = signalsViewModel::selectHistoryEntry,
                                    onShowMessage = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    },
                                )
                            }
                            composable(Routes.SCREENER) {
                                LiveScreenerScreen(
                                    uiState = screenerState,
                                    kpiColumns = kpiColumns,
                                    onCompanyInputChange = screenerViewModel::onCompanyInputChanged,
                                    onSelectPreset = screenerViewModel::selectPreset,
                                    onSelectInterval = screenerViewModel::setPollingInterval,
                                    onFetchSnapshot = screenerViewModel::fetchSnapshot,
                                    onStartStreaming = screenerViewModel::startStreaming,
                                    onStopStreaming = screenerViewModel::stopStreaming,
                                )
                            }
                            composable(Routes.SETTINGS) {
                                SettingsScreen(
                                    uiState = portfolioState,
                                    themeMode = themeMode,
                                    onSelectThemeMode = onSelectThemeMode,
                                    onCheckServerStatus = portfolioViewModel::checkServerStatus,
                                    onResetAllSnapshots = portfolioViewModel::resetAllSnapshots,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isExpanded) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = GraphiteNavSurface,
                    contentColor = GraphiteTextPrimary,
                    modifier = Modifier
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    header = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 12.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = GraphiteAccentViolet,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "SF",
                                        color = GraphiteOnAccent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                    )
                                }
                            }
                        }
                    },
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        topDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route ||
                                (dest.route == Routes.HISTORY && isDetailRoute)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label,
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.shortLabel,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = GraphiteOnAccent,
                                    selectedTextColor = GraphiteAccentViolet,
                                    indicatorColor = GraphiteAccentViolet,
                                    unselectedIconColor = GraphiteTextSecondary,
                                    unselectedTextColor = GraphiteTextSecondary,
                                ),
                                modifier = Modifier.testTag("nav_${dest.route}"),
                            )
                        }
                    }
                }
                VerticalDivider(thickness = 1.dp, color = GraphiteKpiDivider)
                Box(modifier = Modifier.weight(1f)) {
                    appScaffold()
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = GraphiteNavSurface,
                        drawerContentColor = GraphiteTextPrimary,
                        modifier = Modifier.width(290.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = GraphiteAccentViolet,
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "SF",
                                            color = GraphiteOnAccent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "StockFolio",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = GraphiteTextPrimary,
                                    )
                                    Text(
                                        text = "Midnight Finance",
                                        color = GraphiteTextSecondary,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = GraphiteKpiDivider,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                            topDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route ||
                                    (dest.route == Routes.HISTORY && isDetailRoute)
                                NavigationDrawerItem(
                                    selected = selected,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        navigateToTopLevel(dest.route)
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = null,
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = dest.label,
                                            fontSize = 14.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        )
                                    },
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = GraphiteAccentViolet,
                                        selectedIconColor = GraphiteOnAccent,
                                        selectedTextColor = GraphiteOnAccent,
                                        unselectedContainerColor = Color.Transparent,
                                        unselectedIconColor = GraphiteTextSecondary,
                                        unselectedTextColor = GraphiteTextPrimary,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("nav_${dest.route}"),
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f, fill = false))
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = GraphiteKpiDivider,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                            Text(
                                text = "Theme",
                                color = GraphiteTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                            ThemeModeSelectorRow(
                                selectedMode = themeMode,
                                onSelect = onSelectThemeMode,
                            )
                        }
                    }
                },
            ) {
                appScaffold()
            }
        }
    }
}
