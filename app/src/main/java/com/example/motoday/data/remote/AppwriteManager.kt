package com.example.motoday.data.remote

import android.content.Context
import io.appwrite.Client
import io.appwrite.services.Account
import io.appwrite.services.Databases
import io.appwrite.services.Storage

class AppwriteManager(context: Context) {
    val client = Client(context)
        .setEndpoint("https://nyc.cloud.appwrite.io/v1")
        .setProject("69e6836b00267f431c20")
        .setSelfSigned(true) // Útil si usas un servidor local con certificados auto-firmados

    val account = Account(client)
    val databases = Databases(client)
    val storage = Storage(client)
}
