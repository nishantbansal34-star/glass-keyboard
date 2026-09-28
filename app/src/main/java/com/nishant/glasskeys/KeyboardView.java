package com.nishant.glasskeys;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

/** The letter/symbol key area. Draws glass keys and handles all touch gestures. */
public class KeyboardView extends View {

    public interface Listener {
        void onText(String text);
        void onKey(int code);
        void onCursorMove(int steps);
        void onDeleteWords(int count);
        void onSpaceLongPress();
        void onOneHandedSwap();
        void onOneHandedExit();
        void feedback(boolean strong);
    }

    public static final int SHIFT_OFF = 0, SHIFT_ON = 1, SHIFT_LOCK = 2;

    private Listener listener;
    private final GlassPainter gp;
    private Theme theme = Theme.get(0);
    private final float dp;
    private List<List<Key>> rows;
    private int page = Layouts.ALPHA;
    private boolean numberRow = true;
    private int shift = SHIFT_OFF;
    private long lastShiftTap;
    private boolean shiftHeld, shiftUsedWhileHeld;
    private int oneHanded = 0;
    private final RectF sideBtnA = new RectF(), sideBtnB = new RectF();
    private boolean keyPopupEnabled = true, rippleEnabled = true;

    private int enterIcon = GlassPainter.IC_ENTER;
    private String enterText = null;
    private String spaceLabel = "English";

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler h = new Handler(Looper.getMainLooper());

    // Touch tracking (multi-touch, like fast two-thumb typing)
    private static class Ptr {
        Key key;
        float downX, downY, lastX;
        boolean consumed, popup, spaceSwipe, delSwipe;
        int popupIndex, cursorSteps, delWords;
    }
    private final SparseArray<Ptr> ptrs = new SparseArray<>();
    private Ptr longPressTarget;
    private Ptr popupPtr;
    private final RectF popupRect = new RectF();
    private int popupCols, popupRows;
    private float popupCellW, popupCellH;

    // Ripple of light across the glass on each press
    private float rippleX, rippleY;
    private long rippleStart = -1;

    private int repeatCount;

    public View overlay; // full-size view that draws previews/popups above everything

    public KeyboardView(Context c, GlassPainter gp) {
        super(c);
        this.gp = gp;
        dp = c.getResources().getDisplayMetrics().density;
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        hintPaint.setTextAlign(Paint.Align.CENTER);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        rows = Layouts.build(page, numberRow);
    }

    public void setListener(Listener l) { listener = l; }

    public void configure(Theme t, boolean numberRow, int oneHanded, boolean popup, boolean ripple) {
        theme = t;
        this.keyPopupEnabled = popup;
        this.rippleEnabled = ripple;
        boolean relayout = this.numberRow != numberRow || this.oneHanded != oneHanded;
        this.numberRow = numberRow;
        this.oneHanded = oneHanded;
        if (relayout) { rows = Layouts.build(page, numberRow); requestLayout(); layoutKeys(); }
        invalidate();
    }

    public int rowCount() { return rows.size(); }
    public int page() { return page; }

    public void setPage(int p) {
        page = p;
        rows = Layouts.build(p, numberRow);
        layoutKeys();
        invalidate();
    }

    public int shiftState() { return shift; }

    public void setShift(int s) {
        if (shift != s) { shift = s; invalidate(); }
    }

    public void setEnter(int icon, String label) {
        enterIcon = icon;
        enterText = label;
        invalidate();
    }

    public void setSpaceLabel(String s) { spaceLabel = s; invalidate(); }

    // ------------------------------------------------------------------ layout

    @Override
    protected void onSizeChanged(int w, int hgt, int ow, int oh) {
        layoutKeys();
    }

    private float keysLeft() {
        if (oneHanded == 1) return 0;
        if (oneHanded == 2) return getWidth() * 0.16f;
        return 0;
    }

    private float keysRight() {
        if (oneHanded == 1) return getWidth() * 0.84f;
        return getWidth();
    }

