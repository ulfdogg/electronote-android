package de.graetz.electronote.stickers

enum class StickerCategory(val label: String) {
    ARROWS("Pfeile"),
    SYMBOLS("Symbole")
}

enum class StickerType(val category: StickerCategory, val displayName: String) {
    ARROW_RIGHT(StickerCategory.ARROWS, "Pfeil rechts"),
    ARROW_LEFT(StickerCategory.ARROWS, "Pfeil links"),
    ARROW_UP(StickerCategory.ARROWS, "Pfeil hoch"),
    ARROW_DOWN(StickerCategory.ARROWS, "Pfeil runter"),
    ARROW_CURVED(StickerCategory.ARROWS, "Pfeil gebogen"),

    CHECK(StickerCategory.SYMBOLS, "Haken"),
    CROSS(StickerCategory.SYMBOLS, "Kreuz"),
    STAR(StickerCategory.SYMBOLS, "Stern"),
    HEART(StickerCategory.SYMBOLS, "Herz"),
    EXCLAMATION(StickerCategory.SYMBOLS, "Wichtig"),
    QUESTION(StickerCategory.SYMBOLS, "Frage"),
    SPEECH_BUBBLE(StickerCategory.SYMBOLS, "Sprechblase"),
    FLAG(StickerCategory.SYMBOLS, "Flagge"),
    LIGHTBULB(StickerCategory.SYMBOLS, "Idee")
}
