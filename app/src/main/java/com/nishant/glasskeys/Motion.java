package com.nishant.glasskeys;

import android.content.Context;
import android.provider.Settings;
import android.view.animation.PathInterpolator;

/** Shared motion rules: strong ease-out curve, and the phone's "Remove animations" setting. */
final class Motion {
    /** Strong ease-out: moves immediately, settles softly. No overshoot. */
    static final PathInterpolator EASE_OUT = new PathInterpolator(0.23f, 1f, 0.32f, 1f);

    /** True when the user turned animations off in Android settings (accessibility / developer options). */
    static boolean reduced(Context c) {
        try {
            return Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        } catch (Exception e) {
            return false;
        }
    }

    private Motion() { }
}
