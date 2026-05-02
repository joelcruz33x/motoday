package com.example.motoday.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.motoday.data.remote.AppwriteManager
import com.example.motoday.navigation.Screen
import io.appwrite.exceptions.AppwriteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.motoday.data.local.AppDatabase
import com.example.motoday.data.local.entities.UserEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    val appwrite = remember { AppwriteManager.getInstance(context) }
    val db = AppDatabase.getDatabase(context)
    val scope = rememberCoroutineScope()
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "¡Hola de nuevo!",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Inicia sesión con Appwrite",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo electrónico") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (email.isNotBlank() && password.isNotBlank()) {
                    isLoading = true
                    scope.launch(Dispatchers.IO) {
                        try {
                            appwrite.account.createEmailPasswordSession(email, password)
                            
                            // 1. Obtener ID de usuario
                            val userId = appwrite.account.get().id
                            
                            // 2. Descargar perfil de Appwrite
                            val remoteProfile = appwrite.getUserProfile(userId)
                            
                            // 3. Si existe en la nube, guardarlo en Room (Local)
                            if (remoteProfile != null) {
                                val userEntity = UserEntity(
                                    id = 1, // ID fijo para el perfil local
                                    name = remoteProfile.data["name"] as? String ?: "Motero",
                                    level = remoteProfile.data["level"] as? String ?: "Novato",
                                    bikeModel = remoteProfile.data["bikeModel"] as? String ?: "",
                                    bikeSpecs = remoteProfile.data["bikeSpecs"] as? String ?: "",
                                    bikeYear = remoteProfile.data["bikeYear"] as? String ?: "",
                                    bikeColor = remoteProfile.data["bikeColor"] as? String ?: "",
                                    bikeStatus = remoteProfile.data["bikeStatus"] as? String ?: "Disponible",
                                    profilePictureUri = remoteProfile.data["profilePic"] as? String,
                                    totalKilometers = (remoteProfile.data["totalKm"] as? Number)?.toInt() ?: 0,
                                    ridesCompleted = (remoteProfile.data["rides"] as? Number)?.toInt() ?: 0,
                                    octanos = (remoteProfile.data["octanos"] as? Number)?.toInt() ?: 0
                                )
                                db.userDao().insertOrUpdate(userEntity)
                            }

                            withContext(Dispatchers.Main) {
                                isLoading = false
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            }
                        } catch (e: AppwriteException) {
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("Entrar", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        TextButton(
            onClick = { navController.navigate(Screen.Register.route) },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("¿No tienes cuenta? Regístrate aquí")
        }
    }
}
