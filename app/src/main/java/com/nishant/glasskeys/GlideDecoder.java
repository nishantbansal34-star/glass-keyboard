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

    // context for the next swipe (set by the keyboard service)
    private volatile String ctxPrev, ctxPrev2;
    public void setContext(String prev, String prev2) { ctxPrev = prev; ctxPrev2 = prev2; }

    // tuning (key-width units)
    static float START_R = 1.25f, END_R = 1.35f, SIGMA = 0.5f, W_ALIGN = 0.5f, W_SHAPE = 0.8f, W_END = 0.9f, W_START = 0.5f, LAMBDA = 2.0f;

    /** px/py are in key-width units. Returns the best words, best first. */
    public synchronized Result decode(float[] px, float[] py, int n, String prevWord, boolean partial) {
        Result res = new Result();
        if (n < 2) return res;
        String prev = prevWord != null ? prevWord : ctxPrev, prev2 = prevWord != null ? null : ctxPrev2;
        float[][] user = resample(px, py, n);
        for (int ki = 0; ki < 26; ki++) {
            if (!has[ki]) continue;
            for (int j = 0; j < N; j++) {
                float dx = user[0][j] - kx[ki], dy = user[1][j] - ky[ki];
                d2[ki][j] = dx * dx + dy * dy;
            }
        }
        float sx = user[0][0], sy = user[1][0];
        float ex = user[0][N - 1], ey = user[1][N - 1];
        float pathLen = length(px, py, n);

        // letters whose keys are near the start / end of the swipe (generous: fingers start a bit off)
        boolean[] startOk = new boolean[26], endOk = new boolean[26];
        for (int i = 0; i < 26; i++) {
            if (!has[i]) continue;
            startOk[i] = dist(sx, sy, kx[i], ky[i]) < START_R;
            endOk[i] = partial || dist(ex, ey, kx[i], ky[i]) < END_R;
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
                // rare short tokens (abbreviations, junk) just get in the way when swiping
                if (w.length() <= 3 && freq[k] < 360 && !learned.containsKey(w)) continue;
                if (w.length() <= 5 && freq[k] < 240 && !learned.containsKey(w)) continue;
                double c = score(w, user, endOk, pathLen, partial);
                if (c < 0) continue;
                c -= LAMBDA * dict.swipePrior(prev2, prev, w);
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
            cands.add(new Cand(w, c - LAMBDA * dict.swipePrior(prev2, prev, w)));
        }
        Collections.sort(cands);
        for (Cand c : cands) {
            if (res.words.size() >= 4) break;
            if (!res.words.contains(c.w)) res.words.add(c.w);
        }
        return res;
    }

    private final float[] dpPrev = new float[N], dpCur = new float[N];
    private final float[][] d2 = new float[26][N];   // squared distance from each key to each path point

    /** Path cost for one word (lower = better), or -1 if the word can't be what was swiped. */
    private double score(String w, float[][] user, boolean[] endOk, float pathLen, boolean partial) {
        int L = w.length();
        if (L < 1 || L > 22) return -1;
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
        if (m == 1 && pathLen > 1.3f) return -1;
        if (!endOk[seq[m - 1]]) return -1;

        // the ideal path length should roughly match what the finger drew
        float ideal = 0;
        for (int i = 1; i < m; i++) ideal += dist(kx[seq[i - 1]], ky[seq[i - 1]], kx[seq[i]], ky[seq[i]]);
        if (!partial && (ideal > pathLen * 2.0f + 1.4f || ideal < pathLen * 0.4f - 1.4f)) return -1;

        // 1. alignment: every letter is matched, in order, to the closest point of the path it can reach
        //    (soft — a letter cut short at a corner costs a little instead of ruling the word out)
        float inv = 1f / (SIGMA * SIGMA);
        float[] r0 = d2[seq[0]];
        for (int j = 0; j < N; j++) {
            dpPrev[j] = r0[j] * inv;
            if (j > 0 && dpPrev[j - 1] < dpPrev[j]) dpPrev[j] = dpPrev[j - 1];   // running min (monotonic)
        }
        for (int i = 1; i < m; i++) {
            float[] ri = d2[seq[i]];
            float best = Float.MAX_VALUE;
            for (int j = 0; j < N; j++) {
                float v = (j > 0 ? dpPrev[j - 1] : dpPrev[0] + 1f) + Math.min(ri[j] * inv, 9f);
                if (v < best) best = v;
                dpCur[j] = best;
            }
            System.arraycopy(dpCur, 0, dpPrev, 0, N);
        }
        double align = dpPrev[N - 1] / m;
        if (align > 6.0) return -1;

        // 2. shape/location: resampled finger path vs the ideal path through the key centres
        float[] ix = new float[m], iy = new float[m];
        for (int i = 0; i < m; i++) { ix[i] = kx[seq[i]]; iy[i] = ky[seq[i]]; }
        float[][] ideal2 = m == 1 ? single(ix[0], iy[0]) : resample(ix, iy, m);
        double sum = 0;
        for (int k = 0; k < N; k++) sum += dist(user[0][k], user[1][k], ideal2[0][k], ideal2[1][k]);
        double mean = sum / N;
        double c = W_ALIGN * align * 2.2 + W_SHAPE * (mean / 0.34) * (mean / 0.34);
        // where the finger lifts is deliberate: weigh the last letter extra (and the first a little)
        if (!partial) {
            float de = dist(user[0][N - 1], user[1][N - 1], ix[m - 1], iy[m - 1]);
            c += W_END * (de / 0.48) * (de / 0.48);
        }
        float ds = dist(user[0][0], user[1][0], ix[0], iy[0]);
        c += W_START * (ds / 0.48) * (ds / 0.48);
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
