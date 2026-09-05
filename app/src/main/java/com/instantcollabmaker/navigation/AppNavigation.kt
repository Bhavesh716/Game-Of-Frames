package com.instantcollabmaker.navigation

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.instantcollabmaker.data.InMemoryAnalysisSession
import com.instantcollabmaker.data.export.PlaceholderCollageExporter
import com.instantcollabmaker.data.mock.DefaultCollageGenerator
import com.instantcollabmaker.data.mock.MockVideoProcessor
import com.instantcollabmaker.data.mock.SampleVideoProvider
import com.instantcollabmaker.data.video.AndroidVideoMetadataReader
import com.instantcollabmaker.domain.export.GallerySaver
import com.instantcollabmaker.domain.export.ShareManager
import com.instantcollabmaker.ui.screens.FullCollageScreen
import com.instantcollabmaker.ui.screens.HomeScreen
import com.instantcollabmaker.ui.screens.PersonCollageScreen
import com.instantcollabmaker.ui.screens.PersonDetailScreen
import com.instantcollabmaker.ui.screens.ProcessingScreen
import com.instantcollabmaker.ui.screens.ProcessingUiState
import com.instantcollabmaker.ui.screens.ProcessingViewModel
import com.instantcollabmaker.ui.screens.ResultsScreen
import com.instantcollabmaker.ui.screens.ResultsViewModel

@Composable
fun AppNavigation(navController: NavHostController) {
    val context = LocalContext.current

    // Dependencies - in Phase 1 these are created inline; Phase 2 uses DI
    val metadataReader = remember { AndroidVideoMetadataReader(context) }
    val processor = remember { MockVideoProcessor(metadataReader) }
    val session = remember { InMemoryAnalysisSession() }
    val collageGenerator = remember { DefaultCollageGenerator() }
    val exporter = remember { PlaceholderCollageExporter() }
    val gallerySaver = remember { GallerySaver(context) }
    val shareManager = remember { ShareManager(context) }

    val processingViewModel: ProcessingViewModel = viewModel {
        ProcessingViewModel(processor, session)
    }

    val resultsViewModel: ResultsViewModel = viewModel {
        ResultsViewModel(session, collageGenerator)
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            processingViewModel.processVideo(it)
            navController.navigate(Screen.Processing.route)
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onChooseVideo = { videoPicker.launch("video/*") },
                onTrySample = {
                    processingViewModel.processVideo(SampleVideoProvider.uri)
                    navController.navigate(Screen.Processing.route)
                },
            )
        }

        composable(Screen.Processing.route) {
            val uiState by processingViewModel.uiState.collectAsState()

            when (val state = uiState) {
                is ProcessingUiState.Running -> {
                    ProcessingScreen(
                        progress = state.progress,
                        stage = state.stage,
                        stats = state.stats,
                    )
                }

                is ProcessingUiState.Complete -> {
                    LaunchedEffect(Unit) {
                        navController.navigate(Screen.Results.route) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                }

                is ProcessingUiState.Failed -> {
                    LaunchedEffect(Unit) {
                        Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                        navController.popBackStack()
                    }
                }

                ProcessingUiState.Idle -> {}
            }
        }

        composable(Screen.Results.route) {
            val result by resultsViewModel.result.collectAsState()

            result?.let { analysisResult ->
                ResultsScreen(
                    result = analysisResult,
                    onBack = { navController.popBackStack() },
                    onPersonClick = { personId ->
                        navController.navigate(Screen.PersonDetail.createRoute(personId))
                    },
                    onViewFullCollage = {
                        navController.navigate(Screen.FullCollage.route)
                    },
                )
            }
        }

        composable(
            route = Screen.PersonDetail.route,
            arguments = listOf(navArgument("personId") { })
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getString("personId") ?: return@composable
            val person = resultsViewModel.getPersonById(personId) ?: return@composable

            PersonDetailScreen(
                person = person,
                onBack = { navController.popBackStack() },
                onViewCollage = { id ->
                    navController.navigate(Screen.PersonCollage.createRoute(id))
                },
            )
        }

        composable(
            route = Screen.PersonCollage.route,
            arguments = listOf(navArgument("personId") { })
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getString("personId") ?: return@composable
            val person = resultsViewModel.getPersonById(personId) ?: return@composable
            val layout = resultsViewModel.generatePersonCollage(personId) ?: return@composable

            PersonCollageScreen(
                person = person,
                layout = layout,
                onBack = { navController.popBackStack() },
                onSave = {
                    Toast.makeText(context, "Save feature coming in Phase 2", Toast.LENGTH_SHORT).show()
                },
                onShare = {
                    Toast.makeText(context, "Share feature coming in Phase 2", Toast.LENGTH_SHORT).show()
                },
            )
        }

        composable(Screen.FullCollage.route) {
            val result by resultsViewModel.result.collectAsState()
            val layout = resultsViewModel.generateFullCollage()

            if (result != null && layout != null) {
                FullCollageScreen(
                    result = result!!,
                    layout = layout,
                    onBack = { navController.popBackStack() },
                    onSave = {
                        Toast.makeText(context, "Save feature coming in Phase 2", Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        Toast.makeText(context, "Share feature coming in Phase 2", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
}
