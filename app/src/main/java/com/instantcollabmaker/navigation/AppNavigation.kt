package com.instantcollabmaker.navigation

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.instantcollabmaker.data.InMemoryAnalysisSession
import com.instantcollabmaker.data.collage.CanvasCollageExporter
import com.instantcollabmaker.data.collage.DefaultCollageGenerator
import com.instantcollabmaker.data.pipeline.RealVideoProcessor
import com.instantcollabmaker.data.samples.SampleVideoProvider
import com.instantcollabmaker.data.video.AndroidVideoMetadataReader
import com.instantcollabmaker.domain.export.GallerySaver
import com.instantcollabmaker.domain.export.ShareManager
import com.instantcollabmaker.domain.model.CollageLayout
import com.instantcollabmaker.ui.screens.FullCollageScreen
import com.instantcollabmaker.ui.screens.FullscreenImageViewerScreen
import com.instantcollabmaker.ui.screens.HomeScreen
import com.instantcollabmaker.ui.screens.PersonCollageScreen
import com.instantcollabmaker.ui.screens.PersonDetailScreen
import com.instantcollabmaker.ui.screens.ProcessingScreen
import com.instantcollabmaker.ui.screens.ProcessingUiState
import com.instantcollabmaker.ui.screens.ProcessingViewModel
import com.instantcollabmaker.ui.screens.ResultsScreen
import com.instantcollabmaker.ui.screens.ResultsViewModel
import com.instantcollabmaker.ui.screens.SimpleCollageScreen
import com.instantcollabmaker.ui.screens.SplashScreen
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Real, on-device pipeline. No cloud calls, no server, nothing bundled from the
    // assignment's sample clips — every dependency here is created fresh per app process
    // and holds no state about any specific video.
    val metadataReader = remember { AndroidVideoMetadataReader(context) }
    val processor = remember { RealVideoProcessor(context, metadataReader) }
    val session = remember { InMemoryAnalysisSession() }
    val collageGenerator = remember { DefaultCollageGenerator() }
    val exporter = remember { CanvasCollageExporter(context) }
    val gallerySaver = remember { GallerySaver(context) }
    val shareManager = remember { ShareManager(context) }
    val sampleVideoProvider = remember { SampleVideoProvider(context) }

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

    // API 26-28 only: MediaStore inserts need this permission there (API 29+ needs none).
    var pendingSave by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingSave
        pendingSave = null
        if (granted && action != null) scope.launch { action() }
        else if (!granted) Toast.makeText(context, "Storage permission is needed to save to the gallery.", Toast.LENGTH_LONG).show()
    }

    fun saveCollage(layout: CollageLayout, fileNameStem: String) {
        val doSave: suspend () -> Unit = {
            val bitmap = exporter.export(layout, targetWidthPx = 1080)
            if (bitmap == null) {
                Toast.makeText(context, "Couldn't build the collage image.", Toast.LENGTH_LONG).show()
            } else {
                val uri = gallerySaver.save(bitmap, "${fileNameStem}_${System.currentTimeMillis()}")
                bitmap.recycle()
                Toast.makeText(
                    context,
                    if (uri != null) "Saved to Pictures/Game Of Frames" else "Couldn't save to the gallery.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        val needsLegacyPermission = Build.VERSION.SDK_INT in Build.VERSION_CODES.O..Build.VERSION_CODES.P
        val hasPermission = !needsLegacyPermission ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            scope.launch { doSave() }
        } else {
            pendingSave = doSave
            storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    fun shareCollage(layout: CollageLayout, fileNameStem: String) {
        scope.launch {
            val bitmap = exporter.export(layout, targetWidthPx = 1080)
            if (bitmap == null) {
                Toast.makeText(context, "Couldn't build the collage image.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val intent = shareManager.createShareIntent(bitmap, "$fileNameStem.jpg")
            bitmap.recycle()
            if (intent != null) {
                context.startActivity(Intent.createChooser(intent, "Share collage"))
            } else {
                Toast.makeText(context, "Couldn't prepare the collage for sharing.", Toast.LENGTH_LONG).show()
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Screen.Home.route,
            enterTransition = { fadeIn(tween(500)) + scaleIn(initialScale = 0.96f, animationSpec = tween(500)) },
            exitTransition = { fadeOut(tween(200)) },
        ) {
            HomeScreen(
                onChooseVideo = { videoPicker.launch("video/*") },
                samples = sampleVideoProvider.samples,
                onChooseSample = { sample ->
                    scope.launch {
                        val uri = sampleVideoProvider.resolveUri(sample)
                        if (uri != null) {
                            processingViewModel.processVideo(uri)
                            navController.navigate(Screen.Processing.route)
                        } else {
                            Toast.makeText(context, "Couldn't load that sample video.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onWatchItCook = { openExternalUrl(context, WATCH_IT_COOK_URL) },
                onOpenGitHub = { openExternalUrl(context, GITHUB_URL) },
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
                        onCancel = {
                            processingViewModel.cancel()
                            navController.popBackStack()
                        },
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
                    // Assignment-minimal mode: the appearance-gallery detail screen is
                    // intentionally no longer opened from the results list — see
                    // ResultsScreen's own doc comment. PersonDetailScreen/its route below
                    // are untouched and still fully functional if this is ever re-enabled.
                    onPersonClick = { /* disabled for the minimal flow — see comment above */ },
                    onViewFullCollage = {
                        // Assignment-minimal mode: "Create Collage" now opens the
                        // simplified, presentation-only SimpleCollageScreen instead of the
                        // full interactive editor. The editor (Screen.FullCollage /
                        // FullCollageScreen) is untouched below and still reachable by
                        // navigating to it directly if this is ever re-enabled.
                        navController.navigate(Screen.SimpleCollage.route)
                    },
                    onRenamePerson = { personId, newName -> resultsViewModel.renamePerson(personId, newName) },
                    // Disabled along with the personal-collage-editor entry point (Part 15)
                    // — PersonCollageScreen/its route below are untouched.
                    onViewPersonCollage = { /* disabled for the minimal flow — see comment above */ },
                    onDownloadPersonCollage = { /* disabled for the minimal flow — see comment above */ },
                )
            }
        }

        composable(Screen.SimpleCollage.route) {
            val baseLayout = resultsViewModel.generateFullCollage()
            if (baseLayout != null) {
                SimpleCollageScreen(
                    baseLayout = baseLayout,
                    onBack = { navController.popBackStack() },
                    onSave = { finalLayout -> saveCollage(finalLayout, "GameOfFrames_Collage") },
                    onShare = { finalLayout -> shareCollage(finalLayout, "gameofframes_collage") },
                )
            }
        }

        composable(
            route = Screen.PersonDetail.route,
            arguments = listOf(navArgument("personId") { })
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getString("personId") ?: return@composable
            // Collected reactively (not a one-shot getPersonById snapshot) so a rename —
            // whether triggered from this screen's own pencil icon or anywhere else —
            // recomposes this screen immediately instead of only on the next navigation.
            val result by resultsViewModel.result.collectAsState()
            val person = result?.people?.find { it.id == personId } ?: return@composable

            PersonDetailScreen(
                person = person,
                onBack = { navController.popBackStack() },
                onRename = { newName -> resultsViewModel.renamePerson(personId, newName) },
                onViewCollage = { id ->
                    navController.navigate(Screen.PersonCollage.createRoute(id))
                },
                onOpenAppearance = { appearanceIndex ->
                    navController.navigate(Screen.FullscreenAlbum.createRoute(personId, appearanceIndex))
                },
            )
        }

        composable(
            route = Screen.FullscreenAlbum.route,
            arguments = listOf(navArgument("personId") { }, navArgument("appearanceIndex") { type = NavType.IntType })
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getString("personId") ?: return@composable
            val appearanceIndex = backStackEntry.arguments?.getInt("appearanceIndex") ?: return@composable
            val result by resultsViewModel.result.collectAsState()
            val person = result?.people?.find { it.id == personId } ?: return@composable
            val appearance = person.appearances.find { it.index == appearanceIndex } ?: return@composable

            FullscreenImageViewerScreen(
                frame = appearance.bestFrame,
                fileNameStem = "GameOfFrames_${person.displayName.replace(" ", "")}_${appearance.index}",
                onClose = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.PersonCollage.route,
            arguments = listOf(navArgument("personId") { })
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getString("personId") ?: return@composable
            val result by resultsViewModel.result.collectAsState()
            val person = result?.people?.find { it.id == personId } ?: return@composable
            val baseLayout = resultsViewModel.generatePersonCollage(personId) ?: return@composable

            PersonCollageScreen(
                person = person,
                baseLayout = baseLayout,
                onBack = { navController.popBackStack() },
                onSave = { finalLayout -> saveCollage(finalLayout, "GameOfFrames_${person.displayName.replace(" ", "")}") },
                onShare = { finalLayout -> shareCollage(finalLayout, "gameofframes_${person.displayName.replace(" ", "").lowercase()}") },
            )
        }

        composable(Screen.FullCollage.route) {
            val result by resultsViewModel.result.collectAsState()
            val baseLayout = resultsViewModel.generateFullCollage()

            if (result != null && baseLayout != null) {
                FullCollageScreen(
                    result = result!!,
                    baseLayout = baseLayout,
                    onBack = { navController.popBackStack() },
                    onSave = { finalLayout -> saveCollage(finalLayout, "GameOfFrames_Collage") },
                    onShare = { finalLayout -> shareCollage(finalLayout, "gameofframes_collage") },
                )
            }
        }
    }
}

private const val WATCH_IT_COOK_URL = "https://drive.google.com/file/d/1VjfmeBIGMnrp8xvqm72w7DvdRrBEJ2W6/view?usp=sharing"
private const val GITHUB_URL = "https://github.com/Bhavesh716/Game-Of-Frames"

/** Opens [url] in whatever the device considers its default browser/handler — the
 * standard external-intent pattern, never an in-app WebView. */
private fun openExternalUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open this link.", Toast.LENGTH_LONG).show()
    }
}
