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
| Modo continuo de reconocimiento | Descartado | Perdía texto |
| Licencia | **GPL v3, código público** | espeak-ng (GPL) va dentro de sherpa-onnx |
| `applicationId` | `com.cefrspeakingcoach.app` | Play rechaza `com.example` |
| Voz de James | `sid 92` en libritts_r (antes 8) | Afinada de oído, 20-sep-2026 |
| `tts-server` / ElevenLabs | Borrado del disco y de GitHub, llave revocada | Motor descartado; no dejar credenciales sueltas |

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
| El coach no responde en AI Conversation | Token de depuración de App Check |
| El botón Descargar sale gris | `MODELS_BASE_URL` sin configurar |
| "Falló la descarga: HTTP 404" | El repo de voces se volvió privado, o cambió el tag |
| Voz del sistema en vez de neuronal | El modelo no está instalado; míralo en Coach voice |
| Un coach no usa el sid nuevo del catálogo | Tiene una elección guardada; los defaults solo aplican si el usuario nunca tocó esa voz |
| *(histórico)* "No matching client found for package name" | `google-services.json` viejo — resuelto en el paso 2 |
| *(histórico)* "Redeclaration: MainActivity" | Carpeta `com\example\` sin borrar — resuelto en el paso 1 |
