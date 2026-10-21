@file:OptIn(ExperimentalMaterial3Api::class)

package com.damanfra.streaminghub.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.damanfra.streaminghub.AppViewModel
import com.damanfra.streaminghub.data.Provider
import com.damanfra.streaminghub.data.StreamingApps
import com.damanfra.streaminghub.data.Tmdb
import kotlinx.coroutines.CancellationException

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val key by vm.apiKey.collectAsState()
    val selected by vm.providers.collectAsState()
    var draft by rememberSaveable(key) { mutableStateOf(key.orEmpty()) }
    var showKey by remember { mutableStateOf(false) }

    var all by remember { mutableStateOf<List<Provider>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(key, retry) {
        val k = key
        if (k.isNullOrBlank()) return@LaunchedEffect
        loading = true
        error = null
        try {
            all = Tmdb.providers(k)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Erro ao carregar serviços."
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Chave do TMDB", style = MaterialTheme.typography.titleMedium)
            Text(
                "Crie uma conta grátis no themoviedb.org e copie a \"Chave da API\" ou o \"Token de leitura\". " +
                    "É de lá que vêm o catálogo e a informação de onde cada título está.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Chave ou token") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Mostrar")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        vm.saveApiKey(draft)
                        Toast.makeText(context, "Chave salva", Toast.LENGTH_SHORT).show()
                    },
                    enabled = draft.isNotBlank() && draft.trim() != key,
                ) { Text("Salvar") }
                TextButton(onClick = { StreamingApps.openUrl(context, "https://www.themoviedb.org/settings/api") }) {
                    Text("Abrir site do TMDB")
                }
            }

            SectionTitle("Serviços que você assina (${selected.size})")
            val err = error
            when {
                key.isNullOrBlank() -> Text("Salve a chave para carregar a lista de serviços.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                loading -> Loading()
                err != null -> ErrorBox(err) { retry++ }
                else -> {
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        label = { Text("Filtrar serviços") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val selectedIds = selected.map { it.id }.toSet()
                    // Selecionados primeiro, depois os demais na ordem de relevância do TMDB.
                    val visible = all
                        .filter { filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true) }
                        .sortedByDescending { it.id in selectedIds }
                    visible.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.toggleProvider(p) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = p.id in selectedIds, onCheckedChange = { vm.toggleProvider(p) })
                            ProviderLogo(p.logoPath, p.name)
                            Text(p.name, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
            }
        }
    }
}
