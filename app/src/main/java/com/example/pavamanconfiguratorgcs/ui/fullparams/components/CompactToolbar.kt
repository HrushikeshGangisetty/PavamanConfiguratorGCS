package com.example.pavamanconfiguratorgcs.ui.fullparams.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CompactToolbar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onSaveToFile: () -> Unit,
    onLoadFromFile: () -> Unit,
    onWriteParams: () -> Unit,
    onRefreshParams: () -> Unit,
    onCompareParams: () -> Unit,
    hasUnsavedChanges: Boolean,
    paramCount: Int,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF2C2C2C)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: Compact search box using BasicTextField
            Row(
                modifier = Modifier
                    .width(200.dp)
                    .height(36.dp)
                    .background(Color(0xFF000000))
                    .border(2.dp, Color(0xFF00FF00))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF00FF00)
                )
                Spacer(modifier = Modifier.width(4.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        fontSize = 12.sp,
                        color = Color(0xFF00FF00),
                        fontWeight = FontWeight.Bold
                    ),
                    cursorBrush = SolidColor(Color(0xFF00FF00)),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                "Search...",
                                fontSize = 12.sp,
                                color = Color(0xFF006600)
                            )
                        }
                        innerTextField()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchChange("") },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear",
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF00FF00)
                        )
                    }
                }
            }

            // Center: Parameter count
            Text(
                text = "$paramCount parameters",
                fontSize = 12.sp,
                color = Color(0xFFAAAAAA),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Right side: Action buttons (Mission Planner style)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Write Params button (highlighted when changes exist)
                Button(
                    onClick = onWriteParams,
                    enabled = hasUnsavedChanges,
                    modifier = Modifier.height(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasUnsavedChanges) Color(0xFF7CB342) else Color(0xFF505050),
                        disabledContainerColor = Color(0xFF505050)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Write", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                // Refresh Params button - Always visible
                Button(
                    onClick = onRefreshParams,
                    enabled = true, // Always enabled
                    modifier = Modifier.height(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Refresh", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
