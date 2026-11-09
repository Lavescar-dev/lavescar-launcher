package app.lavescar.launcher.core

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.text.InputFilter
import android.util.AttributeSet
import android.view.DragEvent
import android.view.Gravity
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class LauncherRootView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val removeTargetView = AppCompatTextView(context).apply {
        text = context.getString(R.string.launcher_remove_target)
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = pillDrawable("#CCB42318")
        setPadding(dp(22), dp(10), dp(22), dp(10))
        elevation = dp(8).toFloat()
        isVisible = false
    }

    private val scrimView = View(context).apply {
        setBackgroundColor(Color.BLACK)
        alpha = 0f
        isVisible = false
        setOnClickListener {
            when {
                currentState?.actionSheet != null -> callbacks?.onDismissActionSheet()
                currentState?.editSheet != null -> callbacks?.onDismissEditSheet()
                currentState?.openFolder != null -> callbacks?.onCloseFolder()
                currentState?.surfaceMode == LauncherSurfaceMode.SPOTLIGHT -> callbacks?.onCloseSpotlight()
            }
        }
    }
    private val homeAtmosphereView = View(context).apply {
        background = homeAtmosphereDrawable()
        alpha = 1f
    }

    private val pageContainer = ItemContainerView(context, LauncherContainerKind.PAGE)
    private val dockContainer = ItemContainerView(context, LauncherContainerKind.DOCK).apply {
        background = dockGlassDrawable()
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        elevation = dp(10).toFloat()
    }
    private val searchPillView = HomeSearchPillView(context)
    private val pageIndicatorView = HomePageIndicatorView(context)
    private val editSheetView = EditSheetView(context)

    private val drawerPanel = DrawerPanelView(context)
    private val spotlightPanel = SpotlightPanelView(context)
    private val folderPanel = FolderPanelView(context)
    private val actionSheetView = ActionSheetView(context)

    private var callbacks: LauncherRootCallbacks? = null
    private var currentState: LauncherUiState? = null
    private var widgetViewBinder: WidgetViewBinder? = null

    private var systemTopInset = 0
    private var systemBottomInset = 0
    private var gestureBottomInset = 0
    private var navigationStartX = 0f
    private var navigationStartY = 0f
    private val navigationGestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                if (currentState?.openFolder != null || currentState?.actionSheet != null || currentState?.editSheet != null) {
                    return false
                }
                val start = e1 ?: return false
                val deltaX = e2.x - start.x
                val deltaY = e2.y - start.y
                if (kotlin.math.abs(deltaX) < dpF(48f) || kotlin.math.abs(deltaX) < kotlin.math.abs(deltaY)) {
                    return false
                }
                if (deltaX < 0f) {
                    navigateToNextSurface()
                } else {
                    navigateToPreviousSurface()
                }
                return true
            }
        },
    )

    init {
        clipChildren = false
        clipToPadding = false
        setBackgroundColor(Color.TRANSPARENT)

        addView(scrimView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(homeAtmosphereView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(pageContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(dockContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        addView(searchPillView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        addView(pageIndicatorView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        addView(removeTargetView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        addView(drawerPanel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(spotlightPanel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(folderPanel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(editSheetView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(actionSheetView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        setupContainers()
        setupPanels()
        setupRemoveTarget()
        pageIndicatorView.onVisibilityChanged = { showing ->
            val state = currentState
            val homeVisible = state?.surfaceMode == LauncherSurfaceMode.HOME
            if (homeVisible) {
                searchPillView.isVisible = !showing && state.openFolder == null && state.actionSheet == null && state.editSheet == null
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val gestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures())
            systemTopInset = bars.top
            systemBottomInset = bars.bottom
            gestureBottomInset = gestures.bottom
            applyInsets()
            insets
        }
    }

    fun setCallbacks(callbacks: LauncherRootCallbacks) {
        this.callbacks = callbacks
    }

    fun setWidgetViewBinder(widgetViewBinder: WidgetViewBinder) {
        this.widgetViewBinder = widgetViewBinder
        pageContainer.widgetViewBinder = widgetViewBinder
        dockContainer.widgetViewBinder = widgetViewBinder
    }

    fun beginWorkspaceItemMove(itemId: String): Boolean {
        val sourceView = pageContainer.findChildViewByItemId(itemId)
            ?: dockContainer.findChildViewByItemId(itemId)
            ?: return false
        startWorkspaceDrag(itemId, sourceView)
        return true
    }

    fun render(state: LauncherUiState) {
        val previous = currentState
        currentState = state

        val appLibraryVisible = state.surfaceMode == LauncherSurfaceMode.APP_LIBRARY_OVERVIEW ||
            state.surfaceMode == LauncherSurfaceMode.APP_LIBRARY_SEARCH
        val spotlightVisible = state.surfaceMode == LauncherSurfaceMode.SPOTLIGHT
        val overlayVisible = spotlightVisible || state.openFolder != null || state.actionSheet != null || state.editSheet != null
        val homeVisible = state.surfaceMode == LauncherSurfaceMode.HOME
        val homeChromeVisible = homeVisible && !overlayVisible
        searchPillView.isVisible = homeVisible && !overlayVisible && !pageIndicatorView.isShowing
        pageIndicatorView.isVisible = homeVisible && pageIndicatorView.isShowing
        dockContainer.isVisible = (homeVisible || appLibraryVisible) && state.openFolder == null

        pageContainer.submitItems(state.pageItems.map { it.toRenderCell() })
        dockContainer.submitItems(state.dockItems.map { it.toRenderCell() })

        drawerPanel.render(state.appLibrary)
        spotlightPanel.render(state.spotlight)
        folderPanel.render(state.openFolder)
        editSheetView.render(state.editSheet)
        actionSheetView.render(state.actionSheet)

        val drawerOpenChanged = previous?.drawerOpen != state.drawerOpen
        val spotlightChanged = previous?.surfaceMode != state.surfaceMode &&
            (previous?.surfaceMode == LauncherSurfaceMode.SPOTLIGHT || spotlightVisible)
        val pageChanged = previous?.currentHomePageIndex != state.currentHomePageIndex
        val folderOpenChanged = previous?.openFolder?.folderId != state.openFolder?.folderId
        val editSheetChanged = previous?.editSheet != state.editSheet
        val actionSheetChanged = previous?.actionSheet != state.actionSheet

        if (drawerOpenChanged) {
            animateAppLibraryTransition(open = state.drawerOpen, animated = previous != null, showHomeChrome = homeChromeVisible)
            drawerPanel.setOpen(state.drawerOpen, animated = previous != null)
        } else {
            syncAppLibraryTransition(open = state.drawerOpen, showHomeChrome = homeChromeVisible)
            drawerPanel.syncOpenState(state.drawerOpen)
        }

        if (spotlightChanged) {
            spotlightPanel.setOpen(spotlightVisible, animated = previous != null)
        } else {
            spotlightPanel.syncOpenState(spotlightVisible)
        }

        if (folderOpenChanged) {
            folderPanel.setOpen(state.openFolder != null, animated = previous != null)
        } else {
            folderPanel.syncOpenState(state.openFolder != null)
        }

        if (editSheetChanged) {
            editSheetView.setOpen(state.editSheet != null, animated = previous != null)
        } else {
            editSheetView.syncOpenState(state.editSheet != null)
        }

        if (actionSheetChanged) {
            actionSheetView.setOpen(state.actionSheet != null, animated = previous != null)
        } else {
            actionSheetView.syncOpenState(state.actionSheet != null)
        }

        if (pageChanged && homeVisible && previous?.surfaceMode == LauncherSurfaceMode.HOME) {
            animateHomePageChange(state.currentHomePageIndex > (previous.currentHomePageIndex))
            if (state.homePages.size > 1) {
                pageIndicatorView.render(state.homePages.size, state.currentHomePageIndex)
                pageIndicatorView.flash()
                searchPillView.isVisible = false
            }
        } else {
            pageIndicatorView.render(state.homePages.size, state.currentHomePageIndex)
        }

        val shouldShowScrim = spotlightVisible || state.openFolder != null || state.actionSheet != null || state.editSheet != null
        if (shouldShowScrim != scrimView.isVisible || spotlightChanged || folderOpenChanged || editSheetChanged || actionSheetChanged) {
            animateScrim(shouldShowScrim)
        }
    }

    override fun onDragEvent(event: DragEvent): Boolean {
        if (event.action == DragEvent.ACTION_DRAG_ENDED) {
            setRemoveTargetVisible(false)
        }
        return super.onDragEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                navigationStartX = ev.x
                navigationStartY = ev.y
            }

            MotionEvent.ACTION_UP -> {
                val deltaX = ev.x - navigationStartX
                val deltaY = ev.y - navigationStartY
                if (kotlin.math.abs(deltaX) > dpF(84f) && kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY)) {
                    if (deltaX < 0f) {
                        navigateToNextSurface()
                    } else {
                        navigateToPreviousSurface()
                    }
                    return true
                }
            }
        }
        navigationGestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun setupContainers() {
        pageContainer.widgetViewBinder = widgetViewBinder
        pageContainer.onEmptyLongPress = { callbacks?.onRequestWorkspaceMenu() }
        pageContainer.onCellClicked = { cell, source ->
            when (cell) {
                is RenderCell.FolderCell -> callbacks?.onOpenFolder(cell.id, source.viewAnchor())
                is RenderCell.ShortcutCell -> callbacks?.onLaunchApp(cell.componentName)
                is RenderCell.WidgetCell, RenderCell.PlaceholderCell -> Unit
            }
        }
        pageContainer.onCellLongPressed = { cell, source ->
            when (cell) {
                is RenderCell.AppActionCell -> {
                    source.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    callbacks?.onRequestWorkspaceItemActions(cell.id, source.viewAnchor())
                }
                is RenderCell.FolderCell -> startWorkspaceDrag(cell.id, source)
                RenderCell.PlaceholderCell -> Unit
            }
        }
        pageContainer.onDrop = { target, targetItemId, payload ->
            handleDrop(target, targetItemId, payload)
        }

        dockContainer.widgetViewBinder = widgetViewBinder
        dockContainer.onCellClicked = pageContainer.onCellClicked
        dockContainer.onCellLongPressed = pageContainer.onCellLongPressed
        dockContainer.onDrop = pageContainer.onDrop

        folderPanel.onClose = { callbacks?.onCloseFolder() }
        folderPanel.onAppClick = { item -> callbacks?.onLaunchApp(item.componentName) }
        folderPanel.onStartWorkspaceDrag = { itemId, source -> startWorkspaceDrag(itemId, source) }
        folderPanel.onRenameCommitted = { folderId, title -> callbacks?.onConfirmRenameFolder(folderId, title) }
        folderPanel.onDrop = { target, targetItemId, payload ->
            handleDrop(target, targetItemId, payload)
        }
    }

    private fun setupPanels() {
        drawerPanel.onQueryChanged = { query -> callbacks?.onAppLibraryQueryChanged(query) }
        drawerPanel.onAppClick = { app -> callbacks?.onLaunchApp(app.componentName) }
        drawerPanel.onAppLongPress = { app, source ->
            source.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            callbacks?.onRequestDrawerItemActions(app, source.viewAnchor())
        }
        drawerPanel.setOnTouchListener(object : OnSwipeTouchListener(context) {
            override fun onSwipeRight() {
                callbacks?.onCloseAppLibrary()
            }
        })
        spotlightPanel.onQueryChanged = { query -> callbacks?.onSpotlightQueryChanged(query) }
        spotlightPanel.onAppClick = { app -> callbacks?.onLaunchApp(app.componentName) }
        spotlightPanel.onAppLongPress = { app, source ->
            source.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            callbacks?.onRequestDrawerItemActions(app, source.viewAnchor())
        }
        editSheetView.onActionClick = { actionId -> callbacks?.onSelectEditSheetAction(actionId) }
        actionSheetView.onActionClick = { actionId -> callbacks?.onSelectActionSheetAction(actionId) }
        searchPillView.onOpenSearch = { callbacks?.onOpenSpotlight() }
        drawerPanel.onEnterSearchMode = { callbacks?.onEnterAppLibrarySearch() }
    }

    private fun setupRemoveTarget() {
        removeTargetView.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> event.localState is DragPayload.WorkspaceItem
                DragEvent.ACTION_DRAG_ENTERED -> {
                    removeTargetView.animate().scaleX(1.04f).scaleY(1.04f).setDuration(90L).start()
                    true
                }

                DragEvent.ACTION_DRAG_EXITED -> {
                    removeTargetView.animate().scaleX(1f).scaleY(1f).setDuration(90L).start()
                    true
                }

                DragEvent.ACTION_DROP -> {
                    val payload = event.localState as? DragPayload.WorkspaceItem ?: return@setOnDragListener false
                    callbacks?.onRemoveWorkspaceItem(payload.itemId)
                    setRemoveTargetVisible(false)
                    true
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    removeTargetView.animate().scaleX(1f).scaleY(1f).setDuration(90L).start()
                    true
                }

                else -> true
            }
        }
    }

    private fun handleDrop(
        target: LauncherDropTarget,
        targetItemId: String?,
        payload: DragPayload,
    ) {
        when (payload) {
            is DragPayload.DrawerApp -> {
                callbacks?.onDropDrawerApp(payload.app, target, targetItemId)
                callbacks?.onCloseAppLibrary()
            }

            is DragPayload.WorkspaceItem -> callbacks?.onDropWorkspaceItem(payload.itemId, target, targetItemId)
        }
        setRemoveTargetVisible(false)
    }

    private fun startWorkspaceDrag(itemId: String, source: View) {
        source.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        setRemoveTargetVisible(true)
        source.startDragAndDrop(
            ClipData.newPlainText("workspace-item", itemId),
            DragShadowBuilder(source),
            DragPayload.WorkspaceItem(itemId),
            0,
        )
    }

    private fun navigateToNextSurface() {
        val state = currentState ?: return
        if (state.surfaceMode != LauncherSurfaceMode.HOME || state.openFolder != null || state.actionSheet != null || state.editSheet != null) {
            return
        }
        if (state.currentHomePageIndex < state.homePages.lastIndex) {
            callbacks?.onNavigateHome(state.currentHomePageIndex + 1)
        } else {
            callbacks?.onOpenAppLibrary()
        }
    }

    private fun navigateToPreviousSurface() {
        val state = currentState ?: return
        when {
            state.surfaceMode == LauncherSurfaceMode.HOME && state.currentHomePageIndex > 0 -> {
                callbacks?.onNavigateHome(state.currentHomePageIndex - 1)
            }

            state.drawerOpen -> callbacks?.onCloseAppLibrary()
        }
    }

    private fun animateHomePageChange(movingForward: Boolean) {
        val offset = if (movingForward) dp(32).toFloat() else -dp(32).toFloat()
        pageContainer.translationX = offset
        pageContainer.alpha = 0.82f
        pageContainer.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(LauncherMotionSpecs.pageTransitionDurationMillis)
            .setInterpolator(LauncherMotionSpecs.standardInterpolator)
            .start()
    }

    private fun animateAppLibraryTransition(
        open: Boolean,
        animated: Boolean,
        showHomeChrome: Boolean,
    ) {
        if (!animated) {
            syncAppLibraryTransition(open, showHomeChrome)
            return
        }
        val widthPx = width.takeIf { it > 0 }?.toFloat() ?: resources.displayMetrics.widthPixels.toFloat()
        val pageOffset = if (open) -(widthPx * LauncherVisualSpec.Pager.pageTravelFraction) else 0f
        val atmosphereOffset = if (open) -(widthPx * LauncherVisualSpec.Pager.atmosphereTravelFraction) else 0f
        val chromeOffset = if (open) -(widthPx * 0.84f) else 0f

        if (open) {
            dockContainer.isVisible = currentState?.openFolder == null
            searchPillView.isVisible = true
            pageIndicatorView.isVisible = pageIndicatorView.isShowing
        } else {
            dockContainer.isVisible = currentState?.openFolder == null
            searchPillView.isVisible = showHomeChrome && !pageIndicatorView.isShowing
            pageIndicatorView.isVisible = showHomeChrome && pageIndicatorView.isShowing
        }

        listOf(pageContainer, homeAtmosphereView, dockContainer, searchPillView, pageIndicatorView).forEach {
            it.animate().cancel()
        }

        pageContainer.animate()
            .translationX(pageOffset)
            .setDuration(LauncherMotionSpecs.pageTransitionDurationMillis)
            .setInterpolator(LauncherMotionSpecs.standardInterpolator)
            .start()

        homeAtmosphereView.animate()
            .translationX(atmosphereOffset)
            .alpha(if (open) 0.78f else 1f)
            .setDuration(LauncherMotionSpecs.pageTransitionDurationMillis)
            .setInterpolator(LauncherMotionSpecs.standardInterpolator)
            .start()

        dockContainer.animate()
            .translationX(chromeOffset)
            .alpha(if (open) LauncherVisualSpec.Pager.dockHiddenAlpha else 1f)
            .setDuration(LauncherMotionSpecs.pageTransitionDurationMillis)
            .setInterpolator(LauncherMotionSpecs.standardInterpolator)
            .withEndAction {
                dockContainer.isVisible = !open && currentState?.openFolder == null
            }
            .start()

        searchPillView.animate()
            .translationX(chromeOffset)
            .alpha(if (open) LauncherVisualSpec.Pager.hiddenAlpha else 1f)
            .setDuration(LauncherMotionSpecs.pageTransitionDurationMillis)
            .setInterpolator(LauncherMotionSpecs.standardInterpolator)
            .withEndAction {
                searchPillView.isVisible = !open && showHomeChrome && !pageIndicatorView.isShowing
            }
            .start()

        pageIndicatorView.animate()
            .translationX(chromeOffset)
            .alpha(if (open) LauncherVisualSpec.Pager.hiddenAlpha else if (pageIndicatorView.isShowing) 1f else 0f)
            .setDuration(LauncherMotionSpecs.pageTransitionDurationMillis)
            .setInterpolator(LauncherMotionSpecs.standardInterpolator)
            .withEndAction {
                pageIndicatorView.isVisible = !open && showHomeChrome && pageIndicatorView.isShowing
            }
            .start()
    }

    private fun syncAppLibraryTransition(
        open: Boolean,
        showHomeChrome: Boolean,
    ) {
        val widthPx = width.takeIf { it > 0 }?.toFloat() ?: resources.displayMetrics.widthPixels.toFloat()
        pageContainer.translationX = if (open) -(widthPx * LauncherVisualSpec.Pager.pageTravelFraction) else 0f
        homeAtmosphereView.translationX = if (open) -(widthPx * LauncherVisualSpec.Pager.atmosphereTravelFraction) else 0f
        homeAtmosphereView.alpha = if (open) 0.78f else 1f

        val chromeOffset = if (open) -(widthPx * 0.84f) else 0f
        dockContainer.translationX = chromeOffset
        dockContainer.alpha = if (open) LauncherVisualSpec.Pager.dockHiddenAlpha else 1f
        dockContainer.isVisible = !open && currentState?.openFolder == null

        searchPillView.translationX = chromeOffset
        searchPillView.alpha = if (open) LauncherVisualSpec.Pager.hiddenAlpha else 1f
        searchPillView.isVisible = !open && showHomeChrome && !pageIndicatorView.isShowing

        pageIndicatorView.translationX = chromeOffset
        pageIndicatorView.alpha = if (open) LauncherVisualSpec.Pager.hiddenAlpha else if (pageIndicatorView.isShowing) 1f else 0f
        pageIndicatorView.isVisible = !open && showHomeChrome && pageIndicatorView.isShowing
    }

    private fun animateScrim(show: Boolean) {
        scrimView.isVisible = true
        scrimView.animate().cancel()
        scrimView.animate()
            .alpha(if (show) LauncherMotionSpecs.scrimAlpha else 0f)
            .setDuration(if (show) LauncherMotionSpecs.spotlightDurationMillis else 160L)
            .setInterpolator(
                if (show) LauncherMotionSpecs.standardInterpolator else AccelerateInterpolator(),
            )
            .withEndAction { scrimView.isVisible = show }
            .start()
    }

    private fun applyInsets() {
        (removeTargetView.layoutParams as LayoutParams).topMargin = systemTopInset + dp(18)

        pageContainer.setPadding(
            dp(LauncherVisualSpec.homeSidePaddingDp),
            systemTopInset + dp(LauncherVisualSpec.homeTopPaddingDp),
            dp(LauncherVisualSpec.homeSidePaddingDp),
            systemBottomInset + gestureBottomInset + dp(LauncherVisualSpec.homeBottomPaddingDp),
        )

        (dockContainer.layoutParams as LayoutParams).apply {
            leftMargin = dp(LauncherVisualSpec.dockSideMarginDp)
            rightMargin = dp(LauncherVisualSpec.dockSideMarginDp)
            bottomMargin = systemBottomInset + gestureBottomInset + dp(LauncherVisualSpec.dockBottomMarginDp)
        }
        dockContainer.setPadding(
            dp(LauncherVisualSpec.dockHorizontalPaddingDp),
            dp(LauncherVisualSpec.dockVerticalPaddingDp),
            dp(LauncherVisualSpec.dockHorizontalPaddingDp),
            dp(LauncherVisualSpec.dockVerticalPaddingDp),
        )

        val searchPillLiftDp = LauncherVisualSpec.itemIconSizeDp +
            (LauncherVisualSpec.dockVerticalPaddingDp * 2)
        (searchPillView.layoutParams as LayoutParams).bottomMargin =
            systemBottomInset + gestureBottomInset +
            dp(LauncherVisualSpec.dockBottomMarginDp + LauncherVisualSpec.searchPillBottomSpacingDp + searchPillLiftDp)
        (pageIndicatorView.layoutParams as LayoutParams).bottomMargin =
            systemBottomInset + gestureBottomInset +
            dp(LauncherVisualSpec.dockBottomMarginDp + LauncherVisualSpec.searchPillBottomSpacingDp + searchPillLiftDp + 2)

        drawerPanel.updateInsets(systemTopInset, systemBottomInset + gestureBottomInset)
        spotlightPanel.updateInsets(systemTopInset, systemBottomInset + gestureBottomInset)
        folderPanel.updateInsets(systemTopInset, systemBottomInset + gestureBottomInset)
        editSheetView.updateInsets(systemBottomInset + gestureBottomInset)
        actionSheetView.updateInsets(systemTopInset, systemBottomInset + gestureBottomInset)
        requestLayout()
    }

    private fun setRemoveTargetVisible(visible: Boolean) {
        removeTargetView.isVisible = visible
        removeTargetView.animate().cancel()
        if (visible) {
            removeTargetView.alpha = 0f
            removeTargetView.scaleX = 0.92f
            removeTargetView.scaleY = 0.92f
            removeTargetView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(LauncherMotionSpecs.folderDurationMillis)
                .setInterpolator(LauncherMotionSpecs.emphasizedInterpolator)
                .start()
        }
    }
}

private sealed interface DragPayload {
    data class DrawerApp(val app: DrawerAppItem) : DragPayload
    data class WorkspaceItem(val itemId: String) : DragPayload
}

private sealed interface RenderCell {
    val id: String
    val title: String
    val spanX: Int
        get() = 1
    val spanY: Int
        get() = 1

    sealed interface AppActionCell : RenderCell

    data class ShortcutCell(
        override val id: String,
        override val title: String,
        val componentName: ComponentName,
        val icon: Drawable?,
    ) : AppActionCell

    data class FolderCell(
        override val id: String,
        override val title: String,
        val previewIcons: List<Drawable?>,
    ) : RenderCell

    data class WidgetCell(
        val widget: WorkspaceSurfaceItem.Widget,
    ) : AppActionCell {
        override val id: String = widget.id
        override val title: String = widget.title
        override val spanX: Int = widget.spanX
        override val spanY: Int = widget.spanY
    }

    data object PlaceholderCell : RenderCell {
        override val id: String = "__preview__"
        override val title: String = ""
    }
}

private fun WorkspaceSurfaceItem.toRenderCell(): RenderCell {
    return when (this) {
        is WorkspaceSurfaceItem.AppShortcut -> RenderCell.ShortcutCell(id, title, componentName, icon)
        is WorkspaceSurfaceItem.Folder -> RenderCell.FolderCell(id, title, previewIcons)
        is WorkspaceSurfaceItem.Widget -> RenderCell.WidgetCell(this)
    }
}

private class ItemContainerView(
    context: Context,
    private val kind: LauncherContainerKind,
    private val columns: Int = 4,
) : ViewGroup(context) {

    var onCellClicked: ((RenderCell, View) -> Unit)? = null
    var onCellLongPressed: ((RenderCell, View) -> Unit)? = null
    var onEmptyLongPress: (() -> Unit)? = null
    var onDrop: ((LauncherDropTarget, String?, DragPayload) -> Unit)? = null
    var widgetViewBinder: WidgetViewBinder? = null

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        style = Paint.Style.FILL
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5CFFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = dpF(1.5f)
    }
    private val highlightRect = RectF()

    private var items: List<RenderCell> = emptyList()
    private var highlightedIndex: Int? = null
    private var highlightedTargetItemId: String? = null
    private var slotWidth = 0
    private var slotHeight = 0
    private var slotSpacing = dp(LauncherVisualSpec.gridSpacingDp)
    private var layoutSlots: List<LayoutSlot> = emptyList()

    init {
        clipChildren = false
        clipToPadding = false
        isLongClickable = true
        setOnLongClickListener {
            onEmptyLongPress?.invoke()
            onEmptyLongPress != null
        }
    }

    fun submitItems(items: List<RenderCell>) {
        this.items = items
        removeAllViews()
        items.forEach { cell ->
            val child = when (cell) {
                is RenderCell.ShortcutCell, is RenderCell.FolderCell -> LauncherItemView(
                    context = context,
                    showLabel = kind != LauncherContainerKind.DOCK,
                ).apply {
                    bind(cell)
                }

                is RenderCell.WidgetCell -> WidgetCellView(context).apply {
                    bind(cell, widgetViewBinder)
                }

                RenderCell.PlaceholderCell -> View(context)
            }
            child.tag = cell.id
            child.setOnClickListener { onCellClicked?.invoke(cell, child) }
            child.setOnLongClickListener {
                onCellLongPressed?.invoke(cell, child)
                onCellLongPressed != null
            }
            addView(child)
        }
        requestLayout()
    }

    fun findChildViewByItemId(itemId: String): View? {
        return (0 until childCount)
            .map(::getChildAt)
            .firstOrNull { it.tag == itemId }
    }

    override fun onDragEvent(event: DragEvent): Boolean {
        val payload = event.localState as? DragPayload ?: return false
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> return true
            DragEvent.ACTION_DRAG_LOCATION -> {
                highlightedIndex = computeDropIndex(event.x, event.y)
                highlightedTargetItemId = findTargetItemId(event.x, event.y)
                invalidate()
                return true
            }

            DragEvent.ACTION_DRAG_EXITED -> {
                highlightedIndex = null
                highlightedTargetItemId = null
                invalidate()
                return true
            }

            DragEvent.ACTION_DROP -> {
                val target = LauncherDropTarget(
                    container = kind,
                    position = computeDropIndex(event.x, event.y),
                    containerId = null,
                    page = 0,
                )
                onDrop?.invoke(target, findTargetItemId(event.x, event.y), payload)
                highlightedIndex = null
                highlightedTargetItemId = null
                invalidate()
                return true
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                highlightedIndex = null
                highlightedTargetItemId = null
                invalidate()
                return true
            }

            else -> return true
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val highlight = highlightedTargetItemId?.let { id ->
            layoutSlots.firstOrNull { it.id == id }?.rect
        } ?: highlightedIndex?.let(::previewRectForInsertIndex)

        highlight?.let { rect ->
            highlightRect.set(rect)
            canvas.drawRoundRect(highlightRect, dpF(18f), dpF(18f), highlightPaint)
            canvas.drawRoundRect(highlightRect, dpF(18f), dpF(18f), outlinePaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val availableWidth = width - paddingLeft - paddingRight
        val columns = columnCount()
        slotWidth = ((availableWidth - ((columns - 1) * slotSpacing)) / columns).coerceAtLeast(dp(68))
        slotHeight = if (kind == LauncherContainerKind.DOCK) {
            slotWidth
        } else {
            slotWidth + dp(LauncherVisualSpec.gridSlotExtraHeightDp)
        }
        layoutSlots = computeLayoutSlots(items)

        repeat(childCount) { index ->
            val child = getChildAt(index)
            val slot = layoutSlots.getOrNull(index)
            child.measure(
                MeasureSpec.makeMeasureSpec(slot?.rect?.width() ?: slotWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(slot?.rect?.height() ?: slotHeight, MeasureSpec.EXACTLY),
            )
        }

        val contentHeight = layoutSlots.maxOfOrNull { it.rect.bottom } ?: (paddingTop + slotHeight)
        val measuredHeight = paddingBottom + contentHeight
        setMeasuredDimension(width, resolveSize(measuredHeight, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        repeat(childCount) { index ->
            val rect = layoutSlots.getOrNull(index)?.rect ?: return@repeat
            getChildAt(index).layout(rect.left, rect.top, rect.right, rect.bottom)
        }
    }

    private fun findTargetItemId(x: Float, y: Float): String? {
        return layoutSlots.firstOrNull { slot ->
            x >= slot.rect.left && x <= slot.rect.right && y >= slot.rect.top && y <= slot.rect.bottom
        }?.id
    }

    private fun computeDropIndex(x: Float, y: Float): Int {
        if (layoutSlots.isEmpty()) {
            return 0
        }
        layoutSlots.forEachIndexed { index, slot ->
            val centerY = slot.rect.centerY().toFloat()
            val centerX = slot.rect.centerX().toFloat()
            if (y < centerY || (y <= slot.rect.bottom && x < centerX)) {
                return index
            }
        }
        return items.size
    }

    private fun previewRectForInsertIndex(index: Int): Rect? {
        if (kind == LauncherContainerKind.DOCK) {
            val slotIndex = index.coerceIn(0, 3)
            return computeDockRect(slotIndex)
        }
        val previewItems = items.toMutableList()
        previewItems.add(index.coerceIn(0, items.size), RenderCell.PlaceholderCell)
        return computeLayoutSlots(previewItems).getOrNull(index)?.rect
    }

    private fun computeLayoutSlots(cells: List<RenderCell>): List<LayoutSlot> {
        return when (kind) {
            LauncherContainerKind.DOCK -> cells.mapIndexed { index, cell ->
                LayoutSlot(id = cell.id, rect = computeDockRect(index), index = index)
            }

            else -> {
                val occupied = mutableSetOf<GridCell>()
                val result = mutableListOf<LayoutSlot>()
                cells.forEachIndexed { index, cell ->
                    val position = findNextFreeCell(occupied, cell.spanX.coerceIn(1, columnCount()), cell.spanY.coerceAtLeast(1))
                    occupy(occupied, position.row, position.column, cell.spanX.coerceIn(1, columnCount()), cell.spanY.coerceAtLeast(1))
                    result += LayoutSlot(
                        id = cell.id,
                        rect = Rect(
                            paddingLeft + (position.column * (slotWidth + slotSpacing)),
                            paddingTop + (position.row * (slotHeight + slotSpacing)),
                            paddingLeft + (position.column * (slotWidth + slotSpacing)) +
                                (cell.spanX * slotWidth) + ((cell.spanX - 1) * slotSpacing),
                            paddingTop + (position.row * (slotHeight + slotSpacing)) +
                                (cell.spanY * slotHeight) + ((cell.spanY - 1) * slotSpacing),
                        ),
                        index = index,
                    )
                }
                result
            }
        }
    }

    private fun findNextFreeCell(
        occupied: Set<GridCell>,
        spanX: Int,
        spanY: Int,
    ): GridPosition {
        var row = 0
        while (row < 24) {
            for (column in 0..(columnCount() - spanX)) {
                if (canOccupy(occupied, row, column, spanX, spanY)) {
                    return GridPosition(row, column)
                }
            }
            row++
        }
        return GridPosition(row, 0)
    }

    private fun canOccupy(
        occupied: Set<GridCell>,
        row: Int,
        column: Int,
        spanX: Int,
        spanY: Int,
    ): Boolean {
        repeat(spanY) { rowOffset ->
            repeat(spanX) { columnOffset ->
                if (occupied.contains(GridCell(row + rowOffset, column + columnOffset))) {
                    return false
                }
            }
        }
        return true
    }

    private fun occupy(
        occupied: MutableSet<GridCell>,
        row: Int,
        column: Int,
        spanX: Int,
        spanY: Int,
    ) {
        repeat(spanY) { rowOffset ->
            repeat(spanX) { columnOffset ->
                occupied += GridCell(row + rowOffset, column + columnOffset)
            }
        }
    }

    private fun computeDockRect(index: Int): Rect {
        val column = index.coerceIn(0, 3)
        val left = paddingLeft + (column * (slotWidth + slotSpacing))
        val top = paddingTop
        return Rect(left, top, left + slotWidth, top + slotHeight)
    }

    private fun columnCount(): Int = columns

    private data class LayoutSlot(
        val id: String,
        val rect: Rect,
        val index: Int,
    )

    private data class GridCell(
        val row: Int,
        val column: Int,
    )

    private data class GridPosition(
        val row: Int,
        val column: Int,
    )
}

private class LauncherItemView(
    context: Context,
    private val showLabel: Boolean,
) : LinearLayout(context) {

    private val iconView = AppCompatImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    private val labelView = AppCompatTextView(context).apply {
        gravity = Gravity.CENTER
        maxLines = 1
        textSize = LauncherVisualSpec.itemLabelTextSizeSp
        setTextColor(Color.WHITE)
        setShadowLayer(dpF(6f), 0f, dpF(2f), Color.parseColor("#B0000000"))
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipToPadding = false
        clipChildren = false
        addView(iconView, LayoutParams(dp(LauncherVisualSpec.itemIconSizeDp), dp(LauncherVisualSpec.itemIconSizeDp)))
        addView(
            labelView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(LauncherVisualSpec.itemLabelGapDp)
            },
        )
        outlineProvider = ViewOutlineProvider.BACKGROUND
        setOnTouchListener(PressScaleTouchListener())
        labelView.isVisible = showLabel
    }

    fun bind(cell: RenderCell) {
        labelView.text = cell.title
        when (cell) {
            is RenderCell.ShortcutCell -> {
                iconView.background = null
                iconView.setImageDrawable(cell.icon)
            }

            is RenderCell.FolderCell -> {
                iconView.setImageDrawable(buildFolderPreview(cell.previewIcons))
            }

            else -> Unit
        }
    }

    private fun buildFolderPreview(previewIcons: List<Drawable?>): Drawable {
        val background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            colors = intArrayOf(
                Color.parseColor("#47F2F6FD"),
                Color.parseColor("#2ADAE5F1"),
            )
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            setStroke(dp(1), Color.parseColor("#36FFFFFF"))
        }
        val layers = mutableListOf<Drawable>(background)
        previewIcons.take(4).forEachIndexed { index, drawable ->
            val copy = drawable?.constantState?.newDrawable()?.mutate() ?: return@forEachIndexed
            val inset = when (index) {
                0 -> InsetDrawable(copy, dp(11), dp(11), dp(35), dp(35))
                1 -> InsetDrawable(copy, dp(35), dp(11), dp(11), dp(35))
                2 -> InsetDrawable(copy, dp(11), dp(35), dp(35), dp(11))
                else -> InsetDrawable(copy, dp(35), dp(35), dp(11), dp(11))
            }
            layers += inset
        }
        return LayerDrawable(layers.toTypedArray())
    }
}

private class WidgetCellView(context: Context) : FrameLayout(context) {

    private val hostContainer = FrameLayout(context).apply {
        background = roundedDrawable("#16FFFFFF")
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
    }

    init {
        setPadding(0, dp(4), 0, dp(4))
        addView(hostContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun bind(
        cell: RenderCell.WidgetCell,
        widgetViewBinder: WidgetViewBinder?,
    ) {
        hostContainer.removeAllViews()
        if (widgetViewBinder == null) {
            hostContainer.addView(buildFallbackPlaceholder(context, cell.widget.title))
            return
        }
        widgetViewBinder.bind(hostContainer, cell.widget)
        if (hostContainer.childCount == 0) {
            hostContainer.addView(buildFallbackPlaceholder(context, cell.widget.title))
        }
    }

    private fun buildFallbackPlaceholder(context: Context, title: String): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(18), dp(18), dp(18))
            addView(
                AppCompatTextView(context).apply {
                    text = title
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                },
            )
            addView(
                AppCompatTextView(context).apply {
                    text = context.getString(R.string.launcher_widget_unavailable)
                    textSize = 12f
                    setTextColor(Color.parseColor("#B8FFFFFF"))
                    gravity = Gravity.CENTER
                },
            )
        }
    }
}

private class PressScaleTouchListener : View.OnTouchListener {
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.animate()
                    .scaleX(LauncherMotionSpecs.pressScale)
                    .scaleY(LauncherMotionSpecs.pressScale)
                    .setDuration(LauncherMotionSpecs.pressDurationMillis)
                    .setInterpolator(LauncherMotionSpecs.standardInterpolator)
                    .start()
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(LauncherMotionSpecs.pressDurationMillis)
                    .setInterpolator(LauncherMotionSpecs.standardInterpolator)
                    .start()
            }
        }
        return false
    }
}

private class HomePageIndicatorView(context: Context) : LinearLayout(context) {

    private var hideRunnable: Runnable? = null
    var onVisibilityChanged: ((Boolean) -> Unit)? = null
    var isShowing: Boolean = false
        private set

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        alpha = 0f
        isVisible = false
    }

    fun render(pageCount: Int, currentPageIndex: Int) {
        removeAllViews()
        if (pageCount <= 1) {
            isShowing = false
            isVisible = false
            alpha = 0f
            onVisibilityChanged?.invoke(false)
            return
        }
        repeat(pageCount) { index ->
            addView(
                View(context).apply {
                    background = pillDrawable(
                        if (index == currentPageIndex) "#F7FFFFFF" else "#47FFFFFF",
                    )
                },
                LayoutParams(
                    if (index == currentPageIndex) dp(20) else dp(5),
                    dp(5),
                ).apply {
                    if (index > 0) {
                        marginStart = dp(5)
                    }
                },
            )
        }
    }

    fun flash() {
        hideRunnable?.let(::removeCallbacks)
        isShowing = true
        isVisible = true
        onVisibilityChanged?.invoke(true)
        animate().cancel()
        animate()
            .alpha(1f)
            .setDuration(120L)
            .setInterpolator(LauncherMotionSpecs.standardInterpolator)
            .start()
        hideRunnable = Runnable {
            animate().cancel()
            animate()
                .alpha(0f)
                .setDuration(160L)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    isShowing = false
                    isVisible = false
                    onVisibilityChanged?.invoke(false)
                }
                .start()
        }.also { postDelayed(it, 1_200L) }
    }
}

private class HomeSearchPillView(context: Context) : LinearLayout(context) {

    var onOpenSearch: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = searchPillDrawable()
        elevation = dp(8).toFloat()
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.launcher_home_search_pill_description)
        minimumWidth = dp(LauncherVisualSpec.HomeSearchControl.minWidthDp)
        setPadding(
            dp(LauncherVisualSpec.HomeSearchControl.horizontalPaddingDp),
            dp(LauncherVisualSpec.HomeSearchControl.verticalPaddingDp),
            dp(LauncherVisualSpec.HomeSearchControl.horizontalPaddingDp),
            dp(LauncherVisualSpec.HomeSearchControl.verticalPaddingDp),
        )
        addView(
            AppCompatImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_search)
                setColorFilter(Color.parseColor("#2A3648"))
            },
            LayoutParams(
                dp(LauncherVisualSpec.HomeSearchControl.iconSizeDp),
                dp(LauncherVisualSpec.HomeSearchControl.iconSizeDp),
            ),
        )
        addView(
            AppCompatTextView(context).apply {
                text = context.getString(R.string.launcher_search_title)
                setTextColor(Color.parseColor("#2C3748"))
                textSize = LauncherVisualSpec.HomeSearchControl.textSizeSp
            },
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(LauncherVisualSpec.HomeSearchControl.iconGapDp)
            },
        )
        setOnClickListener { onOpenSearch?.invoke() }
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}

