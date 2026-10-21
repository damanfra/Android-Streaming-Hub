package com.damanfra.streaminghub.data

import java.time.LocalDate

enum class SortOption(val label: String) {
    POPULAR("Em alta"),
    NEWEST("Mais recentes"),
    TOP_RATED("Melhor avaliados"),
    MOST_VOTED("Mais votados"),
}

enum class Period(val label: String) {
    ANY("Qualquer época"),
    LAST_YEAR("Últimos 12 meses"),
    Y2020("Anos 2020"),
    Y2010("Anos 2010"),
    Y2000("Anos 2000"),
    Y1990("Anos 90"),
    OLDER("Antes de 1990"),
}

/** Filtros e ordenação do Início. Gêneros ficam separados porque filmes e séries usam IDs diferentes no TMDB. */
data class Filters(
    val sort: SortOption = SortOption.POPULAR,
    val period: Period = Period.ANY,
    val minRating: Int = 0,
    val genresMovie: Set<Int> = emptySet(),
    val genresTv: Set<Int> = emptySet(),
) {
    fun genres(type: String): Set<Int> = if (type == "tv") genresTv else genresMovie

    fun withGenres(type: String, ids: Set<Int>): Filters =
        if (type == "tv") copy(genresTv = ids) else copy(genresMovie = ids)

    fun activeCount(type: String): Int = listOf(
        sort != SortOption.POPULAR,
        period != Period.ANY,
        minRating > 0,
        genres(type).isNotEmpty(),
    ).count { it }

    /** Converte para os parâmetros do /discover do TMDB. */
    fun toParams(type: String, today: LocalDate = LocalDate.now()): Map<String, String> {
        val dateField = if (type == "tv") "first_air_date" else "primary_release_date"
        val params = mutableMapOf<String, String>()
        var minVotes = 0

        var from: LocalDate? = null
        var to: LocalDate? = null
        when (period) {
            Period.ANY -> Unit
            Period.LAST_YEAR -> from = today.minusYears(1)
            Period.Y2020 -> { from = LocalDate.of(2020, 1, 1); to = LocalDate.of(2029, 12, 31) }
            Period.Y2010 -> { from = LocalDate.of(2010, 1, 1); to = LocalDate.of(2019, 12, 31) }
            Period.Y2000 -> { from = LocalDate.of(2000, 1, 1); to = LocalDate.of(2009, 12, 31) }
            Period.Y1990 -> { from = LocalDate.of(1990, 1, 1); to = LocalDate.of(1999, 12, 31) }
            Period.OLDER -> to = LocalDate.of(1989, 12, 31)
        }

        when (sort) {
            SortOption.POPULAR -> params["sort_by"] = "popularity.desc"
            SortOption.NEWEST -> {
                params["sort_by"] = "$dateField.desc"
                // Nada de datas futuras: "mais recentes" é o que já saiu.
                to = if (to == null || to.isAfter(today)) today else to
            }
            SortOption.TOP_RATED -> {
                params["sort_by"] = "vote_average.desc"
                minVotes = if (type == "tv") 150 else 300 // evita títulos obscuros com nota 10 de poucos votos
            }
            SortOption.MOST_VOTED -> params["sort_by"] = "vote_count.desc"
        }

        from?.let { params["$dateField.gte"] = it.toString() }
        to?.let { params["$dateField.lte"] = it.toString() }

        if (minRating > 0) {
            params["vote_average.gte"] = minRating.toString()
            minVotes = maxOf(minVotes, 50)
        }
        if (minVotes > 0) params["vote_count.gte"] = minVotes.toString()

        val g = genres(type)
        if (g.isNotEmpty()) params["with_genres"] = g.joinToString("|") // "|" = qualquer um dos gêneros
        return params
    }
}
