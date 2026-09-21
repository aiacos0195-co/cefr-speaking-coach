package com.cefrspeakingcoach.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

enum class DrawerScreen {
    HOME,
    SESSION,
    AI_CONVERSATION,
    HISTORY,
    PROGRESS,
    PRACTICE_PLAN,
    SAVED_PROMPTS,
    COACH_VOICE,
    AUDITION,
    SETTINGS,
    ABOUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawerShell(
    selectedScreen: DrawerScreen,
    onScreenChange: (DrawerScreen) -> Unit,
    currentUser: SignedInUser?,
    isSigningIn: Boolean,
    authError: String?,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit,
    homeContent: @Composable () -> Unit,
    sessionContent: @Composable () -> Unit,
    aiConversationContent: @Composable () -> Unit,
    historyContent: @Composable () -> Unit,
    progressContent: @Composable () -> Unit,
    practicePlanContent: @Composable () -> Unit,
    savedPromptsContent: @Composable () -> Unit,
    coachVoiceContent: @Composable () -> Unit,
    auditionContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit,
    aboutContent: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    fun navigate(screen: DrawerScreen) {
        onScreenChange(screen)
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(20.dp))

                Text(
                    text = "CEFR Speaking Coach",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (currentUser == null) {
                        Text(
                            "Account",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Button(
                            onClick = onGoogleSignIn,
                            enabled = !isSigningIn,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Person, contentDescription = null)
                            Spacer(Modifier.weight(0.05f))
                            Text(if (isSigningIn) "Signing in..." else "Sign in with Google")
                        }

                        authError?.let { errorMessage ->
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        Text(
                            currentUser.displayName ?: "User",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            currentUser.email ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                            Spacer(Modifier.weight(0.05f))
                            Text("Sign out")
                        }
                    }
                }

                HorizontalDivider()

                DrawerItem(
                    icon = Icons.Outlined.Home,
                    label = "Home",
                    selected = selectedScreen == DrawerScreen.HOME
                ) { navigate(DrawerScreen.HOME) }

                DrawerItem(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    label = "AI Conversation",
                    selected = selectedScreen == DrawerScreen.AI_CONVERSATION
                ) { navigate(DrawerScreen.AI_CONVERSATION) }

                DrawerItem(
                    icon = Icons.Outlined.History,
                    label = "History",
                    selected = selectedScreen == DrawerScreen.HISTORY
                ) { navigate(DrawerScreen.HISTORY) }

                DrawerItem(
                    icon = Icons.Outlined.Speed,
                    label = "Progress",
                    selected = selectedScreen == DrawerScreen.PROGRESS
                ) { navigate(DrawerScreen.PROGRESS) }

                DrawerItem(
                    icon = Icons.Outlined.EventNote,
                    label = "Practice Plan",
                    selected = selectedScreen == DrawerScreen.PRACTICE_PLAN
                ) { navigate(DrawerScreen.PRACTICE_PLAN) }

                DrawerItem(
                    icon = Icons.Outlined.AutoAwesome,
                    label = "Saved Prompts",
                    selected = selectedScreen == DrawerScreen.SAVED_PROMPTS
                ) { navigate(DrawerScreen.SAVED_PROMPTS) }

                DrawerItem(
                    icon = Icons.Outlined.RecordVoiceOver,
                    label = "Coach voice",
                    selected = selectedScreen == DrawerScreen.COACH_VOICE
                ) { navigate(DrawerScreen.COACH_VOICE) }

                DrawerItem(
                    icon = Icons.Outlined.Speed,
                    label = "Voice audition (debug)",
                    selected = selectedScreen == DrawerScreen.AUDITION
                ) { navigate(DrawerScreen.AUDITION) }

                DrawerItem(
                    icon = Icons.Outlined.Settings,
                    label = "Settings",
                    selected = selectedScreen == DrawerScreen.SETTINGS
                ) { navigate(DrawerScreen.SETTINGS) }

                DrawerItem(
                    icon = Icons.Outlined.Info,
                    label = "About",
                    selected = selectedScreen == DrawerScreen.ABOUT
                ) { navigate(DrawerScreen.ABOUT) }

                Spacer(Modifier.height(20.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (selectedScreen) {
                                DrawerScreen.HOME -> "CEFR Speaking Coach"
                                DrawerScreen.SESSION -> "Practice Session"
                                DrawerScreen.AI_CONVERSATION -> "AI Conversation"
                                DrawerScreen.HISTORY -> "History"
                                DrawerScreen.PROGRESS -> "Progress"
                                DrawerScreen.PRACTICE_PLAN -> "Practice Plan"
                                DrawerScreen.SAVED_PROMPTS -> "Saved Prompts"
                                DrawerScreen.COACH_VOICE -> "Coach Voice"
                                DrawerScreen.AUDITION -> "Voice Audition"
                                DrawerScreen.SETTINGS -> "Settings"

                                DrawerScreen.ABOUT -> "About"
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Menu"
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (selectedScreen) {
                    DrawerScreen.HOME -> homeContent()
                    DrawerScreen.SESSION -> sessionContent()
                    DrawerScreen.AI_CONVERSATION -> aiConversationContent()
                    DrawerScreen.HISTORY -> historyContent()
                    DrawerScreen.PROGRESS -> progressContent()
                    DrawerScreen.PRACTICE_PLAN -> practicePlanContent()
                    DrawerScreen.SAVED_PROMPTS -> savedPromptsContent()
                    DrawerScreen.COACH_VOICE -> coachVoiceContent()
                    DrawerScreen.AUDITION -> auditionContent()
                    DrawerScreen.SETTINGS -> settingsContent()

                    DrawerScreen.ABOUT -> aboutContent()
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}