    private void layoutKeys() {
        int W = getWidth(), H = getHeight();
        if (W == 0 || H == 0) return;
        float padX = 3 * dp, padTop = 4 * dp, padBottom = 4 * dp;
        float gapX = 5 * dp, gapY = 8 * dp;
        float left = keysLeft() + padX, right = keysRight() - padX;
        float rowH = (H - padTop - padBottom) / rows.size();
        for (int r = 0; r < rows.size(); r++) {
            List<Key> row = rows.get(r);
            float total = 0;
            for (Key k : row) total += k.weight;
            // Rows that are shorter than 10 units (e.g. 9 letters) keep standard key width and centre.
            float unit = (right - left) / Math.max(total, 10f);
            float x = left + ((right - left) - unit * total) / 2f;
            float top = padTop + r * rowH;
            for (Key k : row) {
                float w = unit * k.weight;
                k.rect.set(x + gapX / 2, top + gapY / 2, x + w - gapX / 2, top + rowH - gapY / 2);
                x += w;
            }
        }
        if (oneHanded != 0) {
            float colL = oneHanded == 1 ? keysRight() : 0;
            float colR = oneHanded == 1 ? W : keysLeft();
            float cx = (colL + colR) / 2, bw = Math.min(colR - colL - 12 * dp, 44 * dp);
            sideBtnA.set(cx - bw / 2, H * 0.22f - bw / 2, cx + bw / 2, H * 0.22f + bw / 2);
            sideBtnB.set(cx - bw / 2, H * 0.55f - bw / 2, cx + bw / 2, H * 0.55f + bw / 2);
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        float radius = 9 * dp;
        drawRipple(c);
        for (List<Key> row : rows) for (Key k : row) {
            if (k.code == 0) continue;
            boolean pressed = isPressed(k);
            int style = GlassPainter.STYLE_KEY;
            if (k.code == Key.ENTER) style = GlassPainter.STYLE_ACTION;
            else if (k.code == Key.SHIFT && shift == SHIFT_LOCK) style = GlassPainter.STYLE_ACTIVE;
            else if (k.isFunction()) style = GlassPainter.STYLE_FUNC;
            RectF r = k.rect;
            if (pressed && k.isChar() && !keyPopupEnabled) {
                // subtle "liquid" swell when previews are off
                r = new RectF(r);
                r.inset(-1.5f * dp, -1.5f * dp);
            }
            gp.drawGlass(c, r, radius, style, pressed, theme);
            drawKeyContent(c, k);
        }
        if (oneHanded != 0) {
            gp.drawGlass(c, sideBtnA, sideBtnA.width() / 2, GlassPainter.STYLE_FUNC, false, theme);
            gp.drawIcon(c, GlassPainter.IC_SWAP, sideBtnA.centerX(), sideBtnA.centerY(), 20 * dp, theme.text);
            gp.drawGlass(c, sideBtnB, sideBtnB.width() / 2, GlassPainter.STYLE_FUNC, false, theme);
            gp.drawIcon(c, GlassPainter.IC_EXPAND, sideBtnB.centerX(), sideBtnB.centerY(), 20 * dp, theme.text);
        }
    }

    private boolean isPressed(Key k) {
        for (int i = 0; i < ptrs.size(); i++) if (ptrs.valueAt(i).key == k) return true;
        return false;
    }

    private void drawKeyContent(Canvas c, Key k) {
        RectF r = k.rect;
        float cx = r.centerX(), cy = r.centerY();
        int col = k.code == Key.ENTER ? 0xFFFFFFFF : theme.text;
        float icon = Math.min(r.height() * 0.5f, 24 * dp);
        switch (k.code) {
            case Key.SHIFT:
                int id = shift == SHIFT_OFF ? GlassPainter.IC_SHIFT : shift == SHIFT_ON ? GlassPainter.IC_SHIFT_ON : GlassPainter.IC_CAPS;
                gp.drawIcon(c, id, cx, cy, icon, shift == SHIFT_LOCK ? 0xFFFFFFFF : col);
                return;
            case Key.DELETE:
                Ptr p = ptrFor(k);
                if (p != null && p.delSwipe) {
                    label(c, "− " + p.delWords + (p.delWords == 1 ? " word" : " words"), cx, cy, r.height() * 0.24f, col, true);
                } else gp.drawIcon(c, GlassPainter.IC_DEL, cx, cy, icon, col);
                return;
            case Key.ENTER:
                if (enterText != null) label(c, enterText, cx, cy, r.height() * 0.32f, col, true);
                else gp.drawIcon(c, enterIcon, cx, cy, icon, col);
                return;
            case Key.EMOJI:
                gp.drawIcon(c, GlassPainter.IC_EMOJI, cx, cy, icon * 0.95f, col);
                return;
            case Key.SPACE:
                Ptr sp = ptrFor(k);
                if (sp != null && sp.spaceSwipe) {
                    gp.drawIcon(c, GlassPainter.IC_LEFT, cx - 22 * dp, cy, icon * 0.8f, theme.subText);
                    gp.drawIcon(c, GlassPainter.IC_RIGHT, cx + 22 * dp, cy, icon * 0.8f, theme.subText);
                } else label(c, spaceLabel, cx, cy, r.height() * 0.26f, theme.subText, false);
                return;
            case Key.SYMBOLS: case Key.ALPHA: case Key.SYMBOLS2:
                label(c, k.label, cx, cy, r.height() * 0.3f, col, true);
                return;
        }
        String s = k.label;
        boolean letter = s.length() == 1 && Character.isLetter(s.charAt(0));
        if (letter && shift != SHIFT_OFF) s = s.toUpperCase();
        label(c, s, cx, cy, r.height() * (letter ? 0.44f : 0.4f), col, false);
        if (k.hint != null) {
            hintPaint.setColor(theme.subText);
            hintPaint.setTextSize(r.height() * 0.22f);
            c.drawText(k.hint, r.right - r.width() * 0.2f, r.top + r.height() * 0.3f, hintPaint);
        }
    }

    private Ptr ptrFor(Key k) {
        for (int i = 0; i < ptrs.size(); i++) if (ptrs.valueAt(i).key == k) return ptrs.valueAt(i);
        return null;
    }

    private void label(Canvas c, String s, float cx, float cy, float size, int color, boolean bold) {
        text.setColor(color);
        text.setTextSize(size);
        text.setFakeBoldText(bold);
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2, text);
        text.setFakeBoldText(false);
    }

