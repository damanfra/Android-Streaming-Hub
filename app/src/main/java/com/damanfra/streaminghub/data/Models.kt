package com.damanfra.streaminghub.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Item de lista do TMDB (descoberta, busca, tendências). */
@Serializable
data class MediaItem(
    val id: Int,
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val overview: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
) {
    val displayTitle: String get() = title ?: name ?: "Sem título"
    val year: String? get() = (releaseDate ?: firstAirDate)?.take(4)?.takeIf { it.isNotBlank() }
}

@Serializable
data class Paged<T>(
    val page: Int = 1,
    val results: List<T> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
)

/** Serviço de streaming conforme o TMDB/JustWatch. */
@Serializable
data class Provider(
    @SerialName("provider_id") val id: Int,
    @SerialName("provider_name") val name: String,
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("display_priority") val displayPriority: Int = 999,
)

@Serializable
data class ProviderList(val results: List<Provider> = emptyList())

@Serializable
data class Genre(val id: Int, val name: String)

@Serializable
data class GenreList(val genres: List<Genre> = emptyList())

@Serializable
data class Person(val name: String)

@Serializable
data class CastMember(val name: String, val order: Int = 999)

@Serializable
data class CrewMember(val name: String, val job: String? = null)

@Serializable
data class Credits(val cast: List<CastMember> = emptyList(), val crew: List<CrewMember> = emptyList())

@Serializable
data class ExternalIds(@SerialName("imdb_id") val imdbId: String? = null)

/** Título aberto em algum serviço, para a aba Recentes. */
@Serializable
data class RecentItem(
    val id: Int,
    val type: String,
    val title: String,
    val posterPath: String? = null,
    val year: String? = null,
    val app: String,
    val openedAt: Long,
)

@Serializable
data class CountryProviders(
    val link: String? = null,
    val flatrate: List<Provider> = emptyList(),
    val ads: List<Provider> = emptyList(),
    val free: List<Provider> = emptyList(),
    val rent: List<Provider> = emptyList(),
    val buy: List<Provider> = emptyList(),
) {
    /** Tudo que entra "na assinatura" (inclui planos com anúncio e grátis). */
    val streaming: List<Provider> get() = (flatrate + ads + free).distinctBy { it.id }
}

@Serializable
data class WatchProviders(val results: Map<String, CountryProviders> = emptyMap())

@Serializable
data class MediaDetail(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val runtime: Int? = null,
    @SerialName("number_of_seasons") val seasons: Int? = null,
    val genres: List<Genre> = emptyList(),
    @SerialName("watch/providers") val watchProviders: WatchProviders? = null,
    val credits: Credits? = null,
    @SerialName("external_ids") val externalIds: ExternalIds? = null,
    /** Criadores (só séries). */
    @SerialName("created_by") val createdBy: List<Person> = emptyList(),
) {
    /** Até [n] atores principais, na ordem de destaque do TMDB. */
    fun mainCast(n: Int = 6): List<String> =
        credits?.cast.orEmpty().sortedBy { it.order }.take(n).map { it.name }

    fun directors(): List<String> =
        credits?.crew.orEmpty().filter { it.job == "Director" }.map { it.name }.distinct()

    val imdbId: String? get() = externalIds?.imdbId?.takeIf { it.isNotBlank() }
    val displayTitle: String get() = title ?: name ?: "Sem título"
    val year: String? get() = (releaseDate ?: firstAirDate)?.take(4)?.takeIf { it.isNotBlank() }
}

/** Item salvo localmente em "Minha lista". */
@Serializable
data class SavedItem(
    val id: Int,
    val type: String,
    val title: String,
    val posterPath: String? = null,
    val year: String? = null,
    val watched: Boolean = false,
    val addedAt: Long = 0,
)
