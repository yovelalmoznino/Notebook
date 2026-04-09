package com.example.notebook.ui.screen.canvas

import android.content.Context
import android.os.Build
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.notebook.data.model.CanvasTool
import com.example.notebook.data.model.MarkerShape
import com.example.notebook.data.model.PenType
import com.example.notebook.data.model.ShapeType
import com.example.notebook.data.model.Stroke as CanvasStroke
import com.example.notebook.data.model.StrokePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val META_STYLUS_LEGACY = 0x200
private const val META_ERASER_LEGACY = 0x400

private class StylusInputView(
    context: Context
) : View(context) {

    var activeTool: CanvasTool = CanvasTool.PEN
    var onDrawAction: ((action: Int, x: Float, y: Float, pressure: Float, isEraser: Boolean) -> Unit)? = null

    private var activePointerId: Int = MotionEvent.INVALID_POINTER_ID
    private var strokeInProgress = false
    private var currentIsEraser = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val actionIndex = event.actionIndex

        fun toolTypeAt(index: Int): Int {
            return if (index in 0 until event.pointerCount) event.getToolType(index)
            else MotionEvent.TOOL_TYPE_UNKNOWN
        }

        fun isFinger(index: Int): Boolean {
            return toolTypeAt(index) == MotionEvent.TOOL_TYPE_FINGER
        }

        fun detectEraser(index: Int): Boolean {
            val toolType = toolTypeAt(index)
            if (toolType == MotionEvent.TOOL_TYPE_ERASER) return true
            return (event.metaState and META_ERASER_LEGACY) == META_ERASER_LEGACY
        }

        fun detectStylus(index: Int): Boolean {
            val toolType = toolTypeAt(index)
            if (toolType == MotionEvent.TOOL_TYPE_STYLUS) return true
            if ((event.metaState and META_STYLUS_LEGACY) == META_STYLUS_LEGACY) return true

            if (toolType == MotionEvent.TOOL_TYPE_UNKNOWN && !isFinger(index)) return true
            return false
        }

        fun isStylusLike(index: Int): Boolean {
            return detectStylus(index) || detectEraser(index)
        }

        fun requestFastDispatch() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                requestUnbufferedDispatch(event)
            }
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (isFinger(0)) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    strokeInProgress = false
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    return false
                }

                if (!isStylusLike(0)) return false

                parent?.requestDisallowInterceptTouchEvent(true)
                requestFastDispatch()

                activePointerId = event.getPointerId(0)
                currentIsEraser = detectEraser(0)
                strokeInProgress = true

                onDrawAction?.invoke(
                    0,
                    event.getX(0),
                    event.getY(0),
                    event.getPressure(0).coerceAtLeast(0.01f),
                    currentIsEraser
                )
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (strokeInProgress) return true
                if (isFinger(actionIndex)) return false
                if (!isStylusLike(actionIndex)) return false

                parent?.requestDisallowInterceptTouchEvent(true)
                requestFastDispatch()

                activePointerId = event.getPointerId(actionIndex)
                currentIsEraser = detectEraser(actionIndex)
                strokeInProgress = true

                onDrawAction?.invoke(
                    0,
                    event.getX(actionIndex),
                    event.getY(actionIndex),
                    event.getPressure(actionIndex).coerceAtLeast(0.01f),
                    currentIsEraser
                )
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!strokeInProgress) return false

                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex == -1) {
                    strokeInProgress = false
                    currentIsEraser = false
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return false
                }

                if (isFinger(pointerIndex)) {
                    strokeInProgress = false
                    currentIsEraser = false
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return false
                }

                currentIsEraser = detectEraser(pointerIndex)

                for (i in 0 until event.historySize) {
                    onDrawAction?.invoke(
                        2,
                        event.getHistoricalX(pointerIndex, i),
                        event.getHistoricalY(pointerIndex, i),
                        event.getHistoricalPressure(pointerIndex, i).coerceAtLeast(0.01f),
                        currentIsEraser
                    )
                }

                onDrawAction?.invoke(
                    2,
                    event.getX(pointerIndex),
                    event.getY(pointerIndex),
                    event.getPressure(pointerIndex).coerceAtLeast(0.01f),
                    currentIsEraser
                )
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!strokeInProgress) return false

                onDrawAction?.invoke(
                    1,
                    event.getX(0),
                    event.getY(0),
                    event.getPressure(0).coerceAtLeast(0.01f),
                    currentIsEraser
                )

                strokeInProgress = false
                currentIsEraser = false
                activePointerId = MotionEvent.INVALID_POINTER_ID
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = event.getPointerId(actionIndex)
                if (pointerId == activePointerId && strokeInProgress) {
                    onDrawAction?.invoke(
                        1,
                        event.getX(actionIndex),
                        event.getY(actionIndex),
                        event.getPressure(actionIndex).coerceAtLeast(0.01f),
                        currentIsEraser
                    )

                    strokeInProgress = false
                    currentIsEraser = false
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                return false
            }

            MotionEvent.ACTION_CANCEL -> {
                if (strokeInProgress) {
                    val pointerIndex = event.findPointerIndex(activePointerId).takeIf { it != -1 } ?: 0
                    onDrawAction?.invoke(
                        1,
                        event.getX(pointerIndex),
                        event.getY(pointerIndex),
                        0f,
                        currentIsEraser
                    )
                }

                strokeInProgress = false
                currentIsEraser = false
                activePointerId = MotionEvent.INVALID_POINTER_ID
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }

        return false
    }
}

