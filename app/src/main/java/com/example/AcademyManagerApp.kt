package com.example

import android.app.Application
import com.example.data.ai.AcademyAiCoordinator
import com.example.data.local.AcademyDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.repository.AcademyRepository
import com.example.data.repository.AuthRepository
import com.example.data.sync.CloudSyncManager
import com.example.data.sync.NetworkMonitor
import kotlinx.coroutines.flow.first

class AcademyManagerApp : Application() {
    val database: AcademyDatabase by lazy {
        AcademyDatabase.getInstance(this)
    }

    val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepository(this)
    }

    val networkMonitor: NetworkMonitor by lazy {
        NetworkMonitor(this)
    }

    val cloudSyncManager: CloudSyncManager by lazy {
        CloudSyncManager(
            context = this,
            db = database,
            prefs = preferencesRepository,
            networkMonitor = networkMonitor
        )
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(
            context = this,
            db = database,
            prefs = preferencesRepository,
            syncManager = cloudSyncManager
        )
    }

    val repository: AcademyRepository by lazy {
        AcademyRepository(
            db = database,
            prefs = preferencesRepository,
            onLocalMutation = { cloudSyncManager.requestPush() }
        )
    }

    val aiCoordinator: AcademyAiCoordinator by lazy {
        AcademyAiCoordinator(
            networkMonitor = networkMonitor,
            userApiKeyProvider = { preferencesRepository.userGeminiApiKeyFlow.first() }
        )
    }
}