private class DrawerPanelView(context: Context) : FrameLayout(context) {

    var onQueryChanged: ((String) -> Unit)? = null
    var onAppClick: ((DrawerAppItem) -> Unit)? = null
    var onAppLongPress: ((DrawerAppItem, View) -> Unit)? = null
    var onEnterSearchMode: (() -> Unit)? = null

    private val surface = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = appLibrarySurfaceDrawable()
        elevation = dp(6).toFloat()
        setPadding(
            dp(LauncherVisualSpec.AppLibrary.horizontalPaddingDp),
            dp(LauncherVisualSpec.AppLibrary.topPaddingDp),
            dp(LauncherVisualSpec.AppLibrary.horizontalPaddingDp),
            0,
        )
    }
    private val titleView = AppCompatTextView(context).apply {
        text = context.getString(R.string.launcher_app_library_title)
        textSize = 13f
        setTextColor(Color.parseColor("#B6C6D7"))
        isAllCaps = true
        letterSpacing = 0.08f
    }
    private val searchField = androidx.appcompat.widget.AppCompatEditText(context).apply {
        hint = context.getString(R.string.launcher_drawer_hint)
        contentDescription = context.getString(R.string.launcher_app_library_search_field_description)
        setHintTextColor(Color.parseColor("#6F7E92"))
        setTextColor(Color.parseColor("#233244"))
        textSize = LauncherVisualSpec.AppLibrary.searchFieldTextSizeSp
        background = searchFieldSurfaceDrawable()
        setPadding(
            dp(18),
            dp(LauncherVisualSpec.AppLibrary.searchFieldVerticalPaddingDp),
            dp(18),
            dp(LauncherVisualSpec.AppLibrary.searchFieldVerticalPaddingDp),
        )
        setSingleLine(true)
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        setCompoundDrawablesRelativeWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0)
        compoundDrawablePadding = dp(10)
        compoundDrawablesRelative.firstOrNull()?.mutate()?.setTint(Color.parseColor("#6E7D90"))
    }
    private val overviewScroll = android.widget.ScrollView(context).apply {
        isFillViewport = true
        overScrollMode = OVER_SCROLL_NEVER
    }
    private val overviewContent = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(LauncherVisualSpec.AppLibrary.sectionSpacingDp), 0, dp(24))
    }
    private val topCollectionsRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.TOP
    }
    private val categoryBlocksContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    private val recyclerView = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context)
        overScrollMode = OVER_SCROLL_NEVER
        clipToPadding = false
        setPadding(0, dp(LauncherVisualSpec.AppLibrary.sectionSpacingDp), 0, dp(24))
    }
    private val emptyStateView = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(22), dp(42), dp(22), dp(22))
        background = roundedDrawable("#12FFFFFF")
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        isVisible = false
    }
    private val emptyTitleView = AppCompatTextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 18f
        gravity = Gravity.CENTER
    }
    private val emptySubtitleView = AppCompatTextView(context).apply {
        setTextColor(Color.parseColor("#A8D4DDEE"))
        textSize = 13f
        gravity = Gravity.CENTER
    }
    private val adapter = DrawerAdapter(
        onClick = { onAppClick?.invoke(it) },
        onLongClick = { app, source -> onAppLongPress?.invoke(app, source) },
    )

    private var topInset = 0
    private var bottomInset = 0
    private var state = AppLibraryUiState()
    private var suppressQueryCallbacks = false

    init {
        recyclerView.adapter = adapter
        overviewScroll.addView(
            overviewContent,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        overviewContent.addView(
            topCollectionsRow,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            },
        )
        overviewContent.addView(
            categoryBlocksContainer,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        emptyStateView.addView(emptyTitleView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        emptyStateView.addView(
            emptySubtitleView,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            },
        )

        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        surface.addView(titleView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        surface.addView(
            searchField,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            },
        )
        surface.addView(overviewScroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        surface.addView(recyclerView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        surface.addView(
            emptyStateView,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(18)
            },
        )
        alpha = 0f
        translationX = dp(420).toFloat()
        isVisible = false

        searchField.setOnClickListener {
            if (state.mode != AppLibraryMode.SEARCH) {
                onEnterSearchMode?.invoke()
            }
        }

        searchField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: android.text.Editable?) {
                if (suppressQueryCallbacks) {
                    return
                }
                onQueryChanged?.invoke(s?.toString().orEmpty())
            }
        })
    }

    fun render(state: AppLibraryUiState) {
        val previousMode = this.state.mode
        this.state = state
        val fieldValue = searchField.text?.toString().orEmpty()
        if (fieldValue != state.query) {
            suppressQueryCallbacks = true
            searchField.setText(state.query)
            searchField.setSelection(searchField.text?.length ?: 0)
            suppressQueryCallbacks = false
        }
        adapter.submitItems(state.searchRows)
        renderOverviewBlocks(state.overviewBlocks)
        val showSearchResults = state.mode == AppLibraryMode.SEARCH && state.searchRows.isNotEmpty()
        overviewScroll.isVisible = state.mode == AppLibraryMode.OVERVIEW
        recyclerView.isVisible = showSearchResults
        emptyStateView.isVisible = state.mode == AppLibraryMode.SEARCH && !showSearchResults && state.emptyState != null
        emptyTitleView.text = state.emptyState?.title.orEmpty()
        emptySubtitleView.text = state.emptyState?.subtitle.orEmpty()
        titleView.text = if (state.mode == AppLibraryMode.SEARCH) {
            context.getString(R.string.launcher_search_results_title)
        } else {
            context.getString(R.string.launcher_app_library_title)
        }
        titleView.alpha = if (state.mode == AppLibraryMode.SEARCH) 0.76f else 1f
        if (isVisible && previousMode != state.mode) {
            if (state.mode == AppLibraryMode.SEARCH) {
                focusSearchField()
            } else {
                clearSearchFocus()
            }
        }
    }

    fun setOpen(open: Boolean, animated: Boolean) {
        if (!animated) {
            syncOpenState(open)
            if (open) {
                if (state.mode == AppLibraryMode.SEARCH) {
                    focusSearchField()
                } else {
                    clearSearchFocus()
                }
            } else {
                clearSearchFocus()
            }
            return
        }
        isVisible = true
        animate().cancel()
        animate()
            .alpha(if (open) 1f else 0f)
            .translationX(if (open) 0f else width.toFloat().coerceAtLeast(dp(360).toFloat()))
            .setDuration(LauncherMotionSpecs.pageTransitionDurationMillis)
            .setInterpolator(LauncherMotionSpecs.standardInterpolator)
            .withEndAction {
                isVisible = open
                if (open) {
                    if (state.mode == AppLibraryMode.SEARCH) {
                        focusSearchField()
                    } else {
                        clearSearchFocus()
                    }
                } else {
                    clearSearchFocus()
                }
            }
            .start()
    }

    fun syncOpenState(open: Boolean) {
        isVisible = open
        alpha = if (open) 1f else 0f
        translationX = if (open) 0f else width.toFloat().takeIf { it > 0f } ?: dp(420).toFloat()
    }

    fun updateInsets(topInset: Int, bottomInset: Int) {
        this.topInset = topInset
        this.bottomInset = bottomInset
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        surface.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height - topInset - bottomInset, MeasureSpec.EXACTLY),
        )
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        surface.layout(
            0,
            topInset,
            surface.measuredWidth,
            topInset + surface.measuredHeight,
        )
    }

    private fun renderOverviewBlocks(blocks: List<AppLibraryOverviewBlockUiState>) {
        topCollectionsRow.removeAllViews()
        categoryBlocksContainer.removeAllViews()
        val primaryBlocks = blocks.filter {
            it is AppLibraryOverviewBlockUiState.Suggested || it is AppLibraryOverviewBlockUiState.RecentlyAdded
        }.take(2)
        primaryBlocks.forEachIndexed { index, block ->
            topCollectionsRow.addView(
                when (block) {
                    is AppLibraryOverviewBlockUiState.Suggested -> AppLibraryCollectionCardView(context).apply {
                        bind(block.title, block.apps, onAppClick, onAppLongPress)
                    }

                    is AppLibraryOverviewBlockUiState.RecentlyAdded -> AppLibraryCollectionCardView(context).apply {
                        bind(block.title, block.apps, onAppClick, onAppLongPress)
                    }

                    is AppLibraryOverviewBlockUiState.Category -> View(context)
                },
                LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) {
                        marginStart = dp(12)
                    }
                },
            )
        }
        if (primaryBlocks.size == 1) {
            topCollectionsRow.addView(
                View(context),
                LinearLayout.LayoutParams(0, 0, 1f).apply {
                    marginStart = dp(12)
                },
            )
        }

        val categories = blocks.filterIsInstance<AppLibraryOverviewBlockUiState.Category>()
        categories.chunked(2).forEachIndexed { rowIndex, rowBlocks ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            rowBlocks.forEachIndexed { blockIndex, block ->
                row.addView(
                    AppLibraryCategoryCardView(context).apply {
                        bind(
                            state = block,
                            onAppClick = { onAppClick?.invoke(it) },
                            onAppLongPress = { app, source -> onAppLongPress?.invoke(app, source) },
                        )
                    },
                    LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (blockIndex > 0) {
                            marginStart = dp(12)
                        }
                    },
                )
            }
            if (rowBlocks.size == 1) {
                row.addView(
                    View(context),
                    LinearLayout.LayoutParams(0, 0, 1f).apply {
                        marginStart = dp(12)
                    },
                )
            }
            categoryBlocksContainer.addView(
                row,
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    if (rowIndex > 0) {
                        topMargin = dp(LauncherVisualSpec.AppLibrary.gridSpacingDp)
                    }
                },
            )
        }
    }

    private fun focusSearchField() {
        post {
            searchField.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(searchField, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun clearSearchFocus() {
        searchField.clearFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }
}

private class SpotlightPanelView(context: Context) : FrameLayout(context) {

    var onQueryChanged: ((String) -> Unit)? = null
    var onAppClick: ((DrawerAppItem) -> Unit)? = null
    var onAppLongPress: ((DrawerAppItem, View) -> Unit)? = null

    private val surface = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = spotlightSurfaceDrawable()
        setPadding(
            dp(LauncherVisualSpec.Spotlight.horizontalPaddingDp),
            dp(LauncherVisualSpec.Spotlight.topPaddingDp),
            dp(LauncherVisualSpec.Spotlight.horizontalPaddingDp),
            dp(0),
        )
    }
    private val searchField = androidx.appcompat.widget.AppCompatEditText(context).apply {
        hint = context.getString(R.string.launcher_drawer_hint)
        contentDescription = context.getString(R.string.launcher_search_content_description)
        setHintTextColor(Color.parseColor("#D7E1ED"))
        setTextColor(Color.WHITE)
        textSize = LauncherVisualSpec.Spotlight.searchFieldTextSizeSp
        background = spotlightSearchFieldDrawable()
        setPadding(
            dp(18),
            dp(LauncherVisualSpec.Spotlight.searchFieldVerticalPaddingDp),
            dp(18),
            dp(LauncherVisualSpec.Spotlight.searchFieldVerticalPaddingDp),
        )
        setSingleLine(true)
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        setCompoundDrawablesRelativeWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0)
        compoundDrawablePadding = dp(10)
        compoundDrawablesRelative.firstOrNull()?.mutate()?.setTint(Color.parseColor("#D8E2EE"))
    }
    private val suggestedCard = AppLibraryCollectionCardView(context)
    private val recyclerView = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context)
        overScrollMode = OVER_SCROLL_NEVER
        clipToPadding = false
        setPadding(0, dp(12), 0, dp(24))
    }
    private val emptyStateView = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(22), dp(42), dp(22), dp(22))
        background = roundedDrawable("#12FFFFFF")
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        isVisible = false
    }
    private val emptyTitleView = AppCompatTextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 18f
        gravity = Gravity.CENTER
    }
    private val emptySubtitleView = AppCompatTextView(context).apply {
        setTextColor(Color.parseColor("#BFDCE5F7"))
        textSize = 13f
        gravity = Gravity.CENTER
    }
    private val adapter = DrawerAdapter(
        onClick = { onAppClick?.invoke(it) },
        onLongClick = { app, source -> onAppLongPress?.invoke(app, source) },
    )

    private var topInset = 0
    private var bottomInset = 0
    private var state = SpotlightUiState()
    private var suppressQueryCallbacks = false

    init {
        recyclerView.adapter = adapter
        emptyStateView.addView(emptyTitleView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        emptyStateView.addView(
            emptySubtitleView,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            },
        )

        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        surface.addView(searchField, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        surface.addView(
            suggestedCard,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(LauncherVisualSpec.Spotlight.sectionSpacingDp)
            },
        )
        surface.addView(recyclerView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        surface.addView(
            emptyStateView,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(18)
            },
        )
        alpha = 0f
        translationY = dp(24).toFloat()
        isVisible = false

        searchField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: android.text.Editable?) {
                if (suppressQueryCallbacks) {
                    return
                }
                onQueryChanged?.invoke(s?.toString().orEmpty())
            }
        })
    }

    fun render(state: SpotlightUiState) {
        this.state = state
        val fieldValue = searchField.text?.toString().orEmpty()
        if (fieldValue != state.query) {
            suppressQueryCallbacks = true
            searchField.setText(state.query)
            searchField.setSelection(searchField.text?.length ?: 0)
            suppressQueryCallbacks = false
        }
        suggestedCard.bind(
            title = context.getString(R.string.launcher_suggested_title),
            apps = state.suggestedApps,
            onAppClick = onAppClick,
            onAppLongPress = onAppLongPress,
        )
        suggestedCard.isVisible = state.query.isBlank() && state.suggestedApps.isNotEmpty()
        adapter.submitItems(state.searchRows)
        recyclerView.isVisible = state.searchRows.isNotEmpty()
        emptyStateView.isVisible = state.query.isNotBlank() && state.searchRows.isEmpty() && state.emptyState != null
        emptyTitleView.text = state.emptyState?.title.orEmpty()
        emptySubtitleView.text = state.emptyState?.subtitle.orEmpty()
    }

    fun setOpen(open: Boolean, animated: Boolean) {
        if (!animated) {
            syncOpenState(open)
            if (open) {
                focusSearchField()
            } else {
                clearSearchFocus()
            }
            return
        }
        isVisible = true
        animate().cancel()
        animate()
            .alpha(if (open) 1f else 0f)
            .translationY(if (open) 0f else dp(24).toFloat())
            .setDuration(LauncherMotionSpecs.spotlightDurationMillis)
            .setInterpolator(LauncherMotionSpecs.spotlightInterpolator)
            .withEndAction {
                isVisible = open
                if (open) {
                    focusSearchField()
                } else {
                    clearSearchFocus()
                }
            }
            .start()
    }

    fun syncOpenState(open: Boolean) {
        isVisible = open
        alpha = if (open) 1f else 0f
        translationY = if (open) 0f else dp(24).toFloat()
    }

    fun updateInsets(topInset: Int, bottomInset: Int) {
        this.topInset = topInset
        this.bottomInset = bottomInset
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        surface.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height - topInset - bottomInset, MeasureSpec.EXACTLY),
        )
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        surface.layout(
            0,
            topInset,
            surface.measuredWidth,
            topInset + surface.measuredHeight,
        )
    }

    private fun focusSearchField() {
        post {
            searchField.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(searchField, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun clearSearchFocus() {
        searchField.clearFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }
}

private class FolderPanelView(context: Context) : FrameLayout(context) {

    var onClose: (() -> Unit)? = null
    var onRenameCommitted: ((String, String) -> Unit)? = null
    var onAppClick: ((FolderChildItem) -> Unit)? = null
    var onStartWorkspaceDrag: ((String, View) -> Unit)? = null
    var onDrop: ((LauncherDropTarget, String?, DragPayload) -> Unit)? = null

    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = folderSurfaceDrawable()
        elevation = dp(18).toFloat()
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        setPadding(dp(20), dp(20), dp(20), dp(18))
    }
    private val titleInput = androidx.appcompat.widget.AppCompatEditText(context).apply {
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#7AFFFFFF"))
        background = null
        gravity = Gravity.CENTER
        textSize = 20f
        setSingleLine(true)
        filters = arrayOf(InputFilter.LengthFilter(28))
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
    }
    private val pageDots = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }
    private val itemContainer = ItemContainerView(context, LauncherContainerKind.FOLDER, columns = 3).apply {
        background = roundedDrawable("#18FFFFFF")
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        setPadding(dp(14), dp(18), dp(14), dp(10))
    }

    private var bottomInset = 0
    private var topInset = 0
    private var currentState: FolderSheetUiState? = null
    private var currentPageIndex = 0
    private var committedTitle = ""

    init {
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        card.addView(titleInput, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        card.addView(itemContainer, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        card.addView(
            pageDots,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14)
            },
        )
        alpha = 0f
        scaleX = 0.94f
        scaleY = 0.94f
        isVisible = false

        itemContainer.onCellClicked = { cell, _ ->
            if (cell is RenderCell.ShortcutCell) {
                currentItems().firstOrNull { it.id == cell.id }?.let { item ->
                    onAppClick?.invoke(item)
                }
            }
        }
        itemContainer.onCellLongPressed = { cell, source ->
            if (cell is RenderCell.ShortcutCell) {
                onStartWorkspaceDrag?.invoke(cell.id, source)
            }
        }
        itemContainer.onDrop = { target, targetItemId, payload ->
            onDrop?.invoke(target.copy(containerId = currentState?.folderId), targetItemId, payload)
        }

        titleInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                commitTitleChange()
                true
            } else {
                false
            }
        }
        titleInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                commitTitleChange()
            }
        }
        card.setOnTouchListener(object : OnSwipeTouchListener(context) {
            override fun onSwipeLeft() {
                setPage(currentPageIndex + 1)
            }

            override fun onSwipeRight() {
                setPage(currentPageIndex - 1)
            }
        })
    }

    fun render(state: FolderSheetUiState?) {
        val previousId = currentState?.folderId
        currentState = state
        if (previousId != state?.folderId) {
            currentPageIndex = 0
        }
        val title = state?.title.orEmpty()
        if (!titleInput.hasFocus() && titleInput.text?.toString() != title) {
            titleInput.setText(title)
            titleInput.setSelection(title.length)
        }
        committedTitle = title
        renderCurrentPage()
        renderPageDots()
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
            .scaleX(if (open) 1f else 0.94f)
            .scaleY(if (open) 1f else 0.94f)
            .setDuration(LauncherMotionSpecs.folderDurationMillis)
            .setInterpolator(LauncherMotionSpecs.emphasizedInterpolator)
            .withEndAction { isVisible = open }
            .start()
    }

    fun syncOpenState(open: Boolean) {
        isVisible = open
        alpha = if (open) 1f else 0f
        scaleX = if (open) 1f else 0.94f
        scaleY = if (open) 1f else 0.94f
    }

    fun updateInsets(topInset: Int, bottomInset: Int) {
        this.topInset = topInset
        this.bottomInset = bottomInset
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val cardWidth = min(width - dp(46), dp(348))
        val cardHeight = min(height - topInset - bottomInset - dp(96), dp(388))
        card.measure(
            MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(cardHeight, MeasureSpec.AT_MOST),
        )
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val state = currentState
        if (state == null) {
            card.layout(0, 0, 0, 0)
            return
        }
        val horizontalMargin = dp(22)
        val verticalMargin = dp(56)
        val anchor = state.anchor
        val desiredLeft = anchor?.let { ((it.left + it.right) / 2) - (card.measuredWidth / 2) }
            ?: ((width - card.measuredWidth) / 2)
        val desiredTop = anchor?.let { it.top - dp(18) } ?: ((height - card.measuredHeight) / 2)
        val cardLeft = desiredLeft.coerceIn(horizontalMargin, width - horizontalMargin - card.measuredWidth)
        val cardTop = desiredTop.coerceIn(
            topInset + verticalMargin,
            height - bottomInset - verticalMargin - card.measuredHeight,
        )
        card.pivotX = ((anchor?.left ?: cardLeft) + (anchor?.right ?: (cardLeft + card.measuredWidth))) / 2f - cardLeft
        card.pivotY = ((anchor?.top ?: cardTop) + (anchor?.bottom ?: (cardTop + card.measuredHeight))) / 2f - cardTop
        card.layout(cardLeft, cardTop, cardLeft + card.measuredWidth, cardTop + card.measuredHeight)
    }

    private fun renderCurrentPage() {
        val page = currentState?.pages?.getOrNull(currentPageIndex) ?: currentState?.pages?.firstOrNull()
        itemContainer.submitItems(
            page?.items.orEmpty().map {
                RenderCell.ShortcutCell(
                    id = it.id,
                    title = it.title,
                    componentName = it.componentName,
                    icon = it.icon,
                )
            },
        )
    }

    private fun renderPageDots() {
        pageDots.removeAllViews()
        val pageCount = currentState?.pages?.size ?: 0
        if (pageCount <= 1) {
            pageDots.isVisible = false
            return
        }
        pageDots.isVisible = true
        repeat(pageCount) { index ->
            pageDots.addView(
                View(context).apply {
                    background = pillDrawable(
                        if (index == currentPageIndex) "#D6FFFFFF" else "#33FFFFFF",
                    )
                },
                LinearLayout.LayoutParams(
                    if (index == currentPageIndex) dp(18) else dp(6),
                    dp(6),
                ).apply {
                    if (index > 0) {
                        marginStart = dp(6)
                    }
                },
            )
        }
    }

    private fun setPage(index: Int) {
        val pages = currentState?.pages ?: return
        val nextPage = index.coerceIn(0, pages.lastIndex)
        if (nextPage == currentPageIndex) {
            return
        }
        currentPageIndex = nextPage
        itemContainer.alpha = 0.82f
        renderCurrentPage()
        renderPageDots()
        itemContainer.animate()
            .alpha(1f)
            .setDuration(LauncherMotionSpecs.folderDurationMillis)
            .setInterpolator(LauncherMotionSpecs.standardInterpolator)
            .start()
    }

    private fun currentItems(): List<FolderChildItem> {
        return currentState?.pages?.getOrNull(currentPageIndex)?.items.orEmpty()
    }

    private fun commitTitleChange() {
        val folderId = currentState?.folderId ?: return
        val updatedTitle = titleInput.text?.toString()?.trim().orEmpty().ifBlank { committedTitle }
        if (updatedTitle != committedTitle) {
            committedTitle = updatedTitle
            onRenameCommitted?.invoke(folderId, updatedTitle)
        }
    }
}

