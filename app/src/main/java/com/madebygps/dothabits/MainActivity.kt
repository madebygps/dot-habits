package com.madebygps.dothabits

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.madebygps.dothabits.data.AppSettings
import com.madebygps.dothabits.ui.DetailScreen
import com.madebygps.dothabits.ui.DotTheme
import com.madebygps.dothabits.ui.EditHabitScreen
import com.madebygps.dothabits.ui.HomeScreen
import com.madebygps.dothabits.ui.MainViewModel
import com.madebygps.dothabits.ui.SettingsScreen
import com.madebygps.dothabits.ui.StatsScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val navRequests = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) navRequests.value = intent.habitExtra()
        setContent {
            val settings by vm.settingsStore.settings.collectAsStateWithLifecycle(AppSettings())
            val nav = rememberNavController()
            LifecycleResumeEffect(Unit) {
                vm.onResume()
                vm.startStepsTicker()
                onPauseOrDispose { vm.stopStepsTicker() }
            }
            val request by navRequests.collectAsStateWithLifecycle()
            LaunchedEffect(request) {
                request?.let { nav.navigate("detail/$it") { launchSingleTop = true }; navRequests.value = null }
            }
            DotTheme(highlight = Color(settings.highlight)) {
                NavHost(
                    nav,
                    startDestination = "home",
                    enterTransition = { fadeIn(animationSpec = tween(200)) },
                    exitTransition = { fadeOut(animationSpec = tween(200)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(200)) },
                    popExitTransition = { fadeOut(animationSpec = tween(200)) },
                    predictivePopEnterTransition = { fadeIn(animationSpec = tween(200)) },
                    predictivePopExitTransition = { fadeOut(animationSpec = tween(200)) },
                    sizeTransform = null,
                ) {
                    composable("home") {
                        HomeScreen(
                            vm = vm,
                            onOpen = { nav.navigate("detail/$it") },
                            onAdd = { nav.navigate("edit/0") },
                            onStats = { nav.navigate("stats") },
                            onSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        DetailScreen(
                            vm = vm,
                            habitId = it.arguments!!.getLong("id"),
                            onBack = { nav.popBackStack() },
                            onEdit = { id -> nav.navigate("edit/$id") },
                        )
                    }
                    composable("edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        EditHabitScreen(
                            vm = vm,
                            habitId = it.arguments!!.getLong("id"),
                            onDone = { nav.popBackStack() },
                            onDeleted = { nav.popBackStack("home", inclusive = false) },
                        )
                    }
                    composable("stats") { StatsScreen(vm = vm, onOpen = { id -> nav.navigate("detail/$id") }) }
                    composable("settings") { SettingsScreen(vm = vm) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.habitExtra()?.let { navRequests.value = it }
    }

    private fun Intent.habitExtra(): Long? = getLongExtra(EXTRA_HABIT_ID, -1L).takeIf { it > 0 }

    companion object {
        const val EXTRA_HABIT_ID = "habit_id"
    }
}
