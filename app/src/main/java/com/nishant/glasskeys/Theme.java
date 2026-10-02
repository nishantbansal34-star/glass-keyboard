package com.nishant.glasskeys;

import android.graphics.Color;

/** Colour sets for the frosted backdrop and the glass keys. */
public class Theme {
    public final String name;
    public final int base;
    public final int[] blobs;
    public final int text;
    public final int subText;
    public final int accent;
    public final boolean dark;

    Theme(String name, int base, int[] blobs, int text, int accent, boolean dark) {
        this.name = name;
        this.base = base;
        this.blobs = blobs;
        this.text = text;
        this.subText = (text & 0x00FFFFFF) | 0x99000000;
        this.accent = accent;
        this.dark = dark;
    }

    public static final Theme[] ALL = {
        new Theme("Graphite", 0xFF0A0C10, new int[]{0xFF2A3342, 0xFF3A4556, 0xFF1C2230}, 0xFFF4F6FA, 0xFF9DB4D6, true),
        new Theme("Aurora", 0xFF0E1024, new int[]{0xFF6A3DF0, 0xFF10B6C9, 0xFFE0457B}, Color.WHITE, 0xFF7C5CFF, true),
        new Theme("Midnight", 0xFF05070D, new int[]{0xFF1B2A6B, 0xFF3A1670, 0xFF0B4A5A}, Color.WHITE, 0xFF4F8CFF, true),
        new Theme("Frost", 0xFFDCE6F2, new int[]{0xFF9DBDFF, 0xFFFFBCD9, 0xFFA8EEDF}, 0xFF1B1F2A, 0xFF3B6BFF, false),
        new Theme("Rose", 0xFF1A0B14, new int[]{0xFFE0457B, 0xFFFF8A5C, 0xFF8E2DE2}, Color.WHITE, 0xFFFF5C8A, true),
        new Theme("Ocean", 0xFF031622, new int[]{0xFF0077B6, 0xFF00B4D8, 0xFF1E3A8A}, Color.WHITE, 0xFF00B4D8, true),
        new Theme("Saffron", 0xFF1A1005, new int[]{0xFFFF9933, 0xFFE0457B, 0xFFFFC94D}, Color.WHITE, 0xFFFF9933, true),
        new Theme("Emerald", 0xFF04140F, new int[]{0xFF10B981, 0xFF0E7490, 0xFF84CC16}, Color.WHITE, 0xFF10B981, true),
    };

    /** Shadow Realm theme pack (kept separate from the Liquid Glass colour list). */
    public static final Theme SHADOW = new Theme("Shadow Realm", 0xFF07050F,
            new int[]{0xFF3A1A8C, 0xFF6A3DF0, 0xFF1A0F40}, 0xFFEFE8FF, 0xFF8B5CFF, true);

    /** Still pack: quiet, text-first monochrome on pure black (pairs with the Still launcher). */
    public static final Theme STILL = new Theme("Still", 0xFF000000,
            new int[]{0xFF0A0A0A, 0xFF111111, 0xFF080808}, 0xFFE8E8E8, 0xFFFFFFFF, true);

    /** Same accent, frosted light glass with dark text (for bright wallpapers). */
    public Theme lightVariant() {
        if (!dark) return this;
        return new Theme(name, 0xFFE9EDF2, blobs, 0xFF15181E, accent, false);
    }

    public static Theme get(int i) {
        if (i < 0 || i >= ALL.length) i = 0;
        return ALL[i];
    }
}
