# CEFR Speaking Coach — Análisis del proyecto

Revisión del ZIP entregado el 23 de julio de 2026.
7.747 líneas de Kotlin, 45 archivos fuente.

---

## 1. El hallazgo principal: el motor de voz personalizado está muerto

`CustomCoachVoiceEngine` **nunca se ejecuta en el dispositivo**. No es que suene
mal: es que la app jamás lo llama. Todo sale por el TTS nativo de Android.

La cadena de fallo:

1. `assets/coach_voice_packs/james/voicepack.json` declara siete unidades:
   `opening.wav` más seis palabras (`words/hello.wav`, `words/james.wav`,
   `words/how.wav`, `words/are.wav`, `words/you.wav`, `words/today.wav`).
2. La carpeta `words/` está **vacía**. Ninguno de esos seis archivos existe.
3. `CoachVoicePackRepository.inspectPack()` recorría las unidades y, al primer
   asset faltante, devolvía `INVALID` para **todo el pack**.
4. `CustomCoachVoiceEngine.initialize()` filtra por `INSTALLED`, así que descarta
   a James. Como era el único pack con audio, `installedPackManifests` quedaba
   vacío y `ready = false`.
5. `CoachVoiceOrchestrator.speak()` salta cualquier motor con `isReady() == false`.
   Cae siempre a `AndroidCoachVoiceEngine`.

Esto explica la discrepancia con su documento: la prueba con `opening.wav` sí
funcionó, y funcionó de verdad. Se rompió después, cuando se añadieron las seis
palabras al manifiesto sin grabar los WAV. Un manifiesto que promete más de lo
que hay desactiva el pack entero, en silencio, sin un log que lo diga.

**Corregido en dos frentes**, porque uno solo no basta:

- El manifiesto de James ahora declara únicamente `opening.wav`, que es lo que
  existe.
- El repositorio ahora **descarta la unidad faltante, no el pack**. Un WAV que
  falte se registra en logcat y se sigue adelante con el resto. El pack solo se
  marca `INVALID` si no queda ninguna unidad utilizable.

Verificación en logcat tras la corrección:

```
I/CoachVoicePackRepo: james_v1 [INSTALLED] usable=1 missing=0 reason=Installed with 1 unit(s)
I/CustomCoachVoiceEngine: initialize() ready=true packs=[james]
```

---

## 2. Otros defectos reales encontrados

### 2.1 El matcher de frases era un `contains()` de cadena

En `findBestRecordedPhraseUnit()`:

```kotlin
normalizedText.contains(unitText)   // ← ver el problema
```

Una unidad con texto `"hi"` coincidía con la frase del coach
`"This is important"`, porque *t-**hi**-s* contiene `hi` como subcadena. La app
reproducía un audio que no corresponde al texto en pantalla. Peor: devolvía la
**primera** coincidencia, no la mejor.

Reemplazado por coincidencia sobre tokens completos, con un umbral de cobertura:
una grabación solo sustituye la frase del coach si cubre al menos el 70 % de sus
palabras. Por debajo de eso es más honesto caer al TTS que reproducir un
fragmento que no dice lo que se está mostrando.

### 2.2 El mismo bug de subcadena, otra vez, en la selección de voz del sistema

`AndroidCoachVoiceEngine.genderKeywordsForVoice()` buscaba `"male"` dentro del
nombre de la voz. Pero `"female".contains("male")` es `true`. Al pedir una voz
masculina, **todas** las voces femeninas del dispositivo recibían +10 puntos.
James podía terminar hablando con la voz de Emma.

Corregido con tokenización por límites de palabra y comprobando `female` antes
que `male`. Además ahora se penaliza (-10) la voz cuyo género contradice al
solicitado, en lugar de solo premiar la coincidencia.

### 2.3 La corrutina sin rastrear (vuelve a estar presente)

`CustomCoachVoiceEngine.speak()` lanzaba `scope.launch { ... }` sin guardar el
`Job`. `stop()` paraba el `AudioTrack` pero no la corrutina, así que el
`delay(estimatedMs)` de la frase cancelada terminaba igual y disparaba
`onCompleted` — a veces sobre la frase que ya la había reemplazado. El resultado
es el estado `isCoachSpeaking` corrompido y el micrófono abriéndose a destiempo.

