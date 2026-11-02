package com.meta.plugin;

import android.media.AudioManager;
import android.media.ToneGenerator;

/** Tiny sound helper: short beeps for timer / alarm. */
final class Beeper {

    private static ToneGenerator tg;

    private Beeper() {
    }

    private static ToneGenerator gen() {
        if (tg == null) {
            try {
                tg = new ToneGenerator(AudioManager.STREAM_ALARM, 90);
            } catch (Exception e) {
                tg = null;
            }
        }
        return tg;
    }

    static void beep() {
        ToneGenerator g = gen();
        if (g != null) {
            try {
                g.startTone(ToneGenerator.TONE_PROP_BEEP, 200);
            } catch (Exception ignored) {
            }
        }
    }

    static void alarm() {
        ToneGenerator g = gen();
        if (g != null) {
            try {
                g.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 900);
            } catch (Exception ignored) {
            }
        }
    }

    static void click() {
        ToneGenerator g = gen();
        if (g != null) {
            try {
                g.startTone(ToneGenerator.TONE_PROP_ACK, 60);
            } catch (Exception ignored) {
            }
        }
    }

    static void stop() {
        ToneGenerator g = gen();
        if (g != null) {
            try {
                g.stopTone();
            } catch (Exception ignored) {
            }
        }
    }

    static void release() {
        if (tg != null) {
            try {
                tg.release();
            } catch (Exception ignored) {
            }
            tg = null;
        }
    }
}
