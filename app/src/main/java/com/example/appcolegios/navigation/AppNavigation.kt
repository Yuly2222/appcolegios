@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.appcolegios.navigation

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.appcolegios.R
import com.example.appcolegios.academico.AttendanceScreen
import com.example.appcolegios.academico.AnnouncementsScreen
import com.example.appcolegios.academico.CalendarScreen
import com.example.appcolegios.academico.NotesScreen
import com.example.appcolegios.academico.TasksScreen
import com.example.appcolegios.auth.LoginActivity
import com.example.appcolegios.auth.RegisterScreen
import com.example.appcolegios.auth.ResetPasswordScreen
import com.example.appcolegios.auth.SplashScreen
import com.example.appcolegios.dashboard.DashboardScreen
import com.example.appcolegios.home.HomeScreen
import com.example.appcolegios.mensajes.ChatScreen
import com.example.appcolegios.mensajes.ConversationsScreen
import com.example.appcolegios.mensajes.NewMessageScreen
import com.example.appcolegios.notificaciones.NotificationsScreen
import com.example.appcolegios.pagos.PaymentsScreen
import com.example.appcolegios.perfil.ProfileScreen
import com.example.appcolegios.transporte.TransportScreen
import com.example.appcolegios.admin.AdminScreen
import com.example.appcolegios.ubicacion.UbicacionScreen
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcolegios.data.UserPreferencesRepository
import androidx.navigation.NavType
import androidx.navigation.navArgument
import android.widget.Toast
import com.example.appcolegios.auth.VerifyEmailScreen
import com.example.appcolegios.teacher.TeacherHomeScreen
import com.example.appcolegios.student.StudentHomeScreen

