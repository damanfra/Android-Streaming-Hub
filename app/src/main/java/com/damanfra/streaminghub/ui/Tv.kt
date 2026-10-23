@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)

package com.damanfra.streaminghub.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.damanfra.streaminghub.AppViewModel
import com.damanfra.streaminghub.data.Filters
import com.damanfra.streaminghub.data.Genre
import com.damanfra.streaminghub.data.Jellyfin
import kotlinx.coroutines.delay
import kotlin.math.abs

/** true quando o app roda numa TV (Fire TV / Android TV). Definido uma vez na MainActivity. */
val LocalIsTv = staticCompositionLocalOf { false }

fun isTvDevice(context: Context): Boolean {
    val ui = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return ui.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

/** Na TV o realce de foco dos componentes do Material fica bem mais forte, para ser visto do sofá. */
val TvRipple = RippleConfiguration(
    color = Color.White,
    rippleAlpha = RippleAlpha(draggedAlpha = 0.16f, focusedAlpha = 0.28f, hoveredAlpha = 0.08f, pressedAlpha = 0.24f),
)

private val FocusRing = Color.White
private val SelectedBg = Color(0xFF4A4458)

/** Na TV, destaca o item com foco: moldura branca e leve aumento. Vai antes do clickable. */
@Composable
fun Modifier.tvFocusFrame(shape: Shape = RoundedCornerShape(8.dp), scale: Float = 1.06f): Modifier {
    if (!LocalIsTv.current) return this
    var focused by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (focused) scale else 1f, label = "focusScale")
    return this
        .onFocusChanged { focused = it.hasFocus }
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer { scaleX = s; scaleY = s }
        .then(if (focused) Modifier.border(3.dp, FocusRing, shape) else Modifier)
}

/**
 * Na TV, o campo de texto só recebe foco (e abre o teclado) quando o usuário aperta OK.
 * Navegando com o direcional, o foco para na moldura em volta dele.
 */
@Composable
fun TvTextFieldBox(modifier: Modifier = Modifier, field: @Composable (Modifier) -> Unit) {
    if (!LocalIsTv.current) {
        Box(modifier) { field(Modifier) }
        return
    }
    var editing by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    Box(modifier.tvFocusFrame(RoundedCornerShape(6.dp), scale = 1f).clickable { editing = true }) {
        field(
            Modifier
                .focusRequester(requester)
                .focusProperties { canFocus = editing }
                .onFocusChanged { if (editing && !it.hasFocus) editing = false }
        )
    }
    LaunchedEffect(editing) { if (editing) requester.requestFocus() }
}

private data class PanelItem(val route: String, val label: String, val icon: ImageVector)

private val panelItems = listOf(
    PanelItem("home", "Início", Icons.Filled.Home),
    PanelItem("search", "Buscar", Icons.Filled.Search),
    PanelItem("list", "Lista", Icons.Filled.Bookmark),
    PanelItem("apps", "Apps", Icons.Filled.Apps),
    PanelItem("settings", "Ajustes", Icons.Filled.Settings),
)

/** Rotas que mostram o painel lateral na TV. Detalhes e Configurações ocupam a tela toda. */
val tvPanelRoutes = setOf("home", "search", "list", "apps")

/**
 * Alvos de foco do painel e da grade do Início. Em vez de deixar a busca geométrica decidir
 * (que leva ao vizinho na mesma altura), ► do painel entra na grade pela capa [grid] e ◄ da
 * primeira coluna volta sempre ao [dial].
 */
class TvPanelFocus {
    val dial = FocusRequester()
    val nav = panelItems.associate { it.route to FocusRequester() }

    /** Fica na capa onde o usuário estava por último (ou na primeira visível). */
    val grid = FocusRequester()
    /** true enquanto a grade do Início está na tela; fora dela, ► segue a busca normal. */
    var gridReady = false
    var lastPosterId by mutableStateOf<Int?>(null)
    /** Igual a [lastPosterId], para as capas do Jellyfin (IDs em texto). */
    var lastJfKey by mutableStateOf<String?>(null)
    /** Ligado ao abrir um título: ao voltar dos Detalhes, o foco retorna à capa [lastPosterId]. */
    var restorePoster = false

    fun request(route: String?) {
        val ok = route == "home" && runCatching { dial.requestFocus() }.isSuccess
        if (!ok) runCatching { nav[route ?: "home"]?.requestFocus() }
    }
}

val LocalTvPanelFocus = staticCompositionLocalOf<TvPanelFocus?> { null }

