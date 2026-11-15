package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class SchoolColorTokens(
    val accent: Color,
    val container: Color,
    val onContainer: Color
)

object SchoolColorPalette {

    private val lightTokens = listOf(
        // 0: Cobalt (ZSME / placówka główna)
        SchoolColorTokens(
            accent = ZsmeCobalt,
            container = ZsmeCobaltContainer,
            onContainer = ZsmeOnCobaltContainer
        ),
        // 1: Emerald Teal
        SchoolColorTokens(
            accent = ZsmeLiveGreenLight,
            container = ZsmeLiveGreenContainerLight,
            onContainer = Color(0xFF033824)
        ),
        // 2: Warm Amber
        SchoolColorTokens(
            accent = ZsmeAmber,
            container = ZsmeAmberContainer,
            onContainer = ZsmeOnAmberContainer
        ),
        // 3: Plum Violet
        SchoolColorTokens(
            accent = Color(0xFF6D28D9),
            container = Color(0xFFEDE9FE),
            onContainer = Color(0xFF2E1065)
        ),
        // 4: Crimson Rose
        SchoolColorTokens(
            accent = Color(0xFFBE123C),
            container = Color(0xFFFFE4E6),
            onContainer = Color(0xFF4C0519)
        ),
        // 5: Cyan Sky
        SchoolColorTokens(
            accent = ZsmeTeal,
            container = ZsmeTealContainer,
            onContainer = ZsmeOnTealContainer
        )
    )

    private val darkTokens = listOf(
        // 0: Cobalt
        SchoolColorTokens(
            accent = ZsmeCobaltDark,
            container = ZsmeCobaltContainerDark,
            onContainer = ZsmeOnCobaltContainerDark
        ),
        // 1: Emerald
        SchoolColorTokens(
            accent = ZsmeLiveGreenDark,
            container = ZsmeLiveGreenContainerDark,
            onContainer = Color(0xFFD3F4E7)
        ),
        // 2: Amber
        SchoolColorTokens(
            accent = ZsmeAmberDark,
            container = ZsmeAmberContainerDark,
            onContainer = ZsmeOnAmberContainerDark
        ),
        // 3: Violet
        SchoolColorTokens(
            accent = Color(0xFFC4B5FD),
            container = Color(0xFF3B1D6E),
            onContainer = Color(0xFFEDE9FE)
        ),
        // 4: Rose
        SchoolColorTokens(
            accent = Color(0xFFFDA4AF),
            container = Color(0xFF6E122C),
            onContainer = Color(0xFFFFE4E6)
        ),
        // 5: Cyan
        SchoolColorTokens(
            accent = ZsmeTealDark,
            container = ZsmeTealContainerDark,
            onContainer = ZsmeOnTealContainerDark
        )
    )

    @Composable
    fun tokensForIndex(colorIndex: Int, darkTheme: Boolean = LocalZsmeDarkTheme.current): SchoolColorTokens {
        val list = if (darkTheme) darkTokens else lightTokens
        val safeIdx = ((colorIndex % list.size) + list.size) % list.size
        return list[safeIdx]
    }
}