private class AppLibraryCollectionCardView(context: Context) : LinearLayout(context) {

    private val iconsClusterView = AppLibraryIconClusterView(context)
    private val titleView = AppCompatTextView(context).apply {
        setTextColor(Color.parseColor("#D6E2F0"))
        textSize = 12f
        letterSpacing = 0.02f
    }

    init {
        orientation = VERTICAL
        background = appLibraryCollectionCardDrawable()
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        setPadding(dp(18), dp(18), dp(18), dp(18))
        addView(iconsClusterView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(
            titleView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14)
            },
        )
    }

    fun bind(
        title: String,
        apps: List<DrawerAppItem>,
        onAppClick: ((DrawerAppItem) -> Unit)?,
        onAppLongPress: ((DrawerAppItem, View) -> Unit)?,
    ) {
        titleView.text = title
        iconsClusterView.bind(apps.take(4), onAppClick, onAppLongPress)
    }
}

private class AppLibraryCategoryCardView(context: Context) : LinearLayout(context) {

    private val iconsClusterView = AppLibraryIconClusterView(context)
    private val titleView = AppCompatTextView(context).apply {
        setTextColor(Color.parseColor("#D8E4F3"))
        textSize = LauncherVisualSpec.AppLibrary.categoryLabelTextSizeSp
        letterSpacing = 0.02f
        maxLines = 1
    }

