package com.example.pavamanconfiguratorgcs.ui.fullparams.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pavamanconfiguratorgcs.data.models.Parameter

@Composable
fun CompactParameterRow(
    parameter: Parameter,
    isPending: Boolean,
    onEdit: (String) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf(parameter.getValueAsString()) }
    var showDescriptionDialog by remember { mutableStateOf(false) }

    // Debug logging for first few parameters
    LaunchedEffect(parameter.name) {
        if (parameter.index.toInt() < 5) {
            android.util.Log.i("CompactParameterRow", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            android.util.Log.i("CompactParameterRow", "Parameter: ${parameter.name}")
            android.util.Log.i("CompactParameterRow", "Display Name: '${parameter.displayName}'")
            android.util.Log.i("CompactParameterRow", "Value: ${parameter.value}")
            android.util.Log.i("CompactParameterRow", "Units: '${parameter.units}'")
            android.util.Log.i("CompactParameterRow", "Default: ${parameter.defaultValue}")
            android.util.Log.i("CompactParameterRow", "Description: '${parameter.description.take(80)}'")
            android.util.Log.i("CompactParameterRow", "Min: ${parameter.minValue}, Max: ${parameter.maxValue}")
            android.util.Log.i("CompactParameterRow", "Reboot Required: ${parameter.rebootRequired}")
            android.util.Log.i("CompactParameterRow", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        }
    }

    LaunchedEffect(parameter.value) {
        if (!isEditing) {
            editValue = parameter.getValueAsString()
        }
    }

    // Description Dialog
    if (showDescriptionDialog) {
        AlertDialog(
            onDismissRequest = { showDescriptionDialog = false },
            title = {
                Column {
                    Text(
                        text = parameter.displayName.ifEmpty { parameter.name },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (parameter.displayName.isNotEmpty() && parameter.displayName != parameter.name) {
                        Text(
                            text = parameter.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (parameter.description.isNotEmpty()) {
                        Text(
                            text = parameter.description,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = "No description available for this parameter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Additional info
                    if (parameter.units.isNotEmpty()) {
                        Text(
                            text = "Units: ${parameter.units}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (parameter.minValue != null || parameter.maxValue != null) {
                        Text(
                            text = "Range: ${parameter.minValue ?: "∞"} to ${parameter.maxValue ?: "∞"}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDescriptionDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { /* Could expand for details in future */ },
        color = when {
            isPending -> Color(0xFF3A5A3A) // Dark green for pending changes
            parameter.isDirty -> Color(0xFF4A4A2A) // Dark yellow for modified
            else -> Color(0xFF1E1E1E) // Default dark gray
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Name column (without info icon)
            Text(
                text = parameter.name,
                fontSize = 11.sp,
                color = if (parameter.isDirty) Color(0xFFFFEB3B) else Color.White,
                fontWeight = if (parameter.isDirty) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(0.25f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Value column (editable) - Using BasicTextField
            Box(modifier = Modifier.weight(0.15f)) {
                if (isEditing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(Color(0xFF000000))
                            .border(2.dp, Color(0xFF00FF00))
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = editValue,
                            onValueChange = { editValue = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                fontSize = 11.sp,
                                color = Color(0xFF00FF00),
                                fontWeight = FontWeight.Bold
                            ),
                            cursorBrush = SolidColor(Color(0xFF00FF00)),
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
                } else {
                    Text(
                        text = parameter.getValueAsString(),
                        fontSize = 11.sp,
                        color = if (parameter.isDirty) Color(0xFFFFEB3B) else Color(0xFFCCCCCC),
                        fontWeight = if (parameter.isDirty) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isEditing = true },
                        maxLines = 1
                    )
                }
            }

            // Units column
            Text(
                text = parameter.units.ifEmpty { "-" },
                fontSize = 11.sp,
                color = Color(0xFF999999),
                modifier = Modifier.weight(0.15f),
                maxLines = 1
            )

            // Description column with info icon
            Row(
                modifier = Modifier.weight(0.4f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = parameter.description.ifEmpty { "No description" },
                    fontSize = 11.sp,
                    color = Color(0xFF999999),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Info icon in description column
                IconButton(
                    onClick = { showDescriptionDialog = true },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Show description",
                        tint = Color(0xFF64B5F6),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.width(40.dp)
            ) {
                if (isEditing) {
                    IconButton(
                        onClick = {
                            onEdit(editValue)
                            isEditing = false
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Confirm",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            editValue = parameter.getValueAsString()
                            isEditing = false
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else if (isPending) {
                    IconButton(
                        onClick = onSave,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Text("↑", color = Color(0xFF4CAF50), fontSize = 14.sp)
                    }
                    IconButton(
                        onClick = onDiscard,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Text("↺", color = Color(0xFFF44336), fontSize = 14.sp)
                    }
                } else {
                    IconButton(
                        onClick = { isEditing = true },
                        modifier = Modifier.size(20.dp),
                        enabled = !parameter.isReadOnly
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Color(0xFF999999),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
