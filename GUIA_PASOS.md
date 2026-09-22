# CEFR Speaking Coach — Guía de pasos

Para profe Andy. **Estado al 20 de septiembre de 2026.**

| Paso | Estado |
|---|---|
| 1 — Cambio de paquete a `com.cefrspeakingcoach.app` | ✅ **Hecho** |
| 2 — Firebase con el paquete nuevo | ✅ **Hecho** |
| 3 — Compilar y probar | ✅ **Hecho** |
| 4 — Publicar el código en GitHub (GPL v3) | ✅ **Hecho** |
| 5 — Lo que falta antes de Play | 🔶 En curso (5.1 y 5.3 hechos) |
| Abiertos — bug de transcripción, ícono, "PREMIUM ACCESS" | 🔴 **Lo de ahora** |

---

## PASO 1 — Cambio de paquete ✅ HECHO

Se hizo **directamente sobre los archivos**, no con el ZIP v8. Los ZIP que
quedaron por ahí ya no sirven para nada: aplicar cualquiera de ellos ahora
volvería a meter el paquete viejo. Bórralos.

Lo que quedó:

- [x] 58 archivos `.kt` movidos de `com\example\cefrspeakingcoach\` a
      `com\cefrspeakingcoach\app\` (incluidos los 3 de `ui\theme\`), con
      `package` e `import` reescritos
- [x] `app\build.gradle.kts`: `namespace` y `applicationId` en
      `com.cefrspeakingcoach.app`
- [x] `com\example\` borrado por completo en `main`, `test` y `androidTest`
- [x] Borrados dos archivos sueltos de solo comentarios que estorbaban:
      `app\src\main\java\FirebaseAiGateway.kt` y `HomeScreenV2.kt`
- [x] `LICENSE` (GPL v3) y `README.md` en la raíz
- [x] `MODELS_BASE_URL` apuntando a
      `https://github.com/aiacos0195-co/voices-v1/releases/download/models-v1/`

El `AndroidManifest.xml` no necesitó cambios: usa nombres relativos
(`.MainActivity`, `.ReminderReceiver`, `.CefrSpeakingCoachApp`) que se resuelven
contra el `namespace`.

---

## PASO 2 — Firebase con el paquete nuevo ✅ HECHO

- [x] App nueva `com.cefrspeakingcoach.app` creada en el proyecto
      **cefr-speaking-coach**
- [x] SHA-1 y SHA-256 de debug registradas
- [x] `app\google-services.json` reemplazado por el nuevo
- [x] App Check con **Play Integrity** registrado
- [x] Token de depuración nuevo agregado (el viejo estaba atado a la app vieja)
- [x] App vieja `com.example.cefrspeakingcoach` borrada de Firebase

---

## PASO 3 — Compilar y probar ✅ HECHO

- [x] Compila e instala
- [x] La conversación con Gemini responde → App Check quedó bien
- [x] Modelo de voz descargado desde GitHub Releases: 904 voces, funcionando

---

## PASO 4 — Publicar el código (GPL v3) ✅ HECHO

**Repositorio:** https://github.com/aiacos0195-co/cefr-speaking-coach

- [x] Repo público creado con el nombre que ya traía `SOURCE_CODE_URL`
      (`AboutScreen.kt`, línea 45) — no hubo que cambiar el enlace
- [x] `git init` y commit único `a7d93c2` "CEFR Speaking Coach bajo GPL v3":
      109 archivos, 15.879 líneas, rama `main`
- [x] Firmado como `Andy Páez <268912546+aiacos0195-co@users.noreply.github.com>`
      — el correo privado de GitHub, no el real. Identidad puesta con `--local`,
      así que solo aplica a este repo
- [x] Push hecho y verificado: se ven README y LICENSE, y GitHub muestra la
      etiqueta **GPL-3.0** junto al nombre del repo

### Lo que quedó FUERA, verificado contra el commit

