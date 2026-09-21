# Voces por acento — Fase 2, paso 1 (refactor multi-modelo)

Profe Andy: esto es el refactor que quedó pendiente en el traspaso. El motor de
voz neuronal ya no está atado a un solo modelo: ahora maneja varios, carga uno
a la vez y decide cuál usar según el coach.

**Nada de esto rompe lo que ya funciona.** Mientras no exista
`assets/sherpa-model-uk/`, los seis coaches siguen sonando exactamente igual que
hoy (modelo americano, con los speaker id que ya elegiste de oído). El acento
británico entra solo cuando agregues el modelo.

---

## 1. El modelo británico elegido: VCTK

Verificado en el catálogo oficial de sherpa-onnx (release `tts-models`):

| Modelo | Acento | Hablantes | Descarga |
|---|---|---|---|
| `vits-piper-en_US-libritts_r-medium` | Americano | 904 | ya lo tienes |
| `vits-piper-en_GB-vctk-medium` | Británico | **109** | 76 MB (`.tar.bz2`) |
| `vits-piper-en_GB-vctk-medium-int8` | Británico | 109 | 22 MB, versión cuantizada |

Por qué VCTK y no `southern_english_female/male`:

- VCTK es el **único en_GB multi-hablante** del catálogo. Con un archivo cubre
  voces femeninas y masculinas, igual que libritts_r cubre las americanas. Las
  southern_english son mono-hablante: necesitarías dos modelos para conseguir
  menos variedad, y cada modelo extra es otra carga/descarga que administrar.
- Licencia CC BY 4.0 (igual que LibriTTS-R). Toca poner la atribución en los
  créditos de la app cuando publiques.
- Ojo: el corpus VCTK mezcla acentos británicos — sur de Inglaterra, escocés,
  irlandés, norte. No todas las 109 voces suenan "RP de la BBC". Por eso los
  defaults apuntan a hablantes del sur y la pantalla de audición ahora te dice
  el género de cada voz para que no recorras 109 a ciegas.

**Recomendación:** empieza con `vits-piper-en_GB-vctk-medium` (el normal), que
es el mismo tipo de paquete que ya te funciona. Si al final pesa mucho para los
teléfonos de los alumnos, prueba después el `-int8`: mismo layout de archivos,
un tercio del tamaño.

---

## 2. Qué tienes que hacer tú (una sola vez)

1. Descarga:
   `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-vctk-medium.tar.bz2`
2. Descomprime (7-Zip abre `.tar.bz2` en Windows).
3. Crea la carpeta `app/src/main/assets/sherpa-model-uk/` y copia **solo dos
   archivos**:
   - `en_GB-vctk-medium.onnx`
   - `tokens.txt`
4. **NO copies `espeak-ng-data`.** Son los mismos ~19 MB que ya están
   instalados con el modelo americano, y el instalador ahora los comparte.
   (Si por lo que sea algo sonara raro, copia también esa carpeta dentro de
   `sherpa-model-uk/` y el motor usará la propia; verifiqué que el paquete
   británico trae exactamente la misma data, incluida la voz `en-GB-x-rp`.)
5. Compila y abre el menú lateral → **Voice audition (debug)**.

---

## 3. Qué cambió en el código

Archivos nuevos o reescritos completos:

### `SherpaVoiceCatalog.kt` (nuevo)
El catálogo. Define los dos modelos, qué modelo le toca a cada coach y el
speaker id por defecto de cada coach **en cada modelo**.

- Británico: Sophie, Lily, James, Luca.
- Americano: Emma, Ethan.
- Trae la tabla de los 109 hablantes de VCTK con su **género real** del corpus
  (`p225 · F`, `p226 · M`, …), en el orden exacto del `speaker_id_map` del
  modelo. Eso es lo que permite el botón "Sig. F" en la audición.

### `SherpaModelInstaller.kt` (reescrito)
- Layout nuevo: `filesDir/sherpa-tts/models/<modelId>/`.
- **Migra sola** la carpeta vieja `sherpa-tts/current/` al modelo americano, así
  que el `.onnx` de 75 MB que ya está en tu teléfono NO se vuelve a copiar.
