package com.example.appcolegios.perfil

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcolegios.R
import com.example.appcolegios.demo.DemoData
import com.example.appcolegios.data.UserPreferencesRepository
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import android.content.Intent
import com.example.appcolegios.auth.RegisterActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(profileViewModel: ProfileViewModel = viewModel(), navController: NavController? = null) {
    val studentResult by profileViewModel.student.collectAsState(initial = null)
    val teacherResult by profileViewModel.teacherState.collectAsState(initial = null)
    val isDemo = DemoData.isDemoUser()

    val context = LocalContext.current
    val userPrefs = UserPreferencesRepository(context)
    val userDataState = userPrefs.userData.collectAsState(initial = com.example.appcolegios.data.UserData(null, null, null)).value
    val isAdmin = (userDataState.role ?: "").equals("ADMIN", ignoreCase = true)
    val isDocente = (userDataState.role ?: "").equals("DOCENTE", ignoreCase = true)

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val userPrefsRepo = UserPreferencesRepository(LocalContext.current)

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(contentPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isAdmin) {
                // Vista simplificada para administradores
                AdminCard(onOpenAdmin = {
                    navController?.navigate(com.example.appcolegios.navigation.AppRoutes.Admin.route)
                }, onCreateUser = {
                    val intent = Intent(context, RegisterActivity::class.java)
                    intent.putExtra("fromAdmin", true)
                    context.startActivity(intent)
                })
            } else {
                if (isDocente) {
                    // Mostrar formulario editable para docentes
                    when (val t = teacherResult) {
                        null -> {
                            // cargando o sin datos: permitir editar campos vacíos
                            TeacherCard(profileViewModel = profileViewModel, initial = null, snackbarHostState = snackbarHostState, coroutineScope = coroutineScope, userPrefsRepo = userPrefsRepo)
                        }
                        else -> {
                            t.onSuccess { teacher ->
                                TeacherCard(profileViewModel = profileViewModel, initial = teacher, snackbarHostState = snackbarHostState, coroutineScope = coroutineScope, userPrefsRepo = userPrefsRepo)
                            }.onFailure { e ->
                                // Mostrar error y permitir crear/editar
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f))) {
                                    Text(text = stringResource(R.string.error_label) + ": " + (e.message ?: ""), modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                                }
                                TeacherCard(profileViewModel = profileViewModel, initial = null, snackbarHostState = snackbarHostState, coroutineScope = coroutineScope, userPrefsRepo = userPrefsRepo)
                            }
                        }
                    }
                } else {
                    when (val result = studentResult) {
                        null -> {
                            if (isDemo) {
                                StudentCard(student = DemoData.demoStudent())
                            } else {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        else -> {
                            result.onSuccess { student ->
                                val data = student ?: if (isDemo) DemoData.demoStudent() else null
                                if (data != null) {
                                    StudentCard(student = data)
                                } else {
                                    Text(
                                        text = stringResource(R.string.no_student_data),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }.onFailure { e ->
                                if (isDemo) {
                                    StudentCard(student = DemoData.demoStudent())
                                } else {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                        ),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = stringResource(R.string.error_label) + ": " + (e.message ?: ""),
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherCard(
    profileViewModel: ProfileViewModel,
    initial: com.example.appcolegios.perfil.TeacherProfile?,
    snackbarHostState: SnackbarHostState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    userPrefsRepo: UserPreferencesRepository
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.nombre ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var photoUrl by remember { mutableStateOf(initial?.photoUrl ?: "") }
    var isUploading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    // Leer la prefs como estado composable (no dentro de coroutines)
    val currentUserData by userPrefsRepo.userData.collectAsState(initial = com.example.appcolegios.data.UserData(null, null, null))
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            // subir foto y actualizar photoUrl
            isUploading = true
            profileViewModel.uploadPhoto(uri) { url ->
                isUploading = false
                if (url != null) {
                    photoUrl = url
                    coroutineScope.launch { snackbarHostState.showSnackbar("Foto subida correctamente") }
                } else {
                    coroutineScope.launch { snackbarHostState.showSnackbar("No se pudo subir la foto") }
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = "Perfil Docente", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            // Avatar / picker
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.LightGray)
                    .clickable { launcher.launch("image/*") }, contentAlignment = Alignment.Center) {
                    if (photoUrl.isBlank()) {
                        Text(text = "Foto", textAlign = TextAlign.Center)
                    } else {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Teléfono") }, singleLine = true)
                }
            }

            Spacer(Modifier.height(12.dp))
            // email no editable (proviene de Auth)
            Text(text = "Email: ${profileViewModel.getCurrentUserEmail() ?: ""}", style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Button(onClick = {
                    // Mostrar progreso de guardado y actualizar DataStore
                    isSaving = true
                    profileViewModel.saveTeacherProfile(if (name.isBlank()) null else name, if (phone.isBlank()) null else phone, if (photoUrl.isBlank()) null else photoUrl)
                    coroutineScope.launch {
                        try {
                            // Solo actualizar DataStore si existe un userId (evita eliminar el userId accidentalmente)
                            if (!currentUserData.userId.isNullOrBlank()) {
                                userPrefsRepo.updateUserData(currentUserData.userId, currentUserData.role, if (name.isBlank()) null else name)
                            }
                        } catch (_: Exception) { }
                        isSaving = false
                        snackbarHostState.showSnackbar("Perfil guardado")
                    }
                }) {
                    if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Text("Guardar")
                }
            }
        }
    }
}

@Composable
private fun AdminCard(onOpenAdmin: () -> Unit, onCreateUser: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Panel de Administración",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            Text(text = "Acciones disponibles para administradores:")
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onOpenAdmin) { Text("Abrir Panel Admin") }
                Button(onClick = onCreateUser) { Text("Crear usuario") }
            }
        }
    }
}

@Composable
private fun StudentCard(student: com.example.appcolegios.data.model.Student) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = student.nombre,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            ProfileInfoRow(
                label = stringResource(R.string.curso_label),
                value = student.curso
            )
            Spacer(Modifier.height(12.dp))

            ProfileInfoRow(
                label = stringResource(R.string.select_group).replace("Selecciona ", ""),
                value = student.grupo
            )
            Spacer(Modifier.height(12.dp))

            ProfileInfoRow(
                label = stringResource(R.string.promedio_global),
                value = student.promedio.toString()
            )
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
