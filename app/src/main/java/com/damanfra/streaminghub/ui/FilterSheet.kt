@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.damanfra.streaminghub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.damanfra.streaminghub.data.Filters
import com.damanfra.streaminghub.data.Genre
import com.damanfra.streaminghub.data.Period
import com.damanfra.streaminghub.data.SortOption

/** Painel de ordenação e filtros. As mudanças só valem ao tocar em "Ver resultados". */
@Composable
fun FilterSheet(
    type: String,
    current: Filters,
    genres: List<Genre>,
    onApply: (Filters) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(current) { mutableStateOf(current) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (type == "tv") "Filtrar séries" else "Filtrar filmes",
                style = MaterialTheme.typography.titleLarge,
            )

            SectionTitle("Ordenar por")
            ChipGroup {
                SortOption.entries.forEach { opt ->
                    FilterChip(selected = draft.sort == opt, onClick = { draft = draft.copy(sort = opt) }, label = { Text(opt.label) })
                }
            }

            SectionTitle("Época")
            ChipGroup {
                Period.entries.forEach { p ->
                    FilterChip(selected = draft.period == p, onClick = { draft = draft.copy(period = p) }, label = { Text(p.label) })
                }
            }

            SectionTitle("Nota mínima")
            ChipGroup {
                listOf(0, 6, 7, 8).forEach { n ->
                    FilterChip(
                        selected = draft.minRating == n,
                        onClick = { draft = draft.copy(minRating = n) },
                        label = { Text(if (n == 0) "Qualquer" else "★ $n+") },
                    )
                }
            }

            SectionTitle("Gêneros")
            val selectedGenres = draft.genres(type)
            if (genres.isEmpty()) {
                Text("Carregando gêneros…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                ChipGroup {
                    genres.forEach { g ->
                        val on = g.id in selectedGenres
                        FilterChip(
                            selected = on,
                            onClick = {
                                draft = draft.withGenres(type, if (on) selectedGenres - g.id else selectedGenres + g.id)
                            },
                            label = { Text(g.name) },
                        )
                    }
                }
                if (selectedGenres.size > 1) {
                    Text(
                        "Mostra títulos de qualquer um dos gêneros marcados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = { draft = Filters() }, modifier = Modifier.weight(1f)) { Text("Limpar") }
                Button(onClick = { onApply(draft) }, modifier = Modifier.weight(1f)) { Text("Ver resultados") }
            }
        }
    }
}

@Composable
private fun ChipGroup(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
