package app.lavescar.launcher

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lavescar.launcher.core.MinimalLauncherRootCallbacks
import app.lavescar.launcher.core.MinimalLauncherRootView
import app.lavescar.launcher.core.MinimalLauncherSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

class LauncherActivity : AppCompatActivity() {

    private val appGraph by lazy { (application as LauncherApplication).appGraph }
    private val viewModel: MinimalLauncherViewModel by viewModels {
        MinimalLauncherViewModel.Factory(
            application = application,
            pinnedAppsRepository = appGraph.pinnedAppsRepository,
            preferencesRepository = appGraph.preferencesRepository,
            appCatalogRepository = appGraph.appCatalogRepository,
            homeRoleController = appGraph.homeRoleController,
            appActionController = appGraph.appActionController,
        )
    }

    private lateinit var rootView: MinimalLauncherRootView
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var lastTouchUptimeMs = 0L
    private var lastLibraryOpenUptimeMs = 0L
    private var lastHomeSwipeUpUptimeMs = 0L

    private val defaultLauncherRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.refresh()
        }

    private val backHandler = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            rootView.clearSearchFocus()
            viewModel.handleBackPressed()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appGraph.wallpaperWindowController.apply(this)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        rootView = MinimalLauncherRootView(this).apply {
            setCallbacks(object : MinimalLauncherRootCallbacks {
                override fun onHomeQueryChanged(query: String) {
                    viewModel.updateHomeQuery(query)
                }

                override fun onLibraryQueryChanged(query: String) {
                    viewModel.updateLibraryQuery(query)
                }

                override fun onOpenAppLibrary() {
                    lastLibraryOpenUptimeMs = SystemClock.uptimeMillis()
                    viewModel.openAppLibrary()
                }

                override fun onCloseAppLibrary() {
                    viewModel.closeAppLibrary()
                }

                override fun onLaunchApp(componentName: ComponentName) {
                    clearSearchFocus()
                    if (appGraph.appLauncher.launch(componentName)) {
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                        viewModel.handleAppLaunched()
                    }
                }

                override fun onRequestAppActions(componentName: ComponentName) {
                    viewModel.requestAppActions(componentName)
                }

                override fun onDismissActionSheet() {
                    viewModel.dismissActionSheet()
                }

                override fun onSelectActionSheetAction(actionId: String) {
                    viewModel.onActionSheetActionSelected(actionId)
                }

                override fun onRequestDefaultLauncher() {
                    viewModel.requestDefaultLauncher()
                }
            })
        }
        setContentView(rootView)
        onBackPressedDispatcher.addCallback(this, backHandler)

        forceSystemBlackWallpaper()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        rootView.render(state)
                    }
                }
                launch {
                    viewModel.launcherCommands.collect { command ->
                        when (command) {
                            MinimalLauncherViewModel.LauncherCommand.RequestDefaultLauncher -> requestDefaultLauncher()
                        }
                    }
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val now = SystemClock.uptimeMillis()
        lastTouchUptimeMs = now
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = ev.rawX
                touchDownY = ev.rawY
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dy = ev.rawY - touchDownY
                val dx = ev.rawX - touchDownX
                val minSwipePx = 40f * resources.displayMetrics.density
                val state = viewModel.uiState.value
                if (state.surface == MinimalLauncherSurface.HOME &&
                    state.homeQuery.isBlank() &&
                    dy < -minSwipePx &&
                    abs(dy) > abs(dx)
                ) {
                    lastHomeSwipeUpUptimeMs = now
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        val now = SystemClock.uptimeMillis()
        if (now - lastLibraryOpenUptimeMs < 1500L) {
            return
        }
        if (now - lastHomeSwipeUpUptimeMs < 1500L) {
            lastLibraryOpenUptimeMs = now
            if (::rootView.isInitialized) {
                rootView.clearSearchFocus()
            }
            viewModel.openAppLibrary()
            return
        }
        val currentState = viewModel.uiState.value
        if (now - lastTouchUptimeMs < 1200L &&
            (currentState.homeQuery.isNotBlank() || currentState.libraryQuery.isNotBlank())
        ) {
            return
        }
        if (::rootView.isInitialized) {
            rootView.clearSearchFocus()
        }
        viewModel.onHomeIntent()
    }

    override fun onResume() {
        super.onResume()
        appGraph.wallpaperWindowController.apply(this)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        viewModel.refresh()
    }

    private fun forceSystemBlackWallpaper() {
        val prefs = getSharedPreferences("launcher_wallpaper_state", MODE_PRIVATE)
        if (prefs.getBoolean("black_wallpaper_applied_v1", false)) {
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val wm = WallpaperManager.getInstance(applicationContext)
                val blackBitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
                blackBitmap.eraseColor(Color.BLACK)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    wm.setBitmap(
                        blackBitmap,
                        null,
                        true,
                        WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
                    )
                } else {
                    wm.setBitmap(blackBitmap)
                }
                prefs.edit().putBoolean("black_wallpaper_applied_v1", true).apply()
            }
        }
    }

    private fun requestDefaultLauncher() {
        val roleIntent = appGraph.homeRoleController.createRequestIntentOrNull()
        if (roleIntent != null) {
            defaultLauncherRequest.launch(roleIntent)
        } else {
            startActivity(appGraph.homeRoleController.createFallbackSettingsIntent())
        }
    }
}
