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
    private java.util.Set<String> noCorrect = new java.util.HashSet<>();
    /** Names from your contacts (lowercase -> how it's written), only when you turn that on. */
    private volatile Map<String, String> contacts = new HashMap<>();

    public void setContactNames(Map<String, String> m) { contacts = m == null ? new HashMap<>() : m; }
    private int learnsSinceDecay;
    /** Marks the start of a sentence in word-pair memory. */
    public static final String START = "^";
    private final Prefs prefs;  // null in tests
    private volatile boolean loaded;
    private int dirty;

    /** Chat words people type on purpose: never "corrected". */
    private static final java.util.Set<String> SLANG = new java.util.HashSet<>(java.util.Arrays.asList(
            "ok", "okk", "okkk", "okie", "okey", "k", "kk", "hmm", "hmmm", "hm", "plz", "pls", "plzz", "thx", "thnx", "ty", "tq",
            "gm", "gn", "bro", "sis", "ya", "yaa", "yup", "yep", "nope", "lol", "lmao", "btw", "idk", "omg", "brb", "ttyl",
            "haan", "han", "ji", "na", "nahi", "nhi", "acha", "accha", "achha", "theek", "thik", "bhai", "yaar", "yr", "sahi",
            "hai", "hain", "ho", "kya", "kyu", "kyun", "kab", "kaha", "kahan", "abhi", "kal", "aaj", "toh", "bhi", "mein",
            "main", "hum", "tum", "aap", "ka", "ki", "ke", "ko", "se", "pe", "par", "wala", "wali", "sir", "ma'am", "mam",
            "msg", "pic", "pics", "vid", "asap", "fyi", "tmrw", "tmr", "gud", "nyc", "wid", "u", "ur", "r", "n", "y"));

    private static final Map<String, String> FIXES = new HashMap<>();
    static {
        String[] f = {"i", "I", "im", "I'm", "ive", "I've", "dont", "don't",
                "cant", "can't", "wont", "won't", "didnt", "didn't", "doesnt", "doesn't", "isnt", "isn't",
                "wasnt", "wasn't", "havent", "haven't", "couldnt", "couldn't", "shouldnt", "shouldn't",
                "wouldnt", "wouldn't", "thats", "that's", "whats", "what's", "lets", "let's", "youre", "you're",
                "theyre", "they're", "arent", "aren't", "teh", "the", "adn", "and", "recieve", "receive",
                "definately", "definitely", "seperate", "separate", "tommorow", "tomorrow", "untill", "until",
                "wich", "which", "becuase", "because", "thier", "their", "alot", "a lot", "helo", "hello",
                "occured", "occurred", "goverment", "government", "acheive", "achieve", "beleive", "believe",
                "freind", "friend", "wierd", "weird", "truely", "truly", "untill", "until", "begining", "beginning"};
        for (int i = 0; i < f.length; i += 2) FIXES.put(f[i], f[i + 1]);
    }

    public Dictionary(Context ctx, Prefs prefs) {
        this.prefs = prefs;
        learned = prefs.learned();
        bigrams = prefs.bigrams();
        trigrams = prefs.trigrams();
        caseForms = prefs.caseForms();
        noCorrect = prefs.noCorrect();
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
        byLen = bucketByLength(w);
        try { lm.load(ctx.getAssets().open(BuildConfig.PLAY ? "ngrams_safe.txt" : "ngrams.txt")); } catch (Exception ignored) { }
        loaded = true;
    }

    /** Words grouped by length, so the one-typo search only looks at words of a similar length. */
    private String[][] byLen = new String[0][];

    private static String[][] bucketByLength(String[] w) {
        int max = 0;
        for (String s : w) max = Math.max(max, s.length());
        int[] n = new int[max + 2];
        for (String s : w) n[s.length()]++;
        String[][] b = new String[max + 2][];
        for (int i = 0; i < b.length; i++) b[i] = new String[n[i]];
        int[] k = new int[max + 2];
        for (String s : w) b[s.length()][k[s.length()]++] = s;
        return b;
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
        byLen = bucketByLength(sortedWords);
        loaded = true;
    }

    /** Test hook: load the built-in context model from a file. */
    void loadModel(java.io.InputStream in) { lm.load(in); }

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
        String p = prev == null || prev.isEmpty() ? START : prev.toLowerCase();
        Integer c = bigrams.get(p + " " + w);
        double mine = c == null ? 0 : Math.log(1 + c);
        double base = Math.log(1 + lm.prob(null, p, w) * 200);   // built-in conversation model
        return Math.min(3.0, Math.max(mine, base));
    }

    public boolean isWord(String w) {
        String l = w.toLowerCase();
        return index.containsKey(l) || learnedCount(l) >= 2 || contacts.containsKey(l);
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
        String w; int s; float typo;
        Cand(String w, int s) { this.w = w; this.s = s; }
        public int compareTo(Cand o) { return Integer.compare(o.s, s); }
    }

    // ================================================================== ranking
    // Scores are in "zipf × 100" units (log10 frequency per billion words × 100): the word list's own
    // scale, so "the" ≈ 770 and a rare word ≈ 250. Context and typos move a word up or down that scale.

    final LangModel lm = new LangModel();

    /** log-probability score of w given the previous words: built-in context model + your habits. */
    private double contextScore(String prev2, String prev, String w) {
        Integer f = index.get(w);
        double zipf = f != null ? f / 100.0 : 0;
        int lc = learnedCount(w);
        if (f == null && lc >= 2) zipf = 3.4 + Math.log10(1 + lc) * 0.8;   // your own words
        if (zipf <= 0) return -1e9;
        double pu = Math.pow(10, zipf - 9);
        String p1 = prev == null || prev.isEmpty() ? START : prev.toLowerCase();
        String p2 = prev2 == null ? (p1.equals(START) ? null : START) : prev2.toLowerCase();
        double p = pu;
        if (lm.hasContext(p2, p1)) p = 0.6 * lm.prob(p2, p1, w) + 0.4 * pu;
        double s = 100 * (Math.log10(p) + 9);
        // what YOU write
        if (lc > 0) s += 45 * Math.log(1 + lc) + 2.5 * Math.min(lc, 80);
        Integer pb = bigrams.get(p1 + " " + w);
        if (pb != null) s += 70 * Math.log(1 + pb);
        if (p2 != null) {
            Integer pt = trigrams.get(p2 + " " + p1 + " " + w);
            if (pt != null) s += 90 * Math.log(1 + pt);
        }
        return s;
    }

    /** Prior for swipe typing, in zipf units: how likely w is here (built-in model + your habits). */
    public double swipePrior(String prev2, String prev, String w) { return contextScore(prev2, prev, w) / 100.0; }

    private static float maxTypo(int len) { return len <= 2 ? 0.6f : len <= 4 ? 1.1f : len <= 7 ? 2.0f : 2.6f; }

    /** Penalty for each unit of typing-error cost. */
    private static final double TYPO = 165;

    /** Candidates for what you meant by `typed`, best first. */
    private List<Cand> rank(String typed, String prev, String prev2, boolean forAutocorrect) {
        String low = typed.toLowerCase();
        Map<String, Cand> best = new HashMap<>();
        if (!loaded) return new ArrayList<>();
        float maxT = maxTypo(low.length());
        char c0 = low.charAt(0);

        // 1. exact word and completions (incl. completions with a typo in the part typed so far)
        int i = Arrays.binarySearch(words, low);
        if (i < 0) i = -i - 1;
        // cheap pass first (frequency only), full context scoring for the strongest ~60 + context words
        int start = i;
        List<Cand> pre = new ArrayList<>();
        for (int n = 0; i < words.length && n < 6000 && words[i].startsWith(low); i++, n++)
            pre.add(new Cand(words[i], freq[i] - (words[i].length() - low.length()) * 9));
        Collections.sort(pre);
        java.util.Set<String> want = new java.util.HashSet<>();
        for (int k = 0; k < Math.min(60, pre.size()); k++) want.add(pre.get(k).w);
        String cp1 = prev == null || prev.isEmpty() ? START : prev.toLowerCase();
        LangModel.Next nb = lm.bi.get(cp1);
        if (nb != null) for (String w : nb.w) if (w.startsWith(low)) want.add(w);
        String cp2 = prev2 == null ? (cp1.equals(START) ? null : START) : prev2.toLowerCase();
        if (cp2 != null) { LangModel.Next nt = lm.tri.get(cp2 + " " + cp1); if (nt != null) for (String w : nt.w) if (w.startsWith(low)) want.add(w); }
        String bp = cp1 + " " + low;
        for (String k : bigrams.keySet()) if (k.startsWith(bp)) want.add(k.substring(cp1.length() + 1));
        for (String w : want) {
            if (!index.containsKey(w)) continue;
            double sc = contextScore(prev2, prev, w) - (w.length() - low.length()) * 9;
            if (w.equals(low)) sc += 60;
            put(best, w, sc, 0f);
        }
        int exactCompletions = 0;
        for (Cand cc : best.values()) if (cc.s > 250) exactCompletions++;
        if (!forAutocorrect && low.length() >= 4 && exactCompletions < 2) {
            int[] bk = bucket(c0);
            if (bk != null) for (int k = bk[0]; k < bk[1]; k++) {
                String w = words[k];
                if (w.length() <= low.length() + 1 || w.startsWith(low)) continue;
                if (w.length() > 1 && low.length() > 1) {
                    char w1 = w.charAt(1), t1 = low.charAt(1);
                    if (w1 != t1 && w1 != low.charAt(2 < low.length() ? 2 : 1) && !LangModel.near(w1, t1)) continue;
                }
                float pc = LangModel.prefixCost(low, w, 1.0f);
                if (pc > 1.0f) continue;
                double s = contextScore(prev2, prev, w) - pc * TYPO - (w.length() - low.length()) * 9 - 25;
                put(best, w, s, pc);
            }
        }
        // 2. what you might have meant: neighbouring-key slips, swaps, missed/extra letters
        if (low.length() >= 2) {
            int extra = low.length() >= 8 ? 2 : 1;
            String[][] bl = byLen;
            int[] bag = new int[27];
            for (int q = 0; q < low.length(); q++) { char ch = low.charAt(q); bag[ch >= 'a' && ch <= 'z' ? ch - 'a' : 26]++; }
            int allowMiss = (int) Math.floor(maxT / 0.55f);
            int[] tmpBag = new int[27];
            for (int L = Math.max(1, low.length() - extra); L <= low.length() + extra && L < bl.length; L++)
                for (String w : bl[L]) {
                    char w0 = w.charAt(0);
                    if (w0 != c0 && !LangModel.near(w0, c0) && !(low.length() > 1 && w0 == low.charAt(1))) continue;
                    // second letter: same, a neighbour, or one of the letters around it (swap / missed letter)
                    if (low.length() > 2 && w.length() > 1) {
                        char w1 = w.charAt(1), t1 = low.charAt(1);
                        if (w1 != t1 && w1 != c0 && w1 != low.charAt(2) && !LangModel.near(w1, t1)) continue;
                    }
                    // quick reject: too many typed letters that the word doesn't even contain
                    System.arraycopy(bag, 0, tmpBag, 0, 27);
                    int miss = 0;
                    for (int q = 0; q < w.length(); q++) { char ch = w.charAt(q); int ix = ch >= 'a' && ch <= 'z' ? ch - 'a' : 26; if (tmpBag[ix] > 0) tmpBag[ix]--; }
                    for (int q = 0; q < 27; q++) miss += tmpBag[q];
                    if (miss > allowMiss) continue;
                    float tc = LangModel.cost(low, w, maxT);
                    if (tc > maxT || tc == 0f) continue;
                    double s = contextScore(prev2, prev, w) - tc * TYPO;
                    if (low.length() <= 3) s -= 60;    // very short input: corrections are guesswork
                    put(best, w, s, tc);
                }
        }
        // 3. your own words (names, brands, Hinglish) that aren't in the word list
        for (Map.Entry<String, Integer> e : learned.entrySet()) {
            String w = e.getKey();
            if (e.getValue() < 2 || index.containsKey(w)) continue;
            if (w.startsWith(low)) put(best, w, contextScore(prev2, prev, w) - (w.length() - low.length()) * 6 + 40, 0f);
            else if (low.length() >= 3) {
                float tc = LangModel.cost(low, w, maxT);
                if (tc <= maxT) put(best, w, contextScore(prev2, prev, w) - tc * TYPO + 40, tc);
            }
        }
        // 4. names from your contacts (if enabled)
        if (low.length() >= 2) for (Map.Entry<String, String> e : contacts.entrySet()) {
            String w = e.getKey();
            if (w.startsWith(low)) put(best, w, 330 + learnedCount(w) * 20 - (w.length() - low.length()) * 6, 0f);
        }
        List<Cand> out = new ArrayList<>(best.values());
        Collections.sort(out);
        return out;
    }

    private static void put(Map<String, Cand> m, String w, double s, float typo) {
        Cand c = m.get(w);
        if (c == null || c.s < s) { c = new Cand(w, (int) Math.round(s)); c.typo = typo; m.put(w, c); }
    }

    public List<String> suggest(String typed, String prevWord) { return suggest(typed, prevWord, null); }

    /** Up to 4 suggestions for the word being typed, best first. */
    public List<String> suggest(String typed, String prevWord, String prevWord2) {
        List<String> out = new ArrayList<>();
        if (typed.isEmpty()) return out;
        String low = typed.toLowerCase();
        String fix = FIXES.get(low);
        if (fix != null) out.add(matchCaseFix(typed, fix));
        for (Cand c : rank(typed, prevWord, prevWord2, false)) {
            if (out.size() >= 4) break;
            String shown = matchCase(typed, c.w);
            // typed in lowercase? use your usual capitalisation (NRRL, Nishant, WhatsApp)
            if (typed.equals(typed.toLowerCase()) && caseForms.containsKey(c.w)) shown = caseForms.get(c.w);
            else if (typed.equals(typed.toLowerCase()) && contacts.containsKey(c.w) && !index.containsKey(c.w)) shown = contacts.get(c.w);
            boolean dup = false;
            for (String o : out) if (o.equalsIgnoreCase(shown)) dup = true;
            if (!dup) out.add(shown);
        }
        return out;
    }

    private int bigramBoost(String prev, String w) {
        if (prev == null) return 0;
        Integer c = bigrams.get(prev.toLowerCase() + " " + w);
        return c == null ? 0 : Math.min(c, 20) * 25;
    }

    public String autocorrect(String typed, String prev) { return autocorrect(typed, prev, null); }

    /**
     * Autocorrect: returns the replacement for a finished word, or null to leave it alone.
     * Only fixes when the intended word is clearly more likely than anything else.
     */
    public String autocorrect(String typed, String prev, String prev2) {
        String low = typed.toLowerCase();
        String fix = FIXES.get(low);
        if (fix != null) return fix.equals(typed) ? null : matchCaseFix(typed, fix);
        if (!loaded || typed.length() < 2 || isWord(typed)) return null;
        for (int i = 0; i < typed.length(); i++) if (!Character.isLetter(typed.charAt(i)) && typed.charAt(i) != '\'') return null;
        if (SLANG.contains(low) || noCorrect.contains(low)) return null;
        // shouting/ACRONYMS and mixed case (iPhone, GSTIN) are probably intentional
        if (typed.length() > 1 && typed.equals(typed.toUpperCase())) return null;
        List<Cand> r = rank(typed, prev, prev2, true);
        Cand top = null, second = null;
        for (Cand c : r) {
            if (c.w.length() < low.length() - 2) continue;
            if (top == null) top = c; else { second = c; break; }
        }
        if (top == null || top.typo <= 0f) return null;
        float limit = low.length() <= 3 ? 0.6f : low.length() <= 5 ? 1.1f : 2.0f;
        if (top.typo > limit) return null;
        if (top.s < 230) return null;                       // too rare to be a confident guess
        if (second != null && top.s - second.s < 45) return null;   // two plausible fixes: let you pick
        String out = matchCase(typed, top.w);
        if (typed.equals(low) && caseForms.containsKey(top.w)) out = caseForms.get(top.w);
        return out;
    }

    /** A phrase you often finish the same way ("thank you for" → "your order"): chained from your 3-word habits. */
    public String personalPhrase(String prev2, String prev) {
        if (prev == null || prev2 == null) return null;
        String a = prev2.toLowerCase(), b = prev.toLowerCase();
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (int step = 0; step < 6; step++) {
            String pre = a + " " + b + " ";
            String best = null; int bc = 0, tot = 0;
            for (Map.Entry<String, Integer> e : trigrams.entrySet())
                if (e.getKey().startsWith(pre)) { tot += e.getValue(); if (e.getValue() > bc) { bc = e.getValue(); best = e.getKey().substring(pre.length()); } }
            // only follow a path you clearly repeat
            if (best == null || bc < 3 || bc < tot * 0.6) break;
            if (sb.length() > 0) sb.append(' ');
            sb.append(best.equals("i") ? "I" : form(best));
            n++;
            a = b; b = best;
        }
        return n >= 2 ? sb.toString() : null;
    }

    public List<String> predict(String prev) { return predict(null, prev); }

    /**
     * Next-word predictions: the built-in conversation model ("thank you" -> "for", "let me" -> "know")
     * blended with YOUR phrases and word pairs; at the start of a sentence, how people (and you) begin.
     */
    public List<String> predict(String prev2, String prev) {
        List<String> out = new ArrayList<>();
        String p1 = (prev == null || prev.isEmpty()) ? START : prev.toLowerCase();
        String p2 = prev2 == null ? (p1.equals(START) ? null : START) : prev2.toLowerCase();
        Map<String, Double> score = new HashMap<>();
        // candidates from the built-in model
        LangModel.Next b = lm.bi.get(p1);
        if (b != null) for (String w : b.w) score.put(w, 0.0);
        if (p2 != null) {
            LangModel.Next t = lm.tri.get(p2 + " " + p1);
            if (t != null) for (String w : t.w) score.put(w, 0.0);
        }
        // ... and from what you write
        String pre = p1 + " ";
        for (Map.Entry<String, Integer> e : bigrams.entrySet())
            if (e.getKey().startsWith(pre) && (e.getValue() >= 2 || !p1.equals(START))) score.put(e.getKey().substring(pre.length()), 0.0);
        if (p2 != null) {
            String pt = p2 + " " + p1 + " ";
            for (Map.Entry<String, Integer> e : trigrams.entrySet())
                if (e.getKey().startsWith(pt)) score.put(e.getKey().substring(pt.length()), 0.0);
        }
        List<Cand> c = new ArrayList<>();
        for (String w : score.keySet()) {
            if (w.isEmpty() || w.equals(p1)) continue;
            double pb = lm.prob(p2, p1, w);
            // "?" / "!" only when the sentence clearly ends here ("how are you" → "?")
            if (w.equals("?") || w.equals("!")) {
                if (p1.equals(START) || p2 == null || p2.equals(START) || pb < 0.12) continue;
            }
            double s = pb > 0 ? 100 * (Math.log10(pb) + 9) : 100 * (Math.log10(1e-5) + 9);
            int lc = learnedCount(w);
            Integer ub = bigrams.get(p1 + " " + w);
            if (ub != null) s += 110 * Math.log(1 + ub);
            if (p2 != null) { Integer ut = trigrams.get(p2 + " " + p1 + " " + w); if (ut != null) s += 140 * Math.log(1 + ut); }
            if (lc > 0) s += 10 * Math.log(1 + lc);
            c.add(new Cand(w, (int) Math.round(s)));
        }
        Collections.sort(c);
        for (Cand x : c) {
            if (out.size() >= 4) break;
            String w = x.w.equals("i") ? "I" : x.w.startsWith("i'") ? "I" + x.w.substring(1) : form(x.w);
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
        if (++dirty >= 40) flush();
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

    /**
     * You undid a correction (or chose your own spelling over it): never correct this word again,
     * treat it as a real word, and take back the credit given to the replacement.
     */
    public void rejectCorrection(String original, String replacement) {
        if (original == null) return;
        String o = original.toLowerCase();
        noCorrect.add(o);
        learned.put(o, Math.max(learnedCount(o), 0) + 2);
        if (!original.equals(o)) caseForms.put(o, original);
        if (replacement != null) {
            String r = replacement.toLowerCase();
            int c = learnedCount(r);
            if (c > 1) learned.put(r, c - 1); else learned.remove(r);
        }
        if (prefs != null) prefs.saveNoCorrect(new java.util.HashSet<>(noCorrect));
        flush();
    }

    public java.util.Set<String> noCorrectWords() { return noCorrect; }

    public void allowCorrection(String w) {
        noCorrect.remove(w.toLowerCase());
        if (prefs != null) prefs.saveNoCorrect(new java.util.HashSet<>(noCorrect));
    }

    public boolean wouldCorrect(String typed, String prev, String prev2) { return autocorrect(typed, prev, prev2) != null; }

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

    private static final java.util.concurrent.ExecutorService SAVER = java.util.concurrent.Executors.newSingleThreadExecutor();

    public void flush() {
        dirty = 0;
        if (prefs == null) return;
        // Settings edited the learned data meanwhile: take theirs instead of overwriting it
        if (seenEdit != -1 && prefs.learnEdit() != seenEdit) { reloadLearned(); return; }
        final Map<String, Integer> l = new HashMap<>(learned), b = new HashMap<>(bigrams), t = new HashMap<>(trigrams);
        final Map<String, String> cf = new HashMap<>(caseForms);
        final Prefs p = prefs;
        // building the JSON for thousands of words took long enough to stall typing; do it off the UI thread
        SAVER.execute(() -> {
            try { p.saveLearned(l); p.saveBigrams(b); p.saveTrigrams(t); p.saveCaseForms(cf); } catch (Exception ignored) { }
        });
    }

    private long seenEdit = -1;

    /** Reloads learned data only if Settings changed it (the in-memory copy is otherwise newer). */
    public void reloadLearned() {
        long v = prefs.learnEdit();
        if (seenEdit == -1) { seenEdit = v; return; }   // just loaded in the constructor
        if (v == seenEdit) return;
        seenEdit = v;
        learned = prefs.learned();
        bigrams = prefs.bigrams();
        trigrams = prefs.trigrams();
        caseForms = prefs.caseForms();
        noCorrect = prefs.noCorrect();
    }

    private static String matchCase(String typed, String w) {
        if (w.equals("i")) return "I";
        if (w.startsWith("i'")) w = "I" + w.substring(1);
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
