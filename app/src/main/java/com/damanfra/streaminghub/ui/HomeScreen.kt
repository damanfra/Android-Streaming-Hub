@file:OptIn(ExperimentalMaterial3Api::class)

package com.damanfra.streaminghub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.damanfra.streaminghub.AppViewModel
import com.damanfra.streaminghub.data.MediaItem
import com.damanfra.streaminghub.data.Provider
import com.damanfra.streaminghub.data.Tmdb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

@Composable
fun HomeScreen(vm: AppViewModel, onOpen: (String, Int) -> Unit, onSettings: () -> Unit) {
    val key by vm.apiKey.collectAsState()
    val providers by vm.providers.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Streaming Hub") }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val k = key
            when {
                k == null -> Loading()
                k.isBlank() || providers.isEmpty() -> SetupCard(hasKey = k.isNotBlank(), onSettings = onSettings)
                else -> HomeContent(k, providers, onOpen)
            }
        }
    }
}

@Composable
private fun SetupCard(hasKey: Boolean, onSettings: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Vamos configurar", style = MaterialTheme.typography.titleMedium)
            if (!hasKey) {
                Text("1. Crie uma conta grátis em themoviedb.org e gere uma chave de API (Configurações → API).")
                Text("2. Cole a chave em Configurações.")
                Text("3. Marque os serviços que você assina.")
            } else {
                Text("Chave salva. Agora marque os serviços que você assina para montar seu catálogo.")
            }
            Button(onClick = onSettings) { Text("Abrir configurações") }
        }
    }
}

@Composable
private fun HomeContent(key: String, providers: List<Provider>, onOpen: (String, Int) -> Unit) {
    var type by rememberSaveable { mutableStateOf("movie") }
    var filter by rememberSaveable { mutableIntStateOf(-1) } // -1 = todos os meus serviços
    val results = remember { mutableStateListOf<MediaItem>() }
    var page by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()

    // Se o serviço filtrado foi desmarcado nas configurações, volta para "todos".
    if (filter != -1 && providers.none { it.id == filter }) filter = -1
    val ids = if (filter == -1) providers.map { it.id }.toSet() else setOf(filter)

    suspend fun fetch(target: Int) {
        loading = true
        error = null
        try {
            val res = Tmdb.discover(key, type, ids, target)
            val existing = results.map { it.id }.toHashSet()
            results.addAll(res.results.filter { it.id !in existing })
            page = target
            totalPages = res.totalPages
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Erro ao carregar."
        } finally {
            if (currentCoroutineContext().isActive) loading = false
        }
    }

    LaunchedEffect(type, ids, retry) {
        results.clear()
        page = 0
        totalPages = 1
        gridState.scrollToItem(0)
        fetch(1)
    }

    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, page, type, ids, error) {
        if (nearEnd && !loading && error == null && page in 1 until totalPages) fetch(page + 1)
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = if (type == "movie") 0 else 1) {
            Tab(selected = type == "movie", onClick = { type = "movie" }, text = { Text("Filmes") })
            Tab(selected = type == "tv", onClick = { type = "tv" }, text = { Text("Séries") })
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(selected = filter == -1, onClick = { filter = -1 }, label = { Text("Todos") })
            }
            items(providers, key = { it.id }) { p ->
                FilterChip(
                    selected = filter == p.id,
                    onClick = { filter = p.id },
                    label = { Text(p.name) },
                    leadingIcon = { ProviderLogo(p.logoPath, p.name, size = 20) },
                )
            }
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(110.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(results, key = { it.id }) { m ->
                PosterCard(m.posterPath, m.displayTitle, m.year) { onOpen(type, m.id) }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val err = error
                when {
                    loading -> Loading()
                    err != null -> ErrorBox(err) { if (page == 0) retry++ else error = null }
                    results.isEmpty() && page > 0 -> EmptyBox("Nada encontrado para esse filtro no Brasil.")
                }
            }
        }
    }
}
