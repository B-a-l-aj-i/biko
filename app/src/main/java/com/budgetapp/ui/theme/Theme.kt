package com.budgetapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** Colours for budget states, which Material's colour scheme has no slot for. */
@Immutable
data class StatusColors(
    val warningContainer: Color,
    val onWarningContainer: Color,
    val danger: Color,
    val success: Color,
    val successContainer: Color,
)

private val LightStatus = StatusColors(
    warningContainer = Color(0xFFFAEEDA),
    onWarningContainer = Color(0xFF633806),
    danger = Color(0xFFC0392B),
    success = Color(0xFF27500A),
    successContainer = Color(0xFFEAF3DE),
)

private val DarkStatus = StatusColors(
    warningContainer = Color(0xFF412402),
    onWarningContainer = Color(0xFFFAC775),
    danger = Color(0xFFF09595),
    success = Color(0xFFC0DD97),
    successContainer = Color(0xFF173404),
)

/** Black and white only: every state is said in ink on paper, and by its wording. */
private fun monoStatus(ink: Color, paper: Color) = StatusColors(
    warningContainer = paper,
    onWarningContainer = ink,
    danger = ink,
    success = ink,
    successContainer = paper,
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

/**
 * The colours Material has no slot for that change with the theme: the donuts,
 * the wash behind Home and the header's frosted glass. Screens read these from
 * [LocalPalette] and never from [Pastel] directly, so every theme gets its own.
 */
@Immutable
data class Palette(
    /** Home's donut: what was spent, and what is left of the budget. */
    val donutSpent: Color,
    val donutLeft: Color,
    /** One per category by its place in the list, for the Budget donut and its dots. */
    val categories: List<Color>,
    /** For categories past the end of [categories], and spending under a removed one. */
    val categoryNeutral: Color,
    /** A donut with nothing to show yet. */
    val emptyArc: Color,
    /** A donut's percent label when it sits on the page instead of on its slice. */
    val labelOnPage: Color,
    /** Behind Home: spending going well, running ahead, and no budget set. */
    val washGood: Color,
    val washBad: Color,
    val washNone: Color,
    /** The frosted header: what shows before the blur is ready, and the tint over it. */
    val frostBackground: Color,
    val frostTint: Color,
    /**
     * True in black-and-white mode, where a card is the same colour as the
     * page and only an outline sets it apart. See [cardBorder].
     */
    val outlined: Boolean,
)

private val LightPalette = Palette(
    donutSpent = Pastel.Sky,
    donutLeft = Pastel.Lilac,
    categories = Pastel.Categories,
    categoryNeutral = Pastel.CategoryNeutral,
    emptyArc = Color(0xFFE7E5EE),
    labelOnPage = Color(0xFF26262B),
    washGood = Pastel.WashGood,
    washBad = Pastel.WashBad,
    washNone = Pastel.WashLavender,
    frostBackground = Color(0xFFF8F7FC),
    frostTint = Color.White.copy(alpha = 0.35f),
    outlined = false,
)

// The pastels stay: they read well on a dark page and keep dark text on top.
// The washes go deep instead of pale, so light text stays readable over them.
private val DarkPalette = LightPalette.copy(
    emptyArc = Color(0xFF2C2C32),
    labelOnPage = Color(0xFFF1F1F1),
    washGood = Color(0xFF1B4A2E),
    washBad = Color(0xFF5A2020),
    washNone = Color(0xFF2C2752),
    frostBackground = Color(0xFF0B0B0B),
    frostTint = Color.Black.copy(alpha = 0.35f),
)

/**
 * Greys for the donuts in black-and-white mode, the one place it uses shades.
 * Neighbours in the list alternate dark and light so two slices side by side
 * stay apart, and none is so near black or white that it is lost on the page.
 */
private val GreyCategories = listOf(
    Color(0xFF3A3A3A),
    Color(0xFFCFCFCF),
    Color(0xFF707070),
    Color(0xFFDADADA),
    Color(0xFF505050),
    Color(0xFFB0B0B0),
    Color(0xFF8A8A8A),
    Color(0xFFC0C0C0),
    Color(0xFF606060),
    Color(0xFFA0A0A0),
)

private fun monoPalette(ink: Color, paper: Color, emptyArc: Color) = Palette(
    donutSpent = Color(0xFF4D4D4D),
    donutLeft = Color(0xFFBDBDBD),
    categories = GreyCategories,
    categoryNeutral = Color(0xFF959595),
    emptyArc = emptyArc,
    labelOnPage = ink,
    // No colour behind the page: the pace line in the header says how the month is going.
    washGood = Color.Transparent,
    washBad = Color.Transparent,
    washNone = Color.Transparent,
    frostBackground = paper,
    frostTint = paper.copy(alpha = 0.35f),
    outlined = true,
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/**
 * Text colour that stays readable on a donut slice of [fill]: dark on the
 * pastels and light greys, white on the dark greys.
 */
fun labelOn(fill: Color): Color = if (fill.luminance() > 0.179f) Color(0xFF26262B) else Color.White

/**
 * The outline that sets a card apart in black-and-white mode; null in the
 * other themes, where the card's own colour does that. Pass it to a Surface's
 * `border`.
 */
@Composable
fun cardBorder(): BorderStroke? =
    if (LocalPalette.current.outlined) BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface) else null

/** [cardBorder] for a card drawn with a plain background instead of a Surface. */
@Composable
fun Modifier.cardOutline(shape: Shape): Modifier = cardBorder()?.let { border(it, shape) } ?: this

/**
 * Soft pastels for charts and colour washes. Light enough that dark text
 * stays readable on top of any of them. Screens take these through
 * [LocalPalette], which swaps them per theme, not from here.
 */
object Pastel {
    val Sky = Color(0xFFA9DFFB)
    val Lilac = Color(0xFFE3C6F7)

    /** Soft washes of colour behind the page. */
    val WashLavender = Color(0xFFDCD4FF)

    /** Behind the home page while spending is going well. */
    val WashGood = Color(0xFFBDEFCB)

    /** Behind the home page once spending runs ahead of the budget. */
    val WashBad = Color(0xFFFFC7C7)

    /**
     * One colour per category, by its place in the category list, for the Budget
     * screen's donut and the dots that key it. None is blue or lilac (Home's
     * donut) nor green or red (the page's good/bad wash), so a colour never
     * means two things. Neighbours alternate warm and cool to stay apart.
     * Never a greyish blue such as steel (#C9D3E6): the user dislikes it and
     * asked for it never to be used anywhere in the app.
     */
    val Categories = listOf(
        Color(0xFFF8D6A8), // peach
        Color(0xFFC6D7BE), // sage
        Color(0xFFF2BFD3), // blush
        Color(0xFFAEE6DA), // mint
        Color(0xFFE6E58C), // lemon
        Color(0xFFD8C2D9), // mauve
        Color(0xFFFFC4A3), // apricot
        Color(0xFFC3C0F4), // periwinkle
        Color(0xFFE5D3B3), // sand
        Color(0xFFF6CF8A), // honey
    )

    /** For categories past the palette's end, and for spending under a removed category. */
    val CategoryNeutral = Color(0xFFDCDAE2)
}

// Near-white page, white cards, black accents, pastel washes behind.
private val LightColors = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    background = Color(0xFFF8F7FC),
    onBackground = Color(0xFF0B0B0B),
    surface = Color.White,
    onSurface = Color(0xFF0B0B0B),
    onSurfaceVariant = Color(0xFF6B6B6B),
    outline = Color(0xFFD3D2DC),
    outlineVariant = Color(0xFFECEBF2),
)

