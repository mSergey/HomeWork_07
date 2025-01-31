package otus.homework.customview

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.view.doOnLayout
import java.util.Random
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

private const val TOTAL_DEGREES = 360f
private const val MIN_SIZE = 240
private const val SECTOR_WIDTH = 48
private const val CONTENT_PADDING = 8

class PieChartView : View {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?
    ) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr)

    private val Int.dp: Float
        get() = this * Resources.getSystem().displayMetrics.density

    private var onCategoryClickListener: ((categoryName: String) -> Unit)? = null
    private var totalAmount: Int = 0
    private var chartSide: Float = 0f
    private val categoriesToDraw = mutableMapOf<String, CategoryVisualisationModel>()
    private val data = mutableMapOf<String, Int>()
    private val totalAmountTextPaint: Paint = Paint().apply {
        textSize = 21.dp
        color = Color.DKGRAY
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val generalGestureDetector = GestureDetector(
        context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapUp(event: MotionEvent): Boolean {
                categoriesToDraw.forEach {
                    val isChartCategoryUnderTouch = isChartCategoryUnderTouch(
                        touchX = event.x,
                        touchY = event.y,
                        chartCategoryStartAngle = it.value.startAngle,
                        chartCategoryEndAngle = it.value.endAngle,
                        chartCategoryInnerRadius = chartSide / 2f - CONTENT_PADDING.dp - SECTOR_WIDTH.dp,
                        chartCategoryOuterRadius = chartSide / 2f - CONTENT_PADDING.dp
                    )
                    if (isChartCategoryUnderTouch) {
                        onCategoryClickListener?.invoke(it.key)
                        return@forEach
                    }
                }
                return true
            }
        }
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        // check all available width/height combinations
        when {
            widthMode == MeasureSpec.AT_MOST && heightMode == MeasureSpec.AT_MOST -> {
                setMeasuredDimension(
                    MIN_SIZE.dp.toInt(),
                    MIN_SIZE.dp.toInt()
                )
            }
            widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.AT_MOST -> {
                setMeasuredDimension(
                    widthSize.coerceAtLeast(MIN_SIZE.dp.toInt()),
                    MIN_SIZE.dp.toInt()
                )
            }
            widthMode == MeasureSpec.AT_MOST && heightMode == MeasureSpec.EXACTLY -> {
                setMeasuredDimension(
                    MIN_SIZE.dp.toInt(),
                    heightSize.coerceAtLeast(MIN_SIZE.dp.toInt())
                )
            }
            widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY -> {
                setMeasuredDimension(
                    widthSize.coerceAtLeast(MIN_SIZE.dp.toInt()),
                    heightSize.coerceAtLeast(MIN_SIZE.dp.toInt())
                )
            }
            else -> {
                // nothing to do
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        chartSide = min(width, height).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        this.categoriesToDraw.forEach {
            canvas.drawPath(it.value.path, it.value.paint)
        }
        canvas.drawText(
            "$totalAmount $",
            width / 2f,
            height / 2f + 7.dp,
            totalAmountTextPaint
        )
    }

    override fun onSaveInstanceState(): Parcelable {
        return PieChartSavedState(super.onSaveInstanceState())
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is PieChartSavedState) {
            return super.onRestoreInstanceState(state)
        }
        super.onRestoreInstanceState(state.superState)
        updateData(state.savedData)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return generalGestureDetector.onTouchEvent(event)
    }

    fun updateData(data: Map<String, Int>) {
        this.data.putAll(data)
        totalAmount = data.map { it.value }.sum()
        doOnLayout {
            categoriesToDraw.populate(data)
            invalidate()
        }
    }

    fun setOnCategoryClickListener(
        listener: (categoryName: String) -> Unit
    ) {
        this.onCategoryClickListener = listener
    }

    // prepares the category visualization dataset
    // with needed objects creating (path, paint, etc)
    // and a calculations before onDraw() call
    private fun MutableMap<String, CategoryVisualisationModel>.populate(
        data: Map<String, Int>
    ) {
        clear()
        var startAngle = 0f
        data.forEach {
            val category = CategoryVisualisationModel(
                categoryAmount = it.value,
                startAngle = startAngle
            )
            startAngle = category.endAngle
            put(it.key, category)
        }
    }

    // checks whether the touch is on specific sector or not
    // based on the solution of the Inverse Geodetic Problem on the plane
    private fun isChartCategoryUnderTouch(
        touchX: Float,
        touchY: Float,
        chartCategoryStartAngle: Float,
        chartCategoryEndAngle: Float,
        chartCategoryInnerRadius: Float,
        chartCategoryOuterRadius: Float
    ): Boolean {
        val deltaX = touchX - width / 2
        val deltaY = height / 2 - touchY
        val angleRadians = atan(deltaY/deltaX)
        val angleDegree = angleRadians * 180f / PI
        val angleAbs = when {
            deltaX > 0 && deltaY > 0 -> 360f - angleDegree
            deltaX > 0 && deltaY < 0 -> -angleDegree
            deltaX < 0 && deltaY < 0 -> 180f - angleDegree
            deltaX < 0 && deltaY > 0 -> 180f - angleDegree
            else -> 180f + angleDegree
        }
        val distance = sqrt(deltaX.pow(2) + deltaY.pow(2))
        val isDistanceValid =
            distance > chartCategoryInnerRadius && distance < chartCategoryOuterRadius
        val isSectorValid =
            angleAbs > chartCategoryStartAngle && angleAbs < chartCategoryEndAngle
        return isDistanceValid && isSectorValid
    }

    // represents the category visualisation data to pass into onDraw()
    private inner class CategoryVisualisationModel(
        categoryAmount: Int,
        val startAngle: Float
    ) {
        val path: Path = Path()
        val paint: Paint = Paint()
        val endAngle: Float
        init {
            val verticalCenter = height / 2
            val horizontalCenter = width / 2
            val left = horizontalCenter - chartSide / 2 + CONTENT_PADDING.dp + SECTOR_WIDTH.dp / 2f
            val top = verticalCenter - chartSide / 2 + CONTENT_PADDING.dp + SECTOR_WIDTH.dp / 2f
            val right = horizontalCenter + chartSide / 2 - CONTENT_PADDING.dp - SECTOR_WIDTH.dp / 2f
            val bottom = verticalCenter + chartSide / 2 - CONTENT_PADDING.dp - SECTOR_WIDTH.dp / 2f
            val chartBaseRect = RectF(left, top, right, bottom)
            val sweepAngel = TOTAL_DEGREES * categoryAmount / totalAmount
            path.apply {
                addArc(chartBaseRect, startAngle, sweepAngel)
            }
            paint.apply {
                this.color = generateRandomColor()
                this.style = Paint.Style.STROKE
                this.strokeWidth = SECTOR_WIDTH.dp
            }
            endAngle = startAngle + sweepAngel
        }

        private fun generateRandomColor(): Int {
            val random = Random()
            return Color.argb(
                255,
                random.nextInt(256),
                random.nextInt(256),
                random.nextInt(256)
            )
        }
    }

    private inner class PieChartSavedState : BaseSavedState {

        val savedData = mutableMapOf<String, Int>()

        constructor(source: Parcelable?) : super(source) {
            savedData.putAll(data)
        }
        private constructor(parcelIn: Parcel) : super(parcelIn) {
            parcelIn.readMap(savedData, ClassLoader.getSystemClassLoader())
        }
        override fun writeToParcel(parcelOut: Parcel, flags: Int) {
            super.writeToParcel(parcelOut, flags)
            parcelOut.writeMap(savedData)
        }

        @JvmField
        val CREATOR: Parcelable.Creator<PieChartSavedState?> =
            object : Parcelable.Creator<PieChartSavedState?> {
            override fun createFromParcel(parcelIn: Parcel): PieChartSavedState {
                return PieChartSavedState(parcelIn)
            }

            override fun newArray(size: Int): Array<PieChartSavedState?> {
                return arrayOfNulls(size)
            }
        }
    }
}