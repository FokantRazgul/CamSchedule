package app.camplanner.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.camplanner.CamPlannerApp
import app.camplanner.data.DataGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime

/** A ViewModel built from the process-wide data graph. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (DataGraph, CamPlannerApp) -> VM): VM {
    val app = LocalContext.current.applicationContext as CamPlannerApp
    return viewModel(key = key, factory = viewModelFactory { initializer { create(app.graph, app) } })
}

/** The local time now, then again at the start of every minute. */
fun minuteTicker(): Flow<LocalDateTime> = flow {
    while (true) {
        val now = LocalDateTime.now()
        emit(now)
        delay(((60 - now.second) * 1000L - now.nano / 1_000_000).coerceAtLeast(500))
    }
}