private val DarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color(0xFF0B0B0B),
    background = Color(0xFF0B0B0B),
    onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF1C1C1C),
    onSurface = Color(0xFFF1F1F1),
    onSurfaceVariant = Color(0xFFA0A0A0),
    outline = Color(0xFF4A4A4A),
    outlineVariant = Color(0xFF2E2E2E),
)

/**
 * Thin but readable, to match the thin Phosphor icons: body text is Light (300),
 * small labels and titles stay Normal (400) so they don't go spidery. Reach
 * for Medium only where a figure must stand out; avoid Bold.
 */
private val ThinTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Light),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Light),
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Light),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Light),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Light),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Light),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Normal),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Normal),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.Normal),
        bodyLarge = bodyLarge.copy(fontWeight = FontWeight.Light),
        bodyMedium = bodyMedium.copy(fontWeight = FontWeight.Light),
        bodySmall = bodySmall.copy(fontWeight = FontWeight.Light),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Normal),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Normal),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.Normal),
    )
}

/**
 * Black and white only: [ink] on [paper] and nothing else. Every slot is set,
 * so no Material component falls back to a default colour or a grey.
 */
private fun monoColors(ink: Color, paper: Color, dark: Boolean) =
    (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = ink,
        onPrimary = paper,
        primaryContainer = paper,
        onPrimaryContainer = ink,
        inversePrimary = paper,
        secondary = ink,
        onSecondary = paper,
        secondaryContainer = paper,
        onSecondaryContainer = ink,
        tertiary = ink,
        onTertiary = paper,
        tertiaryContainer = paper,
        onTertiaryContainer = ink,
        background = paper,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        surfaceVariant = paper,
        onSurfaceVariant = ink,
        surfaceTint = paper,
        inverseSurface = ink,
        inverseOnSurface = paper,
        error = ink,
        onError = paper,
        errorContainer = paper,
        onErrorContainer = ink,
        outline = ink,
        outlineVariant = ink,
    )

/**
 * [darkTheme] picks the light or dark look. [blackAndWhite] strips either one
 * to pure black and white (black on white when light, white on black when
 * dark), with greys kept for the donuts alone.
 */
@Composable
fun BudgetAppTheme(
    darkTheme: Boolean = false,
    blackAndWhite: Boolean = false,
    content: @Composable () -> Unit,
) {
    val ink = if (darkTheme) Color.White else Color.Black
    val paper = if (darkTheme) Color.Black else Color.White
    val status = when {
        blackAndWhite -> monoStatus(ink, paper)
        darkTheme -> DarkStatus
        else -> LightStatus
    }
    val palette = when {
        blackAndWhite -> monoPalette(ink, paper, emptyArc = if (darkTheme) Color(0xFF3A3A3A) else Color(0xFFDDDDDD))
        darkTheme -> DarkPalette
        else -> LightPalette
    }
    val colors = when {
        blackAndWhite -> monoColors(ink, paper, darkTheme)
        darkTheme -> DarkColors
        else -> LightColors
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalStatusColors provides status,
        LocalPalette provides palette,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = ThinTypography,
            content = content,
        )
    }
}
