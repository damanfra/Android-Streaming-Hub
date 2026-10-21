@file:OptIn(ExperimentalMaterial3Api::class)

package com.damanfra.streaminghub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.damanfra.streaminghub.AppViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ListScreen(vm: AppViewModel, onOpen: (String, Int) -> Unit) {
    val list by vm.watchlist.collectAsState()
    val recents by vm.recents.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val toWatch = list.filter { !it.watched }
    val watched = list.filter { it.watched }
    val cells = posterCells(vm)

    val landscape = isLandscape()
    Scaffold(topBar = { if (!landscape) TopAppBar(title = { Text("Minha lista") }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Recentes") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Quero ver (${toWatch.size})") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Assistidos (${watched.size})") })
            }
            when (tab) {
                0 -> if (recents.isEmpty()) {
                    EmptyBox("Os títulos que você abrir em algum serviço aparecem aqui, do mais recente para o mais antigo.")
                } else {
                    PosterGrid(cells) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                                TextButton(onClick = { vm.clearRecents() }) { Text("Limpar recentes") }
                            }
                        }
                        items(recents, key = { "${it.type}-${it.id}" }) { r ->
                            PosterCard(r.posterPath, r.title, "${r.app} · ${whenOpened(r.openedAt)}", onRemove = { vm.removeRecent(r.type, r.id) }) {
                                onOpen(r.type, r.id)
                            }
                        }
                    }
                }
                else -> {
                    val shown = if (tab == 1) toWatch else watched
                    if (shown.isEmpty()) {
                        EmptyBox(
                            if (tab == 1) "Nada por aqui. Na tela de um título, toque em \"Quero ver\"."
                            else "Os títulos que você marcar como assistidos aparecem aqui."
                        )
                    } else {
                        PosterGrid(cells) {
                            items(shown, key = { "${it.type}-${it.id}" }) { s ->
                                PosterCard(s.posterPath, s.title, listOfNotNull(typeLabel(s.type), s.year).joinToString(" · "), onRemove = { vm.removeSaved(s.type, s.id) }) {
                                    onOpen(s.type, s.id)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterGrid(cells: GridCells, content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = cells,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
        content = content,
    )
}

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dateFmt = DateTimeFormatter.ofPattern("dd/MM")

/** "hoje 21:40", "ontem" ou "20/10". */
private fun whenOpened(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(millis).atZone(zone)
    val today = LocalDate.now(zone)
    return when (at.toLocalDate()) {
        today -> "hoje ${at.format(timeFmt)}"
        today.minusDays(1) -> "ontem"
        else -> at.format(dateFmt)
    }
}
