package com.example.volvoai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.volvoai.yolo.DetBox

@Composable
fun DetectionOverlay(
    detections: List<DetBox>,
    inputW: Int,
    inputH: Int,
    previewW: Int,
    previewH: Int
) {
    val sx = previewW.toFloat() / inputW.toFloat()
    val sy = previewH.toFloat() / inputH.toFloat()
    Canvas(Modifier.fillMaxSize()) {
        val stroke = Stroke(width = 3f, pathEffect = PathEffect.cornerPathEffect(2f))
        detections.forEach { d ->
            val x1 = d.x1 * sx
            val y1 = d.y1 * sy
            val x2 = d.x2 * sx
            val y2 = d.y2 * sy
            drawRect(
                color = Color.Green,
                topLeft = Offset(x1, y1),
                size = androidx.compose.ui.geometry.Size(x2 - x1, y2 - y1),
                style = stroke
            )
        }
    }
}
