package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.util.FormatUtils
import com.example.util.PinHasher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "academy_prefs")

data class AuthSession(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val academyId: String = "",
    val role: String = UserRole.ADMIN,
    val isOfflineLocalMode: Boolean = false
) {
    val isAuthenticated: Boolean
        get() = (uid.isNotBlank() && academyId.isNotBlank()) || isOfflineLocalMode

    val isAdmin: Boolean
        get() = role == UserRole.ADMIN
}

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val DEVICE_PREFIX = stringPreferencesKey("device_prefix")
        val NEXT_TUITION_SEQ = intPreferencesKey("next_tuition_seq")
        val NEXT_PAPER_SEQ = intPreferencesKey("next_paper_seq")

        val AUTH_UID = stringPreferencesKey("auth_uid")
        val AUTH_EMAIL = stringPreferencesKey("auth_email")
        val AUTH_NAME = stringPreferencesKey("auth_name")
        val ACADEMY_ID = stringPreferencesKey("academy_id")
        val USER_ROLE = stringPreferencesKey("user_role")
        val OFFLINE_LOCAL_MODE = booleanPreferencesKey("offline_local_mode")
        val AI_ASSISTANT_ENABLED = booleanPreferencesKey("ai_assistant_enabled")
        val AI_USER_API_KEY = stringPreferencesKey("ai_user_api_key")
    }

    val hasPinSet: Flow<Boolean> = context.dataStore.data.map { prefs ->
        !prefs[Keys.PIN_HASH].isNullOrBlank()
    }

    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.DARK_MODE] ?: false
    }

    val aiAssistantEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AI_ASSISTANT_ENABLED] ?: false
    }

    val userGeminiApiKeyFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.AI_USER_API_KEY] ?: ""
    }

    val devicePrefixFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEVICE_PREFIX] ?: "A"
    }

    val authSessionFlow: Flow<AuthSession> = context.dataStore.data.map { prefs ->
        AuthSession(
            uid = prefs[Keys.AUTH_UID] ?: "",
            email = prefs[Keys.AUTH_EMAIL] ?: "",
            name = prefs[Keys.AUTH_NAME] ?: "",
            academyId = prefs[Keys.ACADEMY_ID] ?: "",
            role = prefs[Keys.USER_ROLE] ?: UserRole.ADMIN,
            isOfflineLocalMode = prefs[Keys.OFFLINE_LOCAL_MODE] ?: false
        )
    }

    suspend fun getAuthSessionOnce(): AuthSession = authSessionFlow.first()

    suspend fun saveAuthSession(
        uid: String,
        email: String,
        name: String,
        academyId: String,
        role: String,
        isOfflineLocalMode: Boolean = false
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTH_UID] = uid
            prefs[Keys.AUTH_EMAIL] = email
            prefs[Keys.AUTH_NAME] = name
            prefs[Keys.ACADEMY_ID] = academyId
            prefs[Keys.USER_ROLE] = role
            prefs[Keys.OFFLINE_LOCAL_MODE] = isOfflineLocalMode
        }
    }

    suspend fun clearAuthSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.AUTH_UID)
            prefs.remove(Keys.AUTH_EMAIL)
            prefs.remove(Keys.AUTH_NAME)
            prefs.remove(Keys.ACADEMY_ID)
            prefs.remove(Keys.USER_ROLE)
            prefs.remove(Keys.OFFLINE_LOCAL_MODE)
        }
    }

    suspend fun ensureDevicePrefix(): String {
        val existing = context.dataStore.data.first()[Keys.DEVICE_PREFIX]
        if (!existing.isNullOrBlank()) return existing
        val generated = "${('A'..'Z').random()}${('1'..'9').random()}"
        context.dataStore.edit { prefs ->
            if (prefs[Keys.DEVICE_PREFIX].isNullOrBlank()) {
                prefs[Keys.DEVICE_PREFIX] = generated
            }
        }
        return context.dataStore.data.first()[Keys.DEVICE_PREFIX] ?: generated
    }

    suspend fun setDevicePrefix(prefix: String) {
        val normalized = FormatUtils.normalizeDevicePrefix(prefix)
        context.dataStore.edit { prefs ->
            prefs[Keys.DEVICE_PREFIX] = normalized
        }
    }

    suspend fun allocateNextReceiptNumber(isPaperFee: Boolean): String {
        val devPrefix = ensureDevicePrefix()
        var seq = 1
        context.dataStore.edit { prefs ->
            val key = if (isPaperFee) Keys.NEXT_PAPER_SEQ else Keys.NEXT_TUITION_SEQ
            seq = prefs[key] ?: 1
            prefs[key] = seq + 1
        }
        val ledgerPrefix = if (isPaperFee) "PF" else "R"
        return FormatUtils.formatReceiptNumber(ledgerPrefix, devPrefix, seq)
    }

    suspend fun saveNewPin(pin: String) {
        val hash = PinHasher.hashPin(pin)
        context.dataStore.edit { prefs ->
            prefs[Keys.PIN_HASH] = hash
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val stored = context.dataStore.data.first()[Keys.PIN_HASH] ?: return false
        return PinHasher.verifyPin(pin, stored)
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DARK_MODE] = enabled
        }
    }

    suspend fun setAiAssistantEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AI_ASSISTANT_ENABLED] = enabled
        }
    }

    suspend fun setUserGeminiApiKey(apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AI_USER_API_KEY] = apiKey.trim()
        }
    }
}
