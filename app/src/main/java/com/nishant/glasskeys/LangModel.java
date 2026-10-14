package com.nishant.glasskeys;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Built-in context model: which words usually follow one or two previous words, learned offline from
 * ~1.4 million words of everyday conversation plus common Indian shop / WhatsApp phrases.
 * Also holds the QWERTY neighbour map used to judge how likely a typo is.
 */
final class LangModel {
    static final class Next {
        final int total; final String[] w; final int[] c;
        Next(int total, String[] w, int[] c) { this.total = total; this.w = w; this.c = c; }
        int count(String word) { for (int i = 0; i < w.length; i++) if (w[i].equals(word)) return c[i]; return 0; }
    }

    final Map<String, Next> bi = new HashMap<>(), tri = new HashMap<>();

    void load(InputStream in) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\t");
                if (p.length != 4) continue;
                String[] items = p[3].split(" ");
                String[] w = new String[items.length];
                int[] c = new int[items.length];
                for (int i = 0; i < items.length; i++) {
                    int k = items[i].lastIndexOf(':');
                    w[i] = items[i].substring(0, k);
                    c[i] = Integer.parseInt(items[i].substring(k + 1));
                }
                Next n = new Next(Integer.parseInt(p[2]), w, c);
                if (p[0].equals("B")) bi.put(p[1], n); else tri.put(p[1], n);
            }
        } catch (Exception ignored) { }
    }

    /** P(word | context) from the built-in model, 0 when unknown. */
    double prob(String prev2, String prev, String word) {
        double pb = 0, pt = 0;
        Next b = bi.get(prev);
        if (b != null) { int c = b.count(word); if (c > 0) pb = c / (double) b.total; }
        if (prev2 != null) {
            Next t = tri.get(prev2 + " " + prev);
            if (t != null) { int c = t.count(word); if (c > 0) pt = c / (double) t.total; }
            if (t != null) return 0.65 * pt + 0.35 * pb;
        }
        return pb;
    }

    boolean hasContext(String prev2, String prev) {
        return bi.containsKey(prev) || (prev2 != null && tri.containsKey(prev2 + " " + prev));
    }

    // ---------------------------------------------------------------- typo model

    private static final String[] ROWS = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
    private static final boolean[][] NEAR = new boolean[26][26];
    static {
        float[][] pos = new float[26][];
        for (int r = 0; r < 3; r++)
            for (int i = 0; i < ROWS[r].length(); i++)
                pos[ROWS[r].charAt(i) - 'a'] = new float[]{i + r * 0.5f, r};
        for (int a = 0; a < 26; a++) for (int b = 0; b < 26; b++) {
            if (a == b || pos[a] == null || pos[b] == null) continue;
            float dx = pos[a][0] - pos[b][0], dy = pos[a][1] - pos[b][1];
            NEAR[a][b] = dx * dx + dy * dy <= 1.3f;   // touching keys, including diagonals
        }
    }

    static boolean near(char a, char b) {
        int x = a - 'a', y = b - 'a';
        return x >= 0 && x < 26 && y >= 0 && y < 26 && NEAR[x][y];
    }

    private static float sub(char a, char b) {
        if (a == b) return 0f;
        if (near(a, b)) return 0.55f;                 // fat-finger: hit the key next door
        return 1f;
    }

    /**
     * Typing-error cost of turning what was typed (t) into word (w): neighbouring-key slips, swapped
     * letters and missed double letters are cheap. Returns > max as soon as it can't be within max.
     */
    // reusable rows (all ranking runs on the UI thread)
    private static float[] B0 = new float[48], B1 = new float[48], B2 = new float[48];

    static float cost(String t, String w, float max) {
        int n = t.length(), m = w.length();
        if (Math.abs(n - m) > max + 0.01f) return 99f;
        if (m + 1 > B0.length) { B0 = new float[m + 8]; B1 = new float[m + 8]; B2 = new float[m + 8]; }
        float[] p2 = B0, p1 = B1, cur = B2;
        for (int j = 0; j <= m; j++) p1[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            float rowMin = cur[0];
            char ti = t.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                char wj = w.charAt(j - 1);
                float best = p1[j - 1] + sub(ti, wj);
                // missed letter (word has an extra char); cheaper if it's a doubled letter
                float del = (j >= 2 && w.charAt(j - 2) == wj) ? 0.3f : 1f;
                best = Math.min(best, cur[j - 1] + del);
                // extra letter typed; cheaper if it repeats the previous letter or is a neighbour slip
                float ins = (i >= 2 && t.charAt(i - 2) == ti) ? 0.5f : (j >= 1 && near(ti, wj)) ? 0.8f : 1f;
                best = Math.min(best, p1[j] + ins);
                if (i >= 2 && j >= 2 && ti == w.charAt(j - 2) && t.charAt(i - 2) == wj)
                    best = Math.min(best, p2[j - 2] + 0.6f);   // swapped letters ("teh")
                cur[j] = best;
                if (best < rowMin) rowMin = best;
            }
            if (rowMin > max) return 99f;
            float[] tmp = p2; p2 = p1; p1 = cur; cur = tmp;
        }
        float c = p1[m];
        // a wrong first letter is a rarer mistake
        if (n > 0 && m > 0 && t.charAt(0) != w.charAt(0) && !near(t.charAt(0), w.charAt(0))) c += 0.4f;
        return c;
    }

    /** Typing-error cost of t against the start of a longer word (for completions with a typo). */
    static float prefixCost(String t, String w, float max) {
        float best = 99f;
        for (int L = Math.max(1, t.length() - 1); L <= Math.min(w.length(), t.length() + 1); L++) {
            float c = cost(t, w.substring(0, L), max);
            if (c < best) best = c;
        }
        return best;
    }
}
