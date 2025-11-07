package com.example.pavamanconfiguratorgcs.ui.fullparams

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pavamanconfiguratorgcs.data.models.EditState
import com.example.pavamanconfiguratorgcs.data.models.LoadingProgress
import com.example.pavamanconfiguratorgcs.ui.fullparams.components.CompactParameterRow
import com.example.pavamanconfiguratorgcs.ui.fullparams.components.CompactToolbar

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

    // Auto-load parameters when screen opens
    LaunchedEffect(Unit) {
        viewModel.fetchParameters()
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Full Parameter List", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2C2C2C),
                    titleContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF1E1E1E)
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Compact toolbar with search and buttons (Mission Planner style)
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

            // Loading progress
            when (val progress = loadingProgress) {
                is LoadingProgress.Loading -> {
                    LinearProgressIndicator(
                        progress = { if (progress.total > 0) progress.current.toFloat() / progress.total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF4CAF50)
                    )
                }
                else -> {}
            }

            // Table header (Mission Planner style)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF2C2C2C)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Name", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(0.25f))
                    Text("Value", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(0.15f))
                    Text("Default", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(0.15f))
                    Text("Units", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(0.1f))
                    Text("Description", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(0.3f))
                    Spacer(modifier = Modifier.width(40.dp)) // For action buttons
                }
            }

            Divider(color = Color(0xFF404040), thickness = 1.dp)

            // Parameters list (compact table rows)
            if (parameters.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (loadingProgress is LoadingProgress.Loading) {
                            CircularProgressIndicator(color = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Loading parameters from flight controller...",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        } else {
                            Text(
                                "No parameters loaded",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    items(items = parameters, key = { it.name }) { parameter ->
                        CompactParameterRow(
                            parameter = parameter,
                            isPending = pendingEdits.containsKey(parameter.name),
                            onEdit = { viewModel.editParameter(parameter.name, it) },
                            onSave = { viewModel.saveParameter(parameter.name) },
                            onDiscard = { viewModel.discardEdit(parameter.name) }
                        )
                        Divider(color = Color(0xFF2C2C2C), thickness = 0.5.dp)
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard all changes?") },
            text = { Text("This will discard all pending edits.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.discardAllEdits()
                    showDiscardDialog = false
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Cancel") }
            }
        )
    }
}
