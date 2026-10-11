package app.camplanner.ui.next

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.components.starField
import app.camplanner.designsystem.kit.AppNavBar
import app.camplanner.designsystem.kit.NavDestination
import app.camplanner.designsystem.theme.Atlas

enum class Tab { TODAY, CALENDAR, FITNESS, HOMEWORK, MORE }

@Suppress("DEPRECATION") // MenuBook moved to AutoMirrored; the plain one renders the same here.
val tabs = listOf(
    NavDestination(Tab.TODAY, "Today", Icons.Outlined.WbSunny, Icons.Filled.WbSunny),
    NavDestination(Tab.CALENDAR, "Calendar", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    NavDestination(Tab.FITNESS, "Fitness", Icons.Outlined.FitnessCenter, Icons.Filled.FitnessCenter),
    NavDestination(Tab.HOMEWORK, "Homework", Icons.Outlined.MenuBook, Icons.Filled.MenuBook),
    NavDestination(Tab.MORE, "More", Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz),
)

/**
 * A tab screen: ink ground with a faint star scatter, scrolling content, the tab bar, an optional
 * add button and a snackbar line.
 */
@Composable
fun TabScaffold(
    tab: Tab,
    onTab: (Tab) -> Unit,
    fab: (@Composable () -> Unit)? = null,
    snackbar: String? = null,
    snackbarAction: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Atlas.colors
    Scaffold(
        containerColor = c.background,
        bottomBar = { AppNavBar(tabs, tab, onTab) },
        floatingActionButton = { fab?.invoke() },
        snackbarHost = {
            if (snackbar != null) {
                Snackbar(
                    modifier = Modifier.padding(12.dp),
                    containerColor = c.surfaceHigh,
                    contentColor = c.text,
                    action = snackbarAction?.let { { Text(it, style = Atlas.type.button, color = c.accentText, modifier = Modifier.padding(end = 8.dp)) } },
                ) { Text(snackbar, style = Atlas.type.reading) }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().background(c.background).starField(c.text, starsPer10k = 0.5f)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                content = {
                    content()
                    Spacer(Modifier.height(96.dp))
                },
            )
        }
    }
}

/** Horizontal page padding for content under a [app.camplanner.designsystem.kit.LargeHeader]. */
val PagePadding = PaddingValues(horizontal = 16.dp)

@Composable
fun Paged(content: @Composable ColumnScope.() -> Unit) = Column(Modifier.padding(PagePadding), content = content)
