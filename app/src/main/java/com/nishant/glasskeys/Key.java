package com.nishant.glasskeys;

import android.graphics.RectF;

public class Key {
    // Special codes (negative); printable keys use their character code.
    public static final int SHIFT = -1, SYMBOLS = -2, ALPHA = -3, ENTER = -4, DELETE = -5, EMOJI = -6,
            SYMBOLS2 = -7, SPACE = 32;

    public final String label;
    public final int code;
    public final float weight;
    public final String hint;        // small corner hint (e.g. number on top row)
    public final String[] popups;    // long-press alternatives
    public final RectF rect = new RectF();

    public Key(String label, int code, float weight, String hint, String[] popups) {
        this.label = label;
        this.code = code;
        this.weight = weight;
        this.hint = hint;
        this.popups = popups;
    }

    public boolean isFunction() {
        return code == SHIFT || code == DELETE || code == SYMBOLS || code == ALPHA || code == SYMBOLS2
                || code == EMOJI;
    }

    public boolean isChar() { return code > 0 && code != SPACE; }
}
