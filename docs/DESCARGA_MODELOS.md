# Fase 2 — El modelo de voz sale del APK y se descarga

Profe Andy: con esto el `.onnx` de 75 MB deja de viajar dentro del APK. La app
lo descarga una vez desde **tus** GitHub Releases, lo guarda en `filesDir` y ahí
se queda: sobrevive a las actualizaciones de la app y solo lo baja quien vaya a
usar la voz neuronal. Quien no lo descargue sigue teniendo conversación con el
TTS de Android, no se queda sin app.

Además, todos los coaches quedaron con **acento americano** (label y `localeTag`),
que es lo que realmente suenan.

---

## 1. Lo que tienes que hacer tú (una vez)

### a) Armar el .zip

Descarga el modelo del catálogo oficial:

```
https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-libritts_r-medium.tar.bz2
```

Y conviértelo con el script que va en `tools/`:

```
python tools/repack_model.py vits-piper-en_US-libritts_r-medium.tar.bz2 en_us_libritts_r.zip
```

El nombre `en_us_libritts_r.zip` **no es opcional**: tiene que coincidir con
`downloadFileName` en `SherpaVoiceCatalog.kt`.

> Si algún día subes un segundo modelo, ese sí puede ir con `--sin-espeak`
> (ahorra ~19 MB): la app comparte la carpeta `espeak-ng-data` del primero. El
> primero SIEMPRE la lleva.

**Alternativa más liviana:** existe `vits-piper-en_US-libritts_r-medium-int8`
(22 MB en vez de 78 MB). Es el mismo modelo cuantizado, mismo layout de
archivos, mismas 904 voces. Para alumnos con datos móviles la diferencia es
enorme. Vale la pena que compares el audio de los dos antes de decidir; si no
notas diferencia, quédate con el int8.

### b) Subirlo

1. Crea un repo (puede ser solo para las voces, no tiene que ser el de la app).
2. Releases → nuevo release con tag `voices-v1`.
3. Sube `en_us_libritts_r.zip` como asset.
4. Copia la URL del asset. Queda algo así:
   `https://github.com/tuusuario/turepo/releases/download/voices-v1/en_us_libritts_r.zip`

### c) Configurar la app

En `SherpaVoiceCatalog.kt`, una sola línea:

```kotlin
const val MODELS_BASE_URL =
    "https://github.com/tuusuario/turepo/releases/download/voices-v1/"
```

Ojo: **termina en `/`** y no incluye el nombre del archivo.

Mientras siga con `TU_USUARIO`, el botón de descarga aparece deshabilitado y la
pantalla te lo dice, para que no te quedes esperando una descarga que no puede
salir.

### d) Sacar el modelo del APK

Borra `app/src/main/assets/sherpa-model/` del proyecto. Tu teléfono no se ve
afectado: el modelo ya está instalado en `filesDir` y sigue funcionando. Un
celular nuevo simplemente lo descarga.

---

## 2. Cómo probarlo

En el menú lateral → **Voice audition (debug)**, arriba del diagnóstico hay una
tarjeta nueva:

- **No instalado · descarga de ~80 MB** → botón **Descargar**, con porcentaje y
  MB en vivo, y **Cancelar**.
- **Instalado · N MB** → botones **Volver a descargar** y **Borrar**.

Prueba en este orden:

1. **Borrar** (con el modelo actual instalado). El diagnóstico debe pasar a
   "filesDir: NO instalado". Las voces se van al TTS de Android.
2. **Descargar**. Debe subir el porcentaje, decir "Instalando..." y terminar en
   "Listo: 904 voces". El diagnóstico debe mostrar el `.onnx` y su peso.
3. Reproduce una voz y vuelve a la conversación: debe sonar la voz neuronal otra vez.

Si se corta el internet a mitad, no pasa nada: la descarga va a un archivo
temporal en cache y solo se instala si llegó completa. Nunca queda una carpeta
de modelo a medias.

---

## 3. Qué cambió en el código

| Archivo | Cambio |
|---|---|
| `ConversationModels.kt` | Los seis coaches en `American` / `en-US`. |
| `SherpaVoiceCatalog.kt` | `MODELS_BASE_URL`, `downloadFileName` y peso aproximado por modelo. |
| `SherpaModelDownloader.kt` | **Nuevo.** Descarga con progreso, cancelable, y llama al instalador. |
| `SherpaCoachVoiceEngine.kt` | `reloadModel()` (recarga tras descargar) y `deleteModel()` (borra y libera RAM). |
| `SpeakerAuditionScreen.kt` | Tarjeta de descarga/borrado con progreso. |
| `tools/repack_model.py` | **Nuevo.** Convierte el `.tar.bz2` oficial al `.zip` que espera la app. |

Detalles de diseño que conviene no perder:

- La descarga va **en dos pasos** (bajar a cache, después descomprimir) y no
  descomprimiendo el stream HTTP directo. Con celular, la conexión se corta a
  media descarga todo el tiempo; en un solo paso quedaría una carpeta de modelo
  incompleta que el motor podría dar por buena.
- `reloadModel()` existe porque `prepareModel()` se salta la carga cuando el id
  ya está cargado. Después de descargar encima de un modelo cargado, sin
  recargar el motor seguiría hablando con el archivo viejo.
- El progreso se muestra en texto, no con `LinearProgressIndicator`: la firma de
  ese composable cambió entre versiones de Material3 (`Float` vs lambda) y no
  vale la pena arriesgar el build por una barrita.

---

## 4. Lo que sigue

- Mover la descarga de la pantalla de debug a una pantalla de ajustes de verdad
  ("Voz del coach"), con el aviso de peso antes de bajar y opción de "solo por
  WiFi".
- Créditos con la atribución CC BY 4.0 de LibriTTS-R.
- Opcional: verificar la descarga con un SHA-256 publicado en el release, por si
  el zip se corrompe en tránsito.
