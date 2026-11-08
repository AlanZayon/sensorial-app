package com.sensorial.data.spotify

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.sensorial.domain.feed.PlaybackMode
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import com.sensorial.domain.provider.PlaybackEvent
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/**
 * Prefer Spotify App Remote (full track, Premium). Fall back to 30s preview via ExoPlayer.
 */
class HybridPlaybackController(
    context: Context,
    private val config: SpotifyConfig,
) {
    private val appContext = context.applicationContext
    private var hostActivity: WeakReference<Activity>? = null
    private var appRemote: SpotifyAppRemote? = null
    private var exoPlayer: ExoPlayer? = null
    private var playerStateSubscription: Subscription<PlayerState>? = null

    @Volatile
    var mode: PlaybackMode = PlaybackMode.None
        private set

    @Volatile
    var lastRemoteError: String? = null
        private set

    private val trackCache = mutableMapOf<String, Track>()
    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

    @Volatile
    private var expectedTrackId: String? = null

    @Volatile
    private var ignoreExternalUntilMs: Long = 0L

    @Volatile
    private var lastEmittedTrackId: String? = null

    fun attachHost(activity: Activity) {
        hostActivity = WeakReference(activity)
    }

    fun detachHost() {
        hostActivity = null
    }

    fun remember(track: Track) {
        trackCache[track.id.value] = track
    }

    fun rememberAll(tracks: Collection<Track>) {
        tracks.forEach { remember(it) }
    }

    suspend fun authorizeRemote(showAuth: Boolean = false): Result<Unit> = withContext(Dispatchers.Main) {
        val activity = hostActivity?.get()
            ?: return@withContext Result.failure(
                IllegalStateException("Activity ausente para autorizar o Spotify Player"),
            )
        val remote = connectRemote(activity, showAuth = showAuth)
        if (remote != null) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException(
                    lastRemoteError
                        ?: "Não foi possível conectar o App Remote. Abra o Spotify e tente de novo.",
                ),
            )
        }
    }

    suspend fun play(trackId: String) = withContext(Dispatchers.Main) {
        expectedTrackId = trackId
        // Longer window: play is fire-and-forget and Spotify can take a moment to switch.
        ignoreExternalUntilMs = System.currentTimeMillis() + 4_000L
        lastEmittedTrackId = trackId
        val track = trackCache[trackId]
        val remoteOk = tryPlayRemote(trackId)
        if (remoteOk) {
            stopPreview()
            mode = PlaybackMode.SpotifyRemote
            return@withContext
        }
        val preview = track?.previewUrl
        if (!preview.isNullOrBlank()) {
            playPreview(preview)
            mode = PlaybackMode.Preview
        } else {
            mode = PlaybackMode.None
            val remoteHint = lastRemoteError?.let { " App Remote: $it." }.orEmpty()
            error(
                "Faixa sem preview.$remoteHint " +
                    "Autorize a reprodução (App Remote) com o Spotify aberto na conta Premium.",
            )
        }
    }

    suspend fun pause() = withContext(Dispatchers.Main) {
        when (mode) {
            PlaybackMode.SpotifyRemote -> appRemote?.playerApi?.pause()
            PlaybackMode.Preview -> exoPlayer?.pause()
            PlaybackMode.None -> Unit
        }
    }

    suspend fun resume() = withContext(Dispatchers.Main) {
        when (mode) {
            PlaybackMode.SpotifyRemote -> appRemote?.playerApi?.resume()
            PlaybackMode.Preview -> exoPlayer?.play()
            PlaybackMode.None -> Unit
        }
    }

    /**
     * After backgrounding, PlayerState events are often missed. Reconnect if needed,
     * resubscribe, and force-emit the track Spotify is on right now.
     */
    suspend fun reconcilePlayerState() = withContext(Dispatchers.Main) {
        if (mode == PlaybackMode.Preview) return@withContext
        val activity = hostActivity?.get()
        val remote = appRemote?.takeIf { it.isConnected }
            ?: connectRemote(activity, showAuth = false)
            ?: return@withContext
        subscribeToPlayerState(remote)
        suspendCancellableCoroutine { cont ->
            remote.playerApi.getPlayerState()
                .setResultCallback { state ->
                    handlePlayerState(state, force = true)
                    if (cont.isActive) cont.resume(Unit)
                }
                .setErrorCallback { error ->
                    Log.w(TAG, "reconcile getPlayerState failed", error)
                    if (cont.isActive) cont.resume(Unit)
                }
        }
    }

    fun release() {
        playerStateSubscription?.cancel()
        playerStateSubscription = null
        SpotifyAppRemote.disconnect(appRemote)
        appRemote = null
        exoPlayer?.release()
        exoPlayer = null
        mode = PlaybackMode.None
    }

    private suspend fun tryPlayRemote(trackId: String): Boolean {
        val activity = hostActivity?.get()
        val remote = appRemote?.takeIf { it.isConnected }
            ?: connectRemote(activity, showAuth = false)
            ?: return false
        // Fire-and-forget: waiting for Spotify's ack made every swipe feel stuck.
        remote.playerApi.play("spotify:track:$trackId")
            .setErrorCallback { error ->
                lastRemoteError = error?.message ?: "play failed"
                Log.w(TAG, "App Remote play failed", error)
            }
        return true
    }

    private suspend fun connectRemote(activity: Activity?, showAuth: Boolean): SpotifyAppRemote? {
        appRemote?.takeIf { it.isConnected }?.let { return it }
        if (!config.isConfigured) {
            lastRemoteError = "Client ID ausente"
            return null
        }
        val connectContext: Context = activity
            ?: run {
                lastRemoteError = "Sem Activity para conectar App Remote"
                return null
            }
        return suspendCancellableCoroutine { cont ->
            val params = ConnectionParams.Builder(config.clientId)
                .setRedirectUri(config.appRemoteRedirectUri)
                .showAuthView(showAuth)
                .build()
            SpotifyAppRemote.connect(
                connectContext,
                params,
                object : Connector.ConnectionListener {
                    override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                        lastRemoteError = null
                        appRemote = spotifyAppRemote
                        subscribeToPlayerState(spotifyAppRemote)
                        Log.i(TAG, "App Remote connected (showAuth=$showAuth)")
                        if (cont.isActive) cont.resume(spotifyAppRemote)
                    }

                    override fun onFailure(error: Throwable) {
                        appRemote = null
                        lastRemoteError = error.message ?: error.javaClass.simpleName
                        Log.e(TAG, "App Remote connect failed: $lastRemoteError", error)
                        if (cont.isActive) cont.resume(null)
                    }
                },
            )
        }
    }

    private fun subscribeToPlayerState(remote: SpotifyAppRemote) {
        playerStateSubscription?.cancel()
        val subscription = remote.playerApi.subscribeToPlayerState()
        subscription.setEventCallback { state ->
            handlePlayerState(state)
        }
        subscription.setErrorCallback { error ->
            Log.w(TAG, "PlayerState subscription error", error)
        }
        playerStateSubscription = subscription
    }

    private fun handlePlayerState(state: PlayerState, force: Boolean = false) {
        val uri = state.track?.uri ?: return
        if (!uri.startsWith("spotify:track:")) return
        val trackId = uri.removePrefix("spotify:track:")
        val now = System.currentTimeMillis()
        val inIgnoreWindow = !force && now < ignoreExternalUntilMs

        _events.tryEmit(PlaybackEvent.IsPaused(state.isPaused))

        if (force || trackId != lastEmittedTrackId) {
            if (inIgnoreWindow && trackId == expectedTrackId) {
                lastEmittedTrackId = trackId
                return
            }
            lastEmittedTrackId = trackId
            Log.i(TAG, "External/remote track active: $trackId (force=$force)")
            _events.tryEmit(PlaybackEvent.TrackActive(TrackId(trackId)))
        }
    }

    private fun playPreview(url: String) {
        val player = exoPlayer ?: ExoPlayer.Builder(appContext).build().also { exo ->
            exoPlayer = exo
            exo.addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            _events.tryEmit(PlaybackEvent.TrackEnded)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.w(TAG, "ExoPlayer error", error)
                    }
                },
            )
        }
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
        player.prepare()
        player.play()
    }

    private fun stopPreview() {
        exoPlayer?.stop()
    }

    companion object {
        private const val TAG = "SensorialAppRemote"
    }
}
