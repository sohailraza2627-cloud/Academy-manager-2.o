package com.example

import android.app.Application
import com.example.data.local.AcademyDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.repository.AcademyRepository

class AcademyManagerApp : Application() {
    val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepository(this)
    }

    val repository: AcademyRepository
        get() = AcademyRepository(AcademyDatabase.getInstance(this))
}
