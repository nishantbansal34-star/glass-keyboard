package com.nishant.glasskeys;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

/** Setup + settings screen (also opened from the keyboard's gear icon). */
public class SettingsActivity extends Activity {
    private static final int REQ_PHOTO = 11, REQ_MIC = 12;

    private Prefs prefs;
    private float dp;
    private LinearLayout col;
    private ScrollView scroll;
    private View snippetsAnchor, businessAnchor;
    private TextView step1, step2, step3;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        dp = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().setNavigationBarColor(0xFF000000);
        build();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent i) {
        String section = i == null ? null : i.getStringExtra("section");
        if ("mic".equals(section)) requestMic();
        if ("business".equals(section) && businessAnchor != null)
            scroll.post(() -> scroll.smoothScrollTo(0, businessAnchor.getTop()));
        if ("snippets".equals(section) && snippetsAnchor != null)
            scroll.post(() -> scroll.smoothScrollTo(0, snippetsAnchor.getTop()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSteps();
        if (insights != null) renderInsights();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) refreshSteps(); // after the keyboard picker closes
    }

    // ------------------------------------------------------------------ UI building

    private void build() {
        scroll = new ScrollView(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF000000, 0xFF000000, 0xFF0A0A0A});
        scroll.setBackground(bg);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (16 * dp);
        col.setPadding(p, (int) (28 * dp), p, (int) (40 * dp));
        scroll.addView(col);
        setContentView(scroll);

        TextView title = text("Glass Keys", 30, true);
        col.addView(title);
        TextView sub = text("Your private liquid-glass keyboard. It has no internet permission, so nothing you type can leave your phone.", 14, false);
        sub.setAlpha(0.75f);
        sub.setPadding(0, (int) (4 * dp), 0, (int) (12 * dp));
        col.addView(sub);

        // --- Setup
        LinearLayout setup = card("Set up");
        step1 = text("", 15, false);
        setup.addView(step1);
        setup.addView(button("1 · Turn on Glass Keys", v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))));
        step2 = text("", 15, false);
        setup.addView(step2);
        setup.addView(button("2 · Switch to Glass Keys", v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.showInputMethodPicker();
        }));
        step3 = text("", 15, false);
        setup.addView(step3);
        setup.addView(button("3 · Allow microphone (voice typing)", v -> requestMic()));
        EditText test = new EditText(this);
        test.setHint("Try typing here…");
        test.setHintTextColor(0x99FFFFFF);
        test.setTextColor(Color.WHITE);
        test.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        test.setBackground(pill(0x26FFFFFF, 12));
        test.setPadding((int) (14 * dp), (int) (12 * dp), (int) (14 * dp), (int) (12 * dp));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = (int) (10 * dp);
        setup.addView(test, tlp);

        // --- Business
        LinearLayout biz = card("Business");
        businessAnchor = biz;
        TextView bh = text("Used by the ₹ Business button: payment requests, payment QR codes and your shop-hours reply. Shortcuts: ;pay 1250  ·  ;qr  ·  ;hours", 13, false);
        bh.setAlpha(0.7f);
        biz.addView(bh);
        biz.addView(bizField("Business name (e.g. NRRL)", "bizName", false));
        biz.addView(bizField("UPI ID (e.g. nrrl@okaxis)", "bizUpi", false));
        biz.addView(bizField("Phone / WhatsApp number", "bizPhone", false));
        biz.addView(bizField("Shop address", "bizAddress", true));
        biz.addView(bizField("Google Maps link (optional)", "bizMaps", false));
        LinearLayout times = new LinearLayout(this);
        EditText op = bizField("Opens (10:00)", "bizOpen", false);
        EditText cl = bizField("Closes (20:00)", "bizClose", false);
        if (op.getText().length() == 0) op.setText("10:00");
        if (cl.getText().length() == 0) cl.setText("20:00");
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, -2, 1);
        half.rightMargin = (int) (6 * dp);
        times.addView(op, half);
        times.addView(cl, new LinearLayout.LayoutParams(0, -2, 1));
        biz.addView(times);
        biz.addView(label("Closed on  ·  tap to toggle"));
        LinearLayout days = new LinearLayout(this);
        String[] dn = {"S", "M", "T", "W", "T", "F", "S"};
        int[] dow = {1, 2, 3, 4, 5, 6, 7};
        for (int i = 0; i < 7; i++) {
            final String d = String.valueOf(dow[i]);
            TextView t = text(dn[i], 15, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, (int) (10 * dp), 0, (int) (10 * dp));
            Runnable paint = () -> t.setBackground(pill(prefs.str("bizClosed", "1").contains(d) ? 0x99E0457B : 0x1AFFFFFF, 12));
            paint.run();
            t.setOnClickListener(v -> {
                String c = prefs.str("bizClosed", "1");
                prefs.setStr("bizClosed", c.contains(d) ? c.replace(d, "") : c + d);
                paint.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.rightMargin = (int) (5 * dp);
            days.addView(t, lp);
        }
        biz.addView(days);

        // --- Theme pack (kept separate from the clean Liquid Glass options)
        LinearLayout packCard = card("Theme pack");
        TextView packHelp = text("Liquid Glass is the clean look. Shadow Realm is a dark-fantasy pack with violet flame keys and a typing level that grows as you write.", 13, false);
        packHelp.setAlpha(0.7f);
        packCard.addView(packHelp);
        packCard.addView(choice(new String[]{"Liquid Glass", "Shadow Realm"}, new String[]{"0", "1"},
                String.valueOf(prefs.pack()), v -> prefs.setInt("pack", Integer.parseInt(v))));

        // --- Look
        LinearLayout look = card("Liquid Glass look");
        look.addView(label("Accent colour"));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        hs.addView(chips);
        for (int i = 0; i < Theme.ALL.length; i++) {
            final int idx = i;
            Theme t = Theme.ALL[i];
            TextView chip = text(t.name, 14, true);
            chip.setTextColor(t.text);
            chip.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{t.blobs[0], t.base, t.blobs[1]});
            g.setCornerRadius(16 * dp);
            g.setStroke((int) (2 * dp), prefs.theme() == i ? Color.WHITE : 0x40FFFFFF);
            chip.setBackground(g);
            chip.setPadding((int) (18 * dp), (int) (22 * dp), (int) (18 * dp), (int) (22 * dp));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = (int) (8 * dp);
            chip.setOnClickListener(v -> { prefs.setTheme(idx); recreate(); });
            chips.addView(chip, lp);
        }
        look.addView(hs);

        look.addView(label("Glass style"));
        look.addView(choice(new String[]{"Premium dark", "Bright jelly"}, new String[]{"1", "0"},
                prefs.darkGlass() ? "1" : "0", v -> prefs.setBool("darkglass", v.equals("1"))));
        look.addView(label("Background"));
        look.addView(choice(new String[]{"Black", "Bloom", "My photo", "Colours"}, new String[]{"3", "1", "2", "0"},
                String.valueOf(prefs.bgMode()), v -> {
                    if (v.equals("2") && !new File(getFilesDir(), "backdrop.jpg").exists()) { pickPhoto(); return; }
                    prefs.setInt("bgmode", Integer.parseInt(v));
                }));
        look.addView(button("Choose a photo from gallery", v -> pickPhoto()));
        look.addView(label("Photo softness"));
        SeekBar blur = new SeekBar(this);
        blur.setMax(8);
        blur.setProgress(prefs.photoBlur());
        blur.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean f) { prefs.setInt("photoblur", v); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        look.addView(blur);
        look.addView(label("Photo dimming"));
        SeekBar dim = new SeekBar(this);
        dim.setMax(90);
        dim.setProgress(prefs.photoDim());
        dim.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean f) { prefs.setInt("photodim", v); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        look.addView(dim);
        look.addView(toggle("Always show capital letters on keys", "capslabels2", false));

        look.addView(toggle("Live blur of the app behind (experimental, Android 12+)", "liveblur", false));
        if (prefs.bool("liveblurUnsupported", false)) {
            TextView n = text("Your phone draws the keyboard window full-height, so live blur is skipped to avoid blurring your whole screen. The frosted look is used instead.", 12, false);
            n.setAlpha(0.6f);
            look.addView(n);
        }
        look.addView(toggle("Liquid droplet preview when a key is pressed", "keypopup", true));
        look.addView(toggle("Liquid motion: flowing background, light that follows your finger, springy keys", "ripple", true));
        look.addView(label("Key height"));
        SeekBar sb = new SeekBar(this);
        sb.setMax(24);
        sb.setProgress(Math.max(0, Math.min(24, prefs.keyHeightDp() - 40)));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean f) { prefs.setInt("keyheight", 40 + v); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        look.addView(sb);

        // --- Typing
        LinearLayout typing = card("Typing");
        typing.addView(toggle("Swipe typing (glide across letters)", "glide", true));
        typing.addView(toggle("Show the glowing swipe trail", "trail", true));
        typing.addView(toggle("Word suggestions & next-word prediction", "suggest", true));
        typing.addView(toggle("Autocorrect (backspace right after undoes it)", "autocorrect", true));
        typing.addView(toggle("Capitalise the first letter of sentences", "autocaps", true));
        typing.addView(toggle("Double-tap space for a full stop", "dblspace", true));
        typing.addView(toggle("Always-visible number row", "numrow", false));
        typing.addView(toggle("Learn words I type (off = always incognito)", "learn", true));
        typing.addView(button("Forget all learned words", v -> { prefs.clearLearned(); toast("Learned words cleared"); if (insights != null) renderInsights(); }));

        buildInsights();

        LinearLayout fb = card("Feel & sound");
        fb.addView(toggle("Vibrate on key press", "haptics", true));
        fb.addView(toggle("Sound on key press", "sound", false));

        LinearLayout voice = card("Voice typing language");
        voice.addView(choice(new String[]{"English (India)", "Hindi", "English (US)"}, new String[]{"en-IN", "hi-IN", "en-US"},
                prefs.voiceLang(), v -> prefs.setStr("voicelang", v)));

        LinearLayout calc = card("Calculator GST rate");
        calc.addView(choice(new String[]{"5%", "12%", "18%", "28%"}, new String[]{"5", "12", "18", "28"},
                String.valueOf(prefs.gstRate()), v -> prefs.setInt("gst", Integer.parseInt(v))));

        // --- Snippets
        LinearLayout sn = card("Quick text (snippets)");
        snippetsAnchor = sn;
        TextView help = text("Tap one in the keyboard's ⚡ panel, or type its shortcut followed by space, e.g. ;upi", 13, false);
        help.setAlpha(0.7f);
        sn.addView(help);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sn.addView(list);
        renderSnippets(list);

        EditText t1 = field("Title (e.g. Bank details)", false);
        EditText t2 = field("Text to insert", true);
        EditText t3 = field("Shortcut (optional, e.g. ;bank)", false);
        sn.addView(t1); sn.addView(t2); sn.addView(t3);
        sn.addView(button("Add quick text", v -> {
            String a = t1.getText().toString().trim(), bb = t2.getText().toString(), c = t3.getText().toString().trim();
            if (bb.trim().isEmpty()) { toast("Write the text to insert"); return; }
            if (a.isEmpty()) a = bb.length() > 20 ? bb.substring(0, 20) + "…" : bb;
            List<Prefs.Snippet> l = prefs.snippets();
            l.add(0, new Prefs.Snippet(a, bb, c));
            prefs.saveSnippets(l);
            t1.setText(""); t2.setText(""); t3.setText("");
            renderSnippets(list);
            toast("Added");
        }));

        LinearLayout tips = card("Gestures");
        tips.addView(text("• Swipe across letters to type a whole word — no lifting\n• Slide on the space bar to move the cursor\n• Slide left from backspace to delete whole words\n• Hold backspace: deletes letters, then words\n• Hold a key for accents & symbols (hold ₹ for $ € £)\n• Hold the space bar to switch keyboards\n• Double-tap shift for CAPS LOCK\n• Type 250*12= and tap the answer in the bar\n• Copied text shows up as a paste chip for a minute — with one-tap actions for phone numbers, GSTINs, pincodes, UPI IDs and amounts\n• ;pay 1250 + space types a UPI payment request · ;qr sends a payment QR · ;hours sends your shop timings", 14, false));
    }

    private EditText bizField(String hint, String key, boolean multi) {
        EditText e = field(hint, multi);
        e.setText(prefs.str(key, ""));
        e.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable ed) { prefs.setStr(key, ed.toString()); }
        });
        return e;
    }

    private void pickPhoto() {
        Intent it = new Intent(Intent.ACTION_GET_CONTENT);
        it.setType("image/*");
        startActivityForResult(it, REQ_PHOTO);
    }

    // ------------------------------------------------------------------ Your typing (insights)

    private LinearLayout insights;

    private void buildInsights() {
        insights = card("Your typing");
        renderInsights();
    }

    private static List<java.util.Map.Entry<String, Integer>> sorted(java.util.Map<String, Integer> m) {
        List<java.util.Map.Entry<String, Integer>> l = new java.util.ArrayList<>(m.entrySet());
        l.sort((x, y) -> y.getValue() - x.getValue());
        return l;
    }

    private void renderInsights() {
        while (insights.getChildCount() > 1) insights.removeViewAt(1);
        java.util.Map<String, Integer> learned = prefs.learned(), bi = prefs.bigrams(), tri = prefs.trigrams();
        java.util.Map<String, String> forms = prefs.caseForms();
        int total = 0;
        for (int v : learned.values()) total += v;
        TextView sum = text(learned.size() + " different words learned · " + total + " words analysed. "
                + "It all stays on this phone and shapes your suggestions, autocorrect and swipe typing.", 13, false);
        sum.setAlpha(0.75f);
        insights.addView(sum);
        if (learned.isEmpty()) {
            TextView e = text("Start typing and your most-used words and phrases will appear here.", 14, false);
            e.setPadding(0, (int) (8 * dp), 0, 0);
            insights.addView(e);
            return;
        }

        insights.addView(label("Most-used words  ·  tap one to forget it"));
        List<java.util.Map.Entry<String, Integer>> top = sorted(learned);
        LinearLayout row = null;
        float rowW = 0, maxW = getResources().getDisplayMetrics().widthPixels - 72 * dp;
        for (int i = 0; i < Math.min(30, top.size()); i++) {
            String w = top.get(i).getKey();
            String shown = forms.containsKey(w) ? forms.get(w) : w;
            TextView chip = text(shown + "  " + top.get(i).getValue(), 14, false);
            chip.setBackground(pill(0x1FFFFFFF, 14));
            chip.setPadding((int) (12 * dp), (int) (7 * dp), (int) (12 * dp), (int) (7 * dp));
            chip.setOnClickListener(v -> confirmForget(w, shown));
            chip.measure(0, 0);
            float cw = chip.getMeasuredWidth() + 8 * dp;
            if (row == null || rowW + cw > maxW) {
                row = new LinearLayout(this);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.bottomMargin = (int) (8 * dp);
                insights.addView(row, rl);
                rowW = 0;
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = (int) (8 * dp);
            row.addView(chip, lp);
            rowW += cw;
        }

        StringBuilder starts = new StringBuilder();
        int n = 0;
        for (java.util.Map.Entry<String, Integer> e : sorted(bi)) {
            if (!e.getKey().startsWith(Dictionary.START + " ") || e.getValue() < 2) continue;
            String w = e.getKey().substring(2);
            w = forms.containsKey(w) ? forms.get(w) : Character.toUpperCase(w.charAt(0)) + w.substring(1);
            if (n++ > 0) starts.append("  ·  ");
            starts.append(w);
            if (n >= 6) break;
        }
        if (n > 0) {
            insights.addView(label("How you usually start messages"));
            insights.addView(text(starts.toString(), 15, false));
        }

        StringBuilder ph = new StringBuilder();
        n = 0;
        for (java.util.Map.Entry<String, Integer> e : sorted(tri)) {
            if (e.getValue() < 2 || e.getKey().startsWith(Dictionary.START)) continue;
            if (n++ > 0) ph.append("\n");
            ph.append("“").append(e.getKey().replace(Dictionary.START + " ", "")).append("”  ×").append(e.getValue());
            if (n >= 8) break;
        }
        if (n > 0) {
            insights.addView(label("Your favourite phrases (it finishes these for you)"));
            insights.addView(text(ph.toString(), 15, false));
        }
    }

    private void confirmForget(String w, String shown) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Forget “" + shown + "”?")
                .setMessage("It won't be suggested from your typing any more (you can still type it).")
                .setPositiveButton("Forget", (d, x) -> {
                    java.util.Map<String, Integer> l = prefs.learned(), bi = prefs.bigrams(), tri = prefs.trigrams();
                    java.util.Map<String, String> f = prefs.caseForms();
                    l.remove(w);
                    f.remove(w);
                    bi.keySet().removeIf(k -> k.endsWith(" " + w) || k.startsWith(w + " "));
                    tri.keySet().removeIf(k -> (" " + k + " ").contains(" " + w + " "));
                    prefs.saveLearned(l); prefs.saveBigrams(bi); prefs.saveTrigrams(tri); prefs.saveCaseForms(f);
                    renderInsights();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void renderSnippets(LinearLayout list) {
        list.removeAllViews();
        List<Prefs.Snippet> l = prefs.snippets();
        for (int i = 0; i < l.size(); i++) {
            final int idx = i;
            Prefs.Snippet s = l.get(i);
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setBackground(pill(0x1AFFFFFF, 12));
            r.setPadding((int) (12 * dp), (int) (8 * dp), (int) (6 * dp), (int) (8 * dp));
            TextView tv = text(s.title + (s.shortcut.isEmpty() ? "" : "   " + s.shortcut) + "\n" + s.text, 14, false);
            tv.setMaxLines(4);
            r.addView(tv, new LinearLayout.LayoutParams(0, -2, 1));
            Button edit = smallButton("Edit");
            edit.setOnClickListener(v -> editSnippet(idx, list));
            r.addView(edit);
            Button del = smallButton("✕");
            del.setOnClickListener(v -> {
                List<Prefs.Snippet> cur = prefs.snippets();
                if (idx < cur.size()) cur.remove(idx);
                prefs.saveSnippets(cur);
                renderSnippets(list);
            });
            r.addView(del);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = (int) (8 * dp);
            list.addView(r, lp);
        }
    }

    private void editSnippet(int idx, LinearLayout list) {
        List<Prefs.Snippet> cur = prefs.snippets();
        if (idx >= cur.size()) return;
        Prefs.Snippet s = cur.get(idx);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) (20 * dp), (int) (8 * dp), (int) (20 * dp), 0);
        EditText a = new EditText(this); a.setText(s.title); a.setHint("Title");
        EditText b = new EditText(this); b.setText(s.text); b.setHint("Text");
        b.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText c = new EditText(this); c.setText(s.shortcut); c.setHint("Shortcut e.g. ;upi");
        box.addView(a); box.addView(b); box.addView(c);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Edit quick text")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    List<Prefs.Snippet> l = prefs.snippets();
                    if (idx < l.size()) {
                        l.set(idx, new Prefs.Snippet(a.getText().toString(), b.getText().toString(), c.getText().toString().trim()));
                        prefs.saveSnippets(l);
                        renderSnippets(list);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------------ setup status

    private void refreshSteps() {
        if (step1 == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        boolean enabled = false;
        for (InputMethodInfo i : imm.getEnabledInputMethodList())
            if (i.getPackageName().equals(getPackageName())) enabled = true;
        String cur = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean selected = cur != null && cur.startsWith(getPackageName());
        boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        step1.setText(enabled ? "✓ Turned on" : "Turn on Glass Keys in your phone's keyboard list:");
        step2.setText(selected ? "✓ Glass Keys is your keyboard" : "Then make it your active keyboard:");
        step3.setText(mic ? "✓ Voice typing allowed" : "Optional, for the 🎤 button:");
    }

    private void requestMic() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        else toast("Microphone already allowed");
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        refreshSteps();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PHOTO || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        new Thread(() -> {
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, bounds); }
                int sample = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1400) sample *= 2;
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                Bitmap src;
                try (InputStream in = getContentResolver().openInputStream(uri)) { src = BitmapFactory.decodeStream(in, null, o); }
                if (src == null) throw new Exception();
                try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "backdrop.jpg"))) {
                    src.compress(Bitmap.CompressFormat.JPEG, 90, out);
                }
                prefs.setInt("bgmode", 2);
                runOnUiThread(() -> { toast("Photo set — open the keyboard to see it"); recreate(); });
            } catch (Exception e) {
                runOnUiThread(() -> toast("Couldn't open that photo"));
            }
        }).start();
    }

    // ------------------------------------------------------------------ widgets

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.WHITE);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView label(String s) {
        TextView t = text(s, 13, true);
        t.setAlpha(0.7f);
        t.setPadding(0, (int) (12 * dp), 0, (int) (6 * dp));
        return t;
    }

    private GradientDrawable pill(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusDp * dp);
        g.setStroke((int) Math.max(1, dp), 0x40FFFFFF);
        return g;
    }

    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x1FFFFFFF, 0x0DFFFFFF});
        g.setCornerRadius(22 * dp);
        g.setStroke((int) Math.max(1, dp), 0x55FFFFFF);
        c.setBackground(g);
        int p = (int) (16 * dp);
        c.setPadding(p, p, p, p);
        TextView t = text(title, 18, true);
        t.setPadding(0, 0, 0, (int) (6 * dp));
        c.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = (int) (14 * dp);
        col.addView(c, lp);
        return c;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setBackground(pill(0x2EFFFFFF, 14));
        b.setOnClickListener(l);
        b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, (int) (48 * dp));
        lp.topMargin = (int) (6 * dp);
        lp.bottomMargin = (int) (6 * dp);
        lp.leftMargin = (int) (2 * dp);
        lp.rightMargin = (int) (2 * dp);
        b.setLayoutParams(lp);
        return b;
    }

    private Button smallButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackground(pill(0x26FFFFFF, 10));
        b.setStateListAnimator(null);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding((int) (12 * dp), 0, (int) (12 * dp), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, (int) (38 * dp));
        lp.leftMargin = (int) (6 * dp);
        b.setLayoutParams(lp);
        return b;
    }

    private Switch toggle(String s, String key, boolean def) {
        Switch sw = new Switch(this);
        sw.setText(s);
        sw.setTextColor(Color.WHITE);
        sw.setTextSize(15);
        sw.setChecked(prefs.bool(key, def));
        sw.setPadding(0, (int) (8 * dp), 0, (int) (8 * dp));
        sw.setOnCheckedChangeListener((b, v) -> prefs.setBool(key, v));
        return sw;
    }

    private EditText field(String hint, boolean multi) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0x88FFFFFF);
        e.setTextColor(Color.WHITE);
        e.setInputType(InputType.TYPE_CLASS_TEXT | (multi ? InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES : 0));
        e.setBackground(pill(0x1AFFFFFF, 12));
        e.setPadding((int) (12 * dp), (int) (10 * dp), (int) (12 * dp), (int) (10 * dp));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = (int) (8 * dp);
        e.setLayoutParams(lp);
        return e;
    }

    interface OnChoice { void chose(String v); }

    private View choice(String[] labels, String[] values, String current, OnChoice cb) {
        LinearLayout r = new LinearLayout(this);
        TextView[] views = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = text(labels[i], 14, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding((int) (8 * dp), (int) (12 * dp), (int) (8 * dp), (int) (12 * dp));
            views[i] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.rightMargin = (int) (6 * dp);
            r.addView(t, lp);
        }
        Runnable paint = null;
        final String[] sel = {current};
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            views[i].setOnClickListener(v -> {
                sel[0] = values[idx];
                cb.chose(values[idx]);
                for (int k = 0; k < views.length; k++)
                    views[k].setBackground(pill(values[k].equals(sel[0]) ? 0x807C5CFF : 0x1AFFFFFF, 12));
            });
            views[i].setBackground(pill(values[i].equals(current) ? 0x807C5CFF : 0x1AFFFFFF, 12));
        }
        return r;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
