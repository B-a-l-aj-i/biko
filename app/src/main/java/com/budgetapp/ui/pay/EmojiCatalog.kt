package com.budgetapp.ui.pay

import android.graphics.Paint
import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.os.Build
import java.util.Locale

/** Tabs of the emoji panel, in display order: what a spending category is likely to be comes first. */
enum class EmojiCategory(val label: String) {
    FOOD("Food and drink"),
    TRAVEL("Travel and places"),
    OBJECTS("Objects"),
    ACTIVITIES("Activities"),
    NATURE("Animals and nature"),
    PEOPLE("People"),
    SMILEYS("Smileys"),
    SYMBOLS("Symbols"),
}

/** One emoji, its lower-case Unicode name (which search matches against) and its tab. */
data class EmojiEntry(val emoji: String, val name: String, val category: EmojiCategory)

/**
 * The emoji this phone can draw, read from the system rather than a bundled
 * list, so no library or data file is needed.
 */
object EmojiCatalog {

    /**
     * Grouped by category, in [EmojiCategory] order. Slow the first time (checks
     * every candidate against the font); call off the main thread.
     */
    val all: List<EmojiEntry> by lazy(::build)

    private val ranges = listOf(
        0x1F300..0x1F5FF,
        0x1F600..0x1F64F,
        0x1F680..0x1F6FF,
        0x1F7E0..0x1F7EB,
        0x1F900..0x1F9FF,
        0x1FA70..0x1FAFF,
        0x2600..0x27BF,
    )
    private val skinTones = 0x1F3FB..0x1F3FF