    private void drawRipple(Canvas c) {
        if (rippleStart < 0) return;
        float t = (SystemClock.uptimeMillis() - rippleStart) / 420f;
        if (t >= 1f) { rippleStart = -1; return; }
        float rad = 20 * dp + t * getWidth() * 0.35f;
        int a = (int) ((1f - t) * (theme.dark ? 70 : 110));
        ripplePaint.setShader(new RadialGradient(rippleX, rippleY, rad,
                new int[]{0x00FFFFFF, (a << 24) | 0xFFFFFF, 0x00FFFFFF}, new float[]{0.55f, 0.85f, 1f},
                Shader.TileMode.CLAMP));
        c.drawCircle(rippleX, rippleY, rad, ripplePaint);
        postInvalidateOnAnimation();
    }

    /** Called by the overlay view so bubbles can float above the suggestion strip. */
    public void drawOverlay(Canvas c, float offsetY) {
        c.save();
        c.translate(getLeft(), offsetY);
        float minTop = -offsetY + 2 * dp;
        if (popupPtr != null && popupPtr.popup) {
            drawPopup(c);
        } else if (keyPopupEnabled) {
            for (int i = 0; i < ptrs.size(); i++) {
                Ptr p = ptrs.valueAt(i);
                if (p.key == null || !p.key.isChar() || p.consumed) continue;
                RectF r = p.key.rect;
                float bw = r.width() * 1.25f, bh = r.height() * 1.25f;
                float top = Math.max(minTop, r.top - bh - 4 * dp);
                RectF b = new RectF(r.centerX() - bw / 2, top, r.centerX() + bw / 2, top + bh);
                if (b.left < 2 * dp) b.offset(2 * dp - b.left, 0);
                if (b.right > getWidth() - 2 * dp) b.offset(getWidth() - 2 * dp - b.right, 0);
                gp.drawBubble(c, b, 12 * dp, theme);
                String s = p.key.label;
                if (shift != SHIFT_OFF) s = s.toUpperCase();
                label(c, s, b.centerX(), b.centerY(), bh * 0.5f, theme.text, false);
            }
        }
        c.restore();
    }

    private void drawPopup(Canvas c) {
        Key k = popupPtr.key;
        String[] items = k.popups;
        gp.drawBubble(c, popupRect, 12 * dp, theme);
        for (int i = 0; i < items.length; i++) {
            int col = i % popupCols, row = i / popupCols;
            RectF cell = new RectF(popupRect.left + col * popupCellW, popupRect.top + row * popupCellH,
                    popupRect.left + (col + 1) * popupCellW, popupRect.top + (row + 1) * popupCellH);
            cell.inset(3 * dp, 3 * dp);
            boolean sel = i == popupPtr.popupIndex;
            if (sel) gp.drawGlass(c, cell, 8 * dp, GlassPainter.STYLE_ACTION, true, theme);
            String s = shift != SHIFT_OFF ? items[i].toUpperCase() : items[i];
            label(c, s, cell.centerX(), cell.centerY(), popupCellH * 0.42f, sel ? 0xFFFFFFFF : theme.text, false);
        }
    }

