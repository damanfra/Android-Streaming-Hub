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
) {
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
