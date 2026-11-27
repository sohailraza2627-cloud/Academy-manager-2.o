package com.example.ui.viewmodel

import android.app.Application
import androidx.credentials.CredentialManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AcademyManagerApp
import com.example.data.local.AcademyMember
import com.example.data.local.AcademyProfile
import com.example.data.local.AuthSession
import com.example.data.repository.GoogleSignInOutcome
import com.example.data.sync.SyncStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface PinGateState {
    data object Loading : PinGateState
    data object NeedsAuth : PinGateState
    data object NeedsPinSetup : PinGateState
    data object Locked : PinGateState
    data object Unlocked : PinGateState
}

class AppLockViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AcademyManagerApp
    private val prefs = app.preferencesRepository
    private val authRepo = app.authRepository
    private val syncManager = app.cloudSyncManager
    private val repo get() = app.repository

    private val _pinUnlocked = MutableStateFlow(false)
    private val _initialized = MutableStateFlow(false)
    private val _pendingGoogleUser = MutableStateFlow<GoogleSignInOutcome.NeedsAcademySetup?>(null)
    val pendingGoogleUser: StateFlow<GoogleSignInOutcome.NeedsAcademySetup?> = _pendingGoogleUser.asStateFlow()

    val authSession: StateFlow<AuthSession> = prefs.authSessionFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AuthSession()
    )

    val syncStatus: StateFlow<SyncStatus> = syncManager.syncStatus

    val hasPinSet: StateFlow<Boolean> = prefs.hasPinSet.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    val aiAssistantEnabled: StateFlow<Boolean> = prefs.aiAssistantEnabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    val userGeminiApiKey: StateFlow<String> = prefs.userGeminiApiKeyFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ""
    )

    val devicePrefix: StateFlow<String> = prefs.devicePrefixFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "A"
    )

    val members: StateFlow<List<AcademyMember>> = repo.membersFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val gateState: StateFlow<PinGateState> = combine(
        _initialized,
        prefs.authSessionFlow,
        prefs.hasPinSet,
        _pinUnlocked
    ) { initialized, session, pinConfigured, unlocked ->
        when {
            !initialized -> PinGateState.Loading
            !session.isAuthenticated -> PinGateState.NeedsAuth
            pinConfigured && !unlocked -> PinGateState.Locked
            else -> PinGateState.Unlocked
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PinGateState.Loading)

    val isDarkMode: StateFlow<Boolean> = prefs.isDarkMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val academyProfile: StateFlow<AcademyProfile> = repo.profileFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AcademyProfile()
    )

    val isFirebaseConfigured: Boolean
        get() = authRepo.isFirebaseConfigured()

    init {
        viewModelScope.launch {
            prefs.ensureDevicePrefix()
            val session = prefs.getAuthSessionOnce()
            if (session.isAuthenticated && !session.isOfflineLocalMode && session.academyId.isNotBlank()) {
                syncManager.startListeners(session.academyId)
                syncManager.requestPush()
            }
            if (!prefs.hasPinSet.first()) {
                _pinUnlocked.value = true
            }
            _initialized.value = true
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String): Result<GoogleSignInOutcome> {
        val res = authRepo.signInWithGoogleIdToken(idToken)
        res.onSuccess { outcome ->
            when (outcome) {
                is GoogleSignInOutcome.SignedIn -> {
                    _pendingGoogleUser.value = null
                    if (!prefs.hasPinSet.first()) {
                        _pinUnlocked.value = true
                    }
                }
                is GoogleSignInOutcome.NeedsAcademySetup -> {
                    _pendingGoogleUser.value = outcome
                }
            }
        }
        return res
    }

    suspend fun completeAdminAcademyCreation(
        academyName: String,
        adminName: String
    ): Result<AuthSession> {
        val res = authRepo.completeAdminAcademyCreation(academyName, adminName)
        if (res.isSuccess) {
            _pendingGoogleUser.value = null
            if (!prefs.hasPinSet.first()) {
                _pinUnlocked.value = true
            }
        }
        return res
    }

    fun cancelPendingGoogleSetup(credentialManager: CredentialManager) {
        viewModelScope.launch {
            _pendingGoogleUser.value = null
            authRepo.signOut(credentialManager)
        }
    }

    suspend fun continueOfflineLocalMode(academyName: String): Result<AuthSession> {
        val res = authRepo.continueOfflineLocalMode(academyName)
        if (res.isSuccess) {
            _pendingGoogleUser.value = null
            _pinUnlocked.value = true
        }
        return res
    }

    suspend fun inviteTeacherByEmail(
        name: String,
        email: String
    ): Result<AcademyMember> = authRepo.inviteTeacherByEmail(name, email)

    fun signOut(credentialManager: CredentialManager? = null) {
        viewModelScope.launch {
            _pendingGoogleUser.value = null
            authRepo.signOut(credentialManager)
            _pinUnlocked.value = false
        }
    }

    fun triggerManualSync() {
        syncManager.requestPush()
    }

    fun setAiAssistantEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setAiAssistantEnabled(enabled)
        }
    }

    fun saveUserGeminiApiKey(apiKey: String, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            prefs.setUserGeminiApiKey(apiKey)
            onSaved()
        }
    }

    suspend fun createInitialPin(pin: String) {
        prefs.saveNewPin(pin)
        _pinUnlocked.value = true
    }

    suspend fun verifyAndUnlock(pin: String): Boolean {
        val ok = prefs.verifyPin(pin)
        if (ok) {
            _pinUnlocked.value = true
        }
        return ok
    }

    suspend fun setOrChangePin(currentPin: String, newPin: String): Boolean {
        val pinConfigured = prefs.hasPinSet.first()
        if (pinConfigured) {
            val ok = prefs.verifyPin(currentPin)
            if (!ok) return false
        }
        prefs.saveNewPin(newPin)
        return true
    }

    fun setDevicePrefix(prefix: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            prefs.setDevicePrefix(prefix)
            onDone()
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setDarkMode(enabled)
        }
    }

    fun saveProfile(
        academyName: String,
        tagline: String,
        phone: String,
        address: String,
        logoPath: String?,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            repo.saveProfile(academyName, tagline, phone, address, logoPath)
            onSaved()
        }
    }
}
