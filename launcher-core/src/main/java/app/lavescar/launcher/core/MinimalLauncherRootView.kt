package app.lavescar.launcher.core

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class MinimalLauncherRootView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private var callbacks: MinimalLauncherRootCallbacks? = null
    private var renderedState = MinimalLauncherUiState()
    private var suppressTextCallbacks = false

    private val rowAdapter = AppRowAdapter(
        onRowClicked = { callbacks?.onLaunchApp(it.componentName) },
        onRowLongPressed = { callbacks?.onRequestAppActions(it.componentName) },
    )

    private val gestureDetector = GestureDetectorCompat(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                val start = e1 ?: return false
                val deltaY = e2.y - start.y
                val deltaX = e2.x - start.x
                if (abs(deltaY) <= abs(deltaX) * 1.15f) {
                    return false
                }
                val isStrongFling = abs(deltaY) > dp(44) && abs(velocityY) > 450f
                if (!isStrongFling) {
                    return false
                }
                return when {
                    renderedState.surface == MinimalLauncherSurface.HOME &&
                        renderedState.homeQuery.isBlank() &&
                        deltaY < 0f -> {
                        callbacks?.onOpenAppLibrary()
                        true
                    }

                    renderedState.surface == MinimalLauncherSurface.APP_LIBRARY &&
                        renderedState.libraryQuery.isBlank() &&
                        deltaY > 0f &&
                        !recyclerView.canScrollVertically(-1) -> {
                        callbacks?.onCloseAppLibrary()
                        true
                    }

                    else -> false
                }
            }
        },
    )

    private val contentContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(24), dp(24), dp(24))
    }

    private val searchField = AppCompatEditText(context).apply {
        background = createSearchBackground()
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#6A6A6A"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        typeface = Typeface.SANS_SERIF
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        inputType = InputType.TYPE_CLASS_TEXT
        setPadding(dp(18), dp(16), dp(18), dp(16))
    }

    private val defaultLauncherButton = AppCompatTextView(context).apply {
        text = "Set default launcher"
        setTextColor(Color.parseColor("#A3A3A3"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.START
        setPadding(0, dp(14), 0, dp(2))
        isVisible = false
        setOnClickListener { callbacks?.onRequestDefaultLauncher() }
    }

    private val listContainer = FrameLayout(context)

    private val recyclerView = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context)
        adapter = rowAdapter
        itemAnimator = null
        setHasFixedSize(true)
        clipToPadding = false
        overScrollMode = OVER_SCROLL_NEVER
        setPadding(0, dp(12), 0, dp(12))
    }

    private val emptyView = AppCompatTextView(context).apply {
        setTextColor(Color.parseColor("#7A7A7A"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        gravity = Gravity.CENTER
        isVisible = false
    }

    private val footerButton = AppCompatTextView(context).apply {
        text = "App Library"
        contentDescription = APP_LIBRARY_FOOTER_DESCRIPTION
        setTextColor(Color.parseColor("#6A6A6A"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        setPadding(0, dp(8), 0, 0)
        setOnClickListener { callbacks?.onOpenAppLibrary() }
    }

    private val actionScrim = View(context).apply {
        setBackgroundColor(Color.parseColor("#A6000000"))
        alpha = 0f
        isVisible = false
        setOnClickListener { callbacks?.onDismissActionSheet() }
    }

    private val actionSheetContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(24).toFloat()
            setColor(Color.parseColor("#121212"))
            setStroke(dp(1), Color.parseColor("#1E1E1E"))
        }
        setPadding(dp(20), dp(20), dp(20), dp(20))
        elevation = dp(12).toFloat()
        alpha = 0f
        translationY = dp(24).toFloat()
        isVisible = false
    }

    private val actionTitle = AppCompatTextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val actionSubtitle = AppCompatTextView(context).apply {
        setTextColor(Color.parseColor("#7A7A7A"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(0, dp(4), 0, 0)
        isVisible = false
    }

    private val actionList = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(18), 0, 0)
    }

    init {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        descendantFocusability = FOCUS_BEFORE_DESCENDANTS
        requestFocus()

        addView(
            contentContainer,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        contentContainer.addView(
            searchField,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        contentContainer.addView(
            defaultLauncherButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        listContainer.addView(
            recyclerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        listContainer.addView(
            emptyView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        contentContainer.addView(
            listContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        contentContainer.addView(
            footerButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        addView(
            actionScrim,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        actionSheetContainer.addView(
            actionTitle,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        actionSheetContainer.addView(
            actionSubtitle,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        actionSheetContainer.addView(
            actionList,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(
            actionSheetContainer,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                leftMargin = dp(16)
                rightMargin = dp(16)
                bottomMargin = dp(16)
            },
        )

        searchField.doAfterTextChanged { text ->
            if (suppressTextCallbacks) {
                return@doAfterTextChanged
            }
            when (renderedState.surface) {
                MinimalLauncherSurface.HOME -> callbacks?.onHomeQueryChanged(text?.toString().orEmpty())
                MinimalLauncherSurface.APP_LIBRARY -> callbacks?.onLibraryQueryChanged(text?.toString().orEmpty())
            }
        }
        searchField.setOnEditorActionListener { _, actionId, event ->
            val shouldLaunchFirst = actionId == EditorInfo.IME_ACTION_SEARCH ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (shouldLaunchFirst) {
                currentRows().firstOrNull()?.let { callbacks?.onLaunchApp(it.componentName) }
            }
            shouldLaunchFirst
        }

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            contentContainer.setPadding(
                dp(24),
                bars.top + dp(18),
                dp(24),
                bars.bottom + dp(14),
            )
            (actionSheetContainer.layoutParams as LayoutParams).bottomMargin = bars.bottom + dp(12)
            insets
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!actionSheetContainer.isVisible) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    fun setCallbacks(callbacks: MinimalLauncherRootCallbacks) {
        this.callbacks = callbacks
    }

    fun render(state: MinimalLauncherUiState) {
        val previousSurface = renderedState.surface
        renderedState = state

        updateSearchField(
            query = when (state.surface) {
                MinimalLauncherSurface.HOME -> state.homeQuery
                MinimalLauncherSurface.APP_LIBRARY -> state.libraryQuery
            },
            hint = when (state.surface) {
                MinimalLauncherSurface.HOME -> "Search apps"
                MinimalLauncherSurface.APP_LIBRARY -> "Search app library"
            },
            contentDescription = when (state.surface) {
                MinimalLauncherSurface.HOME -> HOME_SEARCH_FIELD_DESCRIPTION
                MinimalLauncherSurface.APP_LIBRARY -> LIBRARY_SEARCH_FIELD_DESCRIPTION
            },
        )

        defaultLauncherButton.isVisible =
            state.surface == MinimalLauncherSurface.HOME &&
                state.homeQuery.isBlank() &&
                state.showDefaultLauncherAction
        footerButton.isVisible =
            state.surface == MinimalLauncherSurface.HOME &&
                state.homeQuery.isBlank()

        val rows = currentRows()
        val surfaceChanged = previousSurface != state.surface
        rowAdapter.submitList(rows) {
            if (surfaceChanged) {
                recyclerView.scrollToPosition(0)
            }
        }

        val emptyMessage = when (state.surface) {
            MinimalLauncherSurface.HOME -> state.homeEmptyMessage
            MinimalLauncherSurface.APP_LIBRARY -> state.libraryEmptyMessage
        }
        emptyView.text = emptyMessage.orEmpty()
        emptyView.isVisible = rows.isEmpty() && !emptyMessage.isNullOrBlank()

        renderActionSheet(state.actionSheet)

        if (surfaceChanged) {
            clearSearchFocus()
        }
    }

    fun clearSearchFocus() {
        if (searchField.hasFocus()) {
            searchField.clearFocus()
            requestFocus()
            val imm = context.getSystemService(InputMethodManager::class.java)
            imm?.hideSoftInputFromWindow(windowToken, 0)
        }
    }

    private fun currentRows(): List<MinimalLauncherRowUiState> {
        return when (renderedState.surface) {
            MinimalLauncherSurface.HOME -> renderedState.homeRows
            MinimalLauncherSurface.APP_LIBRARY -> renderedState.libraryRows
        }
    }

    private fun updateSearchField(
        query: String,
        hint: String,
        contentDescription: String,
    ) {
        suppressTextCallbacks = true
        try {
            if (searchField.text?.toString().orEmpty() != query) {
                searchField.text = Editable.Factory.getInstance().newEditable(query)
                searchField.setSelection(query.length)
            }
            searchField.hint = hint
            searchField.contentDescription = contentDescription
        } finally {
            suppressTextCallbacks = false
        }
    }

    private fun renderActionSheet(sheet: MinimalLauncherActionSheetUiState?) {
        if (sheet == null) {
            if (!actionSheetContainer.isVisible && !actionScrim.isVisible) {
                return
            }
            actionScrim.animate().alpha(0f).setDuration(140L).withEndAction {
                actionScrim.isVisible = false
            }.start()
            actionSheetContainer.animate().alpha(0f).translationY(dp(24).toFloat()).setDuration(160L)
                .withEndAction {
                    actionSheetContainer.isVisible = false
                    actionList.removeAllViews()
                }
                .start()
            return
        }

        actionTitle.text = sheet.title
        actionSubtitle.text = sheet.subtitle.orEmpty()
        actionSubtitle.isVisible = !sheet.subtitle.isNullOrBlank()

        actionList.removeAllViews()
        sheet.actions.forEachIndexed { index, action ->
            actionList.addView(buildActionButton(action, index == sheet.actions.lastIndex))
        }

        if (!actionScrim.isVisible) {
            actionScrim.alpha = 0f
            actionScrim.isVisible = true
            actionScrim.animate().alpha(1f).setDuration(140L).start()
        }
        if (!actionSheetContainer.isVisible) {
            actionSheetContainer.alpha = 0f
            actionSheetContainer.translationY = dp(24).toFloat()
            actionSheetContainer.isVisible = true
            actionSheetContainer.animate().alpha(1f).translationY(0f).setDuration(160L).start()
        }
    }

    private fun buildActionButton(
        action: MinimalLauncherActionUiState,
        isLast: Boolean,
    ): View {
        return AppCompatTextView(context).apply {
            text = action.title
            setTextColor(
                if (action.destructive) {
                    Color.parseColor("#FF6B6B")
                } else {
                    Color.WHITE
                },
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(14), dp(4), dp(14))
            background = createSelectableBackground()
            setOnClickListener { callbacks?.onSelectActionSheetAction(action.id) }
            alpha = if (isLast) 1f else 0.98f
        }
    }

    private fun createSearchBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(22).toFloat()
            setColor(Color.parseColor("#101010"))
            setStroke(dp(1), Color.parseColor("#1B1B1B"))
        }
    }

    private fun createSelectableBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setColor(Color.TRANSPARENT)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private class AppRowAdapter(
        private val onRowClicked: (MinimalLauncherRowUiState) -> Unit,
        private val onRowLongPressed: (MinimalLauncherRowUiState) -> Unit,
    ) : ListAdapter<MinimalLauncherRowUiState, AppRowViewHolder>(RowDiffCallback) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppRowViewHolder {
            val textView = AppCompatTextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                setPadding(0, dp(parent.context, 18), 0, dp(parent.context, 18))
                minHeight = dp(parent.context, 58)
            }
            return AppRowViewHolder(textView, onRowClicked, onRowLongPressed)
        }

        override fun onBindViewHolder(holder: AppRowViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        private object RowDiffCallback : DiffUtil.ItemCallback<MinimalLauncherRowUiState>() {
            override fun areItemsTheSame(
                oldItem: MinimalLauncherRowUiState,
                newItem: MinimalLauncherRowUiState,
            ): Boolean = oldItem.stableId == newItem.stableId

            override fun areContentsTheSame(
                oldItem: MinimalLauncherRowUiState,
                newItem: MinimalLauncherRowUiState,
            ): Boolean = oldItem == newItem
        }

        private fun dp(context: Context, value: Int): Int {
            return (value * context.resources.displayMetrics.density).toInt()
        }
    }

    private class AppRowViewHolder(
        private val textView: AppCompatTextView,
        onRowClicked: (MinimalLauncherRowUiState) -> Unit,
        onRowLongPressed: (MinimalLauncherRowUiState) -> Unit,
    ) : RecyclerView.ViewHolder(textView) {

        private var row: MinimalLauncherRowUiState? = null

        init {
            textView.setOnClickListener { row?.let(onRowClicked) }
            textView.setOnLongClickListener {
                row?.let(onRowLongPressed)
                row != null
            }
        }

        fun bind(row: MinimalLauncherRowUiState) {
            this.row = row
            textView.text = row.title
            textView.contentDescription = "Launcher app row ${row.title}"
            textView.alpha = if (row.isPinned) 1f else 0.94f
        }
    }

    private companion object {
        const val HOME_SEARCH_FIELD_DESCRIPTION = "Minimal home search field"
        const val LIBRARY_SEARCH_FIELD_DESCRIPTION = "Minimal app library search field"
        const val APP_LIBRARY_FOOTER_DESCRIPTION = "Open app library"
    }
}