@Composable
fun AppNavigation(
    startDestination: String = AppRoutes.Splash.route,
    initialRole: String? = null,
    unreadNotificationsCount: Int = 0,
    unreadMessagesCount: Int = 0
) {
    val navController = rememberNavController()
    val backStackEntry = navController.currentBackStackEntryAsState().value
    val currentDestination = backStackEntry?.destination

    // Mostrar chrome en todas las rutas salvo pantallas de auth/splash
    val authlessRoutes = setOf(
        AppRoutes.Splash.route,
        AppRoutes.Login.route,
        AppRoutes.Register.route,
        AppRoutes.VerifyEmail.route,
        AppRoutes.ResetPassword.route
    )

    val showChrome = currentDestination?.route !in authlessRoutes

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val userPrefs = UserPreferencesRepository(context)
    val userData = userPrefs.userData.collectAsState(initial = com.example.appcolegios.data.UserData(null, null, null)).value
    // Preferir el role inyectado (initialRole) para evitar condiciones de carrera; si no está, usar el de prefs
    val roleString = if (!initialRole.isNullOrBlank()) initialRole else (userData.role ?: "" )
    val isAdmin = roleString.equals("ADMIN", ignoreCase = true)
    val isDocente = roleString.equals("DOCENTE", ignoreCase = true)
    val isStudent = roleString.equals("ESTUDIANTE", ignoreCase = true)

    val bottomItems = if (isAdmin) {
        listOf(
            BottomItem("Admin", AppRoutes.Admin.route, Icons.Filled.Dashboard),
            BottomItem(stringResource(R.string.profile), AppRoutes.Profile.route, Icons.Filled.Person)
        )
    } else {
        listOf(
            BottomItem(stringResource(R.string.home), AppRoutes.Home.route, Icons.Filled.Home),
            BottomItem(stringResource(R.string.messages), AppRoutes.Messages.route, Icons.AutoMirrored.Filled.Message),
            BottomItem(stringResource(R.string.notifications), AppRoutes.Notifications.route, Icons.Filled.Notifications),
            BottomItem(stringResource(R.string.profile), AppRoutes.Profile.route, Icons.Filled.Person)
        )
    }

    // Badges en tiempo real desde Firestore
    val badgesVm: BadgesViewModel = viewModel()
    val badgesState = badgesVm.state.collectAsState().value

    val notifCount = if (badgesState.unreadNotifications > 0) badgesState.unreadNotifications else unreadNotificationsCount
    val msgCount = if (badgesState.unreadMessages > 0) badgesState.unreadMessages else unreadMessagesCount

    val content: @Composable () -> Unit = {
        NavHost(navController = navController, startDestination = startDestination) {
            composable(AppRoutes.Splash.route) { SplashScreen(navController) }
            composable(AppRoutes.Login.route) {
                val localContext = LocalContext.current
                LaunchedEffect(Unit) {
                    val intent = Intent(localContext, LoginActivity::class.java)
                    localContext.startActivity(intent)
                    if (localContext is Activity) {
                        localContext.finish()
                    }
                }
                // Puedes mostrar una pantalla vacía o de carga mientras se lanza la actividad
                Box(modifier = Modifier.fillMaxSize()) {}
            }
            composable(AppRoutes.Register.route) {
                RegisterScreen(
                    onRegisterSuccess = {
                        // Mostrar mensaje de éxito y navegar a verificación de correo
                        Toast.makeText(context, "Registro exitoso. Verifica tu correo.", Toast.LENGTH_SHORT).show()
                        navController.navigate(AppRoutes.VerifyEmail.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToLogin = { navController.popBackStack() }
                )
            }
            // Nueva ruta para registro desde Admin (solo crea documento en Firestore)
            composable(AppRoutes.RegisterAdmin.route) {
                RegisterScreen(
                    onRegisterSuccess = {
                        Toast.makeText(context, "Usuario creado correctamente", Toast.LENGTH_SHORT).show()
                        // Al crear desde admin solo volver al home del admin
                        navController.navigate(AppRoutes.Home.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToLogin = { navController.popBackStack() },
                    createOnly = true
                )
            }
            composable(AppRoutes.ResetPassword.route) {
                ResetPasswordScreen(
                    onPasswordResetSent = { navController.popBackStack() },
                    onNavigateToLogin = { navController.popBackStack() }
                )
            }
            composable(AppRoutes.Home.route) { HomeScreen(navController = navController) }
            composable(AppRoutes.Profile.route) { ProfileScreen() }
            composable(AppRoutes.Payments.route) { PaymentsScreen() }
            composable(AppRoutes.Transport.route) { TransportScreen() }
            composable(AppRoutes.Notes.route) {
                if (isDocente) {
                    com.example.appcolegios.academico.TeacherNotesScreen(navController = navController)
                } else {
                    NotesScreen()
                }
            }
            composable(AppRoutes.Attendance.route) { AttendanceScreen() }
            composable(AppRoutes.Announcements.route) { AnnouncementsScreen(navController) }
            composable(AppRoutes.Tasks.route) { TasksScreen() }
            // Ruta para calificar (Cursos -> Estudiantes -> Notas)
            composable(AppRoutes.Grading.route) { com.example.appcolegios.academico.GradingScreen() }
            composable(AppRoutes.Notifications.route) { NotificationsScreen() }
            composable(AppRoutes.Messages.route) { ConversationsScreen(navController = navController) }
            composable(AppRoutes.Calendar.route) { CalendarScreen() }
            composable(AppRoutes.Admin.route) { AdminScreen() }
            composable(AppRoutes.Dashboard.route) { DashboardScreen() }
            composable(AppRoutes.TeacherHome.route) { TeacherHomeScreen(navController) }
            composable(AppRoutes.StudentHome.route) { StudentHomeScreen(navController = navController) }
            // Ruta Ubicación del colegio
            composable(AppRoutes.Ubicacion.route) { UbicacionScreen() }
            composable(
                route = AppRoutes.Chat.route,
                arguments = listOf(navArgument("otherUserId") { type = NavType.StringType })
            ) { backStackEntry2 ->
                val otherUserId = backStackEntry2.arguments?.getString("otherUserId") ?: ""
                ChatScreen(navController = navController, otherUserId = otherUserId)
            }
            composable(AppRoutes.NewMessage.route) {
                NewMessageScreen(
                    onUserSelected = { userId ->
                        navController.navigate("chat/$userId")
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(AppRoutes.VerifyEmail.route) {
                VerifyEmailScreen(onDone = { navController.navigate(AppRoutes.Splash.route) {
                    popUpTo(0) { inclusive = true }
                } }, onVerified = { dest ->
                    navController.navigate(dest) {
                        popUpTo(0) { inclusive = true }
                    }
                })
            }
        }
    }

    if (!showChrome) {
        // Rutas de auth/splash sin chrome
        content()
    } else {
        // Drawer lateral funcional con opciones
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Menú",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleLarge
                    )
                    HorizontalDivider()

                    // Opción Home / Admin
                    if (isAdmin) {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.Dashboard, contentDescription = null) },
                            label = { Text("Panel Admin") },
                            selected = currentDestination?.route == AppRoutes.Admin.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(AppRoutes.Admin.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    } else {
                        // Opción Home para usuarios no-admin (restaurada: menú completo)
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                            label = { Text(stringResource(R.string.home)) },
                            selected = currentDestination?.route == AppRoutes.Home.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(AppRoutes.Home.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        // Mensajes
                        NavigationDrawerItem(
                            icon = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null) },
                            label = { Text(stringResource(R.string.messages)) },
                            selected = currentDestination?.route == AppRoutes.Messages.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(AppRoutes.Messages.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        // Notificaciones
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                            label = { Text(stringResource(R.string.notifications)) },
                            selected = currentDestination?.route == AppRoutes.Notifications.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(AppRoutes.Notifications.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        // Pagos
                        if (!isStudent && !isDocente) {
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Filled.CreditCard, contentDescription = null) },
                                label = { Text(stringResource(R.string.payments)) },
                                selected = currentDestination?.route == AppRoutes.Payments.route,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate(AppRoutes.Payments.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }

                        // Transporte
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.DirectionsBus, contentDescription = null) },
                            label = { Text(stringResource(R.string.transporte)) },
                            selected = currentDestination?.route == AppRoutes.Transport.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(AppRoutes.Transport.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        // Notas
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.School, contentDescription = null) },
                            label = { Text(stringResource(R.string.notes)) },
                            selected = currentDestination?.route == AppRoutes.Notes.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(AppRoutes.Notes.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        // Asistencia
                        NavigationDrawerItem(
                            icon = { Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null) },
                            label = { Text(stringResource(R.string.attendance)) },
                            selected = currentDestination?.route == AppRoutes.Attendance.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(AppRoutes.Attendance.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        // Tareas
                        NavigationDrawerItem(
                            icon = { Icon(Icons.AutoMirrored.Filled.EventNote, contentDescription = null) },
                            label = { Text(stringResource(R.string.tasks)) },
                            selected = currentDestination?.route == AppRoutes.Tasks.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(AppRoutes.Tasks.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        // Calendario
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.CalendarToday, contentDescription = null) },
                            label = { Text(stringResource(R.string.calendar)) },
                            selected = currentDestination?.route == AppRoutes.Calendar.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(AppRoutes.Calendar.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }

                    // Opciones comunes: Perfil y Cerrar sesión
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        label = { Text(stringResource(R.string.profile)) },
                        selected = currentDestination?.route == AppRoutes.Profile.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(AppRoutes.Profile.route) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    // Logout
                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                        label = { Text(stringResource(R.string.logout)) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            // limpiar datos locales y lanzar actividad de login para cerrar sesión
                            scope.launch {
                                try {
                                    userPrefs.clearAllUserData()
                                } catch (_: Exception) {}
                                val activity = context as? Activity
                                val intent = Intent(context, LoginActivity::class.java)
                                context.startActivity(intent)
                                activity?.finish()
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    var overflowOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                when (currentDestination?.route) {
                                    AppRoutes.Home.route -> stringResource(R.string.home)
                                    AppRoutes.Messages.route -> stringResource(R.string.messages)
                                    AppRoutes.Notifications.route -> stringResource(R.string.notifications)
                                    AppRoutes.Profile.route -> stringResource(R.string.profile)
                                    AppRoutes.Payments.route -> stringResource(R.string.payments)
                                    AppRoutes.Transport.route -> stringResource(R.string.transporte)
                                    AppRoutes.Notes.route -> stringResource(R.string.notes)
                                    AppRoutes.Attendance.route -> stringResource(R.string.attendance)
                                    AppRoutes.Tasks.route -> stringResource(R.string.tasks)
                                    AppRoutes.Calendar.route -> stringResource(R.string.calendar)
                                    AppRoutes.TeacherHome.route -> stringResource(R.string.home)
                                    AppRoutes.StudentHome.route -> stringResource(R.string.home)
                                    AppRoutes.Dashboard.route -> stringResource(R.string.dashboard)
                                    else -> stringResource(R.string.app_name)
                                }
                            )
                        },
                        navigationIcon = {
                            // Ícono de menú que abre el drawer
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menú")
                            }
                        },
                        actions = {
                            // Notificaciones con badge
                            IconButton(onClick = { navController.navigate(AppRoutes.Notifications.route) }) {
                                BadgedBox(badge = {
                                    if (notifCount > 0) {
                                        Badge { Text(if (notifCount > 99) "99+" else notifCount.toString()) }
                                    }
                                }) {
                                    Icon(Icons.Filled.Notifications, contentDescription = "Notificaciones")
                                }
                            }
                            // Overflow con accesos: Perfil, Pagos, Calendario y Dashboard (admin)
                            IconButton(onClick = { overflowOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones")
                            }
                            DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.profile)) }, onClick = {
                                    overflowOpen = false
                                    navController.navigate(AppRoutes.Profile.route)
                                })
                                if (!isStudent && !isDocente) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.payments)) }, onClick = {
                                        overflowOpen = false
                                        navController.navigate(AppRoutes.Payments.route)
                                    })
                                }
                                DropdownMenuItem(text = { Text(stringResource(R.string.calendar)) }, onClick = {
                                    overflowOpen = false
                                    navController.navigate(AppRoutes.Calendar.route)
                                })
                                // El menú overflow ya no muestra "Dashboard" para evitar duplicado
                                // (la opción Dashboard permanece en el drawer lateral para administradores)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        bottomItems.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            val scale by animateFloatAsState(targetValue = if (selected) 1f else 0.95f, label = "nav_icon_scale")
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(AppRoutes.Home.route) { saveState = true }
                                    }
                                },
                                icon = {
                                    val iconModifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
                                    when (item.route) {
                                        AppRoutes.Notifications.route -> {
                                            BadgedBox(badge = {
                                                if (notifCount > 0) {
                                                    Badge { Text(if (notifCount > 99) "99+" else notifCount.toString()) }
                                                }
                                            }) { Icon(item.icon, contentDescription = item.label, modifier = iconModifier) }
                                        }
                                        AppRoutes.Messages.route -> {
                                            BadgedBox(badge = {
                                                if (msgCount > 0) {
                                                    Badge { Text(if (msgCount > 99) "99+" else msgCount.toString()) }
                                                }
                                            }) { Icon(item.icon, contentDescription = item.label, modifier = iconModifier) }
                                        }
                                        else -> Icon(item.icon, contentDescription = item.label, modifier = iconModifier)
                                    }
                                },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(Modifier.padding(innerPadding)) {
                    content()
                }
            }
        }
    }
}

private data class BottomItem(val label: String, val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
