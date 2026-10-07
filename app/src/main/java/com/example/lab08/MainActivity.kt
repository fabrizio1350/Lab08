package com.example.lab08

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.room.Room
import kotlinx.coroutines.launch
import com.example.lab08.ui.theme.Lab08Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Lab08Theme {
                val db = Room.databaseBuilder(
                    applicationContext,
                    TaskDatabase::class.java,
                    "task_db"
                ).fallbackToDestructiveMigration().build()

                val taskDao = db.taskDao()
                val viewModel = TaskViewModel(taskDao)

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        TaskScreen(viewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(viewModel: TaskViewModel) {
    val tasks by viewModel.tasks.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var newTaskDescription by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf("Media") }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }

    // Filtrado y búsqueda local
    val filteredTasks = tasks.filter { task ->
        val matchesFilter = when (filterType) {
            "Pendientes" -> !task.isCompleted
            "Completadas" -> task.isCompleted
            else -> true
        }
        val matchesSearch = task.description.contains(searchQuery, ignoreCase = true)
        matchesFilter && matchesSearch
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Gestor de Tareas",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 🔍 Barra de Búsqueda
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            label = { Text("Buscar tarea...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 🎯 Filtros (Todas, Pendientes, Completadas)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf("Todas", "Pendientes", "Completadas").forEach { option ->
                FilterChip(
                    selected = (filterType == option),
                    onClick = { viewModel.setFilterType(option) },
                    label = { Text(option) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ➕ Agregar Nueva Tarea con Prioridad
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = newTaskDescription,
                    onValueChange = { newTaskDescription = it },
                    label = { Text("Nueva tarea") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Prioridad:")
                    listOf("Alta", "Media", "Baja").forEach { p ->
                        FilterChip(
                            selected = (selectedPriority == p),
                            onClick = { selectedPriority = p },
                            label = { Text(p) }
                        )
                    }
                }

                Button(
                    onClick = {
                        if (newTaskDescription.isNotBlank()) {
                            viewModel.addTask(newTaskDescription, selectedPriority)
                            newTaskDescription = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Agregar Tarea")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 📋 Lista de Tareas
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredTasks, key = { it.id }) { task ->
                TaskItem(
                    task = task,
                    onToggle = { viewModel.toggleTaskCompletion(task) },
                    onEdit = { taskToEdit = task },
                    onDelete = { viewModel.deleteTask(task) }
                )
            }
        }

        // 🗑️ Eliminar todas las tareas
        if (tasks.isNotEmpty()) {
            Button(
                onClick = { coroutineScope.launch { viewModel.deleteAllTasks() } },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("Eliminar todas las tareas")
            }
        }
    }

    // ✏️ Diálogo de Edición de Tarea
    taskToEdit?.let { task ->
        EditTaskDialog(
            task = task,
            onDismiss = { taskToEdit = null },
            onConfirm = { newDesc, newPriority ->
                viewModel.editTask(task, newDesc, newPriority)
                taskToEdit = null
            }
        )
    }
}

@Composable
fun TaskItem(
    task: Task,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Prioridad: ${task.priority}",
                    style = MaterialTheme.typography.bodySmall,
                    color = when (task.priority) {
                        "Alta" -> MaterialTheme.colorScheme.error
                        "Baja" -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggle) {
                    Text(if (task.isCompleted) "Completada" else "Pendiente")
                }
                TextButton(onClick = onEdit) {
                    Text("Editar")
                }
                TextButton(onClick = onDelete) {
                    Text("Borrar", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun EditTaskDialog(
    task: Task,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var editedDesc by remember { mutableStateOf(task.description) }
    var editedPriority by remember { mutableStateOf(task.priority) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Tarea") },
        text = {
            Column {
                OutlinedTextField(
                    value = editedDesc,
                    onValueChange = { editedDesc = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Prioridad:")
                    listOf("Alta", "Media", "Baja").forEach { p ->
                        FilterChip(
                            selected = (editedPriority == p),
                            onClick = { editedPriority = p },
                            label = { Text(p) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (editedDesc.isNotBlank()) {
                        onConfirm(editedDesc, editedPriority)
                    }
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
