package com.sensorial.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sensorial.app.ui.auth.SpotifyAuthScreen
import com.sensorial.app.ui.auth.SpotifyAuthStep
import com.sensorial.app.ui.feed.FeedScreen
import com.sensorial.app.ui.feed.FeedViewModel
import com.sensorial.app.ui.feed.FeedViewModelFactory
import com.sensorial.app.ui.liked.LikedTracksScreen
import com.sensorial.app.ui.liked.LikedTracksViewModel
import com.sensorial.app.ui.liked.LikedTracksViewModelFactory
import com.sensorial.app.ui.theme.SensorialTheme
import com.sensorial.data.spotify.SpotifyAppRemoteAuth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as SensorialApplication).container }

    private var pendingWebAuthResult: ((Intent) -> Unit)? = null
    private var onAppRemoteAuthFinished: ((Result<Unit>) -> Unit)? = null

    private val spotifyWebAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data ?: return@registerForActivityResult
        pendingWebAuthResult?.invoke(data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container.spotifyMusicProvider?.attachHost(this)
        enableEdgeToEdge()
        setContent {
            SensorialTheme {
                val webOk = container.spotifyAuth?.isAuthorized() == true
                val remoteOk = container.spotifyAppRemoteAuth?.isAuthorized() == true
                val needsSpotify = container.usesSpotify

                var ready by remember {
                    mutableStateOf(!needsSpotify || (webOk && remoteOk))
                }
                var step by remember {
                    mutableStateOf(
                        when {
                            !needsSpotify -> SpotifyAuthStep.WebApi
                            !webOk -> SpotifyAuthStep.WebApi
                            !remoteOk -> SpotifyAuthStep.AppRemote
                            else -> SpotifyAuthStep.WebApi
                        },
                    )
                }
                var authBusy by remember { mutableStateOf(false) }
                var authError by remember { mutableStateOf<String?>(null) }

                if (needsSpotify && !ready) {
                    SpotifyAuthScreen(
                        step = step,
                        busy = authBusy,
                        errorMessage = authError,
                        onConnect = {
                            authBusy = true
                            authError = null
                            when (step) {
                                SpotifyAuthStep.WebApi -> startWebAuth(
                                    onSuccess = {
                                        step = SpotifyAuthStep.AppRemote
                                        authBusy = false
                                    },
                                    onFailure = { message ->
                                        authBusy = false
                                        authError = message
                                    },
                                )
                                SpotifyAuthStep.AppRemote -> startAppRemoteAuth(
                                    onSuccess = {
                                        authBusy = false
                                        ready = true
                                    },
                                    onFailure = { message ->
                                        authBusy = false
                                        authError = message
                                    },
                                )
                            }
                        },
                    )
                } else {
                    val session = remember { container.createDiscoverySession() }
                    var showLiked by remember { mutableStateOf(false) }
                    val feedViewModel: FeedViewModel = viewModel(
                        factory = FeedViewModelFactory(
                            session = session,
                            musicProvider = container.musicProvider,
                        ),
                    )
                    val likedViewModel: LikedTracksViewModel = viewModel(
                        factory = LikedTracksViewModelFactory(
                            session = session,
                            interactionRepository = container.interactionRepository,
                        ),
                    )
                    val state by feedViewModel.state.collectAsStateWithLifecycle()
                    val likedState by likedViewModel.state.collectAsStateWithLifecycle()
                    if (showLiked) {
                        LikedTracksScreen(
                            state = likedState,
                            onBack = { showLiked = false },
                            onPlay = { track ->
                                likedViewModel.play(track)
                                showLiked = false
                            },
                            onQueryChange = likedViewModel::setQuery,
                            onToggleArtist = likedViewModel::toggleArtistFilter,
                            onToggleGenre = likedViewModel::toggleGenreFilter,
                            onSortChange = likedViewModel::setSort,
                            onClearFilters = likedViewModel::clearFilters,
                        )
                    } else {
                        FeedScreen(
                            state = state,
                            onTogglePause = feedViewModel::togglePause,
                            onGestureCommitted = feedViewModel::onGestureCommitted,
                            onLike = feedViewModel::onLike,
                            onOpenLiked = {
                                likedViewModel.refresh()
                                showLiked = true
                            },
                        )
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != SpotifyAppRemoteAuth.REQUEST_CODE) return
        val appRemoteAuth = container.spotifyAppRemoteAuth ?: return
        onAppRemoteAuthFinished?.invoke(appRemoteAuth.handleResult(resultCode, data))
    }

    override fun onDestroy() {
        container.spotifyMusicProvider?.detachHost()
        super.onDestroy()
    }

    private fun startWebAuth(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val auth = container.spotifyAuth ?: return onFailure("Spotify não configurado")
        pendingWebAuthResult = { intent ->
            lifecycleScope.launch {
                runCatching { auth.exchangeAuthorization(intent) }
                    .onSuccess { onSuccess() }
                    .onFailure { onFailure(it.message ?: "Falha na autorização Web") }
            }
        }
        spotifyWebAuthLauncher.launch(auth.createAuthIntent())
    }

    private fun startAppRemoteAuth(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val appRemoteAuth = container.spotifyAppRemoteAuth
            ?: return onFailure("App Remote auth indisponível")
        container.spotifyMusicProvider?.attachHost(this)
        onAppRemoteAuthFinished = { result ->
            result
                .onSuccess {
                    // Warm-connect without auth view (already consented via LoginActivity).
                    lifecycleScope.launch {
                        container.spotifyMusicProvider?.authorizeRemotePlayback()
                        onSuccess()
                    }
                }
                .onFailure { onFailure(it.message ?: "Falha ao autorizar reprodução") }
        }
        appRemoteAuth.openLogin(this, SpotifyAppRemoteAuth.REQUEST_CODE)
    }
}
