package com.example.notebook.ui.screen.canvas

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStyle
import androidx.compose.ui.input.pointer.pointerInteropFilter
import com.example.notebook.data.model.Stroke as StrokeModel
import com.example.notebook.ui.theme.PastelSurface
// אימפורטים שחסרו לך:
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin

// ההגדרה הזו חייבת להיות כאן כדי שהקבצים האחרים יזהו אותה
enum class DrawingTool { PEN, ERASER }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingCanvas(
    strokes: List<StrokeModel>,
    currentStroke: StrokeModel?,
    onAction: (MotionEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(PastelSurface)
            .pointerInteropFilter { event ->
                onAction(event)
                true
            }
    ) {
        strokes.forEach { drawStroke(it) }
        currentStroke?.let { drawStroke(it) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(stroke: StrokeModel) {
    if (stroke.points.size < 2) return
    val path = Path().apply {
        moveTo(stroke.points.first().x, stroke.points.first().y)
        stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(
        path = path,
        color = Color(stroke.color),
        style = DrawStyle(width = stroke.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        blendMode = if (stroke.isEraser) BlendMode.Clear else BlendMode.SrcOver
    )
}