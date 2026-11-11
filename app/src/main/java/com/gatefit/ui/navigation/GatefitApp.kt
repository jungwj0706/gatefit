package com.gatefit.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gatefit.app.AppContainer
import com.gatefit.ui.components.LocalDrivingRestrictions
import com.gatefit.ui.feature.splash.SplashDestination
import com.gatefit.ui.feature.splash.SplashScreen

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

@Composable
inline fun <reified VM : ViewModel> gatefitViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}

@Composable
fun GatefitApp(container: AppContainer) {
    val restrictions by container.drivingRestrictionRepository.restrictions.collectAsStateWithLifecycle()
    CompositionLocalProvider(
        LocalAppContainer provides container,
        LocalDrivingRestrictions provides restrictions,
    ) {
        GatefitNavHost(rememberNavController())
    }
}

// 주의 분산을 줄이기 위해 화면 전환 애니메이션 미사용
@Composable
private fun GatefitNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(onFinished = { destination -> navController.leaveSplash(destination) })
        }
    }
}

private fun NavHostController.leaveSplash(destination: SplashDestination) {
    val (pattern, route) = when (destination) {
        SplashDestination.VEHICLE_SETUP -> Routes.VEHICLE_SETUP to Routes.vehicleSetup(onboarding = true)
        SplashDestination.HOME -> Routes.HOME to Routes.HOME
    }
    // 화면이 아직 등록되지 않은 경로로는 이동하지 않음
    if (graph.none { it.route == pattern }) return
    navigate(route) { popUpTo(Routes.SPLASH) { inclusive = true } }
}