    init {
        orientation = VERTICAL
        background = appLibraryCategoryCardDrawable()
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        setPadding(dp(18), dp(18), dp(18), dp(18))
        addView(iconsClusterView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(
            titleView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(16)
            },
        )
    }

    fun bind(
        state: AppLibraryOverviewBlockUiState.Category,
        onAppClick: (DrawerAppItem) -> Unit,
        onAppLongPress: (DrawerAppItem, View) -> Unit,
    ) {
        titleView.text = state.title
        iconsClusterView.bind(
            apps = state.previewApps,
            onAppClick = onAppClick,
            onAppLongPress = onAppLongPress,
        )
    }
}

private class AppLibraryIconClusterView(context: Context) : LinearLayout(context) {

    init {
        orientation = VERTICAL
    }

    fun bind(
        apps: List<DrawerAppItem>,
        onAppClick: ((DrawerAppItem) -> Unit)?,
        onAppLongPress: ((DrawerAppItem, View) -> Unit)?,
    ) {
        removeAllViews()
        apps.chunked(2).forEachIndexed { rowIndex, rowItems ->
            addView(
                LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    rowItems.forEachIndexed { columnIndex, app ->
                        addView(
                            AppLibraryClusterIconTileView(context).apply {
                                bind(app)
                                setOnClickListener { onAppClick?.invoke(app) }
                                setOnLongClickListener {
                                    onAppLongPress?.invoke(app, this)
                                    true
                                }
                            },
                            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                                if (columnIndex > 0) {
                                    marginStart = dp(LauncherVisualSpec.AppLibrary.gridSpacingDp)
                                }
                            },
                        )
                    }
                    if (rowItems.size == 1) {
                        addView(
                            View(context),
                            LayoutParams(0, 0, 1f).apply {
                                marginStart = dp(LauncherVisualSpec.AppLibrary.gridSpacingDp)
                            },
                        )
                    }
                },
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    if (rowIndex > 0) {
                        topMargin = dp(LauncherVisualSpec.AppLibrary.gridSpacingDp)
                    }
                },
            )
        }
    }
}

