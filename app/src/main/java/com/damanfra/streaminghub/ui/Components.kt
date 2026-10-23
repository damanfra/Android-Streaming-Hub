package com.damanfra.streaminghub.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import com.damanfra.streaminghub.AppViewModel
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.damanfra.streaminghub.data.Tmdb

private val HubColors = darkColorScheme(
    primary = Color(0xFFE5A00D),
    onPrimary = Color(0xFF1A1300),
    secondary = Color(0xFF9DB4D6),
    background = Color(0xFF0F1115),
    surface = Color(0xFF0F1115),
    surfaceVariant = Color(0xFF1D2129),
    surfaceContainer = Color(0xFF171A21),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingHubTheme(tv: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HubColors) {
        CompositionLocalProvider(
            LocalIsTv provides tv,
            LocalRippleConfiguration provides if (tv) TvRipple else LocalRippleConfiguration.current,
            content = content,
        )
    }
}

/** Capa + título + ano, usada nas grades. Com [onRemove], o toque longo mostra a opção "Remover". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    posterPath: String?,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier.tvFocusFrame().clip(RoundedCornerShape(8.dp)).combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                // Na TV a moldura de foco basta; o véu branco do realce apagaria a capa.
                indication = if (LocalIsTv.current) null else LocalIndication.current,
                onClick = onClick,
                onLongClick = if (onRemove != null) ({ menu = true }) else null,
            ).then(if (LocalIsTv.current) Modifier.padding(5.dp) else Modifier) // folga entre a moldura de foco e o texto
        ) {
            Poster(posterPath, title, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onRemove != null) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Remover") }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

@Composable
fun Poster(path: String?, title: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (path == null) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(6.dp),
            )
        } else {
            AsyncImage(
                // Caminho do TMDB ("/abc.jpg") ou URL completa (capas do Jellyfin).
                model = if (path.startsWith("http")) path else Tmdb.image(path),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun ProviderLogo(path: String?, name: String, size: Int = 28) {
    AsyncImage(
        model = Tmdb.image(path, "w92"),
        contentDescription = name,
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape(6.dp)),
    )
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
        if (onRetry != null) Button(onClick = onRetry) { Text("Tentar de novo") }
    }
}

@Composable
fun EmptyBox(message: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 6.dp))
}

fun typeLabel(type: String?) = if (type == "tv") "Série" else "Filme"

@Composable
fun isLandscape(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/** Colunas das grades de capas, conforme a orientação atual e o que foi escolhido nas Configurações. */
@Composable
fun posterCells(vm: AppViewModel): GridCells {
    val portrait by vm.colsPortrait.collectAsState()
    val landscape by vm.colsLandscape.collectAsState()
    val n = if (isLandscape()) landscape else portrait
    return when {
        n > 0 -> GridCells.Fixed(n)
        LocalIsTv.current -> GridCells.Fixed(5) // o painel lateral ocupa o espaço de duas colunas
        else -> GridCells.Adaptive(110.dp)
    }
}
