@file:OptIn(ExperimentalMaterial3Api::class)

package com.damanfra.streaminghub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.damanfra.streaminghub.AppViewModel
import com.damanfra.streaminghub.data.Jellyfin
import com.damanfra.streaminghub.data.JfItem
import com.damanfra.streaminghub.data.MediaItem
import com.damanfra.streaminghub.data.Tmdb
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun SearchScreen(vm: AppViewModel, onOpen: (String, Int) -> Unit, onOpenJellyfin: (String) -> Unit) {
    val key by vm.apiKey.collectAsState()
    val jellyfin by vm.jellyfin.collectAsState()
    val jfIndex by vm.jellyfinIndex.collectAsState()
    val jfResults = remember { mutableStateListOf<JfItem>() }
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember { mutableStateListOf<MediaItem>() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(query, key, jellyfin) {
        val k = key ?: return@LaunchedEffect
        val q = query.trim()
        if (q.length < 2) {
            jfResults.clear()
            results.clear(); error = null; searched = false; loading = false
            return@LaunchedEffect
        }
        delay(450) // espera o usuário parar de digitar
        loading = true
        try {
            // O servidor Jellyfin responde em paralelo; se falhar, a busca do TMDB segue normalmente.
            val jf = jellyfin?.let { s -> async { runCatching { Jellyfin.search(s, q) }.getOrDefault(emptyList()) } }
            val found = Tmdb.search(k, q)
            jfResults.clear()
            jf?.await()?.let { jfResults.addAll(it) }
            results.clear()
            results.addAll(found)
            error = null
            searched = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Erro na busca."
        } finally {
            if (currentCoroutineContext().isActive) loading = false
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Buscar") }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TvTextFieldBox(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { fieldModifier ->
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Filme ou série") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Limpar") }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = fieldModifier.fillMaxWidth(),
                )
            }
            val err = error
            when {
                err != null -> ErrorBox(err)
                loading && results.isEmpty() -> Loading()
                searched && results.isEmpty() && jfResults.isEmpty() -> EmptyBox("Nenhum resultado.")
                !searched -> EmptyBox("Busque em todos os serviços de uma vez.")
            }
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val s = jellyfin
                if (s != null && jfResults.isNotEmpty()) {
                    item(key = "jf-header") { SectionTitle("No seu Jellyfin") }
                    items(jfResults, key = { "jf-${it.id}" }) { j ->
                        SearchRow(
                            Jellyfin.posterUrl(s, j, 240), j.title,
                            listOfNotNull(if (j.isSeries) "Série" else "Filme", j.productionYear?.toString()).joinToString(" · "),
                        ) { onOpenJellyfin(j.id) }
                    }
                    if (results.isNotEmpty()) item(key = "tmdb-header") { SectionTitle("Em todos os serviços") }
                }
                items(results, key = { "${it.mediaType}-${it.id}" }) { m ->
                    val type = m.mediaType ?: "movie"
                    val local = if (Jellyfin.match(jfIndex, type, m.id) != null) "no seu Jellyfin" else null
                    SearchRow(m.posterPath, m.displayTitle, listOfNotNull(typeLabel(m.mediaType), m.year, local).joinToString(" · ")) {
                        onOpen(type, m.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(poster: String?, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(poster, title, Modifier.size(width = 60.dp, height = 90.dp))
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
