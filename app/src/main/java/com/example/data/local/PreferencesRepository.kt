package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.util.PinHasher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "academy_prefs")

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
    }

    val hasPinSet: Flow<Boolean> = context.dataStore.data.map { prefs ->
        !prefs[Keys.PIN_HASH].isNullOrBlank()
    }

    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.DARK_MODE] ?: false
    }

    suspend fun isPinConfigured(): Boolean {
        return !context.dataStore.data.first()[Keys.PIN_HASH].isNullOrBlank()
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
}
