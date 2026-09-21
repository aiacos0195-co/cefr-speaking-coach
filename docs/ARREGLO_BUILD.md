# Arreglo del build — error de Firebase AI

El build falló con 8 tareas, pero **es una sola causa** repetida:

```
Could not find com.google.firebase:firebase-ai:.
```

Fíjese en los dos puntos finales sin nada después. Eso no es "el artefacto no
existe" (sí existe, es parte de Firebase AI Logic y el BoM lo versiona). Es
"pedí la versión vacía". Las otras 7 tareas fallaron en cascada por lo mismo.

---

## La causa

Su `libs.versions.toml` —el archivo que no vino en el ZIP— declara `firebase-ai`
con una `version.ref` que apunta a una versión vacía, o con una versión literal
en blanco. Algo así:

```toml
[versions]
firebaseAi = ""        # ← vacío

[libraries]
firebase-ai = { group = "com.google.firebase", name = "firebase-ai", version.ref = "firebaseAi" }
```

Como el BoM (`firebase-bom`) ya dicta la versión de todos los módulos Firebase,
esa versión vacía del TOML no debería existir. Al estar, gana sobre el BoM y
resuelve a la nada.

Esto no tiene que ver con mis cambios de las entregas anteriores. `firebase-ai`
ya estaba en su `build.gradle.kts` original; lo único que toqué del bloque
Firebase fue fijar el BoM de `latest.release` a `33.7.0`, y eso es correcto.

---

## Lo que ya apliqué

En `app/build.gradle.kts` reemplacé el alias por la coordenada literal, sin
versión, dejando que el BoM la resuelva:

```kotlin
implementation("com.google.firebase:firebase-ai")
```

Con esto **el build ya debería pasar**. No necesita el TOML para esto.

---

## El arreglo de raíz (recomendado, cuando pueda)

El camino de arriba funciona pero deja el alias roto en el TOML para el día que
otro módulo lo use. Para arreglarlo bien, abra `gradle/libs.versions.toml` y en
la línea de `firebase-ai` **quite la versión**:

```toml
firebase-ai = { group = "com.google.firebase", name = "firebase-ai" }
```

Sin `version` ni `version.ref`. Y si `firebaseAi = ""` en `[versions]` no lo usa
nadie más, bórrelo también. Luego puede volver a poner `implementation(libs.firebase.ai)`
en el Gradle si prefiere el alias. Las dos formas son equivalentes; el BoM manda
en ambas.

---

## Aparte: los flags deprecados de gradle.properties

El log escupió ~15 warnings antes del error, todos de este tipo:

```
The option setting 'android.newDsl=false' is deprecated.
It will be removed in version 10.0 of the Android Gradle plugin.
```

Su `gradle.properties` fija 10 flags de AGP a valores antiguos. Cada uno de esos
valores ya es el default contrario en su AGP actual, así que fijarlos al valor
viejo es justo lo que dispara el warning. En AGP 10 un flag removido pasa de
warning a **error de build**, así que conviene limpiarlos ahora y no cuando lo
obliguen.

No los toqué en su archivo porque no sé si alguno se agregó para resolver algo
puntual. Le dejé `gradle.properties.suggested` con la versión limpia y la lista
de lo que quité, para que compare y decida. Si ninguno se agregó a propósito,
puede reemplazar el suyo por ese.

Esto es independiente del error de Firebase: el build pasa igual sin tocar los
flags. Es higiene para que no le explote en la próxima subida de AGP.

---

## Recordatorio del ZIP

Sigue faltando `gradle/` en lo que me envía. Sin `libs.versions.toml` no puedo
ver ni corregir la raíz de este tipo de errores; solo puedo darle el rodeo por
`build.gradle.kts`. Cuando reempaquete, incluya la carpeta `gradle/` completa
(el TOML y `gradle/wrapper/`), y así la próxima revisión sí la puedo compilar de
verdad antes de devolvérsela.
