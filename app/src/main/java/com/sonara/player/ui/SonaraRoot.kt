package com.sonara.player.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sonara.player.SonaraApp
import com.sonara.player.data.Track
import com.sonara.player.playback.PlayerController
import com.sonara.player.playback.PlayerUiState
import com.sonara.player.ui.components.LocalFx
import com.sonara.player.ui.components.LocalMessage
import com.sonara.player.ui.components.LocalPlayer
import com.sonara.player.ui.components.LocalRequestAudioAccess
import com.sonara.player.ui.components.LocalTrackMenu
import com.sonara.player.ui.components.LocalUiPrefs
import com.sonara.player.ui.home.HomeScreen
import com.sonara.player.ui.library.FAVORITES_ID
import com.sonara.player.ui.library.LibraryScreen
import com.sonara.player.ui.library.PlaylistDetailScreen
import com.sonara.player.ui.player.MiniPlayer
import com.sonara.player.ui.player.NowPlayingScreen
import com.sonara.player.ui.player.TrackDetailsDialog
import com.sonara.player.ui.search.SearchScreen
import com.sonara.player.ui.settings.LegalScreen
import com.sonara.player.ui.settings.SettingsScreen
import com.sonara.player.ui.settings.WelcomeScreen
import com.sonara.player.util.BatteryOptimization
import com.sonara.player.util.findActivity
import kotlinx.coroutines.launch

private const val ROUTE_HOME = "home"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_SETTINGS = "settings"

private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED

private fun permissionsToRequest(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    context.startActivity(intent)
}

private fun NavController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun SonaraRoot(appVm: AppViewModel, player: PlayerController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val onboardingDone by appVm.onboardingDone.collectAsStateWithLifecycle()
    val playerState by player.state.collectAsStateWithLifecycle()
    val favorites by appVm.favorites.collectAsStateWithLifecycle()
    val playlists by appVm.playlists.collectAsStateWithLifecycle()
    val uiPrefs by appVm.uiPrefs.collectAsStateWithLifecycle()
    val batteryHintShown by appVm.batteryHintShown.collectAsStateWithLifecycle()
    val fx = remember { (context.applicationContext as SonaraApp).container.fx }

    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var playlistPickerTrack by remember { mutableStateOf<Track?>(null) }
    var detailsTrack by remember { mutableStateOf<Track?>(null) }

    // Connect to the playback service as early as possible so the UI can show what's already playing.
    LaunchedEffect(Unit) { player.connect() }

    val showMessage: (String) -> Unit = remember {
        { msg ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(msg)
            }
        }
    }

    // ----- permissions -----
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val granted = hasAudioPermission(context)
        appVm.onPermissionResult(granted)
        if (!granted) {
            val activity = context.findActivity()
            // "Don't ask again" was chosen (or the system won't show the dialog): send the user to Settings.
            if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, audioPermission())) {
                showMessage("Turn on Music and audio access in Settings to see your songs")
                openAppSettings(context)
            }
        }
    }
    val requestAudioAccess: () -> Unit = remember(permissionLauncher) {
        { permissionLauncher.launch(permissionsToRequest()) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        appVm.onPermissionResult(hasAudioPermission(context))
    }

    // ----- playback errors -> snackbar -----
    LaunchedEffect(playerState.error) {
        val err = playerState.error
        if (err != null) {
            showMessage(err)
            player.clearError()
        }
    }

    // Some phones restrict background apps. Once, after the first song starts, tell the user how to fix it.
    LaunchedEffect(playerState.isPlaying, batteryHintShown) {
        if (playerState.isPlaying && batteryHintShown == false) {
            appVm.markBatteryHintShown()
            if (!BatteryOptimization.isIgnoring(context)) {
                showMessage("To keep music going with the screen off, allow background use in Settings > Playback")
            }
        }
    }

    val openMenu: (Track) -> Unit = remember { { track -> menuTrack = track } }

    CompositionLocalProvider(
        LocalPlayer provides player,
        LocalTrackMenu provides openMenu,
        LocalMessage provides showMessage,
        LocalRequestAudioAccess provides requestAudioAccess,
        LocalFx provides fx,
        LocalUiPrefs provides uiPrefs,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (onboardingDone) {
                null -> Unit // still reading saved settings; the splash screen covers this
                false -> WelcomeScreen(
                    onGetStarted = {
                        requestAudioAccess()
                        appVm.finishOnboarding()
                    },
                    onSkip = { appVm.finishOnboarding() },
                )
                true -> MainContent(
                    appVm = appVm,
                    playerState = playerState,
                    nowPlayingOpen = nowPlayingOpen,
                    onNowPlayingOpenChange = { nowPlayingOpen = it },
                    isFavorite = { track -> favorites.any { it.id == track.id } },
                )
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        bottom = when {
                            nowPlayingOpen -> 16.dp
                            playerState.current != null -> 152.dp
                            else -> 88.dp
                        }
                    ),
            )
        }

        // ----- sheets (they float above everything, including the full-screen player) -----
        menuTrack?.let { track ->
            TrackMenuSheet(
                track = track,
                isFavorite = favorites.any { it.id == track.id },
                onDismiss = { menuTrack = null },
                onAddToPlaylist = { playlistPickerTrack = track },
                onToggleFavorite = { appVm.toggleFavorite(track) },
                onStartMix = { appVm.startMix(track) { list -> player.playQueue(list, 0) } },
                onGoToArtist = {
                    nowPlayingOpen = false
                    appVm.requestSearch(track.artist)
                },
                onGoToAlbum = {
                    nowPlayingOpen = false
                    appVm.requestSearch(track.album)
                },
                onShowDetails = { detailsTrack = track },
            )
        }
        detailsTrack?.let { track ->
            TrackDetailsDialog(
                track = track,
                specs = if (playerState.current?.id == track.id) playerState.audioSpecs else null,
                onDismiss = { detailsTrack = null },
            )
        }
        playlistPickerTrack?.let { track ->
            AddToPlaylistSheet(
                track = track,
                playlists = playlists,
                appVm = appVm,
                onDismiss = { playlistPickerTrack = null },
            )
        }
    }
}