| No publicado | Dónde está |
|---|---|
| `app/google-services.json` | En disco; el `.gitignore` lo excluye |
| `local.properties` | En disco; excluido |
| `.idea/` (16 archivos) | En disco; excluido |
| `build/` y `app/build/` (419 MB) | En disco; excluidos |
| `tts-server/` | Borrado — ver abajo |

Barrido de secretos sobre el árbol publicado: sin llaves de Google/Firebase,
ElevenLabs, OpenAI ni AWS, sin tokens de GitHub, sin rutas `C:\Users\`.

El único dato personal que sí se publicó está ahí a propósito: `CONTACT_EMAIL`
en `AboutScreen.kt` línea 37. La GPL quiere que quien recibe la app pueda
conseguir el código y contactar al autor.

### `tts-server` — tema cerrado

Era el servidor de pruebas de ElevenLabs. Al preparar el repo salió que tenía un
`.env` con una API key y que además era un repo git embebido, así que `git add .`
lo iba a meter como puntero vacío.

Ya no hay nada que cuidar: la carpeta **está borrada del disco**, su **repo
privado de GitHub está borrado**, y la **API key de ElevenLabs fue revocada**.
El `.gitignore` conserva las reglas de `tts-server/` y `.env` por si acaso.

### Si algún día vuelves a hacer `git add .`

Mira el `git status` antes del commit. Nunca debe aparecer:
`app/google-services.json`, ningún `.jks` ni `.keystore`, `local.properties`,
ningún `.env`. Si aparece alguno, el `.gitignore` se rompió: para y avísame.
Borrarlo después no sirve — queda en el historial de Git para siempre.

---

## 🔴 ABIERTOS — salieron el 20 de septiembre

Van antes que el resto del paso 5.

### A.1 La transcripción se corta en las sesiones por nivel · PRIORITARIO

En una sesión de práctica por nivel, al tocar **Start record**, una pausa mínima
al hablar corta la transcripción. El alumno se detiene a pensar medio segundo y
pierde lo que venía diciendo.

**Es el mismo problema que ya resolvimos en AI Conversation.** La sospecha es que
las sesiones usan el reconocedor viejo de `MainActivity` (`startRecording`) y no
el de `AIConversationScreen`, donde el arreglo ya vive.

El arreglo es **extraer a un componente compartido** la lógica que ya funciona,
no escribir un segundo parche. Dos reconocedores con dos arreglos distintos es
cómo se llega a tener el mismo bug dos veces.

> **NO modo continuo.** Reiniciar el reconocedor y coser los trozos está
> descartado en el Anexo A: perdía texto. Esto no lo reabre.

### A.2 El ícono sigue siendo el robot verde

El de la plantilla de Android Studio. Hay que hacer uno propio antes de publicar;
también hace falta para la ficha de Play (5.5).

### A.3 La portada dice "PREMIUM ACCESS"

La app es gratuita. Quitarlo.

### A.4 Practice Plan no topa el contador en la meta

Muestra **"9/3 sessions completed"**. El contador sigue subiendo pasada la meta
en vez de quedarse en 3/3. Dos cosas que mirar de una vez: el número, y la
barra de progreso, que con una fracción mayor que 1 puede dibujarse rara.

### A.5 Números y fechas siguen el idioma del teléfono, el texto el de la app

Se ve mezclado dentro de una misma pantalla:

- Progress: **"2,6"** con coma al lado de **"/ 5.0"** con punto
- Historial: **"sept 21"** en una pantalla que está en inglés

La causa es que hay dos fuentes de idioma. El texto sale de `UiLanguage`, que
el alumno elige con el botón de traducción; los números y las fechas salen de
`Locale.getDefault()`, que es el del teléfono. En un teléfono en español con la
app en inglés, cada frase mezcla los dos.

La regla que hay que aplicar distingue dos tipos de pantalla:

- **Las bilingües** (Settings, Coach Voice, About) formatean según `UiLanguage`,
  no según el teléfono. Si el alumno eligió español, la coma decimal es correcta.
- **Las que son siempre en inglés** (Progress, History, Practice Plan, Session)
  formatean **en inglés**, no según el teléfono. Ahí no hay elección que seguir.

Ojo al revisar: `formatReminderTime` en `SettingsScreen` ya usa
`Locale.getDefault()`. Con `"%d:%02d"` no se nota —no hay separador decimal—
pero es el mismo patrón y conviene dejarlo coherente.

---

## PASO 5 — Lo que falta antes de publicar en Play

### 5.1 Esconder la pantalla de audición en release ✅ HECHO

La entrada del menú va envuelta en `BuildConfig.DEBUG` (`AppDrawerShell.kt`). En
release no se dibuja, y como no hay otra forma de navegar hasta ahí, la pantalla
queda inalcanzable. El código sigue en su sitio para seguir afinando voces en
debug.

### 5.2 Decidir sobre `coach_voice_packs`

Los WAV de síntesis concatenativa son el motor de respaldo antes de la voz de
Android. La pregunta sigue sin responder: cuando borraste el modelo y el coach
habló con esos WAV, **¿sonó mejor o peor que la voz del sistema?**

- Si peor → quitamos el motor completo (5 archivos de código + los assets)
- Si mejor → se queda

### 5.3 Traducción en Settings ✅ HECHO

`SettingsStrings.kt`, con el mismo patrón de `AboutStrings` y `CoachVoiceStrings`.

De paso se arregló algo que venía de antes: el idioma vivía dentro de cada
pantalla, así que la barra superior del drawer seguía en inglés encima de una
pantalla en español. Ahora el idioma vive en `MainActivity` y baja tanto a las
tres pantallas traducidas como a la barra.

### 5.4 Play Console y App Check en producción

⚠️ **Fecha límite: 2 de noviembre de 2026** — quedan unas seis semanas. Después
de esa fecha, Firebase rechaza toda llamada a Gemini sin token válido de App
Check.

- [ ] Publicar la app (aunque sea en prueba interna)
- [ ] **Copiar la SHA-256 de la "clave de firma de la app" que muestra Play
      Console** y agregarla en Firebase. Con Play App Signing, Google vuelve a
      firmar tu APK con otra llave; si registras solo la tuya, Play Integrity
      falla para los usuarios reales **y a ti te sigue funcionando en debug**.
      Es el error más difícil de detectar de toda esta lista.
- [ ] Vincular el proyecto de Firebase con Google Play
- [ ] Mirar las métricas en App Check → pestaña APIs
- [ ] Activar la obligatoriedad **solo** cuando veas tráfico verificado real
- [ ] **Protección contra repetición (replay protection) en AI Logic.** Quedó
      sin aplicar en la consola a propósito: exige que la app pida *tokens de un
      solo uso* (limited-use tokens) al inicializar Firebase AI, y hoy no lo
      hace. Activarla antes de cambiar el código deja la conversación sin
      responder. Primero el código, después el interruptor.

### 5.7 Presupuesto de razonamiento en la conversación

`feedbackModel`, `promptBankModel` y `coachingModel` van con
`thinkingBudget = 0`: son tareas de esquema fijo y el razonamiento solo les
gastaba presupuesto de salida.

`conversationModel` quedó **sin tocar**, con el razonamiento por defecto de
Gemini 2.5 y `maxOutputTokens = 1500`. Funciona, así que no se cambió en la
misma tanda.

**Pendiente:** probar ahí un presupuesto bajo — 256 o 512, **no cero** — a ver
si acorta la demora de las respuestas del coach. A diferencia del examinador,
aquí el razonamiento sí puede estar aportando: decidir qué responder y qué
corregir es una tarea abierta. Por eso se prueba, no se apaga.

### 5.9 Versión 1 sin inicio de sesión ni Firestore

**Decisión (21-sep-2026).** La v1 sale sin cuenta de Google y sin nube. El
progreso lo cubre el Auto Backup de Android. La base de Firestore nunca llegó a
crearse, así que no hay datos que migrar.

Lo que se gana: desaparece el "recogido por el desarrollador" del formulario de
Seguridad de los datos. Sin correo, sin nombre, sin transcripciones en un
servidor propio.

**Impedimento que hay que resolver en el mismo cambio.** Auto Backup tiene un
tope de 25 MB por app, y el modelo de voz vive en `filesDir` y ocupa ~96 MB.
Medido con `bmgr` en el S24: sin la voz descargada el paquete da Success con
316 KB; con la voz, **"Size quota exceeded"**. Y cuando se pasa el tope Android
**no recorta: descarta la copia entera, en silencio**. O sea que hoy, con Auto
Backup "activado", el progreso de quien haya descargado la voz no se respalda.

**Tres commits, en este orden:**

1. **Reglas de respaldo.** Lista de **inclusión**, no de exclusión: solo los seis
   `.xml` de la app. Así `filesDir` queda fuera —el modelo con él— y ningún
   interno de Firebase entra nunca, se llame como se llame en la versión que
   venga. Con la extensión en el `path`, que si no coincide deja las prefs fuera
   sin avisar:

   `cefr_sessions.xml` · `cefr_settings.xml` · `cefr_prompt_store.xml` ·
   `sherpa_speaker_ids.xml` · `ui_language_prefs.xml` · `voice_download_prefs.xml`

   En `backup_rules.xml` y en las **dos** secciones de
   `data_extraction_rules.xml` (`cloud-backup` y `device-transfer`), para que el
   comportamiento sea igual por cualquier camino.

   ⚠️ El costo de la lista de inclusión: **unas prefs nuevas hay que agregarlas
   a las dos reglas**, o dejan de respaldarse en silencio. Va comentado en cada
   `getSharedPreferences`.

   Incluye el texto de Coach Voice: "Se descarga una vez en cada teléfono y
   queda guardada ahí."

2. **Fuera login y Firestore.** `AuthManager.kt` y `FirestoreSessionRepository.kt`;
   seis dependencias (`firebase-auth`, `firebase-firestore`, las dos de
   `credentials`, `googleid` y `kotlinx-coroutines-play-services`); el bloque de
   cuenta del drawer con sus cinco parámetros; unas 60 líneas de `MainActivity`.
   `google-services.json` y su plugin se quedan: los usan AI Logic y App Check.

   Con el texto nuevo de privacidad en "Acerca de", en los dos idiomas.

3. **Evaluación duplicada.** Marca de "esta transcripción ya se evaluó", limpiada
   en `beginCapture()`, con el botón **deshabilitado** — no una guarda que
   ignore el toque en silencio. Hoy dos pulsaciones guardan dos sesiones
   idénticas, porque el id sale de `System.currentTimeMillis()` y el dedup de
   `SessionStore` compara por id.

**Plan de prueba** — en este orden. **La restauración va de última**, porque
desinstalar cambia el token de depuración de App Check y hay que registrar el
nuevo antes de volver a probar nada que use AI Logic.

**1. Respaldo con la voz descargada.** Es la prueba que justifica el commit de
reglas.

```
adb shell bmgr backupnow com.cefrspeakingcoach.app
```

Debe dar Success. **La línea que vale es la del paquete** — la última,
`Backup finished with result: Success`, se refiere a la corrida y sale bien
aunque el paquete falle.

**2. Tamaño: que no entre nada de más.**

```
adb shell run-as com.cefrspeakingcoach.app ls -l shared_prefs
```

Sumar los seis y comparar con lo que reportó `bmgr`. Si `bmgr` da claramente
más que la suma, está entrando algo que no debería.

**3. Sin inicio de sesión.** La app abre directo a la sesión: ni pantalla de
Google, ni entrada de cuenta en el menú, ni mención de sincronización en ningún
texto. El historial y el progreso siguen cargando —viven en `cefr_sessions.xml`,
que no se tocó.

**4. Una transcripción, una evaluación.** Grabar → Evaluate → esperar el
resultado → Evaluate otra vez: el botón queda deshabilitado y dice
`Already evaluated - record again`, y el historial **no** gana una sesión
repetida. Grabar de nuevo, o New Prompt, lo vuelve a habilitar.

**5. Grabar, no evaluar, y pulsar Refresh Prompt (AI):** la transcripción se
borra y Evaluate queda deshabilitado.

**6. Restauración — al final.**

Primero el APK. **"Run" de Android Studio no actualiza
`app/build/outputs/apk/debug/`**: el que esté ahí puede ser de hace semanas.
Generarlo desde **Build → Build APK(s)** y **comprobar la fecha del archivo**
antes de instalar.

Esta prueba cambia un ajuste del teléfono, así que va con el camino de vuelta.
Anotar primero cuál transporte tiene el `*`:

```
adb shell bmgr list transports
adb shell bmgr transport com.android.localtransport/.LocalTransport
adb shell bmgr backupnow com.cefrspeakingcoach.app
adb uninstall com.cefrspeakingcoach.app
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell bmgr list sets          # tomar el token
adb shell bmgr restore <token> com.cefrspeakingcoach.app
```

Vuelta al original, **sin falta** — es un teléfono personal y sus copias reales
no pueden dejar de hacerse:

```
adb shell bmgr transport <el que tenía el * al principio>
adb shell bmgr list transports    # el * de vuelta donde estaba
adb shell bmgr enabled            # tiene que decir enabled
```

Tres criterios al abrir la app restaurada:

- El historial vuelve, con las sesiones que había.
- Coach Voice dice "Voz natural no descargada" — el modelo de 96 MB no viajó,
  que es justo lo que queremos.
- El recordatorio: **o** queda una alarma programada, **o** aparece la tarjeta
  de notificaciones bloqueadas. Las dos son correctas, porque Android puede
  restaurar o no el permiso. **Que no salga ninguna de las dos es el fallo.**

```
adb shell "dumpsys alarm | grep cefrspeakingcoach"
```

**Las comillas no son opcionales.** En PowerShell, sin ellas el `grep` corre en
Windows, donde no existe; hay que mandar la tubería entera dentro del `adb
shell`.

Al terminar: registrar en Firebase el token de depuración nuevo de App Check.

### 5.8 Evaluar con audio en vez de texto

Hoy el examinador recibe la **transcripción**, no la voz. Eso arrastra dos
límites que no se arreglan con prompts:

- Los errores del reconocedor llegan mezclados con los del alumno. El prompt ya
  le pide a Gemini que no los penalice, pero eso es mitigar, no resolver.
- **La pronunciación no se puede medir.** Por eso se quitó del esquema: pedirle
  una nota a un modelo que solo ve texto le obligaba a inventarla.

Y un tercero, de la misma familia: **las muletillas tampoco se pueden contar.**
`fillerCount` estaba fijo en 0 y se le mandaba a Gemini como si fuera una
medición — le decíamos en cada evaluación que el alumno no había dicho ni un
"um". Ya se quitó del prompt y de la pantalla. Contarlas desde el texto no
serviría: el reconocedor de Google suele eliminar los "um" y "uh" antes de
entregar la transcripción, así que el texto siempre diría cero aunque el alumno
dudara todo el tiempo.

Gemini 2.5 Flash acepta audio. Mandarle la grabación resolvería los tres de una
vez: sin errores del reconocedor, con pronunciación medida de verdad, y con las
muletillas audibles donde sí están.

**El costo es real:** `SpeechRecognizer` no entrega el audio, así que habría que
grabar en paralelo con `MediaRecorder`, manejar el archivo y subirlo.

⚠️ **Y antes de tocar una línea de código, hay que actualizar la sección de
privacidad de "Acerca de".** Hoy dice que lo que sale del teléfono es el *texto*
de la conversación. Con esto saldría **la voz del alumno**, que es un dato
personal de otra categoría. Son alumnos militares hablando de su día: el aviso
va primero, y el formulario de Seguridad de los datos de Play (5.5) tiene que
decir lo mismo.

### 5.5 Ficha de Play

- [ ] Política de privacidad (obligatoria). Debe decir que el texto de la
      conversación se envía a Google/Gemini. El texto de "About" te sirve de base.
- [ ] Formulario de Seguridad de los datos, coherente con lo anterior
- [ ] Ícono, capturas, descripción
- [ ] Clasificación de contenido

### 5.6 El recordatorio diario ✅ HECHO

No estaba en esta lista y resultó estar roto: no llegaba con el teléfono en
reposo.

**Causa:** `setRepeating` es inexacta desde API 19, y en Doze se aplazaba a la
siguiente ventana de mantenimiento. Con la pantalla encendida llegaba; dormido,
no. El `SCHEDULE_EXACT_ALARM` del manifest estaba declarado y no se usaba en
ninguna parte.

**Arreglado:**

- `setAndAllowWhileIdle`, un disparo a la vez, reprogramando el siguiente al
  recibir cada uno (`ReminderReceiver`). Sin alarmas exactas: para un
  recordatorio de práctica el margen de ~9 minutos sobra, y evita un permiso
  que en Android 14+ el alumno tendría que conceder a mano y que Play obliga a
  justificar
- `SCHEDULE_EXACT_ALARM` fuera del manifest
- `BootReceiver` nuevo: Android borra las alarmas al apagar, y sin esto el
  recordatorio quedaba muerto hasta que el alumno abriera la app — que es
  justo lo que el recordatorio venía a provocar
- La pantalla dice si el próximo recordatorio es **hoy** o **mañana**. Elegir el
  minuto en curso lo manda a mañana, y antes eso era invisible
- Tarjeta de aviso cuando el interruptor está encendido pero las notificaciones
  están bloqueadas, con botón directo a los ajustes de la app. Al conceder el
  permiso y volver, la alarma se programa: si no, la advertencia desaparecía sin
  arreglar nada
- Botón "Probar recordatorio ahora", solo en debug, por el mismo camino que la
  alarma real

**Probado:** dispara en reposo (22:55 → 23:00), el receptor reprograma, y tras
reiniciar el teléfono `BootReceiver` vuelve a registrarla a los 66 segundos.

---

## Anexo A — Decisiones tomadas (para no volver a discutirlas)

| Tema | Decisión | Razón |
|---|---|---|
| Acento de las voces | Todas americanas | VCTK no daba un británico convincente |
| Modelo | `libritts_r-medium`, 904 voces | Ya afinado y funcionando |
| Modelo británico | Descartado y borrado | No cumplía; ocupaba 94 MB |
| Dónde vive el modelo | Descarga desde tu GitHub Releases | Sacar 95 MB del APK |
| ElevenLabs | Descartado | Voces protegidas |
| Reinicio **ingenuo** del reconocedor | Descartado | Reiniciar y coser sin merge ni dedup perdía y duplicaba texto |
| Reinicio **con acumulación** | En uso en AI Conversation | Con merge por prefijo y dedup encima, sí funciona. Ver la nota de abajo |
| Licencia | **GPL v3, código público** | espeak-ng (GPL) va dentro de sherpa-onnx |
| `applicationId` | `com.cefrspeakingcoach.app` | Play rechaza `com.example` |
| Voz de James | `sid 92` en libritts_r (antes 8) | Afinada de oído, 20-sep-2026 |
| `tts-server` / ElevenLabs | Borrado del disco y de GitHub, llave revocada | Motor descartado; no dejar credenciales sueltas |

### Nota sobre el reconocimiento de voz — leer antes de tocarlo

Esta distinción se perdió una vez y costó una instrucción equivocada, así que
queda escrita.

Lo que se descartó fue **reiniciar el reconocedor y pegar los trozos, sin más**.
Así perdía texto: los motores de Android devuelven muchas veces la frase entera
acumulada en cada sesión en vez de solo lo nuevo, y otras veces devuelven un
final en blanco aunque las palabras sí hayan llegado por los parciales. Pegar a
ciegas duplicaba lo primero y perdía lo segundo.

Lo que corre hoy en `AIConversationScreen` **sí reinicia**, y funciona porque
encima lleva la capa que a la versión ingenua le faltaba:

- `appendCommittedSegment` con merge por prefijo: si el segmento nuevo contiene
  al anterior como prefijo, lo reemplaza en vez de sumarlo
- `normalizeForDedup` contra repeticiones, no solo contra la inmediata anterior
- Rescate del final en blanco: si `onResults` llega vacío, se compromete lo que
  haya en `currentPartialSegment` en lugar de tirarlo

Y por debajo de todo eso, independiente del reinicio, están las **ventanas de
silencio** del intent (`COMPLETE_SILENCE 4000`, `POSSIBLY_COMPLETE 2500`,
`MINIMUM_LENGTH 1500`), que por sí solas ya toleran pausas de unos cuatro
segundos dentro de una sola sesión de reconocimiento.

**Son dos capas separables**, y conviene no confundirlas al hablar de esto:

| Capa | Qué da | ¿Reinicia? |
|---|---|---|
| Ventanas de silencio | pausas de hasta ~4 s | No |
| Reinicio con acumulación | pausas de cualquier duración | Sí |

⚠️ Los extras de silencio son **ignorados por varios fabricantes**. Si en un
teléfono concreto la capa de ventanas no alcanza, la de reinicio es el plan B y
tiene que seguir estando disponible.

## Anexo B — Datos que vas a necesitar

- **Repo del código:** `github.com/aiacos0195-co/cefr-speaking-coach` (público, GPL v3)
- **Repo de las voces:** `github.com/aiacos0195-co/voices-v1`, tag `models-v1`
- **Archivo del modelo:** `en_us_libritts_r.zip` (81,8 MB)
- **Proyecto Firebase:** `cefr-speaking-coach`
- **Paquete:** `com.cefrspeakingcoach.app`
- **Reempaquetar un modelo:** `python tools\repack_model.py <archivo.tar.bz2> <salida.zip>`
- **Defaults de voz:** `SherpaVoiceCatalog.kt` → `DEFAULT_SPEAKER_IDS`

## Anexo C — Si algo falla

| Síntoma | Causa probable |
|---|---|
| El coach no responde en AI Conversation | Token de depuración de App Check. **Cambia cada vez que desinstalas la app o borras sus datos** — hay que registrar el nuevo en Firebase → App Check → Administrar tokens de depuración |
| `grep` "no se reconoce como cmdlet" al leer logs | PowerShell corta la tubería antes de `adb`. Va todo dentro de comillas: `adb shell "dumpsys alarm \| grep ..."` |
| El APK instalado no tiene el cambio | **Run de Studio no escribe en `app/build/outputs/apk/debug/`.** Generar con Build → Build APK(s) y mirar la fecha del archivo |
| El botón Descargar sale gris | `MODELS_BASE_URL` sin configurar |
| "Falló la descarga: HTTP 404" | El repo de voces se volvió privado, o cambió el tag |
| Voz del sistema en vez de neuronal | El modelo no está instalado; míralo en Coach voice |
| Un coach no usa el sid nuevo del catálogo | Tiene una elección guardada; los defaults solo aplican si el usuario nunca tocó esa voz |
| *(histórico)* "No matching client found for package name" | `google-services.json` viejo — resuelto en el paso 2 |
| *(histórico)* "Redeclaration: MainActivity" | Carpeta `com\example\` sin borrar — resuelto en el paso 1 |