private class AppLibraryClusterIconTileView(context: Context) : FrameLayout(context) {

    private val iconView = AppCompatImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        background = appLibraryClusterTileDrawable()
        setPadding(
            dp(LauncherVisualSpec.AppLibrary.clusterIconInsetDp),
            dp(LauncherVisualSpec.AppLibrary.clusterIconInsetDp),
            dp(LauncherVisualSpec.AppLibrary.clusterIconInsetDp),
            dp(LauncherVisualSpec.AppLibrary.clusterIconInsetDp),
        )
    }

    init {
        addView(iconView, LayoutParams(LayoutParams.MATCH_PARENT, dp(72)))
    }

    fun bind(item: DrawerAppItem) {
        iconView.setImageDrawable(item.icon)
        contentDescription = item.title
    }
}

private abstract class OnSwipeTouchListener(context: Context) : View.OnTouchListener {
    private val gestureDetector = android.view.GestureDetector(
        context,
        object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                val start = e1 ?: return false
                val deltaX = e2.x - start.x
                val deltaY = e2.y - start.y
                if (kotlin.math.abs(deltaX) < dpF(42f) || kotlin.math.abs(deltaX) < kotlin.math.abs(deltaY)) {
                    return false
                }
                if (deltaX < 0) {
                    onSwipeLeft()
                } else {
                    onSwipeRight()
                }
                return true
            }
        },
    )

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return false
    }

    open fun onSwipeLeft() = Unit

    open fun onSwipeRight() = Unit
}

