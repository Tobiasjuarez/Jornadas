package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.Servicios
import ar.jornadas.data.Charla
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ProgramaModel(app: Application, private val estado: SavedStateHandle) : AndroidViewModel(app) {
    private val servicios = Servicios.de(app)
    val mesa = servicios.mesa
    private val repo = servicios.agenda
    private val sincronizador = servicios.sincronizador

    /** null mientras se lee la base local. */
    val charlas: StateFlow<List<Charla>?> =
        repo.programa.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val hayEdicion: StateFlow<Boolean?> =
        repo.hayEdicion.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val cargando: StateFlow<Boolean> = sincronizador.cargando
    val fallo: StateFlow<Boolean> = sincronizador.fallo

    // En SavedStateHandle: sobreviven a la rotación y a la muerte del proceso; una tarea nueva arranca sin filtros.
    val dia: StateFlow<Int> = estado.getStateFlow(CLAVE_DIA, 0)
    val tema: StateFlow<String> = estado.getStateFlow(CLAVE_TEMA, TODOS)

    init {
        sincronizador.cargaAutomatica()
    }

    fun elegirDia(valor: Int) { estado[CLAVE_DIA] = valor }
    fun elegirTema(valor: String) { estado[CLAVE_TEMA] = valor }
    fun actualizar() = sincronizador.pedir()
    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }

    companion object {
        const val TODOS = "Todos"
        private const val CLAVE_DIA = "filtro_dia"
        private const val CLAVE_TEMA = "filtro_tema"
    }
}
