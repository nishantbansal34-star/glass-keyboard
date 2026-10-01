package com.nishant.glasskeys;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** All persisted settings and user data (clipboard, snippets, learned words). Stored only on the phone. */
public class Prefs {
    public static final String FILE = "glasskeys";
    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public SharedPreferences raw() { return sp; }

    // ---------- simple settings ----------
    public int theme() { return sp.getInt("theme", 0); }
    public void setTheme(int t) { sp.edit().putInt("theme", t).apply(); }

    public boolean bool(String key, boolean def) { return sp.getBoolean(key, def); }
    public void setBool(String key, boolean v) { sp.edit().putBoolean(key, v).apply(); }
    public int integer(String key, int def) { return sp.getInt(key, def); }
    public void setInt(String key, int v) { sp.edit().putInt(key, v).apply(); }
    public String str(String key, String def) { return sp.getString(key, def); }
    public void setStr(String key, String v) { sp.edit().putString(key, v).apply(); }

    public boolean haptics() { return bool("haptics", true); }
    public boolean sound() { return bool("sound", false); }
    public boolean autoCaps() { return bool("autocaps", true); }
    public boolean doubleSpacePeriod() { return bool("dblspace", true); }
    public boolean autoCorrect() { return bool("autocorrect", true); }
    public boolean suggestions() { return bool("suggest", true); }
    public boolean numberRow() { return bool("numrow", false); }
    public boolean keyPopup() { return bool("keypopup", true); }
    public boolean learnWords() { return bool("learn", true); }
    public boolean liveBlur() { return bool("liveblur", false); }
    public boolean glassRipple() { return bool("ripple", true); }
    /** 0 = off, 1 = left hand, 2 = right hand */
    public int oneHanded() { return integer("onehand", 0); }
    public void setOneHanded(int v) { setInt("onehand", v); }
    /** Key height in dp */
    public int keyHeightDp() { return integer("keyheight", 56); }
    /** 0 = flowing colours, 1 = built-in bloom picture, 2 = my photo */
    public int bgMode() { return integer("bgmode", 3); }   // 3 = pure black (AMOLED)
    public boolean darkGlass() { return bool("darkglass", true); }
    public int photoDim() { return integer("photodim", 55); }  // percent
    public int photoBlur() { return integer("photoblur", 1); }
    public boolean capsLabels() { return bool("capslabels2", false); }
    /** 0 = Liquid Glass (clean), 1 = Shadow Realm */
    public int pack() { return integer("pack", 0); }
    public String voiceLang() { return str("voicelang", "en-IN"); }
    public int gstRate() { return integer("gst", 18); }
    public String backdropUri() { return str("backdrop", null); }

    /** 0 = English, 1 = Hinglish → हिंदी (type in English letters), 2 = हिंदी keys */
    public int lang() { return bool("hindi", true) ? integer("lang", 0) : 0; }
    public boolean incognito() { return bool("incognito", false); }

    /** Hinglish choices you made: "roman\u0001हिंदी" -> times picked */
    public Map<String, Integer> hiPicks() { return readMap("hiPicks"); }
    public void saveHiPicks(Map<String, Integer> m) { writeMap("hiPicks", m); }

    // ---------- price list ----------
    public static class Product {
        public String name, unit;
        public double price;
        public Product(String name, double price, String unit) { this.name = name; this.price = price; this.unit = unit == null ? "" : unit; }
    }