    /**
     * Which tab each stretch of Unicode belongs to. Unicode orders emoji only
     * roughly by theme, so this is hand-made and approximate; the first
     * matching rule wins, which is why narrow exceptions sit above the wide
     * range they carve out of. Anything unlisted is an object.
     */
    private val rules: List<Pair<IntRange, EmojiCategory>> = listOf(
        // Weather, plants, food, celebrations, sport, buildings, animals.
        0x1F32D..0x1F32F to EmojiCategory.FOOD,
        0x1F300..0x1F344 to EmojiCategory.NATURE,
        0x1F345..0x1F37F to EmojiCategory.FOOD,
        0x1F382..0x1F382 to EmojiCategory.FOOD,
        0x1F380..0x1F393 to EmojiCategory.ACTIVITIES,
        0x1F3A0..0x1F3D3 to EmojiCategory.ACTIVITIES,
        0x1F3D4..0x1F3F0 to EmojiCategory.TRAVEL,
        0x1F3F4..0x1F3F4 to EmojiCategory.SYMBOLS,
        0x1F3F8..0x1F3F9 to EmojiCategory.ACTIVITIES,
        0x1F400..0x1F43F to EmojiCategory.NATURE,
        // Body parts, clothing, people, hearts.
        0x1F440..0x1F450 to EmojiCategory.PEOPLE,
        0x1F451..0x1F463 to EmojiCategory.OBJECTS,
        0x1F479..0x1F480 to EmojiCategory.SMILEYS,
        0x1F464..0x1F487 to EmojiCategory.PEOPLE,
        0x1F48B..0x1F48C to EmojiCategory.SMILEYS,
        0x1F48D..0x1F48E to EmojiCategory.OBJECTS,
        0x1F490..0x1F490 to EmojiCategory.NATURE,
        0x1F492..0x1F492 to EmojiCategory.TRAVEL,
        0x1F48F..0x1F491 to EmojiCategory.PEOPLE,
        0x1F493..0x1F49F to EmojiCategory.SMILEYS,
        0x1F4A1..0x1F4A1 to EmojiCategory.OBJECTS,
        0x1F4AA..0x1F4AA to EmojiCategory.PEOPLE,
        0x1F4AE..0x1F4AE to EmojiCategory.NATURE,
        0x1F4A0..0x1F4AF to EmojiCategory.SMILEYS,
        // Money, office, phones, tools, then signs and clocks.
        0x1F4B0..0x1F4FF to EmojiCategory.OBJECTS,
        0x1F50B..0x1F517 to EmojiCategory.OBJECTS,
        0x1F525..0x1F525 to EmojiCategory.NATURE,
        0x1F526..0x1F52E to EmojiCategory.OBJECTS,
        0x1F500..0x1F567 to EmojiCategory.SYMBOLS,
        0x1F57A..0x1F57A to EmojiCategory.PEOPLE,
        0x1F595..0x1F596 to EmojiCategory.PEOPLE,
        0x1F5A4..0x1F5A4 to EmojiCategory.SMILEYS,
        0x1F5FB..0x1F5FF to EmojiCategory.TRAVEL,
        // Faces, then gestures.
        0x1F645..0x1F64F to EmojiCategory.PEOPLE,
        0x1F600..0x1F644 to EmojiCategory.SMILEYS,
        // Transport, with the signs and bathroom fittings that share its block.
        0x1F6AA..0x1F6AA to EmojiCategory.OBJECTS,
        0x1F6B2..0x1F6B2 to EmojiCategory.TRAVEL,
        0x1F6B4..0x1F6B6 to EmojiCategory.ACTIVITIES,
        0x1F6BD..0x1F6BD to EmojiCategory.OBJECTS,
        0x1F6BF..0x1F6C1 to EmojiCategory.OBJECTS,
        0x1F6A9..0x1F6C5 to EmojiCategory.SYMBOLS,
        0x1F6CB..0x1F6CC to EmojiCategory.OBJECTS,
        0x1F6D0..0x1F6D0 to EmojiCategory.SYMBOLS,
        0x1F6D2..0x1F6D2 to EmojiCategory.OBJECTS,
        0x1F6DC..0x1F6DC to EmojiCategory.SYMBOLS,
        0x1F680..0x1F6FF to EmojiCategory.TRAVEL,
        0x1F7E0..0x1F7EB to EmojiCategory.SYMBOLS,
        // The newer, more mixed block.
        0x1F90D..0x1F90E to EmojiCategory.SMILEYS,
        0x1F90C..0x1F90F to EmojiCategory.PEOPLE,
        0x1F918..0x1F91F to EmojiCategory.PEOPLE,
        0x1F926..0x1F926 to EmojiCategory.PEOPLE,
        0x1F910..0x1F92F to EmojiCategory.SMILEYS,
        0x1F938..0x1F93E to EmojiCategory.ACTIVITIES,
        0x1F930..0x1F937 to EmojiCategory.PEOPLE,
        0x1F940..0x1F940 to EmojiCategory.NATURE,
        0x1F941..0x1F941 to EmojiCategory.ACTIVITIES,
        0x1F942..0x1F944 to EmojiCategory.FOOD,
        0x1F945..0x1F94F to EmojiCategory.ACTIVITIES,
        0x1F950..0x1F96F to EmojiCategory.FOOD,
        0x1F970..0x1F97A to EmojiCategory.SMILEYS,
        0x1F980..0x1F9AE to EmojiCategory.NATURE,
        0x1F9AF..0x1F9AF to EmojiCategory.OBJECTS,
        0x1F9BA..0x1F9BA to EmojiCategory.OBJECTS,
        0x1F9BC..0x1F9BD to EmojiCategory.TRAVEL,
        0x1F9B0..0x1F9BF to EmojiCategory.PEOPLE,
        0x1F9C0..0x1F9CB to EmojiCategory.FOOD,
        0x1F9CC..0x1F9E0 to EmojiCategory.PEOPLE,
        0x1F9E1..0x1F9E1 to EmojiCategory.SMILEYS,
        0x1FA75..0x1FA77 to EmojiCategory.SMILEYS,
        0x1FA80..0x1FA88 to EmojiCategory.ACTIVITIES,
        0x1FAB0..0x1FABF to EmojiCategory.NATURE,
        0x1FAC0..0x1FACF to EmojiCategory.PEOPLE,
        0x1FAD0..0x1FADF to EmojiCategory.FOOD,
        0x1FAE0..0x1FAEF to EmojiCategory.SMILEYS,
        0x1FAF0..0x1FAFF to EmojiCategory.PEOPLE,
        // The oldest symbols, with a few everyday things among them.
        0x2615..0x2615 to EmojiCategory.FOOD,
        0x2614..0x2614 to EmojiCategory.NATURE,
        0x26A1..0x26A1 to EmojiCategory.NATURE,
        0x26C4..0x26C5 to EmojiCategory.NATURE,
        0x26BD..0x26BE to EmojiCategory.ACTIVITIES,
        0x26F3..0x26F3 to EmojiCategory.ACTIVITIES,
        0x2693..0x2693 to EmojiCategory.TRAVEL,
        0x26EA..0x26EA to EmojiCategory.TRAVEL,
        0x26F2..0x26F2 to EmojiCategory.TRAVEL,
        0x26F5..0x26F5 to EmojiCategory.TRAVEL,
        0x26FA..0x26FA to EmojiCategory.TRAVEL,
        0x26FD..0x26FD to EmojiCategory.TRAVEL,
        0x270A..0x270B to EmojiCategory.PEOPLE,
        0x2600..0x27BF to EmojiCategory.SYMBOLS,
    )

