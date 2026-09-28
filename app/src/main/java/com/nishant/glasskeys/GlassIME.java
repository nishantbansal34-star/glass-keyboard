package com.nishant.glasskeys;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GlassIME extends InputMethodService implements KeyboardView.Listener {

    private static final int P_NONE = 0, P_EMOJI = 1, P_CLIP = 2, P_SNIP = 3, P_CALC = 4, P_EDIT = 5;

    private Prefs prefs;
    private Dictionary dict;
    private GlassPainter gp;
    private Theme theme;
    private float dp;

    private RootView root;
    private StripView strip;
    private FrameLayout content;
    private KeyboardView keyboard;
    private View overlay;
    private int panel = P_NONE;

    // panels
    private LinearLayout emojiPanel;
    private EmojiGrid emojiGrid;
    private ScrollView emojiScroll;
    private PadView emojiTabs;
    private int emojiTab = 1;
    private ScrollView clipScroll, snipScroll;
    private CardList clipList, snipList;
    private LinearLayout calcPanel;
    private View calcDisplay;
    private PadView calcPad;
    private String calcExpr = "";
    private PadView editPad;
    private boolean selectMode;

    // typing state
    private final StringBuilder composing = new StringBuilder();
    private String prevWord = null;
    private boolean noSuggest, noLearn, noAutoCorrect, noAutoCaps, isPassword;
    private String lastCorrOriginal, lastCorrReplacement;
    private long lastSpaceTime;
    private boolean toolbarForced;

    // clipboard
    private ClipboardManager cm;
    private long lastClipTime;
    private String lastClipText;
    private final ClipboardManager.OnPrimaryClipChangedListener clipListener = this::onClipChanged;

    // voice
    private SpeechRecognizer recognizer;
    private boolean listening;
    private String partialVoice = "";

    private Vibrator vibrator;
    private AudioManager audio;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        dict = new Dictionary(this, prefs);
        dp = getResources().getDisplayMetrics().density;
        gp = new GlassPainter(dp);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.addPrimaryClipChangedListener(clipListener);
    }

    @Override
    public void onDestroy() {
        if (cm != null) cm.removePrimaryClipChangedListener(clipListener);
        stopVoice();
        dict.flush();
        super.onDestroy();
    }

    @Override
    public boolean onEvaluateFullscreenMode() { return false; }

    // ================================================================= views

    /** Root container that paints the frosted glass backdrop behind everything. */
    private class RootView extends FrameLayout {
        private Bitmap backdrop;
        private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        RootView(Context c) { super(c); setWillNotDraw(false); setClipChildren(false); }
        void refresh() { backdrop = null; invalidate(); }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { backdrop = null; }
        /** Size ourselves to the keyboard column only — never stretch to the full-height IME window. */
        @Override protected void onMeasure(int wSpec, int hSpec) {
            View col = getChildAt(0);
            int w = MeasureSpec.getSize(wSpec);
            col.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int h = col.getMeasuredHeight();
            int ew = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), eh = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY);
            for (int i = 1; i < getChildCount(); i++) getChildAt(i).measure(ew, eh);
            setMeasuredDimension(w, h);
        }
        @Override protected void onDraw(Canvas c) {
            if (backdrop == null) backdrop = gp.renderBackdrop(getWidth(), getHeight(), theme, loadCustomBackdrop(), liveBlurActive);
            if (backdrop != null) c.drawBitmap(backdrop, 0, 0, p);
        }
    }

    private boolean liveBlurActive;

    private Bitmap loadCustomBackdrop() {
        File f = new File(getFilesDir(), "backdrop.png");
        if (!f.exists()) return null;
        try { return BitmapFactory.decodeFile(f.getAbsolutePath()); } catch (Exception e) { return null; }
    }

    @Override
    public View onCreateInputView() {
        theme = Theme.get(prefs.theme());
        root = new RootView(this);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);

        strip = new StripView(this, gp);
        strip.feedback = () -> feedback(false);
        column.addView(strip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (46 * dp)));

        content = new FrameLayout(this);
        content.setClipChildren(false);
        keyboard = new KeyboardView(this, gp);
        keyboard.setListener(this);
        content.addView(keyboard, new FrameLayout.LayoutParams(-1, -1));
        column.addView(content, new LinearLayout.LayoutParams(-1, keyboardHeight()));

        root.addView(column, new FrameLayout.LayoutParams(-1, -2));

        overlay = new View(this) {
            @Override protected void onDraw(Canvas c) { keyboard.drawOverlay(c, strip.getHeight()); }
        };
        overlay.setWillNotDraw(false);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        keyboard.overlay = overlay;
        keyboard.overflowAbove = 46 * dp;

        buildPanels();
        applySettings();
        return root;
    }

    private int keyboardHeight() {
        int rows = prefs.numberRow() ? 5 : 4;
        return (int) ((rows * prefs.keyHeightDp() + 8) * dp);
    }

    private void applySettings() {
        theme = Theme.get(prefs.theme());
        keyboard.configure(theme, prefs.numberRow(), prefs.oneHanded(), prefs.keyPopup(), prefs.glassRipple());
        strip.setTheme(theme);
        for (View v : new View[]{emojiGrid, emojiTabs, clipList, snipList, calcPad, editPad})
            if (v instanceof PadView) ((PadView) v).setTheme(theme);
        emojiGrid.setTheme(theme);
        clipList.setTheme(theme);
        snipList.setTheme(theme);
        ViewGroup.LayoutParams lp = content.getLayoutParams();
        int hgt = keyboardHeight();
        if (lp.height != hgt) { lp.height = hgt; content.setLayoutParams(lp); }
        root.refresh();
        styleWindow();
    }

    private void styleWindow() {
        Window w = getWindow() != null ? getWindow().getWindow() : null;
        if (w == null) return;
        w.setNavigationBarColor(theme.base);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int flags = w.getDecorView().getSystemUiVisibility();
            if (theme.dark) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            else flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            w.getDecorView().setSystemUiVisibility(flags);
        }
        boolean want = prefs.liveBlur() && Build.VERSION.SDK_INT >= 31;
        boolean active = false;
        if (want) {
            android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
            View decor = w.getDecorView();
            // Only blur when our window is no taller than the keyboard, so the app above never gets blurred.
            boolean fits = decor.getHeight() > 0 && root.getHeight() > 0 && decor.getHeight() <= root.getHeight() + 64 * dp;
            if (wm.isCrossWindowBlurEnabled() && fits) {
                w.setBackgroundDrawable(new ColorDrawable(0));
                w.setBackgroundBlurRadius((int) (28 * dp));
                active = true;
            } else if (!fits && decor.getHeight() > 0) {
                prefs.setBool("liveblurUnsupported", true);
            }
        } else if (Build.VERSION.SDK_INT >= 31) {
            w.setBackgroundBlurRadius(0);
        }
        if (active != liveBlurActive) { liveBlurActive = active; root.refresh(); }
    }

    @Override
    public void onWindowShown() {
        super.onWindowShown();
        if (root != null) root.post(this::styleWindow);
    }

    // ================================================================= input lifecycle

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        dict.reloadLearned();
        applySettings();
        composing.setLength(0);
        prevWord = null;
        lastCorrOriginal = null;
        toolbarForced = false;
        selectMode = false;
        if (!restarting) showPanel(P_NONE);

        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        int var = info.inputType & InputType.TYPE_MASK_VARIATION;
        isPassword = cls == InputType.TYPE_CLASS_TEXT && (var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                || cls == InputType.TYPE_CLASS_NUMBER && var == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        boolean urlOrEmail = cls == InputType.TYPE_CLASS_TEXT && (var == InputType.TYPE_TEXT_VARIATION_URI
                || var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS);
        noSuggest = isPassword || !prefs.suggestions() || cls != InputType.TYPE_CLASS_TEXT;
        noAutoCorrect = noSuggest || urlOrEmail || !prefs.autoCorrect() || var == InputType.TYPE_TEXT_VARIATION_FILTER
                || (info.inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0;
        noAutoCaps = urlOrEmail || isPassword || !prefs.autoCaps();
        noLearn = isPassword || !prefs.learnWords()
                || (info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0;

        if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME)
            keyboard.setPage(Layouts.SYM1);
        else keyboard.setPage(Layouts.ALPHA);

        keyboard.setSpaceLabel(isPassword ? "Private" : (noLearn ? "Incognito" : "English"));
        configureEnter(info);
        keyboard.setShift(KeyboardView.SHIFT_OFF);
        updateShift();
        updateStrip();
    }

    @Override
    public void onFinishInputView(boolean finishing) {
        super.onFinishInputView(finishing);
        stopVoice();
        dict.flush();
        composing.setLength(0);
    }

    private void configureEnter(EditorInfo info) {
        int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean multiLine = (info.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        if ((info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 || multiLine && action != EditorInfo.IME_ACTION_SEND) {
            keyboard.setEnter(GlassPainter.IC_ENTER, null);
            return;
        }
        switch (action) {
            case EditorInfo.IME_ACTION_SEARCH: keyboard.setEnter(GlassPainter.IC_SEARCH, null); break;
            case EditorInfo.IME_ACTION_SEND: keyboard.setEnter(GlassPainter.IC_SEND, null); break;
            case EditorInfo.IME_ACTION_GO: keyboard.setEnter(0, "Go"); break;
            case EditorInfo.IME_ACTION_NEXT: keyboard.setEnter(GlassPainter.IC_ARROW_RIGHT, null); break;
            case EditorInfo.IME_ACTION_DONE: keyboard.setEnter(GlassPainter.IC_CHECK, null); break;
            default: keyboard.setEnter(GlassPainter.IC_ENTER, null);
        }
    }

    private long lastEditTime;
    private boolean autoSpaced;

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        // A cursor change we didn't just cause = the user tapped elsewhere in the text.
        boolean ours = SystemClock.uptimeMillis() - lastEditTime < 350;
        boolean movedInsideWord = candidatesEnd != -1 && (newSelStart != newSelEnd || newSelStart != candidatesEnd);
        if (!ours || movedInsideWord) {
            if (composing.length() > 0) {
                composing.setLength(0);
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) ic.finishComposingText();
            }
            if (!ours) { prevWord = null; lastCorrOriginal = null; autoSpaced = false; }
        }
        if (keyboard == null) return;
        updateShift();
        updateStrip();
    }

    // ================================================================= typing

    private boolean isWordChar(String s) {
        if (s.length() == 0) return false;
        int cp = s.codePointAt(0);
        return Character.isLetter(cp) || (s.equals("'") && composing.length() > 0) || (Character.isDigit(cp) && composing.length() > 0);
    }

    @Override
    public void onText(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        lastEditTime = SystemClock.uptimeMillis();
        toolbarForced = false;
        boolean wasAutoSpaced = autoSpaced;
        autoSpaced = false;
        if (!noSuggest && isWordChar(s)) {
            lastCorrOriginal = null;
            composing.append(s);
            ic.setComposingText(composing, 1);
            updateStrip();
            return;
        }
        // Separator / symbol: finish the current word first (with autocorrect for punctuation).
        boolean sentencePunct = s.equals(".") || s.equals(",") || s.equals("!") || s.equals("?") || s.equals(";") || s.equals(":");
        ic.beginBatchEdit();
        if (composing.length() > 0) finishWord(ic, sentencePunct && !noAutoCorrect, null);
        if (sentencePunct) {
            // "word ." -> "word." : remove a space we auto-inserted before punctuation
            CharSequence before = ic.getTextBeforeCursor(2, 0);
            if (wasAutoSpaced && before != null && before.length() == 2 && before.charAt(1) == ' '
                    && Character.isLetterOrDigit(before.charAt(0))) {
                // the space came from tapping a suggestion: "word ." becomes "word. "
                ic.deleteSurroundingText(1, 0);
                ic.commitText(s + " ", 1);
            } else ic.commitText(s, 1);
        } else {
            ic.commitText(s, 1);
        }
        ic.endBatchEdit();
        lastCorrOriginal = null;
        if (s.codePointCount(0, s.length()) >= 1 && isEmoji(s)) prefs.pushRecentEmoji(s);
        updateShift();
        updateStrip();
    }

    private boolean isEmoji(String s) {
        int cp = s.codePointAt(0);
        return cp >= 0x1F000 || (cp >= 0x2600 && cp <= 0x27BF) || (cp >= 0x2190 && cp <= 0x21FF && s.length() > 1)
                || (s.length() > 1 && s.codePointCount(0, s.length()) > 1 && !Character.isLetter(cp));
    }

    /** Commits the word being typed, optionally autocorrected. Returns the committed word. */
    private String finishWord(InputConnection ic, boolean correct, String forced) {
        String typed = composing.toString();
        String out = typed;
        if (forced != null) out = forced;
        else if (correct) {
            String fix = dict.autocorrect(typed, prevWord);
            if (fix != null) out = fix;
        }
        ic.setComposingText(out, 1);
        ic.finishComposingText();
        composing.setLength(0);
        if (!out.equals(typed) && forced == null) { lastCorrOriginal = typed; lastCorrReplacement = out; }
        else lastCorrOriginal = null;
        if (!noLearn) dict.learn(out.contains(" ") ? out.substring(out.lastIndexOf(' ') + 1) : out, prevWord);
        prevWord = out;
        return out;
    }

    @Override
    public void onKey(int code) {
        InputConnection ic = getCurrentInputConnection();
        lastEditTime = SystemClock.uptimeMillis();
        if (code != Key.SHIFT) autoSpaced = false;
        switch (code) {
            case Key.DELETE: handleDelete(ic); break;
            case Key.SPACE: handleSpace(ic); break;
            case Key.ENTER: handleEnter(ic); break;
            case Key.SYMBOLS: commitComposing(); keyboard.setPage(Layouts.SYM1); break;
            case Key.SYMBOLS2: keyboard.setPage(Layouts.SYM2); break;
            case Key.ALPHA: keyboard.setPage(Layouts.ALPHA); updateShift(); break;
            case Key.EMOJI: commitComposing(); showPanel(P_EMOJI); break;
        }
    }

    private void commitComposing() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && composing.length() > 0) finishWord(ic, false, null);
    }

    private void handleSpace(InputConnection ic) {
        if (ic == null) return;
        toolbarForced = false;
        ic.beginBatchEdit();
        long now = SystemClock.uptimeMillis();
        // Quick-text shortcut expansion, e.g. ";upi" + space
        if (snippetForToken() != null) {
            if (composing.length() > 0) finishWord(ic, false, null);
            expandSnippet(ic);
            ic.endBatchEdit();
            lastSpaceTime = 0;
            updateShift();
            updateStrip();
            return;
        }
        if (composing.length() > 0) {
            finishWord(ic, !noAutoCorrect, null);
            ic.commitText(" ", 1);
        } else {
            CharSequence before = ic.getTextBeforeCursor(2, 0);
            if (prefs.doubleSpacePeriod() && now - lastSpaceTime < 1200 && before != null && before.length() == 2
                    && before.charAt(1) == ' ' && Character.isLetterOrDigit(before.charAt(0))) {
                ic.deleteSurroundingText(1, 0);
                ic.commitText(". ", 1);
                prevWord = null;
            } else {
                ic.commitText(" ", 1);
            }
        }
        ic.endBatchEdit();
        lastSpaceTime = now;
        updateShift();
        updateStrip();
    }

    private boolean expandSnippet(InputConnection ic) {
        CharSequence before = ic.getTextBeforeCursor(24, 0);
        if (before == null) return false;
        String t = before.toString();
        int sp = Math.max(t.lastIndexOf(' '), t.lastIndexOf('\n'));
        String token = t.substring(sp + 1);
        if (token.length() < 2) return false;
        for (Prefs.Snippet s : prefs.snippets()) {
            if (!s.shortcut.isEmpty() && s.shortcut.equalsIgnoreCase(token)) {
                ic.deleteSurroundingText(token.length(), 0);
                ic.commitText(s.text, 1);
                return true;
            }
        }
        return false;
    }

    private Prefs.Snippet snippetForToken() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return null;
        CharSequence before = ic.getTextBeforeCursor(24, 0);
        if (before == null) return null;
        String t = before.toString() + composing;
        int sp = Math.max(t.lastIndexOf(' '), t.lastIndexOf('\n'));
        String token = t.substring(sp + 1);
        if (token.length() < 2) return null;
        for (Prefs.Snippet s : prefs.snippets())
            if (!s.shortcut.isEmpty() && s.shortcut.equalsIgnoreCase(token)) return s;
        return null;
    }

    private void handleDelete(InputConnection ic) {
        if (ic == null) return;
        toolbarForced = false;
        if (composing.length() > 0) {
            int cp = composing.codePointBefore(composing.length());
            composing.setLength(composing.length() - Character.charCount(cp));
            if (composing.length() == 0) ic.commitText("", 1);
            else ic.setComposingText(composing, 1);
            updateStrip();
            return;
        }
        // Backspace right after an autocorrection restores what you actually typed.
        if (lastCorrOriginal != null) {
            CharSequence before = ic.getTextBeforeCursor(lastCorrReplacement.length() + 1, 0);
            if (before != null && before.toString().equals(lastCorrReplacement + " ")) {
                ic.deleteSurroundingText(before.length(), 0);
                ic.commitText(lastCorrOriginal, 1);
                lastCorrOriginal = null;
                updateStrip();
                return;
            }
            lastCorrOriginal = null;
        }
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) ic.commitText("", 1);
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        updateShift();
        updateStrip();
    }

    private void handleEnter(InputConnection ic) {
        if (ic == null) return;
        commitComposing();
        EditorInfo info = getCurrentInputEditorInfo();
        int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean multiLine = (info.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        if ((info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0 && action != EditorInfo.IME_ACTION_NONE
                && action != EditorInfo.IME_ACTION_UNSPECIFIED && !(multiLine && action != EditorInfo.IME_ACTION_SEND)) {
            ic.performEditorAction(action);
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        }
        prevWord = null;
        updateShift();
        updateStrip();
    }

    @Override
    public void onCursorMove(int steps) {
        lastEditTime = SystemClock.uptimeMillis();
        commitComposing();
        int code = steps < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
        for (int i = 0; i < Math.abs(steps); i++) sendDownUpKeyEvents(code);
    }

    @Override
    public void onDeleteWords(int count) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        lastEditTime = SystemClock.uptimeMillis();
        if (composing.length() > 0) { composing.setLength(0); ic.commitText("", 1); count--; }
        if (count <= 0) { updateStrip(); return; }
        CharSequence before = ic.getTextBeforeCursor(400, 0);
        if (before == null) return;
        String t = before.toString();
        int i = t.length();
        for (int n = 0; n < count && i > 0; n++) {
            while (i > 0 && !Character.isLetterOrDigit(t.charAt(i - 1))) i--;
            while (i > 0 && Character.isLetterOrDigit(t.charAt(i - 1))) i--;
        }
        ic.deleteSurroundingText(t.length() - i, 0);
        prevWord = null;
        updateShift();
        updateStrip();
    }

    @Override
    public void onSpaceLongPress() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showInputMethodPicker();
    }

    @Override
    public void onOneHandedSwap() {
        prefs.setOneHanded(prefs.oneHanded() == 1 ? 2 : 1);
        applySettings();
    }

    @Override
    public void onOneHandedExit() {
        prefs.setOneHanded(0);
        applySettings();
    }

    private void updateShift() {
        if (keyboard == null) return;
        if (keyboard.page() != Layouts.ALPHA || keyboard.shiftState() == KeyboardView.SHIFT_LOCK) return;
        InputConnection ic = getCurrentInputConnection();
        EditorInfo info = getCurrentInputEditorInfo();
        if (ic == null || info == null || noAutoCaps || composing.length() > 0) return;
        int caps = ic.getCursorCapsMode(info.inputType);
        keyboard.setShift(caps != 0 ? KeyboardView.SHIFT_ON : KeyboardView.SHIFT_OFF);
    }

    // ================================================================= feedback

    @Override
    public void feedback(boolean strong) {
        if (prefs.haptics() && vibrator != null && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= 29)
                    vibrator.vibrate(VibrationEffect.createPredefined(strong ? VibrationEffect.EFFECT_HEAVY_CLICK : VibrationEffect.EFFECT_TICK));
                else vibrator.vibrate(VibrationEffect.createOneShot(strong ? 25 : 12, VibrationEffect.DEFAULT_AMPLITUDE));
            } catch (Exception ignored) { }
        }
        if (prefs.sound() && audio != null) audio.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.4f);
    }

    // ================================================================= strip

    private void updateStrip() {
        if (strip == null) return;
        List<StripView.Cell> cells = new ArrayList<>();
        if (listening) {
            StripView.Cell mic = StripView.Cell.icon(GlassPainter.IC_MIC, this::stopVoice);
            mic.active = true;
            cells.add(mic);
            cells.add(StripView.Cell.title(partialVoice.isEmpty() ? "Listening… (" + prefs.voiceLang() + ")" : partialVoice));
            cells.add(StripView.Cell.icon(GlassPainter.IC_CLOSE, this::stopVoice));
            strip.setCells(cells);
            return;
        }
        if (panel != P_NONE) { panelHeader(cells); strip.setCells(cells); return; }

        InputConnection ic = getCurrentInputConnection();
        CharSequence before = ic != null ? ic.getTextBeforeCursor(60, 0) : null;

        // 1. Inline maths: "250*12=" -> tap to insert 3000
        String math = Calc.inlineResult(before == null ? null : before.toString() + composing);
        if (math != null) {
            cells.add(StripView.Cell.icon(GlassPainter.IC_GRID, this::forceToolbar));
            final String res = math;
            cells.add(StripView.Cell.chip(GlassPainter.IC_CALC, "= " + Calc.format(Double.parseDouble(res), true), () -> {
                commitComposing();
                InputConnection c2 = getCurrentInputConnection();
                if (c2 != null) c2.commitText(res, 1);
                updateStrip();
            }));
            strip.setCells(cells);
            return;
        }

        // 2. Quick-text shortcut typed -> chip to expand
        Prefs.Snippet sn = snippetForToken();
        if (sn != null) {
            cells.add(StripView.Cell.icon(GlassPainter.IC_GRID, this::forceToolbar));
            cells.add(StripView.Cell.chip(GlassPainter.IC_SNIPPET, sn.title + " — tap or press space", () -> {
                commitComposing();
                InputConnection c2 = getCurrentInputConnection();
                if (c2 != null) expandSnippet(c2);
                updateStrip();
            }));
            strip.setCells(cells);
            return;
        }

        // 3. Recently copied text -> paste chip (like Gboard, but we keep history forever)
        boolean recentClip = lastClipText != null && SystemClock.uptimeMillis() - lastClipTime < 60000 && !isPassword;
        if (composing.length() == 0 && recentClip && !toolbarForced) {
            cells.add(StripView.Cell.icon(GlassPainter.IC_GRID, this::forceToolbar));
            final String clip = lastClipText;
            cells.add(StripView.Cell.chip(GlassPainter.IC_PASTE, clip.replace('\n', ' '), () -> {
                InputConnection c2 = getCurrentInputConnection();
                if (c2 != null) c2.commitText(clip, 1);
                lastClipText = null;
                updateStrip();
            }));
            cells.add(StripView.Cell.icon(GlassPainter.IC_CLOSE, () -> { lastClipText = null; updateStrip(); }));
            strip.setCells(cells);
            return;
        }

        // 4. Word suggestions while typing
        if (!noSuggest && composing.length() > 0 && !toolbarForced) {
            String typed = composing.toString();
            List<String> sug = dict.suggest(typed, prevWord);
            cells.add(StripView.Cell.icon(GlassPainter.IC_GRID, this::forceToolbar));
            String best = sug.isEmpty() ? typed : sug.get(0);
            boolean willCorrect = !noAutoCorrect && dict.autocorrect(typed, prevWord) != null;
            List<String> order = new ArrayList<>();
            List<Boolean> primary = new ArrayList<>();
            if (willCorrect) {
                order.add("“" + typed + "”"); primary.add(false);
                order.add(best); primary.add(true);
                for (String s : sug) if (order.size() < 3 && !s.equals(best)) { order.add(s); primary.add(false); }
            } else {
                String second = null;
                for (String s : sug) if (!s.equals(typed)) { second = s; break; }
                if (second != null) { order.add(second); primary.add(false); }
                order.add(typed); primary.add(true);
                for (String s : sug) if (order.size() < 3 && !s.equals(typed) && !s.equals(second)) { order.add(s); primary.add(false); }
            }
            for (int i = 0; i < order.size(); i++) {
                String label = order.get(i);
                final String word = label.startsWith("“") ? typed : label;
                cells.add(StripView.Cell.word(label, primary.get(i), () -> pickSuggestion(word)));
            }
            strip.setCells(cells);
            return;
        }

        // 5. Next-word predictions after a space
        if (!noSuggest && composing.length() == 0 && prevWord != null && !toolbarForced
                && before != null && before.length() > 0 && before.charAt(before.length() - 1) == ' ') {
            List<String> pred = dict.predict(prevWord);
            if (!pred.isEmpty()) {
                cells.add(StripView.Cell.icon(GlassPainter.IC_GRID, this::forceToolbar));
                for (int i = 0; i < pred.size(); i++) {
                    final String w = pred.get(i);
                    cells.add(StripView.Cell.word(w, i == 0, () -> pickPrediction(w)));
                }
                strip.setCells(cells);
                return;
            }
        }

        // 6. Toolbar
        cells.add(StripView.Cell.icon(GlassPainter.IC_CLIP, () -> showPanel(P_CLIP)));
        cells.add(StripView.Cell.icon(GlassPainter.IC_SNIPPET, () -> showPanel(P_SNIP)));
        cells.add(StripView.Cell.icon(GlassPainter.IC_CALC, () -> showPanel(P_CALC)));
        cells.add(StripView.Cell.icon(GlassPainter.IC_EDIT, () -> showPanel(P_EDIT)));
        cells.add(StripView.Cell.icon(GlassPainter.IC_ONEHAND, () -> {
            prefs.setOneHanded(prefs.oneHanded() == 0 ? 2 : 0);
            applySettings();
        }));
        cells.add(StripView.Cell.icon(GlassPainter.IC_MIC, this::startVoice));
        cells.add(StripView.Cell.icon(GlassPainter.IC_GEAR, () -> openSettings(null)));
        cells.add(StripView.Cell.icon(GlassPainter.IC_HIDE, this::requestHideSelf0));
        strip.setCells(cells);
    }

    private void requestHideSelf0() { requestHideSelf(0); }

    private void forceToolbar() {
        toolbarForced = true;
        updateStrip();
    }

    private void pickSuggestion(String word) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        finishWord(ic, false, word);
        lastCorrOriginal = null;
        ic.commitText(" ", 1);
        ic.endBatchEdit();
        lastEditTime = lastSpaceTime = SystemClock.uptimeMillis();
        autoSpaced = true;
        updateShift();
        updateStrip();
    }

    private void pickPrediction(String word) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.commitText(word + " ", 1);
        if (!noLearn) dict.learn(word, prevWord);
        prevWord = word;
        lastEditTime = lastSpaceTime = SystemClock.uptimeMillis();
        autoSpaced = true;
        updateShift();
        updateStrip();
    }

    // ================================================================= panels

    private void buildPanels() {
        // --- Emoji
        emojiPanel = new LinearLayout(this);
        emojiPanel.setOrientation(LinearLayout.VERTICAL);
        emojiScroll = new ScrollView(this);
        emojiScroll.setVerticalScrollBarEnabled(false);
        emojiGrid = new EmojiGrid(this, gp);
        emojiGrid.feedback = () -> feedback(false);
        emojiGrid.setOnPick(e -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.commitText(e, 1);
            prefs.pushRecentEmoji(e);
        });
        emojiScroll.addView(emojiGrid);
        emojiPanel.addView(emojiScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        emojiTabs = new PadView(this, gp);
        emojiTabs.setEmojiLabels(true);
        emojiTabs.setTextScale(0.5f);
        emojiTabs.feedback = () -> feedback(false);
        List<List<PadView.Btn>> tabRows = new ArrayList<>();
        List<PadView.Btn> tabs = new ArrayList<>();
        tabs.add(new PadView.Btn("abc", null, GlassPainter.IC_KEYBOARD).weight(1.3f));
        for (int i = 0; i < Emojis.TABS.length; i++) tabs.add(new PadView.Btn("t" + i, Emojis.TABS[i], 0));
        tabs.add(new PadView.Btn("del", null, GlassPainter.IC_DEL).weight(1.3f).repeating());
        tabRows.add(tabs);
        emojiTabs.setRows(tabRows);
        emojiTabs.setOnPress(b -> {
            if (b.id.equals("abc")) showPanel(P_NONE);
            else if (b.id.equals("del")) handleDelete(getCurrentInputConnection());
            else showEmojiTab(Integer.parseInt(b.id.substring(1)));
        });
        emojiPanel.addView(emojiTabs, new LinearLayout.LayoutParams(-1, (int) (44 * dp)));

        // --- Clipboard
        clipScroll = new ScrollView(this);
        clipScroll.setVerticalScrollBarEnabled(false);
        clipList = new CardList(this, gp);
        clipList.feedback = () -> feedback(false);
        clipScroll.addView(clipList);
        clipList.setOnCard(new CardList.OnCard() {
            @Override public void tap(CardList.Card c) {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) { commitComposing(); ic.commitText(c.body, 1); }
            }
            @Override public void icon(CardList.Card c, int i) {
                List<Prefs.Clip> clips = prefs.clips();
                int idx = (Integer) c.tag;
                if (idx >= clips.size()) return;
                if (i == 0) clips.get(idx).pinned = !clips.get(idx).pinned;
                else if (i == 1) {
                    List<Prefs.Snippet> sn = prefs.snippets();
                    String body = clips.get(idx).text;
                    String title = body.length() > 24 ? body.substring(0, 24) + "…" : body;
                    sn.add(0, new Prefs.Snippet(title.replace('\n', ' '), body, ""));
                    prefs.saveSnippets(sn);
                    toast("Saved to Quick text");
                } else clips.remove(idx);
                prefs.saveClips(clips);
                refreshClips();
            }
        });

        // --- Snippets
        snipScroll = new ScrollView(this);
        snipScroll.setVerticalScrollBarEnabled(false);
        snipList = new CardList(this, gp);
        snipList.feedback = () -> feedback(false);
        snipScroll.addView(snipList);
        snipList.setOnCard(new CardList.OnCard() {
            @Override public void tap(CardList.Card c) {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) { commitComposing(); ic.commitText(c.body, 1); }
            }
            @Override public void icon(CardList.Card c, int i) { }
        });

        // --- Calculator
        calcPanel = new LinearLayout(this);
        calcPanel.setOrientation(LinearLayout.VERTICAL);
        calcDisplay = new View(this) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                android.graphics.RectF r = new android.graphics.RectF(6 * dp, 4 * dp, getWidth() - 6 * dp, getHeight() - 2 * dp);
                gp.drawGlass(c, r, 12 * dp, GlassPainter.STYLE_FUNC, false, theme);
                p.setTextAlign(Paint.Align.RIGHT);
                p.setColor(theme.subText);
                p.setTextSize(15 * dp);
                String e = calcExpr.isEmpty() ? "0" : calcExpr;
                String shown = TextUtils.ellipsize(e, new android.text.TextPaint(p), r.width() - 24 * dp, TextUtils.TruncateAt.START).toString();
                c.drawText(shown, r.right - 12 * dp, r.top + 20 * dp, p);
                Double v = Calc.eval(calcExpr);
                p.setColor(theme.text);
                p.setTextSize(24 * dp);
                p.setFakeBoldText(true);
                c.drawText(v == null ? (calcExpr.isEmpty() ? "0" : "…") : "= " + Calc.format(v, true), r.right - 12 * dp, r.bottom - 10 * dp, p);
                p.setFakeBoldText(false);
            }
        };
        calcDisplay.setWillNotDraw(false);
        calcPanel.addView(calcDisplay, new LinearLayout.LayoutParams(-1, 0, 1.3f));
        calcPad = new PadView(this, gp);
        calcPad.feedback = () -> feedback(false);
        calcPad.setRows(calcRows());
        calcPad.setOnPress(this::calcPress);
        calcPanel.addView(calcPad, new LinearLayout.LayoutParams(-1, 0, 5f));

        // --- Text editing
        editPad = new PadView(this, gp);
        editPad.feedback = () -> feedback(false);
        editPad.setRows(editRows());
        editPad.setOnPress(this::editPress);

        for (View v : new View[]{emojiPanel, clipScroll, snipScroll, calcPanel, editPad}) {
            v.setVisibility(View.GONE);
            content.addView(v, new FrameLayout.LayoutParams(-1, -1));
        }
    }

    private void showEmojiTab(int i) {
        emojiTab = i;
        List<String> list = Emojis.tab(i, prefs);
        emojiGrid.setItems(list, i == 0 ? "Emoji you use will show up here" : "");
        emojiScroll.scrollTo(0, 0);
        for (PadView.Btn b : emojiTabs.rows().get(0)) b.selected = b.id.equals("t" + i);
        emojiTabs.invalidate();
    }

    private void refreshClips() {
        List<Prefs.Clip> clips = prefs.clips();
        // pinned first
        List<CardList.Card> cards = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) for (int i = 0; i < clips.size(); i++) {
            Prefs.Clip c = clips.get(i);
            if (c.pinned != (pass == 0)) continue;
            CardList.Card card = new CardList.Card();
            card.body = c.text;
            card.icons = new int[]{c.pinned ? GlassPainter.IC_PIN_ON : GlassPainter.IC_PIN, GlassPainter.IC_SNIPPET, GlassPainter.IC_TRASH};
            card.tag = i;
            cards.add(card);
        }
        clipList.setCards(cards, "Copy something and it will appear here");
    }

    private void refreshSnippets() {
        List<CardList.Card> cards = new ArrayList<>();
        for (Prefs.Snippet s : prefs.snippets()) {
            CardList.Card card = new CardList.Card();
            card.title = s.title;
            card.body = s.text;
            card.badge = s.shortcut;
            cards.add(card);
        }
        snipList.setCards(cards, "No quick text yet — add some in settings");
    }

    private void showPanel(int p) {
        if (content == null) return;
        if (p != P_NONE) commitComposing();
        panel = p;
        keyboard.setVisibility(p == P_NONE ? View.VISIBLE : View.GONE);
        emojiPanel.setVisibility(p == P_EMOJI ? View.VISIBLE : View.GONE);
        clipScroll.setVisibility(p == P_CLIP ? View.VISIBLE : View.GONE);
        snipScroll.setVisibility(p == P_SNIP ? View.VISIBLE : View.GONE);
        calcPanel.setVisibility(p == P_CALC ? View.VISIBLE : View.GONE);
        editPad.setVisibility(p == P_EDIT ? View.VISIBLE : View.GONE);
        if (p == P_EMOJI) showEmojiTab(prefs.recentEmoji().isEmpty() ? 1 : 0);
        if (p == P_CLIP) { refreshClips(); clipScroll.scrollTo(0, 0); }
        if (p == P_SNIP) { refreshSnippets(); snipScroll.scrollTo(0, 0); }
        if (p == P_CALC) calcDisplay.invalidate();
        if (p == P_EDIT) { selectMode = false; editPad.setRows(editRows()); }
        if (p == P_NONE) updateShift();
        overlay.invalidate();
        updateStrip();
    }

    private void panelHeader(List<StripView.Cell> cells) {
        cells.add(StripView.Cell.icon(GlassPainter.IC_KEYBOARD, () -> showPanel(P_NONE)));
        switch (panel) {
            case P_EMOJI:
                cells.add(StripView.Cell.title("Emoji"));
                break;
            case P_CLIP:
                cells.add(StripView.Cell.title("Clipboard · pinned items never expire"));
                cells.add(StripView.Cell.icon(GlassPainter.IC_TRASH, () -> {
                    List<Prefs.Clip> keep = new ArrayList<>();
                    for (Prefs.Clip c : prefs.clips()) if (c.pinned) keep.add(c);
                    prefs.saveClips(keep);
                    refreshClips();
                    toast("Cleared (pinned kept)");
                }));
                break;
            case P_SNIP:
                cells.add(StripView.Cell.title("Quick text · type shortcut + space"));
                cells.add(StripView.Cell.icon(GlassPainter.IC_PLUS, () -> openSettings("snippets")));
                break;
            case P_CALC:
                Double v = Calc.eval(calcExpr);
                if (v != null) {
                    final String res = Calc.format(v, false);
                    cells.add(StripView.Cell.chip(GlassPainter.IC_ARROW_RIGHT, "Insert " + Calc.format(v, true), () -> {
                        InputConnection ic = getCurrentInputConnection();
                        if (ic != null) ic.commitText(res, 1);
                    }));
                    cells.add(StripView.Cell.chip(0, "Insert sum", () -> {
                        InputConnection ic = getCurrentInputConnection();
                        if (ic != null) ic.commitText(calcExpr + " = " + res, 1);
                    }));
                } else cells.add(StripView.Cell.title("Calculator · GST " + prefs.gstRate() + "%"));
                break;
            case P_EDIT:
                cells.add(StripView.Cell.title(countText()));
                break;
        }
    }

    private String countText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "Text editing";
        ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
        if (et == null || et.text == null) return "Text editing";
        String t = et.text.toString();
        String trimmed = t.trim();
        int words = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
        return words + " words · " + t.length() + " characters";
    }

    // ---------------- calculator

    private List<List<PadView.Btn>> calcRows() {
        int F = GlassPainter.STYLE_FUNC, A = GlassPainter.STYLE_ACTION;
        List<List<PadView.Btn>> r = new ArrayList<>();
        r.add(row(b("C", "C", F), b("(", "(", F), b(")", ")", F), b("%", "%", F), new PadView.Btn("bk", null, GlassPainter.IC_DEL).style(F).repeating()));
        r.add(row(b("7", "7", 0), b("8", "8", 0), b("9", "9", 0), b("÷", "÷", F), b("gst+", "+GST", F)));
        r.add(row(b("4", "4", 0), b("5", "5", 0), b("6", "6", 0), b("×", "×", F), b("gst-", "−GST", F)));
        r.add(row(b("1", "1", 0), b("2", "2", 0), b("3", "3", 0), b("−", "−", F), b("abc", "ABC", F)));
        r.add(row(b("0", "0", 0), b("00", "00", 0), b(".", ".", 0), b("+", "+", F), b("=", "=", A)));
        return r;
    }

    private static PadView.Btn b(String id, String label, int style) { return new PadView.Btn(id, label, 0).style(style); }

    private static List<PadView.Btn> row(PadView.Btn... bs) {
        List<PadView.Btn> l = new ArrayList<>();
        for (PadView.Btn x : bs) l.add(x);
        return l;
    }

    private void calcPress(PadView.Btn b) {
        double gst = prefs.gstRate() / 100.0;
        switch (b.id) {
            case "C": calcExpr = ""; break;
            case "bk": if (!calcExpr.isEmpty()) calcExpr = calcExpr.substring(0, calcExpr.length() - 1); break;
            case "abc": showPanel(P_NONE); return;
            case "=": {
                Double v = Calc.eval(calcExpr);
                if (v != null) calcExpr = Calc.format(v, false);
                break;
            }
            case "gst+": case "gst-": {
                Double v = Calc.eval(calcExpr);
                if (v != null) {
                    double res = b.id.equals("gst+") ? v * (1 + gst) : v / (1 + gst);
                    toast(b.id.equals("gst+") ? "Added " + prefs.gstRate() + "% GST: +" + Calc.format(res - v, true)
                            : "Removed " + prefs.gstRate() + "% GST: −" + Calc.format(v - res, true));
                    calcExpr = Calc.format(Math.round(res * 100) / 100.0, false);
                }
                break;
            }
            default: calcExpr += b.label;
        }
        calcDisplay.invalidate();
        updateStrip();
    }

    // ---------------- text editing

    private List<List<PadView.Btn>> editRows() {
        int F = GlassPainter.STYLE_FUNC;
        List<List<PadView.Btn>> r = new ArrayList<>();
        PadView.Btn sel = new PadView.Btn("sel", "Select", GlassPainter.IC_SELECT);
        sel.selected = selectMode;
        r.add(row(new PadView.Btn("home", "Start", GlassPainter.IC_LEFT).style(F), new PadView.Btn("up", null, GlassPainter.IC_UP).repeating(),
                new PadView.Btn("end", "End", GlassPainter.IC_RIGHT).style(F), new PadView.Btn("undo", "Undo", GlassPainter.IC_UNDO).style(F),
                new PadView.Btn("redo", "Redo", GlassPainter.IC_REDO).style(F)));
        r.add(row(new PadView.Btn("left", null, GlassPainter.IC_LEFT).repeating(), sel,
                new PadView.Btn("right", null, GlassPainter.IC_RIGHT).repeating(), new PadView.Btn("copy", "Copy", GlassPainter.IC_COPY).style(F),
                new PadView.Btn("cut", "Cut", GlassPainter.IC_CUT).style(F)));
        r.add(row(new PadView.Btn("all", "Select all", GlassPainter.IC_SELECT).style(F), new PadView.Btn("down", null, GlassPainter.IC_DOWN).repeating(),
                new PadView.Btn("del", "Delete", GlassPainter.IC_DEL).style(F).repeating(), new PadView.Btn("paste", "Paste", GlassPainter.IC_PASTE).style(F),
                new PadView.Btn("clip", "Clipboard", GlassPainter.IC_CLIP).style(F)));
        r.add(row(b("UP", "ABC", F), b("low", "abc", F), b("title", "Abc Title", F), b("sent", "Sentence.", F), b("kb", "Keyboard", F)));
        return r;
    }

    private void sendKey(int code, int meta) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        long t = SystemClock.uptimeMillis();
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta));
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta));
    }

    private void editPress(PadView.Btn b) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        int shiftMeta = selectMode ? KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON : 0;
        switch (b.id) {
            case "left": sendKey(KeyEvent.KEYCODE_DPAD_LEFT, shiftMeta); break;
            case "right": sendKey(KeyEvent.KEYCODE_DPAD_RIGHT, shiftMeta); break;
            case "up": sendKey(KeyEvent.KEYCODE_DPAD_UP, shiftMeta); break;
            case "down": sendKey(KeyEvent.KEYCODE_DPAD_DOWN, shiftMeta); break;
            case "home": sendKey(KeyEvent.KEYCODE_MOVE_HOME, shiftMeta); break;
            case "end": sendKey(KeyEvent.KEYCODE_MOVE_END, shiftMeta); break;
            case "sel": selectMode = !selectMode; editPad.setRows(editRows()); break;
            case "all": ic.performContextMenuAction(android.R.id.selectAll); break;
            case "copy": ic.performContextMenuAction(android.R.id.copy); break;
            case "cut": ic.performContextMenuAction(android.R.id.cut); break;
            case "paste": ic.performContextMenuAction(android.R.id.paste); break;
            case "undo": sendKey(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON); break;
            case "redo": sendKey(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_SHIFT_ON); break;
            case "del": handleDelete(ic); break;
            case "clip": showPanel(P_CLIP); return;
            case "kb": showPanel(P_NONE); return;
            case "UP": case "low": case "title": case "sent": convertCase(ic, b.id); break;
        }
        updateStrip();
    }

    private void convertCase(InputConnection ic, String mode) {
        CharSequence sel = ic.getSelectedText(0);
        String src;
        boolean fromSelection = sel != null && sel.length() > 0;
        if (fromSelection) src = sel.toString();
        else {
            CharSequence before = ic.getTextBeforeCursor(200, 0);
            if (before == null || before.length() == 0) { toast("Select some text first"); return; }
            String t = before.toString();
            int i = t.length();
            while (i > 0 && Character.isWhitespace(t.charAt(i - 1))) i--;
            int end = i;
            while (i > 0 && !Character.isWhitespace(t.charAt(i - 1))) i--;
            if (end == i) return;
            src = t.substring(i, end);
            ic.deleteSurroundingText(t.length() - i, 0);
            src = src + t.substring(end);
        }
        String out;
        switch (mode) {
            case "UP": out = src.toUpperCase(); break;
            case "low": out = src.toLowerCase(); break;
            case "title": {
                StringBuilder sb = new StringBuilder();
                boolean start = true;
                for (char ch : src.toLowerCase().toCharArray()) {
                    sb.append(start ? Character.toUpperCase(ch) : ch);
                    start = Character.isWhitespace(ch);
                }
                out = sb.toString();
                break;
            }
            default: {
                StringBuilder sb = new StringBuilder();
                boolean start = true;
                for (char ch : src.toLowerCase().toCharArray()) {
                    if (start && Character.isLetter(ch)) { sb.append(Character.toUpperCase(ch)); start = false; }
                    else sb.append(ch);
                    if (ch == '.' || ch == '!' || ch == '?') start = true;
                }
                out = sb.toString();
            }
        }
        ic.commitText(out, 1);
        if (fromSelection) {
            // keep it selected so you can try another case
            ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
            if (et != null) {
                int end = et.selectionStart + et.startOffset;
                ic.setSelection(end - out.length(), end);
            }
        }
    }

    // ================================================================= clipboard

    private void onClipChanged() {
        try {
            ClipData d = cm.getPrimaryClip();
            if (d == null || d.getItemCount() == 0) return;
            CharSequence t = d.getItemAt(0).coerceToText(this);
            if (t == null || t.length() == 0) return;
            String s = t.toString();
            prefs.addClip(s);
            lastClipText = s;
            lastClipTime = SystemClock.uptimeMillis();
            if (panel == P_CLIP) refreshClips();
            updateStrip();
        } catch (Exception ignored) { }
    }

    // ================================================================= voice

    private void startVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast("Allow microphone access for voice typing");
            openSettings("mic");
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("No speech service on this phone (install the Google app)");
            return;
        }
        commitComposing();
        stopVoice();
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle b) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float v) { }
            @Override public void onBufferReceived(byte[] bytes) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onError(int e) {
                listening = false;
                if (e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) toast("Didn't catch that");
                updateStrip();
            }
            @Override public void onResults(Bundle b) {
                List<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                listening = false;
                partialVoice = "";
                if (r != null && !r.isEmpty()) {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        String text = r.get(0);
                        CharSequence before = ic.getTextBeforeCursor(1, 0);
                        if (before != null && before.length() > 0 && !Character.isWhitespace(before.charAt(0))) text = " " + text;
                        if (keyboard.shiftState() != KeyboardView.SHIFT_OFF && text.length() > 0)
                            text = Character.toUpperCase(text.charAt(0)) + text.substring(1);
                        ic.commitText(text, 1);
                    }
                }
                updateStrip();
            }
            @Override public void onPartialResults(Bundle b) {
                List<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (r != null && !r.isEmpty()) { partialVoice = r.get(0); updateStrip(); }
            }
            @Override public void onEvent(int i, Bundle bundle) { }
        });
        Intent it = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        it.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        it.putExtra(RecognizerIntent.EXTRA_LANGUAGE, prefs.voiceLang());
        it.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        it.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        listening = true;
        partialVoice = "";
        updateStrip();
        recognizer.startListening(it);
    }

    private void stopVoice() {
        boolean was = listening;
        listening = false;
        if (recognizer != null) {
            try { recognizer.cancel(); recognizer.destroy(); } catch (Exception ignored) { }
            recognizer = null;
        }
        if (was) updateStrip();
    }

    // ================================================================= misc

    private void openSettings(String section) {
        Intent i = new Intent(this, SettingsActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (section != null) i.putExtra("section", section);
        startActivity(i);
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }
}