private class DrawerAdapter(
    private val onClick: (DrawerAppItem) -> Unit,
    private val onLongClick: (DrawerAppItem, View) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<AppLibrarySearchRowUiState>()

    init {
        setHasStableIds(true)
    }

    fun submitItems(items: List<AppLibrarySearchRowUiState>) {
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long = items[position].stableId.hashCode().toLong()

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is AppLibrarySearchRowUiState.Header -> 0
            is AppLibrarySearchRowUiState.App -> 1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 0) {
            DrawerHeaderViewHolder(parent.context)
        } else {
            DrawerViewHolder(parent.context)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is AppLibrarySearchRowUiState.Header -> (holder as DrawerHeaderViewHolder).bind(item.title)
            is AppLibrarySearchRowUiState.App -> {
                val app = item.item
                (holder as DrawerViewHolder).bind(app)
                holder.itemView.setOnClickListener { onClick(app) }
                holder.itemView.setOnLongClickListener {
                    onLongClick(app, holder.itemView)
                    true
                }
            }
        }
    }
}

private class DrawerHeaderViewHolder(context: Context) : RecyclerView.ViewHolder(
    AppCompatTextView(context).apply {
        setTextColor(Color.parseColor("#A9BBCD"))
        textSize = 11f
        letterSpacing = 0.08f
        setPadding(dp(2), dp(18), dp(2), dp(8))
    },
) {
    fun bind(title: String) {
        (itemView as AppCompatTextView).text = title
    }
}

