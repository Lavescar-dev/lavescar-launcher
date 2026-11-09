package app.lavescar.launcher.core

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import kotlin.math.max
import kotlin.math.min

class ActionSheetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    var onActionClick: ((String) -> Unit)? = null

    private val scrollContainer = ScrollView(context).apply {
        isFillViewport = true
        overScrollMode = OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
    }
    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = glassCardDrawable()
        elevation = dp(18).toFloat()
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }
    private val headerRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val iconShell = FrameLayout(context).apply {
        background = headerIconDrawable()
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        isVisible = false
    }
    private val iconView = AppCompatImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    private val titleColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    private val titleView = AppCompatTextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 18f
    }
    private val subtitleView = AppCompatTextView(context).apply {
        setTextColor(Color.parseColor("#B8E4EAFA"))
        textSize = 12.5f
        isVisible = false
    }
    private val sectionContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    private var bottomInset = 0
    private var topInset = 0
    private var state: LauncherActionSheetState? = null

    init {
        isVisible = false
        alpha = 0f
        scaleX = 0.96f
        scaleY = 0.96f
        isClickable = true

        addView(scrollContainer, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        scrollContainer.addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        iconShell.addView(
            iconView,
            LayoutParams(dp(34), dp(34), Gravity.CENTER),
        )
        headerRow.addView(
            iconShell,
            LinearLayout.LayoutParams(dp(48), dp(48)),
        )
        titleColumn.addView(titleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        titleColumn.addView(
            subtitleView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            },
        )
        headerRow.addView(
            titleColumn,
            LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            },
        )

        card.addView(headerRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        card.addView(
            sectionContainer,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(16)
            },
        )
    }

    fun render(state: LauncherActionSheetState?) {
        this.state = state
        sectionContainer.removeAllViews()
        if (state == null) {
            titleView.text = ""
            subtitleView.text = ""
            subtitleView.isVisible = false
            iconShell.isVisible = false
            return
        }

        titleView.text = state.title
        subtitleView.text = state.subtitle.orEmpty()
        subtitleView.isVisible = !state.subtitle.isNullOrBlank()
        iconView.setImageDrawable(state.icon)
        iconShell.isVisible = state.icon != null

        state.sections.forEachIndexed { index, section ->
            sectionContainer.addView(
                ActionSectionView(context).apply {
                    bind(section, onActionClick)
                },
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    if (index > 0) {
                        topMargin = dp(12)
                    }
                },
            )
        }
        requestLayout()
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
            .scaleX(if (open) 1f else 0.96f)
            .scaleY(if (open) 1f else 0.96f)
            .translationY(if (open) 0f else dp(14).toFloat())
            .setDuration(LauncherMotionSpecs.contextMenuDurationMillis)
            .setInterpolator(LauncherMotionSpecs.emphasizedInterpolator)
            .withEndAction { isVisible = open }
            .start()
    }

    fun syncOpenState(open: Boolean) {
        isVisible = open
        alpha = if (open) 1f else 0f
        scaleX = if (open) 1f else 0.96f
        scaleY = if (open) 1f else 0.96f
        translationY = 0f
    }

    fun updateInsets(topInset: Int, bottomInset: Int) {
        this.topInset = topInset
        this.bottomInset = bottomInset
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val presentation = state?.presentation
        val cardWidth = when (presentation) {
            is LauncherActionSheetPresentation.Anchored ->
                min(dp(LauncherVisualSpec.anchoredMenuWidthDp), width - dp(22))

            else -> width - dp(LauncherVisualSpec.bottomSheetWidthMarginDp * 2)
        }
        val maxHeight = when (presentation) {
            is LauncherActionSheetPresentation.Anchored -> height - topInset - bottomInset - dp(28)
            else -> height / 2
        }
        scrollContainer.measure(
            MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(max(dp(180), maxHeight), MeasureSpec.AT_MOST),
        )
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        when (val presentation = state?.presentation ?: LauncherActionSheetPresentation.BottomSheet) {
            is LauncherActionSheetPresentation.Anchored -> layoutAnchored(presentation.anchor)
            LauncherActionSheetPresentation.BottomSheet -> layoutBottomSheet()
        }
    }

    private fun layoutBottomSheet() {
        val horizontalMargin = dp(LauncherVisualSpec.bottomSheetWidthMarginDp)
        val cardTop = height - bottomInset - scrollContainer.measuredHeight - dp(12)
        scrollContainer.layout(
            horizontalMargin,
            cardTop,
            horizontalMargin + scrollContainer.measuredWidth,
            cardTop + scrollContainer.measuredHeight,
        )
        pivotX = (scrollContainer.left + scrollContainer.right) / 2f
        pivotY = scrollContainer.bottom.toFloat()
    }

    private fun layoutAnchored(anchor: LauncherSurfaceAnchor) {
        val menuWidth = scrollContainer.measuredWidth
        val menuHeight = scrollContainer.measuredHeight
        val horizontalMargin = dp(12)
        val verticalMargin = dp(8)
        val anchorCenterX = (anchor.left + anchor.right) / 2
        val preferredLeft = anchorCenterX - (menuWidth / 2)
        val left = preferredLeft.coerceIn(horizontalMargin, width - horizontalMargin - menuWidth)

        val aboveTop = anchor.top - verticalMargin - menuHeight
        val belowTop = anchor.bottom + verticalMargin
        val top = if (aboveTop >= topInset + verticalMargin) {
            aboveTop
        } else {
            min(belowTop, height - bottomInset - verticalMargin - menuHeight)
        }

        scrollContainer.layout(left, top, left + menuWidth, top + menuHeight)
        pivotX = (anchorCenterX - left).coerceIn(0, menuWidth).toFloat()
        pivotY = if (top < anchor.top) menuHeight.toFloat() else 0f
    }
}

