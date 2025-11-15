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
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

import androidx.compose.ui.layout.ContentScale
import android.util.Base64 as AndroidBase64
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.tasks.await

@Composable
fun ProfileScreen(navController: NavController? = null) {
    val profileViewModel = viewModel<ProfileViewModel>()
     val studentResult by profileViewModel.student.collectAsState(initial = null)
     val teacherResult by profileViewModel.teacherState.collectAsState(initial = null)
     val isDemo = DemoData.isDemoUser()

    val context = LocalContext.current
    val userPrefs = UserPreferencesRepository(context)
    val userDataState = userPrefs.userData.collectAsState(initial = com.example.appcolegios.data.UserData(null, null, null)).value
    val isAdmin = (userDataState.role ?: "").equals("ADMIN", ignoreCase = true)
    val isDocente = (userDataState.role ?: "").equals("DOCENTE", ignoreCase = true)

    // Nuevo: leer rol desde el VM (desde BD)
    val roleFromDb by profileViewModel.roleString.collectAsState(initial = null)
    val roleToShow = roleFromDb ?: userDataState.role

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val userPrefsRepo = UserPreferencesRepository(LocalContext.current)

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { contentPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
                .padding(contentPadding),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mostrar rol visible arriba, para todos los usuarios
            if (!roleToShow.isNullOrBlank()) {
                AssistChip(onClick = {}, label = { Text("Rol: ${roleToShow}") })
                Spacer(Modifier.height(12.dp))
            }

            // Botón para forzar recarga manual desde la UI (útil para pruebas)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    Log.d("ProfileScreen", "Refrescar button clicked")
                    profileViewModel.refreshAllData()
                    // Mostrar snackbar y un Toast para evidenciar visualmente la acción
                    coroutineScope.launch { snackbarHostState.showSnackbar("Recargando datos...") }
                    Toast.makeText(context, "Refrescando datos...", Toast.LENGTH_SHORT).show()
                }) {
                    Text(text = "Refrescar")
                }
            }
            Spacer(Modifier.height(12.dp))

            if (isAdmin) {
                // Vista simplificada para administradores
                AdminCard(onOpenAdmin = {
                    navController?.navigate(com.example.appcolegios.navigation.AppRoutes.Admin.route)
                }, onCreateUser = {
                    val intent = Intent(context, RegisterActivity::class.java)
                    intent.putExtra("fromAdmin", true)
                    context.startActivity(intent)
                })

                Spacer(Modifier.height(12.dp))
                // Botón de administración para rellenar campos de foto desde Base64 (backfill)
                OutlinedButton(onClick = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Iniciando backfill de fotos...")
                        profileViewModel.backfillMissingPhotoUrls { msg ->
                            coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    }
                }) {
                    Text("Backfill fotos")
                }
            } else if (roleToShow?.equals("PADRE", ignoreCase = true) == true) {
                // Perfil para PADRE: mostrar foto/nombre del hijo seleccionado y botón para agregar info
                val children by profileViewModel.children.collectAsState()
                val selectedIndexState by profileViewModel.selectedChildIndex.collectAsState()
                val selectedIndex = selectedIndexState ?: 0
                val selectedChild = children.getOrNull(selectedIndex)

                ParentProfileCard(
                    child = selectedChild,
                    children = children,
                    selectedIndex = selectedIndex,
                    onSelectChild = { idx -> profileViewModel.selectChildAtIndex(idx) },
                    onSaveParentInfo = { childId, infoMap ->
                        val db = FirebaseFirestore.getInstance()
                        db.collection("students").document(childId)
                            .set(mapOf("parentInfo" to infoMap), SetOptions.merge())
                            .addOnSuccessListener {
                                Toast.makeText(context, "Información guardada", Toast.LENGTH_SHORT).show()
                                // refrescar datos del viewmodel
                                profileViewModel.refreshAllData()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Error guardando: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                    }
                )

                Spacer(Modifier.height(12.dp))
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
                    // Comportamiento original: mostrar studentResult directamente
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
    initial: TeacherProfile?,
    snackbarHostState: SnackbarHostState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    userPrefsRepo: UserPreferencesRepository
) {
     var name by remember { mutableStateOf(initial?.nombre ?: "") }
     var phone by remember { mutableStateOf(initial?.phone ?: "") }
     var photoUrl by remember { mutableStateOf(initial?.photoUrl ?: "") }
     var isUploading by remember { mutableStateOf(false) }
     var isSaving by remember { mutableStateOf(false) }
     // Leer la prefs como estado composable (no dentro de coroutines)
     val currentUserData by userPrefsRepo.userData.collectAsState(initial = com.example.appcolegios.data.UserData(null, null, null))
     // Pre-capturamos el contentResolver en el scope composable para no invocar LocalContext.current dentro del callback
     val resolver = LocalContext.current.contentResolver
     // También capturamos el contexto local para usar en ImageRequest y otros callbacks
     val context = LocalContext.current
     val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
         if (uri != null) {
             // subir foto y actualizar photoUrl
             isUploading = true
            profileViewModel.uploadPhotoAsBase64WithResolver(resolver, uri) { url: String?, error: String? ->
                 isUploading = false
                 if (url != null) {
                     photoUrl = url
                     coroutineScope.launch { snackbarHostState.showSnackbar("Foto subida correctamente") }
                 } else {
                     val message = error ?: "No se pudo subir la foto"
                     coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                 }
             }
         }
     }

    LaunchedEffect(initial?.photoUrl) {
        // Actualizar el estado local cuando el initial cambie (por ejemplo tras guardado en Firestore)
        photoUrl = initial?.photoUrl ?: ""
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
                    } else if (photoUrl.startsWith("data:image")) {
                        val decodedBytesState = produceState<ByteArray?>(initialValue = null, photoUrl) {
                            val base64Part = photoUrl.substringAfter(",", "").replace("\\s".toRegex(), "").trim()
                            if (base64Part.isBlank()) { value = null; return@produceState }
                            value = try {
                                withContext(Dispatchers.IO) {
                                    try { AndroidBase64.decode(base64Part, AndroidBase64.NO_WRAP) }
                                    catch (_: IllegalArgumentException) {
                                        try { AndroidBase64.decode(base64Part, AndroidBase64.DEFAULT) } catch (_: Exception) { null }
                                    }
                                }
                            } catch (_: Exception) { null }
                        }
                         if (decodedBytesState.value != null) {
                            val req = ImageRequest.Builder(context).data(decodedBytesState.value).build()
                            AsyncImage(model = req, contentDescription = "Avatar", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Text(text = "Foto", textAlign = TextAlign.Center)
                        }
                    } else {
                        AsyncImage(model = photoUrl, contentDescription = "Avatar", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    // Usar isUploading para mostrar overlay, evitando que la variable quede sin leer
                    if (isUploading) {
                        Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
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
     val profileViewModel: ProfileViewModel = viewModel<ProfileViewModel>()
     val coroutineScope = rememberCoroutineScope()
     val context = LocalContext.current
    // Leer nombre registrado en prefs/Firestore (Firebase) para mostrarlo arriba del curso
    val userPrefsLocal = UserPreferencesRepository(context)
    val currentUserData by userPrefsLocal.userData.collectAsState(initial = com.example.appcolegios.data.UserData(null, null, null))
    val displayName = (currentUserData.name ?: student.nombre).trim()
     var photoUrl by remember { mutableStateOf(student.avatarUrl ?: "") }
     // Pre-capturamos el contentResolver para no invocar APIs composables dentro del callback
     val resolver = LocalContext.current.contentResolver
     val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            profileViewModel.uploadStudentPhotoAsBase64WithResolver(resolver, uri) { url: String?, error: String? ->
                if (url != null) {
                    photoUrl = url
                    coroutineScope.launch { Toast.makeText(context, "Foto subida correctamente", Toast.LENGTH_SHORT).show() }
                } else {
                    val message = error ?: "No se pudo subir la foto"
                    coroutineScope.launch { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
                }
            }
        }
     }

    LaunchedEffect(student.avatarUrl) {
        photoUrl = student.avatarUrl ?: ""
    }

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
            Box(modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color.LightGray)
                .clickable { launcher.launch("image/*") }, contentAlignment = Alignment.Center) {
                if (photoUrl.isBlank()) {
                    Text(text = "Foto", textAlign = TextAlign.Center)
                } else if (photoUrl.startsWith("data:image")) {
                    val decodedBytesState = produceState<ByteArray?>(initialValue = null, photoUrl) {
                        val base64Part = photoUrl.substringAfter(",", "").replace("\\s".toRegex(), "").trim()
                        if (base64Part.isBlank()) { value = null; return@produceState }
                        value = try {
                            withContext(Dispatchers.IO) {
                                try { AndroidBase64.decode(base64Part, AndroidBase64.NO_WRAP) }
                                catch (_: IllegalArgumentException) {
                                    try { AndroidBase64.decode(base64Part, AndroidBase64.DEFAULT) } catch (_: Exception) { null }
                                }
                            }
                        } catch (_: Exception) { null }
                    }
                     if (decodedBytesState.value != null) {
                        val req = ImageRequest.Builder(context).data(decodedBytesState.value).build()
                        AsyncImage(model = req, contentDescription = "Avatar", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                     } else {
                         Text(text = "Foto", textAlign = TextAlign.Center)
                     }
                } else {
                    AsyncImage(model = photoUrl, contentDescription = "Avatar", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Spacer(Modifier.height(16.dp))

            Text(
                text = displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))

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

@Composable
private fun ParentProfileCard(
    child: com.example.appcolegios.data.model.Student?,
    children: List<com.example.appcolegios.data.model.Student>,
    selectedIndex: Int,
    onSelectChild: (Int) -> Unit,
    onSaveParentInfo: (childId: String, info: Map<String, String>) -> Unit
) {
    var showSelectDialog by remember { mutableStateOf(false) }
    var showAddInfoDialog by remember { mutableStateOf(false) }

    // Obtener datos del usuario (padre) desde DataStore y Firestore para mostrar nombre/avatar del padre
    val context = LocalContext.current
    val userPrefsRepo = UserPreferencesRepository(context)
    val currentUserData by userPrefsRepo.userData.collectAsState(initial = com.example.appcolegios.data.UserData(null, null, null))
    var parentAvatar by remember { mutableStateOf<String?>(null) }
    var parentDisplayName by remember { mutableStateOf(currentUserData.name ?: (child?.nombre ?: "Sin estudiante seleccionado")) }

    LaunchedEffect(currentUserData.userId) {
        // actualizar nombre desde prefs
        parentDisplayName = currentUserData.name ?: (child?.nombre ?: "Sin estudiante seleccionado")
        val uid = currentUserData.userId
        if (!uid.isNullOrBlank()) {
            try {
                val doc = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
                if (doc.exists()) {
                    // Preferir campos legibles (photoUrl / avatarUrl), luego los Base64
                    val photoUrl = doc.getString("photoUrl") ?: doc.getString("avatarUrl")
                    if (!photoUrl.isNullOrBlank()) {
                        parentAvatar = photoUrl
                    } else {
                        val pb = doc.getString("photoBase64")
                        val ab = doc.getString("avatarBase64")
                        parentAvatar = when {
                            !pb.isNullOrBlank() -> "data:image/jpeg;base64,$pb"
                            !ab.isNullOrBlank() -> if (ab.startsWith("data:")) ab else "data:image/jpeg;base64,$ab"
                            else -> null
                        }
                    }
                    // Si no hay nombre en prefs, intentar leer displayName del doc
                    if (parentDisplayName.isBlank()) {
                        val nameFromDoc = doc.getString("name") ?: doc.getString("displayName")
                        if (!nameFromDoc.isNullOrBlank()) parentDisplayName = nameFromDoc
                    }
                }
            } catch (_: Exception) {
                // no bloquear UI en caso de error
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Avatar del padre (fallback a avatar del hijo si no existe)
            val displayedAvatar = parentAvatar ?: child?.avatarUrl
            Box(modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color.LightGray), contentAlignment = Alignment.Center) {
                if (displayedAvatar.isNullOrBlank()) {
                    Text(text = "Foto", textAlign = TextAlign.Center)
                } else if (displayedAvatar.startsWith("data:image")) {
                    val decodedBytesState = produceState<ByteArray?>(initialValue = null, displayedAvatar) {
                        val base64Part = displayedAvatar.substringAfter(",", "").replace("\\s".toRegex(), "").trim()
                        if (base64Part.isBlank()) { value = null; return@produceState }
                        value = try {
                            withContext(Dispatchers.IO) {
                                try { AndroidBase64.decode(base64Part, AndroidBase64.NO_WRAP) }
                                catch (_: IllegalArgumentException) {
                                    try { AndroidBase64.decode(base64Part, AndroidBase64.DEFAULT) } catch (_: Exception) { null }
                                }
                            }
                        } catch (_: Exception) { null }
                    }
                    if (decodedBytesState.value != null) {
                        val req = ImageRequest.Builder(context).data(decodedBytesState.value).build()
                        AsyncImage(model = req, contentDescription = "Avatar", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text(text = "Foto", textAlign = TextAlign.Center)
                    }
                } else {
                    AsyncImage(model = displayedAvatar, contentDescription = "Avatar", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(text = parentDisplayName.ifBlank { "Sin estudiante seleccionado" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(text = if (child != null) "Curso: ${child.curso}" else "", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showSelectDialog = true }, modifier = Modifier.weight(1f)) { Text("Seleccionar hijo") }
                // Si no hay hijo seleccionado, abrir el selector para elegir uno; si hay, abrir el formulario
                Button(onClick = { if (child != null) showAddInfoDialog = true else showSelectDialog = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Agregar información")
                }
            }
        }
    }

    // Selección de hijo
    if (showSelectDialog) {
        var sel by remember { mutableStateOf(selectedIndex) }
        AlertDialog(onDismissRequest = { showSelectDialog = false }, title = { Text("Selecciona estudiante") }, text = {
            Column {
                if (children.isEmpty()) Text("No hay hijos asociados") else children.forEachIndexed { idx, c ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { sel = idx }) {
                        RadioButton(selected = sel == idx, onClick = { sel = idx })
                        Spacer(Modifier.width(8.dp))
                        Column { Text(c.nombre, fontWeight = FontWeight.SemiBold); Text("Curso: ${c.curso}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { if (children.isNotEmpty()) onSelectChild(sel); showSelectDialog = false }) { Text("Aceptar") } }, dismissButton = { TextButton(onClick = { showSelectDialog = false }) { Text("Cancelar") } })
    }

    // Dialogo para agregar info
    if (showAddInfoDialog) {
        var allergies by remember { mutableStateOf("") }
        var medications by remember { mutableStateOf("") }
        var contactName by remember { mutableStateOf("") }
        var contactPhone by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddInfoDialog = false },
            title = { Text("Agregar información del hijo") },
            text = {
                Column {
                    OutlinedTextField(value = allergies, onValueChange = { allergies = it }, label = { Text("Alergias") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = medications, onValueChange = { medications = it }, label = { Text("Medicaciones") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = contactName, onValueChange = { contactName = it }, label = { Text("Contacto - Nombre") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = contactPhone, onValueChange = { contactPhone = it }, label = { Text("Contacto - Teléfono") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val childId = child?.id
                    if (childId.isNullOrBlank()) {
                        // nothing selected: simplemente cerrar el diálogo
                        showAddInfoDialog = false
                    } else {
                        val info = mapOf(
                            "allergies" to allergies.trim(),
                            "medications" to medications.trim(),
                            "contactName" to contactName.trim(),
                            "contactPhone" to contactPhone.trim()
                        )
                        onSaveParentInfo(childId, info)
                        showAddInfoDialog = false
                    }
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddInfoDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
