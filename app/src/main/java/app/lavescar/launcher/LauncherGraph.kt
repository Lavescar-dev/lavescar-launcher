package app.lavescar.launcher

import android.content.Context
import androidx.room.Room
import app.lavescar.launcher.data.DataStoreLauncherPreferencesRepository
import app.lavescar.launcher.data.DataStorePinnedAppsRepository
import app.lavescar.launcher.data.LauncherDatabase
import app.lavescar.launcher.data.LauncherPreferencesRepository
import app.lavescar.launcher.data.PinnedAppsRepository
import app.lavescar.launcher.data.RoomWorkspaceRepository
import app.lavescar.launcher.data.WorkspaceRepository
import app.lavescar.launcher.system.AndroidAppActionController
import app.lavescar.launcher.system.AndroidAppLauncher
import app.lavescar.launcher.system.AndroidHomeRoleController
import app.lavescar.launcher.system.AndroidHomeSeedDefaultsController
import app.lavescar.launcher.system.AndroidSampleWallpaperController
import app.lavescar.launcher.system.AndroidWidgetHostController
import app.lavescar.launcher.system.AppActionController
import app.lavescar.launcher.system.AndroidWallpaperWindowController
import app.lavescar.launcher.system.AppCatalogRepository
import app.lavescar.launcher.system.AppLauncher
import app.lavescar.launcher.system.HomeSeedDefaultsController
import app.lavescar.launcher.system.HomeRoleController
import app.lavescar.launcher.system.LauncherAppsCatalogRepository
import app.lavescar.launcher.system.SampleWallpaperController
import app.lavescar.launcher.system.WallpaperWindowController
import app.lavescar.launcher.system.WidgetHostController

class LauncherGraph(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val database: LauncherDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            LauncherDatabase::class.java,
            "launcher.db",
        ).addMigrations(
            LauncherDatabase.MIGRATION_1_2,
        ).build()
    }

    val workspaceRepository: WorkspaceRepository by lazy { RoomWorkspaceRepository(database) }
    val preferencesRepository: LauncherPreferencesRepository =
        DataStoreLauncherPreferencesRepository(appContext)
    val pinnedAppsRepository: PinnedAppsRepository =
        DataStorePinnedAppsRepository(appContext)
    val appCatalogRepository: AppCatalogRepository = LauncherAppsCatalogRepository(appContext)
    val homeRoleController: HomeRoleController = AndroidHomeRoleController(appContext)
    val homeSeedDefaultsController: HomeSeedDefaultsController = AndroidHomeSeedDefaultsController(appContext)
    val appLauncher: AppLauncher = AndroidAppLauncher(appContext)
    val appActionController: AppActionController = AndroidAppActionController(appContext)
    val wallpaperWindowController: WallpaperWindowController = AndroidWallpaperWindowController()
    val sampleWallpaperController: SampleWallpaperController = AndroidSampleWallpaperController(appContext)
    val widgetHostController: WidgetHostController = AndroidWidgetHostController(appContext)
}
