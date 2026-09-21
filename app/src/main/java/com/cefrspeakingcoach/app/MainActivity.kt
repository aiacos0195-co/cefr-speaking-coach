package com.cefrspeakingcoach.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.cefrspeakingcoach.app.ui.theme.CEFRSpeakingCoachTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("cefr_sessions", Context.MODE_PRIVATE)

    fun saveSession(session: PracticeSession) {
        val sessions = getSessions().toMutableList()
        if (sessions.any { it.id == session.id }) return

        sessions.add(0, session)

        val arr = JSONArray()
        sessions.take(300).forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("createdAt", s.createdAt)
                    put("level", s.level)
                    put("category", s.category)
                    put("prompt", s.prompt)
                    put("transcript", s.transcript)
                    put("spokenSeconds", s.spokenSeconds)
                    put("wordCount", s.wordCount)
                    put("wpm", s.wpm)
                    put("fillerCount", s.fillerCount)
                    put("ai", s.ai?.toJson())
                }
            )
        }

        prefs.edit().putString("sessions_json", arr.toString()).apply()
    }

    fun replaceAll(sessions: List<PracticeSession>) {
        val arr = JSONArray()
        sessions.take(300).forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("createdAt", s.createdAt)
                    put("level", s.level)
                    put("category", s.category)
                    put("prompt", s.prompt)
                    put("transcript", s.transcript)
                    put("spokenSeconds", s.spokenSeconds)
                    put("wordCount", s.wordCount)
                    put("wpm", s.wpm)
                    put("fillerCount", s.fillerCount)
                    put("ai", s.ai?.toJson())
                }
            )
        }
        prefs.edit().putString("sessions_json", arr.toString()).apply()
    }

    fun getSessions(): List<PracticeSession> {
        val raw = prefs.getString("sessions_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        PracticeSession(
                            id = o.getString("id"),
                            createdAt = o.getLong("createdAt"),
                            level = o.getString("level"),
                            category = o.getString("category"),
                            prompt = o.getString("prompt"),
                            transcript = o.getString("transcript"),
                            spokenSeconds = o.getInt("spokenSeconds"),
                            wordCount = o.getInt("wordCount"),
                            wpm = o.getInt("wpm"),
                            fillerCount = o.optInt("fillerCount", 0),
                            ai = o.optJSONObject("ai")?.toAiFeedback()
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

private fun AiFeedback.toJson(): JSONObject {
    return JSONObject().apply {
        put("overall", overall)
        put("cefr_level_estimate", cefr_level_estimate)
        put(
            "scores",
            JSONObject().apply {
                put("fluency", scores.fluency)
                put("pronunciation", scores.pronunciation)
                put("grammar", scores.grammar)
                put("vocabulary", scores.vocabulary)
                put("coherence", scores.coherence)
            }
        )
        put("strengths", JSONArray(strengths))
        put("improvements", JSONArray(improvements))
        put("corrected_version", corrected_version)
    }
}

private fun JSONObject.toAiFeedback(): AiFeedback {
    val scoresObj = getJSONObject("scores")
    return AiFeedback(
        overall = getInt("overall"),
        cefr_level_estimate = getString("cefr_level_estimate"),
        scores = AiScores(
            fluency = scoresObj.getInt("fluency"),
            pronunciation = scoresObj.getInt("pronunciation"),
            grammar = scoresObj.getInt("grammar"),
            vocabulary = scoresObj.getInt("vocabulary"),
            coherence = scoresObj.getInt("coherence")
        ),
        strengths = optJSONArray("strengths")?.let { arr ->
            List(arr.length()) { i -> arr.getString(i) }
        } ?: emptyList(),
        improvements = optJSONArray("improvements")?.let { arr ->
            List(arr.length()) { i -> arr.getString(i) }
        } ?: emptyList(),
        corrected_version = optString("corrected_version", "")
    )
}

class MainActivity : ComponentActivity() {
    private lateinit var settingsStore: AppSettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = AppSettingsStore(applicationContext)

        setContent {
            val settings by settingsStore.settings.collectAsState()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            CEFRSpeakingCoachTheme(darkTheme = darkTheme) {
                MaterialTheme(
                    colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
                ) {
                    MainScreen(settingsStore = settingsStore)
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    settingsStore: AppSettingsStore
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authManager = remember { AuthManager(context) }
    val firestoreRepository = remember { FirestoreSessionRepository() }
    val sessionStore = remember { SessionStore(context) }
    val reminderManager = remember { DailyReminderManager(context) }
    val promptStore = remember { PromptStore(context) }
    val languageStore = remember { UiLanguageStore(context) }

    val settings by settingsStore.settings.collectAsState()

    var currentScreen by remember { mutableStateOf(DrawerScreen.HOME) }

    // El idioma de las pantallas de ajustes vive aqui y no dentro de cada una,
    // porque la barra superior del drawer tambien tiene que seguirlo. Con el
    // estado metido en la pantalla, la barra se quedaba en ingles encima de un
    // titulo en espanol.
    var uiLanguage by remember { mutableStateOf(languageStore.language) }
    val onUiLanguageChange: (UiLanguage) -> Unit = { next ->
        uiLanguage = next
        languageStore.language = next
    }

    var selectedLevel by remember { mutableStateOf("A1") }
    var selectedHistorySession by remember { mutableStateOf<PracticeSession?>(null) }
    var savedPromptsLevelFilter by remember { mutableStateOf("All") }

    var signedInUser by remember { mutableStateOf(authManager.currentUser()) }
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    var selectedCategory by remember { mutableStateOf(promptStore.getSelectedCategory(selectedLevel)) }
    var currentPrompt by remember { mutableStateOf<PromptItem?>(null) }
    var savedPromptsVersion by remember { mutableIntStateOf(0) }

    var isRecording by remember { mutableStateOf(false) }
    var isEvaluating by remember { mutableStateOf(false) }
    var isRefreshingPrompt by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }
    var liveTranscript by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<AiFeedback?>(null) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("Ready") }

    var spokenSeconds by remember { mutableIntStateOf(0) }
    var totalSeconds by remember { mutableIntStateOf(60) }
    var timeLeft by remember { mutableIntStateOf(60) }

    var recordingStartMs by remember { mutableLongStateOf(0L) }
    var sessions by remember { mutableStateOf(sessionStore.getSessions()) }

    var activeRecordToken by remember { mutableIntStateOf(0) }
    var evaluationJob by remember { mutableStateOf<Job?>(null) }

    val savedPrompts = remember(savedPromptsVersion) {
        promptStore.getFavoritePrompts()
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
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }
    }

    fun durationForLevel(level: String): Int {
        return when (level) {
            "A1" -> 60
            "A2" -> 75
            "B1" -> 90
            "B2" -> 105
            "C1" -> 120
            "C2" -> 135
            else -> 60
        }
    }

    fun nextLocalPrompt(level: String, category: String): PromptItem {
        val prompt = PromptRepository.getRandomPrompt(
            level = level,
            category = category.takeUnless { it == "All" },
            excludedIds = promptStore.getRecentPromptIds(level).toSet()
        )
        promptStore.pushRecentPrompt(level, prompt.id)
        return prompt
    }

    fun clearSessionDataPreservePrompt() {
        try {
            speechRecognizer.cancel()
        } catch (_: Exception) {
        }

        evaluationJob?.cancel()
        evaluationJob = null

        isRecording = false
        isEvaluating = false
        isRefreshingPrompt = false
        transcript = ""
        liveTranscript = ""
        spokenSeconds = 0
        timeLeft = totalSeconds
        feedback = null
        aiError = null
        statusText = "Ready"
    }

    fun stopRecordingNow() {
        if (!isRecording) return
        isRecording = false
        spokenSeconds = ((SystemClock.elapsedRealtime() - recordingStartMs) / 1000L).toInt().coerceAtLeast(0)
        timeLeft = (totalSeconds - spokenSeconds).coerceAtLeast(0)
        statusText = "Stopped"

        try {
            speechRecognizer.stopListening()
        } catch (_: Exception) {
            try {
                speechRecognizer.cancel()
            } catch (_: Exception) {
            }
        }
    }

    val recognitionListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                statusText = "Listening..."
            }

            override fun onBeginningOfSpeech() {
                statusText = "Speak now"
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                if (isRecording) {
                    statusText = "Processing speech..."
                }
            }

            override fun onError(error: Int) {
                if (!isRecording) return

                isRecording = false
                spokenSeconds = ((SystemClock.elapsedRealtime() - recordingStartMs) / 1000L).toInt().coerceAtLeast(0)
                timeLeft = (totalSeconds - spokenSeconds).coerceAtLeast(0)

                statusText = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No clear speech detected"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    SpeechRecognizer.ERROR_CLIENT -> "Stopped"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    else -> "Error: $error"
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull()?.trim().orEmpty()

                if (best.isNotBlank()) {
                    transcript = best
                    liveTranscript = best
                }

                if (isRecording) {
                    isRecording = false
                    spokenSeconds = ((SystemClock.elapsedRealtime() - recordingStartMs) / 1000L).toInt().coerceAtLeast(0)
                    timeLeft = (totalSeconds - spokenSeconds).coerceAtLeast(0)
                    statusText = "Stopped"
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()?.trim().orEmpty()
                if (partial.isNotBlank()) {
                    liveTranscript = partial
                    if (transcript.isBlank()) {
                        transcript = partial
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
    }

    DisposableEffect(speechRecognizer) {
        speechRecognizer.setRecognitionListener(recognitionListener)
        onDispose {
            try {
                speechRecognizer.cancel()
            } catch (_: Exception) {
            }
            try {
                speechRecognizer.destroy()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(isRecording, activeRecordToken) {
        if (isRecording) {
            while (isRecording && timeLeft > 0) {
                delay(200)
                val elapsedSec = ((SystemClock.elapsedRealtime() - recordingStartMs) / 1000L).toInt()
                spokenSeconds = elapsedSec.coerceAtLeast(0)
                timeLeft = (totalSeconds - elapsedSec).coerceAtLeast(0)

                if (timeLeft <= 0 && isRecording) {
                    isRecording = false
                    try {
                        speechRecognizer.stopListening()
                    } catch (_: Exception) {
                    }
                    statusText = "Time's up"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        totalSeconds = durationForLevel(selectedLevel)
        val available = PromptRepository.getCategories(selectedLevel)
        val savedCategory = promptStore.getSelectedCategory(selectedLevel)
        selectedCategory = if (savedCategory in available) savedCategory else "All"

        if (currentPrompt == null) {
            currentPrompt = nextLocalPrompt(selectedLevel, selectedCategory)
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecording(
                speechRecognizer = speechRecognizer,
                recognizerIntent = recognizerIntent,
                onStart = {
                    activeRecordToken += 1
                    transcript = ""
                    liveTranscript = ""
                    feedback = null
                    aiError = null
                    spokenSeconds = 0
                    timeLeft = totalSeconds
                    recordingStartMs = SystemClock.elapsedRealtime()
                    isRecording = true
                    statusText = "Listening..."
                }
            )
        } else {
            aiError = "Microphone permission denied."
            statusText = "Permission denied"
        }
    }

    fun toggleRecording() {
        if (isRecording) {
            stopRecordingNow()
            return
        }

        val permissionCheck =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)

        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            startRecording(
                speechRecognizer = speechRecognizer,
                recognizerIntent = recognizerIntent,
                onStart = {
                    activeRecordToken += 1
                    transcript = ""
                    liveTranscript = ""
                    feedback = null
                    aiError = null
                    spokenSeconds = 0
                    timeLeft = totalSeconds
                    recordingStartMs = SystemClock.elapsedRealtime()
                    isRecording = true
                    statusText = "Listening..."
                }
            )
        } else {
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun analyzeSpeech() {
        val finalTranscript = (transcript.ifBlank { liveTranscript }).trim()
        if (finalTranscript.isBlank() || isEvaluating || isRefreshingPrompt) {
            if (finalTranscript.isBlank()) {
                aiError = "Please record some speech first."
            }
            return
        }

        isEvaluating = true
        aiError = null
        statusText = "Evaluating..."

        evaluationJob?.cancel()
        evaluationJob = scope.launch {
            try {
                val words = finalTranscript.split("\\s+".toRegex()).filter { it.isNotBlank() }
                val wordCount = words.size
                val calculatedWpm = if (spokenSeconds > 0) (wordCount * 60 / spokenSeconds) else 0

                val result = FirebaseAiGateway.evaluateSpeaking(
                    targetLevel = currentPrompt?.level ?: selectedLevel,
                    prompt = currentPrompt?.text ?: "",
                    transcript = finalTranscript,
                    wpm = calculatedWpm,
                    fillerCount = 0,
                    spokenSeconds = spokenSeconds
                )

                feedback = result
                statusText = "Evaluation complete"
                isEvaluating = false

                val session = PracticeSession(
                    id = System.currentTimeMillis().toString(),
                    createdAt = System.currentTimeMillis(),
                    level = currentPrompt?.level ?: selectedLevel,
                    category = currentPrompt?.category ?: selectedCategory,
                    prompt = currentPrompt?.text ?: "",
                    transcript = finalTranscript,
                    spokenSeconds = spokenSeconds,
                    wordCount = wordCount,
                    wpm = calculatedWpm,
                    fillerCount = 0,
                    ai = result
                )

                sessionStore.saveSession(session)
                signedInUser?.let { user ->
                    firestoreRepository.saveSession(user.uid, session)
                }
                sessions = sessionStore.getSessions()
                evaluationJob = null
                return@launch
            } catch (e: Exception) {
                aiError = e.message ?: "Analysis failed"
                statusText = "Evaluation failed"
            } finally {
                if (isEvaluating) {
                    isEvaluating = false
                }
                evaluationJob = null
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && settings.dailyReminderEnabled) {
            reminderManager.scheduleDailyReminder(settings.reminderHour, settings.reminderMinute)
        }
    }

    LaunchedEffect(settings.dailyReminderEnabled, settings.reminderHour, settings.reminderMinute) {
        if (settings.dailyReminderEnabled) {
            if (reminderManager.hasNotificationPermission()) {
                reminderManager.scheduleDailyReminder(settings.reminderHour, settings.reminderMinute)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            reminderManager.cancelDailyReminder()
        }
    }

    LaunchedEffect(signedInUser?.uid) {
        val user = signedInUser
        if (user == null) {
            sessions = sessionStore.getSessions()
        } else {
            try {
                firestoreRepository.saveUserProfile(user)
                val cloudSessions = firestoreRepository.loadSessions(user.uid)
                if (cloudSessions.isNotEmpty()) {
                    sessions = cloudSessions
                    sessionStore.replaceAll(cloudSessions)
                } else {
                    sessions = sessionStore.getSessions()
                }
            } catch (e: Exception) {
                authError = e.message ?: "Failed to sync cloud data."
                sessions = sessionStore.getSessions()
            }
        }
    }

    AppDrawerShell(
        selectedScreen = currentScreen,
        onScreenChange = {
            currentScreen = it
            if (it != DrawerScreen.HISTORY) {
                selectedHistorySession = null
            }
        },
        uiLanguage = uiLanguage,
        currentUser = signedInUser,
        isSigningIn = isSigningIn,
        authError = authError,
        onGoogleSignIn = {
            scope.launch {
                isSigningIn = true
                authError = null
                try {
                    val user = authManager.signInWithGoogle()
                    signedInUser = user
                    firestoreRepository.saveUserProfile(user)
                } catch (e: Exception) {
                    authError = e.message ?: "Google sign-in failed."
                } finally {
                    isSigningIn = false
                }
            }
        },
        onSignOut = {
            authManager.signOut()
            signedInUser = null
            sessions = sessionStore.getSessions()
        },
        homeContent = {
            HomeScreenV2(
                selectedLevel = selectedLevel,
                onLevelChange = { level ->
                    selectedLevel = level
                    val available = PromptRepository.getCategories(level)
                    val savedCategory = promptStore.getSelectedCategory(level)
                    selectedCategory = if (savedCategory in available) savedCategory else "All"
                    totalSeconds = durationForLevel(level)
                    currentPrompt = nextLocalPrompt(level, selectedCategory)
                    clearSessionDataPreservePrompt()
                },
                onStart = {
                    if (currentPrompt == null) {
                        currentPrompt = nextLocalPrompt(selectedLevel, selectedCategory)
                    }
                    clearSessionDataPreservePrompt()
                    currentScreen = DrawerScreen.SESSION
                }
            )
        },
        sessionContent = {
            val finalTranscript = transcript.ifBlank { liveTranscript }.trim()
            val words = finalTranscript.split("\\s+".toRegex()).filter { it.isNotBlank() }
            val wordCount = wordCount(words)
            val calculatedWpm = if (spokenSeconds > 0) (wordCount * 60 / spokenSeconds) else 0
            val categories = PromptRepository.getCategories(selectedLevel)

            SessionScreen(
                state = SessionScreenState(
                    level = currentPrompt?.level ?: selectedLevel,
                    promptCategory = currentPrompt?.category ?: selectedCategory,
                    promptText = currentPrompt?.text ?: "No prompt available.",
                    totalSeconds = totalSeconds,
                    timeLeft = timeLeft,
                    isRecording = isRecording,
                    statusText = statusText,
                    liveTranscript = liveTranscript,
                    finalTranscript = finalTranscript,
                    spokenSeconds = spokenSeconds,
                    wordCount = wordCount,
                    wpm = calculatedWpm,
                    fillerCount = 0,
                    aiLoading = isEvaluating,
                    promptRefreshLoading = isRefreshingPrompt,
                    aiError = aiError,
                    aiResult = feedback,
                    canEvaluate = finalTranscript.isNotBlank() && !isRecording && !isEvaluating && !isRefreshingPrompt,
                    engineLabel = "Default",
                    speechDetected = liveTranscript.isNotBlank(),
                    availableCategories = categories,
                    selectedCategory = selectedCategory,
                    isFavoritePrompt = currentPrompt?.let { promptStore.isFavorite(it) } == true
                ),
                onToggleRecording = { toggleRecording() },
                onEvaluate = { analyzeSpeech() },
                onNewPrompt = {
                    currentPrompt = nextLocalPrompt(selectedLevel, selectedCategory)
                    clearSessionDataPreservePrompt()
                },
                onRefreshPromptAi = {
                    if (!isEvaluating && !isRefreshingPrompt) {
                        scope.launch {
                            isRefreshingPrompt = true
                            aiError = null
                            statusText = "Refreshing prompt..."
                            try {
                                val prompts = FirebaseAiGateway.generatePromptBank(selectedLevel, 1)
                                val aiPrompt = prompts.firstOrNull()?.let { prompt ->
                                    val stableId = "ai_${selectedLevel}_${prompt.text.hashCode()}"
                                    prompt.copy(
                                        id = stableId,
                                        level = selectedLevel,
                                        category = prompt.category.ifBlank { selectedCategory },
                                        source = PromptSource.AI
                                    )
                                }

                                currentPrompt = if (aiPrompt != null) {
                                    promptStore.pushRecentPrompt(selectedLevel, aiPrompt.id)
                                    aiPrompt
                                } else {
                                    nextLocalPrompt(selectedLevel, selectedCategory)
                                }
                                clearSessionDataPreservePrompt()
                            } catch (_: Exception) {
                                currentPrompt = nextLocalPrompt(selectedLevel, selectedCategory)
                                clearSessionDataPreservePrompt()
                            } finally {
                                isRefreshingPrompt = false
                                statusText = "Ready"
                            }
                        }
                    }
                },
                onBack = {
                    stopRecordingNow()
                    currentScreen = DrawerScreen.HOME
                },
                onOpenHistory = { currentScreen = DrawerScreen.HISTORY },
                onOpenProgress = { currentScreen = DrawerScreen.PROGRESS },
                onSelectCategory = { category ->
                    if (!isRecording && !isEvaluating && !isRefreshingPrompt) {
                        selectedCategory = category
                        promptStore.setSelectedCategory(selectedLevel, category)
                        currentPrompt = nextLocalPrompt(selectedLevel, category)
                        clearSessionDataPreservePrompt()
                    }
                },
                onToggleFavorite = {
                    currentPrompt?.let { prompt ->
                        promptStore.toggleFavorite(prompt)
                        savedPromptsVersion += 1
                    }
                }
            )
        },
        aiConversationContent = {
            AIConversationScreen()
        },
        historyContent = {
            if (selectedHistorySession == null) {
                HistoryScreen(
                    sessions = sessions,
                    onOpenSession = { session ->
                        selectedHistorySession = session
                    }
                )
            } else {
                HistoryDetailScreen(session = selectedHistorySession)
            }
        },
        progressContent = {
            ProgressScreen(sessions = sessions)
        },
        practicePlanContent = {
            PracticePlanScreen(
                sessions = sessions,
                selectedLevel = selectedLevel,
                onStartNextSession = {
                    if (currentPrompt == null) {
                        currentPrompt = nextLocalPrompt(selectedLevel, selectedCategory)
                    }
                    clearSessionDataPreservePrompt()
                    currentScreen = DrawerScreen.SESSION
                }
            )
        },
        savedPromptsContent = {
            SavedPromptsScreen(
                prompts = savedPrompts,
                selectedLevelFilter = savedPromptsLevelFilter,
                onLevelFilterChange = { savedPromptsLevelFilter = it },
                onUsePrompt = { prompt ->
                    selectedLevel = prompt.level
                    totalSeconds = durationForLevel(prompt.level)

                    val available = PromptRepository.getCategories(prompt.level)
                    val nextCategory = if (prompt.category in available) prompt.category else "All"
                    selectedCategory = nextCategory
                    promptStore.setSelectedCategory(prompt.level, nextCategory)

                    currentPrompt = prompt
                    clearSessionDataPreservePrompt()
                    currentScreen = DrawerScreen.SESSION
                },
                onRemovePrompt = { prompt ->
                    promptStore.removeFavorite(prompt.id)
                    savedPromptsVersion += 1
                }
            )
        },
        coachVoiceContent = {
            CoachVoiceScreen(
                language = uiLanguage,
                onLanguageChange = onUiLanguageChange
            )
        },
        auditionContent = {
            SpeakerAuditionScreen()
        },
        settingsContent = {
            SettingsScreen(
                settings = settings,
                onThemeModeChange = { settingsStore.setThemeMode(it) },
                onDailyReminderChange = { enabled ->
                    settingsStore.setDailyReminderEnabled(enabled)

                    if (enabled) {
                        if (reminderManager.hasNotificationPermission()) {
                            reminderManager.scheduleDailyReminder(
                                settings.reminderHour,
                                settings.reminderMinute
                            )
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        reminderManager.cancelDailyReminder()
                    }
                },
                onReminderTimeChange = { hour, minute ->
                    settingsStore.setReminderTime(hour, minute)

                    if (settings.dailyReminderEnabled && reminderManager.hasNotificationPermission()) {
                        reminderManager.scheduleDailyReminder(hour, minute)
                    }
                },
                onNotificationsGranted = {
                    // Vuelve de los ajustes de Android con el permiso dado.
                    // El LaunchedEffect de arriba no se entera, porque ni el
                    // interruptor ni la hora cambiaron.
                    if (settings.dailyReminderEnabled) {
                        reminderManager.scheduleDailyReminder(
                            settings.reminderHour,
                            settings.reminderMinute
                        )
                    }
                },
                language = uiLanguage,
                onLanguageChange = onUiLanguageChange
            )
        },
        aboutContent = {
            AboutScreen(
                language = uiLanguage,
                onLanguageChange = onUiLanguageChange
            )
        }
    )
}

private fun startRecording(
    speechRecognizer: SpeechRecognizer,
    recognizerIntent: Intent,
    onStart: () -> Unit
) {
    try {
        speechRecognizer.cancel()
    } catch (_: Exception) {
    }

    onStart()

    try {
        speechRecognizer.startListening(recognizerIntent)
    } catch (_: Exception) {
    }
}

private fun wordCount(words: List<String>): Int = words.size
