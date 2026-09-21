# Integración de sherpa-onnx (voz neuronal offline)

Guía de integración para reemplazar el TTS nativo por voces neuronales que
corren en el dispositivo, sin claves de API, sin servidor y sin depender de una
app externa instalada.

Estado a julio de 2026: sherpa-onnx va por la versión 1.13.4, activamente
mantenido, licencia Apache-2.0. Cumple sus tres restricciones (gratis, offline,
publicable en Play Store), que es exactamente lo que ElevenLabs, Polly y RHVoice
no le daban.

---

## 1. Elegir el modelo

Los modelos se descargan de
`https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models`.

| Familia | Tamaño | Calidad | Veredicto |
|---|---|---|---|
| Piper VITS `low` | 20–30 MB | Aceptable | Punto de partida |
| Piper VITS `medium` | 60–80 MB | Notablemente mejor | **Recomendado** |
| Piper VITS `high` | 100 MB+ | Mejor | Lento en gama baja |
| Kokoro-82M | ~350 MB | Estudio | Requiere Android 11+, ARM64, ~500 MB libres |

Empiece con `medium`. Kokoro suena mejor, pero su `minSdk` es 26 y perdería los
dispositivos de gama baja que probablemente tienen sus estudiantes.

**La decisión que más le importa: modelo multi-hablante.**

Un modelo Piper entrenado con LibriTTS-R expone cientos de speaker ids en un
solo archivo. Sophie, Emma, Lily, James, Ethan y Luca salen del mismo `.onnx`
cambiando el parámetro `sid`. La alternativa —seis modelos, uno por coach— es
seis veces el disco por cero beneficio.

Verifíquelo en tiempo de ejecución: `tts.numSpeakers()`. Si devuelve 1, el
modelo es de un solo hablante y los seis coaches sonarán idénticos. Eso es
esperado, no un bug.

Sobre acentos: los modelos Piper vienen por variedad (`en_US`, `en_GB`). Sophie
y James son británicos, Lily y Luca australianos. Si necesita esos acentos de
verdad, va a requerir más de un modelo, o aceptar que el acento pasa a ser una
etiqueta de la ficha del coach y no algo audible. Decisión de producto, vale la
pena tomarla antes de escoger.

---

## 2. Añadir la dependencia

sherpa-onnx **no publica artefacto oficial en Maven Central**. El AAR va local:

1. Descargue `sherpa-onnx-<versión>.aar` desde
   `https://github.com/k2-fsa/sherpa-onnx/releases`
2. Colóquelo en `app/libs/`
3. Descomente en `app/build.gradle.kts`:

```kotlin
implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
```

No lo suba a git: son ~20 MB por ABI. Añada `app/libs/*.aar` al `.gitignore`.

### La trampa que le va a costar una tarde

Si mete el `.onnx` en `assets/` sin más, la app crashea al cargar:

```
Fatal signal 7 (SIGBUS), code 1 (BUS_ADRALN) in libonnxruntime.so
```

ONNX Runtime mapea el modelo en memoria y lee valores multi-byte en offsets
alineados. `aapt` comprime `assets/` por defecto y rompe esa alineación. En su
proyecto esto ya está confirmado: el build anterior generaba
`compressDebugAssets/out/assets/coach_voice_packs/james/opening.wav.jar`.

Ya lo dejé prevenido en `app/build.gradle.kts`:

```kotlin
androidResources {
    noCompress += listOf("onnx", "bin", "tokens", "fst", "far")
}
```

Aun así, **la recomendación es no cargar desde assets**: 60–80 MB dentro del APK
es mucho, y Play Store empieza a poner peros. `SherpaModelInstaller` instala en
`filesDir` y `SherpaCoachVoiceEngine` carga desde ahí.

Sobre el empaquetado del modelo: las releases oficiales son `.tar.bz2`, que
Android no abre sin librería adicional. Reempaquete el modelo como `.zip` una
vez, hospédelo usted (su backend de Render ya sirve para eso) y descargue ese.
Más simple que añadir commons-compress.

---

## 3. Los archivos

En `docs/sherpa/` hay dos archivos preparados:

- `SherpaModelInstaller.kt.txt` — sin dependencias de sherpa. **Puede moverlo ya
  mismo** al paquete y probar la instalación del modelo antes de tocar el AAR.
- `SherpaCoachVoiceEngine.kt.txt` — importa `com.k2fsa.sherpa.onnx`, así que
  solo compila después del paso 2.

Ambos están fuera del árbol de fuentes a propósito: así el proyecto sigue
compilando hoy. Para activarlos, muévalos a
`app/src/main/java/com/example/cefrspeakingcoach/` y quíteles el `.txt`.

La API de sherpa que usan, verificada contra `kotlin-api/Tts.kt` del repo:

