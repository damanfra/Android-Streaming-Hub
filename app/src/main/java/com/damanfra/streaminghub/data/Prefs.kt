package com.damanfra.streaminghub.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Tudo que o app guarda no aparelho: chave do TMDB, serviços assinados, Minha lista e o estado do Início. */
class Prefs(private val context: Context) {
    private object Keys {
        val API_KEY = stringPreferencesKey("api_key")
        val PROVIDERS = stringPreferencesKey("providers")
        val WATCHLIST = stringPreferencesKey("watchlist")
        val RECENTS = stringPreferencesKey("recents")
        val COLS_PORTRAIT = intPreferencesKey("cols_portrait")
        val COLS_LANDSCAPE = intPreferencesKey("cols_landscape")
        val HOME = stringPreferencesKey("home_state")
    }

    /** Estado do Início salvo da última vez; null na primeira abertura (ou se o formato mudou). */
    suspend fun loadHome(): HomeState? =
        context.dataStore.data.first()[Keys.HOME]?.let { runCatching { json.decodeFromString<HomeState>(it) }.getOrNull() }

    suspend fun saveHome(state: HomeState) {
        context.dataStore.edit { it[Keys.HOME] = json.encodeToString(state) }
    }

    /** Capas por linha; 0 = automático (pelo tamanho da tela). */
    val colsPortrait: Flow<Int> = context.dataStore.data.map { it[Keys.COLS_PORTRAIT] ?: 0 }
    val colsLandscape: Flow<Int> = context.dataStore.data.map { it[Keys.COLS_LANDSCAPE] ?: 0 }

    suspend fun setColumns(landscape: Boolean, value: Int) {
        context.dataStore.edit { it[if (landscape) Keys.COLS_LANDSCAPE else Keys.COLS_PORTRAIT] = value }
    }

    private val json = Tmdb.json

    val apiKey: Flow<String> = context.dataStore.data.map { it[Keys.API_KEY].orEmpty() }

    val providers: Flow<List<Provider>> = context.dataStore.data.map { decodeProviders(it[Keys.PROVIDERS]) }

    val watchlist: Flow<List<SavedItem>> = context.dataStore.data.map { decodeList(it[Keys.WATCHLIST]) }

    suspend fun setApiKey(value: String) {
        context.dataStore.edit { it[Keys.API_KEY] = value.trim() }
    }

    suspend fun updateProviders(transform: (List<Provider>) -> List<Provider>) {
        context.dataStore.edit { p ->
            p[Keys.PROVIDERS] = json.encodeToString(transform(decodeProviders(p[Keys.PROVIDERS])))
        }
    }

    suspend fun updateWatchlist(transform: (List<SavedItem>) -> List<SavedItem>) {
        context.dataStore.edit { p ->
            p[Keys.WATCHLIST] = json.encodeToString(transform(decodeList(p[Keys.WATCHLIST])))
        }
    }

    val recents: Flow<List<RecentItem>> = context.dataStore.data.map { decodeRecents(it[Keys.RECENTS]) }

    suspend fun updateRecents(transform: (List<RecentItem>) -> List<RecentItem>) {
        context.dataStore.edit { p ->
            p[Keys.RECENTS] = json.encodeToString(transform(decodeRecents(p[Keys.RECENTS])))
        }
    }

    private fun decodeRecents(raw: String?): List<RecentItem> =
        raw?.let { runCatching { json.decodeFromString<List<RecentItem>>(it) }.getOrNull() } ?: emptyList()

    private fun decodeProviders(raw: String?): List<Provider> =
        raw?.let { runCatching { json.decodeFromString<List<Provider>>(it) }.getOrNull() } ?: emptyList()

    private fun decodeList(raw: String?): List<SavedItem> =
        raw?.let { runCatching { json.decodeFromString<List<SavedItem>>(it) }.getOrNull() } ?: emptyList()
}
