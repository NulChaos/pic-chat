package com.picchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun ModalDrawingDrawer(
    grid: IntArray,
    onGridUpdate: (IntArray) -> Unit,
    currentColorIndex: Int,
    onColorChange: (Int) -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    val colors = listOf(
        Color.White,
        Color(0xFF8B4513), // brown
        Color(0xFF333333), // black
        MaterialTheme.colorScheme.primary,
        Color(0xFFFFD700), // gold
        Color(0xFF90EE90), // green
        Color(0xFF87CEEB), // blue
        Color(0xFFFF69B4)  // pink
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))) {
        Card(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text("🎨 Draw a picture!", style = MaterialTheme.typography.titleLarge, color = Color.Black)

                // Grid preview
                repeat(6) { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                        repeat(4) { c ->
                            val idx = r * 4 + c
                            Box(
                                modifier = Modifier.size(50.dp)
                                    .background(if (grid[idx] != -1) colors[grid[idx]] else Color.White, RoundedCornerShape(6.dp))
                                    .border(0.5.dp, Color.LightGray, RoundedCornerShape(6.dp))
                                    .pointerInput(c, r) { detectTapGestures(onTap = {
                                        val newGrid = grid.copyOf()
                                        newGrid[idx] = currentColorIndex
                                        onGridUpdate(newGrid)
                                    })}
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Color palette
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    colors.forEachIndexed { idx, color ->
                        Box(
                            modifier = Modifier.size(36.dp)
                                .background(color, RoundedCornerShape(50))
                                .then(Modifier.border(if (idx == currentColorIndex) 4.dp else 2.dp, Color.Black, RoundedCornerShape(50)))
                                .pointerInput(idx) { detectTapGestures(onTap = { onColorChange(idx) }) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { onGridUpdate(IntArray(24) { -1 }) }, modifier = Modifier.weight(1f)) { Text("Clear") }
                    Button(onClick = onSend, modifier = Modifier.weight(1f)) { Text("Send 📤") }
                }
            }
        }
    }
}