    private void openPopup(Ptr p, float offsetYHint) {
        Key k = p.key;
        String[] items = k.popups;
        popupCellW = Math.min(k.rect.width() * 1.05f, 44 * dp);
        popupCellH = k.rect.height() * 1.05f;
        int maxCols = Math.max(1, (int) ((getWidth() - 8 * dp) / popupCellW));
        popupCols = Math.min(items.length, Math.min(maxCols, 6));
        popupRows = (items.length + popupCols - 1) / popupCols;
        float w = popupCols * popupCellW, hh = popupRows * popupCellH;
        float left = k.rect.centerX() - popupCellW / 2;
        if (left + w > getWidth() - 4 * dp) left = getWidth() - 4 * dp - w;
        if (left < 4 * dp) left = 4 * dp;
        float top = Math.max(-offsetYHint + 2 * dp, k.rect.top - hh - 6 * dp);
        popupRect.set(left, top, left + w, top + hh);
        p.popup = true;
        p.popupIndex = 0;
        popupPtr = p;
        // start on the item under the finger if possible
        updatePopupIndex(p, p.lastX, k.rect.top - popupCellH / 2);
    }

    private void updatePopupIndex(Ptr p, float x, float y) {
        int col = (int) ((x - popupRect.left) / popupCellW);
        col = Math.max(0, Math.min(popupCols - 1, col));
        int row = popupRows == 1 ? 0 : (int) ((y - popupRect.top) / popupCellH);
        row = Math.max(0, Math.min(popupRows - 1, row));
        int idx = Math.min(p.key.popups.length - 1, row * popupCols + col);
        if (idx != p.popupIndex) { p.popupIndex = idx; listener.feedback(false); }
    }

    public float overflowAbove = 0; // height of the strip above us (set by the IME)

    private void invalidateAll() {
        invalidate();
        if (overlay != null) overlay.invalidate();
    }

    // ------------------------------------------------------------------ touch

