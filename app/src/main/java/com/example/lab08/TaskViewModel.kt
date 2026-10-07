package com.example.lab08

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TaskViewModel(private val dao: TaskDao) : ViewModel() {

    // Lista completa de tareas desde Room
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks

    // Filtro por estado: "Todas", "Pendientes", "Completadas"
    private val _filterType = MutableStateFlow("Todas")
    val filterType: StateFlow<String> = _filterType

    // Filtro por categoría: "Todas", "General", "Trabajo", "Estudio", "Hogar"
    private val _selectedCategory = MutableStateFlow("Todas")
    val selectedCategory: StateFlow<String> = _selectedCategory

    // Búsqueda por texto
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Estado de la sincronización en la nube
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    init {
        loadTasks()
    }

    private fun loadTasks() {
        viewModelScope.launch {
            _tasks.value = dao.getAllTasks()
        }
    }

    fun setFilterType(type: String) {
        _filterType.value = type
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Agregar nueva tarea con categoría, prioridad e intervalo de repetición
    fun addTask(
        description: String,
        priority: String = "Media",
        category: String = "General",
        repeatInterval: String = "Ninguna"
    ) {
        val newTask = Task(
            description = description,
            priority = priority,
            category = category,
            repeatInterval = repeatInterval,
            isSynced = false
        )
        viewModelScope.launch {
            dao.insertTask(newTask)
            loadTasks()
        }
    }

    // Editar tarea existente
    fun editTask(
        task: Task,
        newDescription: String,
        newPriority: String,
        newCategory: String,
        newRepeatInterval: String
    ) {
        val updatedTask = task.copy(
            description = newDescription,
            priority = newPriority,
            category = newCategory,
            repeatInterval = newRepeatInterval,
            isSynced = false
        )
        viewModelScope.launch {
            dao.updateTask(updatedTask)
            loadTasks()
        }
    }

    // Alternar estado de completado (Soporte para tareas recurrentes)
    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            val newCompletedState = !task.isCompleted
            val updatedTask = task.copy(isCompleted = newCompletedState, isSynced = false)
            dao.updateTask(updatedTask)

            // Si la tarea se completa y es recurrente (Diaria o Semanal), se genera automáticamente la nueva tarea recurrente
            if (newCompletedState && task.repeatInterval != "Ninguna") {
                val nextRecurringTask = Task(
                    description = "${task.description} (${task.repeatInterval})",
                    priority = task.priority,
                    category = task.category,
                    repeatInterval = task.repeatInterval,
                    isCompleted = false,
                    isSynced = false
                )
                dao.insertTask(nextRecurringTask)
            }

            loadTasks()
        }
    }

    // Simular Sincronización en la Nube
    fun syncWithCloud() {
        viewModelScope.launch {
            _isSyncing.value = true
            delay(1500) // Simular tiempo de respuesta de red
            val currentList = dao.getAllTasks()
            currentList.forEach { task ->
                if (!task.isSynced) {
                    dao.updateTask(task.copy(isSynced = true))
                }
            }
            _isSyncing.value = false
            loadTasks()
        }
    }

    // Eliminar una tarea individual
    fun deleteTask(task: Task) {
        viewModelScope.launch {
            dao.deleteTask(task)
            loadTasks()
        }
    }

    // Eliminar todas las tareas
    fun deleteAllTasks() {
        viewModelScope.launch {
            dao.deleteAllTasks()
            _tasks.value = emptyList()
        }
    }
}
