# Conversación con IA — segunda entrega

Esto resuelve el punto 4 de `ANALISIS.md`: la pantalla donde el estudiante
conversa era un chatbot con guion, mientras `FirebaseAiGateway` —con esquemas
JSON ya definidos para evaluación CEFR— solo se usaba desde `SessionScreen`.

Ahora la conversación usa Gemini, corrige, y cae al motor de reglas cuando no
hay red.

---

## 1. Cómo queda el flujo

```
AIConversationScreen
        │
        ▼
AiConversationCoachEngine.nextTurn()      ← nuevo, nunca lanza excepción
        │
        ├─ 1º  FirebaseAiGateway.generateConversationTurn()   (Gemini)
        │        JSON: { reply, correction, cefr_level_estimate }
        │
        └─ 2º  ConversationCoachEngine.buildReply()           (reglas, offline)
```

El orden importa: **la IA es el camino principal y las reglas son el respaldo**,
no al revés. Un estudiante en el wifi de la escuela con conexión intermitente
sigue recibiendo respuesta; solo que guionada, y la barra de estado lo dice
("Coach replied (offline mode)").

`AiConversationCoachEngine.nextTurn()` **nunca lanza**. Cualquier fallo —sin
red, cuota agotada, JSON malformado, respuesta vacía— cae a reglas. Un coach
mudo es peor que un coach guionado.

---

## 2. La corrección se ve, no se escucha

`ConversationMessage` ahora tiene un campo `correction: String?` separado de
`text`. El coach **habla solo `text`**; la corrección se muestra como una
tarjeta "Tip" debajo de la burbuja.

Fue una decisión deliberada. Leer en voz alta *"You said 'I go yesterday'.
Better: 'I went yesterday'"* en mitad de una conversación rompe el flujo y suena
a examen. `playCoachReply()` solo recibe `turn.reply`, nunca la corrección.

El prompt pide corregir únicamente errores que bloquean el significado o que
están claramente por debajo del nivel detectado, e ignorar acento, muletillas y
artefactos de transcripción. Sin esa restricción el modelo corrige todo, todo el
tiempo, que es exactamente lo que desmotiva en A1.

Cuando el enunciado está bien, `correction` viene vacío y no se dibuja nada.

---

## 3. Adaptación por nivel

El nivel sale de `ConversationCoachEngine.detectLevel()` (heurística local, sin
costo) y viaja al prompt como restricción de longitud:

| Nivel | Regla en el prompt |
|---|---|
| A1 | 1–2 frases muy cortas, presente simple, vocabulario de alta frecuencia |
| A2 | 2 frases cortas, pasado y futuro simples permitidos |
| B1 | 2–3 frases, conectores básicos |
| B2 | 3 frases, fraseo natural, algo de idiom |
| C1/C2 | 3–4 frases, fluido y matizado |

Gemini devuelve además su propia estimación en `cefr_level_estimate`, que es la
que se muestra en el chip de la pantalla. Si la llamada falla, se usa la
heurística local.

---

## 4. Saneado del texto antes de hablarlo

`sanitizeForSpeech()` en `AiConversationCoachEngine` elimina markdown, viñetas,
emoji y acotaciones tipo `(smiling)` o `*laughs*`.

No es paranoia: el prompt prohíbe explícitamente esas cosas y aun así los
modelos las emiten de vez en cuando. Un TTS lee `*smiles*` como "asterisco
smiles asterisco". Más barato limpiarlo que confiar en que no aparezca.

También hay un tope de 600 caracteres, cortando en el último punto para no
truncar a mitad de frase.

---

## 5. Estados nuevos en la pantalla

- **`isCoachThinking`** — la llamada a Gemini tarda entre medio segundo y un par
  de segundos. Se muestra una burbuja con spinner ("James is thinking...") y el
  micrófono queda bloqueado mientras tanto, para que el estudiante no hable
  encima de una respuesta que aún no llegó.

- **`turnGeneration`** — contador que se incrementa al reiniciar la conversación
  o al cambiar de coach. Si una respuesta de la IA llega tarde para una
  conversación abandonada, se descarta. Sin esto, cambiar de Sophie a James
  mientras hay una petición en vuelo hace que la respuesta de Sophie aparezca
  bajo el nombre de James.

También quedó cableado el `retryWithNextEngine()` de la primera entrega: si
Sherpa (o el motor personalizado) falla después de aceptar la frase, se
reintenta con el siguiente motor en vez de dejar al coach mudo.

---

## 6. El interruptor

```kotlin
AiConversationCoachEngine.aiEnabled = false
```

Eso es todo. Vuelve al comportamiento anterior de reglas puras, sin tocar la
pantalla ni el gateway. Útil para demos sin red, para comparar A/B, o si el
consumo de cuota resulta más alto de lo esperado.

Buen sitio para engancharlo: `SettingsScreen`, junto a las demás preferencias.

---

## 7. Lo que conviene vigilar

**Costo por turno.** Cada intervención del estudiante es ahora una llamada a
`gemini-2.5-flash` (~500 tokens de salida máximo). Con un curso completo
practicando a diario esto se nota. Mídalo antes de desplegarlo a toda la
escuela, no después.

**Latencia percibida.** Medio segundo se tolera; tres segundos rompen la
sensación de conversación. Si en la práctica se va arriba, la opción es reducir
`maxOutputTokens` y acortar el historial (`HISTORY_TURNS`, hoy 6 turnos).

**El prompt no está probado con estudiantes reales.** Está construido sobre
restricciones razonables, pero cuál es el equilibrio entre corregir y no
interrumpir es algo que solo se ve en el aula. Si nota que corrige de más en A1,
suba el umbral en el prompt antes de tocar código.

**App Check.** El proyecto ya incluye `firebase-appcheck-playintegrity`.
Verifique que esté aplicado en la consola de Firebase antes de publicar: sin
eso, la clave del proyecto expuesta en `google-services.json` permite que
cualquiera consuma su cuota de Gemini.

---

## 8. Archivos de esta entrega

| Archivo | Cambio |
|---|---|
| `AiConversationCoachEngine.kt` | **Nuevo.** IA + respaldo por reglas + saneado |
| `FirebaseAiGateway.kt` | `conversationSchema`, `conversationModel`, `generateConversationTurn()` |
| `ConversationModels.kt` | `ConversationMessage.correction`, `CoachTurn`, `CoachTurnSource` |
| `AIConversationScreen.kt` | Turno asíncrono, estados nuevos, tarjeta "Tip", `retryWithNextEngine` |

`ConversationCoachEngine.kt` no se tocó: sigue siendo el respaldo y todavía
funciona igual que antes.
