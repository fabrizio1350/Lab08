package com.example.lab08

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TaskViewModel(private val dao: TaskDao) : ViewModel() {

    // Lista completa de tareas desde Room
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks

    // Filtro actual: "Todas", "Pendientes", "Completadas"
    private val _filterType = MutableStateFlow("Todas")
    val filterType: StateFlow<String> = _filterType

    // Texto de búsqueda
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

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

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Agregar nueva tarea con prioridad
    fun addTask(description: String, priority: String = "Media") {
        val newTask = Task(description = description, priority = priority)
        viewModelScope.launch {
            dao.insertTask(newTask)
            loadTasks()
        }
    }

    // Editar la descripción y prioridad de una tarea existente
    fun editTask(task: Task, newDescription: String, newPriority: String) {
        val updatedTask = task.copy(description = newDescription, priority = newPriority)
        viewModelScope.launch {
            dao.updateTask(updatedTask)
            loadTasks()
        }
    }

    // Alternar el estado de completado
    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            val updatedTask = task.copy(isCompleted = !task.isCompleted)
            dao.updateTask(updatedTask)
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
