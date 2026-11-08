package com.picchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PixelDrawingBoard(
    columns: Int = 4,
    rows: Int = 6,
    initialColorIndex: Int = 0,
    currentColorIndex: Int,
    grid: IntArray,
    onGridUpdate: (IntArray) -> Unit,
    onColorChange: (Int) -> Unit
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

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Drawing Grid
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            repeat(rows) { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(columns) { c ->
                        val cellIdx = r * columns + c
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (grid[cellIdx] != -1) colors[grid[cellIdx]] else Color.White,
                                    RoundedCornerShape(6.dp)
                                )
                                .border(0.5.dp, Color.LightGray, RoundedCornerShape(6.dp))
                        ) {
                            Box(modifier = Modifier.fillMaxSize()
                                .pointerInput(c, r) { detectTapGestures(onTap = {
                                    val newGrid = grid.copyOf()
                                    newGrid[cellIdx] = currentColorIndex
                                    onGridUpdate(newGrid)
                                })})
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Color Palette
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            colors.forEachIndexed { idx, color ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(color, RoundedCornerShape(50))
                        .then(Modifier.border(if (idx == currentColorIndex) 3.dp else 1.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(50)))
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = { onGridUpdate(IntArray(columns * rows) { -1 }) }) {
                androidx.compose.material3.Text("Clear")
            }
            androidx.compose.material3.ElevatedButton(onClick = { /* Send handled by caller */ }) {
                androidx.compose.material3.Text("Draw More")
            }
        }
    }
}
