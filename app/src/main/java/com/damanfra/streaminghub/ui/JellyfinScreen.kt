@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)

package com.damanfra.streaminghub.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.damanfra.streaminghub.AppViewModel
import com.damanfra.streaminghub.PlayerActivity
import com.damanfra.streaminghub.data.Jellyfin
import com.damanfra.streaminghub.data.JfItem
import com.damanfra.streaminghub.data.JfServer
import com.damanfra.streaminghub.data.JfSession
import com.damanfra.streaminghub.data.RecentItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** "1:02:33" ou "12:05". */
private fun clock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun remaining(item: JfItem): String? {
    val left = item.runtimeMs - item.resumeMs
    return if (item.resumeMs > 0 && left > 0) "faltam ${maxOf(1, left / 60_000)} min" else null
}

private sealed interface JfEntry { val key: String }
private data class JfHeader(override val key: String, val text: String) : JfEntry
private data class JfCard(override val key: String, val item: JfItem, val resume: Boolean) : JfEntry

/**
 * Início com o Jellyfin escolhido: "Continuar assistindo" (que o servidor registra) e a biblioteca,
 * das adições mais recentes para as mais antigas. Na TV segue as mesmas regras de foco da grade do TMDB.
 */
@Composable
fun JellyfinHomeGrid(vm: AppViewModel, s: JfSession, type: String, cells: GridCells, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val c = vm.jfCatalog
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    suspend fun fetchPage() {
        loading = true
        error = null
        try {
            val res = Jellyfin.library(s, type, c.items.size)
            val existing = c.items.map { it.id }.toHashSet()
            c.items.addAll(res.items.filter { it.id !in existing })
            c.total = res.totalRecordCount
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Erro ao carregar o Jellyfin."
        } finally {
            if (currentCoroutineContext().isActive) loading = false
        }
    }

    LaunchedEffect(s, type, retry) {
        val query = Triple(s.server, s.userId, type)
        if (retry == 0 && c.query == query && c.total >= 0) return@LaunchedEffect
        c.query = query
        c.items.clear()
        c.resume.clear()
        c.total = -1
        gridState.scrollToItem(0)
        fetchPage()
    }
    // "Continuar assistindo" muda a cada vídeo: recarrega sempre que a tela volta (ex.: ao sair do player).
    LifecycleResumeEffect(s, type) {
        val job = scope.launch {
            runCatching { Jellyfin.continueWatching(s, type) }.onSuccess {
                c.resume.clear()
                c.resume.addAll(it)
            }
        }
        onPauseOrDispose { job.cancel() }
    }

    val entries by remember(type) {
        derivedStateOf {
            buildList {
                if (c.resume.isNotEmpty()) {
                    add(JfHeader("h-resume", "Continuar assistindo"))
                    c.resume.forEach { add(JfCard("r-${it.id}", it, resume = true)) }
                }
                if (c.items.isNotEmpty()) {
                    add(JfHeader("h-lib", if (type == "tv") "Séries no servidor" else "Filmes no servidor"))
                    c.items.forEach { add(JfCard("l-${it.id}", it, resume = false)) }
                }
            }
        }
    }

    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, c.items.size, c.total, error) {
        if (nearEnd && !loading && error == null && c.total >= 0 && c.items.size < c.total) fetchPage()
    }

    // TV: mesma lógica da grade do TMDB (entrada pela última capa com foco, ◄ volta ao dial).
    val panel = if (LocalIsTv.current) LocalTvPanelFocus.current else null
    // panel.gridReady fica a cargo do HomeContent, que envolve as duas grades.
    val entryIndex by remember {
        derivedStateOf {
            val visible = gridState.layoutInfo.visibleItemsInfo.map { it.index }.filter { entries.getOrNull(it) is JfCard }
            val last = panel?.lastJfKey?.let { k -> entries.indexOfFirst { it.key == k } } ?: -1
            if (last in visible) last else visible.firstOrNull() ?: -1
        }
    }
    LaunchedEffect(entries.size) {
        if (panel?.restorePoster != true || entries.isEmpty()) return@LaunchedEffect
        panel.restorePoster = false
        val idx = entries.indexOfFirst { it.key == panel.lastJfKey }
        if (idx >= 0) gridState.scrollToItem(idx)
        withFrameNanos { }
        runCatching { panel.grid.requestFocus() }
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
                .focusProperties { exit = { dir -> if (dir == FocusDirection.Left) panel.dial else FocusRequester.Default } }
                .focusGroup()
        ),
    ) {
        itemsIndexed(
            entries,
            key = { _, e -> e.key },
            span = { _, e -> if (e is JfHeader) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
        ) { i, e ->
            when (e) {
                is JfHeader -> Text(e.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                is JfCard -> {
                    val m = e.item
                    val posterModifier = if (panel == null) Modifier else Modifier
                        .then(if (i == entryIndex) Modifier.focusRequester(panel.grid) else Modifier)
                        .onFocusChanged { if (it.hasFocus) panel.lastJfKey = e.key }
                    val title = if (m.isEpisode) m.seriesName ?: m.title else m.title
                    val subtitle = if (e.resume) {
                        listOfNotNull(m.episodeLabel, remaining(m)).joinToString(" · ").ifEmpty { null }
                    } else m.productionYear?.toString()
                    PosterCard(Jellyfin.posterUrl(s, m), title, subtitle, modifier = posterModifier) {
                        if (e.resume) {
                            // Continuar assistindo: um OK e já toca do ponto onde parou.
                            PlayerActivity.start(context, m.id, m.resumeMs)
                        } else {
                            panel?.restorePoster = true
                            onOpen(m.id)
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            val err = error
            when {
                loading -> Loading()
                err != null -> ErrorBox(err) { if (c.total < 0) retry++ else error = null }
                c.total == 0 && c.resume.isEmpty() -> EmptyBox(
                    if (type == "tv") "Nenhuma série no servidor ainda." else "Nenhum filme no servidor ainda."
                )
            }
        }
    }
}

/** Abre o player e, quando o título tem ID do TMDB, registra em Recentes. */
private fun playJellyfin(context: Context, vm: AppViewModel, s: JfSession, show: JfItem, playId: String, startMs: Long) {
    show.tmdbId?.let { tmdb ->
        vm.addRecent(
            RecentItem(
                id = tmdb, type = if (show.isSeries) "tv" else "movie", title = show.title,
                posterPath = Jellyfin.posterUrl(s, show), year = show.productionYear?.toString(),
                app = Jellyfin.NAME, openedAt = System.currentTimeMillis(),
            )
        )
    }
    PlayerActivity.start(context, playId, startMs)
}

/** Título do servidor Jellyfin: filme (Assistir/Continuar) ou série (temporadas e episódios). */
@Composable
fun JellyfinDetailScreen(vm: AppViewModel, id: String, onBack: () -> Unit, onTmdb: (String, Int) -> Unit) {
    val session by vm.jellyfin.collectAsState()
    val s = session
    val tv = LocalIsTv.current
    var item by remember { mutableStateOf<JfItem?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var seasons by remember { mutableStateOf<List<JfItem>>(emptyList()) }
    var season by rememberSaveable { mutableStateOf<String?>(null) }
    var episodes by remember { mutableStateOf<List<JfItem>?>(null) }
    var next by remember { mutableStateOf<JfItem?>(null) }
    // Sobe a cada volta para a tela (ex.: saindo do player), para atualizar posição e "assistido".
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }

    LaunchedEffect(s, id, retry, refresh) {
        if (s == null) return@LaunchedEffect
        try {
            val it = Jellyfin.item(s, id)
            item = it
            error = null
            if (it.isSeries) {
                if (seasons.isEmpty()) seasons = Jellyfin.seasons(s, id)
                next = runCatching { Jellyfin.nextUp(s, id) }.getOrNull()
                if (season == null) season = next?.seasonId ?: seasons.firstOrNull()?.id
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Erro ao carregar."
        }
    }
    LaunchedEffect(s, id, season, refresh) {
        val ss = season ?: return@LaunchedEffect
        if (s == null) return@LaunchedEffect
        episodes = runCatching { Jellyfin.episodes(s, id, ss) }.getOrNull() ?: episodes
    }

    val body: @Composable (Modifier) -> Unit = { mod ->
        val it = item
        val err = error
        when {
            s == null -> EmptyBox("Conecte o Jellyfin em Configurações.")
            it != null -> JellyfinDetailBody(vm, s, it, seasons, season, { season = it }, episodes, next, onTmdb, onBack, mod)
            err != null -> ErrorBox(err) { retry++ }
            else -> Loading()
        }
    }
    if (tv) {
        body(Modifier.fillMaxSize())
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
            )
        },
    ) { pad -> body(Modifier.padding(pad).fillMaxSize()) }
}

@Composable
private fun JellyfinDetailBody(
    vm: AppViewModel,
    s: JfSession,
    show: JfItem,
    seasons: List<JfItem>,
    season: String?,
    onSeason: (String) -> Unit,
    episodes: List<JfItem>?,
    next: JfItem?,
    onTmdb: (String, Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val tv = LocalIsTv.current
    val firstAction = remember { FocusRequester() }
    LaunchedEffect(show.id) {
        withFrameNanos { }
        runCatching { firstAction.requestFocus() }
    }
    val pill = RoundedCornerShape(50)

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(horizontal = if (tv) 32.dp else 16.dp, vertical = if (tv) 24.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (tv) {
            item {
                OutlinedButton(onClick = onBack, modifier = Modifier.tvFocusFrame(pill, 1.05f)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Voltar")
                }
            }
        }
        item {
            Row {
                Poster(
                    Jellyfin.posterUrl(s, show),
                    show.title,
                    Modifier.width(if (tv) 200.dp else 120.dp).aspectRatio(2f / 3f),
                )
                Spacer(Modifier.width(if (tv) 24.dp else 14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(show.title, style = if (tv) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val runtime = show.runtimeMs.takeIf { it > 0 }?.let { "${it / 3_600_000}h ${(it / 60_000) % 60}min" }
                    Text(
                        listOfNotNull(
                            if (show.isSeries) "Série" else "Filme",
                            show.productionYear?.toString(),
                            runtime.takeIf { !show.isSeries },
                            show.officialRating,
                        ).joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    show.communityRating?.takeIf { it > 0 }?.let {
                        Text("★ %.1f".format(it), color = MaterialTheme.colorScheme.primary)
                    }
                    if (show.genres.isNotEmpty()) Text(show.genres.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                    if (show.userData?.played == true) Text("✓ Assistido", color = MaterialTheme.colorScheme.secondary)

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        val play = Modifier.focusRequester(firstAction).tvFocusFrame(pill, 1.05f)
                        if (show.isSeries) {
                            if (next != null) {
                                Button(onClick = { playJellyfin(context, vm, s, show, next.id, next.resumeMs) }, modifier = play) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    val label = next.episodeLabel ?: next.title
                                    Text(if (next.resumeMs > 0) "Continuar $label (${clock(next.resumeMs)})" else "Assistir $label")
                                }
                            }
                        } else if (show.resumeMs > 0) {
                            Button(onClick = { playJellyfin(context, vm, s, show, show.id, show.resumeMs) }, modifier = play) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Text("Continuar de ${clock(show.resumeMs)}")
                            }
                            OutlinedButton(
                                onClick = { playJellyfin(context, vm, s, show, show.id, 0) },
                                modifier = Modifier.tvFocusFrame(pill, 1.05f),
                            ) { Text("Do início") }
                        } else {
                            Button(onClick = { playJellyfin(context, vm, s, show, show.id, 0) }, modifier = play) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Text("Assistir")
                            }
                        }
                        show.tmdbId?.let { tmdb ->
                            OutlinedButton(
                                onClick = { onTmdb(if (show.isSeries) "tv" else "movie", tmdb) },
                                modifier = Modifier
                                    .then(if (show.isSeries && next == null) Modifier.focusRequester(firstAction) else Modifier)
                                    .tvFocusFrame(pill, 1.05f),
                            ) { Text("Mais informações") }
                        }
                    }
                }
            }
        }
        if (!show.overview.isNullOrBlank()) {
            item { Text(show.overview, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
        }
        if (show.isSeries) {
            if (seasons.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(seasons, key = { it.id }) { ss ->
                            FilterChip(
                                selected = ss.id == season,
                                onClick = { onSeason(ss.id) },
                                label = { Text(ss.title) },
                                modifier = Modifier.tvFocusFrame(RoundedCornerShape(8.dp), 1.04f),
                            )
                        }
                    }
                }
            } else if (seasons.size == 1) {
                item { SectionTitle(seasons[0].title) }
            }
            when {
                episodes == null -> item { Loading() }
                episodes.isEmpty() -> item { EmptyBox("Nenhum episódio nesta temporada.") }
                else -> items(episodes, key = { it.id }) { ep ->
                    EpisodeRow(s, ep) { playJellyfin(context, vm, s, show, ep.id, ep.resumeMs) }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(s: JfSession, ep: JfItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tvFocusFrame(RoundedCornerShape(8.dp), 1.02f)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(Jellyfin.thumbUrl(s, ep), ep.title, Modifier.width(150.dp).aspectRatio(16f / 9f))
        Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                listOfNotNull(ep.indexNumber?.let { "$it." }, ep.title).joinToString(" "),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ep.runtimeMs.takeIf { it > 0 }?.let {
                    Text("${it / 60_000} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                remaining(ep)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                if (ep.userData?.played == true) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Assistido", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                }
            }
            if (!ep.overview.isNullOrBlank()) {
                Text(ep.overview, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Configurações → Jellyfin. Entra com usuário e senha ou pelo Quick Connect (o app mostra um código e o
 * usuário aprova no Jellyfin, sem digitar senha com o controle). "Procurar na rede" acha o servidor sozinho.
 */
@Composable
fun JellyfinSettings(vm: AppViewModel) {
    val session by vm.jellyfin.collectAsState()
    val index by vm.jellyfinIndex.collectAsState()
    val scope = rememberCoroutineScope()
    val current = session

    SectionTitle("Jellyfin (mídia própria)")
    if (current != null) {
        Text("Conectado a ${current.serverName.ifBlank { Jellyfin.NAME }} (${current.server}) como ${current.userName}.")
        val movies = index.keys.count { it.startsWith("movie:tmdb:") }
        val shows = index.keys.count { it.startsWith("tv:tmdb:") }
        Text(
            "Reconhecidos pelo TMDB: $movies filmes e $shows séries.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.refreshJellyfinIndex() }, modifier = Modifier.tvFocusFrame(RoundedCornerShape(50), 1.04f)) {
                Text("Atualizar biblioteca")
            }
            TextButton(onClick = { vm.setJellyfin(null) }, modifier = Modifier.tvFocusFrame(RoundedCornerShape(50), 1.04f)) {
                Text("Desconectar")
            }
        }
        return
    }

    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<List<JfServer>>(emptyList()) }
    var code by remember { mutableStateOf<String?>(null) }
    val deviceId = rememberSaveable { Jellyfin.newDeviceId() }

    Text(
        "Mostra a biblioteca do seu servidor como mais um serviço no Início e toca os vídeos aqui mesmo.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TvTextFieldBox(Modifier.fillMaxWidth()) { fm ->
        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text("Endereço do servidor (ex.: 192.168.0.10:8096)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = fm.fillMaxWidth(),
        )
    }
    OutlinedButton(
        enabled = !busy,
        onClick = {
            scope.launch {
                busy = true
                status = "Procurando servidores na rede…"
                found = Jellyfin.discover()
                status = if (found.isEmpty()) "Nenhum servidor respondeu. Digite o endereço." else null
                if (found.size == 1) server = found[0].address
                busy = false
            }
        },
        modifier = Modifier.tvFocusFrame(RoundedCornerShape(50), 1.04f),
    ) { Text("Procurar na rede") }
    if (found.size > 1) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            found.forEach { f ->
                FilterChip(selected = server == f.address, onClick = { server = f.address }, label = { Text("${f.name} · ${f.address}") })
            }
        }
    }
    TvTextFieldBox(Modifier.fillMaxWidth()) { fm ->
        OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Usuário") }, singleLine = true, modifier = fm.fillMaxWidth())
    }
    TvTextFieldBox(Modifier.fillMaxWidth()) { fm ->
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Senha (pode ficar vazia)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = fm.fillMaxWidth(),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            enabled = !busy && server.isNotBlank() && user.isNotBlank(),
            onClick = {
                scope.launch {
                    busy = true
                    status = "Conectando…"
                    try {
                        vm.setJellyfin(Jellyfin.login(Jellyfin.normalizeServer(server), user.trim(), password, deviceId))
                        status = null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        status = e.message ?: "Não foi possível conectar."
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier.tvFocusFrame(RoundedCornerShape(50), 1.04f),
        ) { Text("Entrar") }
        OutlinedButton(
            enabled = !busy && server.isNotBlank(),
            onClick = {
                scope.launch {
                    busy = true
                    val address = Jellyfin.normalizeServer(server)
                    try {
                        val qc = Jellyfin.quickConnectStart(address, deviceId)
                        code = qc.code
                        status = null
                        // O código vale por alguns minutos; confere a cada 3 s se já foi aprovado.
                        var approved = false
                        repeat(100) {
                            if (approved) return@repeat
                            delay(3000)
                            approved = Jellyfin.quickConnectApproved(address, deviceId, qc.secret)
                        }
                        if (!approved) throw IllegalStateException("O código expirou. Tente de novo.")
                        vm.setJellyfin(Jellyfin.quickConnectLogin(address, deviceId, qc.secret))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        status = e.message ?: "Quick Connect falhou."
                    } finally {
                        code = null
                        busy = false
                    }
                }
            },
            modifier = Modifier.tvFocusFrame(RoundedCornerShape(50), 1.04f),
        ) { Text("Quick Connect") }
    }
    code?.let {
        Text("Código: $it", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(
            "No Jellyfin (navegador ou outro app já logado), abra seu perfil → Quick Connect e digite este código.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
