package app.lavescar.launcher.core

import android.widget.FrameLayout

fun interface WidgetViewBinder {
    fun bind(container: FrameLayout, widget: WorkspaceSurfaceItem.Widget)
}
