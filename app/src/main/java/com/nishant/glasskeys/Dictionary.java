package com.nishant.glasskeys;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Word suggestions, autocorrect and next-word prediction. Everything happens on the phone:
 * a ~30k word frequency list plus the words and word pairs you type most.
 */
public class Dictionary {
    private String[] words = new String[0];   // sorted
    private int[] freq = new int[0];
    private volatile Map<String, Integer> index = new HashMap<>();
    private Map<String, Integer> learned = new HashMap<>();
    private Map<String, Integer> bigrams = new HashMap<>();
    private Map<String, Integer> trigrams = new HashMap<>();
    private Map<String, String> caseForms = new HashMap<>();
    private int learnsSinceDecay;
    /** Marks the start of a sentence in word-pair memory. */
    public static final String START = "^";
    private final Prefs prefs;  // null in tests
    private volatile boolean loaded;
    private int dirty;

    private static final Map<String, String> FIXES = new HashMap<>();
    static {
        String[] f = {"i", "I", "im", "I'm", "ive", "I've", "dont", "don't",
                "cant", "can't", "wont", "won't", "didnt", "didn't", "doesnt", "doesn't", "isnt", "isn't",
                "wasnt", "wasn't", "havent", "haven't", "couldnt", "couldn't", "shouldnt", "shouldn't",
                "wouldnt", "wouldn't", "thats", "that's", "whats", "what's", "lets", "let's", "youre", "you're",
                "theyre", "they're", "arent", "aren't", "teh", "the", "adn", "and", "recieve", "receive",
                "definately", "definitely", "seperate", "separate", "tommorow", "tomorrow", "untill", "until",
                "wich", "which", "becuase", "because", "thier", "their", "alot", "a lot"};
        for (int i = 0; i < f.length; i += 2) FIXES.put(f[i], f[i + 1]);
    }

    public Dictionary(Context ctx, Prefs prefs) {
        this.prefs = prefs;
        learned = prefs.learned();
        bigrams = prefs.bigrams();
        trigrams = prefs.trigrams();
        caseForms = prefs.caseForms();
        final Context app = ctx.getApplicationContext();
        new Thread(() -> load(app)).start();
    }

