package app.lavescar.launcher.system

import android.content.pm.ApplicationInfo

enum class LauncherAppCategory(
    val title: String,
) {
    PRODUCTIVITY("Productivity"),
    SOCIAL("Social"),
    UTILITIES("Utilities"),
    PHOTOS_VIDEO("Photos & Video"),
    ENTERTAINMENT("Entertainment"),
    GAMES("Games"),
    TRAVEL("Travel"),
    HEALTH("Health"),
    OTHER("Other"),
}

object LauncherAppCategoryResolver {
    fun resolve(
        packageName: String,
        appCategory: Int?,
    ): LauncherAppCategory {
        val normalizedPackage = packageName.lowercase()
        return when (appCategory) {
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> LauncherAppCategory.PRODUCTIVITY
            ApplicationInfo.CATEGORY_SOCIAL -> LauncherAppCategory.SOCIAL
            ApplicationInfo.CATEGORY_IMAGE,
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_AUDIO,
            -> LauncherAppCategory.PHOTOS_VIDEO
            ApplicationInfo.CATEGORY_GAME -> LauncherAppCategory.GAMES
            ApplicationInfo.CATEGORY_MAPS -> LauncherAppCategory.TRAVEL
            ApplicationInfo.CATEGORY_NEWS,
            ApplicationInfo.CATEGORY_UNDEFINED,
            -> fallback(normalizedPackage)
            else -> fallback(normalizedPackage)
        }
    }

    private fun fallback(packageName: String): LauncherAppCategory {
        return when {
            packageName.containsAny("camera", "gallery", "photo", "video", "snap", "instagram") ->
                LauncherAppCategory.PHOTOS_VIDEO

            packageName.containsAny("game", "playgames") ->
                LauncherAppCategory.GAMES

            packageName.containsAny("maps", "uber", "lyft", "travel", "booking", "trip") ->
                LauncherAppCategory.TRAVEL

            packageName.containsAny("health", "fit", "fitness", "run", "sleep") ->
                LauncherAppCategory.HEALTH

            packageName.containsAny("chat", "message", "messaging", "telegram", "whatsapp", "discord", "social", "facebook", "twitter") ->
                LauncherAppCategory.SOCIAL

            packageName.containsAny("music", "movie", "tv", "stream", "video", "podcast", "radio", "news") ->
                LauncherAppCategory.ENTERTAINMENT

            packageName.containsAny("mail", "calendar", "docs", "drive", "note", "task", "office", "word", "sheet", "meet", "zoom") ->
                LauncherAppCategory.PRODUCTIVITY

            packageName.containsAny("settings", "clock", "dialer", "phone", "browser", "files", "calculator", "weather", "tools") ->
                LauncherAppCategory.UTILITIES

            else -> LauncherAppCategory.OTHER
        }
    }

    private fun String.containsAny(vararg fragments: String): Boolean {
        return fragments.any(::contains)
    }
}