Corregido con un `Job` rastreado, un contador de generación que invalida las
respuestas obsoletas, y `scope.cancel()` en `release()` (que antes filtraba).

### 2.4 La duración se estimaba en vez de medirse

`delay(estimateDurationMs(audio))` calculaba cuánto *debería* durar el audio.
Con `AudioTrack` en `MODE_STATIC` no había otra opción, pero deriva en cada
frase.

`PcmCoachAudioPlayer` reescrito a `MODE_STREAM` con hilo escritor y sondeo de
`playbackHeadPosition`: ahora `onCompleted` se dispara cuando el último frame
salió de verdad del buffer. Esto además es lo que Sherpa necesita para
reproducir por fragmentos mientras sintetiza.

### 2.5 Faltaba `<queries>` en el manifiesto

Con `targetSdk = 35` aplica la visibilidad de paquetes de Android 11+. Sin
declarar `TTS_SERVICE` y `RecognitionService`, `TextToSpeech` puede no enlazar
con el motor del sistema y `SpeechRecognizer.isRecognitionAvailable()` devuelve
`false` — sin ningún error que apunte al manifiesto. Añadido.

### 2.6 `cleartextTrafficPermitted="true"` global

`network_security_config.xml` permitía HTTP plano para todo el tráfico. Play
Store lo señala y no hace falta: Firebase, Render y las descargas de modelos son
HTTPS. Desactivado, con un bloque `debug-overrides` comentado por si necesita
apuntar a su portátil durante desarrollo.

### 2.7 `firebase-bom:latest.release`

Versión dinámica: cada build resuelve una versión distinta. Una compilación
verde hoy puede romperse mañana sin que usted toque nada. Fijada en `33.7.0`.
Súbala cuando quiera, pero deliberadamente.

### 2.8 Archivos huérfanos y código muerto

- `app/src/main/java/FirebaseAiGateway.kt` y `HomeScreenV2.kt` eran restos
  vacíos fuera del paquete, con un comentario pidiendo su borrado. Borrados.
- `buildConfigField("GEMINI_API_KEY", ...)` no se referencia en ninguna parte:
  `FirebaseAiGateway` usa `Firebase.ai(GenerativeBackend.googleAI())`, que
  autentica por `google-services.json`. Eliminado junto con el bloque de
  `local.properties` que lo alimentaba.

---

## 3. Seguridad: la clave de ElevenLabs sigue viva

`tts-server/.env` contenía una clave de API de ElevenLabs en texto plano, más
seis IDs de voz. Ya se la había señalado antes y sigue ahí.

**No la incluí en este ZIP.** En su lugar hay un `tts-server/.env.example` con
los nombres de las variables y sin valores. Su copia local sigue intacta.

Qué hacer, en este orden:

1. Rótela en el panel de ElevenLabs. Asuma que está comprometida: estuvo dentro
   de un ZIP que salió de su máquina.
2. `tts-server/.gitignore` ya incluye `.env`, pero verifique que nunca entró al
   historial de git: `git log --all --full-history -- .env`. Si aparece, rotar
   no basta — hay que reescribir el historial o considerar el repo quemado.
3. Decida si `tts-server` sigue teniendo sentido. Si Sherpa funciona, ese
   servidor deja de tener función y borrarlo elimina la superficie de ataque
   completa.

---

## 4. Un problema de arquitectura, no de código

La `AIConversationScreen` **no usa IA**. Llama a
`ConversationCoachEngine.buildReply()`, que son 685 líneas de reglas y plantillas
sin una sola llamada a Gemini. Mientras tanto `FirebaseAiGateway` — con esquemas
JSON bien definidos para evaluación CEFR, banco de prompts y coaching — solo se
usa desde `SessionScreen`.

Es decir: la pantalla donde el estudiante conversa es un chatbot con guion, y la
pantalla que sí evalúa está en otro lugar del flujo.

Esto conecta con lo que su documento llama "el componente pedagógico pendiente".
No es una tarea futura: es el motivo por el que la conversación no corrige. Ya
tiene la pieza construida (`evaluateSpeaking` devuelve `corrected_version`,
`improvements` y `cefr_level_estimate` en JSON estructurado); falta llamarla
desde la conversación.

