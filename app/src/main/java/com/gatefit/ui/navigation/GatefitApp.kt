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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gatefit.app.AppContainer
import com.gatefit.domain.model.FacilityCategory
import com.gatefit.ui.components.LocalDrivingRestrictions
import com.gatefit.ui.feature.analysis.AnalysisScreen
import com.gatefit.ui.feature.entrance.EntranceDetailScreen
import com.gatefit.ui.feature.home.HomeScreen
import com.gatefit.ui.feature.navigation.NavigationHandoffScreen
import com.gatefit.ui.feature.report.ReportScreen
import com.gatefit.ui.feature.saved.SavedPlacesScreen
import com.gatefit.ui.feature.search.SearchScreen
import com.gatefit.ui.feature.splash.SplashDestination
import com.gatefit.ui.feature.splash.SplashScreen
import com.gatefit.ui.feature.vehicle.VehicleSetupScreen

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
        composable(
            route = Routes.VEHICLE_SETUP,
            arguments = listOf(navArgument(Routes.ARG_ONBOARDING) { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            val onboarding = entry.arguments?.getBoolean(Routes.ARG_ONBOARDING) ?: false
            VehicleSetupScreen(
                onboarding = onboarding,
                onSaved = { if (onboarding) navController.enterHome() else navController.popBackStack() },
                onBack = { navController.popBackStack() },
                onSkip = { navController.enterHome() },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onSearch = { category -> navController.open(Routes.SEARCH, Routes.search(category?.name)) },
                onOpenFacility = { id -> navController.open(Routes.ANALYSIS, Routes.analysis(id)) },
                onOpenVehicleSetup = {
                    navController.open(Routes.VEHICLE_SETUP, Routes.vehicleSetup(onboarding = false))
                },
                onOpenSettings = { navController.open(Routes.SETTINGS, Routes.SETTINGS) },
                onOpenSavedPlaces = { kind -> navController.open(Routes.SAVED_PLACES, Routes.savedPlaces(kind)) },
            )
        }
        composable(
            route = Routes.SEARCH,
            arguments = listOf(navArgument(Routes.ARG_CATEGORY) { type = NavType.StringType; nullable = true }),
        ) { entry ->
            val category = entry.arguments?.getString(Routes.ARG_CATEGORY)
                ?.let { name -> FacilityCategory.entries.firstOrNull { it.name == name } }
            SearchScreen(
                initialCategory = category,
                onOpenFacility = { id -> navController.open(Routes.ANALYSIS, Routes.analysis(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ANALYSIS) { entry ->
            AnalysisScreen(
                facilityId = entry.arguments?.getString(Routes.ARG_FACILITY_ID).orEmpty(),
                onOpenEntrance = { id -> navController.open(Routes.ENTRANCE_DETAIL, Routes.entranceDetail(id)) },
                onNavigate = { id -> navController.open(Routes.NAVIGATION_HANDOFF, Routes.navigationHandoff(id)) },
                onOpenVehicleSetup = {
                    navController.open(Routes.VEHICLE_SETUP, Routes.vehicleSetup(onboarding = false))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ENTRANCE_DETAIL) { entry ->
            val entranceId = entry.arguments?.getString(Routes.ARG_ENTRANCE_ID).orEmpty()
            EntranceDetailScreen(
                entranceId = entranceId,
                onNavigate = { id -> navController.open(Routes.NAVIGATION_HANDOFF, Routes.navigationHandoff(id)) },
                onReport = { id -> navController.open(Routes.REPORT, Routes.report(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.NAVIGATION_HANDOFF) { entry ->
            NavigationHandoffScreen(
                entranceId = entry.arguments?.getString(Routes.ARG_ENTRANCE_ID).orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.REPORT) { entry ->
            ReportScreen(
                entranceId = entry.arguments?.getString(Routes.ARG_ENTRANCE_ID).orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SAVED_PLACES) { entry ->
            SavedPlacesScreen(
                kind = entry.arguments?.getString(Routes.ARG_KIND).orEmpty(),
                onOpenFacility = { id -> navController.open(Routes.ANALYSIS, Routes.analysis(id)) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun NavHostController.leaveSplash(destination: SplashDestination) {
    val route = when (destination) {
        SplashDestination.VEHICLE_SETUP -> Routes.vehicleSetup(onboarding = true)
        SplashDestination.HOME -> Routes.HOME
    }
    navigate(route) { popUpTo(Routes.SPLASH) { inclusive = true } }
}

private fun NavHostController.enterHome() {
    navigate(Routes.HOME) { popUpTo(graph.id) { inclusive = true } }
}

// 화면이 아직 등록되지 않은 경로로는 이동하지 않음
private fun NavHostController.open(pattern: String, route: String) {
    if (graph.none { it.route == pattern }) return
    navigate(route) { launchSingleTop = true }
}
