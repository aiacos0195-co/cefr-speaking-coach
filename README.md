# CEFR Speaking Coach

App Android para practicar inglés hablado, alineada a los niveles del MCER
(CEFR). El alumno elige un coach, habla, y el coach responde como lo haría un
interlocutor real, ajustándose a su nivel. Las correcciones aparecen escritas
debajo de la respuesta, para no cortar la conversación.

Desarrollada por **Andy Páez**, instructor de inglés.

---

## Licencia

Este proyecto está bajo la **GNU General Public License v3.0**. Ver
[LICENSE](LICENSE).

La razón es concreta: la app usa [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)
para sintetizar la voz del coach en el dispositivo, y sherpa-onnx incluye
[espeak-ng](https://github.com/espeak-ng/espeak-ng) (vía piper-phonemize) para
convertir texto en fonemas. espeak-ng es GPL-3.0, así que la app también lo es.

> Nota: los mantenedores de sherpa-onnx anunciaron que van a quitar espeak-ng
> en la versión 2.0.0 (issue #3731) justamente por este motivo. Cuando salga,
> este proyecto puede migrar a fonemización por lexicón.

Componentes de terceros:

| Componente | Licencia | Para qué |
|---|---|---|
| sherpa-onnx | Apache 2.0 | síntesis de voz en el dispositivo |
| espeak-ng | GPL 3.0 | texto a fonemas |
| Piper | MIT / GPL 3.0 | modelos de voz |
| LibriTTS-R | CC BY 4.0 | dataset de voces |
| Firebase AI Logic (Gemini) | Términos de Google | conversación |
| Jetpack Compose | Apache 2.0 | interfaz |

---

## Compilar el proyecto

### 1. Requisitos

- Android Studio (Ladybug o posterior)
- JDK 17
- Un dispositivo o emulador con Android 8.0+

### 2. Firebase (obligatorio)

**Este repositorio NO incluye `app/google-services.json`.** Ese archivo apunta
al proyecto de Firebase del autor y sus llamadas a Gemini las paga él. Para
compilar, crea tu propio proyecto:

1. Consola de Firebase → nuevo proyecto.
2. Agrega una app Android con el `applicationId` de `app/build.gradle.kts`.
3. Descarga `google-services.json` y ponlo en `app/`.
4. Habilita **Firebase AI Logic** (Gemini) en tu proyecto.
5. Registra tu app en **App Check**. En depuración se usa el proveedor de
   debug; para release, Play Integrity.

Sin ese archivo el proyecto no compila. Es a propósito.

### 3. Modelo de voz (opcional)

La voz neuronal se descarga desde la app (menú → Coach voice) y **no viaja en
el APK**. Si quieres apuntar a tu propia copia del modelo, cambia
`MODELS_BASE_URL` en `SherpaVoiceCatalog.kt`.

El modelo es
[`vits-piper-en_US-libritts_r-medium`](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models)
del catálogo oficial de sherpa-onnx, reempaquetado a `.zip` con
`tools/repack_model.py` (Android no abre `.tar.bz2` sin librerías extra).

Sin el modelo, la app funciona igual: el coach usa el TTS del sistema.

---

## Privacidad

Vale la pena decirlo claro, porque no todo ocurre en el teléfono:

- **En el dispositivo:** la síntesis de la voz del coach. No se envía nada a
  ningún servidor para producirla, y funciona sin internet.
- **Fuera del dispositivo:** lo que dice el alumno se convierte en texto con el
  reconocimiento de voz de Android, y ese texto se envía a Gemini para generar
  la respuesta del coach.
- **Solo local:** el historial de práctica y el progreso.

---

## Estructura

- `SherpaCoachVoiceEngine.kt` — motor de voz neuronal, un modelo en RAM a la vez
- `SherpaModelInstaller.kt` / `SherpaModelDownloader.kt` — instalación y descarga
- `SherpaVoiceCatalog.kt` — modelos disponibles y voz por coach
- `CoachVoiceScreen.kt` — pantalla de voz para el alumno
- `SpeakerAuditionScreen.kt` — herramienta interna para afinar voces
- `FirebaseAiGateway.kt` — llamadas a Gemini
- `AiConversationCoachEngine.kt` — IA con respaldo por reglas
- `docs/` — notas de implementación en español
