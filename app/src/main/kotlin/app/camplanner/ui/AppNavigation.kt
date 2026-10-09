package app.camplanner.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import app.camplanner.designsystem.components.AtlasNavBar
import app.camplanner.designsystem.components.NavItem
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.designsystem.theme.AtlasMotion
import app.camplanner.ui.checkin.CheckInRoute
import app.camplanner.ui.event.EventRoute
import app.camplanner.ui.fitness.ExercisesRoute
import app.camplanner.ui.fitness.FitnessHistoryRoute
import app.camplanner.ui.fitness.FitnessRoute
import app.camplanner.ui.homework.HomeworkEditRoute
import app.camplanner.ui.homework.HomeworkRoute
import app.camplanner.ui.importics.ImportRoute
import app.camplanner.ui.locations.LocationsRoute
import app.camplanner.ui.more.MoreDestinations
import app.camplanner.ui.more.MoreScreen
import app.camplanner.ui.permissions.PermissionsRoute
import app.camplanner.ui.settings.SettingsRoute
import app.camplanner.ui.stats.StatsRoute
import app.camplanner.ui.subjects.SubjectsRoute
import app.camplanner.ui.today.TodayRoute
import app.camplanner.ui.week.WeekRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private val tabs = listOf(
    NavItem("today", "Today"),
    NavItem("week", "Week"),
    NavItem("fitness", "Fitness"),
    NavItem("homework", "Homework"),
    NavItem("more", "Index"),
)

@Composable
fun CamPlannerNav(deepLink: StateFlow<String?>, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val context = LocalContext.current
    val app = context.applicationContext as CamPlannerApp

    // Notifications open specific pages.
    val link by deepLink.collectAsStateWithLifecycle()
    LaunchedEffect(link) {
        link?.let { target ->
            nav.navigate(target) { launchSingleTop = true }
            onDeepLinkHandled()
        }
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

    Column(Modifier.fillMaxSize().background(Atlas.colors.background)) {
        Box(Modifier.weight(1f)) {
            AppNavHost(nav)
        }
        if (route in tabs.map { it.key }) {
            AtlasNavBar(
                items = tabs,
                selected = route ?: "today",
                onSelect = { key ->
                    nav.navigate(key) {
                        popUpTo("today") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(
        navController = nav,
        startDestination = "today",
        enterTransition = { fadeIn(tween(AtlasMotion.MEDIUM_MS)) },
        exitTransition = { fadeOut(tween(AtlasMotion.MEDIUM_MS)) },
        popEnterTransition = { fadeIn(tween(AtlasMotion.MEDIUM_MS)) },
        popExitTransition = { fadeOut(tween(AtlasMotion.MEDIUM_MS)) },
    ) {
        composable("today") {
            TodayRoute(
                date = null,
                onOpenEvent = { nav.navigate("event/$it") },
                onOpenCheckIn = { nav.navigate("checkin") },
                onFixPermissions = { nav.navigate("permissions") },
            )
        }
        composable("day/{epochDay}", listOf(navArgument("epochDay") { type = NavType.LongType })) { e ->
            TodayRoute(
                date = LocalDate.ofEpochDay(e.arguments!!.getLong("epochDay")),
                onOpenEvent = { nav.navigate("event/$it") },
                onOpenCheckIn = { nav.navigate("checkin") },
                onFixPermissions = { nav.navigate("permissions") },
            )
        }
        composable("week") {
            WeekRoute(
                onOpenDay = { nav.navigate("day/${it.toEpochDay()}") },
                onOpenEvent = { nav.navigate("event/$it") },
                onAdd = { nav.navigate("event/new/${it.toEpochDay()}") },
            )
        }
        composable("event/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
            EventRoute(e.arguments!!.getLong("id"), null, back, onOpenLocations = { nav.navigate("locations") })
        }
        composable("event/new/{epochDay}", listOf(navArgument("epochDay") { type = NavType.LongType })) { e ->
            EventRoute(0, LocalDate.ofEpochDay(e.arguments!!.getLong("epochDay")), back, onOpenLocations = { nav.navigate("locations") })
        }
        composable("fitness") {
            FitnessRoute(onOpenHistory = { nav.navigate("fitness/history") }, onManageExercises = { nav.navigate("exercises") })
        }
        composable("fitness/history") { FitnessHistoryRoute(back) }
        composable("exercises") { ExercisesRoute(back) }
        composable("homework") { HomeworkRoute(onOpen = { nav.navigate("homework/$it") }) }
        composable("homework/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
            HomeworkEditRoute(e.arguments!!.getLong("id"), back)
        }
        composable("checkin") { CheckInRoute(onDone = back) }
        composable("more") {
            MoreScreen(
                MoreDestinations(
                    checkIn = { nav.navigate("checkin") },
                    stats = { nav.navigate("stats") },
                    subjects = { nav.navigate("subjects") },
                    locations = { nav.navigate("locations") },
                    import = { nav.navigate("import") },
                    exercises = { nav.navigate("exercises") },
                    settings = { nav.navigate("settings") },
                    permissions = { nav.navigate("permissions") },
                ),
            )
        }
        composable("stats") { StatsRoute(back) }
        composable("subjects") { SubjectsRoute(back, onImport = { nav.navigate("import") }) }
        composable("locations") { LocationsRoute(back) }
        composable("import") { ImportRoute(back, onOpenSubjects = { nav.navigate("subjects") { popUpTo("import") { inclusive = true } } }) }
        composable("settings") {
            SettingsRoute(
                onBack = back,
                onPermissions = { nav.navigate("permissions") },
                onExercises = { nav.navigate("exercises") },
                onSubjects = { nav.navigate("subjects") },
                onLocations = { nav.navigate("locations") },
                onImport = { nav.navigate("import") },
            )
        }
        composable("permissions") { PermissionsRoute(onDone = back, onBack = back) }
    }
}