- `espeak-ng-data` compartida: si un modelo no la trae, usa la de otro.

### `SherpaCoachVoiceEngine.kt` (reescrito)
- Multi-modelo con **un solo modelo en RAM**. Al pedir hablar con un coach de
  otro acento, libera el actual y carga el nuevo. Un `Mutex` serializa carga y
  síntesis: sin él, liberar un modelo mientras el nativo está generando audio es
  crash seguro.
- **Respaldo:** si el modelo asignado a un coach no está instalado, usa el que sí
  está (el americano) en vez de quedarse mudo. Por eso la app funciona igual
  antes y después de agregar el británico.
- El `release()` corre en un scope aparte que nunca se cancela; si corriera en el
  scope normal no se ejecutaría nunca y los ~150 MB del modelo quedarían colgados.

### `SpeakerIdStore.kt` (reescrito)
- Una elección ahora son dos datos: modelo + speaker id
  (`model_<coach>`, `sid_<modelId>_<coach>`). El sid 40 de libritts_r no tiene
  nada que ver con el 40 de VCTK.
- **Migra tus elecciones anteriores** (`sid_<coach>`) al espacio del modelo
  americano. No se pierde el trabajo de oído que ya hiciste. A propósito NO fija
  el modelo de cada coach en "americano": si lo hiciera, los seis quedarían
  clavados y el británico nunca entraría solo.

### `SpeakerAuditionScreen.kt` (reescrito)
- Selector de acento arriba (British / American).
- Muestra la etiqueta del hablante (`p225 · F`) cuando se conoce.
- Botones **Sig. F / Sig. M** para saltar a la siguiente voz de ese género.
- Al asignar, guarda **modelo + voz**. Cambiar un coach de acento se hace desde
  aquí, sin recompilar.

`AIConversationScreen.kt` no se tocó: el constructor del motor quedó igual.

---

## 4. Cómo probarlo (en orden)

1. **Antes de agregar nada:** compila e instala. Todo debe sonar igual que hoy.
   En logcat, tag `SherpaCoachVoiceEngine`, debe salir
   `sherpa-onnx ready: en_us_libritts_r with 904 speaker(s)`.
   Si eso no sale, párale aquí y mándame el logcat: la migración de carpeta es
   lo primero que hay que descartar.
2. **Con el modelo británico en assets:** vuelve a compilar. En la audición,
   toca "British". La primera vez tarda unos segundos (copia de assets a
   filesDir). Debe decir `VCTK · British: 109 voces`.
3. Recorre con "Sig. F", escucha, asigna a Sophie y a Lily. Igual con "Sig. M"
   para James y Luca.
4. Vuelve a la conversación y habla con Sophie y después con Emma: ahí se ve el
   cambio de modelo en vivo (el primer turno de cada cambio tarda un poco más).

---

## 5. Lo que sigue (Fase 2 real)

- Sacar los modelos del APK y descargarlos de **tus GitHub Releases**. Los
  `.tar.bz2` oficiales hay que reempaquetarlos a `.zip`; el instalador ya tiene
  `installFromZip(modelId, input)` listo para recibirlos, ahora con el id del
  modelo como parámetro.
- Pantalla de ajustes para descargar/borrar cada modelo (el instalador ya expone
  `installedSizeBytes(modelId)` y `uninstall(modelId)`).
- Créditos con las atribuciones CC BY 4.0 de LibriTTS-R y VCTK.

## 6. Pendiente de decisión tuya

En `ConversationModels.kt`, Lily y Luca siguen con `accentLabel = "Australian"` y
`localeTag = "en-AU"`, pero van a sonar británicos. El label es cosmético; hay
que decidir si se cambia a "British" o se deja el australiano. Ojo con un
detalle: el `localeTag` sí se usa cuando la voz cae al TTS de Android
(`AndroidCoachVoiceEngine`), así que dejar `en-AU` no es del todo inocuo.
