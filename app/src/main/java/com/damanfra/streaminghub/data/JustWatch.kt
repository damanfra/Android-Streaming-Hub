package com.damanfra.streaminghub.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Links diretos de cada título em cada serviço (ex.: https://www.netflix.com/title/70143836),
 * vindos da API GraphQL pública (não oficial) da JustWatch — a mesma fonte do "onde assistir" do TMDB.
 * Se a API mudar ou falhar, o app volta ao comportamento anterior (busca + título copiado).
 */
object JustWatch {
    private const val URL = "https://apis.justwatch.com/graphql"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val QUERY = """
        query GetSearchTitles(${'$'}filter: TitleFilter!, ${'$'}country: Country!, ${'$'}language: Language!, ${'$'}first: Int!, ${'$'}offerFilter: OfferFilter!) {
          popularTitles(country: ${'$'}country, filter: ${'$'}filter, first: ${'$'}first, sortBy: POPULAR, sortRandomSeed: 0) {
            edges {
              node {
                objectType
                content(country: ${'$'}country, language: ${'$'}language) {
                  title
                  ... on MovieOrShowOrSeasonContent { externalIds { tmdbId } }
                }
                offers(country: ${'$'}country, platform: WEB, filter: ${'$'}offerFilter) {
                  monetizationType
                  standardWebURL
                  package { clearName technicalName }
                }
              }
            }
          }
        }
    """.trimIndent()

    /**
     * Devolve app → URL direta do título, tentando primeiro o título em português e depois o original.
     * [type] é "movie" ou "tv" (como no TMDB).
     */
    suspend fun directLinks(tmdbId: Int, type: String, titles: List<String>): Map<StreamingApps.App, String> {
        for (title in titles.filter { it.isNotBlank() }.distinct()) {
            val found = runCatching { search(tmdbId, type, title) }.getOrNull()
            if (!found.isNullOrEmpty()) return found
        }
        return emptyMap()
    }

    private suspend fun search(tmdbId: Int, type: String, title: String): Map<StreamingApps.App, String>? =
        withContext(Dispatchers.IO) {
            val objectType = if (type == "tv") "SHOW" else "MOVIE"
            val body = buildJsonObject {
                put("operationName", "GetSearchTitles")
                put("query", QUERY)
                putJsonObject("variables") {
                    put("country", Tmdb.REGION)
                    put("language", "pt")
                    put("first", 8)
                    putJsonObject("offerFilter") { put("bestOnly", false) }
                    putJsonObject("filter") {
                        put("searchQuery", title)
                        put("includeTitlesWithoutUrl", true)
                        putJsonArray("objectTypes") { add(objectType) }
                    }
                }
            }
            val request = Request.Builder()
                .url(URL)
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val text = http.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
                ?: return@withContext null

            val edges = Tmdb.json.parseToJsonElement(text).obj("data")?.obj("popularTitles")?.arr("edges")
                ?: return@withContext null
            val node = edges.mapNotNull { it.obj("node") }.firstOrNull { n ->
                n.str("objectType") == objectType &&
                    n.obj("content")?.obj("externalIds")?.str("tmdbId") == tmdbId.toString()
            } ?: return@withContext null

            // Prioriza ofertas de assinatura; aluguel/compra só se não houver outra do mesmo app.
            val rank = mapOf("FLATRATE" to 0, "ADS" to 1, "FREE" to 2)
            val links = LinkedHashMap<StreamingApps.App, String>()
            node.arr("offers").orEmpty()
                .mapNotNull { it as? JsonObject }
                .sortedBy { rank[it.str("monetizationType")] ?: 9 }
                .forEach { offer ->
                    val url = offer.str("standardWebURL")?.replaceFirst("http://", "https://") ?: return@forEach
                    val name = offer.obj("package")?.str("clearName") ?: return@forEach
                    val app = StreamingApps.forProvider(name) ?: return@forEach
                    links.putIfAbsent(app, url)
                }
            links
        }

    private fun JsonElement.obj(key: String): JsonObject? =
        (this as? JsonObject)?.get(key) as? JsonObject

    private fun JsonElement.arr(key: String): JsonArray? =
        (this as? JsonObject)?.get(key) as? JsonArray

    private fun JsonElement.str(key: String): String? =
        ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull
}
