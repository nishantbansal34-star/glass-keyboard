package com.nishant.glasskeys;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Whole-phrase suggestions: when what you've typed so far in a sentence matches the start of a phrase
 * you use (your snippets, your own habits, or common shop / chat phrases), offer the rest in one tap.
 */
final class Phrases {
    private static final class P {
        final String text; final String[] toks; final int[] ends; final int weight;
        P(String text, int weight) {
            this.text = text; this.weight = weight;
            List<String> t = new ArrayList<>(); List<Integer> e = new ArrayList<>();
            java.util.regex.Matcher m = WORD.matcher(text.toLowerCase(Locale.ROOT));
            while (m.find()) { t.add(m.group()); e.add(m.end()); }
            toks = t.toArray(new String[0]);
            ends = new int[e.size()];
            for (int i = 0; i < ends.length; i++) ends[i] = e.get(i);
        }
    }

    private static final java.util.regex.Pattern WORD = java.util.regex.Pattern.compile("[a-z0-9]+(?:'[a-z]+)?");
    private final List<P> builtin = new ArrayList<>();

    void load(InputStream in) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) { line = line.trim(); if (line.length() > 3) builtin.add(new P(line, 1)); }
        } catch (Exception ignored) { }
    }

    static List<String> words(String s) {
        List<String> out = new ArrayList<>();
        java.util.regex.Matcher m = WORD.matcher(s.toLowerCase(Locale.ROOT));
        while (m.find()) out.add(m.group());
        return out;
    }

    /**
     * The rest of a phrase that continues the sentence typed so far (needs at least 2 matching words
     * and at least 2 words still to come), or null.
     * @param sentence words of the current sentence, lowercase
     * @param snippets your quick texts (highest priority)
     */
    String continueFrom(List<String> sentence, List<String> snippets) {
        if (sentence.size() < 2) return null;
        String best = null;
        int bestScore = 0;
        List<P> pool = new ArrayList<>();
        if (snippets != null) for (String s : snippets) if (s != null && !s.isEmpty()) pool.add(new P(s, 3));
        pool.addAll(builtin);
        for (P p : pool) {
            // longest suffix of what you typed that appears in the phrase
            for (int k = Math.min(5, sentence.size()); k >= 2; k--) {
                int at = find(p.toks, sentence, sentence.size() - k, k);
                if (at < 0) continue;
                int restFrom = at + k;
                if (p.toks.length - restFrom < 2) break;
                boolean wholeSentence = at == 0 && k == sentence.size();
                int score = k * 10 + (wholeSentence ? 8 : 0) + p.weight * 6 - Math.min(6, p.toks.length - restFrom);
                if (score > bestScore) {
                    bestScore = score;
                    best = p.text.substring(p.ends[restFrom - 1]).trim();
                }
                break;
            }
        }
        if (best == null) return null;
        // keep it a phrase, not an essay: the shown chip is ellipsised, the full text is inserted
        return best.replaceAll("^[,;:\\-]+\\s*", "");
    }

    private static int find(String[] toks, List<String> s, int from, int k) {
        outer:
        for (int i = 0; i + k <= toks.length; i++) {
            for (int j = 0; j < k; j++) if (!toks[i + j].equals(s.get(from + j))) continue outer;
            return i;
        }
        return -1;
    }
}