private class DrawerViewHolder(context: Context) : RecyclerView.ViewHolder(
    LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(66)
        setPadding(
            dp(4),
            dp(LauncherVisualSpec.AppLibrary.listRowVerticalPaddingDp),
            dp(4),
            dp(LauncherVisualSpec.AppLibrary.listRowVerticalPaddingDp),
        )
    },
) {

    private val iconView = AppCompatImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            dp(LauncherVisualSpec.AppLibrary.listRowIconSizeDp),
            dp(LauncherVisualSpec.AppLibrary.listRowIconSizeDp),
        )
        background = roundedDrawable("#18FFFFFF")
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }
    private val titleView = AppCompatTextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 16f
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(14)
        }
    }

    init {
        (itemView as LinearLayout).apply {
            addView(iconView)
            addView(titleView)
        }
    }

    fun bind(item: DrawerAppItem) {
        iconView.setImageDrawable(item.icon)
        titleView.text = item.title
    }
}

private fun View.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

private fun View.dpF(value: Float): Float = value * resources.displayMetrics.density

private fun View.viewAnchor(): LauncherSurfaceAnchor {
    val rect = Rect()
    getGlobalVisibleRect(rect)
    val rootLocation = IntArray(2)
    rootView.getLocationOnScreen(rootLocation)
    rect.offset(-rootLocation[0], -rootLocation[1])
    return LauncherSurfaceAnchor(
        left = rect.left,
        top = rect.top,
        right = rect.right,
        bottom = rect.bottom,
    )
}