private class ActionSectionView(context: Context) : LinearLayout(context) {

    private val titleView = AppCompatTextView(context).apply {
        setTextColor(Color.parseColor("#99EAF0FF"))
        textSize = 11f
        isVisible = false
    }
    private val actionStack = LinearLayout(context).apply {
        orientation = VERTICAL
        background = sectionSurfaceDrawable()
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }

    init {
        orientation = VERTICAL
        addView(titleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(
            actionStack,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            },
        )
    }

    fun bind(
        section: LauncherActionSheetSection,
        onActionClick: ((String) -> Unit)?,
    ) {
        titleView.text = section.title.orEmpty()
        titleView.isVisible = !section.title.isNullOrBlank()
        actionStack.removeAllViews()
        section.actions.forEachIndexed { index, action ->
            actionStack.addView(
                ActionButtonView(context).apply {
                    bind(action)
                    setOnClickListener { onActionClick?.invoke(action.id) }
                },
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    if (index > 0) {
                        topMargin = dp(2)
                    }
                },
            )
        }
    }
}

private class ActionButtonView(context: Context) : LinearLayout(context) {

    private val titleColumn = LinearLayout(context).apply {
        orientation = VERTICAL
    }
    private val titleView = AppCompatTextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 15f
    }
    private val subtitleView = AppCompatTextView(context).apply {
        setTextColor(Color.parseColor("#A6FFFFFF"))
        textSize = 12f
        isVisible = false
    }
    private val chevronView = AppCompatTextView(context).apply {
        text = "›"
        textSize = 18f
        setTextColor(Color.parseColor("#80FFFFFF"))
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(54)
        background = actionBackgroundDrawable()
        setPadding(dp(14), dp(12), dp(14), dp(12))

        titleColumn.addView(titleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        titleColumn.addView(
            subtitleView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(3)
            },
        )
        addView(titleColumn, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(
            chevronView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(12)
            },
        )
    }

    fun bind(action: LauncherActionSheetAction) {
        titleView.text = action.title
        titleView.setTextColor(if (action.destructive) Color.parseColor("#FFFFA2A2") else Color.WHITE)
        subtitleView.text = action.subtitle.orEmpty()
        subtitleView.isVisible = !action.subtitle.isNullOrBlank()
        chevronView.setTextColor(
            if (action.destructive) {
                Color.parseColor("#60FFB0B0")
            } else {
                Color.parseColor("#80FFFFFF")
            },
        )
    }
}

private fun glassCardDrawable(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpF(LauncherVisualSpec.menuRadiusDp.toFloat())
        colors = intArrayOf(
            Color.parseColor("#F2263141"),
            Color.parseColor("#EA192331"),
        )
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        setStroke(dp(1), Color.parseColor("#30FFFFFF"))
    }
}

private fun sectionSurfaceDrawable(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpF(22f)
        colors = intArrayOf(
            Color.parseColor("#24FFFFFF"),
            Color.parseColor("#11FFFFFF"),
        )
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        setStroke(dp(1), Color.parseColor("#16FFFFFF"))
    }
}

private fun headerIconDrawable(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpF(16f)
        colors = intArrayOf(
            Color.parseColor("#34FFFFFF"),
            Color.parseColor("#16FFFFFF"),
        )
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        setStroke(dp(1), Color.parseColor("#1EFFFFFF"))
    }
}

private fun actionBackgroundDrawable(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpF(18f)
        colors = intArrayOf(
            Color.parseColor("#32FFFFFF"),
            Color.parseColor("#16FFFFFF"),
        )
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        setStroke(dp(1), Color.parseColor("#14FFFFFF"))
    }
}

private fun dp(value: Int): Int = (value * Resources.getSystem().displayMetrics.density).toInt()

private fun dpF(value: Float): Float = value * Resources.getSystem().displayMetrics.density
