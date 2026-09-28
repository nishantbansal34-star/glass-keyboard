package com.nishant.glasskeys;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

/**
 * Calculator screen: the sum on the top line with a blinking cursor (tap or drag to move it),
 * and the live answer + last calculation on a separate bottom line so nothing overlaps.
 */
public class CalcDisplay extends View {

    public interface Model {
        String expr();
        int cursor();
        void setCursor(int c);
        String history();
        boolean evaluated();
    }

    private final GlassPainter gp;
    private final float dp;
    private Theme theme = Theme.get(0);
    private Model model;
    private final TextPaint exprPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint caret = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final long born = SystemClock.uptimeMillis();
    private long lastMove;
    public Runnable feedback;

    // layout of the last drawn expression (for tap -> cursor)
    private String shown = "";
    private int[] rawToShown = new int[1];
    private float textLeft, scrollX;

    public CalcDisplay(Context c, GlassPainter gp) {
        super(c);
        this.gp = gp;
        dp = c.getResources().getDisplayMetrics().density;
        exprPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        small.setTypeface(Typeface.DEFAULT);
    }

    public void setTheme(Theme t) { theme = t; invalidate(); }
    public void setModel(Model m) { model = m; }

    /** Call after any edit so the caret shows solid (not mid-blink). */
    public void poke() { lastMove = SystemClock.uptimeMillis(); invalidate(); }

    // ------------------------------------------------------------------ pretty text with index map

    private static boolean isOp(char c) { return "+−×÷-*/".indexOf(c) >= 0; }

