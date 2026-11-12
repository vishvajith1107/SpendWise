package com.spendwise.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.data.repository.CategoryRepository
import com.spendwise.domain.model.AppCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoriesUiState(
    val categories: List<AppCategory> = emptyList(),
    val editingCategory: AppCategory? = null,
    val showAddDialog: Boolean = false,
    val statusMessage: String? = null
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _editingCategory = MutableStateFlow<AppCategory?>(null)
    private val _showAddDialog = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CategoriesUiState> = combine(
        categoryRepository.getAllCategories(),
        _editingCategory,
        _showAddDialog,
        _statusMessage
    ) { categories, editing, showAdd, status ->
        CategoriesUiState(
            categories = categories,
            editingCategory = editing,
            showAddDialog = showAdd,
            statusMessage = status
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoriesUiState()
    )

    fun openAddDialog() {
        _editingCategory.value = null
        _showAddDialog.value = true
    }

    fun startEditing(category: AppCategory) {
        _showAddDialog.value = false
        _editingCategory.value = category
    }

    fun dismissDialog() {
        _showAddDialog.value = false
        _editingCategory.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun createCategory(name: String, colorHex: Long, iconKey: String) {
        viewModelScope.launch {
            categoryRepository.createCustomCategory(name, colorHex, iconKey)
            _statusMessage.value = "Added category '$name'"
            dismissDialog()
        }
    }

    fun updateCategory(category: AppCategory, name: String, colorHex: Long, iconKey: String) {
        viewModelScope.launch {
            categoryRepository.saveCategory(
                category.copy(
                    displayName = name.trim().take(32),
                    colorHex = colorHex,
                    iconKey = iconKey
                )
            )
            _statusMessage.value = "Updated '$name'"
            dismissDialog()
        }
    }

    fun deleteCustomCategory(category: AppCategory) {
        if (category.isBuiltIn) return
        viewModelScope.launch {
            categoryRepository.deleteCustomCategory(category.key)
            _statusMessage.value = "Deleted '${category.displayName}' (existing entries moved to Other)"
            dismissDialog()
        }
    }

    fun resetBuiltInDefaults() {
        viewModelScope.launch {
            categoryRepository.resetBuiltInDefaults()
            _statusMessage.value = "Reset built-in categories to defaults"
        }
    }
}