@Composable
private fun MainContent(
    appVm: AppViewModel,
    playerState: PlayerUiState,
    nowPlayingOpen: Boolean,
    onNowPlayingOpenChange: (Boolean) -> Unit,
    isFavorite: (Track) -> Boolean,
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val topLevel = route == ROUTE_HOME || route == ROUTE_SEARCH || route == ROUTE_LIBRARY
    val hasTrack = playerState.current != null

    // "Go to artist / album" from the track menu: show the Search tab with that text.
    val searchRequest by appVm.searchRequest.collectAsStateWithLifecycle()
    LaunchedEffect(searchRequest) {
        if (searchRequest != null) {
            onNowPlayingOpenChange(false)
            nav.navigateTopLevel(ROUTE_SEARCH)
        }
    }

    // Back closes the full-screen player first.
    BackHandler(enabled = nowPlayingOpen && hasTrack) { onNowPlayingOpenChange(false) }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column(if (topLevel) Modifier else Modifier.navigationBarsPadding()) {
                    MiniPlayer(state = playerState, onOpen = { onNowPlayingOpenChange(true) })
                    if (topLevel) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                            NavigationBarItem(
                                selected = route == ROUTE_HOME,
                                onClick = { nav.navigateTopLevel(ROUTE_HOME) },
                                icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                                label = { Text("Home") },
                            )
                            NavigationBarItem(
                                selected = route == ROUTE_SEARCH,
                                onClick = { nav.navigateTopLevel(ROUTE_SEARCH) },
                                icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                label = { Text("Search") },
                            )
                            NavigationBarItem(
                                selected = route == ROUTE_LIBRARY,
                                onClick = { nav.navigateTopLevel(ROUTE_LIBRARY) },
                                icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
                                label = { Text("Library") },
                            )
                        }
                    }
                }
            },
        ) { inner ->
            NavHost(
                navController = nav,
                startDestination = ROUTE_HOME,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(ROUTE_HOME) {
                    HomeScreen(
                        padding = inner,
                        appVm = appVm,
                        onOpenSettings = { nav.navigate(ROUTE_SETTINGS) },
                        onOpenFavorites = { nav.navigate("playlist/$FAVORITES_ID") },
                        onOpenLibrary = { nav.navigateTopLevel(ROUTE_LIBRARY) },
                    )
                }
                composable(ROUTE_SEARCH) { SearchScreen(padding = inner, appVm = appVm) }
                composable(ROUTE_LIBRARY) {
                    LibraryScreen(
                        padding = inner,
                        appVm = appVm,
                        onOpenPlaylist = { id -> nav.navigate("playlist/$id") },
                    )
                }
                composable("playlist/{id}") { backStackEntry ->
                    PlaylistDetailScreen(
                        playlistId = backStackEntry.arguments?.getString("id") ?: FAVORITES_ID,
                        padding = inner,
                        appVm = appVm,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(ROUTE_SETTINGS) {
                    SettingsScreen(
                        padding = inner,
                        appVm = appVm,
                        onBack = { nav.popBackStack() },
                        onOpenLegal = { doc -> nav.navigate("legal/$doc") },
                    )
                }
                composable("legal/{doc}") { backStackEntry ->
                    LegalScreen(
                        doc = backStackEntry.arguments?.getString("doc") ?: "privacy",
                        padding = inner,
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = nowPlayingOpen && hasTrack,
            enter = slideInVertically(tween(300)) { it } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(250)) { it } + fadeOut(tween(250)),
        ) {
            val current = playerState.current
            NowPlayingScreen(
                state = playerState,
                isFavorite = current != null && isFavorite(current),
                onToggleFavorite = { appVm.toggleFavorite(it) },
                onClose = { onNowPlayingOpenChange(false) },
                onSetLyricsScale = { appVm.setLyricsScale(it) },
                onSetLyricsOffset = { appVm.setLyricsOffset(it) },
                onEnableLyrics = { appVm.setLyricsEnabled(true) },
            )
        }
    }
}
