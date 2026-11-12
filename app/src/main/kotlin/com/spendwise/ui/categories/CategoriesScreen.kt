package com.spendwise.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.domain.model.AppCategory
import com.spendwise.ui.components.CategoryEditorDialog
import com.spendwise.ui.components.icon
import com.spendwise.ui.components.tintColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    val customCategories = remember(uiState.categories) {
        uiState.categories.filter { !it.isBuiltIn }
    }
    val builtInCategories = remember(uiState.categories) {
        uiState.categories.filter { it.isBuiltIn }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.resetBuiltInDefaults() }) {
                        Text("Reset")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openAddDialog() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Category") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Tap any category to rename it or customize its icon and color.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (customCategories.isNotEmpty()) {
                item {
                    Text(
                        "Custom Categories",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(customCategories, key = { it.key }) { cat ->
                    CategoryRowCard(
                        category = cat,
                        onClick = { viewModel.startEditing(cat) }
                    )
                }
            }

            item {
                Text(
                    "Default Categories",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            items(builtInCategories, key = { it.key }) { cat ->
                CategoryRowCard(
                    category = cat,
                    onClick = { viewModel.startEditing(cat) }
                )
            }
        }
    }

    if (uiState.showAddDialog) {
        CategoryEditorDialog(
            initialCategory = null,
            onDismiss = { viewModel.dismissDialog() },
            onSave = { name, colorHex, iconKey ->
                viewModel.createCategory(name, colorHex, iconKey)
            }
        )
    }

    uiState.editingCategory?.let { editing ->
        CategoryEditorDialog(
            initialCategory = editing,
            onDismiss = { viewModel.dismissDialog() },
            onSave = { name, colorHex, iconKey ->
                viewModel.updateCategory(editing, name, colorHex, iconKey)
            },
            onDelete = if (!editing.isBuiltIn) {
                { viewModel.deleteCustomCategory(editing) }
            } else null
        )
    }
}

@Composable
private fun CategoryRowCard(
    category: AppCategory,
    onClick: () -> Unit
) {
    val tint = category.tintColor()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = tint.copy(alpha = 0.14f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            category.icon(),
                            contentDescription = category.displayName,
                            tint = tint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(category.displayName, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (category.isBuiltIn) "Built-in • Editable" else "Custom",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
