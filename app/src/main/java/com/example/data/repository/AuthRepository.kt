package com.example.data.repository

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.data.local.*
import com.example.data.sync.CloudSyncManager
import com.example.data.sync.FirebaseClientProvider
import com.example.data.sync.OperationType
import com.example.data.sync.SyncCollections
import com.example.data.sync.handleFirestoreError
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface GoogleSignInOutcome {
    data class SignedIn(val session: AuthSession) : GoogleSignInOutcome
    data class NeedsAcademySetup(
        val uid: String,
        val email: String,
        val displayName: String
    ) : GoogleSignInOutcome
}

class AuthRepository(
    private val context: Context,
    private val db: AcademyDatabase,
    private val prefs: PreferencesRepository,
    private val syncManager: CloudSyncManager
) {
    fun isFirebaseConfigured(): Boolean = FirebaseClientProvider.isConfigured(context)

    suspend fun signInWithGoogleIdToken(idToken: String): Result<GoogleSignInOutcome> = withContext(Dispatchers.IO) {
        try {
            val auth = FirebaseClientProvider.getAuthOrNull(context)
                ?: return@withContext Result.failure(IllegalStateException("Firebase Auth is not configured."))
            val firestore = FirebaseClientProvider.getFirestoreOrNull(context)
                ?: return@withContext Result.failure(IllegalStateException("Firestore is not configured."))

            val previousSession = prefs.getAuthSessionOnce()
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user
                ?: return@withContext Result.failure(IllegalStateException("Google Sign-In did not return a user."))

            val uid = user.uid
            val email = (user.email ?: "").trim()
            val emailLower = email.lowercase()
            val displayName = user.displayName?.trim().orEmpty().ifBlank {
                email.substringBefore("@").ifBlank { "User" }
            }

            // 1. Check existing user link
            val userPath = "${SyncCollections.USERS}/$uid"
            val userSnap = try {
                firestore.collection(SyncCollections.USERS).document(uid).get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.GET, userPath)
                return@withContext Result.failure(e)
            }

            if (userSnap.exists()) {
                val academyId = userSnap.getString("academyId").orEmpty()
                if (academyId.isNotBlank()) {
                    val role = userSnap.getString("role") ?: UserRole.TEACHER
                    val name = userSnap.getString("name") ?: displayName
                    activateSession(
                        previousAcademyId = previousSession.academyId,
                        uid = uid,
                        email = email,
                        name = name,
                        academyId = academyId,
                        role = role
                    )
                    return@withContext Result.success(
                        GoogleSignInOutcome.SignedIn(prefs.getAuthSessionOnce())
                    )
                }
            }

            // 2. Check pending teacher invitation by verified lowercase email
            if (emailLower.isNotBlank()) {
                val invitePath = "${SyncCollections.INVITES}/$emailLower"
                val inviteSnap = try {
                    firestore.collection(SyncCollections.INVITES).document(emailLower).get().await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.GET, invitePath)
                    null
                }

                if (inviteSnap != null && inviteSnap.exists()) {
                    val invitedAcademyId = inviteSnap.getString("academyId").orEmpty()
                    val teacherName = inviteSnap.getString("teacherName")?.takeIf { it.isNotBlank() } ?: displayName
                    if (invitedAcademyId.isNotBlank()) {
                        val now = System.currentTimeMillis()
                        val academyRef = firestore.collection(SyncCollections.ACADEMIES).document(invitedAcademyId)
                        val memberDoc = mapOf(
                            "uid" to uid,
                            "email" to emailLower,
                            "name" to teacherName,
                            "role" to UserRole.TEACHER,
                            "createdAt" to now
                        )
                        val userDoc = mapOf(
                            "academyId" to invitedAcademyId,
                            "role" to UserRole.TEACHER,
                            "email" to emailLower,
                            "name" to teacherName
                        )
                        try {
                            val batch = firestore.batch()
                            batch.set(academyRef.collection(SyncCollections.MEMBERS).document(uid), memberDoc)
                            batch.set(firestore.collection(SyncCollections.USERS).document(uid), userDoc)
                            batch.delete(firestore.collection(SyncCollections.INVITES).document(emailLower))
                            batch.commit().await()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            handleFirestoreError(e, OperationType.WRITE, userPath)
                            return@withContext Result.failure(e)
                        }

                        db.academyMemberDao().deleteByUid("invite:$emailLower")
                        db.academyMemberDao().upsert(
                            AcademyMember(
                                uid = uid,
                                email = emailLower,
                                name = teacherName,
                                role = UserRole.TEACHER,
                                createdAt = now
                            )
                        )
                        activateSession(
                            previousAcademyId = previousSession.academyId,
                            uid = uid,
                            email = emailLower,
                            name = teacherName,
                            academyId = invitedAcademyId,
                            role = UserRole.TEACHER
                        )
                        return@withContext Result.success(
                            GoogleSignInOutcome.SignedIn(prefs.getAuthSessionOnce())
                        )
                    }
                }
            }

            // 3. First-time user without invite -> proceed to Create Academy setup step
            Result.success(
                GoogleSignInOutcome.NeedsAcademySetup(
                    uid = uid,
                    email = email,
                    displayName = displayName
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun completeAdminAcademyCreation(
        academyName: String,
        adminName: String
    ): Result<AuthSession> = withContext(Dispatchers.IO) {
        try {
            val auth = FirebaseClientProvider.getAuthOrNull(context)
                ?: return@withContext Result.failure(IllegalStateException("Firebase Auth is not configured."))
            val firestore = FirebaseClientProvider.getFirestoreOrNull(context)
                ?: return@withContext Result.failure(IllegalStateException("Firestore is not configured."))

            val user = auth.currentUser
                ?: return@withContext Result.failure(IllegalStateException("Please sign in with Google first."))

            val uid = user.uid
            val email = (user.email ?: "").trim().lowercase()
            val cleanAdmin = adminName.trim().ifBlank { user.displayName?.trim().orEmpty().ifBlank { "Admin" } }
            val cleanAcademy = academyName.trim().ifBlank { "Alhamd Science Evening Academy" }
            val previousSession = prefs.getAuthSessionOnce()

            val academyId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            val academyDoc = mapOf(
                "academyName" to cleanAcademy,
                "tagline" to "",
                "phone" to "",
                "address" to "",
                "ownerUid" to uid,
                "createdAt" to now,
                "updatedAt" to now
            )
            val memberDoc = mapOf(
                "uid" to uid,
                "email" to email,
                "name" to cleanAdmin,
                "role" to UserRole.ADMIN,
                "createdAt" to now
            )
            val userDoc = mapOf(
                "academyId" to academyId,
                "role" to UserRole.ADMIN,
                "email" to email,
                "name" to cleanAdmin
            )

            val academyRef = firestore.collection(SyncCollections.ACADEMIES).document(academyId)
            try {
                val batch = firestore.batch()
                batch.set(academyRef, academyDoc)
                batch.set(academyRef.collection(SyncCollections.MEMBERS).document(uid), memberDoc)
                batch.set(firestore.collection(SyncCollections.USERS).document(uid), userDoc)
                batch.commit().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, "${SyncCollections.ACADEMIES}/$academyId")
                return@withContext Result.failure(e)
            }

            if (previousSession.academyId.isNotBlank() && previousSession.academyId != academyId) {
                db.clearAllTables()
            }

            db.academyProfileDao().upsert(
                AcademyProfile(
                    id = AcademyProfile.PROFILE_ID,
                    academyName = cleanAcademy,
                    updatedAt = now,
                    pendingSync = false
                )
            )
            db.academyMemberDao().upsert(
                AcademyMember(
                    uid = uid,
                    email = email,
                    name = cleanAdmin,
                    role = UserRole.ADMIN,
                    createdAt = now
                )
            )
            prefs.ensureDevicePrefix()
            prefs.saveAuthSession(
                uid = uid,
                email = email,
                name = cleanAdmin,
                academyId = academyId,
                role = UserRole.ADMIN,
                isOfflineLocalMode = false
            )
            syncManager.startListeners(academyId)
            syncManager.requestPush()

            Result.success(prefs.getAuthSessionOnce())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun inviteTeacherByEmail(
        name: String,
        email: String
    ): Result<AcademyMember> = withContext(Dispatchers.IO) {
        val current = prefs.getAuthSessionOnce()
        if (!current.isAdmin || current.isOfflineLocalMode || current.academyId.isBlank()) {
            return@withContext Result.failure(SecurityException("Only cloud-signed-in Admin can invite teachers."))
        }
        val firestore = FirebaseClientProvider.getFirestoreOrNull(context)
            ?: return@withContext Result.failure(IllegalStateException("Firestore is not configured."))

        val cleanName = name.trim()
        val cleanEmailLower = email.trim().lowercase()
        val invitePath = "${SyncCollections.INVITES}/$cleanEmailLower"
        val now = System.currentTimeMillis()
        val profile = db.academyProfileDao().getProfileOnce()

        val inviteDoc = mapOf(
            "academyId" to current.academyId,
            "academyName" to (profile?.academyName ?: "Alhamd Science Evening Academy"),
            "role" to UserRole.TEACHER,
            "teacherName" to cleanName,
            "email" to cleanEmailLower,
            "invitedByUid" to current.uid,
            "createdAt" to now
        )

        return@withContext try {
            firestore.collection(SyncCollections.INVITES).document(cleanEmailLower)
                .set(inviteDoc)
                .await()

            val invitedPreview = AcademyMember(
                uid = "invite:$cleanEmailLower",
                email = cleanEmailLower,
                name = "$cleanName (Invited)",
                role = UserRole.TEACHER,
                createdAt = now
            )
            db.academyMemberDao().upsert(invitedPreview)
            Result.success(invitedPreview)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, invitePath)
            Result.failure(e)
        }
    }

    suspend fun continueOfflineLocalMode(academyName: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        val cleanName = academyName.trim().ifBlank { "Alhamd Science Evening Academy" }
        val now = System.currentTimeMillis()
        db.academyProfileDao().upsert(
            AcademyProfile(
                id = AcademyProfile.PROFILE_ID,
                academyName = cleanName,
                updatedAt = now,
                pendingSync = true
            )
        )
        prefs.ensureDevicePrefix()
        prefs.saveAuthSession(
            uid = "local_admin",
            email = "",
            name = "Admin",
            academyId = "local_academy",
            role = UserRole.ADMIN,
            isOfflineLocalMode = true
        )
        Result.success(prefs.getAuthSessionOnce())
    }

    suspend fun signOut(credentialManager: CredentialManager? = null) = withContext(NonCancellable + Dispatchers.IO) {
        syncManager.stopListeners()
        runCatching { FirebaseClientProvider.getAuthOrNull(context)?.signOut() }
        if (credentialManager != null) {
            runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
        }
        prefs.clearAuthSession()
    }

    private suspend fun activateSession(
        previousAcademyId: String,
        uid: String,
        email: String,
        name: String,
        academyId: String,
        role: String
    ) {
        if (previousAcademyId.isNotBlank() && previousAcademyId != academyId) {
            db.clearAllTables()
        }
        prefs.ensureDevicePrefix()
        prefs.saveAuthSession(
            uid = uid,
            email = email,
            name = name,
            academyId = academyId,
            role = role,
            isOfflineLocalMode = false
        )
        syncManager.startListeners(academyId)
        syncManager.requestPush()
    }
}