/** Painel da esquerda na TV: seções, Filmes/Séries, os filtros e o dial de serviços. */
@Composable
fun TvSidePanel(
    vm: AppViewModel,
    route: String?,
    focus: TvPanelFocus,
    onNavigate: (String) -> Unit,
    onFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .focusProperties {
                exit = { dir -> if (dir == FocusDirection.Right && focus.gridReady) focus.grid else FocusRequester.Default }
            }
            .focusGroup()
            .width(272.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Streaming Hub",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            panelItems.forEach { item ->
                NavButton(
                    item = item,
                    selected = route == item.route,
                    onClick = { onNavigate(item.route) },
                    modifier = Modifier.weight(1f).focusRequester(focus.nav.getValue(item.route)),
                )
            }
        }
        if (route == "home") {
            val type by vm.homeType.collectAsState()
            val providers by vm.providers.collectAsState()
            val selected by vm.homeProvider.collectAsState()
            val filters by vm.filters.collectAsState()
            val jellyfin by vm.jellyfin.collectAsState()
            val onJellyfin = selected == Jellyfin.PROVIDER_ID

            TypeSwitch(type) { vm.homeType.value = it }
            // Filtros acima do dial: o dial consome ▲▼, então o que fica abaixo dele só se alcança depois do último serviço.
            val active = filters.activeCount(type)
            OutlinedButton(
                onClick = onFilters,
                // Os filtros são do catálogo do TMDB; não valem para a biblioteca do Jellyfin.
                enabled = !onJellyfin,
                modifier = Modifier.fillMaxWidth().tvFocusFrame(RoundedCornerShape(50), 1.03f),
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(if (active > 0) "  Filtros ($active)" else "  Filtros")
            }
            val entries = remember(providers, jellyfin != null) {
                listOf(DialEntry(-1, "Todos", null)) +
                    (if (jellyfin != null) listOf(DialEntry(Jellyfin.PROVIDER_ID, Jellyfin.NAME, null, Icons.Filled.VideoLibrary)) else emptyList()) +
                    providers.map { DialEntry(it.id, it.name, it.logoPath) }
            }
            ServiceDial(
                entries = entries,
                selected = selected,
                onSelect = { vm.homeProvider.value = it },
                modifier = Modifier.weight(1f).fillMaxWidth().focusRequester(focus.dial),
            )
        }
    }
}

@Composable
private fun NavButton(item: PanelItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .height(54.dp)
            .tvFocusFrame(shape, 1.08f)
            .clip(shape)
            .background(if (selected) SelectedBg else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(item.label, color = tint, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun TypeSwitch(type: String, onPick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF22242C)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf("movie" to "Filmes", "tv" to "Séries").forEach { (value, label) ->
            val on = type == value
            val shape = RoundedCornerShape(9.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .tvFocusFrame(shape, 1.04f)
                    .clip(shape)
                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onPick(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class DialEntry(val id: Int, val name: String, val logoPath: String?, val icon: ImageVector = Icons.Filled.Star)

/**
 * Dial giratório dos serviços: ▲▼ troca, o escolhido fica no centro e os vizinhos se afastam como numa roda.
 * O catálogo só recarrega quando o dial para por um instante, para não disparar uma busca a cada toque.
 */
@Composable
private fun ServiceDial(entries: List<DialEntry>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val index = entries.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    var pos by remember(entries) { mutableIntStateOf(index) }
    var focused by remember { mutableStateOf(false) }
    val anim by animateFloatAsState(pos.toFloat(), tween(220), label = "dial")

    LaunchedEffect(pos, entries) {
        delay(350)
        entries.getOrNull(pos)?.let { onSelect(it.id) }
    }

    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> if (pos > 0) { pos--; true } else false
                    Key.DirectionDown -> if (pos < entries.lastIndex) { pos++; true } else false
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        entries.forEachIndexed { i, entry ->
            val off = i - anim
            val dist = abs(off)
            if (dist < 2.6f) {
                DialItem(
                    entry = entry,
                    center = i == pos,
                    focused = focused && i == pos,
                    modifier = Modifier
                        .zIndex(3f - dist)
                        .graphicsLayer {
                            translationY = off * 58.dp.toPx()
                            val sc = 1f - 0.14f * dist
                            scaleX = sc
                            scaleY = sc
                            alpha = (1f - 0.36f * dist).coerceIn(0f, 1f)
                            rotationX = -off * 22f
                            cameraDistance = 12f * density
                        },
                )
            }
        }
        if (pos > 0) {
            Icon(Icons.Filled.KeyboardArrowUp, null, Modifier.align(Alignment.TopCenter).alpha(0.5f))
        }
        if (pos < entries.lastIndex) {
            Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.align(Alignment.BottomCenter).alpha(0.5f))
        }
    }
}

@Composable
private fun DialItem(entry: DialEntry, center: Boolean, focused: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val ring = when {
        focused -> Modifier.border(3.dp, FocusRing, shape)
        center -> Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), shape)
        else -> Modifier
    }
    Row(
        modifier
            .width(224.dp)
            .height(52.dp)
            .clip(shape)
            .background(if (center) Color(0xFF2A2C37) else Color(0xFF1E2029))
            .then(ring)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (entry.logoPath != null) {
            ProviderLogo(entry.logoPath, entry.name, size = 30)
        } else {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)).background(SelectedBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(entry.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            entry.name,
            fontSize = 15.sp,
            fontWeight = if (center) FontWeight.Medium else FontWeight.Normal,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Topo do Início na TV: serviço escolhido, Filmes/Séries e os filtros ativos. */
@Composable
fun TvHomeHeader(title: String, type: String, filters: Filters, genres: List<Genre>) {
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 24.dp, top = 20.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (type == "tv") "Séries" else "Filmes",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            filters.labels(type, genres).forEach { label ->
                Text(
                    label,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(CircleShape).background(Color(0xFF262833)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}
