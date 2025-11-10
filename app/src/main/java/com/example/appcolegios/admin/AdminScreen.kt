@file:Suppress("RedundantQualifierName", "RemoveRedundantQualifierName", "RedundantQualifiedName", "RedundantQualifier", "UNUSED", "unused", "RedundantInitializer")

package com.example.appcolegios.admin

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.text.Normalizer
import kotlinx.coroutines.tasks.await
import com.example.appcolegios.data.UserData
import com.example.appcolegios.data.UserPreferencesRepository
import com.example.appcolegios.data.model.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import com.example.appcolegios.auth.RegisterActivity
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedInputStream
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import org.apache.poi.ss.usermodel.Row
import com.example.appcolegios.data.TestDataInitializer
import androidx.navigation.NavController
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

// Helper local para leer celdas de forma segura
private fun safeCellValue(row: Row, index: Int): String? = row.getCell(index)?.toString()?.trim()?.takeIf { it.isNotBlank() }

@Composable
fun AdminScreen(navController: NavController? = null) {
    val context = LocalContext.current
    val userPrefs = remember { UserPreferencesRepository(context) }
    val userData = userPrefs.userData.collectAsState(initial = UserData(null, null, null)).value
    // Tratar rol nulo o vacío como ADMIN
    val role = if (userData.role.isNullOrBlank()) Role.ADMIN else Role.fromString(userData.role)

    if (role != Role.ADMIN) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No autorizado", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    val scope = rememberCoroutineScope()
    val db = remember { FirebaseFirestore.getInstance() }

    var status by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Preview state for CSV
    var previewRows by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var previewCols by remember { mutableStateOf(0) }
    val PREVIEW_N = 6

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isLoading = true
                status = null
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}

                // Intentar inicializar FirebaseApp secundaria (importer) si existe google-services
                val authImporter: FirebaseAuth? = try {
                    val opts = FirebaseOptions.fromResource(context)
                    val importerApp = if (opts != null) {
                        try {
                            FirebaseApp.getInstance("importer")
                        } catch (_: IllegalStateException) {
                            FirebaseApp.initializeApp(context, opts, "importer")
                            try { FirebaseApp.getInstance("importer") } catch (_: Exception) { null }
                        }
                    } else null
                    if (importerApp != null) FirebaseAuth.getInstance(importerApp) else null
                } catch (_: Exception) { null }

                try {
                    // Detectar por extensión si es CSV o XLSX
                    val name = uri.lastPathSegment ?: ""
                    val isXlsx = name.endsWith(".xlsx", ignoreCase = true) || name.endsWith(".xls", ignoreCase = true)
                    var count = 0
                    if (isXlsx) {
                        // Leer con Apache POI
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            BufferedInputStream(input).use { bis ->
                                val wb = WorkbookFactory.create(bis)
                                val sheet = wb.getSheetAt(0)
                                for (r in 1..sheet.lastRowNum) {
                                    val row = sheet.getRow(r) ?: continue
                                    // Soportar dos formatos:
                                    // Formato completo (9 cols): 0 nombre,1 apellidos,2 tipoDoc,3 numeroDoc,4 celular,5 direccion,6 email,7 pass,8 rol
                                    // Formato compacto (>=5 cols): 0 nombre,1 apellidos,2 tipo,3 email,4 role
                                    val cellCount = row.lastCellNum.toInt()
                                    // Determinar campos usando una expresión cuando sea posible para evitar inicializadores redundantes
                                    val nombre: String
                                    val apellidos: String
                                    val tipo: String
                                    val email: String
                                    val pass: String?
                                    val rol: String
                                    if (cellCount >= 9) {
                                        nombre = row.getCell(0)?.toString()?.trim() ?: ""
                                        apellidos = safeCellValue(row, 1) ?: ""
                                        tipo = safeCellValue(row, 2) ?: ""
                                        email = safeCellValue(row, 6) ?: ""
                                        pass = safeCellValue(row, 7)
                                        val rawRol = safeCellValue(row, 8) ?: ""
                                        rol = if (rawRol.isBlank()) "ESTUDIANTE" else rawRol
                                    } else if (cellCount >= 5) {
                                        nombre = safeCellValue(row, 0) ?: row.getCell(0)?.toString()?.trim() ?: ""
                                        apellidos = safeCellValue(row, 1) ?: ""
                                        tipo = safeCellValue(row, 2) ?: ""
                                        email = safeCellValue(row, 3) ?: ""
                                        pass = if (cellCount > 5) safeCellValue(row, 5) else null
                                        rol = safeCellValue(row, 4) ?: "ESTUDIANTE"
                                    } else {
                                        var mailTmp = ""
                                        for (c in 0 until cellCount) {
                                            val v = row.getCell(c)?.toString()?.trim() ?: ""
                                            if (v.contains("@")) { mailTmp = v; break }
                                        }
                                        email = mailTmp
                                        nombre = row.getCell(0)?.toString()?.trim() ?: ""
                                        apellidos = ""
                                        tipo = ""
                                        pass = null
                                        rol = "ESTUDIANTE"
                                    }
                                    if (email.isBlank() || nombre.isBlank()) continue

                                    val roleParsed = when (rol.uppercase()) {
                                        "PADRE", "PARENT" -> "PADRE"
                                        "DOCENTE", "TEACHER" -> "DOCENTE"
                                        "ADMIN", "ADMINISTRADOR" -> "ADMIN"
                                        else -> "ESTUDIANTE"
                                    }

                                    val coll = when (roleParsed) {
                                        "PADRE" -> "parents"
                                        "DOCENTE" -> "teachers"
                                        "ADMIN" -> "admins"
                                        else -> "students"
                                    }

                                    try {
                                        if (pass != null && authImporter != null) {
                                            // Crear en Auth y guardar con UID
                                            val res = authImporter.createUserWithEmailAndPassword(email, pass).await()
                                            val uid = res.user?.uid ?: db.collection(coll).document().id
                                            val data = hashMapOf(
                                                "nombres" to nombre,
                                                "apellidos" to apellidos,
                                                "email" to email,
                                                "tipo" to tipo,
                                                "role" to roleParsed,
                                                "importedAt" to Timestamp.now()
                                            )
                                            db.collection(coll).document(uid).set(data).await()
                                            // Además, crear documento en la colección "users" para que la app lo encuentre
                                            try {
                                                val displayName = ("$nombre $apellidos").trim()
                                                val userMap = hashMapOf(
                                                    "displayName" to displayName,
                                                    "email" to email,
                                                    "role" to roleParsed
                                                )
                                                db.collection("users").document(uid).set(userMap).await()
                                            } catch (_: Exception) { }
                                            // enviar verificacion de forma asíncrona y esperar
                                            try {
                                                res.user?.sendEmailVerification()?.await()
                                            } catch (_: Exception) { }
                                            // Asegurarse de no dejar al importer autenticado
                                            try { authImporter.signOut() } catch (_: Exception) {}
                                        } else {
                                            // Guardar doc y encolar petición para backend
                                            val data = hashMapOf(
                                                "nombres" to nombre,
                                                "apellidos" to apellidos,
                                                "email" to email,
                                                "tipo" to tipo,
                                                "role" to roleParsed,
                                                "importedAt" to Timestamp.now()
                                            )
                                            db.collection(coll).add(data).await()
                                            db.collection("auth_queue").add(hashMapOf(
                                                "email" to email,
                                                "role" to roleParsed,
                                                "displayName" to ("$nombre $apellidos").trim(),
                                                "requestedAt" to Timestamp.now()
                                            )).await()

                                            // Cambio mínimo: crear también un documento en "users" para que la app tenga acceso al correo
                                            try {
                                                val userMap = hashMapOf(
                                                    "displayName" to ("$nombre $apellidos").trim(),
                                                    "email" to email,
                                                    "role" to roleParsed,
                                                    "importedAt" to Timestamp.now()
                                                )
                                                db.collection("users").add(userMap).await()
                                            } catch (_: Exception) { }

                                            // Intentar enviar email de restablecimiento (si la cuenta ya existe esto ayudará al usuario a establecer contraseña)
                                            try {
                                                val mainAuth = FirebaseAuth.getInstance()
                                                mainAuth.sendPasswordResetEmail(email).await()
                                            } catch (_: Exception) { }
                                        }
                                        count++
                                    } catch (e: Exception) {
                                        // registrar error y continuar
                                    }
                                }
                                wb.close()
                            }
                        }
                    } else {
                        // CSV: usar lector robusto (maneja comillas, BOM y codificación UTF-8/CP1252)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            // detectar BOM / probar UTF-8 primero
                            val possibleCharsets = listOf(Charset.forName("UTF-8"), Charset.forName("windows-1252"))
                            var reader: BufferedReader? = null
                            for (cs in possibleCharsets) {
                                try {
                                    reader = BufferedReader(InputStreamReader(input, cs))
                                    reader.mark(4096)
                                    // try to read first line and reset; if decode okay, break
                                    reader.readLine()
                                    reader.reset()
                                    break
                                } catch (_: Exception) {
                                    try { reader?.close() } catch (_: Exception) {}
                                    reader = null
                                    try { input.reset() } catch (_: Exception) {}
                                }
                            }
                            if (reader == null) reader = BufferedReader(InputStreamReader(input, Charset.forName("UTF-8")))

                            // función auxiliar para parsear línea CSV que respeta comillas
                            fun parseCsvLine(line: String): List<String> {
                                val regex = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()
                                return line.split(regex).map { it.trim().trim('"').let { s -> Normalizer.normalize(s, Normalizer.Form.NFC) } }
                            }

                            // PREVIEW: leer primeras PREVIEW_N líneas y poblar previewRows
                            val tmpRows = mutableListOf<List<String>>()
                            val headerLine = reader.readLine()
                            val isHeader = headerLine != null && (headerLine.contains("email", true) || headerLine.contains("nombre", true) || headerLine.contains("name", true))
                            if (!isHeader && headerLine != null) {
                                tmpRows.add(parseCsvLine(headerLine))
                            }
                            while (tmpRows.size < PREVIEW_N) {
                                val l = reader.readLine() ?: break
                                tmpRows.add(parseCsvLine(l))
                            }
                            previewRows = tmpRows.map { it.map { cell -> cell } }
                            previewCols = previewRows.maxOfOrNull { it.size } ?: 0
                            status = "Archivo cargado: vista previa ${previewRows.size} filas"
                            // additionally keep original import if user chooses to import full file (handled by existing path)
                        }
                    }

                    status = "Se importaron $count registros correctamente."
                } catch (e: Exception) {
                    status = "Error importando: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Panel de Administración",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Tarjetas principales: organizadas 2x2 para mejor presentación
        val btns: List<Pair<String, () -> Unit>> = listOf(
            "Registrar usuario" to {
                val intent = android.content.Intent(context, RegisterActivity::class.java)
                intent.putExtra("fromAdmin", true)
                context.startActivity(intent)
            },
            "Ver perfiles" to {
                navController?.navigate(com.example.appcolegios.navigation.AppRoutes.AdminUsers.route.replace("{mode}", "view"))
            },
            "Gestionar horarios" to {
                navController?.navigate(com.example.appcolegios.navigation.AppRoutes.AdminUsers.route.replace("{mode}", "manage"))
            },
            "Crear evento/Notificación" to {
                navController?.navigate(com.example.appcolegios.navigation.AppRoutes.AdminEventCreate.route)
            }
        )

        Column(Modifier.fillMaxWidth()) {
            for (rowIdx in 0 until 2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (colIdx in 0 until 2) {
                        val idx = rowIdx * 2 + colIdx
                        val label = btns[idx].first
                        val onClick = btns[idx].second
                        val scale by animateFloatAsState(targetValue = 1f, label = "btn_scale_$idx")
                        ElevatedCard(modifier = Modifier.weight(1f).height(96.dp).graphicsLayer(scaleX = scale, scaleY = scale), onClick = onClick) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(label) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // --- CSV Preview & Mapping ---
        var mappedNameCol by remember { mutableStateOf<Int?>(null) }
        var mappedEmailCol by remember { mutableStateOf<Int?>(null) }
        var mappedRoleCol by remember { mutableStateOf<Int?>(null) }

        // Button to open filePicker (existing) - keep it near preview
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { filePicker.launch(arrayOf("text/*","text/csv","text/comma-separated-values","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }) { Text("Seleccionar archivo a previsualizar") }
            if (previewRows.isNotEmpty()) {
                Button(onClick = {
                    // Confirm import using mapping
                    scope.launch {
                        isLoading = true
                        try {
                            // perform import of previewRows using mapped indices
                            var imported = 0
                            for (r in previewRows) {
                                val nameVal = mappedNameCol?.let { r.getOrNull(it) } ?: r.getOrNull(0)
                                val emailVal = mappedEmailCol?.let { r.getOrNull(it) } ?: r.getOrNull(1)
                                val roleVal = mappedRoleCol?.let { r.getOrNull(it) } ?: "ESTUDIANTE"
                                val display = nameVal?.trim() ?: continue
                                val emailS = emailVal?.trim() ?: continue
                                val roleParsed = when (roleVal.trim().uppercase()) {
                                    "PADRE" -> "PADRE"; "DOCENTE" -> "DOCENTE"; "ADMIN" -> "ADMIN"; else -> "ESTUDIANTE"
                                }
                                val coll = when (roleParsed) {
                                    "PADRE" -> "parents"; "DOCENTE" -> "teachers"; "ADMIN" -> "admins"; else -> "students"
                                }
                                db.collection(coll).add(hashMapOf("name" to display, "email" to emailS, "role" to roleParsed, "importedAt" to Timestamp.now())).await()
                                imported++
                            }
                            status = "Importados $imported registros (preview)"
                        } catch (e: Exception) { status = "Error importando preview: ${e.message}" }
                        finally { isLoading = false }
                    }
                }) { Text("Importar previsualizados") }
            }
        }

        if (previewRows.isNotEmpty()) {
            Text("Previsualización (primeras $PREVIEW_N filas)")
            Spacer(Modifier.height(8.dp))
            // header select
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val cols = previewCols
                for (c in 0 until cols) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Col $c", style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { mappedNameCol = c }) { Text("Nombre") }
                            TextButton(onClick = { mappedEmailCol = c }) { Text("Email") }
                            TextButton(onClick = { mappedRoleCol = c }) { Text("Rol") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // mostrar filas
            Column(Modifier.fillMaxWidth()) {
                previewRows.forEachIndexed { ri, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { cell -> Text(cell, modifier = Modifier.weight(1f), maxLines = 1) }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (status != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (status!!.contains("Error"))
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    status!!,
                    modifier = Modifier.padding(16.dp),
                    color = if (status!!.contains("Error"))
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        if (isLoading) {
            CircularProgressIndicator()
        }

        // Botón para inicializar datos de prueba
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Inicializar Datos de Prueba",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Crea usuarios de prueba con información completa:\n" +
                    "• Estudiante: jcamilodiaz7@gmail.com\n" +
                    "• Profesor: hermanitos605@gmail.com\n" +
                    "• Admin: jcamilodiaz777@gmail.com\n" +
                    "• Padre: hermanitos604@gmail.com",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            status = null
                            try {
                                TestDataInitializer.initializeAllTestData()
                                status = "✅ Datos de prueba inicializados correctamente"
                            } catch (e: Exception) {
                                status = "❌ Error: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Inicializar Datos de Prueba")
                }
            }
        }

        // Botón para importar desde CSV/XLSX (comprobar: el filePicker también carga preview)
        Button(
            onClick = { filePicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","text/*","text/csv","text/comma-separated-values")) },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Seleccionar archivo (previsualizar)")
        }

        Text(
            "Formato CSV: tipo,email,nombre,rol\nEjemplo: estudiante,juan@mail.com,Juan Pérez,ESTUDIANTE",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
