package com.nishant.glasskeys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Swipe ("glide") typing recogniser.
 *
 * The finger path is resampled to fixed points and compared with the ideal path through the
 * key centres of candidate words (shape + location matching). Candidates are pruned hard first:
 * the word must start near where the finger started, end near where it lifted, and every letter
 * must be passed near, in order. Final score mixes path distance with word frequency.
 */
public class GlideDecoder {
    private static final int N = 40;

    /** Key centres for 'a'..'z' in key-width units (set whenever the layout changes). */
    private final float[] kx = new float[26], ky = new float[26];
    private final boolean[] has = new boolean[26];

    private final Dictionary dict;

    public GlideDecoder(Dictionary dict) { this.dict = dict; }

    public synchronized void setKey(char c, float cx, float cy) {
        int i = c - 'a';
        if (i < 0 || i >= 26) return;
        kx[i] = cx; ky[i] = cy; has[i] = true;
    }

    public static class Result {
        public final List<String> words = new ArrayList<>();
    }

    private static class Cand implements Comparable<Cand> {
        String w; double cost;
        Cand(String w, double c) { this.w = w; cost = c; }
        public int compareTo(Cand o) { return Double.compare(cost, o.cost); }
    }

    /** px/py are in key-width units. Returns the best words, best first. */
    public synchronized Result decode(float[] px, float[] py, int n, String prevWord, boolean partial) {
        Result res = new Result();
        if (n < 2) return res;
        float[][] user = resample(px, py, n);
        float sx = user[0][0], sy = user[1][0];
        float ex = user[0][N - 1], ey = user[1][N - 1];
        float pathLen = length(px, py, n);

        // letters whose keys are near the start / end of the swipe
        boolean[] startOk = new boolean[26], endOk = new boolean[26];
        for (int i = 0; i < 26; i++) {
            if (!has[i]) continue;
            startOk[i] = dist(sx, sy, kx[i], ky[i]) < 1.05f;
            endOk[i] = partial || dist(ex, ey, kx[i], ky[i]) < 1.1f;
        }

        List<Cand> cands = new ArrayList<>();
        String[] words = dict.words();
        int[] freq = dict.freqs();
        Map<String, Integer> learned = dict.learnedMap();
        for (int b = 0; b < 26; b++) {
            if (!startOk[b]) continue;
            int[] range = dict.bucket((char) ('a' + b));
            if (range == null) continue;
            for (int k = range[0]; k < range[1]; k++) {
                String w = words[k];
                // rare 2-3 letter tokens (abbreviations, junk) just get in the way when swiping
                if (w.length() <= 3 && freq[k] < 380 && !learned.containsKey(w)) continue;
                if (w.length() <= 5 && freq[k] < 250 && !learned.containsKey(w)) continue;
                double c = score(w, user, endOk, pathLen, partial);
                if (c < 0) continue;
                double f = freq[k] / 100.0;                       // zipf-like 1.5 .. 7.7
                Integer lc = learned.get(w);
                if (lc != null) f += Math.min(1.5, lc * 0.15);
                c -= 0.55 * f;
                c -= dict.bigramScore(prevWord, w) * 0.4;
                cands.add(new Cand(w, c));
            }
        }
        // words you've taught the keyboard that aren't in the dictionary
        for (Map.Entry<String, Integer> e : learned.entrySet()) {
            String w = e.getKey();
            if (e.getValue() < 2 || w.isEmpty() || dict.isDictWord(w)) continue;
            int b = w.charAt(0) - 'a';
            if (b < 0 || b >= 26 || !startOk[b]) continue;
            double c = score(w, user, endOk, pathLen, partial);
            if (c < 0) continue;
            cands.add(new Cand(w, c - 0.55 * (4.2 + Math.min(1.5, e.getValue() * 0.15))));
        }
        Collections.sort(cands);
        for (Cand c : cands) {
            if (res.words.size() >= 4) break;
            if (!res.words.contains(c.w)) res.words.add(c.w);
        }
        return res;
    }

