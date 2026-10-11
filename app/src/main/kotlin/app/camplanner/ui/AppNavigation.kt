package app.camplanner.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.camplanner.CamPlannerApp
import app.camplanner.alarms.AlarmPermissions
import app.camplanner.data.repo.Scope
import app.camplanner.designsystem.kit.AppNavBar
import app.camplanner.designsystem.kit.NavDestination
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.ui.calendar.CalendarRoute
import app.camplanner.ui.calendar.EventEditorRoute
import app.camplanner.ui.calendar.EventSheet
import app.camplanner.ui.common.Notices
import app.camplanner.ui.fitness.ExercisesRoute
import app.camplanner.ui.fitness.FitnessRoute
import app.camplanner.ui.fitness.RunsRoute
import app.camplanner.ui.more.ImportRoute
import app.camplanner.ui.more.MoreLinks
import app.camplanner.ui.more.MoreScreen
import app.camplanner.ui.more.PermissionsRoute
import app.camplanner.ui.more.PlacesRoute
import app.camplanner.ui.more.SettingsLinks
import app.camplanner.ui.more.SettingsRoute
import app.camplanner.ui.more.SubjectsRoute
import app.camplanner.ui.review.ReviewRoute
import app.camplanner.ui.stats.StatsRoute
import app.camplanner.ui.study.HomeworkEditorRoute
import app.camplanner.ui.study.StudyRoute
import app.camplanner.ui.today.TodayActions
import app.camplanner.ui.today.TodayRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val tabs = listOf(
    NavDestination("today", "Today", Icons.Outlined.WbSunny, Icons.Filled.WbSunny),
    NavDestination("calendar", "Calendar", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    NavDestination("fitness", "Fitness", Icons.Outlined.FitnessCenter, Icons.Filled.FitnessCenter),
    NavDestination("homework", "Homework", Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Filled.MenuBook),
    NavDestination("more", "More", Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz),
)

/** Routes, in one place so links and destinations can't drift apart. */
object Routes {
    fun newEvent(date: LocalDate) = "edit/event?date=${date.toEpochDay()}"
    fun editEvent(id: Long, scope: Scope) = "edit/event?id=$id&scope=${scope.name}"
    fun homework(id: Long?) = if (id == null) "homework/edit" else "homework/edit?id=$id"
}

@Composable
fun CamPlannerNav(deepLink: StateFlow<String?>, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val context = LocalContext.current
    val app = context.applicationContext as CamPlannerApp
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var sheetEvent by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastNoticeAt by remember { mutableStateOf(0L) }

    // Every screen reports through Notices; Undo runs here so it survives leaving the screen.
    LaunchedEffect(Unit) {
        Notices.notices.collect { n ->
            snackbar.currentSnackbarData?.dismiss()
            lastNoticeAt = android.os.SystemClock.uptimeMillis()
            scope.launch {
                val result = snackbar.showSnackbar(
                    n.text, actionLabel = if (n.undo != null) "Undo" else null,
                    duration = if (n.undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) n.undo?.invoke()
            }
        }
    }

    // A plain confirmation shouldn't follow you onto the next page; one with Undo stays.
    // Editors post "Saved" just as they close, so a notice younger than a second is kept.
    LaunchedEffect(route) {
        val age = android.os.SystemClock.uptimeMillis() - lastNoticeAt
        if (age > 1_000) snackbar.currentSnackbarData?.takeIf { it.visuals.actionLabel == null }?.dismiss()
    }

    // Notifications and other apps open specific pages.
    val link by deepLink.collectAsStateWithLifecycle()
    LaunchedEffect(link) {
        val target = link ?: return@LaunchedEffect
        when {
            target.startsWith("event/") -> {
                nav.goToTab("today")
                sheetEvent = target.removePrefix("event/").toLongOrNull()
            }
            target == "checkin" -> nav.navigate("review") { launchSingleTop = true }
            target in tabs.map { it.key } -> nav.goToTab(target)
            else -> nav.navigate(target) { launchSingleTop = true }
        }
        onDeepLinkHandled()
    }

    // First launch: walk through the permissions if anything essential is missing.
    LaunchedEffect(Unit) {
        if (!app.graph.settings.permissionsSeen.first()) {
            app.graph.settings.setPermissionsSeen()
            // DataStore may resume us on its own thread; navigation must happen on the main thread.
            withContext(Dispatchers.Main) {
                if (!AlarmPermissions.status(context).essentialsGranted) nav.navigate("permissions")
            }
        }
    }

    Scaffold(
        containerColor = Atlas.colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (route in tabs.map { it.key }) AppNavBar(tabs, route ?: "today", nav::goToTab)
        },
        snackbarHost = {
            val tabShown = route in tabs.map { it.key }
            // On tab pages the snackbar sits above the add button instead of covering it.
            SnackbarHost(snackbar, if (tabShown) Modifier.padding(bottom = 72.dp) else Modifier.navigationBarsPadding()) { data ->
                val c = Atlas.colors
                Snackbar(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    containerColor = c.surfaceHigh,
                    contentColor = c.text,
                    actionContentColor = c.accentText,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    action = data.visuals.actionLabel?.let { label ->
                        { androidx.compose.material3.TextButton({ data.performAction() }) { Text(label, style = Atlas.type.button, color = c.accentText) } }
                    },
                ) { Text(data.visuals.message, style = Atlas.type.reading) }
            }
        },
    ) { padding ->
        AppNavHost(nav, Modifier.padding(padding), openEvent = { sheetEvent = it })
    }

    sheetEvent?.let { id ->
        EventSheet(
            eventId = id,
            onDismiss = { sheetEvent = null },
            onEdit = { eventId, s -> nav.navigate(Routes.editEvent(eventId, s)) },
            onOpenPlaces = { nav.navigate("places") },
        )
    }
}

private fun NavHostController.goToTab(key: String) {
    navigate(key) {
        popUpTo("today") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, modifier: Modifier, openEvent: (Long) -> Unit) {
    val back: () -> Unit = { nav.popBackStack() }
    val go: (String) -> () -> Unit = { r -> { nav.navigate(r) { launchSingleTop = true } } }
    NavHost(
        navController = nav,
        startDestination = "today",
        modifier = modifier,
        enterTransition = { fadeIn(tween(180)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(180)) },
        popExitTransition = { fadeOut(tween(120)) },
    ) {
        composable("today") {
            TodayRoute(
                TodayActions(
                    openEvent = openEvent,
                    addEvent = { nav.navigate(Routes.newEvent(it)) },
                    openReview = go("review"),
                    openFitness = { nav.goToTab("fitness") },
                    openHomework = { nav.navigate(Routes.homework(it)) },
                    openSettings = go("settings"),
                    openImport = go("import"),
                    openPermissions = go("permissions"),
                ),
            )
        }
        composable("calendar") {
            CalendarRoute(onOpenEvent = openEvent, onAdd = { nav.navigate(Routes.newEvent(it)) }, onImport = go("import"))
        }
        composable("fitness") { FitnessRoute(onHistory = go("runs"), onExercises = go("exercises")) }
        composable("homework") { StudyRoute(onOpenHomework = { nav.navigate(Routes.homework(it)) }, onSubjects = go("subjects")) }
        composable("more") {
            MoreScreen(
                MoreLinks(
                    stats = go("stats"), review = go("review"), runs = go("runs"), exercises = go("exercises"),
                    import = go("import"), subjects = go("subjects"), places = go("places"),
                    settings = go("settings"), permissions = go("permissions"),
                ),
            )
        }

        composable(
            "edit/event?id={id}&date={date}&scope={scope}",
            listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                navArgument("date") { type = NavType.LongType; defaultValue = Long.MIN_VALUE },
                navArgument("scope") { type = NavType.StringType; defaultValue = Scope.THIS.name },
            ),
        ) { e ->
            val args = e.arguments!!
            EventEditorRoute(
                eventId = args.getLong("id").takeIf { it >= 0 },
                date = args.getLong("date").takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay),
                scope = Scope.valueOf(args.getString("scope") ?: Scope.THIS.name),
                onBack = back,
            )
        }
        composable("homework/edit?id={id}", listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { e ->
            HomeworkEditorRoute(e.arguments!!.getLong("id").takeIf { it >= 0 }, back)
        }
        composable("review") { ReviewRoute(back) }
        composable("stats") { StatsRoute(back) }
        composable("runs") { RunsRoute(back) }
        composable("exercises") { ExercisesRoute(back) }
        composable("import") { ImportRoute(back, onSubjects = go("subjects"), onPlaces = go("places")) }
        composable("subjects") { SubjectsRoute(back) }
        composable("places") { PlacesRoute(back) }
        composable("permissions") { PermissionsRoute(back) }
        composable("settings") {
            SettingsRoute(
                back,
                SettingsLinks(
                    permissions = go("permissions"), exercises = go("exercises"), subjects = go("subjects"),
                    places = go("places"), import = go("import"),
                ),
            )
        }
    }
}
