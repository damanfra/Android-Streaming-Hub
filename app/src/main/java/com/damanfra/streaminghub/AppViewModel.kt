package com.damanfra.streaminghub

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.damanfra.streaminghub.data.Prefs
import com.damanfra.streaminghub.data.Provider
import com.damanfra.streaminghub.data.SavedItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    /** null enquanto ainda está lendo do disco. */
    val apiKey: StateFlow<String?> = prefs.apiKey.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val providers: StateFlow<List<Provider>> = prefs.providers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val watchlist: StateFlow<List<SavedItem>> = prefs.watchlist.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val colsPortrait: StateFlow<Int> = prefs.colsPortrait.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val colsLandscape: StateFlow<Int> = prefs.colsLandscape.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun setColumns(landscape: Boolean, value: Int) {
        viewModelScope.launch { prefs.setColumns(landscape, value) }
    }

    fun saveApiKey(value: String) {
        viewModelScope.launch { prefs.setApiKey(value) }
    }

    fun toggleProvider(provider: Provider) {
        viewModelScope.launch {
            prefs.updateProviders { current ->
                if (current.any { it.id == provider.id }) current.filterNot { it.id == provider.id } else current + provider
            }
        }
    }

    /** Adiciona ou remove de "Quero ver". */
    fun toggleSaved(item: SavedItem) {
        viewModelScope.launch {
            prefs.updateWatchlist { list ->
                if (list.any { it.id == item.id && it.type == item.type }) {
                    list.filterNot { it.id == item.id && it.type == item.type }
                } else {
                    listOf(item.copy(addedAt = System.currentTimeMillis())) + list
                }
            }
        }
    }

    /** Marca ou desmarca como assistido (entra na lista se ainda não estiver). */
    fun setWatched(item: SavedItem, watched: Boolean) {
        viewModelScope.launch {
            prefs.updateWatchlist { list ->
                val exists = list.any { it.id == item.id && it.type == item.type }
                if (exists) {
                    list.map { if (it.id == item.id && it.type == item.type) it.copy(watched = watched) else it }
                } else {
                    listOf(item.copy(watched = watched, addedAt = System.currentTimeMillis())) + list
                }
            }
        }
    }
}
