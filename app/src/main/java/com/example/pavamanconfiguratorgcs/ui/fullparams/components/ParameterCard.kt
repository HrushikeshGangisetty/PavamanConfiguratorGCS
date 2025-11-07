package com.example.pavamanconfiguratorgcs.ui.fullparams.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pavamanconfiguratorgcs.data.models.Parameter

@Composable
fun ParameterCard(
    parameter: Parameter,
    isPending: Boolean,
    onEdit: (String) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf(parameter.getValueAsString()) }
    var isExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(parameter.value) {
        if (!isEditing) {
            editValue = parameter.getValueAsString()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isPending -> MaterialTheme.colorScheme.secondaryContainer
                parameter.isDirty -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPending) 4.dp else 1.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (parameter.isDirty) {
                        // Small colored dot instead of relying on specific icon availability
                        Text(text = "●", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Column {
                        Text(text = parameter.name, style = MaterialTheme.typography.titleMedium, fontWeight = if (parameter.isDirty) FontWeight.Bold else FontWeight.Normal)
                        if (parameter.group != "Other") {
                            Text(text = "Group: ${parameter.group}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.size(32.dp)) {
                    // Use simple caret characters to avoid missing icon vectors
                    Text(text = if (isExpanded) "▲" else "▼", fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (isEditing) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(Color(0xFF000000))
                            .border(2.dp, Color(0xFF00FF00))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = editValue,
                            onValueChange = { editValue = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 14.sp,
                                color = Color(0xFF00FF00),
                                fontWeight = FontWeight.Bold
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF00FF00)),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    onEdit(editValue)
                                    isEditing = false
                                }
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { onEdit(editValue); isEditing = false }) {
                        Icon(Icons.Default.Check, contentDescription = "Confirm", tint = Color(0xFF4CAF50))
                    }
                    IconButton(onClick = { editValue = parameter.getValueAsString(); isEditing = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFFF44336))
                    }
                } else {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = parameter.getValueAsString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (parameter.isDirty) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface)
                        if (parameter.units.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = parameter.units, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Row {
                        if (isPending) {
                            IconButton(onClick = onSave) { Text("↑", color = Color(0xFF4CAF50), modifier = Modifier.padding(8.dp)) }
                            IconButton(onClick = onDiscard) { Text("↺", color = Color(0xFFF44336), modifier = Modifier.padding(8.dp)) }
                        }
                        IconButton(onClick = { editValue = parameter.getValueAsString(); isEditing = true }, enabled = !parameter.isReadOnly) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit parameter")
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow("Type", parameter.getTypeName())
                    DetailRow("Index", parameter.index.toString())
                    if (parameter.minValue != null || parameter.maxValue != null) {
                        DetailRow("Range", "${parameter.minValue ?: "∞"} to ${parameter.maxValue ?: "∞"}")
                    }
                    if (parameter.isDirty) {
                        DetailRow("Original Value", parameter.originalValue.toString(), valueColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (parameter.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = parameter.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = valueColor)
    }
}
