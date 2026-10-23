@file:OptIn(ExperimentalSerializationApi::class)

package com.damanfra.streaminghub.data

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.TimeUnit

class JellyfinException(message: String) : Exception(message)

/** Login salvo no aparelho. [deviceId] identifica este aparelho no servidor (aparece no painel do Jellyfin). */
@Serializable
data class JfSession(
    val server: String,
    val userId: String,
    val userName: String,
    val token: String,
    val deviceId: String,
    val serverName: String = "",
)

@Serializable
data class JfUserData(
    val playbackPositionTicks: Long = 0,
    val played: Boolean = false,
    val playedPercentage: Double? = null,
)

/** Subconjunto do BaseItemDto do Jellyfin (filme, série, temporada ou episódio). */
@Serializable
data class JfItem(
    val id: String,
    val name: String? = null,
    val type: String? = null,
    val productionYear: Int? = null,
    val overview: String? = null,
    val communityRating: Double? = null,
    val officialRating: String? = null,
    val runTimeTicks: Long? = null,
    val providerIds: Map<String, String?> = emptyMap(),
    val imageTags: Map<String, String> = emptyMap(),
    val seriesId: String? = null,
    val seriesName: String? = null,
    val seriesPrimaryImageTag: String? = null,
    val seasonId: String? = null,
    val indexNumber: Int? = null,
    val parentIndexNumber: Int? = null,
    val userData: JfUserData? = null,
    val genres: List<String> = emptyList(),
) {
    val title: String get() = name ?: "Sem título"
    val isSeries: Boolean get() = type == "Series"
    val isEpisode: Boolean get() = type == "Episode"
    val resumeMs: Long get() = (userData?.playbackPositionTicks ?: 0) / Jellyfin.TICKS_PER_MS
    val runtimeMs: Long get() = (runTimeTicks ?: 0) / Jellyfin.TICKS_PER_MS

    private fun providerId(name: String) =
        providerIds.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.takeIf { it.isNotBlank() }

    val tmdbId: Int? get() = providerId("Tmdb")?.toIntOrNull()
    val imdbId: String? get() = providerId("Imdb")

    /** "T1:E3" para episódios. */
    val episodeLabel: String? get() =
        if (isEpisode && indexNumber != null) "T${parentIndexNumber ?: 1}:E$indexNumber" else null
}

@Serializable
data class JfItems(val items: List<JfItem> = emptyList(), val totalRecordCount: Int = 0)

@Serializable
data class JfStream(
    val type: String? = null,
    val index: Int = -1,
    val codec: String? = null,
    val language: String? = null,
    val title: String? = null,
    val displayTitle: String? = null,
    val isExternal: Boolean = false,
    val isTextSubtitleStream: Boolean = false,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
)

@Serializable
data class JfMediaSource(
    val id: String,
    val container: String? = null,
    val mediaStreams: List<JfStream> = emptyList(),
    val transcodingUrl: String? = null,
)

@Serializable
data class JfPlaybackInfo(val mediaSources: List<JfMediaSource> = emptyList(), val playSessionId: String? = null)

/** Corpo dos avisos de reprodução (/Sessions/Playing...). O Jellyfin usa isso para "Continuar assistindo". */
@Serializable
data class JfPlaybackReport(
    val itemId: String,
    val mediaSourceId: String,
    val playSessionId: String?,
    val positionTicks: Long,
    val isPaused: Boolean = false,
    val canSeek: Boolean = true,
    val playMethod: String = "DirectPlay",
)

@Serializable
private data class JfUser(val id: String, val name: String = "")

@Serializable
private data class JfAuthResult(val accessToken: String, val user: JfUser)

@Serializable
private data class JfPublicInfo(val serverName: String? = null, val version: String? = null, val id: String? = null)

@Serializable
data class JfQuickConnect(val secret: String = "", val code: String = "", val authenticated: Boolean = false)

@Serializable
private data class JfDiscovery(val address: String? = null, val name: String? = null, val id: String? = null)

