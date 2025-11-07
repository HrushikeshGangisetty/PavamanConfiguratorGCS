package com.example.pavamanconfiguratorgcs.ui.fullparams.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SearchAndFilterBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedGroup: String?,
    showOnlyDirty: Boolean,
    paramCount: Int,
    onRefresh: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search parameters...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) { Icon(Icons.Default.Clear, contentDescription = "Clear") }
                    }
                },
                singleLine = true
            )

            IconButton(onClick = onRefresh, enabled = !isLoading) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        if (selectedGroup != null || showOnlyDirty || searchQuery.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Filters:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (searchQuery.isNotEmpty()) {
                    FilterChip(selected = true, onClick = { onSearchChange("") }, label = { Text("Search: \"$searchQuery\"") })
                }
                if (selectedGroup != null) {
                    FilterChip(selected = true, onClick = { /* parent handles */ }, label = { Text("Group: $selectedGroup") })
                }
                if (showOnlyDirty) {
                    FilterChip(selected = true, onClick = { /* parent handles */ }, label = { Text("Modified only") })
                }
            }
        }

        if (paramCount > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Showing $paramCount parameter${if (paramCount != 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