@Composable
fun DrawingCanvas(
    activeTool: CanvasTool,
    strokes: List<CanvasStroke>,
    selectedStrokes: List<CanvasStroke>,
    dragOffset: Offset,
    currentStroke: CanvasStroke?,
    lassoPath: List<Offset>,
    selectionBounds: Rect?,
    onDrawAction: (action: Int, x: Float, y: Float, pressure: Float, isEraser: Boolean) -> Unit
) {
    val context = LocalContext.current
    val currentOnDrawAction = rememberUpdatedState(onDrawAction)

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            strokes.forEach { drawComplexStroke(it, Offset.Zero) }
            selectedStrokes.forEach { drawComplexStroke(it, dragOffset) }
            currentStroke?.let { drawComplexStroke(it, Offset.Zero) }

            if (lassoPath.size > 1 && selectionBounds == null) {
                val path = Path().apply {
                    moveTo(lassoPath[0].x, lassoPath[0].y)
                    lassoPath.forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path = path,
                    color = Color(0xFF3B82F6).copy(alpha = 0.5f),
                    style = DrawStyle(
                        width = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f))
                    )
                )
            }

            selectionBounds?.let { b ->
                drawRect(
                    color = Color(0xFF3B82F6).copy(alpha = 0.15f),
                    topLeft = Offset(b.left, b.top),
                    size = Size(b.width, b.height)
                )
                drawRect(
                    color = Color(0xFF3B82F6),
                    topLeft = Offset(b.left, b.top),
                    size = Size(b.width, b.height),
                    style = DrawStyle(width = 2.5f)
                )
                drawCircle(Color.White, 18f, Offset(b.right, b.bottom))
                drawCircle(
                    color = Color(0xFF3B82F6),
                    radius = 18f,
                    center = Offset(b.right, b.bottom),
                    style = DrawStyle(width = 3f)
                )
            }
        }

        AndroidView(
            factory = {
                StylusInputView(context).apply {
                    this.activeTool = activeTool
                    this.onDrawAction = { action, x, y, pressure, isEraser ->
                        currentOnDrawAction.value(action, x, y, pressure, isEraser)
                    }
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            update = { view ->
                view.activeTool = activeTool
                view.onDrawAction = { action, x, y, pressure, isEraser ->
                    currentOnDrawAction.value(action, x, y, pressure, isEraser)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

fun DrawScope.drawComplexStroke(stroke: CanvasStroke, offset: Offset) {
    if (stroke.points.isEmpty()) return

    val color = Color(stroke.color).copy(alpha = if (stroke.isHighlighter) 0.5f else 1f)

    when (stroke.shapeType ?: ShapeType.FREEHAND) {
        ShapeType.FREEHAND -> {
            if (stroke.isHighlighter) {
                val cap = if (stroke.markerShape == MarkerShape.SQUARE) StrokeCap.Square else StrokeCap.Round
                val path = Path().apply {
                    moveTo(stroke.points[0].x + offset.x, stroke.points[0].y + offset.y)
                    stroke.points.forEach { lineTo(it.x + offset.x, it.y + offset.y) }
                }
                drawPath(
                    path = path,
                    color = color,
                    style = DrawStyle(
                        width = stroke.strokeWidth,
                        cap = cap,
                        join = StrokeJoin.Bevel
                    ),
                    blendMode = BlendMode.Multiply
                )
            } else {
                when (stroke.penType) {
                    PenType.FOUNTAIN -> {
                        for (i in 0 until stroke.points.size - 1) {
                            val p1 = stroke.points[i]
                            val p2 = stroke.points[i + 1]
                            val width = stroke.strokeWidth * (p1.pressure * 2f).coerceIn(0.5f, 2.5f)
                            drawLine(
                                color = color,
                                start = Offset(p1.x + offset.x, p1.y + offset.y),
                                end = Offset(p2.x + offset.x, p2.y + offset.y),
                                strokeWidth = width,
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    PenType.CALLIGRAPHY -> {
                        val angle = PI / 4
                        stroke.points.forEach { pt ->
                            val xShift = cos(angle).toFloat() * stroke.strokeWidth
                            val yShift = sin(angle).toFloat() * stroke.strokeWidth
                            drawLine(
                                color = color,
                                start = Offset(pt.x + offset.x - xShift, pt.y + offset.y - yShift),
                                end = Offset(pt.x + offset.x + xShift, pt.y + offset.y + yShift),
                                strokeWidth = 2f
                            )
                        }
                    }

                    else -> {
                        val path = Path().apply {
                            moveTo(stroke.points[0].x + offset.x, stroke.points[0].y + offset.y)
                            stroke.points.forEach { lineTo(it.x + offset.x, it.y + offset.y) }
                        }
                        drawPath(
                            path = path,
                            color = color,
                            style = DrawStyle(
                                width = stroke.strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }

        else -> drawShape(stroke, color, offset)
    }
}

private fun DrawScope.drawShape(stroke: CanvasStroke, color: Color, offset: Offset) {
    if (stroke.points.size < 2) return

    val p1 = stroke.points.first()
    val p2 = stroke.points.last()

    val start = Offset(p1.x + offset.x, p1.y + offset.y)
    val end = Offset(p2.x + offset.x, p2.y + offset.y)

    val style = DrawStyle(stroke.strokeWidth, join = StrokeJoin.Round)
    val left = min(start.x, end.x)
    val top = min(start.y, end.y)
    val width = abs(end.x - start.x).coerceAtLeast(1f)
    val height = abs(end.y - start.y).coerceAtLeast(1f)

    when (stroke.shapeType) {
        ShapeType.LINE -> drawLine(color, start, end, stroke.strokeWidth, StrokeCap.Round)

        ShapeType.RECTANGLE -> drawRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(width, height),
            style = style
        )

        ShapeType.CIRCLE -> drawOval(
            color = color,
            topLeft = Offset(left, top),
            size = Size(width, height),
            style = style
        )

        ShapeType.TRIANGLE -> {
            val path = Path().apply {
                moveTo(left + width / 2, top)
                lineTo(left, top + height)
                lineTo(left + width, top + height)
                close()
            }
            drawPath(path, color, style = style)
        }

        ShapeType.ARROW -> {
            val angle = atan2(end.y - start.y, end.x - start.x)
            drawLine(color, start, end, stroke.strokeWidth, StrokeCap.Round)
            drawLine(
                color,
                end,
                Offset(
                    end.x - 30f * cos(angle - 0.5f).toFloat(),
                    end.y - 30f * sin(angle - 0.5f).toFloat()
                ),
                stroke.strokeWidth
            )
            drawLine(
                color,
                end,
                Offset(
                    end.x - 30f * cos(angle + 0.5f).toFloat(),
                    end.y - 30f * sin(angle + 0.5f).toFloat()
                ),
                stroke.strokeWidth
            )
        }

        ShapeType.STAR -> {
            val path = Path()
            val cx = left + width / 2
            val cy = top + height / 2
            val outer = min(width, height) / 2
            val inner = outer / 2.5f

            for (i in 0 until 10) {
                val r = if (i % 2 == 0) outer else inner
                val ang = i * PI / 5 - PI / 2
                val px = cx + r * cos(ang).toFloat()
                val py = cy + r * sin(ang).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            drawPath(path, color, style = style)
        }

        else -> {}
    }
}