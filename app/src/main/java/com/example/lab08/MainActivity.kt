package com.example.lab08

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import kotlinx.coroutines.launch
import com.example.lab08.ui.theme.Lab08Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel(this)

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

// 🔔 Crear canal de notificaciones (Requerido para Android 8.0+)
private fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "Recordatorio de Tareas"
        val descriptionText = "Notificaciones para recordar tareas pendientes"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel("TASK_REMINDER_CHANNEL", name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

// 🔔 Función para enviar notificación local
private fun sendTaskNotification(context: Context, taskDescription: String) {
    val builder = NotificationCompat.Builder(context, "TASK_REMINDER_CHANNEL")
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle("Recordatorio de Tarea 📌")
        .setContentText("¡No olvides completar: \"$taskDescription\"!")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)

    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(viewModel: TaskViewModel) {
    val context = LocalContext.current
    val tasks by viewModel.tasks.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val sortType by viewModel.sortType.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var newTaskDescription by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf("Media") }
    var newCategory by remember { mutableStateOf("General") }
    var newRepeatInterval by remember { mutableStateOf("Ninguna") }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }

    val categoriesList = listOf("Todas", "General", "Trabajo", "Estudio", "Hogar")
    val sortOptions = listOf("Creación", "Nombre", "Estado")

    // Launcher para pedir permiso de notificaciones en Android 13+
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Filtrado por estado, categoría y búsqueda
    val filteredTasks = tasks.filter { task ->
        val matchesStatus = when (filterType) {
            "Pendientes" -> !task.isCompleted
            "Completadas" -> task.isCompleted
            else -> true
        }
        val matchesCategory = if (selectedCategory == "Todas") true else task.category == selectedCategory
        val matchesSearch = task.description.contains(searchQuery, ignoreCase = true)
        matchesStatus && matchesCategory && matchesSearch
    }

    // 🔀 Ordenamiento de tareas
    val sortedTasks = when (sortType) {
        "Nombre" -> filteredTasks.sortedBy { it.description.lowercase() }
        "Estado" -> filteredTasks.sortedBy { it.isCompleted }
        else -> filteredTasks.sortedBy { it.id } // Creación
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Cabecera con Botón de Sincronización en la Nube
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Gestor de Tareas",
                style = MaterialTheme.typography.headlineSmall
            )

            Button(
                onClick = { viewModel.syncWithCloud() },
                enabled = !isSyncing
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("☁️ Nube")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 🔍 Barra de Búsqueda
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            label = { Text("Buscar tarea...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 🎯 Filtros de Estado
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

        // 🏷️ Filtros de Categoría
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            categoriesList.forEach { cat ->
                FilterChip(
                    selected = (selectedCategory == cat),
                    onClick = { viewModel.setSelectedCategory(cat) },
                    label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // 🔀 Opciones de Ordenamiento
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text("Ordenar:", style = MaterialTheme.typography.labelSmall)
            sortOptions.forEach { sort ->
                FilterChip(
                    selected = (sortType == sort),
                    onClick = { viewModel.setSortType(sort) },
                    label = { Text(sort, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // ➕ Agregar Nueva Tarea
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

                Spacer(modifier = Modifier.height(6.dp))

                // Prioridad
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Prioridad:", style = MaterialTheme.typography.bodySmall)
                    listOf("Alta", "Media", "Baja").forEach { p ->
                        FilterChip(
                            selected = (selectedPriority == p),
                            onClick = { selectedPriority = p },
                            label = { Text(p, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Categoría
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Categoría:", style = MaterialTheme.typography.bodySmall)
                    listOf("General", "Trabajo", "Estudio", "Hogar").forEach { c ->
                        FilterChip(
                            selected = (newCategory == c),
                            onClick = { newCategory = c },
                            label = { Text(c, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Recurrencia
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Repetir:", style = MaterialTheme.typography.bodySmall)
                    listOf("Ninguna", "Diaria", "Semanal").forEach { r ->
                        FilterChip(
                            selected = (newRepeatInterval == r),
                            onClick = { newRepeatInterval = r },
                            label = { Text(r, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Button(
                    onClick = {
                        if (newTaskDescription.isNotBlank()) {
                            viewModel.addTask(
                                description = newTaskDescription,
                                priority = selectedPriority,
                                category = newCategory,
                                repeatInterval = newRepeatInterval
                            )
                            sendTaskNotification(context, newTaskDescription)
                            newTaskDescription = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Text("Agregar Tarea")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 📋 Lista de Tareas
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sortedTasks, key = { it.id }) { task ->
                TaskItem(
                    task = task,
                    onToggle = { viewModel.toggleTaskCompletion(task) },
                    onEdit = { taskToEdit = task },
                    onDelete = { viewModel.deleteTask(task) },
                    onNotify = { sendTaskNotification(context, task.description) }
                )
            }
        }

        // 🗑️ Eliminar todas
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

        // Mostrar el diálogo de edición si hay una tarea seleccionada
        taskToEdit?.let { task ->
            EditTaskDialog(
                task = task,
                onDismiss = { taskToEdit = null },
                onConfirm = { desc, priority, category, repeat ->
                    viewModel.editTask(task, desc, priority, category, repeat)
                    taskToEdit = null
                }
            )
        }
    }
}

@Composable
fun TaskItem(
    task: Task,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onNotify: () -> Unit
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
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "P: ${task.priority}",
                            style = MaterialTheme.typography.labelSmall,
                            color = when (task.priority) {
                                "Alta" -> MaterialTheme.colorScheme.error
                                "Baja" -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                        Text(
                            text = "🏷️ ${task.category}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (task.repeatInterval != "Ninguna") {
                            Text(
                                text = "🔄 ${task.repeatInterval}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Text(
                            text = if (task.isSynced) "☁️ Sincronizado" else "☁️ Pendiente",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (task.isSynced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNotify) {
                        Text("🔔")
                    }
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
}

@Composable
fun EditTaskDialog(
    task: Task,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var editedDesc by remember { mutableStateOf(task.description) }
    var editedPriority by remember { mutableStateOf(task.priority) }
    var editedCategory by remember { mutableStateOf(task.category) }
    var editedRepeat by remember { mutableStateOf(task.repeatInterval) }

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

                // Prioridad
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Prioridad:", style = MaterialTheme.typography.bodySmall)
                    listOf("Alta", "Media", "Baja").forEach { p ->
                        FilterChip(
                            selected = (editedPriority == p),
                            onClick = { editedPriority = p },
                            label = { Text(p, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Categoría
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Categoría:", style = MaterialTheme.typography.bodySmall)
                    listOf("General", "Trabajo", "Estudio", "Hogar").forEach { c ->
                        FilterChip(
                            selected = (editedCategory == c),
                            onClick = { editedCategory = c },
                            label = { Text(c, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Recurrencia
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Repetir:", style = MaterialTheme.typography.bodySmall)
                    listOf("Ninguna", "Diaria", "Semanal").forEach { r ->
                        FilterChip(
                            selected = (editedRepeat == r),
                            onClick = { editedRepeat = r },
                            label = { Text(r, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (editedDesc.isNotBlank()) {
                        onConfirm(editedDesc, editedPriority, editedCategory, editedRepeat)
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
