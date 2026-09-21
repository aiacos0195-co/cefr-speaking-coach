# Arreglo del build (2) — la causa real

Con su `libs.versions.toml` a la vista, el diagnóstico anterior estaba
incompleto. Corrijo.

## Lo que YO tenía mal en el diagnóstico anterior

Dije que su TOML declaraba `firebase-ai` con una versión vacía. **Falso.** Su
TOML lo tiene bien:

```toml
firebase-ai = { group = "com.google.firebase", name = "firebase-ai" }
```

Sin versión, que es correcto. Me disculpo por mandarlo a buscar un problema que
no estaba ahí.

## La causa real

El TOML tenía DOS cosas que rompían la resolución del BoM:

1. **`firebase-crashlytics` con versión propia fija:**
   ```toml
   firebaseCrashlytics = "20.0.4"
   firebase-crashlytics = { ..., version.ref = "firebaseCrashlytics" }
   ```
   Cuando un módulo Firebase trae su propia versión, rompe la alineación del
   BoM para el resto. `firebase-ai`, que depende del BoM para su versión, queda
   sin resolver → `firebase-ai:.` con la versión vacía.

2. **El BoM del TOML (`firebaseBom = "34.10.0"`) no se aplicaba.** El
   `build.gradle.kts` que le dejé en v3 usaba un BoM literal `33.7.0`
   hardcodeado, ignorando el 34.10.0 del TOML. Dos fuentes de verdad para lo
   mismo.

En resumen: el `firebase-ai:.` con versión vacía era el síntoma; la causa era el
Crashlytics versionado a mano desincronizando el BoM.

## Lo que quedó corregido

**`gradle/libs.versions.toml`:**
- Eliminé `firebaseCrashlytics = "20.0.4"` de `[versions]`.
- `firebase-crashlytics` ahora va SIN `version.ref`, como `firebase-ai`.
- `firebaseBom = "34.10.0"` se mantiene: es la única versión de Firebase que
  queda declarada.

**`app/build.gradle.kts`:**
- El BoM ahora usa `platform(libs.firebase.bom)` (el 34.10.0 del TOML), no el
  33.7.0 hardcodeado. Una sola fuente de verdad.
- `firebase-ai` vuelve al alias `libs.firebase.ai`.
- `firebase-crashlytics` vuelve al alias `libs.firebase.crashlytics`.
- **Reordenado:** el BoM va PRIMERO, antes de cualquier módulo Firebase. El BoM
  solo versiona lo que viene después de él. Antes, `firebase-crashlytics` estaba
  declarado arriba del BoM.

La regla, para que no vuelva a pasar: **el BoM manda y va primero; ningún módulo
Firebase lleva versión propia, ni en el TOML ni en el Gradle.**

## Pasos en Android Studio

1. Reemplace `gradle/libs.versions.toml` y `app/build.gradle.kts` por los de
   este ZIP.
2. `File → Sync Project with Gradle Files`.
3. Si persiste: `Build → Clean Project`, luego `Build → Rebuild Project`.

El log anterior mostraba "16 up-to-date", señal de que Gradle reusaba estado
cacheado. El Clean fuerza a re-resolver dependencias desde cero.

## No pude compilar esto

Se lo digo directo: en mi entorno no hay Gradle ni acceso a los repos de Android
(`dl.google.com`), solo a Maven Central. Verifiqué contra Maven que el BoM
34.10.0 existe (publicado feb 2026) y que incluye `firebase-ai`, pero **la
compilación real la hace usted.** La configuración quedó coherente; si algo más
salta, mándeme el log nuevo.

## Nota sobre versiones

Su BoM 34.10.0 es de febrero de 2026. Hay más nuevos (34.15.0 de junio 2026).
No lo subí porque no hace falta para arreglar esto y prefiero no cambiarle cosas
que funcionan. Si quiere actualizarlo, cambie solo `firebaseBom` en el TOML.
