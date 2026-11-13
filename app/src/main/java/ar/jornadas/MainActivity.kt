package ar.jornadas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import ar.jornadas.ui.*

class MainActivity : ComponentActivity() {
    private val model: ProgramaModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Extra de prueba: se aplica antes de crear el ViewModel, que dispara la carga automática.
        if (savedInstanceState == null && intent.hasExtra(EXTRA_SERVICIO)) {
            Servicios.de(applicationContext).mesa.disponible = intent.getBooleanExtra(EXTRA_SERVICIO, true)
            intent.removeExtra(EXTRA_SERVICIO)
        }
        // Sin recarga en onStart: la carga automática es una por proceso (ver Sincronizador).
        setContent { Portada(model) }
    }

    private companion object {
        const val EXTRA_SERVICIO = "servicio_disponible"
    }
}
