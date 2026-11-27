package com.example.ui.screens.auth

import android.app.Activity
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.example.data.repository.GoogleSignInOutcome
import com.example.ui.components.GoogleCard
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import kotlinx.coroutines.launch

private const val AUTH_TAG = "Auth"

@Composable
fun AuthScreen(
    isFirebaseConfigured: Boolean,
    pendingGoogleUser: GoogleSignInOutcome.NeedsAcademySetup?,
    onGoogleIdToken: suspend (String) -> Result<GoogleSignInOutcome>,
    onCreateAcademyWithGoogle: suspend (academyName: String, adminName: String) -> Result<*>,
    onCancelGoogleSetup: (CredentialManager) -> Unit,
    onContinueOfflineLocal: suspend (academyName: String) -> Result<*>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var academyName by rememberSaveable { mutableStateOf("Alhamd Science Evening Academy") }
    var adminName by rememberSaveable(pendingGoogleUser?.uid) {
        mutableStateOf(pendingGoogleUser?.displayName ?: "")
    }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    val requiredMsg = stringResource(R.string.field_required)

    // Silent auto sign-in check on startup for returning Google users
    LaunchedEffect(isFirebaseConfigured, pendingGoogleUser) {
        if (!isFirebaseConfigured || pendingGoogleUser != null) return@LaunchedEffect
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return@LaunchedEffect
        }
        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val response = credentialManager.getCredential(context, request)
            val cred = response.credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                isBusy = true
                onGoogleIdToken(token).onFailure { err ->
                    errorMessage = err.localizedMessage
                }
                isBusy = false
            }
        } catch (_: GetCredentialException) {
            // Silent check found no authorized credential; interactive Google Sign-In button stays visible.
        } catch (_: Exception) {
            // Ignore silent auto sign-in failures.
        }
    }

    fun launchInteractiveGoogleSignIn() {
        val activity = context as? Activity
        if (activity == null) {
            errorMessage = "Unable to open Google Sign-In dialog from this context."
            return
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            errorMessage = "Google Sign-In configuration missing."
            return
        }
        isBusy = true
        errorMessage = null
        scope.launch {
            try {
                val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()
                val cred = credentialManager.getCredential(activity, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    val res = onGoogleIdToken(token)
                    res.onFailure { err ->
                        errorMessage = err.localizedMessage ?: "Google Sign-In failed"
                    }
                } else {
                    errorMessage = "Unexpected credential type returned"
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(AUTH_TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            } catch (e: Exception) {
                Log.e(AUTH_TAG, "Google Sign-In failed", e)
                errorMessage = e.localizedMessage ?: "Google Sign-In failed"
            } finally {
                isBusy = false
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.widthIn(max = 440.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.auth_welcome_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (pendingGoogleUser == null) {
                            stringResource(R.string.auth_google_subtitle)
                        } else {
                            stringResource(R.string.auth_finish_academy_subtitle)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(22.dp))

                    GoogleCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (pendingGoogleUser != null) {
                                Text(
                                    text = stringResource(
                                        R.string.auth_signed_in_as,
                                        pendingGoogleUser.email.ifBlank { pendingGoogleUser.displayName }
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                                OutlinedTextField(
                                    value = academyName,
                                    onValueChange = { academyName = it; errorMessage = null },
                                    label = { Text(stringResource(R.string.academy_name_label)) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_academy_name_input")
                                )
                                OutlinedTextField(
                                    value = adminName,
                                    onValueChange = { adminName = it; errorMessage = null },
                                    label = { Text(stringResource(R.string.auth_admin_name_label)) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_admin_name_input")
                                )

                                AnimatedVisibility(visible = errorMessage != null) {
                                    Text(
                                        text = errorMessage.orEmpty(),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (academyName.isBlank() || adminName.isBlank()) {
                                            errorMessage = requiredMsg
                                            return@Button
                                        }
                                        isBusy = true
                                        errorMessage = null
                                        scope.launch {
                                            val res = onCreateAcademyWithGoogle(academyName, adminName)
                                            isBusy = false
                                            res.onFailure { err ->
                                                errorMessage = err.localizedMessage ?: "Failed to create academy"
                                            }
                                        }
                                    },
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(28.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 50.dp)
                                        .testTag("auth_create_academy_confirm_btn")
                                ) {
                                    if (isBusy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(stringResource(R.string.auth_create_academy_btn))
                                }

                                TextButton(
                                    onClick = {
                                        errorMessage = null
                                        onCancelGoogleSetup(credentialManager)
                                    },
                                    enabled = !isBusy,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.auth_switch_google_account))
                                }
                            } else {
                                if (!isFirebaseConfigured) {
                                    Text(
                                        text = stringResource(R.string.auth_firebase_not_configured),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                AnimatedVisibility(visible = errorMessage != null) {
                                    Text(
                                        text = errorMessage.orEmpty(),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Button(
                                    onClick = { launchInteractiveGoogleSignIn() },
                                    enabled = !isBusy && isFirebaseConfigured,
                                    shape = RoundedCornerShape(28.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .testTag("auth_google_signin_btn")
                                ) {
                                    if (isBusy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.AccountCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(stringResource(R.string.auth_sign_in_google_btn))
                                }

                                Text(
                                    text = stringResource(R.string.auth_google_teacher_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(
                                    text = stringResource(R.string.auth_quota_notice),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                HorizontalDivider()

                                OutlinedButton(
                                    onClick = {
                                        isBusy = true
                                        scope.launch {
                                            onContinueOfflineLocal(academyName)
                                            isBusy = false
                                        }
                                    },
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(28.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("auth_offline_local_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.auth_continue_offline))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
