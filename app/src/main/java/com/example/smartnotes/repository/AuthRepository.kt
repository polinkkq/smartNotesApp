package com.example.smartnotes.repository

import com.example.smartnotes.models.User
import com.example.smartnotes.models.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException

class AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    fun getCurrentUser() = auth.currentUser

    fun logoutUser() {
        auth.signOut()
    }

    suspend fun loginUser(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "loginUser failed")
            Result.failure(e)
        }
    }

    suspend fun registerUser(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        role: UserRole
    ): Result<String> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid ?: return Result.failure(Exception("UID is null"))

            // Создаём пользователя в Firestore (через FirebaseRepository)
            val firebaseRepository = FirebaseRepository()
            val user = User(
                id = uid,
                firstName = firstName,
                lastName = lastName,
                email = email,
                role = role
            )

            val createRes = firebaseRepository.createUser(user)
            if (createRes.isSuccess) Result.success(uid) else Result.failure(createRes.exceptionOrNull()!!)
        } catch (e: Exception) {
            Timber.e(e, "registerUser failed")
            Result.failure(e)
        }
    }


    suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "signInWithGoogleIdToken failed")
            Result.failure(e)
        }
    }


    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "sendPasswordReset failed")
            Result.failure(e)
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        return try {
            val user = getCurrentUser() ?: return Result.failure(Exception("Пользователь не авторизован"))
            val email = user.email ?: return Result.failure(Exception("У аккаунта нет email"))

            // Проверяем, что это парольный аккаунт
            val isPasswordProvider = user.providerData.any { it.providerId == "password" }
            if (!isPasswordProvider) {
                return Result.failure(Exception("Пароль нельзя сменить для этого способа входа"))
            }

            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).await()
            user.updatePassword(newPassword).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun hasPasswordProvider(): Boolean {
        val user = getCurrentUser() ?: return false
        return user.providerData.any { it.providerId == "password" }
    }
    suspend fun deleteAccount(): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("Пользователь не авторизован"))
            val uid = user.uid

            // 1) удаляем данные в Firestore
            val firebaseRepository = FirebaseRepository()
            val dataRes = firebaseRepository.deleteUserData(uid)
            if (dataRes.isFailure) return Result.failure(dataRes.exceptionOrNull()!!)

            // 2) удаляем пользователя в FirebaseAuth
            user.delete().await()

            // 3) на всякий случай разлогин
            auth.signOut()

            Result.success(Unit)
        } catch (e: Exception) {
            // Тут чаще всего будет "recent login required"
            Timber.e(e, "deleteAccount failed")
            Result.failure(e)
        }
    }
    suspend fun reauthAndDeleteAccount(password: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("Пользователь не авторизован"))
            val email = user.email ?: return Result.failure(Exception("У аккаунта нет email"))

            val credential = EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(credential).await()

            // после reauth делаем обычное удаление
            deleteAccount()
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            Result.failure(Exception("Нужен повторный вход. Выйди и войди заново, затем повтори удаление."))
        } catch (e: Exception) {
            Timber.e(e, "reauthAndDeleteAccount failed")
            Result.failure(e)
        }
    }
}
