package ar.jornadas.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import retrofit2.http.GET
import kotlinx.coroutines.flow.Flow

data class FichaRemota(val id: String, val title: String, val track: String, val day: Int,
    val time: String, val room: String, val speaker: String, val summary: String)

interface ProgramaApi {
    @GET("programa") suspend fun consultar(): List<FichaRemota>
}

@Entity(tableName = "charlas")
data class Charla(@PrimaryKey val id: String, val titulo: String, val tema: String, val dia: Int,
    val hora: String, val sala: String, val ponente: String, val resumen: String,
    val favorita: Boolean = false)

/**
 * Última edición descargada con éxito (una sola fila). Distingue "nunca se descargó"
 * de "se descargó una edición sin charlas", que también es válida.
 */
@Entity(tableName = "edicion")
data class Edicion(@PrimaryKey val id: Int = 1, val descargadaEn: Long)

@Dao
abstract class CarteleraDao {
    @Query("SELECT * FROM charlas ORDER BY dia, hora, sala")
    abstract fun observar(): Flow<List<Charla>>

    @Query("SELECT EXISTS(SELECT 1 FROM edicion)")
    abstract fun observarHayEdicion(): Flow<Boolean>

    @Query("UPDATE charlas SET favorita = NOT favorita WHERE id = :id")
    abstract suspend fun alternar(id: String)

    /**
     * Aplica una instantánea completa del programa, todo o nada.
     * La marca de favorita se lee de la base en este momento, así que una marca hecha
     * mientras la petición estaba en curso se respeta.
     */
    @Transaction
    open suspend fun aplicarEdicion(fichas: List<Charla>, descargadaEn: Long) {
        if (fichas.isEmpty()) borrarTodas() else borrarRetiradas(fichas.map { it.id })
        fichas.forEach {
            actualizarContenido(it.id, it.titulo, it.tema, it.dia, it.hora, it.sala, it.ponente, it.resumen)
        }
        insertarNuevas(fichas)
        registrarEdicion(Edicion(descargadaEn = descargadaEn))
    }

    @Query("DELETE FROM charlas")
    abstract suspend fun borrarTodas()

    @Query("DELETE FROM charlas WHERE id NOT IN (:vigentes)")
    abstract suspend fun borrarRetiradas(vigentes: List<String>)

    // No toca la columna favorita.
    @Query("""UPDATE charlas SET titulo = :titulo, tema = :tema, dia = :dia, hora = :hora,
        sala = :sala, ponente = :ponente, resumen = :resumen WHERE id = :id""")
    abstract suspend fun actualizarContenido(id: String, titulo: String, tema: String, dia: Int,
        hora: String, sala: String, ponente: String, resumen: String)

    // IGNORE a propósito: las existentes ya se actualizaron arriba; solo entran las nuevas, sin marcar.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertarNuevas(fichas: List<Charla>)

    // Tabla de una sola fila y sin claves foráneas: reemplazar es seguro.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun registrarEdicion(edicion: Edicion)
}

@Database(entities = [Charla::class, Edicion::class], version = 2, exportSchema = false)
abstract class Archivo : RoomDatabase() {
    abstract fun cartelera(): CarteleraDao
}

/** v1 → v2: agrega la tabla de edición sin tocar las charlas ni las marcas de los usuarios. */
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `edicion` (`id` INTEGER NOT NULL, " +
                "`descargadaEn` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        // Quien ya tenía charlas descargadas en la versión instalada ya tiene una edición válida.
        db.execSQL("INSERT INTO edicion (id, descargadaEn) SELECT 1, 0 WHERE EXISTS (SELECT 1 FROM charlas)")
    }
}
