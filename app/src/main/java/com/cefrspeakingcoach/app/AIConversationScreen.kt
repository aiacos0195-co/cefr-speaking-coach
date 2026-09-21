package com.cefrspeakingcoach.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AIConversationScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedVoice by remember { mutableStateOf<ConversationVoiceModel?>(null) }
    val messages = remember { mutableStateListOf<ConversationMessage>() }

    var detectedLevel by remember { mutableStateOf("A1") }
    var isListening by remember { mutableStateOf(false) }
    var isStickyListening by remember { mutableStateOf(false) }
    var manualStopRequested by remember { mutableStateOf(false) }
    var isCoachSpeaking by remember { mutableStateOf(false) }
    var isCoachThinking by remember { mutableStateOf(false) }

    // Bumped whenever the conversation is reset or the coach is changed, so an
    // AI reply that arrives late for an abandoned conversation is discarded
    // instead of appearing under a different coach.
    var turnGeneration by remember { mutableStateOf(0) }

    var statusText by remember { mutableStateOf("Choose a conversation partner") }
    var liveTranscript by remember { mutableStateOf("") }

    var pendingCoachSpeech by remember { mutableStateOf<Pair<String, ConversationVoiceModel>?>(null) }
    var voiceReadyTick by remember { mutableStateOf(0) }

    val committedSegments = remember { mutableStateListOf<String>() }
    var currentPartialSegment by remember { mutableStateOf("") }
    var lastRecognizedText by remember { mutableStateOf("") }
    var hasSubmittedCurrentUtterance by remember { mutableStateOf(false) }

    // On-screen diagnostic trace of the raw recognizer flow. Visible in the UI
    // so it can be read from a screenshot without a logcat capture. Toggle with
    // SHOW_RECOGNIZER_TRACE below. Remove both once the transcript is confirmed.
    val recognizerTrace = remember { mutableStateListOf<String>() }
    var sessionCounter by remember { mutableStateOf(0) }
    fun trace(msg: String) {
        recognizerTrace.add(msg)
        while (recognizerTrace.size > 14) recognizerTrace.removeAt(0)
    }

    // Deferred handle to the orchestrator. voiceCallbacks.onError needs to call
    // the orchestrator, but the orchestrator is built with voiceCallbacks, so
    // one has to exist before the other. This holder breaks that cycle: onError
    // reads it at call time, by which point the orchestrator below has set it.
    val orchestratorHolder = remember { arrayOfNulls<CoachVoiceOrchestrator>(1) }

    val voiceCallbacks = remember {
        CoachVoiceEngineCallbacks(
            onReady = {
                voiceReadyTick++
            },
            onStarted = {
                isCoachSpeaking = true
                statusText = "Coach speaking..."
            },
            onCompleted = {
                isCoachSpeaking = false
                statusText = "Coach replied"
            },
            onError = { engineId, message ->
                // speak() returning true only meant the engine ACCEPTED the
                // utterance. A neural engine can still fail while synthesising,
                // so hand the same line to the next engine before giving up.
                val retried = orchestratorHolder[0]?.retryWithNextEngine(engineId) ?: false
                if (!retried) {
                    isCoachSpeaking = false
                    statusText = message
                }
            }
        )
    }

    val voiceOrchestrator = remember(context) {
        CoachVoiceOrchestrator(
            engines = listOf(
                // Order is priority. Sherpa (offline neural) first: it only
                // reports ready once a model is installed, so until then it is
                // skipped and the others handle speech. AndroidCoachVoiceEngine
                // stays LAST as the guaranteed fallback.
                SherpaCoachVoiceEngine(
                    context = context,
                    callbacks = voiceCallbacks
                ),
                CustomCoachVoiceEngine(
                    context = context,
                    callbacks = voiceCallbacks
                ),
                AndroidCoachVoiceEngine(
                    context = context,
                    callbacks = voiceCallbacks
                )
            )
        ).also { orchestratorHolder[0] = it }
    }

    val speechRecognizer = remember(context) {
        SpeechRecognizer.createSpeechRecognizer(context)
    }

    val recognizerIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // Longer than the defaults on purpose. A1/A2 learners pause to think
            // mid-sentence; short silence windows make the recognizer finalize
            // (often with an empty result) before they finish. One session per
            // tap tolerates these pauses instead of fragmenting across restarts.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500)
        }
    }

    fun stopAllSpeechPlayback() {
        voiceOrchestrator.stop()
        isCoachSpeaking = false
    }

    fun stopRecognizerCompletely() {
        isListening = false
        isStickyListening = false
        manualStopRequested = false
        runCatching { speechRecognizer.cancel() }
    }

    fun normalizeForDedup(text: String): String {
        return text
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
    }

    fun cleanRepeatedConsecutiveWords(text: String): String {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return ""

        val result = mutableListOf<String>()
        for (word in words) {
            val normalized = word.lowercase()
            val previous = result.lastOrNull()?.lowercase()
            if (normalized != previous) {
                result.add(word)
            }
        }
        return result.joinToString(" ")
    }

    fun cleanRepeatedShortPhrases(text: String): String {
        var output = text.trim()

        val repeatedTwoWordPhrase = Regex("""\b(\w+\s+\w+)(\s+\1\b)+""", RegexOption.IGNORE_CASE)
        output = repeatedTwoWordPhrase.replace(output) { match ->
            match.groupValues[1]
        }

        val repeatedThreeWordPhrase = Regex("""\b(\w+\s+\w+\s+\w+)(\s+\1\b)+""", RegexOption.IGNORE_CASE)
        output = repeatedThreeWordPhrase.replace(output) { match ->
            match.groupValues[1]
        }

        return output
    }

    fun normalizeTranscriptForCoach(text: String): String {
        if (text.isBlank()) return ""

        var cleaned = text
            .replace(Regex("\\s+"), " ")
            .trim()

        cleaned = cleanRepeatedShortPhrases(cleaned)
        cleaned = cleanRepeatedConsecutiveWords(cleaned)
        cleaned = cleaned
            .replace(Regex("""\b(i)\b""", RegexOption.IGNORE_CASE), "I")
            .replace(Regex("""\s+([,.!?])"""), "$1")
            .replace(Regex("""([,.!?])([A-Za-z])"""), "$1 $2")
            .replace(Regex("\\s+"), " ")
            .trim()

        return cleaned
    }

    fun buildTranscriptPreview(): String {
        val parts = mutableListOf<String>()
        parts.addAll(committedSegments.filter { it.isNotBlank() })

        if (currentPartialSegment.isNotBlank()) {
            parts.add(currentPartialSegment.trim())
        }

        return parts.joinToString(" ").trim()
    }

    fun appendCommittedSegment(segment: String) {
        val clean = normalizeTranscriptForCoach(segment)
        if (clean.isBlank()) return

        val last = committedSegments.lastOrNull()

        if (last != null) {
            val previousKey = normalizeForDedup(last)
            val incomingKey = normalizeForDedup(clean)

            // Exact repeat of the previous segment: ignore.
            if (previousKey == incomingKey) return

            // Cumulative recognizer. Many Android engines, when restarted in
            // continuous mode, return the WHOLE utterance so far each session,
            // not just the new words. The incoming segment then contains the
            // previous one as a prefix. Appending both piles up the overlap,
            // and normalizeTranscriptForCoach's repeat-cleaning later collapses
            // the pile down to the last (longest) version — which is exactly
            // the "keeps only the last part" bug on long speech. Replacing the
            // previous segment with the longer one keeps a single clean copy.
            if (incomingKey.startsWith(previousKey)) {
                committedSegments[committedSegments.lastIndex] = clean
                return
            }

            // Previous segment already contains the incoming one (a late,
            // shorter partial arriving after a longer final). Keep the longer.
            if (previousKey.startsWith(incomingKey)) return
        }

        // Guard against a segment that duplicates one committed earlier in the
        // utterance, not just the immediately previous one.
        val incomingKey = normalizeForDedup(clean)
        val alreadyExists = committedSegments.any { normalizeForDedup(it) == incomingKey }
        if (alreadyExists) return

        committedSegments.add(clean)
    }

    fun clearTranscriptBuffers() {
        committedSegments.clear()
        currentPartialSegment = ""
        liveTranscript = ""
        lastRecognizedText = ""
        hasSubmittedCurrentUtterance = false
    }

    fun playCoachReply(
        text: String,
        voice: ConversationVoiceModel
    ) {
        if (!voiceOrchestrator.isReadyFor(voice)) {
            // Per-voice, not global. isReady() is true whenever ANY engine is
            // ready (the James pack makes CustomCoachVoiceEngine report ready),
            // but that engine cannot speak a coach with no pack, e.g. Emma. The
            // old global check passed, speak() then failed, and because it never
            // queued, the coach stayed mute forever. Waiting on isReadyFor()
            // lets the Android TTS fallback finish initializing first.
            pendingCoachSpeech = text to voice
            statusText = "Preparing coach voice..."
            return
        }

        pendingCoachSpeech = null

        stopRecognizerCompletely()
        currentPartialSegment = ""
        liveTranscript = ""

        isCoachSpeaking = true
        statusText = "Coach speaking..."

        val started = voiceOrchestrator.speak(
            text = text,
            voice = voice
        )

        if (!started) {
            isCoachSpeaking = false
            statusText = "Coach voice unavailable"
        }
    }

    fun handleUserUtterance(userText: String) {
        val voice = selectedVoice ?: return
        val clean = normalizeTranscriptForCoach(userText)
        if (clean.isBlank()) return

        messages.add(
            ConversationMessage(
                id = "user_${System.currentTimeMillis()}",
                role = ConversationRole.USER,
                text = clean
            )
        )

        clearTranscriptBuffers()

        val myGeneration = turnGeneration
        isCoachThinking = true
        statusText = "Coach is thinking..."

        scope.launch {
            // Never throws: falls back to the local rule engine on any failure.
            val turn = AiConversationCoachEngine.nextTurn(
                voice = voice,
                userText = clean,
                messages = messages.toList()
            )

            // Conversation restarted or coach changed while we were waiting.
            if (myGeneration != turnGeneration) return@launch

            isCoachThinking = false
            detectedLevel = turn.levelEstimate ?: detectedLevel

            messages.add(
                ConversationMessage(
                    id = "coach_${System.currentTimeMillis()}",
                    role = ConversationRole.COACH,
                    text = turn.reply,
                    correction = turn.correction
                )
            )

            statusText = if (turn.source == CoachTurnSource.LOCAL_RULES) {
                "Coach replied (offline mode)"
            } else {
                "Coach replied"
            }

            playCoachReply(turn.reply, voice)
        }
    }

    fun submitCurrentUtteranceIfNeeded() {
        if (hasSubmittedCurrentUtterance) return

        val finalText = normalizeTranscriptForCoach(
            buildTranscriptPreview()
                .ifBlank { liveTranscript.trim() }
                .ifBlank { lastRecognizedText.trim() }
        )

        trace(
            "SUBMIT committed=${committedSegments.size} " +
                "preview=\"${buildTranscriptPreview().take(40)}\" -> \"${finalText.take(40)}\""
        )

        isListening = false
        isStickyListening = false
        manualStopRequested = false

        if (finalText.isBlank()) {
            statusText = "No speech captured"
            clearTranscriptBuffers()
            return
        }

        hasSubmittedCurrentUtterance = true
        handleUserUtterance(finalText)
    }

    fun restartRecognizerIfNeeded() {
        // Manual-stop model: after the recognizer finalizes a chunk (which it
        // does on its own after a short silence, we cannot stop that), we
        // silently start a new session and keep the button in its "recording"
        // state. The user's words accumulate across pauses and are only sent
        // when they tap to stop. Accumulation is safe now that partials and
        // committed segments are prefix-merged.
        if (!isStickyListening || manualStopRequested || isCoachSpeaking) return
        if (isCoachThinking) return

        scope.launch {
            delay(400)
            if (!isStickyListening || manualStopRequested || isCoachSpeaking) return@launch
            try {
                isListening = true
                statusText = "Keep talking, or tap to send"
                speechRecognizer.startListening(recognizerIntent)
            } catch (_: Exception) {
                isListening = false
                statusText = "Unable to restart microphone"
            }
        }
    }

    fun startStickyListeningFlow() {
        if (selectedVoice == null) return
        if (isCoachThinking) {
            statusText = "Wait for the coach to answer"
            return
        }
        if (isCoachSpeaking) {
            statusText = "Wait until the coach finishes speaking"
            return
        }

        stopAllSpeechPlayback()
        clearTranscriptBuffers()

        manualStopRequested = false
        isStickyListening = true

        try {
            isListening = true
            statusText = "Listening..."
            speechRecognizer.startListening(recognizerIntent)
        } catch (_: Exception) {
            isListening = false
            isStickyListening = false
            statusText = "Unable to start microphone"
        }
    }

    fun stopStickyListeningFlow() {
        manualStopRequested = true
        isStickyListening = false

        // Commit ONLY the in-flight partial segment. Every prior segment is
        // already in committedSegments from its own onResults; re-appending the
        // whole preview here (the old behaviour) added the concatenation as a
        // fresh segment, which duplicated everything on submit.
        val tail = normalizeTranscriptForCoach(currentPartialSegment)
        if (tail.isNotBlank()) {
            appendCommittedSegment(tail)
            currentPartialSegment = ""
        }

        val assembled = committedSegments.joinToString(" ").trim()
        android.util.Log.d(
            TRANSCRIPT_TAG,
            "stop: committed=${committedSegments.size} -> \"$assembled\""
        )

        if (assembled.isNotBlank()) {
            liveTranscript = assembled
            statusText = "Sending..."

            runCatching { speechRecognizer.cancel() }
            submitCurrentUtteranceIfNeeded()
        } else {
            statusText = "Stopping..."
            try {
                speechRecognizer.stopListening()
            } catch (_: Exception) {
                submitCurrentUtteranceIfNeeded()
            }
        }
    }

    fun resetWithVoice(voice: ConversationVoiceModel) {
        stopAllSpeechPlayback()
        stopRecognizerCompletely()

        turnGeneration++
        isCoachThinking = false

        selectedVoice = voice
        messages.clear()
        clearTranscriptBuffers()

        val opening = AiConversationCoachEngine.openingMessage(voice)
        messages.add(
            ConversationMessage(
                id = "coach_opening_${System.currentTimeMillis()}",
                role = ConversationRole.COACH,
                text = opening
            )
        )

        detectedLevel = "A1"
        statusText = "Coach ready"
        playCoachReply(opening, voice)
    }

    LaunchedEffect(voiceReadyTick, pendingCoachSpeech) {
        val pending = pendingCoachSpeech
        if (pending != null && voiceOrchestrator.isReadyFor(pending.second)) {
            playCoachReply(
                text = pending.first,
                voice = pending.second
            )
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startStickyListeningFlow()
        } else {
            statusText = "Microphone permission denied"
        }
    }

    DisposableEffect(voiceOrchestrator) {
        voiceOrchestrator.initialize()

        onDispose {
            voiceOrchestrator.release()
        }
    }

    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                if (isCoachSpeaking) {
                    runCatching { speechRecognizer.cancel() }
                    return
                }
                sessionCounter += 1
                trace("[$sessionCounter] READY (committed=${committedSegments.size})")
                statusText = "Listening..."
            }

            override fun onBeginningOfSpeech() {
                if (isCoachSpeaking) {
                    runCatching { speechRecognizer.cancel() }
                    return
                }
                statusText = "Speak naturally..."
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                if (isCoachSpeaking) return
                statusText = if (manualStopRequested) "Sending..." else "Processing..."
            }

            override fun onError(error: Int) {
                isListening = false

                if (isCoachSpeaking) return

                // ERROR_CLIENT is what the framework reports when WE call cancel()
                // or stopListening(). After a manual send we cancel the recognizer,
                // and that echo arrives here a moment later, once the session is
                // already over and buffers are cleared. Treated as a fresh error
                // it showed a bogus "Speech error (5)" right after a good submit.
                // If we are not in an active listening session, ignore it.
                if (error == SpeechRecognizer.ERROR_CLIENT && !isStickyListening) {
                    return
                }

                trace("[$sessionCounter] ERROR=$error partial=\"${currentPartialSegment.take(30)}\" committed=${committedSegments.size}")

                // Single-session model: no restart. Commit whatever the session
                // captured (the recognizer often fires NO_MATCH / TIMEOUT on a
                // pause while still holding good partial text), then either send
                // it or, if there is truly nothing, ask the user to try again.
                val pendingPartial = normalizeTranscriptForCoach(currentPartialSegment)
                if (pendingPartial.isNotBlank()) {
                    appendCommittedSegment(pendingPartial)
                    currentPartialSegment = ""
                    liveTranscript = normalizeTranscriptForCoach(buildTranscriptPreview())
                }

                val haveSomething = committedSegments.isNotEmpty() ||
                    buildTranscriptPreview().isNotBlank()

                android.util.Log.d(
                    TRANSCRIPT_TAG,
                    "onError=$error manualStop=$manualStopRequested haveSomething=$haveSomething " +
                        "committed=${committedSegments.size}"
                )

                if (manualStopRequested) {
                    isStickyListening = false
                    submitCurrentUtteranceIfNeeded()
                    return
                }

                // Recoverable errors are normal in the manual-stop model: they
                // fire on the silence between phrases. Keep the mic alive so the
                // learner can continue. NO_MATCH with nothing committed just
                // means they have not spoken yet, keep waiting.
                val recoverable = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    SpeechRecognizer.ERROR_CLIENT -> true
                    else -> false
                }

                if (isStickyListening && recoverable) {
                    restartRecognizerIfNeeded()
                } else {
                    isStickyListening = false
                    if (haveSomething) {
                        submitCurrentUtteranceIfNeeded()
                    } else {
                        statusText = when (error) {
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "Microphone permission needed."
                            else -> "Speech error ($error). Tap to try again."
                        }
                        clearTranscriptBuffers()
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false

                if (isCoachSpeaking) return

                val best = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()

                if (best.isNotBlank()) {
                    lastRecognizedText = normalizeTranscriptForCoach(best)
                    appendCommittedSegment(best)
                } else {
                    // Empty final. This is the real cause of "keeps only the
                    // last part". After a sticky-mode restart the recognizer
                    // very often returns a blank onResults for the previous
                    // session, even though the words did arrive through
                    // onPartialResults. currentPartialSegment still holds them,
                    // so commit it here instead of letting the reset below throw
                    // it away. Without this, every restarted segment but the
                    // final one is silently dropped.
                    val pendingPartial = normalizeTranscriptForCoach(currentPartialSegment)
                    if (pendingPartial.isNotBlank()) {
                        appendCommittedSegment(pendingPartial)
                    }
                }

                android.util.Log.d(
                    TRANSCRIPT_TAG,
                    "onResults: engine=\"$best\" committedNow=${committedSegments.size} " +
                        "-> ${committedSegments.joinToString(" | ")}"
                )
                trace("[$sessionCounter] FINAL=\"${best.take(40)}\" committed=${committedSegments.size}")

                currentPartialSegment = ""
                liveTranscript = normalizeTranscriptForCoach(buildTranscriptPreview())

                // Manual-stop model. The recognizer finalizes each chunk on its
                // own after a brief silence, but we only SEND when the user taps
                // stop. Until then we keep the mic alive and accumulate, so a
                // learner can pause to think mid-sentence without their turn
                // being cut and sent early. Accumulation is safe: partials and
                // committed segments are prefix-merged, so no duplication.
                if (manualStopRequested) {
                    isStickyListening = false
                    submitCurrentUtteranceIfNeeded()
                } else if (isStickyListening) {
                    restartRecognizerIfNeeded()
                } else {
                    submitCurrentUtteranceIfNeeded()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (isCoachSpeaking) return

                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()

                if (partial.isNotBlank()) {
                    val incoming = normalizeTranscriptForCoach(partial)
                    val prev = currentPartialSegment

                    if (prev.isBlank()) {
                        currentPartialSegment = incoming
                    } else {
                        val a = normalizeForDedup(incoming)
                        val b = normalizeForDedup(prev)

                        when {
                            // Normal growth: the new partial extends the old.
                            a.startsWith(b) -> {
                                currentPartialSegment = incoming
                            }
                            // Momentary shrink: keep the longer previous text.
                            b.startsWith(a) -> {
                                // ignore the shorter re-hypothesis
                            }
                            // Hypothesis reset mid-session. The recognizer threw
                            // away earlier words and started a new phrase (e.g.
                            // "great thank you" -> "I'm"). Commit the old phrase
                            // before the new one overwrites it, otherwise only
                            // the final fragment survives. THIS is the real
                            // "keeps only the last part" cause, seen in the trace.
                            else -> {
                                appendCommittedSegment(prev)
                                currentPartialSegment = incoming
                            }
                        }
                    }

                    lastRecognizedText = currentPartialSegment
                    liveTranscript = normalizeTranscriptForCoach(buildTranscriptPreview())
                    trace("[$sessionCounter] partial=\"${currentPartialSegment.take(40)}\"")
                }

                liveTranscript = normalizeTranscriptForCoach(
                    buildTranscriptPreview().ifBlank { lastRecognizedText }
                )
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        speechRecognizer.setRecognitionListener(listener)

        onDispose {
            runCatching { speechRecognizer.cancel() }
            runCatching { speechRecognizer.destroy() }
            stopAllSpeechPlayback()
        }
    }

    if (selectedVoice == null) {
        ConversationVoiceSelectionScreen(
            voices = DefaultConversationVoices,
            onSelectVoice = { voice -> resetWithVoice(voice) }
        )
    } else {
        ConversationChatScreen(
            voice = selectedVoice!!,
            messages = messages.toList(),
            detectedLevel = detectedLevel,
            isListening = isListening,
            isStickyListening = isStickyListening,
            isCoachSpeaking = isCoachSpeaking,
            isCoachThinking = isCoachThinking,
            statusText = statusText,
            liveTranscript = liveTranscript,
            recognizerTrace = recognizerTrace.toList(),
            onBackToVoices = {
                runCatching { speechRecognizer.cancel() }
                stopAllSpeechPlayback()
                turnGeneration++
                selectedVoice = null
                messages.clear()
                clearTranscriptBuffers()
                isListening = false
                isStickyListening = false
                manualStopRequested = false
                isCoachSpeaking = false
                isCoachThinking = false
                pendingCoachSpeech = null
                statusText = "Choose a conversation partner"
            },
            onTapMic = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (!granted) {
                    recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    return@ConversationChatScreen
                }

                if (isCoachThinking) {
                    statusText = "Wait for the coach to answer"
                    return@ConversationChatScreen
                }

                if (isCoachSpeaking) {
                    statusText = "Wait until the coach finishes speaking"
                    return@ConversationChatScreen
                }

                if (isStickyListening || isListening) {
                    stopStickyListeningFlow()
                } else {
                    startStickyListeningFlow()
                }
            },
            onRestartConversation = {
                selectedVoice?.let { resetWithVoice(it) }
            }
        )
    }
}

