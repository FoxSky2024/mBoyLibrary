package app.gbalibrary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Panel del modo "Editar pantallas" de la DS: elegir pantalla, intercambiar, escalar, rotar, mostrar/ocultar y restablecer. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DsScreenEditorPanel(
    selected: Int,
    topVisible: Boolean,
    bottomVisible: Boolean,
    onSelect: (Int) -> Unit,
    onSwap: () -> Unit,
    onScale: (Float) -> Unit,
    onRotate90: () -> Unit,
    onToggleVisible: () -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.widthIn(max = 560.dp).background(Color(0xE6101418), RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Editar pantallas — arrastra para mover; con dos dedos pellizca (tamaño) y gira (rotación). Se guarda por juego y orientación.",
            color = Color.White, fontSize = 12.sp,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = selected == 0, onClick = { onSelect(0) }, label = { Text(if (topVisible) "Arriba" else "Arriba (oculta)") })
            FilterChip(selected = selected == 1, onClick = { onSelect(1) }, label = { Text(if (bottomVisible) "Abajo (táctil)" else "Abajo (oculta)") })
            OutlinedButton(onClick = onSwap) { Text("Intercambiar") }
            OutlinedButton(onClick = { onScale(0.9f) }) { Text("−") }
            OutlinedButton(onClick = { onScale(1.1f) }) { Text("+") }
            OutlinedButton(onClick = onRotate90) { Text("Rotar 90°") }
            OutlinedButton(onClick = onToggleVisible) { Text("Mostrar / ocultar") }
            OutlinedButton(onClick = onReset) { Text("Restablecer") }
            Button(onClick = onDone) { Text("Listo") }
        }
    }
}