    private fun categoryOf(codePoint: Int): EmojiCategory =
        rules.firstOrNull { codePoint in it.first }?.second ?: EmojiCategory.OBJECTS

    private fun build(): List<EmojiEntry> {
        val paint = Paint()
        val entries = ArrayList<EmojiEntry>(1_400)
        for (range in ranges) {
            for (codePoint in range) {
                if (codePoint in skinTones) continue
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    // Skips characters that draw as plain text symbols by default.
                    if (!UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI_PRESENTATION)) continue
                } else if (codePoint < 0x1F000) {
                    // Can't tell emoji from text symbols on old Android; leave that range out.
                    continue
                }
                val emoji = String(Character.toChars(codePoint))
                if (!paint.hasGlyph(emoji)) continue
                val name = UCharacter.getName(codePoint)?.lowercase(Locale.ROOT).orEmpty()
                entries += EmojiEntry(emoji, name, categoryOf(codePoint))
            }
        }
        // Stable sort: within a category, emoji keep their Unicode order.
        return entries.sortedBy { it.category.ordinal }
    }

    /** Everyday words mapped to the word Unicode uses in the emoji's name. */
    private val aliases = mapOf(
        "gift" to "present",
        "car" to "automobile",
        "auto" to "rickshaw",
        "bike" to "bicycle",
        "flight" to "airplane",
        "travel" to "airplane",
        "petrol" to "fuel",
        "gas" to "fuel",
        "movie" to "clapper",
        "film" to "clapper",
        "medicine" to "pill",
        "doctor" to "hospital",
        "phone" to "telephone",
        "mobile" to "mobile phone",
        "grocery" to "shopping",
        "groceries" to "shopping",
        "clothes" to "shirt",
        "rent" to "house",
        "home" to "house",
        "food" to "fork",
        "coffee" to "hot beverage",
        "tea" to "teacup",
        "cash" to "banknote",
        "bill" to "receipt",
        "electricity" to "light bulb",
        "internet" to "globe",
        "pet" to "dog",
        "gym" to "lifter",
        "salon" to "haircut",
        "drink" to "glass",
        "study" to "book",
        "fees" to "school",
    )

    fun search(entries: List<EmojiEntry>, query: String): List<EmojiEntry> {
        val word = query.trim().lowercase(Locale.ROOT)
        if (word.isEmpty()) return entries
        val alias = aliases[word]
        return entries.filter { word in it.name || (alias != null && alias in it.name) }
    }
}
