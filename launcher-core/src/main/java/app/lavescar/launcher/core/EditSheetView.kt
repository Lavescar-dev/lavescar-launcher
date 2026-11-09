package app.lavescar.launcher.core

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible

class EditSheetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    var onActionClick: ((String) -> Unit)? = null

    private val sheet = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = editSheetDrawable()
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        elevation = editDp(18).toFloat()
        setPadding(editDp(18), editDp(14), editDp(18), editDp(18))
    }
    private val handleView = FrameLayout(context).apply {
        background = editPillDrawable("#48FFFFFF")
    }
    private val titleView = AppCompatTextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 20f
        gravity = Gravity.CENTER_HORIZONTAL
    }
    private val subtitleView = AppCompatTextView(context).apply {
        setTextColor(Color.parseColor("#B7E7EEF9"))
        textSize = 13f
        gravity = Gravity.CENTER_HORIZONTAL
    }
    private val actionsContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    private var bottomInset = 0

    init {
        isVisible = false
        alpha = 0f
        translationY = editDp(28).toFloat()

        addView(sheet, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        sheet.addView(
            handleView,
            LinearLayout.LayoutParams(editDp(40), editDp(5)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )
        sheet.addView(
            titleView,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = editDp(18)
            },
        )
        sheet.addView(
            subtitleView,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = editDp(6)
            },
        )
        sheet.addView(
            actionsContainer,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = editDp(20)
            },
        )
    }

    fun render(state: EditSheetUiState?) {
        actionsContainer.removeAllViews()
        if (state == null) {
            titleView.text = ""
            subtitleView.text = ""
            subtitleView.isVisible = false
            return
        }

        titleView.text = state.title
        subtitleView.text = state.subtitle.orEmpty()
        subtitleView.isVisible = !state.subtitle.isNullOrBlank()
        state.actions.forEachIndexed { index, action ->
            actionsContainer.addView(
                EditSheetActionRowView(context).apply {
                    bind(action)
                    setOnClickListener { onActionClick?.invoke(action.id) }
                },
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    if (index > 0) {
                        topMargin = editDp(10)
                    }
                },
            )
        }
    }

    fun setOpen(open: Boolean, animated: Boolean) {
        if (!animated) {
            syncOpenState(open)
            return
        }
        isVisible = true
        animate().cancel()
        animate()
            .alpha(if (open) 1f else 0f)
            .translationY(if (open) 0f else editDp(28).toFloat())
            .setDuration(LauncherMotionSpecs.editSheetDurationMillis)
            .setInterpolator(LauncherMotionSpecs.standardInterpolator)
            .withEndAction { isVisible = open }
            .start()
    }

    fun syncOpenState(open: Boolean) {
        isVisible = open
        alpha = if (open) 1f else 0f
        translationY = if (open) 0f else editDp(28).toFloat()
    }

    fun updateInsets(bottomInset: Int) {
        this.bottomInset = bottomInset
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val sideMargin = editDp(LauncherVisualSpec.editSheetHorizontalMarginDp)
        val maxHeight = height - bottomInset - editDp(32)
        sheet.measure(
            MeasureSpec.makeMeasureSpec(width - (sideMargin * 2), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST),
        )
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val sideMargin = editDp(LauncherVisualSpec.editSheetHorizontalMarginDp)
        val sheetTop = height - bottomInset - editDp(LauncherVisualSpec.editSheetBottomSpacingDp) - sheet.measuredHeight
        sheet.layout(
            sideMargin,
            sheetTop,
            sideMargin + sheet.measuredWidth,
            sheetTop + sheet.measuredHeight,
        )
    }
}

private class EditSheetActionRowView(context: Context) : LinearLayout(context) {

    private val titleView = AppCompatTextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 16f
    }
    private val subtitleView = AppCompatTextView(context).apply {
        setTextColor(Color.parseColor("#A9DBE5F4"))
        textSize = 12f
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        background = editSheetRowDrawable()
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        setPadding(editDp(16), editDp(14), editDp(16), editDp(14))
        addView(titleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(
            subtitleView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = editDp(4)
            },
        )
    }

    fun bind(state: EditSheetActionUiState) {
        titleView.text = state.title
        subtitleView.text = state.subtitle.orEmpty()
        subtitleView.isVisible = !state.subtitle.isNullOrBlank()
    }
}

private fun editSheetDrawable(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = editDpF(LauncherVisualSpec.editSheetRadiusDp.toFloat())
        colors = intArrayOf(
            Color.parseColor("#F02A3442"),
            Color.parseColor("#EB1A232F"),
        )
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        setStroke(editDp(1), Color.parseColor("#24FFFFFF"))
    }
}

private fun editSheetRowDrawable(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = editDpF(24f)
        colors = intArrayOf(
            Color.parseColor("#20FFFFFF"),
            Color.parseColor("#12FFFFFF"),
        )
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        setStroke(editDp(1), Color.parseColor("#1EFFFFFF"))
    }
}

private fun editPillDrawable(colorHex: String): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = editDpF(999f)
        setColor(Color.parseColor(colorHex))
    }
}

private fun editDp(value: Int): Int = (value * Resources.getSystem().displayMetrics.density).toInt()

private fun editDpF(value: Float): Float = value * Resources.getSystem().displayMetrics.density