data class JfServer(val address: String, val name: String)

/**
 * Cliente do servidor Jellyfin do usuário (API REST oficial, conferida na especificação 12.3).
 * Tudo que é "por usuário" usa as rotas novas com ?userId=; as antigas /Users/{id}/Items já não existem.
 */
object Jellyfin {
    const val TICKS_PER_MS = 10_000L

    /** ID usado no lugar do provedor do TMDB para o "serviço" Jellyfin no Início. */
    const val PROVIDER_ID = -100
    const val NAME = "Jellyfin"

    private const val CLIENT = "Streaming Hub"
    private const val VERSION = "0.1"

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
        // O Jellyfin usa PascalCase (ItemId, PositionTicks...).
        namingStrategy = JsonNamingStrategy { _, _, name -> name.replaceFirstChar(Char::uppercaseChar) }
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun newDeviceId(): String = UUID.randomUUID().toString().replace("-", "")

    /** Cabeçalho de identificação do Jellyfin; com [token], também autentica. */
    fun authHeader(deviceId: String, token: String? = null): String {
        val device = Build.MODEL.orEmpty().replace("\"", "").ifBlank { "Android" }
        return buildString {
            append("MediaBrowser Client=\"$CLIENT\", Device=\"$device\", DeviceId=\"$deviceId\", Version=\"$VERSION\"")
            if (token != null) append(", Token=\"$token\"")
        }
    }

    fun authHeader(s: JfSession) = authHeader(s.deviceId, s.token)

    /** "192.168.0.10:8096" → "http://192.168.0.10:8096" (sem barra no fim). */
    fun normalizeServer(input: String): String {
        val t = input.trim().trimEnd('/')
        if (t.isEmpty()) return t
        val withScheme = if (t.startsWith("http://", true) || t.startsWith("https://", true)) t else "http://$t"
        // Sem porta, assume a padrão do Jellyfin (8096) quando for http.
        val hostPart = withScheme.substringAfter("://").substringBefore('/')
        return if (':' !in hostPart && withScheme.startsWith("http://", true)) {
            withScheme.replaceFirst(hostPart, "$hostPart:8096")
        } else withScheme
    }