    private void load(Context ctx) {
        Map<String, Integer> m = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(ctx.getAssets().open("words.txt"), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                int t = line.indexOf('\t');
                if (t <= 0) continue;
                String w = line.substring(0, t);
                int f = Integer.parseInt(line.substring(t + 1).trim());
                Integer old = m.get(w);
                if (old == null || old < f) m.put(w, f);
            }
        } catch (Exception ignored) { }
        String[] w = m.keySet().toArray(new String[0]);
        Arrays.sort(w);
        int[] fr = new int[w.length];
        for (int i = 0; i < w.length; i++) fr[i] = m.get(w[i]);
        index = m;
        words = w;
        freq = fr;
        loaded = true;
    }

    // ---- accessors for the swipe recogniser
    private int[][] buckets;

    /** Test / offline constructor. */
    Dictionary(String[] sortedWords, int[] freqs) {
        this.prefs = null;
        words = sortedWords;
        freq = freqs;
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < sortedWords.length; i++) m.put(sortedWords[i], freqs[i]);
        index = m;
        loaded = true;
    }

    public boolean isLoaded() { return loaded; }
    public String[] words() { return words; }
    public int[] freqs() { return freq; }
    public Map<String, Integer> learnedMap() { return learned; }
    public boolean isDictWord(String w) { return index.containsKey(w); }

    /** [start, end) range of words beginning with c (words are sorted). */
    public synchronized int[] bucket(char c) {
        if (!loaded) return null;
        if (buckets == null) {
            buckets = new int[26][];
            String[] w = words;
            int i = 0;
            for (int b = 0; b < 26; b++) {
                char ch = (char) ('a' + b);
                while (i < w.length && (w[i].isEmpty() || w[i].charAt(0) < ch)) i++;
                int start = i;
                while (i < w.length && !w[i].isEmpty() && w[i].charAt(0) == ch) i++;
                buckets[b] = new int[]{start, i};
            }
        }
        int b = c - 'a';
        return b >= 0 && b < 26 ? buckets[b] : null;
    }

    public double bigramScore(String prev, String w) {
        if (prev == null) return 0;
        Integer c = bigrams.get(prev.toLowerCase() + " " + w);
        return c == null ? 0 : Math.min(3.0, Math.log(1 + c));
    }

    public boolean isWord(String w) {
        String l = w.toLowerCase();
        return index.containsKey(l) || learnedCount(l) >= 2;
    }

    private int learnedCount(String w) {
        Integer c = learned.get(w);
        return c == null ? 0 : c;
    }

    /** Dictionary frequency blended with how often YOU use the word. */
    private int score(String w) {
        Integer f = index.get(w);
        int base = f == null ? 0 : f;
        int lc = learnedCount(w);
        int personal = lc > 0 ? (int) (140 * Math.log(1 + lc) + 6 * Math.min(lc, 120)) : 0;
        if (f != null) base += personal;
        else if (lc >= 2) base = 380 + (int) (personal * 1.3);
        return base;
    }

    /** Your preferred spelling/capitalisation of a word, if you have one. */
    public String form(String w) {
        String f = caseForms.get(w.toLowerCase());
        return f != null ? f : w;
    }

    private static class Cand implements Comparable<Cand> {
        String w; int s;
        Cand(String w, int s) { this.w = w; this.s = s; }
        public int compareTo(Cand o) { return Integer.compare(o.s, s); }
    }

    /** Up to 3 suggestions for the word being typed. Index 0 is the "best" (autocorrect) candidate. */
    public List<String> suggest(String typed, String prevWord) {
        List<String> out = new ArrayList<>();
        if (typed.isEmpty()) return out;
        String low = typed.toLowerCase();
        List<Cand> cands = new ArrayList<>();
        Map<String, Boolean> seen = new HashMap<>();

        String fix = FIXES.get(low);
        if (fix != null) { cands.add(new Cand(fix, 100000)); seen.put(fix.toLowerCase(), true); }

        if (loaded) {
            // Prefix completions
            int i = Arrays.binarySearch(words, low);
            if (i < 0) i = -i - 1;
            for (int n = 0; i < words.length && n < 4000 && words[i].startsWith(low); i++, n++) {
                String w = words[i];
                if (seen.containsKey(w)) continue;
                int s = score(w) - (w.length() - low.length()) * 12 + bigramBoost(prevWord, w);
                if (w.equals(low)) s += 400;
                else if (learnedCount(w) >= 2) s += 220;   // you've typed this before: likely again
                cands.add(new Cand(w, s));
                seen.put(w, true);
            }
            // Typo corrections (one edit away), only worth it for 2+ letters
            if (low.length() >= 2) {
                for (int k = 0; k < words.length; k++) {
                    String w = words[k];
                    if (Math.abs(w.length() - low.length()) > 1 || seen.containsKey(w)) continue;
                    if (w.charAt(0) != low.charAt(0) && low.length() > 3 && w.length() > 3
                            && w.charAt(1) != low.charAt(1)) continue;
                    if (editDistanceAtMost1(low, w)) {
                        // spelling fixes of very short input are guesswork; your own words should win
                        int pen = low.length() <= 2 ? 260 : low.length() == 3 ? 150 : 60;
                        cands.add(new Cand(w, score(w) - pen + bigramBoost(prevWord, w)));
                        seen.put(w, true);
                    }
                }
            }
        }
        for (Map.Entry<String, Integer> e : learned.entrySet()) {
            String w = e.getKey();
            if (e.getValue() >= 2 && w.startsWith(low) && !seen.containsKey(w)) {
                cands.add(new Cand(w, score(w) + 220 - (w.length() - low.length()) * 6));
                seen.put(w, true);
            }
        }
        Collections.sort(cands);
        for (Cand c : cands) {
            if (out.size() >= 4) break;
            String shown = matchCase(typed, c.w);
            // typed in lowercase? use your usual capitalisation (NRRL, Nishant, WhatsApp)
            if (typed.equals(typed.toLowerCase()) && caseForms.containsKey(c.w)) shown = caseForms.get(c.w);
            out.add(shown);
        }
        return out;
    }

    private int bigramBoost(String prev, String w) {
        if (prev == null) return 0;
        Integer c = bigrams.get(prev.toLowerCase() + " " + w);
        return c == null ? 0 : Math.min(c, 20) * 25;
    }

    /** Autocorrect: returns the replacement for a finished word, or null to leave it alone. */
    public String autocorrect(String typed, String prev) {
        String low = typed.toLowerCase();
        String fix = FIXES.get(low);
        if (fix != null) return fix.equals(typed) ? null : matchCaseFix(typed, fix);
        if (!loaded || typed.length() < 2 || isWord(typed)) return null;
        for (int i = 0; i < typed.length(); i++) if (!Character.isLetter(typed.charAt(i)) && typed.charAt(i) != '\'') return null;
        List<String> s = suggest(typed, prev);
        for (String c : s) {
            String cl = c.toLowerCase();
            if (cl.length() >= low.length() - 1 && cl.length() <= low.length() + 1
                    && editDistanceAtMost1(low, cl) && score(cl) > 300) return c;
        }
        return null;
    }

    public List<String> predict(String prev) { return predict(null, prev); }

    /**
     * Next-word predictions from YOUR writing: three-word phrases first ("thank you" -> "for"),
     * then word pairs, and at the start of a sentence, how you usually begin ("Hi", "Namaste").
     */
    public List<String> predict(String prev2, String prev) {
        List<String> out = new ArrayList<>();
        String p1 = (prev == null || prev.isEmpty()) ? START : prev.toLowerCase();
        Map<String, Integer> score = new HashMap<>();
        if (prev2 != null && prev != null) {
            String p = prev2.toLowerCase() + " " + p1 + " ";
            for (Map.Entry<String, Integer> e : trigrams.entrySet())
                if (e.getKey().startsWith(p) && e.getValue() >= 2) score.merge(e.getKey().substring(p.length()), e.getValue() * 4, Integer::sum);
        }
        String p = p1 + " ";
        for (Map.Entry<String, Integer> e : bigrams.entrySet())
            if (e.getKey().startsWith(p) && (e.getValue() >= 2 || !p1.equals(START)))
                score.merge(e.getKey().substring(p.length()), e.getValue(), Integer::sum);
        List<Cand> c = new ArrayList<>();
        for (Map.Entry<String, Integer> e : score.entrySet()) c.add(new Cand(e.getKey(), e.getValue()));
        Collections.sort(c);
        for (Cand x : c) {
            if (out.size() >= 4) break;
            String w = x.w.equals("i") ? "I" : form(x.w);
            if (p1.equals(START) && w.length() > 0) w = Character.toUpperCase(w.charAt(0)) + w.substring(1);
            out.add(w);
        }
        return out;
    }

    public void learn(String word, String prev) { learn(word, prev, null); }

    /** Learns from a finished word (skipped in incognito / password fields). */
    public void learn(String word, String prev, String prev2) {
        if (word == null || word.length() < 1 || word.length() > 30) return;
        String w = word.toLowerCase();
        for (int i = 0; i < w.length(); i++) if (!Character.isLetter(w.charAt(i)) && w.charAt(i) != '\'') return;
        learned.put(w, learnedCount(w) + 1);
        // remember deliberate capitalisation of names/brands (not just sentence-start capitals)
        if (!word.equals(w) && (prev != null || word.length() > 1 && word.equals(word.toUpperCase()))) caseForms.put(w, word);
        String p1 = (prev == null || prev.isEmpty()) ? START : prev.toLowerCase();
        bigrams.merge(p1 + " " + w, 1, Integer::sum);
        if (prev2 != null && prev != null) trigrams.merge(prev2.toLowerCase() + " " + p1 + " " + w, 1, Integer::sum);
        if (bigrams.size() > 8000) bigrams = trim(bigrams, 6000);
        if (trigrams.size() > 8000) trigrams = trim(trigrams, 6000);
        if (learned.size() > 9000) learned = trim(learned, 7000);
        // slow forgetting: habits you've dropped fade out over time
        if (++learnsSinceDecay >= 600) { learnsSinceDecay = 0; decay(); }
        if (++dirty >= 8) flush();
    }

    private static Map<String, Integer> trim(Map<String, Integer> m, int keepN) {
        List<Map.Entry<String, Integer>> l = new ArrayList<>(m.entrySet());
        l.sort((a, b) -> b.getValue() - a.getValue());
        Map<String, Integer> keep = new HashMap<>();
        for (int i = 0; i < Math.min(keepN, l.size()); i++) keep.put(l.get(i).getKey(), l.get(i).getValue());
        return keep;
    }

    private void decay() {
        for (Map<String, Integer> m : java.util.Arrays.asList(learned, bigrams, trigrams)) {
            java.util.Iterator<Map.Entry<String, Integer>> it = m.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Integer> e = it.next();
                int v = (int) Math.floor(e.getValue() * 0.9);
                if (v <= 0) it.remove(); else e.setValue(v);
            }
        }
    }

    /** Removes a word everywhere it was learned. */
    public void forget(String word) {
        String w = word.toLowerCase();
        learned.remove(w);
        caseForms.remove(w);
        bigrams.keySet().removeIf(k -> k.endsWith(" " + w) || k.startsWith(w + " "));
        trigrams.keySet().removeIf(k -> (" " + k + " ").contains(" " + w + " "));
        flush();
    }

    private void trimBigrams() {
        List<Map.Entry<String, Integer>> l = new ArrayList<>(bigrams.entrySet());
        l.sort((a, b) -> b.getValue() - a.getValue());
        Map<String, Integer> keep = new HashMap<>();
        for (int i = 0; i < Math.min(4000, l.size()); i++) keep.put(l.get(i).getKey(), l.get(i).getValue());
        bigrams = keep;
    }

    public void flush() {
        dirty = 0;
        if (prefs == null) return;
        prefs.saveLearned(learned);
        prefs.saveBigrams(bigrams);
        prefs.saveTrigrams(trigrams);
        prefs.saveCaseForms(caseForms);
    }

    public void reloadLearned() {
        learned = prefs.learned();
        bigrams = prefs.bigrams();
        trigrams = prefs.trigrams();
        caseForms = prefs.caseForms();
    }

    private static String matchCase(String typed, String w) {
        if (w.equals("i")) return "I";
        if (typed.length() > 1 && typed.equals(typed.toUpperCase()) && !typed.equals(typed.toLowerCase()))
            return w.toUpperCase();
        if (Character.isUpperCase(typed.charAt(0)) && w.length() > 0)
            return Character.toUpperCase(w.charAt(0)) + w.substring(1);
        return w;
    }

    private static String matchCaseFix(String typed, String fix) {
        if (Character.isUpperCase(typed.charAt(0)) && !Character.isUpperCase(fix.charAt(0)))
            return Character.toUpperCase(fix.charAt(0)) + fix.substring(1);
        return fix;
    }

    static boolean editDistanceAtMost1(String a, String b) {
        int la = a.length(), lb = b.length();
        if (Math.abs(la - lb) > 1) return false;
        if (a.equals(b)) return false;
        int i = 0, j = 0, edits = 0;
        while (i < la && j < lb) {
            if (a.charAt(i) == b.charAt(j)) { i++; j++; continue; }
            if (++edits > 1) return false;
            if (la > lb) i++;
            else if (lb > la) j++;
            else {
                // allow a swap of two neighbouring letters ("teh" -> "the")
                if (i + 1 < la && a.charAt(i) == b.charAt(i + 1) && a.charAt(i + 1) == b.charAt(i)) { i += 2; j += 2; continue; }
                i++; j++;
            }
        }
        edits += (la - i) + (lb - j);
        return edits <= 1;
    }
}