```kotlin
val config = OfflineTtsConfig(
    model = OfflineTtsModelConfig(
        vits = OfflineTtsVitsModelConfig(
            model  = "/ruta/voz.onnx",
            tokens = "/ruta/tokens.txt",
            dataDir = "/ruta/espeak-ng-data",
            lexicon = ""
        ),
        numThreads = 2,
        provider = "cpu"
    ),
    maxNumSentences = 1
)

val tts = OfflineTts(config = config)
val audio = tts.generate(text = "Hello", sid = 3, speed = 1.0f)
// audio.samples: FloatArray normalizado a [-1, 1]
// audio.sampleRate: Int
```

`generate()` es bloqueante y puede tardar más de un segundo en gama baja: va en
`Dispatchers.IO`, nunca en el hilo principal. `SherpaCoachVoiceEngine` ya lo
hace, con `Job` rastreado y contador de generación como los demás motores.

---

## 4. Enchufarlo al orquestador

En `AIConversationScreen.kt`, dentro de `remember(context)`:

```kotlin
val voiceOrchestrator = remember(context) {
    CoachVoiceOrchestrator(
        engines = listOf(
            SherpaCoachVoiceEngine(context = context, callbacks = voiceCallbacks),
            CustomCoachVoiceEngine(context = context, callbacks = voiceCallbacks),
            AndroidCoachVoiceEngine(context = context, callbacks = voiceCallbacks)
        )
    )
}
```

**El orden es la prioridad.** `AndroidCoachVoiceEngine` va siempre al final: es
el único motor que existe con seguridad en cualquier dispositivo, y por eso es
la red de seguridad. Si el modelo no está instalado, `SherpaCoachVoiceEngine`
reporta `isReady() == false` y el orquestador lo salta sin ruido.

El orquestador ahora pregunta `canSpeakAs(voice)` a los motores que implementan
`CoachAwareVoiceEngine`, no solo `isReady()`. Sin eso, Sherpa aceptaría una
frase para un coach cuyo `sid` no existe en el modelo.

---

## 5. El cambio de 4 líneas que sí recomiendo en la pantalla

`speak()` devolviendo `true` significa "acepté la frase", no "funcionó". Un
motor neuronal puede fallar *después* de aceptar. Hoy eso deja al coach mudo.

En `voiceCallbacks`, dentro de `AIConversationScreen.kt`:

```kotlin
onError = { engineId, message ->
    if (!voiceOrchestrator.retryWithNextEngine(engineId)) {
        isCoachSpeaking = false
        statusText = message
    }
}
```

Con eso, si Sherpa falla a mitad de la síntesis, la frase se reintenta con el
siguiente motor de la lista en vez de perderse. `retryWithNextEngine()` ya está
implementado en `CoachVoiceOrchestrator`.

(Pídame el archivo completo si prefiere no editarlo a mano; son 944 líneas y se
lo entrego íntegro como acostumbramos.)

---

## 6. Ajustar los speaker ids

`SherpaCoachVoiceEngine.DEFAULT_SPEAKER_IDS` trae 0–5 como **marcador de
posición**. No están afinados y no puedo afinarlos: cuál `sid` suena como "un
hombre británico y calmado" solo se sabe escuchando.

Sugerencia práctica: una pantalla de audición temporal que recorra
`0 until tts.numSpeakers()` diciendo la misma frase, anote los seis que le
gusten y reemplace el mapa.

Dos cosas del modelo de datos que conviene saber:

- **`ConversationVoiceModel.pitch` se ignora.** VITS no tiene parámetro de tono,
  y desplazar el pitch después suena peor que elegir otro hablante. La
  diferenciación entre coaches viene del `sid`, no del pitch.
- **`speechRate` sí se respeta**, se mapea a `speed` (rango útil 0.5–2.0). Para
  A1/A2 bajarlo a ~0.85 mejora bastante la comprensión.

---

## 7. Criterio de decisión

Su nota decía: probar Sherpa y, si no funciona, descartarlo. Concrete qué
significa "no funciona" antes de empezar, o la prueba se vuelve interminable.
Propongo estos umbrales:

| Criterio | Umbral |
|---|---|
| Latencia, frase de ~15 palabras, gama media | < 1,5 s hasta el primer audio |
| Tamaño del modelo en disco | < 100 MB |
| Naturalidad frente al TTS nativo | Mejor de forma audible, a juicio suyo |
| Estabilidad | 50 frases seguidas sin crash ni fuga de memoria |
| Distinción entre coaches | Al menos 4 voces claramente diferenciables |

Si falla latencia o tamaño, baje de `medium` a `low` antes de descartar. Si
falla la naturalidad incluso en `medium`, ahí sí: Sherpa no es el camino y toca
volver a la opción institucional de pago.

---

## 8. Lo que Sherpa no resuelve

Sherpa es síntesis (texto → voz). El *pronunciation assessment* tipo ELSA que
menciona en su documento es el problema inverso: audio del estudiante →
puntuación por fonema. Necesita alineación forzada y un modelo acústico a nivel
de fonema, no un TTS.

Es un proyecto aparte, y bastante más grande. No lo mezcle con esta fase.
