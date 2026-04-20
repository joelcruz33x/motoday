package com.example.motoday.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.motoday.navigation.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(navController: NavController) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()

    val pages = listOf(
        OnboardingPage(
            title = "Bienvenido a MotoDay",
            description = "La red social definitiva para amantes de las dos ruedas. Comparte tu pasión y vive la aventura.",
            icon = Icons.Default.TwoWheeler,
            color = Color(0xFF6200EE)
        ),
        OnboardingPage(
            title = "Rutas y Crónicas",
            description = "Explora nuevas rutas, registra tus viajes y colecciona sellos en tu pasaporte motero.",
            icon = Icons.Default.Explore,
            color = Color(0xFF03DAC6)
        ),
        OnboardingPage(
            title = "Comunidad Activa",
            description = "Únete a grupos, asiste a eventos y mantente en contacto con otros moteros de tu zona.",
            icon = Icons.Default.Groups,
            color = Color(0xFFFF9800)
        ),
        OnboardingPage(
            title = "Mantenimiento y Seguridad",
            description = "Controla el estado de tu moto y viaja tranquilo con nuestro sistema SOS inteligente.",
            icon = Icons.Default.Build,
            color = Color(0xFFE91E63)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { position ->
            OnboardingView(pages[position])
        }

        // Indicadores y Botones
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Indicador de puntos
            Row(
                Modifier
                    .height(50.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(4) { iteration ->
                    val color = if (pagerState.currentPage == iteration) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(if (pagerState.currentPage == iteration) 12.dp else 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botón Principal
            Button(
                onClick = {
                    if (pagerState.currentPage < 3) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (pagerState.currentPage == 3) "¡Empezar ahora!" else "Siguiente",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (pagerState.currentPage < 3) {
                TextButton(
                    onClick = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Omitir", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun OnboardingView(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(page.color.copy(alpha = 0.3f), Color.Transparent)
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = page.color
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = page.title,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = page.description,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = Color.Gray,
            lineHeight = 24.sp
        )
    }
}

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
)
