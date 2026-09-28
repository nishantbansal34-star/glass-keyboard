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
    private View snippetsAnchor;
    private TextView step1, step2, step3;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        dp = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(0xFF0E1024);
        getWindow().setNavigationBarColor(0xFF0E1024);
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
        if ("snippets".equals(section) && snippetsAnchor != null)
            scroll.post(() -> scroll.smoothScrollTo(0, snippetsAnchor.getTop()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSteps();
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
                new int[]{0xFF2A1B6B, 0xFF0E1024, 0xFF08323A});
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

        // --- Look
        LinearLayout look = card("Look");
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

        look.addView(label("Behind the glass"));
        look.addView(choice(new String[]{"Bloom", "My photo", "Flowing colours"}, new String[]{"1", "2", "0"},
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
        look.addView(toggle("Capital letters on keys", "capslabels", true));

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
        typing.addView(toggle("Word suggestions & next-word prediction", "suggest", true));
        typing.addView(toggle("Autocorrect (backspace right after undoes it)", "autocorrect", true));
        typing.addView(toggle("Capitalise the first letter of sentences", "autocaps", true));
        typing.addView(toggle("Double-tap space for a full stop", "dblspace", true));
        typing.addView(toggle("Always-visible number row", "numrow", false));
        typing.addView(toggle("Learn words I type (off = always incognito)", "learn", true));
        typing.addView(button("Forget all learned words", v -> { prefs.clearLearned(); toast("Learned words cleared"); }));

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
        tips.addView(text("• Slide on the space bar to move the cursor\n• Slide left from backspace to delete whole words\n• Hold backspace: deletes letters, then words\n• Hold a key for accents & symbols (hold ₹ for $ € £)\n• Hold the space bar to switch keyboards\n• Double-tap shift for CAPS LOCK\n• Type 250*12= and tap the answer in the bar\n• Copied text shows up as a paste chip for a minute", 14, false));
    }

    private void pickPhoto() {
        Intent it = new Intent(Intent.ACTION_GET_CONTENT);
        it.setType("image/*");
        startActivityForResult(it, REQ_PHOTO);
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
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x33FFFFFF, 0x14FFFFFF});
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
