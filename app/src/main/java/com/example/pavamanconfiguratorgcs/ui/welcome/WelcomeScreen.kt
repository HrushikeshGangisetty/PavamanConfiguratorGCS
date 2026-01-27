package com.example.pavamanconfiguratorgcs.ui.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.pavamanconfiguratorgcs.R
import kotlinx.coroutines.delay

@Composable
fun WelcomeScreen(
    onNavigateToConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Navigate to connection screen after 1 second
    LaunchedEffect(Unit) {
        delay(1000L) // 1 second delay
        onNavigateToConnection()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Full screen welcome image
        Image(
            painter = painterResource(id = R.drawable.welcome),
            contentDescription = "Welcome",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

