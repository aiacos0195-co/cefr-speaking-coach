# CEFR Speaking Coach — Guía de pasos

Para profe Andy. **Estado al 20 de septiembre de 2026.**

| Paso | Estado |
|---|---|
| 1 — Cambio de paquete a `com.cefrspeakingcoach.app` | ✅ **Hecho** |
| 2 — Firebase con el paquete nuevo | ✅ **Hecho** |
| 3 — Compilar y probar | ✅ **Hecho** |
| 4 — Publicar el código en GitHub (GPL v3) | ✅ **Hecho** |
| 5 — Lo que falta antes de Play | ⬜ **Siguiente** |

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

## PASO 5 — Lo que falta antes de publicar en Play

### 5.1 Esconder la pantalla de audición en release

"Voice audition" es una herramienta interna: muestra modelos, speaker ids y
rutas de archivos. Un alumno no debería verla. No hay que borrarla, basta con
ocultar la entrada del menú en builds de release. **Pendiente de hacer.**

### 5.2 Decidir sobre `coach_voice_packs`

Los WAV de síntesis concatenativa son el motor de respaldo antes de la voz de
Android. La pregunta sigue sin responder: cuando borraste el modelo y el coach
habló con esos WAV, **¿sonó mejor o peor que la voz del sistema?**

- Si peor → quitamos el motor completo (5 archivos de código + los assets)
- Si mejor → se queda

### 5.3 Traducción en Settings

El mecanismo bilingüe ya es global (`UiLanguageStore`). Falta el archivo de
textos de esa pantalla. Diez minutos.

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