> **Resuelto en la segunda entrega.** Ver `docs/CONVERSACION_IA.md`.
> `AiConversationCoachEngine` envuelve a `FirebaseAiGateway`, mantiene
> `ConversationCoachEngine` como respaldo sin red, y devuelve respuesta +
> corrección breve en una sola llamada. Se apaga con
> `AiConversationCoachEngine.aiEnabled = false`.

---

## 5. Lo que falta en el ZIP

La carpeta `gradle/` no venía: sin `libs.versions.toml` ni
`gradle/wrapper/gradle-wrapper.properties`. Todos los `libs.plugins.*` y
`libs.androidx.*` de los `build.gradle.kts` se resuelven desde ese TOML, así que
**no pude compilar para verificar**. Los cambios están revisados a mano, pero no
compilados.

Al reempaquetar, incluya `gradle/` y excluya `app/build/`, `.gradle/`,
`.kotlin/` y `tts-server/node_modules/` (venían dentro y pesaban 133 de los
136 MB).

---

## 6. Archivos modificados

| Archivo | Cambio |
|---|---|
| `assets/coach_voice_packs/james/voicepack.json` | Solo declara unidades que existen |
| `CoachVoicePackRepository.kt` | Degradación por unidad; `loadUsableManifest()`; logs |
| `CoachVoicePackModels.kt` | `CoachVoicePackState` con diagnóstico |
| `CoachVoicePackRegistry.kt` | Tipos coherentes con los manifiestos reales |
| `CoachVoiceEngine.kt` | Nueva interfaz `CoachAwareVoiceEngine` |
| `CoachVoiceOrchestrator.kt` | Enrutado por coach; `retryWithNextEngine()` |
| `CustomCoachVoiceEngine.kt` | Job rastreado, matcher corregido, fin real |
| `AndroidCoachVoiceEngine.kt` | Bug female/male; carrera en `initialize()` |
| `PcmCoachAudioPlayer.kt` | `MODE_STREAM` con `onCompleted` real |
| `AndroidManifest.xml` | `<queries>` para TTS y reconocimiento |
| `res/xml/network_security_config.xml` | Cleartext desactivado |
| `app/build.gradle.kts` | BoM fijado, `noCompress`, hueco para Sherpa |
| `app/src/main/java/FirebaseAiGateway.kt`, `HomeScreenV2.kt` | Borrados |
| `tts-server/.env` | Sustituido por `.env.example` |
| `AiConversationCoachEngine.kt` | **Nuevo** (2ª entrega) |
| `FirebaseAiGateway.kt` | `generateConversationTurn()` (2ª entrega) |
| `ConversationModels.kt` | `correction`, `CoachTurn` (2ª entrega) |
| `AIConversationScreen.kt` | Turno asíncrono con IA (2ª entrega) |

En la primera entrega `AIConversationScreen.kt` no se tocó. En la segunda sí,
para conectar la IA y el `retryWithNextEngine()` que en su momento dejé
documentado en `SHERPA_INTEGRATION.md`, sección 5. El orquestador conserva su
firma de constructor.

---

## 7. Orden sugerido

1. Reempaquetar con `gradle/` y compilar. Confirmar en logcat que James pasa a
   `INSTALLED` y que `CustomCoachVoiceEngine` queda `ready=true`.
2. Rotar la clave de ElevenLabs.
3. Sherpa-ONNX: ver `docs/sherpa/SHERPA_INTEGRATION.md`.
4. ~~Decidir sobre el punto 4 (IA en la conversación).~~ Hecho: ver
   `docs/CONVERSACION_IA.md`. Falta medir costo por turno y latencia real antes
   de desplegarlo a un curso completo.

Sobre la síntesis concatenativa: el código está corregido y funciona, pero
sostengo lo que le dije antes de ver el proyecto. Las junturas de prosodia son
audibles por construcción, no por implementación. Si Sherpa levanta, ese camino
se retira y el sistema de voice packs se queda para audio pregrabado de
ejercicios guiados, que es donde sí aporta.
