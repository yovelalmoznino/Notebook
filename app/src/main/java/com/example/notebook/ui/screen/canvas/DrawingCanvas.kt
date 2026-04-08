package com.example.notebook.ui.screen.canvas

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import com.example.notebook.data.model.Stroke // אימפורט חובה!

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingCanvas(
    strokes: List<Stroke>,
    currentStroke: Stroke?,
    onAction: (MotionEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInteropFilter {
                onAction(it)
                true
            }
    ) {
        // רנדור קווים קיימים
        strokes.forEach { stroke ->
            drawStrokePath(stroke)
        }

        // רנדור הקו הנוכחי שנכתב עכשיו
        currentStroke?.let { stroke ->
            drawStrokePath(stroke)
        }
    }
}

// פונקציית עזר לציור Path מתוך אובייקט Stroke
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokePath(stroke: Stroke) {
    if (stroke.points.isEmpty()) return

    val path = Path().apply {
        moveTo(stroke.points[0].x, stroke.points[0].y)
        stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
    }

    drawPath(
        path = path,
        color = Color(stroke.color),
        style = DrawStroke(
            width = stroke.strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}