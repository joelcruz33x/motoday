package com.example.motoday.ui.components

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import java.util.*

enum class MessageStatus {
    SENDING, SENT, READ, ERROR
}

data class ChatMessage(
    val sender: String,
    val message: String,
    val time: String,
    val isMe: Boolean = false,
    val imageUri: String? = null,
    val fileUri: String? = null,
    val fileName: String? = null,
    val isLocation: Boolean = false,
    val id: String = UUID.randomUUID().toString(),
    val status: MessageStatus = MessageStatus.SENT
)

fun isLocationMessage(text: String): Boolean {
    return text.contains("google.com/maps") || 
           text.contains("maps.app.goo.gl") || 
           text.contains("waze.com")
}

@Composable
fun ChatBubble(msg: ChatMessage, showSenderName: Boolean = true) {
    val context = LocalContext.current
    val alignment = if (msg.isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (msg.isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (msg.isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    
    val hasImage = !msg.imageUri.isNullOrBlank() && 
                   msg.imageUri != "null" && 
                   (msg.imageUri.startsWith("http") || msg.imageUri.startsWith("content") || msg.imageUri.startsWith("file"))

    val hasFile = !msg.fileUri.isNullOrBlank() && msg.fileUri != "null"
    val isPlaceholder = msg.message == "📷 Foto" || msg.message == "📄 Documento"
    val hasText = (msg.message.isNotBlank() && !isPlaceholder) || msg.isLocation

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        if (!msg.isMe && showSenderName) {
            Text(
                text = msg.sender,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
            )
        }
        
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (msg.isMe) 16.dp else 4.dp,
                bottomEnd = if (msg.isMe) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            modifier = Modifier
                .widthIn(max = 260.dp)
                .animateContentSize()
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Column {
                    if (hasImage) {
                        AsyncImage(
                            model = msg.imageUri,
                            contentDescription = null,
                            modifier = Modifier
                                .width(260.dp)
                                .heightIn(max = 300.dp)
                                .clip(if (hasText || hasFile) RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp) else RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (hasFile) {
                        Row(
                            modifier = Modifier
                                .padding(10.dp)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, msg.fileUri?.toUri())
                                    context.startActivity(intent)
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Description, null, tint = textColor, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = msg.fileName ?: "Documento",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("Toca para abrir", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f))
                            }
                        }
                    }

                    if (hasText) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .then(if (hasImage) Modifier.fillMaxWidth() else Modifier)
                        ) {
                            if (msg.isLocation) {
                                Row(
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            val intent = Intent(Intent.ACTION_VIEW, msg.message.toUri())
                                            context.startActivity(intent)
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Map, null, tint = textColor, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Ubicación", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = textColor)
                                }
                            } else {
                                Text(
                                    text = msg.message,
                                    color = textColor,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .padding(top = 2.dp)
                            ) {
                                Text(
                                    text = msg.time,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor.copy(alpha = 0.6f),
                                    fontSize = 11.sp
                                )
                                if (msg.isMe) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    MessageStatusIcon(msg.status, textColor)
                                }
                            }
                        }
                    }
                }

                if (hasImage && !hasText) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = msg.time, color = Color.White, fontSize = 10.sp)
                            if (msg.isMe) {
                                Spacer(modifier = Modifier.width(4.dp))
                                MessageStatusIcon(msg.status, Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInput(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    onImageAttach: () -> Unit,
    onFileAttach: () -> Unit,
    onLocationAttach: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var showAttachMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showAttachMenu = true }) {
                    Icon(
                        Icons.Default.AddCircle,
                        contentDescription = "Adjuntar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                DropdownMenu(
                    expanded = showAttachMenu,
                    onDismissRequest = { showAttachMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Imagen") },
                        onClick = {
                            showAttachMenu = false
                            onImageAttach()
                        },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Documento") },
                        onClick = {
                            showAttachMenu = false
                            onFileAttach()
                        },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Ubicación") },
                        onClick = {
                            showAttachMenu = false
                            onLocationAttach()
                        },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
                    )
                }
            }

            TextField(
                value = messageText,
                onValueChange = onMessageChange,
                placeholder = { Text("Escribe un mensaje...") },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp)),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                maxLines = 4
            )
            
            if (messageText.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = onSend,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Enviar", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun MessageStatusIcon(status: MessageStatus, baseColor: Color) {
    val (icon, tint) = when (status) {
        MessageStatus.SENDING -> Icons.Default.Schedule to baseColor.copy(alpha = 0.5f)
        MessageStatus.SENT -> Icons.Default.Check to baseColor.copy(alpha = 0.7f)
        MessageStatus.READ -> Icons.Default.DoneAll to Color(0xFF00BFFF)
        MessageStatus.ERROR -> Icons.Default.Error to MaterialTheme.colorScheme.error
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(13.dp),
        tint = tint
    )
}