@Composable
private fun ConversationVoiceSelectionScreen(
    voices: List<ConversationVoiceModel>,
    onSelectVoice: (ConversationVoiceModel) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "AI Conversation",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Have a spoken conversation with an AI coach. It will adapt to your English level automatically.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Text(
                "Choose your AI conversation partner",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        itemsIndexed(voices.chunked(2), key = { index, _ -> index }) { _, rowVoices ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowVoices.forEach { voice ->
                    ConversationVoiceCard(
                        modifier = Modifier.weight(1f),
                        voice = voice,
                        onClick = { onSelectVoice(voice) }
                    )
                }
                if (rowVoices.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ConversationVoiceCard(
    modifier: Modifier = Modifier,
    voice: ConversationVoiceModel,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    voice.name.first().uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                voice.name,
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                "${voice.genderLabel} · ${voice.accentLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                voice.styleLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConversationChatScreen(
    voice: ConversationVoiceModel,
    messages: List<ConversationMessage>,
    detectedLevel: String,
    isListening: Boolean,
    isStickyListening: Boolean,
    isCoachSpeaking: Boolean,
    isCoachThinking: Boolean,
    statusText: String,
    liveTranscript: String,
    recognizerTrace: List<String> = emptyList(),
    onBackToVoices: () -> Unit,
    onTapMic: () -> Unit,
    onRestartConversation: () -> Unit
) {
    val listState = rememberLazyListState()

    // Number of rows the LazyColumn actually renders: messages, plus the
    // thinking bubble and the transcript card when they are showing. Scrolling
    // targets are computed from this so they never point one row short of the
    // real bottom.
    fun renderedRowCount(): Int {
        return messages.size +
            (if (isCoachThinking) 1 else 0) +
            (if (liveTranscript.isNotBlank()) 1 else 0)
    }

    // A new message or the thinking bubble is a discrete conversational event:
    // always bring the bottom into view. Unconditional on purpose. The bug this
    // replaces was the list staying pinned at the top of the conversation and
    // forcing a manual scroll on every single turn, because the old guarded,
    // multi-effect logic could evaluate false and then never fire again.
    LaunchedEffect(messages.size, isCoachThinking) {
        val rows = renderedRowCount()
        if (rows > 0) {
            listState.animateScrollToItem(rows - 1)
        }
    }

    // The live transcript changes on every partial result, many times a second.
    // Use an instant jump, not an animation, so rapid updates don't fight each
    // other, and only follow while the mic is actually open.
    LaunchedEffect(liveTranscript) {
        if (liveTranscript.isNotBlank() && (isListening || isStickyListening)) {
            val rows = renderedRowCount()
            if (rows > 0) {
                listState.scrollToItem(rows - 1)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBackToVoices) {
                androidx.compose.material3.Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null
                )
                Spacer(Modifier.width(6.dp))
                Text("Back")
            }

            OutlinedButton(onClick = onRestartConversation) {
                androidx.compose.material3.Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = null
                )
                Spacer(Modifier.width(6.dp))
                Text("Restart")
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    voice.name,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    "${voice.genderLabel} · AI Coach",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("~$detectedLevel detected") },
                colors = AssistChipDefaults.assistChipColors(
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    disabledLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(messages, key = { _, item -> item.id }) { _, message ->
                ChatBubble(message = message, coachName = voice.name)
            }

            if (isCoachThinking) {
                item(key = "coach_thinking") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    "${voice.name} is thinking...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (liveTranscript.isNotBlank()) {
                item(key = "live_transcript") {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                if (isStickyListening || isListening) "Current transcript" else "Transcript",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                liveTranscript,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        if (SHOW_RECOGNIZER_TRACE && recognizerTrace.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(
                        "recognizer trace (debug)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    recognizerTrace.takeLast(14).forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Depend on the SESSION flag only, not isListening. isListening
            // flips on every internal recognizer restart during a pause, which
            // made the button strobe between Stop and Mic and appear to resize.
            // isStickyListening stays true for the whole manual-stop session.
            val isRecording = isStickyListening

            FloatingActionButton(
                onClick = onTapMic,
                modifier = Modifier.size(64.dp),
                containerColor = if (isRecording) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = if (isRecording) {
                    MaterialTheme.colorScheme.onError
                } else {
                    MaterialTheme.colorScheme.onPrimary
                }
            ) {
                androidx.compose.material3.Icon(
                    imageVector = if (isRecording) Icons.Filled.Stop else Icons.Outlined.Mic,
                    contentDescription = if (isRecording) "Stop recording" else "Start recording"
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                when {
                    isCoachThinking -> "Coach is preparing an answer..."
                    isCoachSpeaking -> "Coach is speaking..."
                    isStickyListening -> "Tap to stop and send"
                    else -> "Tap to speak"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChatBubble(
    message: ConversationMessage,
    coachName: String
) {
    val isCoach = message.role == ConversationRole.COACH

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isCoach) Arrangement.Start else Arrangement.End
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isCoach) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                }
            ),
            modifier = Modifier.fillMaxWidth(0.84f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    if (isCoach) coachName else "You",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyLarge
                )

                // Seen, not spoken. playCoachReply() only ever receives
                // message.text, so the correction never interrupts the audio.
                message.correction?.takeIf { it.isNotBlank() }?.let { correction ->
                    Spacer(Modifier.height(10.dp))

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "Tip",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                correction,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val TRANSCRIPT_TAG = "ConvTranscript"

// Debug panel OFF. All the transcript/voice bugs it helped diagnose are fixed.
// Flip to true only if you need to inspect the raw recognizer flow again; the
// panel is capped in height and scrolls, so it no longer covers the chat.
private const val SHOW_RECOGNIZER_TRACE = false
