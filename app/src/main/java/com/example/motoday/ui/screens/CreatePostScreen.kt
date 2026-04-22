package com.example.motoday.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.motoday.data.local.AppDatabase
import com.example.motoday.data.remote.AppwriteManager
import com.example.motoday.data.remote.AuthManager
import io.appwrite.models.InputFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appwrite = remember { AppwriteManager(context) }
    val authManager = remember { AuthManager(context) }
    val db = AppDatabase.getDatabase(context)

    var caption by remember { mutableStateOf("") }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // Selector Múltiple (Máximo 3)
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 3),
        onResult = { uris -> 
            if (uris.size > 3) {
                Toast.makeText(context, "Máximo 3 fotos permitidas", Toast.LENGTH_SHORT).show()
                selectedImages = uris.take(3)
            } else {
                selectedImages = uris
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nueva Publicación") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Contenedor de Imágenes Seleccionadas
            if (selectedImages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.LightGray.copy(alpha = 0.3f))
                        .clickable {
                            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                        Text("Añadir fotos de la ruta (Máx. 3)", color = Color.Gray)
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                ) {
                    items(selectedImages) { uri ->
                        Box(modifier = Modifier.width(150.dp).fillMaxHeight()) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { selectedImages = selectedImages.filter { it != uri } },
                                modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.5f), CircleShape).size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    if (selectedImages.size < 3) {
                        item {
                            Box(
                                modifier = Modifier
                                    .width(100.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.LightGray.copy(alpha = 0.3f))
                                    .clickable { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, null, tint = Color.Gray)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("¿Qué está pasando en la ruta?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (selectedImages.isNotEmpty()) {
                        isLoading = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                val userId = authManager.getCurrentUserId() ?: throw Exception("Usuario no identificado")
                                val userLocal = db.userDao().getUserProfileOnce() 
                                
                                val uploadedUrls = mutableListOf<String>()

                                // Subir cada imagen
                                selectedImages.forEachIndexed { index, uri ->
                                    val inputStream = context.contentResolver.openInputStream(uri)
                                    val bytes = inputStream?.use { it.readBytes() } ?: throw Exception("Error al leer imagen $index")
                                    
                                    val fileName = "post_${userId}_${System.currentTimeMillis()}_$index.jpg"
                                    val inputFile = InputFile.fromBytes(bytes, fileName, "image/jpeg")
                                    
                                    val fileId = appwrite.uploadImage(inputFile)
                                    uploadedUrls.add(appwrite.getImageUrl(fileId))
                                }

                                // Crear documento con el ARRAY de URLs
                                appwrite.createPost(
                                    userId = userId,
                                    userName = userLocal?.name ?: "Motero",
                                    userLevel = userLocal?.level ?: "Novato",
                                    profilePic = userLocal?.profilePictureUri,
                                    imageUrls = uploadedUrls,
                                    caption = caption,
                                    timestamp = System.currentTimeMillis()
                                )

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "¡Ruta publicada!", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    } else {
                        Toast.makeText(context, "Añade al menos una foto", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Publicar en el Feed", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
