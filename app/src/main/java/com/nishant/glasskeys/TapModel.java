package com.nishant.glasskeys;

/**
 * Where you actually touch. Each typed letter keeps the exact spot your finger landed (in key-width units);
 * spelling correction then treats "you hit the edge of E, right next to R" as a very likely R, while a tap in
 * the middle of E stays an E. It also learns your personal touch pattern per key (e.g. thumbs landing low).
 */
final class TapModel {
    private static final float[] cx = new float[26], cy = new float[26], ox = new float[26], oy = new float[26];
    private static final boolean[] has = new boolean[26];
    /** Tap points for the word currently being scored (index = letter position), or null. */
    static float[][] taps;
    /** Spot of the most recent key tap, consumed by the keyboard service. */
    static float lastX = Float.NaN, lastY = Float.NaN;
    private static final float MAX_OFF = 0.32f;

    static void setKey(char c, float x, float y) {
        int i = c - 'a';
        if (i < 0 || i >= 26) return;
        cx[i] = x; cy[i] = y; has[i] = true;
    }

    /** Typing-error cost of reading a tap that produced `typed` as letter `want` (0 = same letter). */
    static float sub(int pos, char typed, char want) {
        float[][] t = taps;
        int w = want - 'a';
        if (t != null && pos >= 0 && pos < t.length && t[pos] != null && w >= 0 && w < 26 && has[w]) {
            float dx = t[pos][0] - (cx[w] + ox[w]), dy = t[pos][1] - (cy[w] + oy[w]);
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            return Math.max(0.05f, Math.min(1f, (d - 0.42f) / 0.85f));
        }
        return Float.NaN;   // no tap information: caller falls back to the neighbour-key rule
    }

    /** You kept `word` for these taps: drift each key's centre slightly toward where you really press it. */
    static void learn(String word, float[][] t) {
        if (word == null || t == null || word.length() != t.length) return;
        for (int i = 0; i < word.length(); i++) {
            int c = Character.toLowerCase(word.charAt(i)) - 'a';
            if (c < 0 || c >= 26 || !has[c] || t[i] == null) continue;
            float tx = t[i][0] - cx[c], ty = t[i][1] - cy[c];
            if (tx * tx + ty * ty > 0.8f * 0.8f) continue;   // a stray tap, not a habit
            ox[c] = clamp(ox[c] + (tx - ox[c]) * 0.04f);
            oy[c] = clamp(oy[c] + (ty - oy[c]) * 0.04f);
        }
    }

    private static float clamp(float v) { return Math.max(-MAX_OFF, Math.min(MAX_OFF, v)); }

    static String offsets() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 26; i++) sb.append(Math.round(ox[i] * 1000) / 1000f).append(',').append(Math.round(oy[i] * 1000) / 1000f).append(';');
        return sb.toString();
    }

    static void setOffsets(String s) {
        if (s == null || s.isEmpty()) return;
        String[] p = s.split(";");
        for (int i = 0; i < 26 && i < p.length; i++) {
            String[] xy = p[i].split(",");
            if (xy.length != 2) continue;
            try { ox[i] = clamp(Float.parseFloat(xy[0])); oy[i] = clamp(Float.parseFloat(xy[1])); } catch (Exception ignored) { }
        }
    }

    static void reset() { for (int i = 0; i < 26; i++) { ox[i] = 0; oy[i] = 0; } }
}