    private Key keyAt(float x, float y) {
        Key best = null;
        float bestD = Float.MAX_VALUE;
        for (List<Key> row : rows) for (Key k : row) {
            if (k.code == 0) continue;
            RectF r = k.rect;
            // generous hit area: distance to key rect (0 inside)
            float dx = Math.max(Math.max(r.left - x, 0), x - r.right);
            float dy = Math.max(Math.max(r.top - y, 0), y - r.bottom);
            float d = dx * dx + dy * dy * 1.5f;
            if (d < bestD) { bestD = d; best = k; }
        }
        return bestD < (30 * dp) * (30 * dp) ? best : null;
    }

    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            Ptr p = longPressTarget;
            if (p == null || p.consumed || p.key == null) return;
            if (p.key.code == Key.SPACE) {
                if (!p.spaceSwipe) { p.consumed = true; listener.onSpaceLongPress(); }
                return;
            }
            String[] items = p.key.popups;
            if (items == null && p.key.hint != null) items = new String[]{p.key.hint};
            if (items == null) return;
            if (p.key.popups == null) {
                // single hint (e.g. number on top row) -> type it straight away
                p.consumed = true;
                listener.feedback(true);
                listener.onText(p.key.hint);
                invalidateAll();
                return;
            }
            listener.feedback(true);
            openPopup(p, overflowAbove);
            invalidateAll();
        }
    };

    private final Runnable repeat = new Runnable() {
        @Override public void run() {
            for (int i = 0; i < ptrs.size(); i++) {
                Ptr p = ptrs.valueAt(i);
                if (p.key != null && p.key.code == Key.DELETE && !p.delSwipe) {
                    repeatCount++;
                    if (repeatCount > 25) listener.onDeleteWords(1); else listener.onKey(Key.DELETE);
                    listener.feedback(false);
                    h.postDelayed(this, repeatCount > 25 ? 140 : 55);
                    return;
                }
            }
        }
    };

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int idx = e.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                float x = e.getX(idx), y = e.getY(idx);
                if (oneHanded != 0) {
                    if (sideBtnA.contains(x, y)) { listener.feedback(true); listener.onOneHandedSwap(); return true; }
                    if (sideBtnB.contains(x, y)) { listener.feedback(true); listener.onOneHandedExit(); return true; }
                    if (x < keysLeft() || x > keysRight()) return true;
                }
                // Fast typing: a new finger commits the previous finger's key immediately.
                for (int i = 0; i < ptrs.size(); i++) {
                    Ptr o = ptrs.valueAt(i);
                    if (!o.consumed && o.key != null && o.key.isChar() && !o.popup) {
                        o.consumed = true;
                        fire(o.key);
                    }
                }
                Key k = keyAt(x, y);
                if (k == null) return true;
                Ptr p = new Ptr();
                p.key = k; p.downX = x; p.downY = y; p.lastX = x;
                ptrs.put(e.getPointerId(idx), p);
                listener.feedback(false);
                if (rippleEnabled) { rippleX = x; rippleY = y; rippleStart = SystemClock.uptimeMillis(); }
                h.removeCallbacks(longPress);
                if (k.code == Key.DELETE) {
                    listener.onKey(Key.DELETE);
                    p.consumed = true;
                    repeatCount = 0;
                    h.removeCallbacks(repeat);
                    h.postDelayed(repeat, 400);
                } else if (k.code == Key.SHIFT) {
                    p.consumed = true;
                    handleShiftDown();
                } else if (k.popups != null || k.hint != null || k.code == Key.SPACE) {
                    longPressTarget = p;
                    h.postDelayed(longPress, k.code == Key.SPACE ? 500 : 330);
                }
                invalidateAll();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    Ptr p = ptrs.get(e.getPointerId(i));
                    if (p == null || p.key == null) continue;
                    float x = e.getX(i), y = e.getY(i);
                    if (p.popup) { updatePopupIndex(p, x, y); invalidateAll(); continue; }
                    float dx = x - p.downX;
                    if (p.key.code == Key.SPACE && !p.consumed) {
                        if (!p.spaceSwipe && Math.abs(dx) > 14 * dp) { p.spaceSwipe = true; h.removeCallbacks(longPress); }
                        if (p.spaceSwipe) {
                            int steps = (int) ((x - p.downX) / (11 * dp));
                            if (steps != p.cursorSteps) {
                                listener.onCursorMove(steps - p.cursorSteps);
                                listener.feedback(false);
                                p.cursorSteps = steps;
                            }
                            invalidate();
                        }
                    } else if (p.key.code == Key.DELETE) {
                        if (dx < -26 * dp) {
                            if (!p.delSwipe) { p.delSwipe = true; h.removeCallbacks(repeat); }
                            int words = 1 + (int) ((-dx - 26 * dp) / (32 * dp));
                            if (words != p.delWords) { p.delWords = words; listener.feedback(false); invalidate(); }
                        } else if (p.delSwipe) { p.delWords = 0; invalidate(); }
                    } else if (!p.consumed && p.key.isChar()) {
                        // let the finger slide onto a neighbouring key before release
                        Key k = keyAt(x, y);
                        if (k != null && k != p.key && k.isChar() && Math.hypot(x - p.downX, y - p.downY) > 10 * dp) {
                            p.key = k;
                            h.removeCallbacks(longPress);
                            if (k.popups != null || k.hint != null) { longPressTarget = p; h.postDelayed(longPress, 330); }
                            invalidateAll();
                        }
                    }
                    p.lastX = x;
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                Ptr p = ptrs.get(e.getPointerId(idx));
                ptrs.remove(e.getPointerId(idx));
                h.removeCallbacks(longPress);
                if (p != null && p.key != null) release(p);
                if (ptrs.size() == 0) h.removeCallbacks(repeat);
                invalidateAll();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                ptrs.clear();
                popupPtr = null;
                h.removeCallbacks(longPress);
                h.removeCallbacks(repeat);
                invalidateAll();
                return true;
        }
        return true;
    }

    private void release(Ptr p) {
        if (p.popup) {
            String s = p.key.popups[p.popupIndex];
            if (shift != SHIFT_OFF) s = s.toUpperCase();
            listener.onText(s);
            afterCharTyped();
            popupPtr = null;
            return;
        }
        if (p.key.code == Key.SHIFT) {
            if (shiftHeld && shiftUsedWhileHeld && shift == SHIFT_ON) setShift(SHIFT_OFF);
            shiftHeld = false;
            return;
        }
        if (p.key.code == Key.DELETE) {
            if (p.delSwipe && p.delWords > 0) listener.onDeleteWords(p.delWords);
            return;
        }
        if (p.key.code == Key.SPACE && p.spaceSwipe) return;
        if (!p.consumed) fire(p.key);
    }

    private void fire(Key k) {
        if (k.isChar()) {
            String s = k.label;
            if (shift != SHIFT_OFF) s = s.toUpperCase();
            listener.onText(s);
            afterCharTyped();
        } else {
            listener.onKey(k.code);
        }
    }

    private void afterCharTyped() {
        if (shiftHeld) { shiftUsedWhileHeld = true; return; }
        if (shift == SHIFT_ON) setShift(SHIFT_OFF);
    }

    private void handleShiftDown() {
        long now = SystemClock.uptimeMillis();
        shiftHeld = true;
        shiftUsedWhileHeld = false;
        if (page != Layouts.ALPHA) return;
        if (shift == SHIFT_ON && now - lastShiftTap < 350) setShift(SHIFT_LOCK);
        else if (shift == SHIFT_OFF) setShift(SHIFT_ON);
        else setShift(SHIFT_OFF);
        lastShiftTap = now;
    }
}