private fun dp(value: Int): Int = (value * Resources.getSystem().displayMetrics.density).toInt()

private fun dpF(value: Float): Float = value * Resources.getSystem().displayMetrics.density

private fun roundedDrawable(colorHex: String, topOnly: Boolean = false): GradientDrawable {
    return GradientDrawable().apply {
        setColor(Color.parseColor(colorHex))
        cornerRadii = if (topOnly) {
            floatArrayOf(dpF(28f), dpF(28f), dpF(28f), dpF(28f), 0f, 0f, 0f, 0f)
        } else {
            floatArrayOf(dpF(28f), dpF(28f), dpF(28f), dpF(28f), dpF(28f), dpF(28f), dpF(28f), dpF(28f))
        }
    }
}

private fun pillDrawable(colorHex: String, strokeHex: String? = null): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpF(999f)
        setColor(Color.parseColor(colorHex))
        strokeHex?.let { setStroke(1, Color.parseColor(it)) }
    }
}

private fun homeAtmosphereDrawable(): LayerDrawable {
    val topGlow = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(
            Color.parseColor("#52000000"),
            Color.parseColor("#0C000000"),
            Color.TRANSPARENT,
        ),
    )
    val bottomFade = GradientDrawable(
        GradientDrawable.Orientation.BOTTOM_TOP,
        intArrayOf(
            Color.parseColor("#74000000"),
            Color.parseColor("#1A000000"),
            Color.TRANSPARENT,
        ),
    )
    return LayerDrawable(arrayOf(topGlow, bottomFade))
}

private fun dockGlassDrawable(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpF(LauncherVisualSpec.dockRadiusDp.toFloat())
        colors = intArrayOf(
            Color.parseColor("#7AF8FBFF"),
            Color.parseColor("#42E4EAF2"),
        )
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        setStroke(1, Color.parseColor("#56FFFFFF"))
    }
}

private fun appLibrarySurfaceDrawable(): LayerDrawable {
    val base = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(
            Color.parseColor("#F03C4856"),
            Color.parseColor("#EC202A35"),
        ),
    ).apply {
        setStroke(1, Color.parseColor("#1FFFFFFF"))
    }
    val glow = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(
            Color.parseColor("#22FFFFFF"),
            Color.TRANSPARENT,
            Color.TRANSPARENT,
        ),
    )
    val edgeTint = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(
            Color.parseColor("#09000000"),
            Color.TRANSPARENT,
            Color.parseColor("#0E000000"),
        ),
    )
    return LayerDrawable(arrayOf(base, glow, edgeTint))
}

private fun spotlightSurfaceDrawable(): LayerDrawable {
    val base = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(
            Color.parseColor("#D42A3442"),
            Color.parseColor("#F0131922"),
        ),
    )
    val glow = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(
            Color.parseColor("#24FFFFFF"),
            Color.TRANSPARENT,
        ),
    )
    return LayerDrawable(arrayOf(base, glow))
}

private fun searchPillDrawable(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpF(LauncherVisualSpec.searchPillRadiusDp.toFloat())
        colors = intArrayOf(
            Color.parseColor("#E6F8FBFF"),
            Color.parseColor("#C6EFF3F8"),
        )
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        setStroke(1, Color.parseColor("#78FFFFFF"))
    }
}

private fun searchFieldSurfaceDrawable(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpF(18f)
        colors = intArrayOf(
            Color.parseColor("#F5FCFDFF"),
            Color.parseColor("#E6F1F5FA"),
        )
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        setStroke(1, Color.parseColor("#80FFFFFF"))
    }
}

private fun spotlightSearchFieldDrawable(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpF(18f)
        colors = intArrayOf(
            Color.parseColor("#34FFFFFF"),
            Color.parseColor("#24FFFFFF"),
        )
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        setStroke(1, Color.parseColor("#33FFFFFF"))
    }
}

private fun folderSurfaceDrawable(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpF(32f)
        colors = intArrayOf(
            Color.parseColor("#D24A5868"),
            Color.parseColor("#C0384454"),
        )
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        setStroke(1, Color.parseColor("#40FFFFFF"))
    }
}

private fun appLibraryCollectionCardDrawable(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpF(LauncherVisualSpec.AppLibrary.collectionCardRadiusDp.toFloat())
        colors = intArrayOf(
            Color.parseColor("#20FFFFFF"),
            Color.parseColor("#10FFFFFF"),
        )
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        setStroke(1, Color.parseColor("#24FFFFFF"))
    }
}

private fun appLibraryCategoryCardDrawable(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpF(LauncherVisualSpec.AppLibrary.collectionCardRadiusDp.toFloat())
        colors = intArrayOf(
            Color.parseColor("#18FFFFFF"),
            Color.parseColor("#0EFFFFFF"),
        )
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        setStroke(1, Color.parseColor("#1FFFFFFF"))
    }
}

private fun appLibraryClusterTileDrawable(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpF(LauncherVisualSpec.AppLibrary.clusterTileRadiusDp.toFloat())
        colors = intArrayOf(
            Color.parseColor("#24FFFFFF"),
            Color.parseColor("#14FFFFFF"),
        )
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        setStroke(1, Color.parseColor("#20FFFFFF"))
    }
}
