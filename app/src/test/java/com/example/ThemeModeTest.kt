package com.example

import com.example.data.preferences.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test
    fun `theme mode cycles system dark light`() {
        assertEquals(ThemeMode.DARK, ThemeMode.SYSTEM.next())
        assertEquals(ThemeMode.LIGHT, ThemeMode.DARK.next())
        assertEquals(ThemeMode.SYSTEM, ThemeMode.LIGHT.next())
    }
}
