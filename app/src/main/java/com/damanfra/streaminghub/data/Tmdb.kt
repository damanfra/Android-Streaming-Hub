package com.damanfra.streaminghub.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class TmdbException(message: String) : Exception(message)

/**
 * Cliente mínimo do TMDB. Aceita tanto a "API Key" (v3) quanto o "Read Access Token" (v4, começa com eyJ).
 * Os dados de onde assistir vêm da JustWatch, repassados pelo TMDB.
 */
object Tmdb {
    const val REGION = "BR"
    private const val LANG = "pt-BR"
    private const val BASE = "https://api.themoviedb.org/3/"
    private const val IMG = "https://image.tmdb.org/t/p/"

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun image(path: String?, size: String = "w342"): String? = path?.let { IMG + size + it }

    private fun isBearer(key: String) = key.trim().startsWith("eyJ")

    private suspend fun get(key: String, path: String, params: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            if (key.isBlank()) throw TmdbException("Configure sua chave do TMDB em Apps → Configurações.")
            val url = (BASE + path).toHttpUrl().newBuilder().apply {
                addQueryParameter("language", LANG)
                params.forEach { (k, v) -> addQueryParameter(k, v) }
                if (!isBearer(key)) addQueryParameter("api_key", key.trim())
            }.build()
            val request = Request.Builder().url(url).apply {
                header("accept", "application/json")
                if (isBearer(key)) header("Authorization", "Bearer ${key.trim()}")
            }.build()
            try {
                http.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    when {
                        resp.code == 401 -> throw TmdbException("Chave do TMDB inválida. Confira em Configurações.")
                        !resp.isSuccessful -> throw TmdbException("O TMDB respondeu com erro ${resp.code}.")
                        else -> body
                    }
                }
            } catch (e: IOException) {
                throw TmdbException("Sem conexão com o TMDB. Verifique a internet.")
            }
        }

    /** Populares disponíveis por assinatura nos serviços informados (type = "movie" ou "tv"). */
    suspend fun discover(
        key: String,
        type: String,
        providerIds: Set<Int>,
        page: Int,
        filters: Filters = Filters(),
    ): Paged<MediaItem> {
        val params = mutableMapOf(
            "watch_region" to REGION,
            "sort_by" to "popularity.desc",
            "include_adult" to "false",
            "page" to page.toString(),
            "with_watch_monetization_types" to "flatrate|free|ads",
        )
        if (providerIds.isNotEmpty()) params["with_watch_providers"] = providerIds.joinToString("|")
        params.putAll(filters.toParams(type))
        val res = json.decodeFromString<Paged<MediaItem>>(get(key, "discover/$type", params))
        return res.copy(results = res.results.map { it.copy(mediaType = type) })
    }

    /** Busca filmes e séries (ignora pessoas). */
    suspend fun search(key: String, query: String): List<MediaItem> {
        val res = json.decodeFromString<Paged<MediaItem>>(
            get(key, "search/multi", mapOf("query" to query, "include_adult" to "false"))
        )
        return res.results.filter { it.mediaType == "movie" || it.mediaType == "tv" }
    }

    suspend fun detail(key: String, type: String, id: Int): MediaDetail =
        json.decodeFromString(get(key, "$type/$id", mapOf("append_to_response" to "watch/providers,credits,external_ids")))

    /** Lista de gêneros em português para filmes ou séries. */
    suspend fun genres(key: String, type: String): List<Genre> =
        json.decodeFromString<GenreList>(get(key, "genre/$type/list")).genres

    /** Todos os serviços que o TMDB conhece no Brasil (filmes + séries). */
    suspend fun providers(key: String): List<Provider> {
        val params = mapOf("watch_region" to REGION)
        val movie = json.decodeFromString<ProviderList>(get(key, "watch/providers/movie", params))
        val tv = json.decodeFromString<ProviderList>(get(key, "watch/providers/tv", params))
        return (movie.results + tv.results).distinctBy { it.id }.sortedBy { it.displayPriority }
    }
}
