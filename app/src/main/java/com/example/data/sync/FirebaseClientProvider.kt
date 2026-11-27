package com.example.data.sync

import android.content.Context
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseClientProvider {
    fun isConfigured(context: Context): Boolean {
        return try {
            val dbId = context.getString(R.string.firestore_database_id)
            if (dbId.isBlank()) return false
            if (FirebaseApp.getApps(context).isNotEmpty()) return true
            FirebaseApp.initializeApp(context) != null
        } catch (_: Exception) {
            false
        }
    }

    fun getAuthOrNull(context: Context): FirebaseAuth? {
        if (!isConfigured(context)) return null
        return try {
            FirebaseAuth.getInstance()
        } catch (_: Exception) {
            null
        }
    }

    fun getFirestoreOrNull(context: Context): FirebaseFirestore? {
        if (!isConfigured(context)) return null
        return try {
            val dbId = context.getString(R.string.firestore_database_id)
            if (dbId.isBlank()) null else FirebaseFirestore.getInstance(dbId)
        } catch (_: Exception) {
            null
        }
    }
}
