package com.example.motoday.ui.screens

import android.util.Log
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.motoday.data.local.AppDatabase
import com.example.motoday.data.local.entities.UserEntity
import com.example.motoday.navigation.Screen
import com.example.motoday.ui.components.BottomNavigationBar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import com.example.motoday.data.remote.AppwriteManager
import com.example.motoday.data.remote.AuthManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val userProfile by db.userDao().getUserProfile().collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    
    val appwrite = remember { AppwriteManager.getInstance(context) }
    val authManager = remember { AuthManager(context) }

    var isEditing by remember { mutableStateOf(false) }
    var isLoadingInitial by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Mi Moto", "Pasaporte", "Logros", "Estadísticas")

    LaunchedEffect(Unit) {
        val userId = authManager.getCurrentUserId()
        if (userId != null) {
            // 0. Sincronizar Perfil
            try {
                val profileDoc = appwrite.getUserProfile(userId)
                if (profileDoc != null) {
                    val data = profileDoc.data
                    val profilePicId = data["profilePic"] as? String
                    val bikePicId = data["bikePic"] as? String
                    val localUser = db.userDao().getUserProfileOnce()

                    // Solo actualizar la URL si recibimos un ID válido de Appwrite, 
                    // de lo contrario conservar la que ya tenemos localmente.
                    val updatedProfilePic = when {
                        !profilePicId.isNullOrBlank() && profilePicId != "null" -> {
                            if (profilePicId.startsWith("http")) profilePicId 
                            else appwrite.getImageUrl(profilePicId, AppwriteManager.BUCKET_PROFILES_ID)
                        }
                        else -> localUser?.profilePictureUri?.takeIf { it.isNotBlank() && it != "null" }
                    }

                    val updatedBikePic = when {
                        !bikePicId.isNullOrBlank() && bikePicId != "null" -> {
                            if (bikePicId.startsWith("http")) bikePicId 
                            else appwrite.getImageUrl(bikePicId, AppwriteManager.BUCKET_BIKES_ID)
                        }
                        else -> localUser?.bikePictureUri?.takeIf { it.isNotBlank() && it != "null" }
                    }

                    val newUser = (localUser ?: UserEntity(id = 1, name = "Motero", level = "Novato", bikeModel = "Sin moto", bikeSpecs = "", bikeYear = "", bikeColor = "")).copy(
                        name = data["name"] as? String ?: (localUser?.name ?: "Motero"),
                        level = data["level"] as? String ?: (localUser?.level ?: "Novato"),
                        clubName = data["clubName"] as? String ?: (localUser?.clubName ?: "Independiente"),
                        clubRole = data["clubRole"] as? String ?: (localUser?.clubRole),
                        bikeModel = data["bikeModel"] as? String ?: (localUser?.bikeModel ?: "Sin moto"),
                        bikeSpecs = data["bikeSpecs"] as? String ?: (localUser?.bikeSpecs ?: ""),
                        bikeYear = data["bikeYear"] as? String ?: (localUser?.bikeYear ?: ""),
                        bikeColor = data["bikeColor"] as? String ?: (localUser?.bikeColor ?: ""),
                        profilePictureUri = updatedProfilePic,
                        bikePictureUri = updatedBikePic,
                        isIndependent = when(val ind = data["isIndependent"]) {
                            is Boolean -> ind
                            is Number -> ind.toInt() == 1
                            is String -> ind.lowercase() == "true"
                            else -> localUser?.isIndependent ?: true
                        },
                        totalKilometers = (data["totalKm"] as? Number)?.toInt() ?: (localUser?.totalKilometers ?: 0),
                        ridesCompleted = (data["rides"] as? Number)?.toInt() ?: (localUser?.ridesCompleted ?: 0)
                    )
                    db.userDao().insertOrUpdate(newUser)
                } else if (db.userDao().getUserProfileOnce() == null) {
                    db.userDao().insertOrUpdate(UserEntity(id = 1, name = "Motero", level = "Novato", bikeModel = "Sin moto", bikeSpecs = "", bikeYear = "", bikeColor = ""))
                }
            } catch (e: Exception) {
                Log.e("ProfileScreen", "Error sincronizando perfil: ${e.message}")
                if (db.userDao().getUserProfileOnce() == null) {
                    db.userDao().insertOrUpdate(UserEntity(id = 1, name = "Motero (Offline)", level = "Novato", bikeModel = "Sin moto", bikeSpecs = "", bikeYear = "", bikeColor = ""))
                }
            }

            // 1. Sincronizar Roles de forma que no parpadee (Persistir en BD local)
            try {
                val allGroups = appwrite.getGroups()
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
                var foundRole: String? = null
                var foundGroupName: String = "Independiente"
                var foundGroupPhoto: String? = null
                var userFoundInAnyGroup = false
                
                for (doc in allGroups) {
                    val members = (doc.data["members"] as? List<*>)?.map { it.toString() } ?: emptyList()
                    if (members.contains(userId)) {
                        userFoundInAnyGroup = true
                        val rolesMap: Map<String, String> = gson.fromJson(doc.data["roles"] as? String ?: "{}", type) ?: emptyMap()
                        foundRole = rolesMap[userId] // null significa Miembro
                        foundGroupName = doc.data["name"] as? String ?: "Grupo"
                        val groupPhoto = doc.data["photoUrl"] as? String
                        foundGroupPhoto = if (!groupPhoto.isNullOrBlank()) {
                            appwrite.getImageUrl(groupPhoto, AppwriteManager.BUCKET_GROUPS_ID)
                        } else null
                        break
                    }
                }

                val currentLocal = db.userDao().getUserProfileOnce()
                if (currentLocal != null) {
                    if (userFoundInAnyGroup) {
                        if (currentLocal.clubRole != foundRole || 
                            currentLocal.clubName != foundGroupName || 
                            currentLocal.groupPhotoUri != foundGroupPhoto) {
                            db.userDao().insertOrUpdate(currentLocal.copy(
                                clubRole = foundRole,
                                clubName = foundGroupName,
                                groupPhotoUri = foundGroupPhoto
                            ))
                        }
                    } else {
                        // El usuario no está en ningún grupo, resetear a Independiente localmente
                        if (currentLocal.clubName != "Independiente" || currentLocal.clubRole != null) {
                            db.userDao().insertOrUpdate(currentLocal.copy(
                                clubRole = null,
                                clubName = "Independiente",
                                groupPhotoUri = null
                            ))
                        }
                    }
                }
            } catch (e: Exception) { Log.e("ProfileScreen", "Error roles: ${e.message}") }

            // 2. Sincronizar Sellos
            try {
                val remoteDocuments = appwrite.getUserStamps(userId)
                if (remoteDocuments.isNotEmpty()) {
                            val localStamps = remoteDocuments.map { doc ->
                        com.example.motoday.data.local.entities.PassportStampEntity(
                            id = 0, // Dejar que Room asigne ID si es nuevo
                            rideRemoteId = doc.data["rideRemoteId"] as? String ?: (doc.data["rideId"] as? String ?: ""),
                            rideTitle = doc.data["rideTitle"] as? String ?: "Viaje",
                            locationName = doc.data["locationName"] as? String ?: "Desconocido",
                            iconResName = doc.data["iconResName"] as? String ?: "ic_stamp_default",
                            date = (doc.data["date"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        )
                    }
                    // Usar insertStamps que debería manejar conflictos o simplemente añadir los nuevos
                    db.passportDao().insertStamps(localStamps)
                    Log.d("ProfileScreen", "Sincronizados ${localStamps.size} sellos desde Appwrite")
                }
            } catch (e: Exception) { Log.e("ProfileScreen", "Error sellos: ${e.message}") }

            // 3. Sincronizar Garaje (Motos)
            try {
                val remoteBikes = appwrite.getUserBikes(userId)
                if (remoteBikes.isNotEmpty()) {
                    val currentLocalBikes = db.bikeDao().getAllBikesOnce()
                    remoteBikes.forEach { doc ->
                        val remoteId = doc.id
                        val existingLocal = currentLocalBikes.find { it.remoteId == remoteId }
                        val bike = com.example.motoday.data.local.entities.BikeEntity(
                            id = existingLocal?.id ?: 0,
                            remoteId = remoteId,
                            model = doc.data["model"] as? String ?: "Desconocida",
                            year = doc.data["year"] as? String ?: "",
                            color = doc.data["color"] as? String ?: "",
                            specs = doc.data["specs"] as? String ?: "",
                            status = doc.data["status"] as? String ?: "Excelente",
                            currentKm = (doc.data["currentKm"] as? Number)?.toInt() ?: 0,
                            bikePictureUri = appwrite.getImageUrl(doc.data["bikePic"] as? String ?: "", AppwriteManager.BUCKET_BIKES_ID).ifBlank { existingLocal?.bikePictureUri }
                        )
                        db.bikeDao().insertOrUpdate(bike)
                        
                        // Sincronizar fotos secundarias de esta moto
                        val localBikeId = if (bike.id == 0) {
                            // Si era nueva, necesitamos el ID generado por Room. 
                            // Como insertOrUpdate no devuelve el ID, lo buscamos por remoteId.
                            db.bikeDao().getAllBikesOnce().find { it.remoteId == remoteId }?.id ?: 0
                        } else bike.id
                        
                        if (localBikeId != 0) {
                            try {
                                val remotePhotos = appwrite.getBikePhotos(remoteId)
                                val localPhotos = db.bikePhotoDao().getPhotosForBikeOnce(localBikeId)
                                
                                remotePhotos.forEach { photoDoc ->
                                    val photoFileId = photoDoc.data["fileId"] as? String
                                    if (!photoFileId.isNullOrBlank()) {
                                        val photoUrl = appwrite.getImageUrl(photoFileId, AppwriteManager.BUCKET_BIKES_ID)
                                        if (localPhotos.none { it.remoteId == photoFileId }) {
                                            db.bikePhotoDao().insertPhoto(
                                                com.example.motoday.data.local.entities.BikePhotoEntity(
                                                    bikeId = localBikeId,
                                                    remoteId = photoFileId,
                                                    uri = photoUrl
                                                )
                                            )
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("ProfileScreen", "Error sincronizando fotos de moto $remoteId: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileScreen", "Error garaje: ${e.message}")
            }

            // 4. Catch-up de Rutas Finalizadas (Para asistentes que no tenían la pantalla abierta)
            try {
                val completedRides = appwrite.getAllRemoteRides().filter { doc ->
                    val status = doc.data["status"] as? String
                    val participants = (doc.data["participantIds"] as? List<*>)?.map { it.toString() } ?: emptyList()
                    status == "COMPLETED" && participants.contains(userId)
                }

                completedRides.forEach { doc ->
                    val remoteId = doc.id

                    val localRide = db.rideDao().getRideByRemoteId(remoteId)
                    if (localRide != null) {
                        val rideRemoteId = localRide.remoteId ?: ""
                        if (rideRemoteId.isNotBlank()) {
                            val alreadyHasStamp = db.passportDao().hasStampForRide(rideRemoteId) > 0
                            if (!alreadyHasStamp) {
                                Log.d("ProfileScreen", "Ejecutando catch-up para ruta finalizada: ${localRide.title}")
                                appwrite.processRideCompletion(context, db, localRide, userId)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileScreen", "Error en catch-up de rutas: ${e.message}")
            }
        } else {
            if (db.userDao().getUserProfileOnce() == null) {
                db.userDao().insertOrUpdate(UserEntity(id = 1, name = "Invitado", level = "Novato", bikeModel = "Sin moto", bikeSpecs = "", bikeYear = "", bikeColor = ""))
            }
        }
        isLoadingInitial = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Perfil Motero") },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Configuración")
                    }
                    IconButton(onClick = { isEditing = !isEditing }) {
                        Icon(if (isEditing) Icons.Default.Close else Icons.Default.Edit, contentDescription = "Editar")
                    }
                }
            )
        },
        bottomBar = {
            BottomNavigationBar(navController)
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            userProfile?.let { user ->
                if (isEditing) {
                    EditProfileForm(user) { updatedUser ->
                        scope.launch {
                            val latestUser = db.userDao().getUserProfileOnce() ?: updatedUser
                            val userToSave = updatedUser.copy(
                                profilePictureUri = latestUser.profilePictureUri,
                                bikePictureUri = latestUser.bikePictureUri
                            )
                            db.userDao().insertOrUpdate(userToSave)
                            
                            try {
                                val userId = authManager.getCurrentUserId()
                                if (userId != null) {
                                    val bikePicId = appwrite.extractFileIdFromUrl(userToSave.bikePictureUri)
                                    val profilePicId = appwrite.extractFileIdFromUrl(userToSave.profilePictureUri)
                                    appwrite.updateUserProfile(
                                        userId = userId,
                                        name = userToSave.name,
                                        level = userToSave.level,
                                        bikeModel = userToSave.bikeModel,
                                        bikeSpecs = userToSave.bikeSpecs,
                                        bikeYear = userToSave.bikeYear,
                                        bikeColor = userToSave.bikeColor,
                                        isIndependent = userToSave.isIndependent,
                                        profilePic = profilePicId,
                                        bikePic = bikePicId
                                    )
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            isEditing = false
                        }
                    }
                } else {
                    val currentRoleInfo = remember(user.clubRole, user.clubName, user.groupPhotoUri) {
                        if (user.clubName != "Independiente" && user.clubName.isNotBlank()) {
                            Triple(user.clubRole ?: "Miembro", user.clubName, user.groupPhotoUri)
                        } else null
                    }

                    ProfileHeader(user, currentRoleInfo, onImageSelected = { uriString ->
                        scope.launch {
                            try {
                                val uri = uriString.toUri()
                                
                                // Intentar persistir permiso si es una URI local de tipo content
                                try {
                                    if (uri.scheme == "content") {
                                        context.contentResolver.takePersistableUriPermission(
                                            uri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.d("ProfileScreen", "No se pudo persistir permiso (posiblemente de Google Photos o ya remota): ${e.message}")
                                }

                                val inputStream = context.contentResolver.openInputStream(uri)
                                val bytes = inputStream?.readBytes()
                                inputStream?.close()

                                if (bytes != null) {
                                    val fileName = "profile_${user.name.replace(" ", "_")}_${System.currentTimeMillis()}.jpg"
                                    val inputFile = io.appwrite.models.InputFile.fromBytes(
                                        bytes = bytes,
                                        filename = fileName,
                                        mimeType = "image/jpeg"
                                    )
                                    
                                    Log.d("ProfileScreen", "Subiendo imagen de perfil a Appwrite...")
                                    val fileId = appwrite.uploadImage(inputFile, AppwriteManager.BUCKET_PROFILES_ID)
                                    val remoteUrl = appwrite.getImageUrl(fileId, AppwriteManager.BUCKET_PROFILES_ID)
                                    Log.d("ProfileScreen", "Imagen subida. URL generada: $remoteUrl")
                                    
                                    val userId = authManager.getCurrentUserId()
                                    if (userId != null) {
                                        val bikePicId = appwrite.extractFileIdFromUrl(user.bikePictureUri)
                                        appwrite.updateUserProfile(
                                            userId = userId,
                                            name = user.name,
                                            level = user.level,
                                            bikeModel = user.bikeModel,
                                            bikeSpecs = user.bikeSpecs,
                                            bikeYear = user.bikeYear,
                                            bikeColor = user.bikeColor,
                                            isIndependent = user.isIndependent,
                                            profilePic = fileId,
                                            bikePic = bikePicId
                                        )
                                    }
                                    
                                    val latestUser = db.userDao().getUserProfileOnce() ?: user
                                    db.userDao().insertOrUpdate(latestUser.copy(profilePictureUri = remoteUrl))
                                    Toast.makeText(context, "Foto actualizada!", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Log.e("ProfileScreen", "Error al subir foto de perfil", e)
                                Toast.makeText(context, "Error al subir foto", Toast.LENGTH_SHORT).show()
                            }
                        }
                    })

                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = 16.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        divider = {}
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { 
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1
                                    ) 
                                }
                            )
                        }
                    }

                    when (selectedTab) {
                        0 -> MyBikeSection(user, navController) { uriString ->
                            scope.launch {
                                try {
                                    val userIdRemote = authManager.getCurrentUserId()
                                    if (userIdRemote == null) {
                                        Toast.makeText(context, "Debes iniciar sesión", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }

                                    val uri = uriString.toUri()
                                    try {
                                        if (uri.scheme == "content") {
                                            context.contentResolver.takePersistableUriPermission(
                                                uri,
                                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            )
                                        }
                                    } catch (e: Exception) {
                                        Log.d("ProfileScreen", "No se pudo persistir permiso moto: ${e.message}")
                                    }
                                    
                                    val inputStream = context.contentResolver.openInputStream(uri)
                                    val bytes = inputStream?.readBytes()
                                    inputStream?.close()

                                    if (bytes != null) {
                                        Log.d("ProfileScreen", "Subiendo foto de moto...")
                                        val fileName = "bike_${userIdRemote}_${System.currentTimeMillis()}.jpg"
                                        val fileId = appwrite.uploadImage(
                                            io.appwrite.models.InputFile.fromBytes(
                                                bytes = bytes,
                                                filename = fileName,
                                                mimeType = "image/jpeg"
                                            ),
                                            AppwriteManager.BUCKET_BIKES_ID
                                        )
                                        
                                        val remoteUrl = appwrite.getImageUrl(fileId, AppwriteManager.BUCKET_BIKES_ID)
                                        Log.d("ProfileScreen", "Foto moto subida: $remoteUrl")

                                        val userId = authManager.getCurrentUserId()
                                        if (userId == null) return@launch

                                        // Primero obtenemos el perfil actual de la nube para no sobreescribir con datos viejos
                                        val currentProfile = appwrite.getUserProfile(userId)
                                        val profileData = currentProfile?.data ?: emptyMap()
                                        val profilePicId = (profileData["profilePic"] as? String) ?: appwrite.extractFileIdFromUrl(user.profilePictureUri)

                                        val success = appwrite.updateUserProfile(
                                            userId = userId,
                                            name = (profileData["name"] as? String) ?: user.name,
                                            level = (profileData["level"] as? String) ?: user.level,
                                            bikeModel = (profileData["bikeModel"] as? String) ?: user.bikeModel,
                                            bikeSpecs = (profileData["bikeSpecs"] as? String) ?: user.bikeSpecs,
                                            bikeYear = (profileData["bikeYear"] as? String) ?: user.bikeYear,
                                            bikeColor = (profileData["bikeColor"] as? String) ?: user.bikeColor,
                                            isIndependent = user.isIndependent,
                                            profilePic = profilePicId,
                                            bikePic = fileId
                                        )
                                        
                                        if (success) {
                                            val latestUser = db.userDao().getUserProfileOnce() ?: user
                                            db.userDao().insertOrUpdate(latestUser.copy(bikePictureUri = remoteUrl))
                                            Toast.makeText(context, "¡Foto de la moto actualizada!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Error al actualizar perfil en la nube", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("ProfileScreen", "Error subiendo foto moto: ${e.message}")
                                    Toast.makeText(context, "Error al subir foto de la moto", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        1 -> PassportSection()
                        2 -> AchievementsSection(user)
                        3 -> StatsSection(user)
                    }
                }
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (isLoadingInitial) {
                    CircularProgressIndicator()
                } else {
                    Text("Error al cargar perfil o sesión no iniciada")
                }
            }
        }
    }
}

@Composable
fun EditProfileForm(user: UserEntity, onSave: (UserEntity) -> Unit) {
    var name by remember { mutableStateOf(user.name) }
    var bikeModel by remember { mutableStateOf(user.bikeModel) }
    var bikeSpecs by remember { mutableStateOf(user.bikeSpecs) }
    var bikeYear by remember { mutableStateOf(user.bikeYear) }
    var bikeColor by remember { mutableStateOf(user.bikeColor) }
    var bikeStatus by remember { mutableStateOf(user.bikeStatus) }
    var isIndependent by remember { mutableStateOf(user.isIndependent) }

    LazyColumn(
        modifier = Modifier.padding(16.dp).fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Editar Información Personal", style = MaterialTheme.typography.titleMedium) }
        item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth()) }
        
        item { Spacer(modifier = Modifier.height(16.dp)) }
        item { Text("Editar Información de la Moto", style = MaterialTheme.typography.titleMedium) }
        item { OutlinedTextField(value = bikeModel, onValueChange = { bikeModel = it }, label = { Text("Modelo de Moto") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(value = bikeSpecs, onValueChange = { bikeSpecs = it }, label = { Text("Cilindraje") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(value = bikeYear, onValueChange = { bikeYear = it }, label = { Text("Año") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(value = bikeColor, onValueChange = { bikeColor = it }, label = { Text("Color") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(value = bikeStatus, onValueChange = { bikeStatus = it }, label = { Text("Estado de la Moto") }, modifier = Modifier.fillMaxWidth()) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Independiente", style = MaterialTheme.typography.bodyLarge)
                    Text("Mostrar insignia de independiente", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Switch(
                    checked = isIndependent,
                    onCheckedChange = { isIndependent = it }
                )
            }
        }
        
        item {
            Button(
                onClick = {
                    onSave(user.copy(
                        name = name,
                        bikeModel = bikeModel,
                        bikeSpecs = bikeSpecs,
                        bikeYear = bikeYear,
                        bikeColor = bikeColor,
                        bikeStatus = bikeStatus,
                        isIndependent = isIndependent
                    ))
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text("Guardar Cambios")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileHeader(
    user: UserEntity,
    roleInfo: Triple<String, String, String?>?,
    onImageSelected: (String) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let { onImageSelected(it.toString()) }
        }
    )

    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable {
                        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center
            ) {
                val profileUri = user.profilePictureUri
                Log.d("ProfileScreen", "ProfileHeader loading URI: $profileUri")
                if (!profileUri.isNullOrBlank() && profileUri != "null") {
                    AsyncImage(
                        model = profileUri,
                        contentDescription = "Foto de perfil",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        onError = { state ->
                            Log.e("ProfileScreen", "Error cargando perfil: ${state.result.throwable.message}")
                        },
                        error = remember { androidx.compose.ui.graphics.painter.ColorPainter(Color.LightGray) }
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(50.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(bottom = 4.dp),
                        tint = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name, 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Nivel: ${user.level}", 
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary, 
                        fontWeight = FontWeight.Medium
                    )
                    if (roleInfo == null && !user.isIndependent && user.clubName.isNotBlank() && user.clubName != "Independiente") {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• ${user.clubName}", 
                            style = MaterialTheme.typography.bodySmall, 
                            color = Color.Gray,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (roleInfo != null) {
                val (role, groupName, groupPhoto) = roleInfo
                val rankStyle = when (role) {
                    "Presidente" -> Triple(Color(0xFFFFD700), Icons.Default.MilitaryTech, "Líder de Club")
                    "Vicepresidente" -> Triple(Color(0xFFC0C0C0), Icons.Default.Star, "Mando Directivo")
                    "Sargento de Armas" -> Triple(Color(0xFFB22222), Icons.Default.Security, "Disciplina")
                    "Secretario" -> Triple(Color(0xFF4682B4), Icons.Default.Description, "Administración")
                    "Tesorero" -> Triple(Color(0xFF2E8B57), Icons.Default.MonetizationOn, "Finanzas")
                    "Capitán de Ruta" -> Triple(Color(0xFFFF8C00), Icons.Default.Map, "Navegación")
                    "Prospecto" -> Triple(Color(0xFF808080), Icons.Default.NewReleases, "En Prueba")
                    else -> Triple(MaterialTheme.colorScheme.primary, Icons.Default.Shield, "Miembro")
                }

                Surface(
                    color = rankStyle.first.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, rankStyle.first),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (groupPhoto == null) rankStyle.first else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            if (groupPhoto != null) {
                                AsyncImage(
                                    model = groupPhoto,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    rankStyle.second,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = role.uppercase(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = rankStyle.first,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                text = groupName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (user.isIndependent) {
                // Estilo para el Motero Independiente (Aparece siempre que el toggle esté activo)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f)),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "INDEPENDIENTE",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBikeSection(user: UserEntity, navController: NavController, onImageSelected: (String) -> Unit) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val garageBikesWithPhotos by db.bikeDao().getAllBikesWithPhotos().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val appwrite = remember { AppwriteManager.getInstance(context) }
    val authManager = remember { AuthManager(context) }
    
    var showAddBikeDialog by remember { mutableStateOf(false) }
    var bikeToEdit by remember { mutableStateOf<com.example.motoday.data.local.entities.BikeEntity?>(null) }
    var selectedBikeIndex by remember { mutableIntStateOf(0) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Mi Garaje", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = { showAddBikeDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Añadir moto")
                }
            }
            
            // Switcher de Motos (Pills)
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (garageBikesWithPhotos.isEmpty()) {
                    item {
                        val isSelected = selectedBikeIndex == 0
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedBikeIndex = 0 },
                            label = { Text(user.bikeModel.ifBlank { "Mi Moto" }) },
                            leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) } } else null
                        )
                    }
                } else {
                    items(garageBikesWithPhotos.size) { index ->
                        val isSelected = selectedBikeIndex == index
                        val bike = garageBikesWithPhotos[index].bike
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedBikeIndex = index },
                            label = { Text(bike.model) },
                            leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) } } else null
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Mostrar la moto seleccionada
        item {
            if (garageBikesWithPhotos.isEmpty()) {
                val mainBikeImages = remember(user.bikePictureUri) {
                    listOfNotNull(user.bikePictureUri).filter { it.isNotBlank() }
                }
                BikeCard(
                    model = user.bikeModel,
                    specs = user.bikeSpecs,
                    year = user.bikeYear,
                    color = user.bikeColor,
                    status = user.bikeStatus,
                    images = mainBikeImages,
                    onClick = { 
                        bikeToEdit = com.example.motoday.data.local.entities.BikeEntity(
                            id = 0,
                            model = user.bikeModel,
                            specs = user.bikeSpecs,
                            year = user.bikeYear,
                            color = user.bikeColor,
                            status = user.bikeStatus,
                            bikePictureUri = user.bikePictureUri
                        )
                    },
                    onImageClick = { onImageSelected(it) }
                )
            } else if (selectedBikeIndex < garageBikesWithPhotos.size) {
                val bikeWithPhotos = garageBikesWithPhotos[selectedBikeIndex]
                val bike = bikeWithPhotos.bike
                
                // Consolidar fotos: Foto principal + Fotos de la galería
                val photos = remember(bikeWithPhotos) {
                    val list = mutableListOf<String>()
                    if (!bike.bikePictureUri.isNullOrBlank()) list.add(bike.bikePictureUri!!)
                    list.addAll(bikeWithPhotos.photos.map { it.uri }.filter { it.isNotBlank() })
                    list.distinct().filter { it.isNotBlank() && it != "null" }
                }
                
                key(bike.id) {
                    Column {
                        BikeCard(
                            model = bike.model,
                            specs = bike.specs,
                            year = bike.year,
                            color = bike.color,
                            status = bike.status,
                            images = photos,
                            onClick = { bikeToEdit = bike },
                            onImageClick = { uriString ->
                                scope.launch {
                                    try {
                                        val uri = uriString.toUri()
                                        
                                        // Persistir permiso para la galería
                                        try {
                                            if (uri.scheme == "content") {
                                                context.contentResolver.takePersistableUriPermission(
                                                    uri,
                                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                )
                                            }
                                        } catch (e: Exception) {
                                            Log.d("ProfileScreen", "No se pudo persistir permiso galeria: ${e.message}")
                                        }

                                        val inputStream = context.contentResolver.openInputStream(uri)
                                        val bytes = inputStream?.readBytes() ?: return@launch
                                        inputStream.close()

                                        val fileName = "bike_${bike.id}_${System.currentTimeMillis()}.jpg"
                                        val fileId = appwrite.uploadImage(
                                            io.appwrite.models.InputFile.fromBytes(bytes = bytes, filename = fileName, mimeType = "image/jpeg"),
                                            AppwriteManager.BUCKET_BIKES_ID
                                        )
                                        val remoteUrl = appwrite.getImageUrl(fileId, AppwriteManager.BUCKET_BIKES_ID)
                                        
                                        // Si la moto no tiene foto principal, asignarle esta
                                        if (bike.bikePictureUri.isNullOrBlank() || bike.bikePictureUri == "null") {
                                            val updatedBike = bike.copy(bikePictureUri = remoteUrl)
                                            db.bikeDao().insertOrUpdate(updatedBike)
                                            bike.remoteId?.let { rid ->
                                                appwrite.updateRemoteBike(
                                                    bikeId = rid,
                                                    model = bike.model,
                                                    year = bike.year,
                                                    color = bike.color,
                                                    specs = bike.specs,
                                                    status = bike.status,
                                                    currentKm = bike.currentKm,
                                                    picId = fileId
                                                )
                                            }
                                        }

                                        // Sincronizar relación en Appwrite para la galería
                                        bike.remoteId?.let { remoteId ->
                                            appwrite.syncBikePhoto(remoteId, fileId)
                                        }

                                        db.bikePhotoDao().insertPhoto(
                                            com.example.motoday.data.local.entities.BikePhotoEntity(
                                                bikeId = bike.id, remoteId = fileId, uri = remoteUrl
                                            )
                                        )
                                        Toast.makeText(context, "Foto añadida!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Log.e("ProfileScreen", "Error subiendo foto: ${e.message}")
                                    }
                                }
                            }
                        )

                        // Botón para establecer como principal si no lo es
                        val isPrincipal = bike.model == user.bikeModel && bike.specs == user.bikeSpecs && bike.year == user.bikeYear
                        if (!isPrincipal) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val latestUser = db.userDao().getUserProfileOnce()
                                        if (latestUser != null) {
                                            val finalBikePic = bike.bikePictureUri?.takeIf { it.isNotBlank() && it != "null" } 
                                                ?: photos.firstOrNull()

                                            val userToSave = latestUser.copy(
                                                bikeModel = bike.model,
                                                bikeSpecs = bike.specs,
                                                bikeYear = bike.year,
                                                bikeColor = bike.color,
                                                bikeStatus = bike.status,
                                                bikePictureUri = finalBikePic
                                            )
                                            db.userDao().insertOrUpdate(userToSave)
                                            
                                            val userId = authManager.getCurrentUserId()
                                            if (userId != null) {
                                                val bikePicId = appwrite.extractFileIdFromUrl(finalBikePic)
                                                appwrite.updateUserProfile(
                                                    userId = userId,
                                                    name = userToSave.name,
                                                    level = userToSave.level,
                                                    bikeModel = userToSave.bikeModel,
                                                    bikeSpecs = userToSave.bikeSpecs,
                                                    bikeYear = userToSave.bikeYear,
                                                    bikeColor = userToSave.bikeColor,
                                                    totalKm = userToSave.totalKilometers,
                                                    rides = userToSave.ridesCompleted,
                                                    isIndependent = userToSave.isIndependent,
                                                    bikePic = bikePicId
                                                )
                                            }
                                            Toast.makeText(context, "Moto principal actualizada", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Usar como principal")
                            }
                        } else {
                            Row(
                                modifier = Modifier.align(Alignment.End).padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Stars, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Moto principal", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { navController.navigate(Screen.Maintenance.route) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Build, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hoja de Vida y Mantenimientos")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showAddBikeDialog) {
        AddBikeDialog(
            onDismiss = { showAddBikeDialog = false },
            onSave = { newBike ->
                scope.launch {
                    val userId = authManager.getCurrentUserId()
                    if (userId != null) {
                        val remoteId = appwrite.syncBike(
                            userId = userId,
                            model = newBike.model,
                            year = newBike.year,
                            color = newBike.color,
                            specs = newBike.specs,
                            status = newBike.status,
                            currentKm = newBike.currentKm,
                            picId = null
                        )
                        db.bikeDao().insertOrUpdate(newBike.copy(remoteId = remoteId))
                        Toast.makeText(context, "Moto añadida al garaje", Toast.LENGTH_SHORT).show()
                    }
                    showAddBikeDialog = false
                }
            }
        )
    }

    bikeToEdit?.let { bike ->
        EditBikeDialog(
            bike = bike,
            onDismiss = { bikeToEdit = null },
            onDelete = {
                scope.launch {
                    if (bike.id != 0) {
                        // 1. Obtener todas las fotos asociadas para borrarlas de Appwrite (Archivos y Documentos)
                        val photos = db.bikePhotoDao().getPhotosForBike(bike.id).first()
                        bike.remoteId?.let { remoteBikeId ->
                            try {
                                val remotePhotoDocs = appwrite.getBikePhotos(remoteBikeId)
                                remotePhotoDocs.forEach { doc ->
                                    val fileId = doc.data["fileId"] as? String
                                    if (fileId != null) {
                                        appwrite.deleteFile(fileId, AppwriteManager.BUCKET_BIKES_ID)
                                    }
                                    appwrite.deleteBikePhotoDocument(doc.id)
                                }
                            } catch (e: Exception) {
                                Log.e("ProfileScreen", "Error eliminando fotos remotas: ${e.message}")
                            }
                        }
                        
                        // 2. Borrar la moto de Appwrite (esto borra el documento en COLLECTION_GARAGE_ID)
                        bike.remoteId?.let { appwrite.deleteRemoteBike(it) }
                        
                        // 3. Borrar de la base de datos local (Cascade borrará las fotos en Room)
                        db.bikeDao().delete(bike)
                        selectedBikeIndex = 0
                        Toast.makeText(context, "Moto y sus fotos eliminadas", Toast.LENGTH_SHORT).show()
                    }
                    bikeToEdit = null
                }
            },
            onSave = { updatedBike ->
                scope.launch {
                    if (bike.id == 0) {
                        val latestUser = db.userDao().getUserProfileOnce()
                        if (latestUser != null) {
                            val userToSave = latestUser.copy(
                                bikeModel = updatedBike.model,
                                bikeYear = updatedBike.year,
                                bikeColor = updatedBike.color,
                                bikeSpecs = updatedBike.specs,
                                bikeStatus = updatedBike.status
                            )
                            db.userDao().insertOrUpdate(userToSave)
                            
                            val userId = authManager.getCurrentUserId()
                            if (userId != null) {
                                val profilePicId = appwrite.extractFileIdFromUrl(userToSave.profilePictureUri)
                                val bikePicId = appwrite.extractFileIdFromUrl(userToSave.bikePictureUri)
                                appwrite.updateUserProfile(
                                    userId = userId,
                                    name = userToSave.name,
                                    level = userToSave.level,
                                    bikeModel = userToSave.bikeModel,
                                    bikeSpecs = userToSave.bikeSpecs,
                                    bikeYear = userToSave.bikeYear,
                                    bikeColor = userToSave.bikeColor,
                                    isIndependent = userToSave.isIndependent,
                                    profilePic = profilePicId,
                                    bikePic = bikePicId
                                )
                            }
                        }
                    } else {
                        val bikePicId = appwrite.extractFileIdFromUrl(updatedBike.bikePictureUri)
                        db.bikeDao().insertOrUpdate(updatedBike)
                        updatedBike.remoteId?.let { rid ->
                            appwrite.updateRemoteBike(
                                bikeId = rid,
                                model = updatedBike.model,
                                year = updatedBike.year,
                                color = updatedBike.color,
                                specs = updatedBike.specs,
                                status = updatedBike.status,
                                currentKm = updatedBike.currentKm,
                                picId = bikePicId
                            )
                        }
                    }
                    Toast.makeText(context, "Datos actualizados", Toast.LENGTH_SHORT).show()
                    bikeToEdit = null
                }
            }
        )
    }
}

@Composable
fun EditBikeDialog(
    bike: com.example.motoday.data.local.entities.BikeEntity,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (com.example.motoday.data.local.entities.BikeEntity) -> Unit
) {
    var model by remember { mutableStateOf(bike.model) }
    var year by remember { mutableStateOf(bike.year) }
    var color by remember { mutableStateOf(bike.color) }
    var specs by remember { mutableStateOf(bike.specs) }
    var currentKm by remember { mutableStateOf(bike.currentKm.toString()) }
    var status by remember { mutableStateOf(bike.status) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("¿Eliminar permanentemente?") },
            text = { Text("Esta acción eliminará la moto y todas sus fotos tanto del teléfono como de la nube de forma definitiva. No podrás deshacer este cambio.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar Todo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Moto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Modelo") }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Año") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = color, onValueChange = { color = it }, label = { Text("Color") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = specs, onValueChange = { specs = it }, label = { Text("Cilindraje / Specs") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = currentKm, onValueChange = { currentKm = it }, label = { Text("Kilometraje Actual") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                OutlinedTextField(value = status, onValueChange = { status = it }, label = { Text("Estado") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(bike.copy(model = model, year = year, color = color, specs = specs, status = status, currentKm = currentKm.toIntOrNull() ?: bike.currentKm))
            }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            Row {
                if (bike.id != 0) {
                    TextButton(onClick = { showDeleteConfirm = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("Eliminar")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BikeCard(
    model: String,
    specs: String,
    year: String,
    color: String,
    status: String,
    images: List<String>,
    onClick: () -> Unit,
    onImageClick: (String) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let { onImageClick(it.toString()) }
        }
    )

    val pagerState = rememberPagerState(pageCount = { images.size.coerceAtLeast(1) })

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (images.isNotEmpty()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        AsyncImage(
                            model = images[page],
                            contentDescription = "Foto de la moto ${page + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    // Indicador de añadir más fotos
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable {
                                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }

                    // Indicador de páginas (dots)
                    if (images.size > 1) {
                        Row(
                            Modifier
                                .height(20.dp)
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(images.size) { iteration ->
                                val colorActive = if (pagerState.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.5f)
                                Box(
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(colorActive)
                                        .size(6.dp)
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().clickable {
                            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(40.dp), tint = Color.Gray)
                        Text("Añadir foto", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = model, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = "$specs | $year | $color", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            
            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BikeSpecItem("Motor", specs.take(8))
                BikeSpecItem("Año", year)
                BikeSpecItem("Estado", status)
            }
        }
    }
}

@Composable
fun AddBikeDialog(onDismiss: () -> Unit, onSave: (com.example.motoday.data.local.entities.BikeEntity) -> Unit) {
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var specs by remember { mutableStateOf("") }
    var currentKm by remember { mutableStateOf("0") }
    var status by remember { mutableStateOf("Excelente") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir Moto al Garaje") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Modelo (Ej: Yamaha R3)") }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Año") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = color, onValueChange = { color = it }, label = { Text("Color") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = specs, onValueChange = { specs = it }, label = { Text("Cilindraje / Specs") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = currentKm, onValueChange = { currentKm = it }, label = { Text("Kilometraje Inicial") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                OutlinedTextField(value = status, onValueChange = { status = it }, label = { Text("Estado") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                if (model.isNotBlank()) {
                    onSave(com.example.motoday.data.local.entities.BikeEntity(
                        model = model,
                        year = year,
                        color = color,
                        specs = specs,
                        status = status,
                        currentKm = currentKm.toIntOrNull() ?: 0
                    ))
                }
            }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun BikeSpecItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PassportSection() {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val stamps by db.passportDao().getAllStamps().collectAsState(initial = emptyList())
    
    val uniqueStamps = stamps.distinctBy { it.locationName.lowercase() }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text(text = "Mi Pasaporte de Rutas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text = "Colecciona sellos visitando nuevas ciudades", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        if (uniqueStamps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aún no tienes sellos. ¡Sal a rodar!", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(uniqueStamps) { stamp ->
                    CityStamp(stamp)
                }
            }
        }
    }
}

@Composable
fun CityStamp(stamp: com.example.motoday.data.local.entities.PassportStampEntity) {
    val context = LocalContext.current
    var isVisible by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 3f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(600),
        label = "alpha"
    )
    val rotation by animateFloatAsState(
        targetValue = if (isVisible) 0f else -25f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "rotation"
    )

    // Intentar cargar el recurso de imagen personalizado
    val customResId = remember(stamp.iconResName) {
        context.resources.getIdentifier(stamp.iconResName, "drawable", context.packageName)
    }

    val stampConfig = when (stamp.locationName.lowercase(Locale.getDefault()).trim()) {
        "machala" -> StampStyle(Color(0xFFFFD700), Icons.Default.Agriculture, "MACHALA") 
        "guayaquil" -> StampStyle(Color(0xFF00BFFF), Icons.Default.Anchor, "GUAYAQUIL") 
        "cuenca" -> StampStyle(Color(0xFF8B4513), Icons.Default.Architecture, "CUENCA") 
        "quito" -> StampStyle(Color(0xFFD32F2F), Icons.Default.AccountBalance, "QUITO") 
        "loja" -> StampStyle(Color(0xFF4CAF50), Icons.Default.Park, "LOJA")
        "manta" -> StampStyle(Color(0xFF009688), Icons.Default.DirectionsBoat, "MANTA")
        "ambato" -> StampStyle(Color(0xFFFF5722), Icons.Default.LocalFlorist, "AMBATO")
        else -> StampStyle(
            color = Color(0xFF6200EE), 
            icon = Icons.Default.LocationCity, 
            label = stamp.locationName.uppercase(Locale.getDefault())
        )
    }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Column(
        modifier = Modifier.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    alpha = alpha,
                    rotationZ = rotation
                ),
            contentAlignment = Alignment.Center
        ) {
            if (customResId != 0) {
                // Sello con imagen real desbloqueada
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = customResId),
                    contentDescription = stamp.locationName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Sello genérico (fallback)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color(0xFFFDF5E6))
                        .border(2.dp, stampConfig.color.copy(alpha = 0.4f), CircleShape)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            stampConfig.icon,
                            contentDescription = null,
                            tint = stampConfig.color.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stampConfig.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                shadow = Shadow(color = Color.Black.copy(alpha = 0.15f), offset = Offset(2f, 2f), blurRadius = 3f)
                            ),
                            color = stampConfig.color.copy(alpha = 0.9f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1,
                            fontSize = 9.sp
                        )
                        
                        val dateStr = try {
                            SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(stamp.date))
                        } catch (e: Exception) {
                            "Reciente"
                        }
                        
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = Color.Gray.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

data class StampStyle(val color: Color, val icon: ImageVector, val label: String)

@Composable
fun AchievementsSection(user: UserEntity) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val stamps by db.passportDao().getAllStamps().collectAsState(initial = emptyList())

    LazyColumn(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        item {
            Text(text = "Mis Trofeos y Medallas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(text = "Cumple objetivos para desbloquear recompensas visuales", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            AchievementItem(
                title = "Bautizo de Asfalto",
                desc = "Completa tu primera rodada",
                isUnlocked = user.ridesCompleted >= 1
            )
        }
        item {
            AchievementItem(
                title = "Explorador Regional",
                desc = "Visita 3 ciudades diferentes",
                isUnlocked = stamps.distinctBy { it.locationName.lowercase() }.size >= 3
            )
        }
        item {
            AchievementItem(
                title = "Tragakilómetros",
                desc = if (user.useMiles) "Suma más de 310 millas totales" else "Suma más de 500km totales",
                isUnlocked = user.totalKilometers >= 500
            )
        }
        item {
            AchievementItem(
                title = "Leyenda de la Carretera",
                desc = "Visita 10 ciudades diferentes",
                isUnlocked = stamps.distinctBy { it.locationName.lowercase() }.size >= 10
            )
        }
    }
}

@Composable
fun StatsSection(user: UserEntity) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val stamps by db.passportDao().getAllStamps().collectAsState(initial = emptyList())
    val rides by db.rideDao().getAllRides().collectAsState(initial = emptyList())

    LazyColumn(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        item {
            Text(text = "Rendimiento Motero", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val unit = if (user.useMiles) "Mi" else "Kms"
                
                // Calcular KM reales sumando distancias si el campo totalKilometers está desactualizado
                // pero por ahora priorizamos lo que diga el objeto user sincronizado.
                val distance = if (user.useMiles) (user.totalKilometers * 0.621371).toInt() else user.totalKilometers
                Box(modifier = Modifier.weight(1f)) { StatCard(unit, "$distance", Icons.Default.Place, MaterialTheme.colorScheme.primary) }
                
                val completedRidesCount = user.ridesCompleted
                Box(modifier = Modifier.weight(1f)) { StatCard("Rutas", "$completedRidesCount", Icons.Default.CheckCircle, Color(0xFF4CAF50)) }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Historial de Sellos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(stamps.distinctBy { it.rideRemoteId }.size) { index ->
            val stamp = stamps.distinctBy { it.rideRemoteId }[index]
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF00FFFF).copy(alpha = 0.05f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6200EE).copy(alpha = 0.1f))
            ) {
                ListItem(
                    headlineContent = { 
                        Text(
                            stamp.rideTitle, 
                            style = MaterialTheme.typography.bodyMedium, 
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF6200EE)
                        ) 
                    },
                    supportingContent = { Text("${stamp.locationName} - ${sdf.format(Date(stamp.date))}", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { 
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Verified, 
                                contentDescription = null, 
                                tint = Color(0xFFFFB100),
                                modifier = Modifier.size(42.dp)
                            )
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF6200EE),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
fun AchievementItem(title: String, desc: String, isUnlocked: Boolean) {
    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible && isUnlocked) 1f else 0.8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "achScale"
    )

    LaunchedEffect(isUnlocked) {
        if (isUnlocked) isVisible = true
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) Color(0xFF00FFFF).copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.05f)
        )
    ) {
        ListItem(
            headlineContent = { 
                Text(
                    title, 
                    fontWeight = FontWeight.Bold, 
                    color = if (isUnlocked) Color(0xFF6200EE) else Color.Gray
                ) 
            },
            supportingContent = { Text(desc, style = MaterialTheme.typography.bodySmall) },
            leadingContent = { 
                Icon(
                    if (isUnlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isUnlocked) Color(0xFFFFB100) else Color.Gray
                )
            }
        )
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, iconColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = label, style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}
