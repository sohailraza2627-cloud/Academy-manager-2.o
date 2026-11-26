package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AcademyManagerApp
import com.example.data.local.AcademyProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PinGateState {
    data object Loading : PinGateState
    data object NeedsPinSetup : PinGateState
    data object Locked : PinGateState
    data object Unlocked : PinGateState
}

class AppLockViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AcademyManagerApp
    private val prefs = app.preferencesRepository
    private val repo get() = app.repository

    private val _gateState = MutableStateFlow<PinGateState>(PinGateState.Loading)
    val gateState: StateFlow<PinGateState> = _gateState.asStateFlow()

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

    init {
        viewModelScope.launch {
            val hasPin = prefs.hasPinSet.first()
            _gateState.value = if (hasPin) PinGateState.Locked else PinGateState.NeedsPinSetup
            repo.getOrCreateProfile()
        }
    }

    suspend fun createInitialPin(pin: String) {
        prefs.saveNewPin(pin)
        _gateState.value = PinGateState.Unlocked
    }

    suspend fun verifyAndUnlock(pin: String): Boolean {
        val ok = prefs.verifyPin(pin)
        if (ok) {
            _gateState.value = PinGateState.Unlocked
        }
        return ok
    }

    suspend fun changePin(currentPin: String, newPin: String): Boolean {
        val ok = prefs.verifyPin(currentPin)
        if (!ok) return false
        prefs.saveNewPin(newPin)
        return true
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
