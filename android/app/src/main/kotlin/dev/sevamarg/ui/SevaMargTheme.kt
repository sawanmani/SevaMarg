// android/app/src/main/kotlin/dev/sevamarg/ui/SevaMargTheme.kt
//
// Material 3 defaults, with one deliberate change: the type scale is sized for Devanagari. Hindi needs
// more vertical room than Latin for the same font size because of matras and anuskas, and 14 sp body
// text on a 5-inch budget phone is the difference between "readable" and "the CSC agent reads it for
// me". The contrast/line-height audit is Phase 2.6; this file is where the sizes live so the audit has
// one place to change.
package dev.sevamarg.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Answer body text is 19 sp, not the Material default 16: the a11y rule in docs/PHASES.md 2.6 is >= 18 sp. */
private val SevaMargTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    bodyLarge = TextStyle(fontSize = 19.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp),
)

@Composable
fun SevaMargTheme(content: @Composable () -> Unit) {
    // Light only, for now: the low-end devices in the test matrix ship a light system UI, and a dark
    // variant has to clear the same 4.5:1 contrast measurement before it can be offered.
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = SevaMargTypography,
        content = content,
    )
}
