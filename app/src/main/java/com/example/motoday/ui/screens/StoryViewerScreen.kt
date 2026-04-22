package com.example.motoday.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.appwrite.models.Document
import kotlinx.coroutines.delay

@Composable
fun StoryViewerScreen(
    stories: List<Document<Map<String, Any>>>,
    onClose: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    var progress by remember { mutableStateOf(0f) }
    val currentStory = stories.getOrNull(currentIndex)
    
    // Control para pausar/reiniciar el temporizador cuando se toca
    var isPaused by remember { mutableStateOf(false) }

    LaunchedEffect(currentIndex, isPaused) {
        if (isPaused) return@LaunchedEffect
        
        val duration = 5000L // 5 segundos
        val steps = 100
        val stepDuration = duration / steps
        val startProgress = (progress * steps).toInt()
        
        for (i in startProgress..steps) {
            if (isPaused) break
            delay(stepDuration)
            progress = i / steps.toFloat()
        }
        
        if (!isPaused) {
            if (currentIndex < stories.size - 1) {
                currentIndex++
                progress = 0f
            } else {
                onClose()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Imagen de la historia
        if (currentStory != null) {
            AsyncImage(
                model = currentStory.data["imageUrl"] as? String,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // Detectores de clics (Izquierda para atrás, Derecha para adelante)
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { 
                                isPaused = true
                                tryAwaitRelease()
                                isPaused = false
                            },
                            onTap = {
                                if (currentIndex > 0) {
                                    currentIndex--
                                    progress = 0f
                                }
                            }
                        )
                    }
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { 
                                isPaused = true
                                tryAwaitRelease()
                                isPaused = false
                            },
                            onTap = {
                                if (currentIndex < stories.size - 1) {
                                    currentIndex++
                                    progress = 0f
                                } else {
                                    onClose()
                                }
                            }
                        )
                    }
            )
        }

        // Barras de progreso superiores (Encima de los detectores de clics)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            stories.forEachIndexed { index, _ ->
                LinearProgressIndicator(
                    progress = when {
                        index < currentIndex -> 1f
                        index == currentIndex -> progress
                        else -> 0f
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }

        // Información del usuario y botón cerrar (Encima de todo)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 50.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentStory?.data?.get("userName") as? String ?: "Motero",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(48.dp) // Área de clic generosa
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
        }
    }
}
