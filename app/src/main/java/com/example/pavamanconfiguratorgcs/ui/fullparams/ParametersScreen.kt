package com.example.pavamanconfiguratorgcs.ui.fullparams

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pavamanconfiguratorgcs.data.models.EditState
import com.example.pavamanconfiguratorgcs.data.models.LoadingProgress
import com.example.pavamanconfiguratorgcs.ui.fullparams.components.CompactParameterRow
import com.example.pavamanconfiguratorgcs.ui.fullparams.components.CompactToolbar
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParametersScreen(
    viewModel: ParametersViewModel,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val parameters by viewModel.parameters.collectAsState()
    val pendingEdits by viewModel.pendingEdits.collectAsState()
    val loadingProgress by viewModel.loadingProgress.collectAsState()
    val editState by viewModel.editState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()

    var showDiscardDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Theme colors matching HomeScreen
    val backgroundColor = Color(0xFF0A0E21)
    val cardColor = Color(0xFF1C2541)
    val accentColor = Color(0xFF00D4AA)
    val waveColor = Color(0xFF1E3A5F)
    val headerColor = Color(0xFF1C2541)

    // No longer auto-fetching on screen open - parameters are loaded once at app scope
    // User can manually refresh using the refresh button if needed

    LaunchedEffect(editState) {
        when (val state = editState) {
            is EditState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearEditState()
            }
            is EditState.Error -> {
                snackbarHostState.showSnackbar(state.message)
            }
            else -> {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Wave background decoration
        ParametersWaveBackground(
            waveColor = waveColor,
            modifier = Modifier.fillMaxSize()
        )

        // Star decorations
        ParametersStarDecorations()

        Scaffold(
            topBar = {
                Surface(
                    color = headerColor,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "FULL PARAMETERS",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                            Text(
                                "${parameters.size} parameters loaded",
                                fontSize = 11.sp,
                                color = accentColor
                            )
                        }
                        if (hasUnsavedChanges) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "Unsaved",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Compact toolbar with search and buttons
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    CompactToolbar(
                        searchQuery = searchQuery,
                        onSearchChange = { viewModel.searchParameters(it) },
                        onRefresh = { viewModel.fetchParameters() },
                        onSaveToFile = { /* Future: export to file */ },
                        onLoadFromFile = { /* Future: import from file */ },
                        onWriteParams = {
                            if (hasUnsavedChanges) viewModel.saveAllPendingEdits()
                        },
                        onRefreshParams = { viewModel.fetchParameters() },
                        onCompareParams = { /* Future: compare feature */ },
                        hasUnsavedChanges = hasUnsavedChanges,
                        paramCount = parameters.size,
                        isLoading = loadingProgress is LoadingProgress.Loading,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Loading progress
                when (val progress = loadingProgress) {
                    is LoadingProgress.Loading -> {
                        LinearProgressIndicator(
                            progress = { if (progress.total > 0) progress.current.toFloat() / progress.total else 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            color = accentColor,
                            trackColor = cardColor
                        )
                    }
                    else -> {}
                }

                // Table header
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Name", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentColor, modifier = Modifier.weight(0.25f))
                        Text("Value", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentColor, modifier = Modifier.weight(0.15f))
                        Text("Units", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentColor, modifier = Modifier.weight(0.15f))
                        Text("Description", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentColor, modifier = Modifier.weight(0.4f))
                        Spacer(modifier = Modifier.width(40.dp))
                    }
                }

                // Parameters list
                if (parameters.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardColor.copy(alpha = 0.8f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                if (loadingProgress is LoadingProgress.Loading) {
                                    CircularProgressIndicator(color = accentColor)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "Loading parameters...",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.List,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "No parameters loaded",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Tap refresh to load",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(items = parameters, key = { it.name }) { parameter ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (pendingEdits.containsKey(parameter.name))
                                        accentColor.copy(alpha = 0.1f)
                                    else
                                        cardColor.copy(alpha = 0.7f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                CompactParameterRow(
                                    parameter = parameter,
                                    isPending = pendingEdits.containsKey(parameter.name),
                                    onEdit = { viewModel.editParameter(parameter.name, it) },
                                    onSave = { viewModel.saveParameter(parameter.name) },
                                    onDiscard = { viewModel.discardEdit(parameter.name) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard all changes?", color = Color.White) },
            text = { Text("This will discard all pending edits.", color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.discardAllEdits()
                    showDiscardDialog = false
                }) { Text("Discard", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Cancel", color = accentColor) }
            },
            containerColor = cardColor
        )
    }
}

/**
 * Wave background for parameters screen
 */
@Composable
fun ParametersWaveBackground(
    waveColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveOffset"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val waveHeight = height * 0.1f
        val baseY = height * 0.85f

        for (i in 0..4) {
            val path = Path()
            val offsetY = i * 10f
            val alpha = (0.25f - i * 0.04f).coerceAtLeast(0.05f)

            path.moveTo(0f, baseY + offsetY)

            var x = 0f
            while (x <= width) {
                val y = baseY + offsetY + sin((x / width * 3 + animatedOffset / 60f + i * 0.5f).toDouble()).toFloat() * waveHeight * 0.3f
                path.lineTo(x, y)
                x += 5f
            }

            drawPath(
                path = path,
                color = waveColor.copy(alpha = alpha),
                style = Stroke(width = 1.5f)
            )
        }
    }
}

/**
 * Star decorations for parameters screen
 */
@Composable
fun ParametersStarDecorations() {
    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    val starAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "starAlpha"
    )

    val starPositions = listOf(
        Offset(0.92f, 0.15f),
        Offset(0.08f, 0.25f),
        Offset(0.95f, 0.45f),
        Offset(0.05f, 0.65f)
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val starColor = Color.White

        starPositions.forEachIndexed { index, pos ->
            val x = size.width * pos.x
            val y = size.height * pos.y
            val alpha = if (index % 2 == 0) starAlpha else 1f - starAlpha

            val starSize = 5f
            val path = Path().apply {
                moveTo(x, y - starSize)
                lineTo(x, y + starSize)
                moveTo(x - starSize, y)
                lineTo(x + starSize, y)
            }

            drawPath(
                path = path,
                color = starColor.copy(alpha = alpha.coerceIn(0.1f, 0.6f)),
                style = Stroke(width = 1f)
            )
        }
    }
}
