package app.lavescar.launcher.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomWorkspaceRepositoryTest {

    private lateinit var database: LauncherDatabase
    private lateinit var repository: RoomWorkspaceRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, LauncherDatabase::class.java).build()
        repository = RoomWorkspaceRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createFolderAndRenameIt() = runBlocking {
        repository.addShortcut(
            AppShortcutReference("pkg.one", "pkg.one.MainActivity", "One"),
            WorkspaceDropTarget(WorkspaceContainerType.PAGE, position = 0),
        )
        repository.addShortcut(
            AppShortcutReference("pkg.two", "pkg.two.MainActivity", "Two"),
            WorkspaceDropTarget(WorkspaceContainerType.PAGE, position = 1),
        )

        val before = repository.workspace.first()
        val targetAppId = (before.pageItems[0] as WorkspaceItemModel.AppShortcut).id
        val draggedAppId = (before.pageItems[1] as WorkspaceItemModel.AppShortcut).id

        repository.moveItem(
            itemId = draggedAppId,
            target = WorkspaceDropTarget(WorkspaceContainerType.PAGE, position = 0),
            targetItemId = targetAppId,
        )

        val folder = repository.workspace.first().pageItems.single() as WorkspaceItemModel.Folder
        assertThat(folder.items).hasSize(2)

        repository.renameFolder(folder.id, "Favorites")

        val renamed = repository.workspace.first().pageItems.single() as WorkspaceItemModel.Folder
        assertThat(renamed.title).isEqualTo("Favorites")
    }

    @Test
    fun movingItemOutOfFolderCollapsesSingleChildFolder() = runBlocking {
        repository.addShortcut(
            AppShortcutReference("pkg.one", "pkg.one.MainActivity", "One"),
            WorkspaceDropTarget(WorkspaceContainerType.PAGE, position = 0),
        )
        repository.addShortcut(
            AppShortcutReference("pkg.two", "pkg.two.MainActivity", "Two"),
            WorkspaceDropTarget(WorkspaceContainerType.PAGE, position = 1),
        )

        val initial = repository.workspace.first()
        val firstId = (initial.pageItems[0] as WorkspaceItemModel.AppShortcut).id
        val secondId = (initial.pageItems[1] as WorkspaceItemModel.AppShortcut).id
        repository.moveItem(
            itemId = secondId,
            target = WorkspaceDropTarget(WorkspaceContainerType.PAGE, position = 0),
            targetItemId = firstId,
        )

        val folder = repository.workspace.first().pageItems.single() as WorkspaceItemModel.Folder
        val movedOutChildId = folder.items.first().id

        repository.moveItem(
            itemId = movedOutChildId,
            target = WorkspaceDropTarget(WorkspaceContainerType.PAGE, position = 1),
        )

        val pageItems = repository.workspace.first().pageItems
        assertThat(pageItems).hasSize(2)
        assertThat(pageItems.all { it is WorkspaceItemModel.AppShortcut }).isTrue()
    }

    @Test
    fun widgetPersistsInWorkspaceModel() = runBlocking {
        repository.addWidget(
            widget = WidgetReference(
                appWidgetId = 42,
                providerPackageName = "pkg.widget",
                providerClassName = "pkg.widget.ClockProvider",
                title = "Clock",
                spanX = 4,
                spanY = 2,
            ),
            target = WorkspaceDropTarget(WorkspaceContainerType.PAGE, position = 0),
        )

        val current = repository.workspace.first().pageItems.single() as WorkspaceItemModel.Widget
        assertThat(current.appWidgetId).isEqualTo(42)
        assertThat(current.providerPackageName).isEqualTo("pkg.widget")
        assertThat(current.spanX).isEqualTo(4)

        val restored = RoomWorkspaceRepository(database).workspace.first().pageItems.single() as WorkspaceItemModel.Widget
        assertThat(restored.appWidgetId).isEqualTo(42)
        assertThat(restored.providerClassName).isEqualTo("pkg.widget.ClockProvider")
    }

    @Test
    fun workspacePages_areGroupedAndRestoredByPage() = runBlocking {
        repository.addShortcut(
            AppShortcutReference("pkg.one", "pkg.one.MainActivity", "One"),
            WorkspaceDropTarget(WorkspaceContainerType.PAGE, page = 0, position = 0),
        )
        repository.addShortcut(
            AppShortcutReference("pkg.two", "pkg.two.MainActivity", "Two"),
            WorkspaceDropTarget(WorkspaceContainerType.PAGE, page = 1, position = 0),
        )

        val current = repository.workspace.first()
        assertThat(current.pages.map { it.page }).containsExactly(0, 1).inOrder()
        assertThat(current.pages[0].items.single().title).isEqualTo("One")
        assertThat(current.pages[1].items.single().title).isEqualTo("Two")

        val restored = RoomWorkspaceRepository(database).workspace.first()
        assertThat(restored.pages.map { it.page }).containsExactly(0, 1).inOrder()
        assertThat(restored.pages[1].items.single().title).isEqualTo("Two")
    }

    @Test
    fun widgetCannotBeMovedIntoFolder() = runBlocking {
        repository.addShortcut(
            AppShortcutReference("pkg.one", "pkg.one.MainActivity", "One"),
            WorkspaceDropTarget(WorkspaceContainerType.PAGE, position = 0),
        )
        repository.addShortcut(
            AppShortcutReference("pkg.two", "pkg.two.MainActivity", "Two"),
            WorkspaceDropTarget(WorkspaceContainerType.PAGE, position = 1),
        )

        val initial = repository.workspace.first()
        val firstId = (initial.pageItems[0] as WorkspaceItemModel.AppShortcut).id
        val secondId = (initial.pageItems[1] as WorkspaceItemModel.AppShortcut).id
        repository.moveItem(
            itemId = secondId,
            target = WorkspaceDropTarget(WorkspaceContainerType.PAGE, position = 0),
            targetItemId = firstId,
        )

        repository.addWidget(
            widget = WidgetReference(
                appWidgetId = 77,
                providerPackageName = "pkg.widget",
                providerClassName = "pkg.widget.WeatherProvider",
                title = "Weather",
                spanX = 4,
                spanY = 2,
            ),
            target = WorkspaceDropTarget(WorkspaceContainerType.PAGE, position = 1),
        )

        val workspace = repository.workspace.first()
        val folderId = (workspace.pageItems.first { it is WorkspaceItemModel.Folder } as WorkspaceItemModel.Folder).id
        val widgetId = (workspace.pageItems.first { it is WorkspaceItemModel.Widget } as WorkspaceItemModel.Widget).id

        val moved = repository.moveItem(
            itemId = widgetId,
            target = WorkspaceDropTarget(WorkspaceContainerType.PAGE, position = 0),
            targetItemId = folderId,
        )

        assertThat(moved).isFalse()
        val after = repository.workspace.first().pageItems
        assertThat(after.count { it is WorkspaceItemModel.Widget }).isEqualTo(1)
        assertThat(after.count { it is WorkspaceItemModel.Folder }).isEqualTo(1)
    }

    @Test
    fun folderChildrenPersistAcrossRepositoryRecreation() = runBlocking {
        repeat(10) { index ->
            repository.addShortcut(
                AppShortcutReference(
                    packageName = "pkg.$index",
                    className = "pkg.$index.MainActivity",
                    title = "App $index",
                ),
                WorkspaceDropTarget(WorkspaceContainerType.PAGE, position = index),
            )
        }

        repeat(9) {
            val workspace = repository.workspace.first()
            val anchorId = (workspace.pageItems.first() as WorkspaceItemModel.AppShortcut).id
            val draggedId = (workspace.pageItems[1] as WorkspaceItemModel.AppShortcut).id
            repository.moveItem(
                itemId = draggedId,
                target = WorkspaceDropTarget(WorkspaceContainerType.PAGE, position = 0),
                targetItemId = anchorId,
            )
        }

        val folder = repository.workspace.first().pageItems.first { it is WorkspaceItemModel.Folder } as WorkspaceItemModel.Folder
        assertThat(folder.items).hasSize(10)

        val restored = RoomWorkspaceRepository(database).workspace.first().pageItems.first { it is WorkspaceItemModel.Folder } as WorkspaceItemModel.Folder
        assertThat(restored.items).hasSize(10)
        assertThat(restored.items.map { it.title }).containsExactly(
            "App 0",
            "App 1",
            "App 2",
            "App 3",
            "App 4",
            "App 5",
            "App 6",
            "App 7",
            "App 8",
            "App 9",
        ).inOrder()
    }
}
