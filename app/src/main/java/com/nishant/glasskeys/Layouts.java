package com.nishant.glasskeys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds rows of keys for the letter and symbol pages. */
public class Layouts {
    public static final int ALPHA = 0, SYM1 = 1, SYM2 = 2;

    private static final Map<String, String> ALTS = new HashMap<>();
    static {
        ALTS.put("a", "à á â ä æ ã å ā @");
        ALTS.put("c", "ç ć č ©");
        ALTS.put("e", "è é ê ë ē ę € 3");
        ALTS.put("i", "ì í î ï ī 8");
        ALTS.put("n", "ñ ń");
        ALTS.put("o", "ò ó ô ö õ ø œ ō 9");
        ALTS.put("s", "ß ś š $ §");
        ALTS.put("u", "ù ú û ü ū 7");
        ALTS.put("y", "ý ÿ 6");
        ALTS.put("z", "ž ź ż");
        ALTS.put("q", "1");
        ALTS.put("w", "2");
        ALTS.put("r", "4 ®");
        ALTS.put("t", "5 ™");
        ALTS.put("p", "0 ¶ π");
        ALTS.put("l", "ł");
        ALTS.put("d", "ð");
        ALTS.put("g", "ğ");
        ALTS.put(".", ", ? ! : ; ' \" - … @ #");
        ALTS.put(",", "! ? ; : ' \"");
        ALTS.put("1", "¹ ½ ⅓ ¼ ⅛");
        ALTS.put("2", "² ⅔");
        ALTS.put("3", "³ ¾");
        ALTS.put("4", "⁴");
        ALTS.put("0", "ⁿ ∅ °");
        ALTS.put("₹", "$ € £ ¥ ¢ ₩");
        ALTS.put("-", "_ – — ·");
        ALTS.put("(", "[ { <");
        ALTS.put(")", "] } >");
        ALTS.put("?", "¿ ‽");
        ALTS.put("!", "¡");
        ALTS.put("\"", "“ ” « »");
        ALTS.put("'", "‘ ’ ‚ ‹ ›");
        ALTS.put("*", "★ † ‡");
        ALTS.put("%", "‰ ℅");
        ALTS.put("=", "≠ ≈ ∞");
        ALTS.put("+", "±");
        ALTS.put("/", "÷ \\");
    }

    private static String[] alts(String label) {
        String a = ALTS.get(label);
        return a == null ? null : a.split(" ");
    }

    private static Key ch(String s) { return new Key(s, s.codePointAt(0), 1f, null, alts(s)); }

    private static List<Key> chars(String row) {
        List<Key> l = new ArrayList<>();
        for (String s : row.split(" ")) l.add(ch(s));
        return l;
    }

    public static List<List<Key>> build(int page, boolean numberRow) {
        List<List<Key>> rows = new ArrayList<>();
        if (page == ALPHA) {
            if (numberRow) rows.add(chars("1 2 3 4 5 6 7 8 9 0"));
            List<Key> r1 = chars("q w e r t y u i o p");
            if (!numberRow) {
                String[] nums = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
                for (int i = 0; i < r1.size(); i++) {
                    Key k = r1.get(i);
                    r1.set(i, new Key(k.label, k.code, 1f, nums[i], k.popups));
                }
            }
            rows.add(r1);
            List<Key> r2 = new ArrayList<>();
            r2.add(new Key("", 0, 0.5f, null, null)); // spacer for Gboard-style stagger
            r2.addAll(chars("a s d f g h j k l"));
            r2.add(new Key("", 0, 0.5f, null, null));
            rows.add(r2);
            List<Key> r3 = new ArrayList<>();
            r3.add(new Key("", Key.SHIFT, 1.5f, null, null));
            r3.addAll(chars("z x c v b n m"));
            r3.add(new Key("", Key.DELETE, 1.5f, null, null));
            rows.add(r3);
            rows.add(bottomRow("?123", Key.SYMBOLS, ",", "."));
        } else if (page == SYM1) {
            rows.add(chars("1 2 3 4 5 6 7 8 9 0"));
            rows.add(chars("@ # ₹ _ & - + ( ) /"));
            List<Key> r = new ArrayList<>();
            r.add(new Key("=\\<", Key.SYMBOLS2, 1.5f, null, null));
            r.addAll(chars("* \" ' : ; ! ?"));
            r.add(new Key("", Key.DELETE, 1.5f, null, null));
            rows.add(r);
            rows.add(bottomRow("ABC", Key.ALPHA, ",", "."));
        } else {
            rows.add(chars("~ ` | • √ π ÷ × ¶ ∆"));
            rows.add(chars("£ $ € ¥ ^ ° = { } \\"));
            List<Key> r = new ArrayList<>();
            r.add(new Key("?123", Key.SYMBOLS, 1.5f, null, null));
            r.addAll(chars("% © ® ™ ✓ [ ]"));
            r.add(new Key("", Key.DELETE, 1.5f, null, null));
            rows.add(r);
            rows.add(bottomRow("ABC", Key.ALPHA, "<", ">"));
        }
        return rows;
    }

    private static List<Key> bottomRow(String modeLabel, int modeCode, String left, String right) {
        List<Key> r = new ArrayList<>();
        r.add(new Key(modeLabel, modeCode, 1.5f, null, null));
        r.add(ch(left));
        r.add(new Key("", Key.EMOJI, 1f, null, null));
        r.add(new Key("", Key.SPACE, 4.5f, null, null));
        r.add(ch(right));
        r.add(new Key("", Key.ENTER, 1.5f, null, null));
        return r;
    }
}
