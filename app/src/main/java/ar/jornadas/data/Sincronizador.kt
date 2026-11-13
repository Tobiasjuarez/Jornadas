package ar.jornadas.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Coordina las descargas del programa para todo el proceso, independientemente de la pantalla:
 * una carga automática por proceso, y los pedidos que llegan durante una petición se agrupan
 * en un único intento posterior.
 * Corre en el hilo principal: Retrofit y Room suspenden sin bloquearlo y el estado no necesita locks.
 */
class Sincronizador(private val repo: AgendaRepository, private val scope: CoroutineScope) {
    private val _cargando = MutableStateFlow(false)
    val cargando: StateFlow<Boolean> = _cargando.asStateFlow()

    // true si el último intento terminó en error.
    private val _fallo = MutableStateFlow(false)
    val fallo: StateFlow<Boolean> = _fallo.asStateFlow()

    private var cargaAutomaticaHecha = false
    private var enCurso: Job? = null
    private var pendiente = false

    fun cargaAutomatica() {
        if (cargaAutomaticaHecha) return
        cargaAutomaticaHecha = true
        pedir()
    }

    fun pedir() {
        if (enCurso?.isActive == true) {
            pendiente = true
            return
        }
        enCurso = scope.launch {
            _cargando.value = true
            try {
                do {
                    pendiente = false
                    try {
                        repo.sincronizar()
                        _fallo.value = false
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _fallo.value = true
                    }
                } while (pendiente)
            } finally {
                _cargando.value = false
            }
        }
    }
}