    /** Builds the display string (grouped digits, spaced operators) and maps raw indexes to it. */
    private void buildShown(String raw) {
        StringBuilder out = new StringBuilder();
        int[] map = new int[raw.length() + 1];
        int i = 0;
        while (i < raw.length()) {
            char ch = raw.charAt(i);
            if (Character.isDigit(ch)) {
                int j = i;
                while (j < raw.length() && Character.isDigit(raw.charAt(j))) j++;
                int n = j - i;
                // Indian grouping: last 3, then pairs
                for (int k = 0; k < n; k++) {
                    int fromEnd = n - k;
                    if (k > 0 && (fromEnd == 3 || (fromEnd > 3 && (fromEnd - 3) % 2 == 0))) out.append(',');
                    map[i + k] = out.length();
                    out.append(raw.charAt(i + k));
                }
                i = j;
                // decimal part after a point stays ungrouped
                if (i < raw.length() && raw.charAt(i) == '.') {
                    map[i] = out.length();
                    out.append('.');
                    i++;
                    while (i < raw.length() && Character.isDigit(raw.charAt(i))) {
                        map[i] = out.length();
                        out.append(raw.charAt(i));
                        i++;
                    }
                }
            } else if (isOp(ch) && i > 0) {
                map[i] = out.length();          // caret before an operator sits before its space
                out.append(' ');
                char d = ch == '-' ? '−' : ch == '*' ? '×' : ch == '/' ? '÷' : ch;
                out.append(d).append(' ');
                i++;
            } else {
                map[i] = out.length();
                out.append(ch == '-' ? '−' : ch);
                i++;
            }
        }
        map[raw.length()] = out.length();
        shown = out.toString();
        rawToShown = map;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        if (model == null) return;
        gp.setOriginFromView(this);
        box.set(7 * dp, 4 * dp, getWidth() - 7 * dp, getHeight() - 3 * dp);
        gp.drawGlass(c, box, 16 * dp, GlassPainter.STYLE_FUNC, false, theme);

        float pad = 14 * dp;
        float innerW = box.width() - 2 * pad;
        float line1 = box.top + box.height() * 0.50f;   // baseline of the sum
        float line2 = box.bottom - box.height() * 0.14f; // baseline of answer + history

        String raw = model.expr();
        buildShown(raw);
        String text = raw.isEmpty() ? "0" : shown;

        // sum: fit between 18dp and 28dp, then scroll if still too long
        float size = Math.min(28 * dp, box.height() * 0.40f);
        exprPaint.setTextSize(size);
        while (exprPaint.measureText(text) > innerW && size > 18 * dp) { size -= dp; exprPaint.setTextSize(size); }
        exprPaint.setColor(theme.text);
        float tw = exprPaint.measureText(text);
        int cur = Math.max(0, Math.min(raw.length(), model.cursor()));
        float caretInText = raw.isEmpty() ? tw : exprPaint.measureText(shown, 0, rawToShown[cur]);
        // right-aligned when it fits; otherwise keep the caret in view
        if (tw <= innerW) scrollX = 0;
        else {
            float base = box.right - pad - tw; // position if right aligned
            float cx = base + caretInText - scrollX;
            if (cx < box.left + pad) scrollX -= (box.left + pad) - cx;
            if (cx > box.right - pad) scrollX += cx - (box.right - pad);
            scrollX = Math.max(tw - innerW > 0 ? -(tw - innerW) : 0, Math.min(0, scrollX));
        }
        textLeft = box.right - pad - tw - scrollX;
        c.save();
        c.clipRect(box.left + pad * 0.5f, box.top, box.right - pad * 0.5f, box.bottom);
        c.drawText(text, textLeft, line1, exprPaint);

        // blinking caret (solid right after an edit)
        long now = SystemClock.uptimeMillis();
        boolean on = now - lastMove < 600 || ((now - born) / 530) % 2 == 0;
        if (on) {
            float x = textLeft + (raw.isEmpty() ? 0 : caretInText);
            if (raw.isEmpty()) x = textLeft - 3 * dp;
            Paint.FontMetrics fm = exprPaint.getFontMetrics();
            caret.setColor(theme.accent);
            caret.setStrokeWidth(2 * dp);
            c.drawLine(x + 1 * dp, line1 + fm.ascent * 0.85f, x + 1 * dp, line1 + fm.descent * 0.6f, caret);
        }
        c.restore();

        // bottom line: history on the left, live answer on the right — never on top of the sum
        Double v = Calc.eval(raw);
        boolean hasOp = raw.matches(".*[+\\-*/×÷−%^(].*");
        String answer = (v != null && hasOp && !model.evaluated()) ? "= " + Calc.format(v, true) : "";
        small.setTextSize(19 * dp);
        small.setColor(theme.accent);
        small.setTextAlign(Paint.Align.RIGHT);
        float aw = answer.isEmpty() ? 0 : small.measureText(answer);
        if (!answer.isEmpty()) c.drawText(answer, box.right - pad, line2, small);
        String hist = model.history();
        if (hist != null && !hist.isEmpty()) {
            TextPaint hp = new TextPaint(small);
            hp.setTextSize(12.5f * dp);
            hp.setColor(theme.subText);
            hp.setTextAlign(Paint.Align.LEFT);
            float avail = innerW - aw - (aw > 0 ? 12 * dp : 0);
            if (avail > 30 * dp) {
                String h = TextUtils.ellipsize(hist, hp, avail, TextUtils.TruncateAt.START).toString();
                c.drawText(h, box.left + pad, line2, hp);
            }
        }
        if (isShown()) postInvalidateDelayed(530);
    }

    // ------------------------------------------------------------------ touch: place / drag caret

    private int rawIndexAt(float x) {
        String raw = model.expr();
        if (raw.isEmpty()) return 0;
        int best = raw.length();
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i <= raw.length(); i++) {
            float px = textLeft + exprPaint.measureText(shown, 0, rawToShown[i]);
            float d = Math.abs(px - x);
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (model == null) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                int idx = rawIndexAt(e.getX());
                if (idx != model.cursor()) {
                    model.setCursor(idx);
                    if (feedback != null) feedback.run();
                }
                poke();
                return true;
        }
        return true;
    }
}
