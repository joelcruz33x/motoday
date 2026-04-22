package com.example.motoday.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.motoday.data.local.AppDatabase
import com.example.motoday.data.local.entities.UserEntity
import com.example.motoday.data.remote.AppwriteManager
import com.example.motoday.navigation.Screen
import io.appwrite.ID
import io.appwrite.exceptions.AppwriteException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(navController: NavController) {
    val context = LocalContext.current
    val appwrite = remember { AppwriteManager(context) }
    val db = AppDatabase.getDatabase(context)
    val scope = rememberCoroutineScope()
    
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Opciones de nivel
    val levels = listOf("Novato", "Intermedio", "Experto")
    var expanded by remember { mutableStateOf(false) }
    var selectedLevel by remember { mutableStateOf(levels[0]) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Únete a la ruta",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Crea tu cuenta en Appwrite y empieza a rodar",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre Motero") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

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
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Selector de Nivel
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedLevel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Nivel de experiencia") },
                leadingIcon = { Icon(Icons.Default.Stars, contentDescription = null) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                levels.forEach { level ->
                    DropdownMenuItem(
                        text = { Text(level) },
                        onClick = {
                            selectedLevel = level
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (name.isNotBlank() && email.isNotBlank() && password.length >= 8) {
                    isLoading = true
                    scope.launch(Dispatchers.IO) {
                        try {
                            // 1. Crear cuenta
                            appwrite.account.create(
                                userId = ID.unique(),
                                email = email,
                                password = password,
                                name = name
                            )
                            
                            // Pequeña pausa para asegurar que el servidor procesó la creación
                            kotlinx.coroutines.delay(500)

                            // 2. Iniciar sesión
                            appwrite.account.createEmailPasswordSession(email, password)

                            // 3. Guardar perfil en Appwrite (Online)
                            appwrite.databases.createDocument(
                                databaseId = "69e81a9500157d642919",
                                collectionId = "profiles",
                                documentId = ID.unique(),
                                data = mapOf(
                                    "name" to name,
                                    "level" to selectedLevel,
                                    "bikeModel" to "Por definir",
                                    "bikeSpecs" to "Sin datos",
                                    "bikeYear" to "-",
                                    "bikeColor" to "-",
                                    "profilePic" to ""
                                )
                            )

                            // 4. Guardar perfil inicial en Room (Local)
                            val newUser = UserEntity(
                                name = name,
                                level = selectedLevel,
                                bikeModel = "Por definir",
                                bikeSpecs = "Sin datos",
                                bikeYear = "-",
                                bikeColor = "-"
                            )
                            db.userDao().insertOrUpdate(newUser)
                            
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Welcome.route) { inclusive = true }
                                }
                            }
                        } catch (e: AppwriteException) {
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                // Mostramos el mensaje real y el código de error para saber qué pasa
                                Toast.makeText(context, "Error (${e.code}): ${e.message}", Toast.LENGTH_LONG).show()
                                android.util.Log.e("APPWRITE_ERROR", "Error: ${e.message}", e)
                            }
                        }
                    }
                } else {
                    Toast.makeText(context, "Completa los campos (Password min. 8 caracteres)", Toast.LENGTH_SHORT).show()
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
                Text("Crear Cuenta", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