    private suspend fun call(
        server: String,
        path: String,
        auth: String,
        params: Map<String, String> = emptyMap(),
        body: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val url = try {
            (server + path).toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        } catch (e: IllegalArgumentException) {
            throw JellyfinException("Endereço do servidor inválido: $server")
        }
        val request = Request.Builder().url(url)
            .header("Authorization", auth)
            .header("Accept", "application/json")
            .apply { if (body != null) post(body.toRequestBody("application/json".toMediaType())) }
            .build()
        try {
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when {
                    resp.code == 401 -> throw JellyfinException("O Jellyfin recusou o acesso. Confira usuário e senha (ou conecte de novo em Configurações).")
                    !resp.isSuccessful -> throw JellyfinException("O Jellyfin respondeu com erro ${resp.code}.")
                    else -> text
                }
            }
        } catch (e: IOException) {
            throw JellyfinException("Sem conexão com o Jellyfin em $server. Confira se o servidor está ligado e na mesma rede.")
        }
    }

    private suspend fun get(s: JfSession, path: String, params: Map<String, String> = emptyMap()) =
        call(s.server, path, authHeader(s), params)

    private suspend fun post(s: JfSession, path: String, body: String, params: Map<String, String> = emptyMap()) =
        call(s.server, path, authHeader(s), params, body)

    // ---------- Conexão ----------

    /** Confere se há um Jellyfin no endereço; devolve o nome do servidor. */
    suspend fun checkServer(server: String, deviceId: String): String {
        val raw = call(server, "/System/Info/Public", authHeader(deviceId))
        val info = runCatching { json.decodeFromString<JfPublicInfo>(raw) }.getOrDefault(JfPublicInfo())
        if (info.id == null && info.version == null) throw JellyfinException("Esse endereço não parece ser um servidor Jellyfin.")
        return info.serverName ?: NAME
    }

    suspend fun login(server: String, user: String, password: String, deviceId: String): JfSession {
        val name = checkServer(server, deviceId)
        val body = buildJsonObject { put("Username", user); put("Pw", password) }.toString()
        val res = json.decodeFromString<JfAuthResult>(call(server, "/Users/AuthenticateByName", authHeader(deviceId), body = body))
        return JfSession(server, res.user.id, res.user.name, res.accessToken, deviceId, name)
    }

    /** Quick Connect: o servidor devolve um código de 6 dígitos para o usuário aprovar no Jellyfin. */
    suspend fun quickConnectStart(server: String, deviceId: String): JfQuickConnect {
        checkServer(server, deviceId)
        val enabled = call(server, "/QuickConnect/Enabled", authHeader(deviceId)).trim()
        if (enabled != "true") throw JellyfinException("O Quick Connect está desligado no servidor (Painel → Geral). Use usuário e senha.")
        return json.decodeFromString(call(server, "/QuickConnect/Initiate", authHeader(deviceId), body = "{}"))
    }

    suspend fun quickConnectApproved(server: String, deviceId: String, secret: String): Boolean =
        json.decodeFromString<JfQuickConnect>(
            call(server, "/QuickConnect/Connect", authHeader(deviceId), mapOf("secret" to secret))
        ).authenticated

    suspend fun quickConnectLogin(server: String, deviceId: String, secret: String): JfSession {
        val name = checkServer(server, deviceId)
        val body = buildJsonObject { put("Secret", secret) }.toString()
        val res = json.decodeFromString<JfAuthResult>(
            call(server, "/Users/AuthenticateWithQuickConnect", authHeader(deviceId), body = body)
        )
        return JfSession(server, res.user.id, res.user.name, res.accessToken, deviceId, name)
    }

    suspend fun logout(s: JfSession) {
        runCatching { post(s, "/Sessions/Logout", "{}") }
    }

    /** Procura servidores na rede local pelo protocolo de descoberta do Jellyfin (UDP 7359). */
    suspend fun discover(): List<JfServer> = withContext(Dispatchers.IO) {
        val found = mutableListOf<JfServer>()
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 2500
                val msg = "who is JellyfinServer?".toByteArray()
                socket.send(DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), 7359))
                val buf = ByteArray(4096)
                while (true) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        break
                    }
                    val text = String(packet.data, 0, packet.length)
                    runCatching { json.decodeFromString<JfDiscovery>(text) }.getOrNull()?.let { d ->
                        val address = d.address?.trimEnd('/') ?: return@let
                        if (found.none { it.address == address }) found += JfServer(address, d.name ?: address)
                    }
                }
            }
        }
        found
    }

    // ---------- Biblioteca ----------

    fun jfType(type: String) = if (type == "tv") "Series" else "Movie"

    /** Biblioteca de filmes ou séries, das adições mais recentes para as mais antigas. */
    suspend fun library(s: JfSession, type: String, start: Int, limit: Int = 60): JfItems =
        json.decodeFromString(
            get(
                s, "/Items", mapOf(
                    "userId" to s.userId,
                    "includeItemTypes" to jfType(type),
                    "recursive" to "true",
                    "sortBy" to "DateCreated,SortName",
                    "sortOrder" to "Descending,Ascending",
                    "fields" to "ProviderIds",
                    "startIndex" to start.toString(),
                    "limit" to limit.toString(),
                    "enableTotalRecordCount" to "true",
                )
            )
        )

    /** "Continuar assistindo": filmes pela metade; para séries, episódios pela metade e os próximos da fila. */
    suspend fun continueWatching(s: JfSession, type: String): List<JfItem> {
        val resume = json.decodeFromString<JfItems>(
            get(
                s, "/UserItems/Resume", mapOf(
                    "userId" to s.userId,
                    "includeItemTypes" to if (type == "tv") "Episode" else "Movie",
                    "mediaTypes" to "Video",
                    "limit" to "24",
                    "fields" to "ProviderIds",
                )
            )
        ).items
        if (type != "tv") return resume
        val nextUp = runCatching {
            json.decodeFromString<JfItems>(get(s, "/Shows/NextUp", mapOf("userId" to s.userId, "limit" to "24"))).items
        }.getOrDefault(emptyList())
        // Um episódio por série: o que está pela metade tem prioridade.
        return (resume + nextUp).distinctBy { it.seriesId ?: it.id }
    }

    suspend fun item(s: JfSession, id: String): JfItem =
        json.decodeFromString(get(s, "/Items/$id", mapOf("userId" to s.userId)))

    suspend fun seasons(s: JfSession, seriesId: String): List<JfItem> =
        json.decodeFromString<JfItems>(get(s, "/Shows/$seriesId/Seasons", mapOf("userId" to s.userId))).items

    suspend fun episodes(s: JfSession, seriesId: String, seasonId: String): List<JfItem> =
        json.decodeFromString<JfItems>(
            get(s, "/Shows/$seriesId/Episodes", mapOf("userId" to s.userId, "seasonId" to seasonId, "fields" to "Overview"))
        ).items

    /** Próximo episódio a assistir da série (ou null se não houver). */
    suspend fun nextUp(s: JfSession, seriesId: String): JfItem? =
        json.decodeFromString<JfItems>(
            get(s, "/Shows/NextUp", mapOf("userId" to s.userId, "seriesId" to seriesId, "limit" to "1"))
        ).items.firstOrNull()

    suspend fun search(s: JfSession, query: String): List<JfItem> =
        json.decodeFromString<JfItems>(
            get(
                s, "/Items", mapOf(
                    "userId" to s.userId,
                    "searchTerm" to query,
                    "includeItemTypes" to "Movie,Series",
                    "recursive" to "true",
                    "fields" to "ProviderIds",
                    "limit" to "20",
                )
            )
        ).items

    /**
     * Índice "tipo:tmdb:ID" / "tipo:imdb:ID" → ID do Jellyfin, para os Detalhes e a Busca saberem
     * o que existe no servidor. Uma consulta só, sem imagens.
     */
    suspend fun index(s: JfSession): Map<String, String> {
        val items = json.decodeFromString<JfItems>(
            get(
                s, "/Items", mapOf(
                    "userId" to s.userId,
                    "includeItemTypes" to "Movie,Series",
                    "recursive" to "true",
                    "fields" to "ProviderIds",
                    "enableImages" to "false",
                    "enableUserData" to "false",
                )
            )
        ).items
        val map = HashMap<String, String>()
        items.forEach { item ->
            val t = if (item.isSeries) "tv" else "movie"
            item.tmdbId?.let { map["$t:tmdb:$it"] = item.id }
            item.imdbId?.let { map["$t:imdb:$it"] = item.id }
        }
        return map
    }

    /** ID no Jellyfin do título do TMDB, se ele existir no servidor. */
    fun match(index: Map<String, String>, type: String, tmdbId: Int, imdbId: String? = null): String? =
        listOfNotNull("$type:tmdb:$tmdbId", imdbId?.let { "$type:imdb:$it" }).firstNotNullOfOrNull { index[it] }

    // ---------- Imagens ----------

    private fun image(s: JfSession, id: String, tag: String?, height: Int) =
        "${s.server}/Items/$id/Images/Primary?fillHeight=$height&quality=90" + (tag?.let { "&tag=$it" } ?: "")

    /** Capa vertical. Episódios usam a capa da série. */
    fun posterUrl(s: JfSession, item: JfItem, height: Int = 480): String? = when {
        item.isEpisode && item.seriesId != null -> image(s, item.seriesId, item.seriesPrimaryImageTag, height)
        item.imageTags.containsKey("Primary") -> image(s, item.id, item.imageTags["Primary"], height)
        else -> null
    }

    /** Imagem própria do item (no episódio, a cena em 16:9). */
    fun thumbUrl(s: JfSession, item: JfItem, height: Int = 240): String? =
        item.imageTags["Primary"]?.let { image(s, item.id, it, height) }

    // ---------- Reprodução ----------

    suspend fun playbackInfo(s: JfSession, id: String): JfPlaybackInfo =
        json.decodeFromString(get(s, "/Items/$id/PlaybackInfo", mapOf("userId" to s.userId)))

    /**
     * Pede ao servidor para converter o vídeo (HLS) quando o aparelho não toca o arquivo original.
     * [allowHevc] = o problema era só o áudio e o aparelho decodifica HEVC: o vídeo segue copiado.
     * A lista HLS cobre o vídeo inteiro; o player é que pula para a posição.
     */
    suspend fun transcodeInfo(s: JfSession, id: String, mediaSourceId: String, allowHevc: Boolean): JfPlaybackInfo {
        val profile = buildJsonObject {
            put("Name", CLIENT)
            put("MaxStreamingBitrate", 40_000_000)
            putJsonArray("DirectPlayProfiles") {}
            putJsonArray("TranscodingProfiles") {
                addJsonObject {
                    put("Container", "mp4")
                    put("Type", "Video")
                    put("VideoCodec", if (allowHevc) "h264,hevc" else "h264")
                    put("AudioCodec", "aac,mp3")
                    put("Protocol", "hls")
                    put("Context", "Streaming")
                    put("MaxAudioChannels", "2")
                    put("MinSegments", 1)
                    put("BreakOnNonKeyFrames", true)
                }
            }
            putJsonArray("SubtitleProfiles") {
                listOf("srt", "subrip", "ass", "ssa", "vtt", "webvtt", "microdvd", "sub").forEach { f ->
                    addJsonObject { put("Format", f); put("Method", "External") }
                }
            }
        }
        val body: JsonObject = buildJsonObject {
            put("UserId", s.userId)
            put("MediaSourceId", mediaSourceId)
            put("EnableDirectPlay", false)
            put("EnableDirectStream", false)
            put("EnableTranscoding", true)
            put("AllowVideoStreamCopy", true)
            put("AllowAudioStreamCopy", false)
            put("DeviceProfile", profile)
        }
        return json.decodeFromString(post(s, "/Items/$id/PlaybackInfo", body.toString(), mapOf("userId" to s.userId)))
    }

    /** Arquivo original, sem conversão. A autenticação vai no cabeçalho do player. */
    fun directStreamUrl(s: JfSession, id: String, source: JfMediaSource, playSessionId: String?): String =
        "${s.server}/Videos/$id/stream".toHttpUrl().newBuilder()
            .addQueryParameter("static", "true")
            .addQueryParameter("mediaSourceId", source.id)
            .addQueryParameter("deviceId", s.deviceId)
            .apply { playSessionId?.let { addQueryParameter("playSessionId", it) } }
            .build().toString()

    /** Legenda convertida pelo servidor para SRT em UTF-8 (serve para .srt, .sub, .ass, .vtt, internas ou externas). */
    fun subtitleUrl(s: JfSession, id: String, mediaSourceId: String, index: Int): String =
        "${s.server}/Videos/$id/$mediaSourceId/Subtitles/$index/Stream.srt"

    suspend fun reportStart(s: JfSession, r: JfPlaybackReport) {
        runCatching { post(s, "/Sessions/Playing", json.encodeToString(r)) }
    }

    suspend fun reportProgress(s: JfSession, r: JfPlaybackReport) {
        runCatching { post(s, "/Sessions/Playing/Progress", json.encodeToString(r)) }
    }

    suspend fun reportStopped(s: JfSession, r: JfPlaybackReport) {
        runCatching { post(s, "/Sessions/Playing/Stopped", json.encodeToString(r)) }
    }
}
