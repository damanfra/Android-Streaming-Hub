package com.damanfra.streaminghub

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.damanfra.streaminghub.data.Filters
import com.damanfra.streaminghub.data.Genre
import com.damanfra.streaminghub.data.Prefs
import com.damanfra.streaminghub.data.RecentItem
import com.damanfra.streaminghub.data.Tmdb
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.damanfra.streaminghub.data.HomeState
import com.damanfra.streaminghub.data.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
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

    val recents: StateFlow<List<RecentItem>> = prefs.recents.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Registra que o título foi aberto em um serviço; o mesmo título sobe para o topo. Guarda os 60 últimos. */
    fun addRecent(item: RecentItem) {
        viewModelScope.launch {
            prefs.updateRecents { list ->
                (listOf(item) + list.filterNot { it.id == item.id && it.type == item.type }).take(60)
            }
        }
    }

    fun removeRecent(type: String, id: Int) {
        viewModelScope.launch { prefs.updateRecents { list -> list.filterNot { it.id == id && it.type == type } } }
    }

    /** Tira da Minha lista (vale para "Quero ver" e "Assistidos"). */
    fun removeSaved(type: String, id: Int) {
        viewModelScope.launch { prefs.updateWatchlist { list -> list.filterNot { it.id == id && it.type == type } } }
    }

    fun clearRecents() {
        viewModelScope.launch { prefs.updateRecents { emptyList() } }
    }

    /** Filtros do Início. Salvos no aparelho junto com [homeType], [homeProvider] e [lastOpened]. */
    val filters = MutableStateFlow(Filters())

    /** Início: Filmes ("movie") ou Séries ("tv") e o serviço escolhido (-1 = todos os meus). */
    val homeType = MutableStateFlow("movie")
    val homeProvider = MutableStateFlow(-1)

    /**
     * Catálogo já carregado no Início (todas as páginas). Fica aqui, e não na tela, para que voltar dos
     * Detalhes não recarregue a lista do zero e perca títulos que estavam além da 1ª página.
     */
    class HomeCatalog {
        val results = mutableStateListOf<MediaItem>()
        var page by mutableIntStateOf(0)
        var totalPages by mutableIntStateOf(1)
        /** Tipo, serviços e filtros da busca que gerou [results]. */
        var query: Any? = null
    }

    val catalog = HomeCatalog()

    /** Último título aberto a partir do Início. */
    val lastOpened = MutableStateFlow<Int?>(null)

    /** false até ler o estado salvo; o Início espera para não buscar o catálogo duas vezes. */
    val homeLoaded = MutableStateFlow(false)

    /** Ligado só na abertura do app quando há [lastOpened]: o Início rola até ele uma vez. */
    var restoreOnStart = false

    init {
        viewModelScope.launch {
            prefs.loadHome()?.let { s ->
                filters.value = s.filters
                homeType.value = s.type
                homeProvider.value = s.provider
                lastOpened.value = s.lastOpened
                restoreOnStart = s.lastOpened != null
            }
            homeLoaded.value = true
            combine(filters, homeType, homeProvider, lastOpened) { f, t, p, l -> HomeState(t, p, f, l) }
                .drop(1)
                .collect { prefs.saveHome(it) }
        }
    }

    private val genreCache = mutableMapOf<String, List<Genre>>()

    suspend fun genres(key: String, type: String): List<Genre> =
        genreCache[type] ?: Tmdb.genres(key, type).also { genreCache[type] = it }

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
