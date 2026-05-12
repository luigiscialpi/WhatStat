package com.whatstat.reborn.ui.theme

import androidx.compose.ui.graphics.Color
import com.whatstat.reborn.data.db.model.SenderStat

/**
 * Palette colori coerente per i mittenti, utilizzata in PieChart e Breakdown.
 */
val senderPieColors = listOf(
    Color(0xFFE53935), // Red
    Color(0xFF1E88E5), // Blue
    Color(0xFF43A047), // Green
    Color(0xFFFF9800), // Orange
    Color(0xFF9C27B0), // Purple
    Color(0xFF00ACC1), // Cyan
    Color(0xFFE91E63), // Pink
    Color(0xFFFFD600), // Yellow
    Color(0xFF3949AB), // Indigo
    Color(0xFF795548), // Brown
    Color(0xFF00695C), // Dark Teal
    Color(0xFF7CB342), // Light Green
)

/**
 * Risolve il colore per un mittente basandosi sulla sua posizione in un breakdown di riferimento.
 * Se non trovato, usa un fallback basato sul JID.
 */
fun getSenderColor(
    senderJid: String,
    referenceBreakdown: List<SenderStat>,
    fallbackColor: Color = Color(0xFF9E9E9E)
): Color {
    val index = referenceBreakdown.indexOfFirst { it.jid == senderJid }
    return if (index != -1) {
        senderPieColors[index % senderPieColors.size]
    } else {
        // Fallback stabile basato sull'hash del JID
        val hash = senderJid.hashCode()
        senderPieColors[Math.abs(hash) % senderPieColors.size]
    }
}
