package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class DockRecentsTest {

    @Test
    fun `pinned dock apps are excluded from visible recent dock apps`() {
        val dock = listOf("com.example.app1", "com.example.app2", null, null)
        val recentApps = listOf("com.example.app1", "com.example.app3", "com.example.app4", "com.example.app2", "com.example.app5")
        val availableApps = setOf("com.example.app1", "com.example.app2", "com.example.app3", "com.example.app4", "com.example.app5")

        val visibleRecents = recentApps.filter { it !in dock && it in availableApps }.take(4)

        assertEquals(listOf("com.example.app3", "com.example.app4", "com.example.app5"), visibleRecents)
    }

    @Test
    fun `visible recents fills remaining slots up to total of four`() {
        val dock = listOf("pinned1", "pinned2", null, null)
        val recentApps = listOf("app1", "app2", "app3", "app4", "app5", "app6")
        val availableApps = recentApps.toSet()

        val recentsLimit = maxOf(0, 4 - dock.count { it != null })
        val visibleRecents = recentApps.filter { it !in dock && it in availableApps }.take(recentsLimit)

        assertEquals(listOf("app1", "app2"), visibleRecents)
        assertEquals(2, visibleRecents.size)
    }

    @Test
    fun `uninstalled or missing apps are omitted from visible recents`() {
        val dock = listOf<String?>(null, null, null, null)
        val recentApps = listOf("installed1", "uninstalled_app", "installed2")
        val availableApps = setOf("installed1", "installed2")

        val visibleRecents = recentApps.filter { it !in dock && it in availableApps }.take(4)

        assertEquals(listOf("installed1", "installed2"), visibleRecents)
    }

    @Test
    fun `disabling recent apps hides them`() {
        val showRecentApps = false
        val dock = listOf<String?>(null, null, null, null)
        val recentApps = listOf("app1", "app2")
        val availableApps = setOf("app1", "app2")

        val visibleRecents = if (!showRecentApps) emptyList()
            else recentApps.filter { it !in dock && it in availableApps }.take(4)

        assertTrue(visibleRecents.isEmpty())
    }

    @Test
    fun `recording new launch places it at the front and removes duplicate previous position`() {
        val initialRecents = listOf("app1", "app2", "app3", "app4")
        val launchedApp = "app3"

        val updated = listOf(launchedApp) + initialRecents.filter { it != launchedApp }

        assertEquals(listOf("app3", "app1", "app2", "app4"), updated)
    }

    @Test
    fun `launcher state defaults include empty recent apps and enabled toggle`() {
        val state = LauncherState()
        assertTrue(state.recentApps.isEmpty())
        assertTrue(state.showRecentApps)
    }
}
