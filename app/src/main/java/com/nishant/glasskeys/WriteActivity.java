package com.nishant.glasskeys;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.proofreading.Proofreader;
import com.google.mlkit.genai.proofreading.ProofreaderOptions;
import com.google.mlkit.genai.proofreading.Proofreading;
import com.google.mlkit.genai.proofreading.ProofreadingRequest;
import com.google.mlkit.genai.proofreading.ProofreadingSuggestion;
import com.google.mlkit.genai.rewriting.Rewriter;
import com.google.mlkit.genai.rewriting.RewriterOptions;
import com.google.mlkit.genai.rewriting.Rewriting;
import com.google.mlkit.genai.rewriting.RewritingRequest;
import com.google.mlkit.genai.rewriting.RewritingSuggestion;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Write with AI": fix grammar, rephrase or change the tone of what you typed, using Gemini Nano
 * on the phone itself (Android AICore). Nothing is sent anywhere — Glass Keys has no internet.
 *
 * Android only lets on-device AI run for the app on screen, so the keyboard opens this small sheet
 * over the chat; the chosen text is handed back and put into the text box when the chat returns.
 */
public class WriteActivity extends Activity {

    /** Text the user picked; the keyboard applies it when the text box gets focus back. */
    static volatile String pendingResult;
    static volatile String pendingOriginal;
    static volatile boolean pendingSelection;

    static final String EXTRA_TEXT = "text", EXTRA_SELECTION = "selection";

