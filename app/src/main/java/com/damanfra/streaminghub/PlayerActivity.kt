package com.damanfra.streaminghub

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.MediaCodecList
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.damanfra.streaminghub.data.JellyfinException
import com.damanfra.streaminghub.data.Jellyfin
import com.damanfra.streaminghub.data.JfMediaSource
import com.damanfra.streaminghub.data.JfPlaybackReport
import com.damanfra.streaminghub.data.JfSession
import com.damanfra.streaminghub.data.JfStream
import com.damanfra.streaminghub.data.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Player embutido para o Jellyfin (Media3/ExoPlayer).
 *
 * Primeiro tenta o arquivo original (sem conversão). Legendas internas do arquivo (SRT, ASS, PGS, VobSub
 * no MKV) o próprio player lê; as externas (.srt, .sub, .ass...) o servidor entrega convertidas para SRT.
 * Se o aparelho não decodifica o vídeo ou o áudio, pede ao servidor uma versão convertida (HLS).
 * O progresso vai para o Jellyfin, que monta o "Continuar assistindo".
 */
class PlayerActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_START = "startMs"

        fun start(context: Context, itemId: String, startMs: Long = 0) {
            context.startActivity(
                Intent(context, PlayerActivity::class.java).putExtra(EXTRA_ID, itemId).putExtra(EXTRA_START, startMs)
            )
        }

        private val reports = CoroutineScope(Dispatchers.IO)
    }

    private lateinit var view: PlayerView
    private var player: ExoPlayer? = null
    private var session: JfSession? = null
    private var itemId = ""
    private var source: JfMediaSource? = null
    private var playSessionId: String? = null
    private var transcoding = false
    private var fallingBack = false
    /** Já avisou o servidor que começou (e precisa avisar quando parar). */
    private var started = false
    private var progressJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
            setShowSubtitleButton(true)
        }
        setContentView(view)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, view).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        itemId = intent.getStringExtra(EXTRA_ID).orEmpty()
        val startMs = intent.getLongExtra(EXTRA_START, 0)
        lifecycleScope.launch {
            val s = Prefs(this@PlayerActivity).jellyfin.first()
            if (s == null || itemId.isEmpty()) {
                fail("Conecte o Jellyfin em Configurações.")
                return@launch
            }
            session = s
            try {
                val info = Jellyfin.playbackInfo(s, itemId)
                val src = info.mediaSources.firstOrNull()
                    ?: throw JellyfinException("O servidor não mandou nenhum arquivo para este título.")
                source = src
                playSessionId = info.playSessionId
                play(Jellyfin.directStreamUrl(s, itemId, src, playSessionId), startMs, hls = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e.message ?: "Erro ao abrir o vídeo.")
            }
        }
    }

    private fun play(url: String, startMs: Long, hls: Boolean) {
        val s = session ?: return
        val src = source ?: return
        releasePlayer()
        transcoding = hls

        // O token vai no cabeçalho de todas as requisições: vídeo, segmentos HLS e legendas.
        val http = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Authorization" to Jellyfin.authHeader(s)))
            .setAllowCrossProtocolRedirects(true)
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguage("pt")
            .build()

        // No arquivo original o player já enxerga as legendas internas; só as externas vêm à parte.
        // Na versão convertida as internas se perdem, então todas as de texto vêm do servidor.
        val subtitles = src.mediaStreams
            .filter { it.type == "Subtitle" && it.isTextSubtitleStream && (hls || it.isExternal) }
            .map { st ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(Jellyfin.subtitleUrl(s, itemId, src.id, st.index)))
                    .setId("jf-${st.index}")
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                    .setLanguage(st.language)
                    .setLabel(subtitleLabel(st))
                    .setSelectionFlags(if (st.isForced) C.SELECTION_FLAG_FORCED else 0)
                    .build()
            }
        val item = MediaItem.Builder()
            .setUri(url)
            .apply { if (hls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .setSubtitleConfigurations(subtitles)
            .build()

        p.addListener(listener)
        p.setMediaItem(item, startMs)
        p.prepare()
        p.playWhenReady = true
        view.player = p
        view.requestFocus()
        player = p
    }

    private fun subtitleLabel(st: JfStream): String =
        st.displayTitle ?: st.title ?: st.language ?: "Legenda ${st.index}"

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> if (!started) {
                    started = true
                    report { s, r -> Jellyfin.reportStart(s, r) }
                    startProgressLoop()
                }
                Player.STATE_ENDED -> finish()
                else -> Unit
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (transcoding || tracks.groups.isEmpty()) return
            val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
            val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            // Vídeo "acima da capacidade" (ex.: 4K numa TV Full HD) costuma tocar mesmo assim; só converte se não houver decodificador.
            val videoBad = video.isNotEmpty() && video.none { it.isSupported(true) }
            val audioBad = audio.isNotEmpty() && audio.none { it.isSupported }
            if (videoBad || audioBad) fallback(videoOk = !videoBad)
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!transcoding) fallback(videoOk = false)
            else fail("Não foi possível tocar este vídeo (${error.errorCodeName}).")
        }
    }

    /** Troca o arquivo original pela versão convertida pelo servidor, no mesmo ponto do vídeo. */
    private fun fallback(videoOk: Boolean) {
        if (fallingBack) return
        fallingBack = true
        val s = session ?: return
        val src = source ?: return
        val position = player?.currentPosition ?: 0
        Toast.makeText(this, "Formato não suportado aqui; o servidor vai converter o vídeo…", Toast.LENGTH_LONG).show()
        lifecycleScope.launch {
            try {
                stopReporting()
                val info = Jellyfin.transcodeInfo(s, itemId, src.id, allowHevc = videoOk && hasHevcDecoder())
                val url = info.mediaSources.firstOrNull()?.transcodingUrl
                    ?: throw JellyfinException("O servidor não conseguiu converter este vídeo.")
                playSessionId = info.playSessionId ?: playSessionId
                play(s.server + url, position, hls = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e.message ?: "O servidor não conseguiu converter este vídeo.")
            }
        }
    }

    private fun hasHevcDecoder(): Boolean =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(MimeTypes.VIDEO_H265, ignoreCase = true) }
        }

    private fun currentReport(): JfPlaybackReport? {
        val p = player ?: return null
        val src = source ?: return null
        return JfPlaybackReport(
            itemId = itemId,
            mediaSourceId = src.id,
            playSessionId = playSessionId,
            positionTicks = p.currentPosition * Jellyfin.TICKS_PER_MS,
            isPaused = !p.isPlaying,
            playMethod = if (transcoding) "Transcode" else "DirectPlay",
        )
    }

    /** Os avisos ao servidor rodam fora da Activity para não se perderem quando ela fecha. */
    private fun report(block: suspend (JfSession, JfPlaybackReport) -> Unit) {
        val s = session ?: return
        val r = currentReport() ?: return
        reports.launch(NonCancellable) { block(s, r) }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            while (isActive) {
                delay(10_000)
                report { s, r -> Jellyfin.reportProgress(s, r) }
            }
        }
    }

    /** Avisa o servidor que parou (ele salva a posição e encerra a conversão, se houver). */
    private fun stopReporting() {
        progressJob?.cancel()
        if (started) report { s, r -> Jellyfin.reportStopped(s, r) }
        started = false
    }

    private fun releasePlayer() {
        player?.let {
            it.removeListener(listener)
            it.release()
        }
        player = null
    }

    private fun fail(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            stopReporting()
            releasePlayer()
        } else {
            // Saiu para outro app (ou o Home): pausa e guarda a posição no servidor.
            player?.pause()
            report { s, r -> Jellyfin.reportProgress(s, r) }
        }
    }

    override fun onDestroy() {
        stopReporting()
        releasePlayer()
        super.onDestroy()
    }

    // Direcional e teclas de mídia do controle vão para o player (mostrar controles, avançar, pausar).
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        view.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
}
