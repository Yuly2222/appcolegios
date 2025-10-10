package com.example.appcolegios.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Formas con bordes redondeados suaves para componentes (12dp y 16dp)
val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),   // botones, chips
    medium = RoundedCornerShape(16.dp),  // cartas, cuadros de contenido
    large = RoundedCornerShape(0.dp)      // contenedores más grandes
)