    private static final String[] LABELS = {"Fix grammar", "Rephrase", "Friendly", "Professional", "Shorter", "Longer", "Emojify"};

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private float dp;
    private String original;
    private boolean selection;
    private TextView status;
    private LinearLayout results;
    private final List<TextView> chips = new ArrayList<>();
    private Proofreader proofreader;
    private Rewriter rewriter;
    private int rewriterType = -1;
    private int running;   // ignores results of an option the user already moved away from

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        original = getIntent().getStringExtra(EXTRA_TEXT);
        selection = getIntent().getBooleanExtra(EXTRA_SELECTION, false);
        if (original == null || original.trim().isEmpty()) { finish(); return; }
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(0xFF101114);
        setContentView(build());
        run(0);
    }

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x99000000);
        root.setOnClickListener(v -> finish());   // tap outside the sheet to cancel

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        int pad = (int) (18 * dp);
        sheet.setPadding(pad, pad, pad, (int) (24 * dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF15161A);
        bg.setCornerRadii(new float[]{24 * dp, 24 * dp, 24 * dp, 24 * dp, 0, 0, 0, 0});
        sheet.setBackground(bg);

        TextView title = text("✦  Write with AI", 18, true, Color.WHITE);
        sheet.addView(title);
        TextView sub = text("Runs on your phone. Nothing leaves it.", 12, false, 0x99FFFFFF);
        sub.setPadding(0, (int) (2 * dp), 0, (int) (12 * dp));
        sheet.addView(sub);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        hs.addView(row);
        for (int i = 0; i < LABELS.length; i++) {
            final int idx = i;
            TextView c = text(LABELS[i], 14, true, Color.WHITE);
            c.setPadding((int) (14 * dp), (int) (9 * dp), (int) (14 * dp), (int) (9 * dp));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = (int) (8 * dp);
            c.setOnClickListener(v -> run(idx));
            chips.add(c);
            row.addView(c, lp);
        }
        sheet.addView(hs);

        status = text("", 13, false, 0xB3FFFFFF);
        status.setPadding(0, (int) (14 * dp), 0, (int) (6 * dp));
        sheet.addView(status);

        ScrollView sv = new ScrollView(this);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        sv.addView(results);
        sheet.addView(sv, new LinearLayout.LayoutParams(-1, (int) (260 * dp)));

        TextView cancel = text("Cancel", 15, true, 0xCCFFFFFF);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, (int) (14 * dp), 0, 0);
        cancel.setOnClickListener(v -> finish());
        sheet.addView(cancel, new LinearLayout.LayoutParams(-1, -2));

        root.addView(sheet, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        return root;
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        return t;
    }

    private void highlight(int sel) {
        for (int i = 0; i < chips.size(); i++) {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(18 * dp);
            g.setColor(i == sel ? 0xFF0A84FF : 0x22FFFFFF);
            chips.get(i).setBackground(g);
        }
    }

    private static int typeFor(int option) {
        switch (option) {
            case 1: return RewriterOptions.OutputType.REPHRASE;
            case 2: return RewriterOptions.OutputType.FRIENDLY;
            case 3: return RewriterOptions.OutputType.PROFESSIONAL;
            case 4: return RewriterOptions.OutputType.SHORTEN;
            case 5: return RewriterOptions.OutputType.ELABORATE;
            default: return RewriterOptions.OutputType.EMOJIFY;
        }
    }

    private void run(int option) {
        highlight(option);
        results.removeAllViews();
        status.setText("Thinking…");
        final int token = ++running;
        final String input = original.length() > 1000 ? original.substring(original.length() - 1000) : original;
        worker.execute(() -> {
            List<String> out = new ArrayList<>();
            String error = null;
            try {
                int st;
                if (option == 0) {
                    if (proofreader == null) proofreader = Proofreading.getClient(ProofreaderOptions.builder(this)
                            .setInputType(ProofreaderOptions.InputType.KEYBOARD)
                            .setLanguage(ProofreaderOptions.Language.ENGLISH).build());
                    st = proofreader.checkFeatureStatus().get();
                } else {
                    int type = typeFor(option);
                    if (rewriter == null || rewriterType != type) {
                        if (rewriter != null) rewriter.close();
                        rewriter = Rewriting.getClient(RewriterOptions.builder(this)
                                .setOutputType(type).setLanguage(RewriterOptions.Language.ENGLISH).build());
                        rewriterType = type;
                    }
                    st = rewriter.checkFeatureStatus().get();
                }
                if (st == FeatureStatus.UNAVAILABLE) {
                    error = "On-device AI isn't available on this phone yet. It needs Gemini Nano (Android AICore) — on supported phones it turns on by itself after the AICore app updates.";
                } else {
                    if (st != FeatureStatus.AVAILABLE)
                        main.post(() -> { if (token == running) status.setText("Getting the AI model ready on your phone (one time)…"); });
                    if (option == 0) {
                        for (ProofreadingSuggestion s : proofreader.runInference(ProofreadingRequest.builder(input).build()).get().getResults())
                            out.add(s.getText());
                    } else {
                        for (RewritingSuggestion s : rewriter.runInference(RewritingRequest.builder(input).build()).get().getResults())
                            out.add(s.getText());
                    }
                }
            } catch (Exception e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                error = "Couldn't run the AI: " + c.getMessage();
            }
            final List<String> fo = out;
            final String fe = error;
            main.post(() -> { if (token == running && !isFinishing()) show(fo, fe); });
        });
    }

    private void show(List<String> out, String error) {
        results.removeAllViews();
        if (error != null) { status.setText(error); return; }
        List<String> uniq = new ArrayList<>();
        for (String s : out) if (s != null && !s.trim().isEmpty() && !uniq.contains(s.trim())) uniq.add(s.trim());
        if (uniq.isEmpty() || (uniq.size() == 1 && uniq.get(0).equals(original.trim()))) {
            status.setText("Looks good — nothing to change.");
            return;
        }
        status.setText("Tap one to use it");
        for (String s : uniq) {
            TextView card = text(s, 15, false, Color.WHITE);
            int p = (int) (14 * dp);
            card.setPadding(p, p, p, p);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(16 * dp);
            g.setColor(0x1AFFFFFF);
            g.setStroke((int) (1 * dp), 0x22FFFFFF);
            card.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, -2);
            lp.bottomMargin = (int) (8 * dp);
            card.setOnClickListener(v -> {
                v.setScaleX(0.98f); v.setScaleY(0.98f);
                pendingOriginal = original;
                pendingSelection = selection;
                pendingResult = s;
                finish();
            });
            results.addView(card, lp);
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.execute(() -> {
            try { if (proofreader != null) proofreader.close(); } catch (Exception ignored) { }
            try { if (rewriter != null) rewriter.close(); } catch (Exception ignored) { }
        });
        worker.shutdown();
    }
}
