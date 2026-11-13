# Cambios · Guardia de mantenimiento

## 41 · Programa sin señal
- **Problema:** ante una falla, el aviso reemplazaba la lista. Además no había forma de distinguir "nunca se descargó" de "se descargó una edición vacía" (v4).
- **Cambio:** nueva tabla `edicion` (una fila) que se registra en cada descarga exitosa, aunque traiga cero charlas. La UI decide con eso:
  - Con una edición previa y una falla: lista, detalle y favoritas siguen visibles, con el aviso "Sin conexión: se están usando datos guardados" y un botón Reintentar.
  - Sin edición previa: "No se pudo descargar el programa" con Reintentar.
  - Edición vacía: "Sin charlas programadas". Al reabrir sin señal sigue siendo una edición válida.
- **Datos de usuarios existentes:** base v1 → v2 con `MIGRACION_1_2`: crea la tabla y, si ya había charlas, registra que existe una edición. No se usa migración destructiva. `versionCode` 13.

## 42 · Agenda personal
- **Problema:** `@Insert(REPLACE)` reemplazaba cada fila con `favorita = false`, así que **cada sincronización borraba Mi agenda**. Las charlas retiradas nunca se eliminaban.
- **Cambio:** `CarteleraDao.aplicarEdicion` aplica la instantánea en una `@Transaction`:
  1. borra las charlas que ya no están (o todas, si la edición viene vacía);
  2. actualiza el contenido de las vigentes **sin tocar `favorita`**;
  3. inserta las nuevas sin marcar (`IGNORE`: las existentes ya se actualizaron).
- **Resultado:** la 11 conserva la marca con el título y la sala nuevos (v2). Si se retira, desaparece del catálogo y de Mi agenda (v3), y si vuelve, vuelve sin marcar. Como la marca se lee de la base al aplicar, **una marca hecha durante la petición se respeta**.

## 43 · Volver al punto de lectura
- **Problema:** el filtro de tema vivía en un `remember` dentro de `Catalogo` y se perdía al abrir una ficha o rotar. El día vivía en el ViewModel y se perdía si moría el proceso.
- **Cambio:** los dos filtros están en el `SavedStateHandle` del ViewModel. Sobreviven a la rotación y a la muerte del proceso, y una tarea nueva arranca sin filtros. La ficha abierta y la pestaña ya usaban `rememberSaveable`. Si la ficha abierta se retiró del programa, se muestra "Esta charla ya no forma parte del programa".

## 44 · Control de descargas
- **Problema:** se cargaba en `MainActivity.onStart` **y** en un `LaunchedEffect` de `Catalogo`: dos peticiones al abrir y más con cada rotación, cambio de pestaña o vuelta del fondo.
- **Cambio:** `Sincronizador`, de alcance de proceso (dentro de `Servicios`, que ahora es una única instancia con una sola base Room):
  - una carga automática por proceso;
  - Actualizar y Reintentar piden una carga; si ya hay una en curso, los toques se **agrupan en un único intento** posterior;
  - terminada la petición (con éxito o con falla) se puede volver a pedir;
  - los filtros no se bloquean durante la espera.
- **Interpretación:** "dos toques durante una petición agregan un intento" se implementó como "toques durante una petición en curso → un intento más al terminar".

## Entorno
- **Wrapper de Gradle 8.7 → 8.9:** el sync fallaba en Android Studio porque un script de inicialización del IDE usa `gradle.lifecycle`, que existe desde Gradle 8.8. Gradle 8.9 es compatible con AGP 8.5.2; no cambian dependencias ni código.
- **`ServicioLocal` no compilaba:** usaba extensiones de OkHttp 4 (`toMediaType`, `toResponseBody`), pero Retrofit 2.11 trae OkHttp 3. Se reemplazaron por `MediaType.get(...)` y `ResponseBody.create(...)`, sin agregar dependencias.
- **`.gitignore`:** agregado para no versionar `build/`, `.idea/` ni `local.properties`.

## Pruebas manuales (emulador)
1. Instalación nueva → Mesa: 1 petición. Rotar, abrir una ficha y cambiar de pestaña → sigue en 1.
2. Demora larga + Actualizar dos veces durante la espera → el contador sube 1 (más la petición en curso). Los filtros responden durante la espera.
3. Marcar la 11 → Mesa v2 → Actualizar → la 11 sigue en Mi agenda como «Compose en producción avanzada» · «Sala Puerto».
4. v3 → Actualizar → la 11 desaparece del catálogo y de Mi agenda, aparece la 90 y las demás marcas siguen. v1 → la 11 vuelve sin marcar.
5. Demora larga → Actualizar → marcar o desmarcar una charla mientras espera → al terminar, la marca se respeta.
6. Apagar el servicio → Actualizar → la lista, el detalle y las favoritas siguen, con el aviso de datos guardados.
7. v4 → Actualizar → «Sin charlas programadas». Apagar el servicio y reabrir → sigue igual, con el aviso.
8. `adb shell pm clear ar.jornadas` y `adb shell am start -n ar.jornadas/.MainActivity --ez servicio_disponible false` → «No se pudo descargar el programa» y Reintentar.
9. Día 2 + Datos → abrir «Cachés para viajeros» → volver y rotar → 2 resultados de Datos. Repetir con la ficha abierta, Inicio, `adb shell am kill ar.jornadas` y volver desde Recientes → la ficha se recupera y, al volver, los dos filtros siguen.
10. Actualizar desde la versión anterior (v1.2 con favoritas) → se conservan las charlas y las marcas.
