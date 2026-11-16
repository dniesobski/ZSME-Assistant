package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.model.ThemeMode
import com.example.data.model.UserPreferences
import com.example.data.model.UserRole
import com.example.mesh.MeshRadioState
import com.example.notifications.NotificationHelper
import com.example.ui.ZsmeUiState
import com.example.ui.ZsmeViewModel
import com.example.ui.components.ClassPickerDialog
import com.example.ui.components.TeacherProfileDialog
import com.example.ui.navigation.ZsmeRoute
import com.example.ui.screens.MessengerScreen
import com.example.ui.screens.PortalWebViewScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SubstitutionsScreen
import com.example.ui.screens.TimetableScreen
import com.example.ui.theme.ZsmePlanTheme

class MainActivity : ComponentActivity() {

    private var pendingRoute by mutableStateOf<String?>(null)
    private var pendingMeshChannel by mutableStateOf<String?>(null)
    private var deepLinkNonce by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeIntentExtras(intent)
        setContent {
            val viewModel: ZsmeViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val useDarkTheme = when (uiState.preferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            ZsmePlanTheme(
                darkTheme = useDarkTheme,
                dynamicColor = uiState.preferences.useDynamicColor
            ) {
                ZsmeAppShell(
                    uiState = uiState,
                    viewModel = viewModel,
                    deepLinkRoute = pendingRoute,
                    deepLinkMeshChannel = pendingMeshChannel,
                    deepLinkNonce = deepLinkNonce,
                    onConsumeDeepLink = {
                        pendingRoute = null
                        pendingMeshChannel = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntentExtras(intent)
    }

    private fun consumeIntentExtras(intent: Intent?) {
        val route = intent?.getStringExtra(NotificationHelper.EXTRA_OPEN_ROUTE)
        val meshChannel = intent?.getStringExtra(NotificationHelper.EXTRA_MESH_CHANNEL)
        if (!route.isNullOrBlank()) {
            pendingRoute = route
            pendingMeshChannel = meshChannel
            deepLinkNonce++
            intent.removeExtra(NotificationHelper.EXTRA_OPEN_ROUTE)
            intent.removeExtra(NotificationHelper.EXTRA_MESH_CHANNEL)
        }
    }
}

private data class NavItem(
    val route: ZsmeRoute,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun ZsmeAppShell(
    uiState: ZsmeUiState,
    viewModel: ZsmeViewModel,
    deepLinkRoute: String? = null,
    deepLinkMeshChannel: String? = null,
    deepLinkNonce: Int = 0,
    onConsumeDeepLink: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    val navItems = remember {
        listOf(
            NavItem(
                route = ZsmeRoute.Substitutions,
                labelRes = R.string.nav_substitutions,
                selectedIcon = Icons.Filled.NotificationsActive,
                unselectedIcon = Icons.Outlined.NotificationsActive,
                testTag = "nav_substitutions"
            ),
            NavItem(
                route = ZsmeRoute.Timetable,
                labelRes = R.string.nav_timetable,
                selectedIcon = Icons.Filled.CalendarViewWeek,
                unselectedIcon = Icons.Outlined.CalendarViewWeek,
                testTag = "nav_timetable"
            ),
            NavItem(
                route = ZsmeRoute.Messenger,
                labelRes = R.string.nav_messenger,
                selectedIcon = Icons.Filled.Hub,
                unselectedIcon = Icons.Outlined.Hub,
                testTag = "nav_messenger"
            ),
            NavItem(
                route = ZsmeRoute.Settings,
                labelRes = R.string.nav_settings,
                selectedIcon = Icons.Filled.Tune,
                unselectedIcon = Icons.Outlined.Tune,
                testTag = "nav_settings"
            )
        )
    }

    val isWebViewRoute = currentDestination?.hasRoute<ZsmeRoute.PortalWebView>() == true
    val hasConfiguredTarget = if (uiState.preferences.userRole == UserRole.TEACHER) {
        uiState.preferences.allTeacherSchools().isNotEmpty() || uiState.preferences.teacherFullName.isNotBlank()
    } else {
        uiState.preferences.selectedClassName.isNotBlank()
    }
    val subBadgeCount = if (hasConfiguredTarget) {
        uiState.myGroupSubstitutions.size
    } else 0
    val meshBadgeCount = if (uiState.meshRadioState == MeshRadioState.ACTIVE) {
        uiState.meshPeers.size
    } else 0

    fun navigateToTopLevel(route: ZsmeRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(deepLinkNonce, deepLinkRoute) {
        if (deepLinkRoute == NotificationHelper.ROUTE_MESSENGER) {
            if (!deepLinkMeshChannel.isNullOrBlank()) {
                viewModel.selectMeshChannel(deepLinkMeshChannel)
            }
            navigateToTopLevel(ZsmeRoute.Messenger)
            onConsumeDeepLink()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp && !isWebViewRoute

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail && !isWebViewRoute) {
                    NavigationBar {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            val label = stringResource(item.labelRes)
                            val badge = when (item.route) {
                                ZsmeRoute.Substitutions -> subBadgeCount
                                ZsmeRoute.Messenger -> meshBadgeCount
                                else -> 0
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(item.route) },
                                icon = {
                                    NavItemIcon(
                                        item = item,
                                        selected = selected,
                                        label = label,
                                        badgeCount = badge
                                    )
                                },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            val label = stringResource(item.labelRes)
                            val badge = when (item.route) {
                                ZsmeRoute.Substitutions -> subBadgeCount
                                ZsmeRoute.Messenger -> meshBadgeCount
                                else -> 0
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(item.route) },
                                icon = {
                                    NavItemIcon(
                                        item = item,
                                        selected = selected,
                                        label = label,
                                        badgeCount = badge
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ZsmeRoute.Substitutions,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<ZsmeRoute.Substitutions> {
                        SubstitutionsScreen(
                            uiState = uiState,
                            onRefresh = { viewModel.refreshSubstitutions(notifyOnChange = true) },
                            onSelectTab = viewModel::setSubstitutionsTab,
                            onOpenClassPicker = { viewModel.setClassPickerOpen(true) },
                            onOpenTeacherSetup = { viewModel.setTeacherProfileDialogOpen(true) },
                            onOpenPortalWebView = {
                                val subUrl = uiState.preferences.substitutionsUrl.ifBlank {
                                    UserPreferences.DEFAULT_SUBSTITUTIONS_URL
                                }
                                navController.navigate(
                                    ZsmeRoute.PortalWebView(
                                        url = subUrl,
                                        title = "Zastępstwa ZSME",
                                        autoFillKey = if (subUrl.contains("tv.php", ignoreCase = true)) {
                                            null
                                        } else {
                                            uiState.preferences.secretariatKey
                                        }
                                    )
                                )
                            },
                            onPermissionResult = { granted ->
                                if (granted) {
                                    viewModel.setNotificationsEnabled(true)
                                }
                            }
                        )
                    }

                    composable<ZsmeRoute.Timetable> {
                        TimetableScreen(
                            uiState = uiState,
                            onSelectCategory = viewModel::selectTimetableCategory,
                            onSelectTarget = viewModel::selectTeacherOrRoom,
                            onOpenTargetPicker = viewModel::setTargetPickerOpen,
                            onSelectDay = viewModel::setDayIndex,
                            onSelectGroup = viewModel::selectGroup,
                            onRefresh = { viewModel.refreshTimetable(notifyOnChange = true) },
                            onRefreshCatalog = { viewModel.refreshClassList(silent = false) },
                            onOpenClassPicker = { viewModel.setClassPickerOpen(true) },
                            onOpenTeacherSetup = { viewModel.setTeacherProfileDialogOpen(true) },
                            onGoogleCredentialSignedIn = viewModel::setGoogleCredentialSession,
                            onGoogleCalendarAccessToken = viewModel::updateGoogleCalendarAccessToken,
                            onGoogleCredentialSignOut = viewModel::signOutGoogleCredential,
                            onOpenPortalWebView = { targetUrl ->
                                navController.navigate(
                                    ZsmeRoute.PortalWebView(
                                        url = targetUrl ?: uiState.preferences.planUrl.ifBlank { UserPreferences.DEFAULT_PLAN_URL },
                                        title = "Plan lekcji ZSME",
                                        autoFillKey = null
                                    )
                                )
                            }
                        )
                    }

                    composable<ZsmeRoute.Messenger> {
                        MessengerScreen(
                            uiState = uiState,
                            requiredBlePermissions = viewModel.meshManager.requiredPermissions(),
                            onToggleMesh = viewModel::toggleMeshRadio,
                            onPermissionsGranted = viewModel::startMeshIfPermitted,
                            onRefreshReadiness = { viewModel.refreshMeshReadiness(autoStartIfReady = true) },
                            onSelectChannel = viewModel::selectMeshChannel,
                            onCheckNickAvailability = viewModel::checkMeshNickAvailability,
                            onSelectRecipientNick = viewModel::setMeshRecipientNick,
                            onSendMessage = viewModel::sendMeshMessage,
                            onSaveNickname = viewModel::updateMeshNickname,
                            onClearHistory = viewModel::clearMeshHistory,
                            onNotificationPermissionResult = { granted ->
                                if (granted) {
                                    viewModel.setNotificationsEnabled(true)
                                }
                            }
                        )
                    }

                    composable<ZsmeRoute.Settings> {
                        SettingsScreen(
                            uiState = uiState,
                            onSelectUserRole = viewModel::setUserRole,
                            onSaveTeacherProfile = viewModel::saveTeacherProfile,
                            onAddTeacherSchool = viewModel::addTeacherSchool,
                            onUpdateTeacherSchool = viewModel::updateTeacherSchool,
                            onRemoveTeacherSchool = viewModel::removeTeacherSchool,
                            onRescanTeacherSchools = {
                                viewModel.refreshTimetable(notifyOnChange = false, forceRescan = true)
                            },
                            onOpenClassPicker = { viewModel.setClassPickerOpen(true) },
                            onSelectGroup = viewModel::selectGroup,
                            onUpdateCustomGroupFilter = viewModel::updateCustomGroupFilter,
                            onSavePortalUrls = viewModel::updatePortalUrls,
                            onResetPortalUrls = viewModel::resetPortalUrls,
                            onToggleNotifications = viewModel::setNotificationsEnabled,
                            onSelectInterval = viewModel::setCheckIntervalMinutes,
                            onCheckNow = viewModel::runImmediateSyncCheck,
                            onSendTestNotification = viewModel::sendTestNotification,
                            onSelectThemeMode = viewModel::setThemeMode,
                            onToggleDynamicColor = viewModel::setUseDynamicColor,
                            onSaveAppUpdateUrl = viewModel::updateAppUpdateUrl,
                            onCheckAppUpdate = viewModel::checkForAppUpdate,
                            onInstallDownloadedApk = viewModel::installDownloadedApk,
                            onConsumePendingApkInstall = viewModel::consumePendingApkInstall,
                            onOpenSekretariatWeb = {
                                val subUrl = uiState.preferences.substitutionsUrl.ifBlank {
                                    UserPreferences.DEFAULT_SUBSTITUTIONS_URL
                                }
                                navController.navigate(
                                    ZsmeRoute.PortalWebView(
                                        url = subUrl,
                                        title = "Zastępstwa ZSME",
                                        autoFillKey = if (subUrl.contains("tv.php", ignoreCase = true)) {
                                            null
                                        } else {
                                            uiState.preferences.secretariatKey
                                        }
                                    )
                                )
                            },
                            onOpenPlanWeb = {
                                val targetPlanUrl = if (uiState.preferences.userRole == UserRole.TEACHER) {
                                    uiState.preferences.planUrl.ifBlank { UserPreferences.DEFAULT_PLAN_URL }
                                } else {
                                    uiState.preferences.selectedClassUrl.ifBlank {
                                        uiState.preferences.planUrl.ifBlank { UserPreferences.DEFAULT_PLAN_URL }
                                    }
                                }
                                navController.navigate(
                                    ZsmeRoute.PortalWebView(
                                        url = targetPlanUrl,
                                        title = "Plan lekcji ZSME",
                                        autoFillKey = null
                                    )
                                )
                            }
                        )
                    }

                    composable<ZsmeRoute.PortalWebView> { entry ->
                        val route = entry.toRoute<ZsmeRoute.PortalWebView>()
                        PortalWebViewScreen(
                            url = route.url,
                            title = route.title,
                            autoFillKey = route.autoFillKey,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }

        if (uiState.isClassPickerOpen) {
            ClassPickerDialog(
                availableClasses = uiState.availableClasses,
                selectedClassName = uiState.preferences.selectedClassName,
                selectedGroup = uiState.preferences.selectedGroup,
                isLoading = uiState.isLoadingClasses,
                errorMessage = uiState.classesError,
                onRefreshClasses = { viewModel.refreshClassList(silent = false) },
                onSelectClass = viewModel::selectClass,
                onManualClassSubmit = viewModel::selectManualClass,
                onSelectGroup = viewModel::selectGroup,
                onDismiss = { viewModel.setClassPickerOpen(false) }
            )
        }

        if (uiState.isTeacherProfileDialogOpen) {
            TeacherProfileDialog(
                currentAbbreviation = uiState.preferences.teacherAbbreviation,
                currentFullName = uiState.preferences.teacherFullName,
                onSave = viewModel::saveTeacherProfile,
                onDismiss = { viewModel.setTeacherProfileDialogOpen(false) }
            )
        }
    }
}

@Composable
private fun NavItemIcon(
    item: NavItem,
    selected: Boolean,
    label: String,
    badgeCount: Int
) {
    val icon = if (selected) item.selectedIcon else item.unselectedIcon
    if (badgeCount > 0) {
        BadgedBox(
            badge = {
                Badge {
                    Text(if (badgeCount > 99) "99+" else "$badgeCount")
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
    } else {
        Icon(imageVector = icon, contentDescription = label)
    }
}
