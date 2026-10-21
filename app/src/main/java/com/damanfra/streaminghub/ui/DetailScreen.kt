@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.damanfra.streaminghub.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.damanfra.streaminghub.AppViewModel
import com.damanfra.streaminghub.data.JustWatch
import com.damanfra.streaminghub.data.MediaDetail
import com.damanfra.streaminghub.data.Provider
import com.damanfra.streaminghub.data.RecentItem
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.damanfra.streaminghub.data.SavedItem
import com.damanfra.streaminghub.data.StreamingApps
import com.damanfra.streaminghub.data.Tmdb
import kotlinx.coroutines.CancellationException
import java.util.Locale

@Composable
fun DetailScreen(vm: AppViewModel, type: String, id: Int, onBack: () -> Unit) {
    val key by vm.apiKey.collectAsState()
    var detail by remember { mutableStateOf<MediaDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(key, type, id, retry) {
        val k = key ?: return@LaunchedEffect
        error = null
        try {
            detail = Tmdb.detail(k, type, id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Erro ao carregar."
        }
    }

    if (LocalIsTv.current) {
        val d = detail
        val err = error
        when {
            d != null -> TvDetailBody(vm, type, d, onBack)
            err != null -> ErrorBox(err) { retry++ }
            else -> Loading()
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.displayTitle ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val d = detail
            val err = error
            when {
                d != null -> DetailBody(vm, type, d)
                err != null -> ErrorBox(err) { retry++ }
                else -> Loading()
            }
        }
    }
}

/** O que as duas versões da tela (celular e TV) precisam saber sobre onde o título está. */
private class DetailInfo(
    val item: SavedItem,
    val saved: SavedItem?,
    /** Serviços que o usuário assina, um por app. */
    val mine: List<Provider>,
    val others: List<Provider>,
    val rentBuy: List<Provider>,
    val hasStreaming: Boolean,
    val jw: JustWatch.Info,
    val imdbId: String?,
    /** Página da JustWatch com os links de todos os serviços. */
    val providersPage: String?,
)

@Composable
private fun rememberDetailInfo(vm: AppViewModel, type: String, d: MediaDetail): DetailInfo {
    val myProviders by vm.providers.collectAsState()
    val watchlist by vm.watchlist.collectAsState()
    val saved = watchlist.firstOrNull { it.id == d.id && it.type == type }
    val item = SavedItem(id = d.id, type = type, title = d.displayTitle, posterPath = d.posterPath, year = d.year)

    val br = d.watchProviders?.results?.get(Tmdb.REGION)
    val myIds = myProviders.map { it.id }.toSet()
    val myNames = myProviders.map { it.name.lowercase() }.toSet()
    val streaming = br?.streaming.orEmpty()
    // Um serviço conta como "seu" se o ID bater ou se for outra variante do mesmo app (ex.: plano com anúncios).
    fun isMine(p: Provider): Boolean {
        if (p.id in myIds) return true
        val app = StreamingApps.forProvider(p.name) ?: return false
        return myNames.any { StreamingApps.forProvider(it) == app }
    }
    // Um botão por app: "Netflix" e "Netflix Standard with Ads" abrem o mesmo lugar.
    fun appKey(p: Provider) = StreamingApps.forProvider(p.name)?.name ?: p.name
    val mine = streaming.filter { isMine(it) }.distinctBy { appKey(it) }
    val mineKeys = mine.map { appKey(it) }.toSet()
    val others = streaming.filterNot { isMine(it) || appKey(it) in mineKeys }.distinctBy { appKey(it) }
    val rentBuy = (br?.rent.orEmpty() + br?.buy.orEmpty()).distinctBy { it.id }

    // JustWatch, em segundo plano: links diretos por serviço e nota do IMDb.
    var jw by remember(d.id) { mutableStateOf(JustWatch.Info()) }
    LaunchedEffect(d.id, type) {
        jw = JustWatch.lookup(d.id, type, listOfNotNull(d.displayTitle, d.originalTitle, d.originalName))
    }
    return DetailInfo(
        item = item, saved = saved, mine = mine, others = others, rentBuy = rentBuy,
        hasStreaming = streaming.isNotEmpty(), jw = jw, imdbId = d.imdbId ?: jw.imdbId, providersPage = br?.link,
    )
}

/** Registra em Recentes e abre o título no app do serviço. */
private fun openIn(context: Context, vm: AppViewModel, type: String, d: MediaDetail, p: Provider, info: DetailInfo) {
    val app = StreamingApps.forProvider(p.name)
    vm.addRecent(
        RecentItem(
            id = d.id, type = type, title = d.displayTitle, posterPath = d.posterPath,
            year = d.year, app = app?.name ?: p.name, openedAt = System.currentTimeMillis(),
        )
    )
    val msg = StreamingApps.openTitle(context, p.name, d.displayTitle, app?.let { info.jw.links[it] })
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}

/** "Filme · 2026 · 1h 49min" ou "Série · 2019 · 3 temporadas". */
private fun metaLine(type: String, d: MediaDetail): String {
    val extra = when {
        type == "tv" && d.seasons != null -> if (d.seasons == 1) "1 temporada" else "${d.seasons} temporadas"
        d.runtime != null && d.runtime > 0 -> "${d.runtime / 60}h ${d.runtime % 60}min"
        else -> null
    }
    return listOfNotNull(typeLabel(type), d.year, extra).joinToString(" · ")
}

@Composable
private fun DetailBody(vm: AppViewModel, type: String, d: MediaDetail) {
    val context = LocalContext.current
    val info = rememberDetailInfo(vm, type, d)
    val saved = info.saved
    val item = info.item
    val mine = info.mine
    val others = info.others
    val rentBuy = info.rentBuy
    val jw = info.jw
    val imdbId = info.imdbId

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row {
            Poster(d.posterPath, d.displayTitle, Modifier.size(width = 120.dp, height = 180.dp))
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(d.displayTitle, style = MaterialTheme.typography.titleLarge)
                Text(
                    metaLine(type, d),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val tmdbScore = d.voteAverage?.takeIf { it > 0 }
                val imdbScore = jw.imdbScore
                if (tmdbScore != null || imdbScore != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (imdbScore != null) {
                            Text("IMDb ★ ${score(imdbScore)}", color = MaterialTheme.colorScheme.primary)
                        }
                        if (tmdbScore != null) {
                            Text("TMDB ★ ${score(tmdbScore)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (d.genres.isNotEmpty()) {
                    Text(d.genres.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodySmall)
                }
                if (!d.tagline.isNullOrBlank()) {
                    Text(d.tagline, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            FilledTonalButton(onClick = { vm.toggleSaved(item) }) {
                Icon(if (saved != null) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (saved != null) "Na lista" else "Quero ver")
            }
            OutlinedButton(onClick = { vm.setWatched(item, !(saved?.watched ?: false)) }) {
                Icon(
                    if (saved?.watched == true) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                )
                Spacer(Modifier.width(6.dp))
                Text(if (saved?.watched == true) "Assistido" else "Marcar assistido")
            }
        }

        SectionTitle("Onde assistir")
        if (mine.isEmpty()) {
            Text(
                if (!info.hasStreaming) "Não está em nenhum streaming por assinatura no Brasil agora."
                else "Não está nos serviços que você assina.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        mine.forEach { p ->
            val app = StreamingApps.forProvider(p.name)
            Button(
                onClick = { openIn(context, vm, type, d, p, info) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                ProviderLogo(p.logoPath, p.name, size = 24)
                Spacer(Modifier.width(10.dp))
                Text("Abrir no ${app?.name ?: p.name}")
            }
        }
        if (others.isNotEmpty()) {
            Text("Também em (você não assina):", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            ProviderRow(others)
        }
        if (rentBuy.isNotEmpty()) {
            Text("Aluguel ou compra:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            ProviderRow(rentBuy)
        }
        info.providersPage?.let { link ->
            TextButton(onClick = { StreamingApps.openUrl(context, link) }) {
                Text("Ver links diretos de cada serviço")
            }
        }

        if (!d.overview.isNullOrBlank()) {
            SectionTitle("Sinopse")
            Text(d.overview, style = MaterialTheme.typography.bodyMedium)
        }

        val cast = d.mainCast()
        val makers = if (type == "tv") d.createdBy.map { it.name } else d.directors()
        if (cast.isNotEmpty() || makers.isNotEmpty()) {
            SectionTitle("Elenco e equipe")
            if (makers.isNotEmpty()) {
                CreditLine(if (type == "tv") "Criação" else "Direção", makers.joinToString(", "))
            }
            if (cast.isNotEmpty()) {
                CreditLine("Elenco", cast.joinToString(", "))
            }
        }

        if (imdbId != null) {
            OutlinedButton(onClick = { StreamingApps.openImdb(context, imdbId) }, modifier = Modifier.padding(top = 8.dp)) {
                Text(jw.imdbScore?.let { "Ver no IMDb · ★ ${score(it)}" } ?: "Ver no IMDb")
            }
        }

        Text(
            "Disponibilidade: JustWatch, via TMDB.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

private fun score(v: Double) = String.format(Locale.US, "%.1f", v)

@Composable
private fun CreditLine(label: String, names: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("$label: ") }
            append(names)
        },
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ProviderRow(list: List<Provider>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProviderLogo(p.logoPath, p.name, size = 22)
                Text(p.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

/**
 * TV: três colunas sem rolagem. Capa grande à esquerda, informações no meio e, à direita, a coluna
 * "Assistir em" com um botão por serviço assinado (▲▼ entre eles; o foco começa no primeiro).
 */
@Composable
private fun TvDetailBody(vm: AppViewModel, type: String, d: MediaDetail, onBack: () -> Unit) {
    val context = LocalContext.current
    val info = rememberDetailInfo(vm, type, d)
    val saved = info.saved
    val firstAction = remember { FocusRequester() }

    LaunchedEffect(d.id) {
        withFrameNanos { }
        runCatching { firstAction.requestFocus() }
    }

    Row(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(Modifier.width(230.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.height(40.dp).tvFocusFrame(RoundedCornerShape(50), 1.05f)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Voltar")
            }
            Poster(d.posterPath, d.displayTitle, Modifier.width(230.dp).height(345.dp))
        }

        Column(
            Modifier.weight(1f).fillMaxHeight().padding(top = 52.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(d.displayTitle, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(metaLine(type, d), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                info.jw.imdbScore?.let {
                    Text("IMDb ★ ${score(it)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
                d.voteAverage?.takeIf { it > 0 }?.let {
                    Text("TMDB ★ ${score(it)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (d.genres.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.genres.take(4).forEach { g ->
                        Text(
                            g.name,
                            fontSize = 11.sp,
                            modifier = Modifier.clip(CircleShape).background(Color(0xFF262833)).padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            if (!d.overview.isNullOrBlank()) {
                Text(
                    d.overview,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    maxLines = 7,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            val cast = d.mainCast()
            val makers = if (type == "tv") d.createdBy.map { it.name } else d.directors()
            if (makers.isNotEmpty()) CreditLine(if (type == "tv") "Criação" else "Direção", makers.joinToString(", "))
            if (cast.isNotEmpty()) CreditLine("Elenco", cast.joinToString(", "))
            Spacer(Modifier.weight(1f))
            if (info.rentBuy.isNotEmpty()) {
                Text(
                    "Aluguel ou compra: " + info.rentBuy.joinToString(", ") { it.name },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("Disponibilidade: JustWatch, via TMDB.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Column(
            Modifier.width(210.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 52.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("ASSISTIR EM", fontSize = 11.sp, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (info.mine.isEmpty()) {
                Text(
                    if (!info.hasStreaming) "Não está em nenhum streaming por assinatura no Brasil agora."
                    else "Não está nos serviços que você assina.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            info.mine.forEachIndexed { i, p ->
                val app = StreamingApps.forProvider(p.name)
                Button(
                    onClick = { openIn(context, vm, type, d, p, info) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .then(if (i == 0) Modifier.focusRequester(firstAction) else Modifier)
                        .tvFocusFrame(RoundedCornerShape(10.dp), 1.04f),
                ) {
                    ProviderLogo(p.logoPath, p.name, size = 24)
                    Spacer(Modifier.width(10.dp))
                    Text(app?.name ?: p.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                }
            }
            if (info.others.isNotEmpty()) {
                Text(
                    "Também em (você não assina): " + info.others.joinToString(", ") { it.name },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            FilledTonalButton(
                onClick = { vm.toggleSaved(info.item) },
                modifier = Modifier.fillMaxWidth()
                    .then(if (info.mine.isEmpty()) Modifier.focusRequester(firstAction) else Modifier)
                    .tvFocusFrame(RoundedCornerShape(50), 1.04f),
            ) {
                Icon(if (saved != null) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (saved != null) "Na lista" else "Quero ver")
            }
            OutlinedButton(
                onClick = { vm.setWatched(info.item, !(saved?.watched ?: false)) },
                modifier = Modifier.fillMaxWidth().tvFocusFrame(RoundedCornerShape(50), 1.04f),
            ) {
                Icon(
                    if (saved?.watched == true) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (saved?.watched == true) "Assistido" else "Marcar assistido")
            }
            info.imdbId?.let { imdb ->
                OutlinedButton(
                    onClick = { StreamingApps.openImdb(context, imdb) },
                    modifier = Modifier.fillMaxWidth().tvFocusFrame(RoundedCornerShape(50), 1.04f),
                ) {
                    Text(info.jw.imdbScore?.let { "Ver no IMDb · ★ ${score(it)}" } ?: "Ver no IMDb")
                }
            }
        }
    }
}
