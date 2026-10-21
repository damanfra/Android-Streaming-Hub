@file:OptIn(ExperimentalMaterial3Api::class)

package com.damanfra.streaminghub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.damanfra.streaminghub.AppViewModel

@Composable
fun ListScreen(vm: AppViewModel, onOpen: (String, Int) -> Unit) {
    val list by vm.watchlist.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val toWatch = list.filter { !it.watched }
    val watched = list.filter { it.watched }
    val shown = if (tab == 0) toWatch else watched

    Scaffold(topBar = { TopAppBar(title = { Text("Minha lista") }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Quero ver (${toWatch.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Assistidos (${watched.size})") })
            }
            if (shown.isEmpty()) {
                EmptyBox(
                    if (tab == 0) "Nada por aqui. Na tela de um título, toque em \"Quero ver\"."
                    else "Os títulos que você marcar como assistidos aparecem aqui."
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { "${it.type}-${it.id}" }) { s ->
                    PosterCard(s.posterPath, s.title, listOfNotNull(typeLabel(s.type), s.year).joinToString(" · ")) {
                        onOpen(s.type, s.id)
                    }
                }
            }
        }
    }
}
