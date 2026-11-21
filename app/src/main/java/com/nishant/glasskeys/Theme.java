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
        // soft pastel palettes
        new Theme("Blush Lilac", 0xFFF3E8F7, new int[]{0xFFD9B8F0, 0xFFF6C1DA, 0xFFC9D2FF}, 0xFF2A1B33, 0xFF8B5CC7, false),
        new Theme("Cotton Candy", 0xFFFBE9F1, new int[]{0xFFFFB7D5, 0xFFB8E0FF, 0xFFE3C6FF}, 0xFF2B1A24, 0xFFE0569A, false),
        new Theme("Sunset Peach", 0xFFFCEDE3, new int[]{0xFFFFC4A3, 0xFFFFA6A6, 0xFFFFDFA0}, 0xFF33201A, 0xFFE8743B, false),
        new Theme("Fresh Mint", 0xFFE6F6EF, new int[]{0xFFA8E6CF, 0xFFB5E3F5, 0xFFD5F2B8}, 0xFF14291F, 0xFF1F9D6B, false),
        // filled in from your wallpaper's Material You colours (Android 12+); see initDynamic
        new Theme("Material You", 0xFF0A0C10, new int[]{0xFF2A3342, 0xFF3A4556, 0xFF1C2230}, 0xFFF4F6FA, 0xFF9DB4D6, true),
    };

    /** Index of the "Material You" entry in ALL. */
    public static final int DYNAMIC = 12;

    /** Rebuilds "Material You" from the system palette (follows the wallpaper and light/dark mode). */
    public static void initDynamic(android.content.Context c) {
        if (android.os.Build.VERSION.SDK_INT < 31 || c == null) return;
        try {
            android.content.res.Resources r = c.getResources();
            boolean night = (r.getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            if (night) {
                ALL[DYNAMIC] = new Theme("Material You", r.getColor(android.R.color.system_neutral1_900, null),
                        new int[]{r.getColor(android.R.color.system_accent1_600, null), r.getColor(android.R.color.system_accent2_600, null),
                                r.getColor(android.R.color.system_accent3_600, null)},
                        r.getColor(android.R.color.system_neutral1_50, null), r.getColor(android.R.color.system_accent1_200, null), true);
            } else {
                ALL[DYNAMIC] = new Theme("Material You", r.getColor(android.R.color.system_neutral1_50, null),
                        new int[]{r.getColor(android.R.color.system_accent1_200, null), r.getColor(android.R.color.system_accent2_200, null),
                                r.getColor(android.R.color.system_accent3_200, null)},
                        r.getColor(android.R.color.system_neutral1_900, null), r.getColor(android.R.color.system_accent1_600, null), false);
            }
        } catch (Exception ignored) { }
    }

    /** Shadow Realm theme pack (kept separate from the Liquid Glass colour list). */
    public static final Theme SHADOW = new Theme("Shadow Realm", 0xFF07050F,
            new int[]{0xFF3A1A8C, 0xFF6A3DF0, 0xFF1A0F40}, 0xFFEFE8FF, 0xFF8B5CFF, true);

    /** Still pack: quiet, text-first monochrome on pure black (pairs with the Still launcher). */
    public static final Theme STILL = new Theme("Still", 0xFF000000,
            new int[]{0xFF0A0A0A, 0xFF111111, 0xFF080808}, 0xFFE8E8E8, 0xFFFFFFFF, true);

    /** Soft Glass pack: graphite glass keys, blue action, red delete. */
    public static final Theme SOFT = new Theme("Soft Glass", 0xFF1C1C1E,
            new int[]{0xFF2C2C30, 0xFF3A3A40, 0xFF26262A}, 0xFFFFFFFF, 0xFF0A84FF, true);

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
