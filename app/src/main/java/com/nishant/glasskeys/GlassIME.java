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

    private static final int P_NONE = 0, P_EMOJI = 1, P_CLIP = 2, P_SNIP = 3, P_CALC = 4, P_EDIT = 5, P_STATS = 6;

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
    private String prevWord2 = null;
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
        if (!prefs.bool("amoledMigrated", false)) {
            // one-time switch to the new dark AMOLED look
            prefs.setInt("bgmode", 3);
            prefs.setBool("darkglass", true);
            prefs.setBool("amoledMigrated", true);
        }
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
    private LiquidBackdrop liquid;
    private long lastNavTint;

    /** Root container that paints the living frosted-glass backdrop behind everything. */
    private class RootView extends FrameLayout {
        RootView(Context c) { super(c); setWillNotDraw(false); setClipChildren(false); }
        void refresh() {
            boolean shadow = prefs.pack() == 1;
            if (liquid != null) {
                liquid.setPhoto(loadCustomBackdrop(), shadow ? 0 : prefs.photoBlur());
                liquid.black = !shadow && prefs.bgMode() == 3;
                liquid.photoDim = shadow ? 8 : prefs.darkGlass() ? prefs.photoDim() : Math.min(prefs.photoDim(), 15);
            }
            gp.setPack(prefs.pack());
            gp.setAmoled(prefs.darkGlass());
            gp.clearSprites();
            invalidate();
        }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (keyboard != null) { keyboard.rootW = w; keyboard.rootH = h; }
        }
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
            boolean motion = prefs.glassRipple();
            liquid.draw(c, getWidth(), getHeight(), theme, liveBlurActive, motion);
            gp.setSource(liquid.source(), liquid.sourceScaleX(getWidth()), liquid.sourceScaleY(getHeight()));
            long now = SystemClock.uptimeMillis();
            if (now - lastNavTint > 900) { lastNavTint = now; tintNavBar(); }
            if (motion && (liquid.awake() || liquid.lightPower > 0.01f)) {
                postInvalidateOnAnimation();
                // keys refract the moving backdrop, so they redraw with it
                if (keyboard != null && keyboard.getVisibility() == View.VISIBLE) keyboard.invalidate();
                if (strip != null) strip.invalidate();
            }
        }
    }

    private void tintNavBar() {
        Window w = getWindow() != null ? getWindow().getWindow() : null;
        if (w != null && liquid != null) w.setNavigationBarColor(liquid.bottomColor(theme));
    }

    @Override
    public void onTouchPoint(float x, float y) {
        if (root == null || liquid == null) return;
        float nx = (x + keyboard.getLeft()) / Math.max(1, root.getWidth());
        float ny = (y + strip.getHeight()) / Math.max(1, root.getHeight());
        liquid.touch(nx, ny);
        root.invalidate();
        keyboard.invalidate();
    }

    private boolean liveBlurActive;

    private Bitmap cachedPhoto;
    private String cachedPhotoKey;

    /** The picture shown behind the glass: none (flowing colours), built-in bloom, or the user's photo. */
    private Bitmap loadCustomBackdrop() {
        if (prefs.pack() == 1) {
            if ("shadow".equals(cachedPhotoKey) && cachedPhoto != null) return cachedPhoto;
            try (java.io.InputStream in = getAssets().open("shadow.jpg")) { cachedPhoto = BitmapFactory.decodeStream(in); }
            catch (Exception e) { cachedPhoto = null; }
            cachedPhotoKey = "shadow";
            return cachedPhoto;
        }
        int mode = prefs.bgMode();
        if (mode == 0 || mode == 3) return null;
        File f = new File(getFilesDir(), "backdrop.jpg");
        String key = mode == 2 && f.exists() ? f.getAbsolutePath() + f.lastModified() : "bloom";
        if (key.equals(cachedPhotoKey) && cachedPhoto != null) return cachedPhoto;
        Bitmap b = null;
        try {
            if (mode == 2 && f.exists()) b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b == null) {
                try (java.io.InputStream in = getAssets().open("bloom.jpg")) { b = BitmapFactory.decodeStream(in); }
            }
        } catch (Exception e) { b = null; }
        cachedPhoto = b;
        cachedPhotoKey = key;
        return b;
    }

    @Override
    public View onCreateInputView() {
        theme = Theme.get(prefs.theme());
        liquid = new LiquidBackdrop(dp);
        root = new RootView(this);
        gp.setRoot(root);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);

        strip = new StripView(this, gp);
        strip.feedback = () -> feedback(false);
        column.addView(strip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (54 * dp)));

        content = new FrameLayout(this);
        content.setClipChildren(false);
        keyboard = new KeyboardView(this, gp);
        keyboard.setListener(this);
        keyboard.backdrop = liquid;
        keyboard.decoder = new GlideDecoder(dict);
        content.addView(keyboard, new FrameLayout.LayoutParams(-1, -1));
        column.addView(content, new LinearLayout.LayoutParams(-1, keyboardHeight()));

        root.addView(column, new FrameLayout.LayoutParams(-1, -2));

        overlay = new View(this) {
            @Override protected void onDraw(Canvas c) { keyboard.drawOverlay(c, strip.getHeight()); }
        };
        overlay.setWillNotDraw(false);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        keyboard.overlay = overlay;
        keyboard.overflowAbove = 54 * dp;

        buildPanels();
        applySettings();
        return root;
    }

    private int keyboardHeight() {
        int rows = prefs.numberRow() ? 5 : 4;
        return (int) ((rows * prefs.keyHeightDp() + 8) * dp);
    }

    private void applySettings() {
        theme = prefs.pack() == 1 ? Theme.SHADOW : Theme.get(prefs.theme());
        keyboard.configure(theme, prefs.numberRow(), prefs.oneHanded(), prefs.keyPopup(), prefs.glassRipple());
        keyboard.capsLabels = prefs.capsLabels();
        keyboard.glideEnabled = prefs.bool("glide", true);
        keyboard.trailEnabled = prefs.bool("trail", true);
        strip.setTheme(theme);
        for (View v : new View[]{emojiGrid, emojiTabs, clipList, snipList, calcPad, editPad})
            if (v instanceof PadView) ((PadView) v).setTheme(theme);
        emojiGrid.setTheme(theme);
        if (calcDisplay instanceof CalcDisplay) ((CalcDisplay) calcDisplay).setTheme(theme);
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
        w.setNavigationBarColor(liquid != null ? liquid.bottomColor(theme) : theme.base);
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
        prevWord = null; prevWord2 = null;
        lastCorrOriginal = null;
        toolbarForced = false;
        glideWord = false;
        glidePreview = null;
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

        keyboard.setSpaceLabel(isPassword ? "private" : (noLearn ? "incognito" : "space"));
        configureEnter(info);
        keyboard.setShift(KeyboardView.SHIFT_OFF);
        updateShift();
        updateStrip();
        if (!restarting && prefs.glassRipple()) keyboard.playEntrance();
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
            if (!ours) { prevWord = null; prevWord2 = null; lastCorrOriginal = null; autoSpaced = false; }
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

    // ================================================================= swipe typing

    private boolean glideWord;                 // composing text came from a swipe
    private List<String> glideAlts = new ArrayList<>();
    private List<String> glidePreview = null;

    private String caseForGlide(String w) {
        w = dict.form(w);
        int st = keyboard.shiftState();
        if (st == KeyboardView.SHIFT_LOCK) return w.toUpperCase();
        if (w.equals("i") || w.startsWith("i'")) return "I" + w.substring(1);
        if (st == KeyboardView.SHIFT_ON) return Character.toUpperCase(w.charAt(0)) + w.substring(1);
        return w;
    }

    @Override
    public void onGlidePreview(List<String> words) {
        glidePreview = words;
        updateStrip();
    }

    @Override
    public void onGlide(List<String> words) {
        glidePreview = null;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (words.isEmpty()) { updateStrip(); return; }
        lastEditTime = SystemClock.uptimeMillis();
        toolbarForced = false;
        ic.beginBatchEdit();
        if (composing.length() > 0) finishWord(ic, false, null);
        // swipe words get their own space, so you can swipe word after word
        CharSequence before = ic.getTextBeforeCursor(1, 0);
        if (before != null && before.length() > 0) {
            char ch = before.charAt(0);
            if (!Character.isWhitespace(ch) && "([{\"'/@#".indexOf(ch) < 0) ic.commitText(" ", 1);
        }
        String w = caseForGlide(words.get(0));
        glideAlts = new ArrayList<>();
        for (int i = 1; i < words.size(); i++) glideAlts.add(caseForGlide(words.get(i)));
        composing.setLength(0);
        composing.append(w);
        ic.setComposingText(composing, 1);
        ic.endBatchEdit();
        glideWord = true;
        lastCorrOriginal = null;
        autoSpaced = false;
        updateStrip();
    }

    /** Swap the swiped word for one of the alternatives. */
    private void pickGlideAlt(String alt) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || !glideWord) return;
        String old = composing.toString();
        glideAlts.remove(alt);
        glideAlts.add(0, old);
        composing.setLength(0);
        composing.append(alt);
        ic.setComposingText(composing, 1);
        lastEditTime = SystemClock.uptimeMillis();
        updateStrip();
    }

    @Override
    public void onText(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        lastEditTime = SystemClock.uptimeMillis();
        toolbarForced = false;
        boolean wasAutoSpaced = autoSpaced;
        autoSpaced = false;
        if (glideWord && composing.length() > 0) {
            ic.beginBatchEdit();
            finishWord(ic, false, null);
            glideWord = false;
            if (Character.isLetterOrDigit(s.codePointAt(0))) ic.commitText(" ", 1);
            ic.endBatchEdit();
        }
        glideWord = false;
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
        if (s.equals(".") || s.equals("!") || s.equals("?")) { prevWord = null; prevWord2 = null; }
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
        if (!noLearn) dict.learn(out.contains(" ") ? out.substring(out.lastIndexOf(' ') + 1) : out, prevWord, prevWord2);
        gainXp(out, glideWord);
        prevWord2 = prevWord;
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
            finishWord(ic, !noAutoCorrect && !glideWord, null);
            glideWord = false;
            ic.commitText(" ", 1);
        } else {
            CharSequence before = ic.getTextBeforeCursor(2, 0);
            if (prefs.doubleSpacePeriod() && now - lastSpaceTime < 1200 && before != null && before.length() == 2
                    && before.charAt(1) == ' ' && Character.isLetterOrDigit(before.charAt(0))) {
                ic.deleteSurroundingText(1, 0);
                ic.commitText(". ", 1);
                prevWord = null; prevWord2 = null;
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
        if (glideWord && composing.length() > 0) {
            glideWord = false;
            composing.setLength(0);
            ic.commitText("", 1);
            updateStrip();
            return;
        }
        glideWord = false;
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
        prevWord = null; prevWord2 = null;
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
        prevWord = null; prevWord2 = null;
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
            cells.add(StripView.Cell.icon(GlassPainter.IC_SPARKLE, this::forceToolbar));
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
            cells.add(StripView.Cell.icon(GlassPainter.IC_SPARKLE, this::forceToolbar));
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
            cells.add(StripView.Cell.icon(GlassPainter.IC_SPARKLE, this::forceToolbar));
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

        // 3b. Swipe typing: live guess while swiping, alternatives after
        if (keyboard.isGliding() && glidePreview != null) {
            cells.add(StripView.Cell.icon(GlassPainter.IC_SPARKLE, this::forceToolbar));
            if (glidePreview.isEmpty()) cells.add(StripView.Cell.title("…"));
            else {
                String g = caseForGlide(glidePreview.get(0));
                if (glidePreview.size() > 1) cells.add(StripView.Cell.word(caseForGlide(glidePreview.get(1)), false, null));
                cells.add(StripView.Cell.word(g, true, null));
                if (glidePreview.size() > 2) cells.add(StripView.Cell.word(caseForGlide(glidePreview.get(2)), false, null));
            }
            strip.setCells(cells);
            return;
        }
        if (glideWord && composing.length() > 0) {
            cells.add(StripView.Cell.icon(GlassPainter.IC_SPARKLE, this::forceToolbar));
            String cur = composing.toString();
            List<String> alts = glideAlts;
            if (alts.size() > 0) { final String a0 = alts.get(0); cells.add(StripView.Cell.word(a0, false, () -> pickGlideAlt(a0))); }
            cells.add(StripView.Cell.word(cur, true, () -> {
                InputConnection c2 = getCurrentInputConnection();
                if (c2 == null) return;
                finishWord(c2, false, null);
                glideWord = false;
                c2.commitText(" ", 1);
                autoSpaced = true;
                updateStrip();
            }));
            if (alts.size() > 1) { final String a1 = alts.get(1); cells.add(StripView.Cell.word(a1, false, () -> pickGlideAlt(a1))); }
            strip.setCells(cells);
            return;
        }

        // 4. Word suggestions while typing
        if (!noSuggest && composing.length() > 0 && !toolbarForced) {
            String typed = composing.toString();
            List<String> sug = dict.suggest(typed, prevWord);
            cells.add(StripView.Cell.icon(GlassPainter.IC_SPARKLE, this::forceToolbar));
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
        boolean atGap = before == null || before.length() == 0
                || Character.isWhitespace(before.charAt(before.length() - 1));
        if (!noSuggest && composing.length() == 0 && !toolbarForced && atGap) {
            List<String> pred = dict.predict(prevWord2, prevWord);
            if (!pred.isEmpty()) {
                cells.add(StripView.Cell.icon(GlassPainter.IC_SPARKLE, this::forceToolbar));
                for (int i = 0; i < pred.size(); i++) {
                    final String w = pred.get(i);
                    cells.add(StripView.Cell.word(w, i == 0, () -> pickPrediction(w)));
                }
                strip.setCells(cells);
                return;
            }
        }

        // 6. Toolbar
        if (levelUpUntil > SystemClock.uptimeMillis() && prefs.pack() == 1) {
            StripView.Cell lu = StripView.Cell.chip(GlassPainter.IC_SPARKLE, "LEVEL UP  ·  LV " + level(prefs.integer("xp", 0)), () -> showPanel(P_STATS));
            lu.active = true;
            cells.add(lu);
            strip.setCells(cells);
            return;
        }
        if (prefs.pack() == 1) {
            int xp = prefs.integer("xp", 0), lv = level(xp);
            StripView.Cell hud = new StripView.Cell();
            hud.text = "LV " + lv;
            hud.hud = (xp - totalXp(lv)) / (float) xpToNext(lv);
            hud.fixedW = 66 * dp;
            hud.action = () -> showPanel(P_STATS);
            cells.add(hud);
        }
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

    // ================================================================= typing XP (Shadow Realm HUD)

    private long levelUpUntil;

    private static int xpToNext(int lv) { return 20 + lv * 12; }

    private static int totalXp(int lv) {
        int t = 0;
        for (int i = 1; i < lv; i++) t += xpToNext(i);
        return t;
    }

    private static int level(int xp) {
        int lv = 1;
        while (xp >= totalXp(lv + 1) && lv < 999) lv++;
        return lv;
    }

    private static String rank(int lv) {
        if (lv >= 60) return "Legend";
        if (lv >= 40) return "Grandmaster";
        if (lv >= 25) return "Master";
        if (lv >= 12) return "Expert";
        if (lv >= 5) return "Adept";
        return "Novice";
    }

    private void gainXp(String word, boolean swiped) {
        if (word == null || word.isEmpty() || isPassword) return;
        int gain = 1 + (word.length() >= 6 ? 1 : 0) + (swiped ? 1 : 0);
        int before = prefs.integer("xp", 0);
        int after = before + gain;
        prefs.setInt("xp", after);
        prefs.setInt("statWords", prefs.integer("statWords", 0) + 1);
        if (swiped) prefs.setInt("statSwipes", prefs.integer("statSwipes", 0) + 1);
        String today = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(new java.util.Date());
        if (!today.equals(prefs.str("statDay", ""))) { prefs.setStr("statDay", today); prefs.setInt("statToday", 0); }
        prefs.setInt("statToday", prefs.integer("statToday", 0) + 1);
        if (level(after) > level(before) && prefs.pack() == 1) {
            levelUpUntil = SystemClock.uptimeMillis() + 2400;
            feedback(true);
            if (strip != null) strip.postDelayed(this::updateStrip, 2500);
        }
    }

    private void refreshStats() {
        int xp = prefs.integer("xp", 0), lv = level(xp);
        List<CardList.Card> cards = new ArrayList<>();
        CardList.Card a = new CardList.Card();
        a.title = "Level " + lv + "  ·  " + rank(lv);
        a.body = (xp - totalXp(lv)) + " / " + xpToNext(lv) + " XP to level " + (lv + 1) + "\nTotal XP " + xp;
        cards.add(a);
        CardList.Card b = new CardList.Card();
        b.title = "Words";
        b.body = prefs.integer("statToday", 0) + " today  ·  " + prefs.integer("statWords", 0) + " all time";
        cards.add(b);
        CardList.Card d = new CardList.Card();
        d.title = "Swiped words";
        d.body = prefs.integer("statSwipes", 0) + " (swipes earn bonus XP, long words too)";
        cards.add(d);
        clipList.setCards(cards, "");
    }

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
        if (!noLearn) dict.learn(word, prevWord, prevWord2);
        gainXp(word, false);
        prevWord2 = prevWord;
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
                if (panel == P_STATS) return;
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) { commitComposing(); ic.commitText(c.body, 1); }
            }
            @Override public void icon(CardList.Card c, int i) {
                if (panel == P_STATS || c.tag == null) return;
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
        CalcDisplay cd = new CalcDisplay(this, gp);
        cd.feedback = () -> feedback(false);
        cd.setModel(new CalcDisplay.Model() {
            @Override public String expr() { return calcExpr; }
            @Override public int cursor() { return Math.min(calcCursor, calcExpr.length()); }
            @Override public void setCursor(int c) { calcCursor = c; calcJustEvaluated = false; updateStrip(); }
            @Override public String history() { return calcHistory; }
            @Override public boolean evaluated() { return calcJustEvaluated; }
        });
        calcDisplay = cd;
        calcDisplay.setWillNotDraw(false);
        calcPanel.addView(calcDisplay, new LinearLayout.LayoutParams(-1, 0, 2.1f));
        calcPad = new PadView(this, gp);
        calcPad.feedback = () -> feedback(false);
        calcPad.setGaps(7, 6);
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
        clipScroll.setVisibility(p == P_CLIP || p == P_STATS ? View.VISIBLE : View.GONE);
        snipScroll.setVisibility(p == P_SNIP ? View.VISIBLE : View.GONE);
        calcPanel.setVisibility(p == P_CALC ? View.VISIBLE : View.GONE);
        editPad.setVisibility(p == P_EDIT ? View.VISIBLE : View.GONE);
        if (p == P_EMOJI) showEmojiTab(prefs.recentEmoji().isEmpty() ? 1 : 0);
        if (p == P_CLIP) { refreshClips(); clipScroll.scrollTo(0, 0); }
        if (p == P_STATS) { refreshStats(); clipScroll.scrollTo(0, 0); }
        if (p == P_SNIP) { refreshSnippets(); snipScroll.scrollTo(0, 0); }
        if (p == P_CALC) calcDisplay.invalidate();
        if (p == P_EDIT) { selectMode = false; editPad.setRows(editRows()); }
        if (p == P_NONE) updateShift();
        View shown = p == P_NONE ? keyboard : p == P_EMOJI ? emojiPanel : (p == P_CLIP || p == P_STATS) ? clipScroll
                : p == P_SNIP ? snipScroll : p == P_CALC ? calcPanel : editPad;
        if (prefs.glassRipple()) {
            shown.setAlpha(0f);
            shown.setTranslationY(18 * dp);
            shown.setScaleX(0.97f);
            shown.setScaleY(0.97f);
            shown.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(260)
                    .setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0.9f, 0.25f, 1.05f)).start();
        }
        overlay.invalidate();
        updateStrip();
    }

    private void panelHeader(List<StripView.Cell> cells) {
        cells.add(StripView.Cell.icon(GlassPainter.IC_KEYBOARD, () -> showPanel(P_NONE)));
        switch (panel) {
            case P_EMOJI:
                cells.add(StripView.Cell.title("Emoji"));
                break;
            case P_STATS:
                cells.add(StripView.Cell.title("Your typing level"));
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
            case P_CALC: {
                int rate = prefs.gstRate();
                cells.add(StripView.Cell.chip(0, "+GST", () -> calcGst(true)));
                cells.add(StripView.Cell.chip(0, "−GST", () -> calcGst(false)));
                StripView.Cell rc = StripView.Cell.chip(0, rate + "%", () -> {
                    int[] rates = {5, 12, 18, 28};
                    int cur = prefs.gstRate(), next = 18;
                    for (int i = 0; i < rates.length; i++) if (rates[i] == cur) next = rates[(i + 1) % rates.length];
                    prefs.setInt("gst", next);
                    updateStrip();
                });
                rc.weight = 0.7f;
                cells.add(rc);
                Double v = Calc.eval(calcExpr);
                if (v != null) {
                    final String res = Calc.format(v, false);
                    StripView.Cell ins = StripView.Cell.chip(GlassPainter.IC_CHECK, "Insert", () -> {
                        InputConnection ic = getCurrentInputConnection();
                        if (ic != null) ic.commitText(res, 1);
                    });
                    ins.active = true;
                    ins.longAction = () -> {
                        InputConnection ic = getCurrentInputConnection();
                        if (ic != null) ic.commitText(prettyExpr(calcExpr) + " = " + Calc.format(v, true), 1);
                        toast("Inserted the full sum");
                    };
                    cells.add(ins);
                }
                break;
            }
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

    private String calcHistory = "";
    private boolean calcJustEvaluated;

    private List<List<PadView.Btn>> calcRows() {
        int F = GlassPainter.STYLE_FUNC, O = GlassPainter.STYLE_ACTIVE, A = GlassPainter.STYLE_ACTION, K = GlassPainter.STYLE_KEY;
        List<List<PadView.Btn>> r = new ArrayList<>();
        r.add(row(b("C", "AC", F), b("()", "( )", F), b("%", "%", F), b("÷", "÷", O).big()));
        r.add(row(b("7", "7", K).big(), b("8", "8", K).big(), b("9", "9", K).big(), b("×", "×", O).big()));
        r.add(row(b("4", "4", K).big(), b("5", "5", K).big(), b("6", "6", K).big(), b("−", "−", O).big()));
        r.add(row(b("1", "1", K).big(), b("2", "2", K).big(), b("3", "3", K).big(), b("+", "+", O).big()));
        r.add(row(b("0", "0", K).big(), b(".", ".", K).big(), new PadView.Btn("bk", null, GlassPainter.IC_DEL).style(F).repeating(), b("=", "=", A).big()));
        return r;
    }

    /** Pretty display: Indian digit grouping and proper operator signs. */
    private static String prettyExpr(String e) {
        StringBuilder out = new StringBuilder();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[0-9]+(\\.[0-9]*)?").matcher(e);
        int last = 0;
        while (m.find()) {
            out.append(e, last, m.start());
            String num = m.group();
            int dot = num.indexOf('.');
            String ip = dot >= 0 ? num.substring(0, dot) : num;
            String fp = dot >= 0 ? num.substring(dot) : "";
            try { out.append(Calc.format(Double.parseDouble(ip), true)).append(fp); } catch (Exception x) { out.append(num); }
            last = m.end();
        }
        out.append(e.substring(last));
        return out.toString().replace("*", "×").replace("/", "÷").replace("-", "−")
                .replace("+", " + ").replace("−", " − ").replace("×", " × ").replace("÷", " ÷ ").replace("  ", " ").trim();
    }

    private static boolean isOp(char c) { return "+−×÷".indexOf(c) >= 0; }

    private void calcGst(boolean add) {
        Double v = Calc.eval(calcExpr);
        if (v == null) { toast("Type an amount first"); return; }
        double g = prefs.gstRate() / 100.0;
        double res = add ? v * (1 + g) : v / (1 + g);
        double tax = Math.abs(res - v);
        res = Math.round(res * 100) / 100.0;
        calcHistory = Calc.format(v, true) + (add ? " + " : " − ") + "GST " + prefs.gstRate() + "% (" + Calc.format(Math.round(tax * 100) / 100.0, true) + ")";
        calcExpr = Calc.format(res, false);
        calcCursor = calcExpr.length();
        calcJustEvaluated = true;
        feedback(false);
        calcDisplay.invalidate();
        updateStrip();
    }

    private int calcCursor = 0;

    /** Every key edits at the cursor, so you can fix the middle of a long sum. */
    private void calcPress(PadView.Btn b) {
        String e = calcExpr;
        int cur = Math.max(0, Math.min(e.length(), calcCursor));
        String before = e.substring(0, cur), after = e.substring(cur);
        char lastCh = before.isEmpty() ? 0 : before.charAt(before.length() - 1);
        boolean atEnd = after.isEmpty();
        switch (b.id) {
            case "C": calcExpr = ""; calcHistory = ""; calcJustEvaluated = false; calcCursor = 0; break;
            case "bk":
                if (calcJustEvaluated && atEnd) { calcExpr = ""; calcCursor = 0; calcJustEvaluated = false; }
                else if (!before.isEmpty()) {
                    // delete an operator with its neighbouring char logic kept simple: one char
                    before = before.substring(0, before.length() - 1);
                    calcExpr = before + after; calcCursor = before.length();
                }
                break;
            case "=": {
                Double v = Calc.eval(e);
                if (v != null && !calcJustEvaluated) {
                    calcHistory = prettyExpr(e) + " =";
                    calcExpr = Calc.format(v, false);
                    calcCursor = calcExpr.length();
                    calcJustEvaluated = true;
                }
                break;
            }
            case "()": {
                if (calcJustEvaluated && atEnd) { before = ""; after = ""; lastCh = 0; calcJustEvaluated = false; }
                int open = 0;
                for (char ch : before.toCharArray()) { if (ch == '(') open++; else if (ch == ')') open--; }
                boolean close = open > 0 && (Character.isDigit(lastCh) || lastCh == ')' || lastCh == '%');
                String ins = close ? ")" : ((Character.isDigit(lastCh) || lastCh == ')') ? "×(" : "(");
                before += ins;
                calcExpr = before + after; calcCursor = before.length();
                break;
            }
            case "+": case "−": case "×": case "÷":
                calcJustEvaluated = false;
                if (before.isEmpty()) {
                    if (b.id.equals("−")) { before = "−"; calcExpr = before + after; calcCursor = 1; }
                    break;
                }
                if (isOp(lastCh)) before = before.substring(0, before.length() - 1) + b.id;
                else if (lastCh != '(') before = before + b.id;
                // avoid two operators in a row when inserting in the middle
                if (!after.isEmpty() && isOp(after.charAt(0)) && isOp(before.charAt(before.length() - 1))) after = after.substring(1);
                calcExpr = before + after; calcCursor = before.length();
                break;
            case "%":
                calcJustEvaluated = false;
                if (Character.isDigit(lastCh) || lastCh == ')') { before += "%"; calcExpr = before + after; calcCursor = before.length(); }
                break;
            case ".": {
                if (calcJustEvaluated && atEnd) { before = ""; after = ""; calcJustEvaluated = false; }
                // is there already a point in the number around the cursor?
                int l = before.length() - 1;
                while (l >= 0 && Character.isDigit(before.charAt(l))) l--;
                boolean hasPoint = l >= 0 && before.charAt(l) == '.';
                int r = 0;
                while (r < after.length() && Character.isDigit(after.charAt(r))) r++;
                if (r < after.length() && after.charAt(r) == '.') hasPoint = true;
                if (hasPoint) break;
                before += (before.isEmpty() || !Character.isDigit(before.charAt(before.length() - 1))) ? "0." : ".";
                calcExpr = before + after; calcCursor = before.length();
                break;
            }
            default: // digits
                if (calcJustEvaluated && atEnd) { before = ""; after = ""; calcJustEvaluated = false; }
                if (before.endsWith(")") || before.endsWith("%")) before += "×";
                before += b.label;
                calcExpr = before + after; calcCursor = before.length();
        }
        ((CalcDisplay) calcDisplay).poke();
        updateStrip();
    }

    private static PadView.Btn b(String id, String label, int style) { return new PadView.Btn(id, label, 0).style(style); }

    private static List<PadView.Btn> row(PadView.Btn... bs) {
        List<PadView.Btn> l = new ArrayList<>();
        for (PadView.Btn x : bs) l.add(x);
        return l;
    }

    // ---------------- text editing

    private List<List<PadView.Btn>> editRows() {
        int F = GlassPainter.STYLE_FUNC, K = GlassPainter.STYLE_KEY;
        List<List<PadView.Btn>> r = new ArrayList<>();
        PadView.Btn sel = new PadView.Btn("sel", "Select", GlassPainter.IC_SELECT);
        sel.selected = selectMode;
        r.add(row(new PadView.Btn("home", "Line start", GlassPainter.IC_LEFT).style(F),
                new PadView.Btn("up", null, GlassPainter.IC_UP).style(K).repeating(),
                new PadView.Btn("end", "Line end", GlassPainter.IC_RIGHT).style(F),
                new PadView.Btn("undo", "Undo", GlassPainter.IC_UNDO).style(F),
                new PadView.Btn("redo", "Redo", GlassPainter.IC_REDO).style(F)));
        r.add(row(new PadView.Btn("left", null, GlassPainter.IC_LEFT).style(K).repeating(), sel,
                new PadView.Btn("right", null, GlassPainter.IC_RIGHT).style(K).repeating(),
                new PadView.Btn("copy", "Copy", GlassPainter.IC_COPY).style(F),
                new PadView.Btn("cut", "Cut", GlassPainter.IC_CUT).style(F)));
        r.add(row(new PadView.Btn("all", "All", GlassPainter.IC_SELECT).style(F),
                new PadView.Btn("down", null, GlassPainter.IC_DOWN).style(K).repeating(),
                new PadView.Btn("del", "Delete", GlassPainter.IC_DEL).style(F).repeating(),
                new PadView.Btn("paste", "Paste", GlassPainter.IC_PASTE).style(F),
                new PadView.Btn("clip", "History", GlassPainter.IC_CLIP).style(F)));
        r.add(row(b("UP", "ABC", F), b("low", "abc", F), b("title", "Abc", F), b("sent", "Abc.", F),
                new PadView.Btn("kb", "Done", GlassPainter.IC_KEYBOARD).style(GlassPainter.STYLE_ACTION)));
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
