@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.damanfra.streaminghub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import java.time.LocalDate
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.damanfra.streaminghub.AppViewModel
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        FilterContent(type, current, genres, onApply)
    }
}

/** Na TV, os mesmos filtros num painel central navegável pelo controle (Voltar fecha sem aplicar). */
@Composable
fun TvFilterDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val key by vm.apiKey.collectAsState()
    val type by vm.homeType.collectAsState()
    val filters by vm.filters.collectAsState()
    var genres by remember { mutableStateOf<List<Genre>>(emptyList()) }
    LaunchedEffect(key, type) {
        val k = key ?: return@LaunchedEffect
        genres = runCatching { vm.genres(k, type) }.getOrDefault(emptyList())
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.width(860.dp).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            var draft by remember(filters) { mutableStateOf(filters) }
            // Duas colunas: os botões ficam sempre à mão na esquerda, sem rolar todas as opções até o fim.
            Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.width(200.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterTitle(type)
                    // Resumo do rascunho, para conferir antes de aplicar.
                    draft.labels(type, genres).forEach {
                        Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = { vm.filters.value = draft; onDismiss() },
                        modifier = Modifier.padding(top = 6.dp).fillMaxWidth().height(44.dp).tvFocusFrame(RoundedCornerShape(50), 1.04f),
                    ) {
                        Text("Ver resultados", fontSize = 16.sp)
                    }
                    OutlinedButton(
                        onClick = { draft = Filters() },
                        modifier = Modifier.fillMaxWidth().height(40.dp).tvFocusFrame(RoundedCornerShape(50), 1.04f),
                    ) { Text("Limpar", fontSize = 15.sp) }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "◄ volta aos botões de qualquer opção\nVoltar fecha sem aplicar",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant))
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterOptions(type, draft, genres) { draft = it }
                }
            }
        }
    }
}

@Composable
private fun FilterTitle(type: String) {
    Text(if (type == "tv") "Filtrar séries" else "Filtrar filmes", style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun FilterContent(type: String, current: Filters, genres: List<Genre>, onApply: (Filters) -> Unit) {
    var draft by remember(current) { mutableStateOf(current) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterTitle(type)
        FilterOptions(type, draft, genres) { draft = it }
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = { draft = Filters() }, modifier = Modifier.weight(1f)) { Text("Limpar") }
            Button(onClick = { onApply(draft) }, modifier = Modifier.weight(1f)) { Text("Ver resultados") }
        }
    }
}

/** Ordenação, época, nota e gêneros. Cada toque gera um novo rascunho em [onChange]. */
@Composable
private fun FilterOptions(type: String, draft: Filters, genres: List<Genre>, onChange: (Filters) -> Unit) {
    SectionTitle("Ordenar por")
    ChipGroup {
        SortOption.entries.forEach { opt ->
            OptionChip(selected = draft.sort == opt, onClick = { onChange(draft.copy(sort = opt)) }, label = opt.label)
        }
    }

    SectionTitle("Época")
    ChipGroup {
        Period.entries.forEach { p ->
            val label = if (p == Period.FROM_YEAR && draft.period == p) "A partir de ${draft.fromYear}" else p.label
            OptionChip(selected = draft.period == p, onClick = { onChange(draft.copy(period = p)) }, label = label)
        }
    }
    if (draft.period == Period.FROM_YEAR) {
        YearPicker(selected = draft.fromYear, onPick = { onChange(draft.copy(fromYear = it)) })
    }

    SectionTitle("Nota mínima")
    ChipGroup {
        listOf(0, 6, 7, 8).forEach { n ->
            OptionChip(
                selected = draft.minRating == n,
                onClick = { onChange(draft.copy(minRating = n)) },
                label = if (n == 0) "Qualquer" else "★ $n+",
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
                OptionChip(
                    selected = on,
                    onClick = {
                        onChange(draft.withGenres(type, if (on) selectedGenres - g.id else selectedGenres + g.id))
                    },
                    label = g.name,
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
}

/** Faixa rolável de anos, do atual até 1930, já posicionada no ano escolhido. */
@Composable
private fun YearPicker(selected: Int, onPick: (Int) -> Unit) {
    val years = remember { (LocalDate.now().year downTo 1930).toList() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (years.indexOf(selected) - 2).coerceAtLeast(0))
    Text(
        "Mostra o que foi lançado em $selected ou depois.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(years) { y ->
            OptionChip(selected = y == selected, onClick = { onPick(y) }, label = y.toString())
        }
    }
}

@Composable
private fun ChipGroup(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(if (LocalIsTv.current) 10.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (LocalIsTv.current) 10.dp else 0.dp),
    ) { content() }
}

/** Chip de opção. Na TV fica maior, com ✓ quando marcado e moldura de foco, para ler e navegar de longe. */
@Composable
private fun OptionChip(selected: Boolean, onClick: () -> Unit, label: String) {
    if (!LocalIsTv.current) {
        FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
        return
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 16.sp) },
        leadingIcon = if (selected) ({ Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }) else null,
        modifier = Modifier.height(42.dp).tvFocusFrame(RoundedCornerShape(8.dp), 1.04f),
    )
}
