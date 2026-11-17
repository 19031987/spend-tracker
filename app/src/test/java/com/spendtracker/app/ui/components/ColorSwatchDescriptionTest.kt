package com.spendtracker.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorSwatchDescriptionTest {
    @Test
    fun `swatches are described by colour name and hex`() {
        assertEquals("Pink, #F9A8D4", colorSwatchDescription("#F9A8D4"))
        assertEquals("Green, #86EFAC", colorSwatchDescription("#86efac"))
        assertEquals("Orange, #FED7AA", colorSwatchDescription("#FED7AA"))
        assertEquals("Red", colorNameFor("#FCA5A5"))
        assertEquals("Light grey", colorNameFor("#9CA3AF"))
    }
}
