# CEFR Speaking Coach — Guía de pasos

Para profe Andy. **Estado al 20 de septiembre de 2026.**

| Paso | Estado |
|---|---|
| 1 — Cambio de paquete a `com.cefrspeakingcoach.app` | ✅ **Hecho** |
| 2 — Firebase con el paquete nuevo | ✅ **Hecho** |
| 3 — Compilar y probar | ✅ **Hecho** |
| 4 — Publicar el código en GitHub (GPL v3) | ⬜ **Siguiente** |
| 5 — Lo que falta antes de Play | ⬜ Pendiente |

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

## PASO 4 — Publicar el código (GPL v3) ⬅ SIGUIENTE

### 4.1 Decide el nombre del repositorio

En `AboutScreen.kt`, línea ~45, está:

```kotlin
private const val SOURCE_CODE_URL = "https://github.com/aiacos0195-co/cefr-speaking-coach"
```

Ajústala al nombre real que le vayas a poner. **No es decorativo:** la GPL exige
que quien recibe la app pueda conseguir el código, y ese enlace es como lo
cumples desde la app.

### 4.2 Crea el repositorio

En GitHub, **público**, con el nombre que decidiste. Sin README (ya tienes uno).

### 4.3 Sube el código

El proyecto **todavía no tiene repo git**. En la terminal, dentro de la carpeta
del proyecto:

```
git init
git add .
git status
```

### 4.4 🔴 REVISA `git status` ANTES DE HACER COMMIT

Confirma que en la lista de archivos **NO aparezca**:

- `app/google-services.json`
- ningún `.jks` o `.keystore`
- `local.properties`

**Si aparece `google-services.json`, PARA.** El `.gitignore` no se aplicó bien;
avísame antes de seguir.

**Por qué importa tanto:** si haces commit con ese archivo, borrarlo después no
sirve — queda en el historial de Git para siempre y hay que reescribirlo. Y con
él, cualquiera puede apuntar su copia a *tu* proyecto de Firebase y gastarte la
cuota de Gemini, que pagas tú.

> El `.gitignore` actual ya cubre `app/google-services.json`, `*.jks`,
> `*.keystore`, `keystore.properties`, `local.properties`, `/build` y los AAR de
> sherpa-onnx. Aun así, revisa el `git status` con los ojos.

### 4.5 Ahora sí

```
git commit -m "CEFR Speaking Coach bajo GPL v3"
git branch -M main
git remote add origin https://github.com/aiacos0195-co/NOMBRE-DEL-REPO.git
git push -u origin main
```

### 4.6 Verifica en el navegador

- [ ] Se ve el README con la explicación de la licencia
- [ ] Se ve el archivo LICENSE
- [ ] GitHub muestra "GPL-3.0" junto al nombre del repo
- [ ] **NO** existe `app/google-services.json` en el repo

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

## Anexo B — Datos que vas a necesitar

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