    /** Path distance cost for one word, or -1 if the word can't match the swipe. */
    private double score(String w, float[][] user, boolean[] endOk, float pathLen, boolean partial) {
        int L = w.length();
        if (L < 1 || L > 20) return -1;
        // collapse the word into its key sequence ("hello" -> h e l o)
        int[] seq = new int[L];
        int m = 0;
        for (int i = 0; i < L; i++) {
            int c = w.charAt(i) - 'a';
            if (c < 0 || c >= 26 || !has[c]) {
                if (w.charAt(i) == '\'') continue; // apostrophes are free
                return -1;
            }
            if (m == 0 || seq[m - 1] != c) seq[m++] = c;
        }
        if (m == 0) return -1;
        if (m == 1 && pathLen > 1.2f) return -1;
        if (!endOk[seq[m - 1]]) return -1;

        // ideal path length should roughly match what the finger drew
        float ideal = 0;
        for (int i = 1; i < m; i++) ideal += dist(kx[seq[i - 1]], ky[seq[i - 1]], kx[seq[i]], ky[seq[i]]);
        if (!partial && (ideal > pathLen * 1.9f + 1.2f || ideal < pathLen * 0.45f - 1.2f)) return -1;

        // every key must be passed near, in order: take the first close approach (its local minimum)
        int j = 0;
        float thr = 0.95f;
        for (int i = 0; i < m; i++) {
            float bx = kx[seq[i]], by = ky[seq[i]];
            int k = j;
            while (k < N && dist(user[0][k], user[1][k], bx, by) >= thr) k++;
            if (k >= N) return -1;
            while (k + 1 < N && dist(user[0][k + 1], user[1][k + 1], bx, by) < dist(user[0][k], user[1][k], bx, by)) k++;
            j = k;
        }

        // location distance between resampled paths
        float[] ix = new float[m], iy = new float[m];
        for (int i = 0; i < m; i++) { ix[i] = kx[seq[i]]; iy[i] = ky[seq[i]]; }
        float[][] ideal2 = m == 1 ? single(ix[0], iy[0]) : resample(ix, iy, m);
        double sum = 0;
        for (int k = 0; k < N; k++) sum += dist(user[0][k], user[1][k], ideal2[0][k], ideal2[1][k]);
        double mean = sum / N;
        double c = (mean / 0.32) * (mean / 0.32);
        // where the finger lifts is deliberate: weigh the last letter extra (and the first a little)
        if (!partial) {
            float de = dist(user[0][N - 1], user[1][N - 1], ix[m - 1], iy[m - 1]);
            c += 1.6 * (de / 0.45) * (de / 0.45);
        }
        float ds = dist(user[0][0], user[1][0], ix[0], iy[0]);
        c += 0.6 * (ds / 0.45) * (ds / 0.45);
        // letters spread along the path should be passed close by, not just roughly
        return c;
    }

    private static float[][] single(float x, float y) {
        float[][] r = new float[2][N];
        for (int i = 0; i < N; i++) { r[0][i] = x; r[1][i] = y; }
        return r;
    }

    private static float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float length(float[] x, float[] y, int n) {
        float s = 0;
        for (int i = 1; i < n; i++) s += dist(x[i - 1], y[i - 1], x[i], y[i]);
        return s;
    }

    /** Resamples a polyline to N equally spaced points. */
    static float[][] resample(float[] x, float[] y, int n) {
        float[][] out = new float[2][N];
        float total = length(x, y, n);
        if (total < 1e-4f) {
            for (int i = 0; i < N; i++) { out[0][i] = x[0]; out[1][i] = y[0]; }
            return out;
        }
        float step = total / (N - 1);
        out[0][0] = x[0]; out[1][0] = y[0];
        int k = 1;
        float acc = 0;
        float cx = x[0], cy = y[0];
        int i = 1;
        while (i < n && k < N) {
            float d = dist(cx, cy, x[i], y[i]);
            if (acc + d >= step && d > 0) {
                float t = (step - acc) / d;
                float nx = cx + t * (x[i] - cx), ny = cy + t * (y[i] - cy);
                out[0][k] = nx; out[1][k] = ny; k++;
                cx = nx; cy = ny; acc = 0;
            } else {
                acc += d;
                cx = x[i]; cy = y[i];
                i++;
            }
        }
        while (k < N) { out[0][k] = x[n - 1]; out[1][k] = y[n - 1]; k++; }
        return out;
    }
}
