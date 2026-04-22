package com.example.motoday.data.remote

import android.content.Context
import io.appwrite.exceptions.AppwriteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthManager(context: Context) {
    private val appwrite = AppwriteManager(context)

    suspend fun isUserLoggedIn(): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                appwrite.account.get()
                true
            }
        } catch (e: AppwriteException) {
            false
        }
    }

    suspend fun logout() {
        try {
            withContext(Dispatchers.IO) {
                appwrite.account.deleteSession("current")
            }
        } catch (e: AppwriteException) {
            // Error al cerrar sesión
        }
    }

    suspend fun getCurrentUserId(): String? {
        return try {
            withContext(Dispatchers.IO) {
                val user = appwrite.account.get()
                user.id
            }
        } catch (e: AppwriteException) {
            null
        }
    }
}