    public List<Product> products() {
        List<Product> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString("products", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Product(o.optString("n"), o.optDouble("p", 0), o.optString("u")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    public void saveProducts(List<Product> list) {
        JSONArray a = new JSONArray();
        try {
            for (Product p : list) {
                JSONObject o = new JSONObject();
                o.put("n", p.name); o.put("p", p.price); o.put("u", p.unit);
                a.put(o);
            }
        } catch (Exception ignored) { }
        sp.edit().putString("products", a.toString()).apply();
    }

    // ---------- backup ----------
    /** Everything the keyboard knows (settings, learned words, snippets, clipboard pins, business details) as JSON. */
    public String exportAll() throws org.json.JSONException {
        JSONObject root = new JSONObject();
        root.put("app", "glasskeys");
        root.put("v", 1);
        root.put("time", System.currentTimeMillis());
        JSONObject data = new JSONObject();
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            Object v = e.getValue();
            JSONObject item = new JSONObject();
            if (v instanceof Boolean) item.put("t", "b");
            else if (v instanceof Integer) item.put("t", "i");
            else if (v instanceof Long) item.put("t", "l");
            else if (v instanceof Float) item.put("t", "f");
            else if (v instanceof String) item.put("t", "s");
            else continue;
            item.put("v", v);
            data.put(e.getKey(), item);
        }
        root.put("data", data);
        return root.toString(1);
    }

    /** Replaces everything with a backup made by exportAll(). Returns how many entries were restored. */
    public int importAll(String json) throws org.json.JSONException {
        JSONObject root = new JSONObject(json);
        if (!"glasskeys".equals(root.optString("app"))) throw new org.json.JSONException("not a Glass Keys backup");
        JSONObject data = root.getJSONObject("data");
        SharedPreferences.Editor ed = sp.edit().clear();
        JSONArray names = data.names();
        int n = 0;
        if (names != null) for (int i = 0; i < names.length(); i++) {
            String k = names.getString(i);
            JSONObject item = data.getJSONObject(k);
            switch (item.optString("t")) {
                case "b": ed.putBoolean(k, item.getBoolean("v")); break;
                case "i": ed.putInt(k, item.getInt("v")); break;
                case "l": ed.putLong(k, item.getLong("v")); break;
                case "f": ed.putFloat(k, (float) item.getDouble("v")); break;
                case "s": ed.putString(k, item.getString("v")); break;
                default: continue;
            }
            n++;
        }
        ed.commit();
        return n;
    }

    // ---------- clipboard ----------
    public static class Clip {
        public String text;
        public boolean pinned;
        public long time;
        Clip(String t, boolean p, long tm) { text = t; pinned = p; time = tm; }
    }

    public List<Clip> clips() {
        List<Clip> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString("clips", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Clip(o.getString("t"), o.optBoolean("p"), o.optLong("m")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    public void saveClips(List<Clip> list) {
        JSONArray a = new JSONArray();
        try {
            for (Clip c : list) {
                JSONObject o = new JSONObject();
                o.put("t", c.text); o.put("p", c.pinned); o.put("m", c.time);
                a.put(o);
            }
        } catch (Exception ignored) { }
        sp.edit().putString("clips", a.toString()).apply();
    }

    /** Adds a clip at the top; keeps pinned items forever and the latest 60 unpinned ones. */
    public void addClip(String text) {
        if (text == null) return;
        text = text.toString();
        if (text.trim().isEmpty() || text.length() > 20000) return;
        List<Clip> list = clips();
        boolean pinned = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).text.equals(text)) { pinned = list.get(i).pinned; list.remove(i); }
        }
        list.add(0, new Clip(text, pinned, System.currentTimeMillis()));
        int unpinned = 0;
        for (int i = 0; i < list.size(); i++) {
            if (!list.get(i).pinned && ++unpinned > 60) { list.remove(i); i--; }
        }
        saveClips(list);
    }

    // ---------- snippets (quick text) ----------
    public static class Snippet {
        public String title, text, shortcut;
        public Snippet(String title, String text, String shortcut) {
            this.title = title; this.text = text; this.shortcut = shortcut == null ? "" : shortcut;
        }
    }

    public List<Snippet> snippets() {
        List<Snippet> out = new ArrayList<>();
        String json = sp.getString("snippets", null);
        if (json == null) {
            out.add(new Snippet("Greeting", "Namaste! Thank you for contacting us. How can I help you today?", ";hi"));
            out.add(new Snippet("Thanks", "Thank you for your order! We'll share the dispatch details soon.", ";ty"));
            out.add(new Snippet("Address", "Add your shop address here (edit in Glass Keys settings)", ";addr"));
            out.add(new Snippet("UPI", "Add your UPI ID here (edit in Glass Keys settings)", ";upi"));
            return out;
        }
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Snippet(o.optString("title"), o.optString("text"), o.optString("sc")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    public void saveSnippets(List<Snippet> list) {
        JSONArray a = new JSONArray();
        try {
            for (Snippet s : list) {
                JSONObject o = new JSONObject();
                o.put("title", s.title); o.put("text", s.text); o.put("sc", s.shortcut);
                a.put(o);
            }
        } catch (Exception ignored) { }
        sp.edit().putString("snippets", a.toString()).apply();
    }

    // ---------- learned words and next-word pairs ----------
    public Map<String, Integer> learned() { return readMap("learned"); }
    public void saveLearned(Map<String, Integer> m) { writeMap("learned", m); }
    public Map<String, Integer> bigrams() { return readMap("bigrams"); }
    public void saveBigrams(Map<String, Integer> m) { writeMap("bigrams", m); }

    private Map<String, Integer> readMap(String key) {
        Map<String, Integer> m = new HashMap<>();
        try {
            JSONObject o = new JSONObject(sp.getString(key, "{}"));
            JSONArray names = o.names();
            if (names != null) for (int i = 0; i < names.length(); i++) {
                String n = names.getString(i);
                m.put(n, o.getInt(n));
            }
        } catch (Exception ignored) { }
        return m;
    }

    private void writeMap(String key, Map<String, Integer> m) {
        JSONObject o = new JSONObject();
        try { for (Map.Entry<String, Integer> e : m.entrySet()) o.put(e.getKey(), e.getValue()); }
        catch (Exception ignored) { }
        sp.edit().putString(key, o.toString()).apply();
    }

    public Map<String, Integer> trigrams() { return readMap("trigrams"); }
    public void saveTrigrams(Map<String, Integer> m) { writeMap("trigrams", m); }

    /** How you like certain words capitalised (e.g. nrrl -> NRRL, nishant -> Nishant). */
    public Map<String, String> caseForms() {
        Map<String, String> m = new HashMap<>();
        try {
            JSONObject o = new JSONObject(sp.getString("caseforms", "{}"));
            JSONArray names = o.names();
            if (names != null) for (int i = 0; i < names.length(); i++) m.put(names.getString(i), o.getString(names.getString(i)));
        } catch (Exception ignored) { }
        return m;
    }

    public void saveCaseForms(Map<String, String> m) {
        JSONObject o = new JSONObject();
        try { for (Map.Entry<String, String> e : m.entrySet()) o.put(e.getKey(), e.getValue()); } catch (Exception ignored) { }
        sp.edit().putString("caseforms", o.toString()).apply();
    }

    public void clearLearned() {
        sp.edit().remove("learned").remove("bigrams").remove("trigrams").remove("caseforms").remove("hiPicks").apply();
    }

    public List<String> recentEmoji() {
        List<String> out = new ArrayList<>();
        String s = sp.getString("recentEmoji", "");
        if (!s.isEmpty()) for (String e : s.split("\u0001")) if (!e.isEmpty()) out.add(e);
        return out;
    }

    public void pushRecentEmoji(String e) {
        List<String> r = recentEmoji();
        r.remove(e);
        r.add(0, e);
        while (r.size() > 32) r.remove(r.size() - 1);
        sp.edit().putString("recentEmoji", String.join("\u0001", r)).apply();
    }
}
