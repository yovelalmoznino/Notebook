package com.example.notebook.ui.screen.canvas

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.RequestDisallowInterceptTouchEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp
import com.example.notebook.data.model.PageBackground
import com.example.notebook.data.model.Stroke

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingCanvas(
    strokes: List<Stroke>,
    currentStroke: Stroke?,
    backgroundType: PageBackground,
    onAction: (MotionEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val requestDisallowInterceptTouchEvent = remember { RequestDisallowInterceptTouchEvent() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInteropFilter(requestDisallowInterceptTouchEvent = requestDisallowInterceptTouchEvent) { event ->
                val toolType = event.getToolType(0)
                val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER

                if (isStylus) {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> requestDisallowInterceptTouchEvent.invoke(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> requestDisallowInterceptTouchEvent.invoke(false)
                    }
                    onAction(event)
                    true
                } else false
            }
    ) {
        // 1. ציור הטמפלט (שורות/משבצות)
        drawTemplate(backgroundType)

        // 2. ציור הקווים
        strokes.forEach { drawStrokePath(it) }
        currentStroke?.let { drawStrokePath(it) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTemplate(type: PageBackground) {
    val lineColor = Color.LightGray.copy(alpha = 0.5f)
    val spacing = 40.dp.toPx()

    when (type) {
        PageBackground.LINES -> {
            var y = spacing
            while (y < size.height) {
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                y += spacing
            }
        }
        PageBackground.GRID -> {
            // קווים אופקיים
            var y = spacing
            while (y < size.height) {
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                y += spacing
            }
            // קווים אנכיים
            var x = spacing
            while (x < size.width) {
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                x += spacing
            }
        }
        PageBackground.DOTS -> {
            for (x in (spacing.toInt()..size.width.toInt() step spacing.toInt())) {
                for (y in (spacing.toInt()..size.height.toInt() step spacing.toInt())) {
                    drawCircle(lineColor, radius = 2f, center = Offset(x.toFloat(), y.toFloat()))
                }
            }
        }
        PageBackground.PLAIN -> {}
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokePath(stroke: Stroke) {
    if (stroke.points.isEmpty()) return
    val path = Path().apply {
        moveTo(stroke.points[0].x, stroke.points[0].y)
        stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(
        path = path,
        color = Color(stroke.color).copy(alpha = if (stroke.isHighlighter) 0.4f else 1f),
        style = DrawStroke(
            width = stroke.strokeWidth,
            cap = if (stroke.isHighlighter) StrokeCap.Square else StrokeCap.Round,
            join = StrokeJoin.Round
        ),
        blendMode = if (stroke.isHighlighter) BlendMode.Multiply else BlendMode.SrcOver
    )
}