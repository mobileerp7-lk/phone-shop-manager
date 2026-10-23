package app.phoneshop.manager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** One gradient + glass colour look. The shop picks one in Settings. */
data class AppTheme(
    val id: String,
    val name: String,
    val dark: Boolean,
    /** Colour for links, selected items and highlights. */
    val accent: Color,
    /** Three stops of the button and header gradient. */
    val start: Color,
    val middle: Color,
    val end: Color,
    /** Three stops of the page background, top to bottom. */
    val bgTop: Color,
    val bgMiddle: Color,
    val bgBottom: Color,
    val glowA: Color,
    val glowB: Color,
)

object Themes {
    const val DEFAULT_ID = "purple"

    val all = listOf(
        AppTheme(
            "purple", "Purple-teal", false,
            Color(0xFF4F46E5), Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFF0891B2),
            Color(0xFFEEF2FF), Color(0xFFF5F3FF), Color(0xFFECFEFF), Color(0x66A5B4FC), Color(0x5967E8F9),
        ),
        AppTheme(
            "ocean", "Ocean blue", false,
            Color(0xFF0369A1), Color(0xFF0369A1), Color(0xFF0284C7), Color(0xFF0E7490),
            Color(0xFFE0F2FE), Color(0xFFF0F9FF), Color(0xFFECFEFF), Color(0x667DD3FC), Color(0x595EEAD4),
        ),
        AppTheme(
            "emerald", "Emerald green", false,
            Color(0xFF047857), Color(0xFF047857), Color(0xFF059669), Color(0xFF0F766E),
            Color(0xFFECFDF5), Color(0xFFF0FDF4), Color(0xFFF0FDFA), Color(0x666EE7B7), Color(0x595EEAD4),
        ),
        AppTheme(
            "sunset", "Sunset", false,
            Color(0xFFC2410C), Color(0xFFC2410C), Color(0xFFDB2777), Color(0xFF7C3AED),
            Color(0xFFFFF7ED), Color(0xFFFDF2F8), Color(0xFFFAF5FF), Color(0x66FDBA74), Color(0x59F9A8D4),
        ),
        AppTheme(
            "dark", "Dark", true,
            Color(0xFFA5B4FC), Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFF0E7490),
            Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF0B1220), Color(0x334F46E5), Color(0x330E7490),
        ),
    )

    fun byId(id: String): AppTheme = all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT_ID }

    /** The theme in use right now. Setting it redraws the whole app. */
    var current by mutableStateOf(byId(DEFAULT_ID))
}
