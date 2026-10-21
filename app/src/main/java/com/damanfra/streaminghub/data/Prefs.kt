package com.damanfra.streaminghub.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Tudo que o app guarda no aparelho: chave do TMDB, serviços assinados e Minha lista. */
class Prefs(private val context: Context) {
    private object Keys {
        val API_KEY = stringPreferencesKey("api_key")
        val PROVIDERS = stringPreferencesKey("providers")
        val WATCHLIST = stringPreferencesKey("watchlist")
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

    private fun decodeProviders(raw: String?): List<Provider> =
        raw?.let { runCatching { json.decodeFromString<List<Provider>>(it) }.getOrNull() } ?: emptyList()

    private fun decodeList(raw: String?): List<SavedItem> =
        raw?.let { runCatching { json.decodeFromString<List<SavedItem>>(it) }.getOrNull() } ?: emptyList()
}
