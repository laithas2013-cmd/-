package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.ContactEntry
import com.example.data.model.UserProfile
import com.example.data.model.UsernameDirectoryEntry
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class UserRepository(private val db: FirebaseFirestore) {

  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  fun isValidUsernameFormat(username: String): Boolean {
    return USERNAME_REGEX.matches(username)
  }

  fun normalizeUsername(raw: String): String {
    return raw.trim().removePrefix("@").lowercase()
  }

  private fun generateBaseUsername(firebaseUser: FirebaseUser): String {
    val rawSource =
      firebaseUser.email?.substringBefore("@")
        ?: firebaseUser.displayName
        ?: "tawasul"
    val sanitized = rawSource.lowercase().replace(Regex("[^a-z0-9_]"), "_").replace(Regex("_+"), "_").trim('_')
    val padded =
      when {
        sanitized.length >= 3 -> sanitized.take(20)
        sanitized.isNotEmpty() -> "${sanitized}_user".take(20)
        else -> "tawasul_user"
      }
    return padded
  }

  suspend fun ensureUserProfile(
    firebaseUser: FirebaseUser,
    defaultLanguage: String = "ar",
    defaultTheme: String = "system",
  ): Result<UserProfile> {
    val uid = firebaseUser.uid
    val userRef = db.collection("users").document(uid)
    return try {
      val existingSnapshot = userRef.get().await()
      if (existingSnapshot.exists()) {
        val existingProfile =
          existingSnapshot.toObject(
            UserProfile::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        if (existingProfile != null) {
          // Mark online on session start
          val batch = db.batch()
          batch.update(
            userRef,
            mapOf(
              "isOnline" to true,
              "lastSeen" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          if (existingProfile.username.isNotBlank()) {
            val unameRef = db.collection("usernames").document(existingProfile.username)
            batch.update(
              unameRef,
              mapOf(
                "isOnline" to true,
                "lastSeen" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            )
          }
          batch.commit().await()
          return Result.success(existingProfile.copy(isOnline = true))
        }
      }

      // First-time sign-in: auto-create profile and claim unique username
      val base = generateBaseUsername(firebaseUser)
      val displayName =
        firebaseUser.displayName?.trim()?.take(60)?.ifBlank { null }
          ?: firebaseUser.email?.substringBefore("@")?.take(60)
          ?: "Tawasul User"
      val email = firebaseUser.email?.take(200) ?: "$uid@tawasul.local"
      val avatarUrl = firebaseUser.photoUrl?.toString()?.take(29000)

      var attempt = 0
      var lastException: Exception? = null
      while (attempt < 5) {
        val candidate =
          if (attempt == 0) base
          else "${base.take(22)}_${Random.nextInt(1000, 9999)}"
        val unameRef = db.collection("usernames").document(candidate)
        try {
          db.runTransaction { tx ->
              val unameSnap = tx.get(unameRef)
              if (unameSnap.exists()) {
                throw IllegalStateException("Username collision")
              }
              val userPayload =
                mutableMapOf<String, Any>(
                  "userId" to uid,
                  "email" to email,
                  "username" to candidate,
                  "displayName" to displayName,
                  "bio" to "",
                  "onboardingCompleted" to false,
                  "preferredLanguage" to defaultLanguage,
                  "themeMode" to defaultTheme,
                  "isOnline" to true,
                  "lastSeen" to FieldValue.serverTimestamp(),
                  "createdAt" to FieldValue.serverTimestamp(),
                  "updatedAt" to FieldValue.serverTimestamp(),
                )
              if (!avatarUrl.isNullOrBlank()) {
                userPayload["avatarUrl"] = avatarUrl
              }

              val unamePayload =
                mutableMapOf<String, Any>(
                  "userId" to uid,
                  "username" to candidate,
                  "displayName" to displayName,
                  "bio" to "",
                  "isOnline" to true,
                  "lastSeen" to FieldValue.serverTimestamp(),
                  "createdAt" to FieldValue.serverTimestamp(),
                  "updatedAt" to FieldValue.serverTimestamp(),
                )
              if (!avatarUrl.isNullOrBlank()) {
                unamePayload["avatarUrl"] = avatarUrl
              }

              tx.set(userRef, userPayload)
              tx.set(unameRef, unamePayload)
            }
            .await()

          val created =
            userRef.get().await().toObject(
              UserProfile::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            ) ?: UserProfile(
              id = uid,
              userId = uid,
              email = email,
              username = candidate,
              displayName = displayName,
              bio = "",
              avatarUrl = avatarUrl,
              onboardingCompleted = false,
              preferredLanguage = defaultLanguage,
              themeMode = defaultTheme,
              isOnline = true,
            )
          return Result.success(created)
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          lastException = e
          attempt++
        }
      }
      val err = lastException ?: IllegalStateException("Could not reserve unique username")
      handleFirestoreError(err, OperationType.CREATE, userRef.path)
      Result.failure(err)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, userRef.path)
      Result.failure(e)
    }
  }

  fun observeMyProfile(uid: String): Flow<UserProfile?> {
    val docRef = db.collection("users").document(uid)
    return docRef
      .snapshots()
      .map { snapshot ->
        if (snapshot.exists()) {
          snapshot.toObject(
            UserProfile::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        } else {
          null
        }
      }
      .catch { error ->
        if (error is CancellationException && error.cause == null) throw error
        handleFirestoreError(error, OperationType.GET, docRef.path)
        throw error
      }
  }

  fun observeUsernameEntry(username: String): Flow<UsernameDirectoryEntry?> {
    val clean = normalizeUsername(username)
    val docRef = db.collection("usernames").document(clean)
    return docRef
      .snapshots()
      .map { snapshot ->
        if (snapshot.exists()) {
          snapshot.toObject(
            UsernameDirectoryEntry::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        } else {
          null
        }
      }
      .catch { error ->
        if (error is CancellationException && error.cause == null) throw error
        handleFirestoreError(error, OperationType.GET, docRef.path)
        throw error
      }
  }

  fun observePresenceByUsernames(usernames: List<String>): Flow<Map<String, Boolean>> {
    val cleanList = usernames.map { normalizeUsername(it) }.filter { isValidUsernameFormat(it) }.distinct().take(15)
    if (cleanList.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyMap())
    val flows =
      cleanList.map { uname ->
        observeUsernameEntry(uname)
          .map { entry -> uname to (entry?.isOnline == true) }
          .catch { emit(uname to false) }
      }
    return kotlinx.coroutines.flow.combine(flows) { pairs -> pairs.toMap() }
  }

  suspend fun checkUsernameAvailable(rawUsername: String, currentUsername: String): Result<Boolean> {
    val clean = normalizeUsername(rawUsername)
    if (!isValidUsernameFormat(clean)) {
      return Result.failure(IllegalArgumentException("Invalid username format"))
    }
    if (clean == currentUsername) return Result.success(true)
    val docRef = db.collection("usernames").document(clean)
    return try {
      val snap = docRef.get().await()
      Result.success(!snap.exists())
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun findUserByUsername(rawUsername: String): Result<UsernameDirectoryEntry?> {
    val clean = normalizeUsername(rawUsername)
    if (!isValidUsernameFormat(clean)) {
      return Result.success(null)
    }
    val docRef = db.collection("usernames").document(clean)
    return try {
      val snap = docRef.get().await()
      val entry =
        if (snap.exists()) {
          snap.toObject(
            UsernameDirectoryEntry::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        } else {
          null
        }
      Result.success(entry)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun updateProfile(
    uid: String,
    currentUsername: String,
    newUsernameRaw: String,
    displayName: String,
    bio: String,
    avatarUrl: String?,
    preferredLanguage: String,
    themeMode: String,
    onboardingCompleted: Boolean = true,
  ): Result<Unit> {
    val cleanNewUsername = normalizeUsername(newUsernameRaw)
    if (!isValidUsernameFormat(cleanNewUsername)) {
      return Result.failure(IllegalArgumentException("Invalid username"))
    }
    val trimmedName = displayName.trim().take(60)
    if (trimmedName.isBlank()) {
      return Result.failure(IllegalArgumentException("Display name required"))
    }
    val trimmedBio = bio.trim().take(200)
    val cleanAvatar = avatarUrl?.trim()?.take(29000)?.ifBlank { null }

    val userRef = db.collection("users").document(uid)
    return try {
      if (cleanNewUsername != currentUsername) {
        val oldUnameRef = db.collection("usernames").document(currentUsername)
        val newUnameRef = db.collection("usernames").document(cleanNewUsername)
        db.runTransaction { tx ->
            val newSnap = tx.get(newUnameRef)
            if (newSnap.exists() && newSnap.getString("userId") != uid) {
              throw IllegalStateException("USERNAME_TAKEN")
            }
            val userUpdates =
              mutableMapOf<String, Any>(
                "username" to cleanNewUsername,
                "displayName" to trimmedName,
                "bio" to trimmedBio,
                "onboardingCompleted" to onboardingCompleted,
                "preferredLanguage" to preferredLanguage,
                "themeMode" to themeMode,
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            userUpdates["avatarUrl"] = cleanAvatar ?: FieldValue.delete()

            val newUnamePayload =
              mutableMapOf<String, Any>(
                "userId" to uid,
                "username" to cleanNewUsername,
                "displayName" to trimmedName,
                "bio" to trimmedBio,
                "isOnline" to true,
                "lastSeen" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            if (cleanAvatar != null) {
              newUnamePayload["avatarUrl"] = cleanAvatar
            }

            tx.update(userRef, userUpdates)
            tx.set(newUnameRef, newUnamePayload)
            if (currentUsername.isNotBlank() && isValidUsernameFormat(currentUsername)) {
              tx.delete(oldUnameRef)
            }
          }
          .await()
      } else {
        val batch = db.batch()
        val userUpdates =
          mutableMapOf<String, Any>(
            "displayName" to trimmedName,
            "bio" to trimmedBio,
            "onboardingCompleted" to onboardingCompleted,
            "preferredLanguage" to preferredLanguage,
            "themeMode" to themeMode,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        userUpdates["avatarUrl"] = cleanAvatar ?: FieldValue.delete()
        batch.update(userRef, userUpdates)

        if (currentUsername.isNotBlank()) {
          val unameRef = db.collection("usernames").document(currentUsername)
          val unameUpdates =
            mutableMapOf<String, Any>(
              "displayName" to trimmedName,
              "bio" to trimmedBio,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          unameUpdates["avatarUrl"] = cleanAvatar ?: FieldValue.delete()
          batch.update(unameRef, unameUpdates)
        }
        batch.commit().await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, userRef.path)
      Result.failure(e)
    }
  }

  suspend fun completeOnboarding(
    uid: String,
    currentUsername: String,
    newUsernameRaw: String,
    displayName: String,
    bio: String,
    avatarUrl: String?,
    preferredLanguage: String,
    themeMode: String,
  ): Result<Unit> =
    updateProfile(
      uid = uid,
      currentUsername = currentUsername,
      newUsernameRaw = newUsernameRaw,
      displayName = displayName,
      bio = bio,
      avatarUrl = avatarUrl,
      preferredLanguage = preferredLanguage,
      themeMode = themeMode,
      onboardingCompleted = true,
    )

  suspend fun updatePreferences(
    uid: String,
    preferredLanguage: String,
    themeMode: String,
  ): Result<Unit> {
    val userRef = db.collection("users").document(uid)
    return try {
      userRef
        .update(
          mapOf(
            "preferredLanguage" to preferredLanguage,
            "themeMode" to themeMode,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, userRef.path)
      Result.failure(e)
    }
  }

  suspend fun setOnlineStatus(uid: String, username: String, isOnline: Boolean) {
    val userRef = db.collection("users").document(uid)
    try {
      val batch = db.batch()
      batch.update(
        userRef,
        mapOf(
          "isOnline" to isOnline,
          "lastSeen" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      if (username.isNotBlank() && isValidUsernameFormat(username)) {
        val unameRef = db.collection("usernames").document(username)
        batch.update(
          unameRef,
          mapOf(
            "isOnline" to isOnline,
            "lastSeen" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, userRef.path)
    }
  }

  fun observeContacts(uid: String): Flow<List<ContactEntry>> {
    val colRef = db.collection("users").document(uid).collection("contacts")
    return colRef
      .snapshots()
      .map { snapshot ->
        snapshot
          .toObjects(ContactEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedBy { it.displayName.lowercase() }
      }
      .catch { error ->
        if (error is CancellationException && error.cause == null) throw error
        handleFirestoreError(error, OperationType.LIST, colRef.path)
        throw error
      }
  }

  suspend fun addContact(uid: String, entry: UsernameDirectoryEntry): Result<Unit> {
    val docRef = db.collection("users").document(uid).collection("contacts").document(entry.userId)
    return try {
      val payload =
        mutableMapOf<String, Any>(
          "contactUid" to entry.userId,
          "username" to entry.username,
          "displayName" to entry.displayName,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      if (!entry.avatarUrl.isNullOrBlank()) {
        payload["avatarUrl"] = entry.avatarUrl
      }
      docRef.set(payload).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun removeContact(uid: String, contactUid: String): Result<Unit> {
    val docRef = db.collection("users").document(uid).collection("contacts").document(contactUid)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  companion object {
    private val USERNAME_REGEX = Regex("^[a-z0-9_]{3,30}$")
  }
}
