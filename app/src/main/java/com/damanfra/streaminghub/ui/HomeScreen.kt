@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)

package com.damanfra.streaminghub.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import com.damanfra.streaminghub.data.Genre
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
    val loaded by vm.homeLoaded.collectAsState()

    // Na horizontal (e na TV) a barra de título some para sobrar altura para as capas.
    val landscape = isLandscape()
    Scaffold(topBar = { if (!landscape) TopAppBar(title = { Text("Streaming Hub") }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val k = key
            when {
                k == null || !loaded -> Loading()
                k.isBlank() || providers.isEmpty() -> SetupCard(hasKey = k.isNotBlank(), onSettings = onSettings)
                else -> HomeContent(vm, k, providers, posterCells(vm), onOpen)
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
private fun HomeContent(
    vm: AppViewModel,
    key: String,
    providers: List<Provider>,
    cells: GridCells,
    onOpen: (String, Int) -> Unit,
) {
    val tv = LocalIsTv.current
    val type by vm.homeType.collectAsState()
    val filters by vm.filters.collectAsState()
    var showFilters by remember { mutableStateOf(false) }
    var genres by remember { mutableStateOf<List<Genre>>(emptyList()) }
    LaunchedEffect(key, type) {
        genres = emptyList()
        genres = runCatching { vm.genres(key, type) }.getOrDefault(emptyList())
    }
    val filter by vm.homeProvider.collectAsState() // -1 = todos os meus serviços
    // O catálogo carregado mora no ViewModel: ao voltar dos Detalhes ele continua lá, com todas as páginas.
    val catalog = vm.catalog
    val results = catalog.results
    var page by catalog::page
    var totalPages by catalog::totalPages
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    // Se o serviço filtrado foi desmarcado nas configurações, volta para "todos".
    if (filter != -1 && providers.none { it.id == filter }) vm.homeProvider.value = -1
    val ids = if (filter == -1) providers.map { it.id }.toSet() else setOf(filter)

    suspend fun fetch(target: Int) {
        loading = true
        error = null
        try {
            val res = Tmdb.discover(key, type, ids, target, filters)
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

    LaunchedEffect(type, ids, filters, retry) {
        val query = Triple(type, ids, filters)
        // Mesma busca de antes (voltou dos Detalhes ou de outra aba): mantém o que já foi carregado.
        if (retry == 0 && catalog.query == query && results.isNotEmpty()) return@LaunchedEffect
        catalog.query = query
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
    LaunchedEffect(nearEnd, page, type, ids, filters, error) {
        if (nearEnd && !loading && error == null && page in 1 until totalPages) fetch(page + 1)
    }

    // TV: a entrada da grade fica na última capa com foco (se visível) ou na primeira visível.
    val panel = if (tv) LocalTvPanelFocus.current else null
    if (panel != null) {
        DisposableEffect(panel) {
            panel.gridReady = true
            onDispose { panel.gridReady = false }
        }
    }
    val entryIndex by remember {
        derivedStateOf {
            val visible = gridState.layoutInfo.visibleItemsInfo.map { it.index }
            val last = panel?.lastPosterId?.let { id -> results.indexOfFirst { it.id == id } } ?: -1
            if (last in visible) last else gridState.firstVisibleItemIndex
        }
    }
    // Ao voltar dos Detalhes (TV) e ao reabrir o app (celular e TV, via estado salvo), a grade rola até
    // o título aberto e, na TV, o foco vai para a capa.
    LaunchedEffect(results.size, loading) {
        if (page == 0 || loading) return@LaunchedEffect
        val target = when {
            panel?.restorePoster == true -> panel.lastPosterId
            vm.restoreOnStart -> vm.lastOpened.value
            else -> return@LaunchedEffect
        }
        val idx = results.indexOfFirst { it.id == target }
        // Ao reabrir o app só a 1ª página vem: busca as seguintes (até 6) até achar o título.
        // Fora deste efeito: a busca muda `loading`, que é chave dele, e o cancelaria no meio.
        if (idx < 0 && page < totalPages && page < 6) {
            scope.launch { fetch(page + 1) }
            return@LaunchedEffect
        }
        panel?.restorePoster = false
        vm.restoreOnStart = false
        if (idx >= 0) {
            panel?.lastPosterId = target
            gridState.scrollToItem(idx)
        }
        if (panel != null) {
            withFrameNanos { }
            runCatching { panel.grid.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Na TV, Filmes/Séries, serviços e filtros ficam no painel lateral (TvSidePanel).
        if (tv) {
            val title = providers.firstOrNull { it.id == filter }?.name ?: "Todos os seus serviços"
            TvHomeHeader(title, type, filters, genres)
        } else {
            PhoneHomeBar(vm, type, filter, providers, filters.activeCount(type)) { showFilters = true }
        }
        LazyVerticalGrid(
            state = gridState,
            columns = cells,
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().then(
                if (panel == null) Modifier
                else Modifier
                    // ◄ na primeira coluna volta sempre ao dial, em qualquer fileira.
                    .focusProperties { exit = { dir -> if (dir == FocusDirection.Left) panel.dial else FocusRequester.Default } }
                    .focusGroup()
            ),
        ) {
            itemsIndexed(results, key = { _, m -> m.id }) { i, m ->
                val posterModifier = if (panel == null) Modifier else Modifier
                    .then(if (i == entryIndex) Modifier.focusRequester(panel.grid) else Modifier)
                    .onFocusChanged { if (it.hasFocus) panel.lastPosterId = m.id }
                PosterCard(m.posterPath, m.displayTitle, m.year, modifier = posterModifier) {
                    panel?.restorePoster = true
                    vm.lastOpened.value = m.id
                    onOpen(type, m.id)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val err = error
                when {
                    loading -> Loading()
                    err != null -> ErrorBox(err) { if (page == 0) retry++ else error = null }
                    results.isEmpty() && page > 0 -> EmptyBox("Nada encontrado com esses filtros no Brasil.")
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            type = type,
            current = filters,
            genres = genres,
            onApply = { vm.filters.value = it; showFilters = false },
            onDismiss = { showFilters = false },
        )
    }
}

/** Celular: abas Filmes/Séries e a faixa com Filtros e os chips de serviço. */
@Composable
private fun PhoneHomeBar(
    vm: AppViewModel,
    type: String,
    filter: Int,
    providers: List<Provider>,
    activeFilters: Int,
    onFilters: () -> Unit,
) {
    TabRow(selectedTabIndex = if (type == "movie") 0 else 1) {
        Tab(selected = type == "movie", onClick = { vm.homeType.value = "movie" }, text = { Text("Filmes") })
        Tab(selected = type == "tv", onClick = { vm.homeType.value = "tv" }, text = { Text("Séries") })
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = activeFilters > 0,
                onClick = onFilters,
                label = { Text(if (activeFilters > 0) "Filtros ($activeFilters)" else "Filtros") },
                leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
            )
        }
        item {
            FilterChip(selected = filter == -1, onClick = { vm.homeProvider.value = -1 }, label = { Text("Todos") })
        }
        items(providers, key = { it.id }) { p ->
            FilterChip(
                selected = filter == p.id,
                onClick = { vm.homeProvider.value = p.id },
                label = { Text(p.name) },
                leadingIcon = { ProviderLogo(p.logoPath, p.name, size = 20) },
            )
        }
    }
}
