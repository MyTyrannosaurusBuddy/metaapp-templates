package com.meta.plugin;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;

import java.util.Random;

/**
 * Tiny sound engine for the Minesweeper board.
 *
 * <p>Waveforms are generated procedurally into PCM buffers (no audio resource files) and played
 * back through pre-buffered MODE_STATIC {@link AudioTrack}s.
 *
 * <p>Two hard-learned Android details are handled here:
 * <ul>
 *   <li>A static track created through {@code AudioTrack.Builder} starts out in
 *       {@link AudioTrack#STATE_NO_STATIC_DATA}, <em>not</em> {@code STATE_INITIALIZED}; it only
 *       becomes initialized after the PCM has been written. Checking for
 *       {@code STATE_INITIALIZED} before the first write made the app permanently silent.</li>
 *   <li>If a static track still cannot play, a MODE_STREAM track (which this device accepts
 *       reliably) is used as fallback, and {@link ToneGenerator} as the very last resort.</li>
 * </ul>
 */
public class SoundFx {

    public static final int REVEAL = 0;
    public static final int FLAG = 1;
    public static final int UNFLAG = 2;
    public static final int EXPLODE = 3;
    public static final int WIN = 4;

    private static final int COUNT = 5;
    private static final int RATE = 44100;
    private static final int MAX_ATTEMPTS = 3;

    private final Context appContext;
    private final AudioTrack[] tracks = new AudioTrack[COUNT];
    private final int[] failedAttempts = new int[COUNT];

    private boolean enabled = true;
    private AudioManager audioManager;
    private ToneGenerator toneFallback;
    private boolean focusRequested;
    private String lastError = "none";
    private int lastPlaybackHead = -1;
    private int lastWrittenFrames = -1;

    // MODE_STREAM fallback machinery (built lazily, played on its own thread).
    private AudioTrack streamTrack;
    private int streamSizeBytes;
    private Handler streamHandler;
    private HandlerThread streamThread;

    public SoundFx(Context context) {
        appContext = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            stopAll();
            abandonFocus();
        }
    }

    /** Fire one of the effect ids. Safe to call at any time, never throws. */
    public void play(int id) {
        if (!enabled || id < 0 || id >= COUNT) {
            return;
        }
        try {
            ensureAudible();
            short[] pcm = pcmFor(id);
            AudioTrack track = obtain(id, pcm);
            if (track != null && startTrack(track)) {
                return;
            }
            // Static playback was not usable: stream the very same samples instead.
            if (playStreamAsync(pcm)) {
                return;
            }
            playToneFallback(id);
        } catch (RuntimeException ignored) {
            // Audio must never break the game.
            try {
                playToneFallback(id);
            } catch (RuntimeException ignored2) {
                // give up silently
            }
        }
    }

    /**
     * Prepare every buffer ahead of time so the first tap has no latency. Called a moment after the
     * UI is attached, when the audio HAL is reliably available.
     */
    public void warmUp() {
        for (int i = 0; i < COUNT; i++) {
            try {
                obtain(i, pcmFor(i));
            } catch (RuntimeException ignored) {
                // ignore
            }
        }
        ensureStreamTrack(2048);
    }

    /** DEBUG hook: last low-level audio error text. */
    public String debugError() {
        return lastError;
    }

    /**
     * DEBUG hook: how far the mixer actually consumed the last static effect. A non-zero playback
     * head proves the samples reached the audio HAL instead of sitting in a silent buffer.
     */
    public String debugPlayback() {
        return "head=" + lastPlaybackHead + "/" + lastWrittenFrames;
    }

    /** DEBUG helper: compact per-effect state string, e.g. "P P - I I". */
    public String debugStatus() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < COUNT; i++) {
            AudioTrack t = tracks[i];
            if (t == null) {
                sb.append("-");
            } else if (t.getState() == AudioTrack.STATE_INITIALIZED) {
                sb.append(t.getPlayState() == AudioTrack.PLAYSTATE_PLAYING ? "P" : "I");
            } else if (t.getState() == AudioTrack.STATE_NO_STATIC_DATA) {
                sb.append("n");
            } else {
                sb.append("x");
            }
            sb.append(' ');
        }
        if (streamTrack != null) {
            sb.append("s");
            sb.append(streamTrack.getState() == AudioTrack.STATE_INITIALIZED ? "1" : "0");
        }
        if (audioManager != null) {
            sb.append(" vol=").append(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
            sb.append('/').append(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Static track lifecycle
    // ------------------------------------------------------------------

    private AudioTrack obtain(int id, short[] pcm) {
        AudioTrack track = tracks[id];
        if (track != null && track.getState() == AudioTrack.STATE_INITIALIZED) {
            return track;
        }
        if (track != null) {
            safeRelease(track);
            tracks[id] = null;
        }
        if (failedAttempts[id] >= MAX_ATTEMPTS) {
            return null;
        }
        failedAttempts[id]++;
        track = createStatic(pcm);
        tracks[id] = track;
        return track;
    }

    private boolean startTrack(AudioTrack track) {
        try {
            stopIfPlaying(track);
            // Static buffers keep their data; rewinding is enough to replay.
            track.reloadStaticData();
            track.setVolume(1f);
            track.play();
            if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                lastPlaybackHead = track.getPlaybackHeadPosition();
                lastWrittenFrames = track.getBufferSizeInFrames();
                return true;
            }
            lastError = "noplay" + track.getState();
            return false;
        } catch (RuntimeException e) {
            lastError = "play:" + e.getClass().getSimpleName();
            return false;
        }
    }

    private AudioTrack createStatic(short[] pcm) {
        AudioTrack track = null;
        int sizeBytes = pcm.length * 2;
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                AudioFormat format = new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build();
                track = new AudioTrack.Builder()
                        .setAudioAttributes(attrs)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(sizeBytes)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();
            } else {
                track = new AudioTrack(AudioManager.STREAM_MUSIC, RATE,
                        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        sizeBytes, AudioTrack.MODE_STATIC);
            }
            AudioTrack filled = fillStatic(track, pcm);
            if (filled != null) {
                return filled;
            }
            return null;
        } catch (RuntimeException e) {
            lastError = e.getClass().getSimpleName();
            if (track != null) {
                safeRelease(track);
            }
            return null;
        }
    }

    /**
     * Writes the PCM into a static track and validates it. A freshly built static track reports
     * {@link AudioTrack#STATE_NO_STATIC_DATA} until this write happens, so the state must only be
     * checked <em>after</em> filling the buffer.
     */
    private AudioTrack fillStatic(AudioTrack track, short[] pcm) {
        int state = track.getState();
        if (state != AudioTrack.STATE_INITIALIZED && state != AudioTrack.STATE_NO_STATIC_DATA) {
            lastError = "state" + state;
            safeRelease(track);
            return null;
        }
        int written = track.write(pcm, 0, pcm.length);
        if (written != pcm.length) {
            lastError = "write" + written;
            safeRelease(track);
            return null;
        }
        // No setLoopPoints() call: the default is "play once, do not loop". Passing a loop count
        // of 0 would loop the effect forever.
        track.setVolume(1f);
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            lastError = "nostatic";
            safeRelease(track);
            return null;
        }
        return track;
    }

    // ------------------------------------------------------------------
    // MODE_STREAM fallback (this device always accepts streaming tracks)
    // ------------------------------------------------------------------

    private synchronized boolean ensureStreamTrack(int minBytes) {
        if (streamTrack != null && streamTrack.getState() == AudioTrack.STATE_INITIALIZED
                && streamSizeBytes >= minBytes) {
            return true;
        }
        if (streamTrack != null) {
            safeRelease(streamTrack);
            streamTrack = null;
        }
        try {
            int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) {
                min = 4096;
            }
            int size = Math.max(min, minBytes * 2);
            streamTrack = new AudioTrack(AudioManager.STREAM_MUSIC, RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    size, AudioTrack.MODE_STREAM);
            if (streamTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                lastError = "stream" + streamTrack.getState();
                safeRelease(streamTrack);
                streamTrack = null;
                return false;
            }
            streamTrack.setVolume(1f);
            streamSizeBytes = size;
            return true;
        } catch (RuntimeException e) {
            lastError = "stream:" + e.getClass().getSimpleName();
            if (streamTrack != null) {
                safeRelease(streamTrack);
                streamTrack = null;
            }
            return false;
        }
    }

    /** Streams the samples on a worker thread so blocking writes never stall the game. */
    private boolean playStreamAsync(final short[] pcm) {
        if (!ensureStreamTrack(pcm.length * 2)) {
            return false;
        }
        final Handler handler = streamWorker();
        if (handler == null) {
            return false;
        }
        handler.post(new Runnable() {
            @Override
            public void run() {
                writeAndPlayStream(pcm);
            }
        });
        return true;
    }

    private synchronized Handler streamWorker() {
        if (streamHandler != null) {
            return streamHandler;
        }
        try {
            streamThread = new HandlerThread("minesweeper-audio");
            streamThread.start();
            streamHandler = new Handler(streamThread.getLooper());
            return streamHandler;
        } catch (RuntimeException e) {
            lastError = "worker";
            return null;
        }
    }

    private void writeAndPlayStream(short[] pcm) {
        AudioTrack track;
        synchronized (this) {
            track = streamTrack;
        }
        if (track == null) {
            return;
        }
        try {
            stopIfPlaying(track);
            track.flush();
            int offset = 0;
            while (offset < pcm.length) {
                int w = track.write(pcm, offset, pcm.length - offset);
                if (w < 0) {
                    lastError = "sw" + w;
                    return;
                }
                offset += w;
            }
            track.play();
        } catch (RuntimeException e) {
            lastError = "sp:" + e.getClass().getSimpleName();
        }
    }

    // ------------------------------------------------------------------
    // ToneGenerator last resort
    // ------------------------------------------------------------------

    private void playToneFallback(int id) {
        if (toneFallback == null) {
            try {
                toneFallback = new ToneGenerator(AudioManager.STREAM_MUSIC, 90);
            } catch (RuntimeException e) {
                toneFallback = null;
                return;
            }
        }
        int tone;
        int duration;
        if (id == EXPLODE) {
            tone = ToneGenerator.TONE_PROP_BEEP2;
            duration = 400;
        } else if (id == WIN) {
            tone = ToneGenerator.TONE_PROP_ACK;
            duration = 300;
        } else if (id == FLAG) {
            tone = ToneGenerator.TONE_PROP_BEEP;
            duration = 80;
        } else if (id == UNFLAG) {
            tone = ToneGenerator.TONE_PROP_BEEP;
            duration = 60;
        } else {
            tone = ToneGenerator.TONE_PROP_BEEP;
            duration = 70;
        }
        try {
            toneFallback.startTone(tone, duration);
        } catch (RuntimeException ignored) {
            // ignore
        }
    }

    // ------------------------------------------------------------------
    // Volume / focus
    // ------------------------------------------------------------------

    /**
     * Make sure playback can actually be heard: take audio focus once and, if the media stream is
     * completely muted, lift it to a usable level (many devices ship with STREAM_MUSIC at 0, which
     * looks exactly like "the app has no sound").
     */
    private void ensureAudible() {
        if (audioManager == null) {
            return;
        }
        try {
            if (!focusRequested) {
                focusRequested = true;
                if (Build.VERSION.SDK_INT >= 26) {
                    AudioAttributes attrs = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();
                    AudioFocusRequest request = new AudioFocusRequest.Builder(
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                            .setAudioAttributes(attrs)
                            .build();
                    audioManager.requestAudioFocus(request);
                }
            }
            int vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (vol == 0) {
                int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int target = Math.max(1, (int) (max * 0.6f));
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
            }
        } catch (RuntimeException ignored) {
            // Some platforms restrict this; playback still works at the current volume.
        }
    }

    private void abandonFocus() {
        if (audioManager == null || !focusRequested) {
            return;
        }
        focusRequested = false;
        try {
            audioManager.abandonAudioFocus(null);
        } catch (RuntimeException ignored) {
            // ignore
        }
    }

    private void stopAll() {
        for (int i = 0; i < COUNT; i++) {
            AudioTrack track = tracks[i];
            if (track == null) {
                continue;
            }
            try {
                stopIfPlaying(track);
            } catch (RuntimeException ignored) {
                // ignore
            }
        }
        synchronized (this) {
            if (streamTrack != null) {
                try {
                    stopIfPlaying(streamTrack);
                } catch (RuntimeException ignored) {
                    // ignore
                }
            }
        }
        if (toneFallback != null) {
            try {
                toneFallback.stopTone();
            } catch (RuntimeException ignored) {
                // ignore
            }
        }
    }

    private static void stopIfPlaying(AudioTrack track) {
        if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            track.stop();
        }
    }

    public void release() {
        for (int i = 0; i < COUNT; i++) {
            AudioTrack track = tracks[i];
            tracks[i] = null;
            if (track != null) {
                safeRelease(track);
            }
        }
        synchronized (this) {
            if (streamTrack != null) {
                safeRelease(streamTrack);
                streamTrack = null;
            }
            if (streamThread != null) {
                try {
                    streamThread.quit();
                } catch (RuntimeException ignored) {
                    // ignore
                }
                streamThread = null;
                streamHandler = null;
            }
        }
        if (toneFallback != null) {
            try {
                toneFallback.release();
            } catch (RuntimeException ignored) {
                // ignore
            }
            toneFallback = null;
        }
        abandonFocus();
    }

    private static void safeRelease(AudioTrack track) {
        try {
            track.stop();
        } catch (RuntimeException ignored) {
            // ignore
        }
        try {
            track.release();
        } catch (RuntimeException ignored) {
            // ignore
        }
    }

    // ------------------------------------------------------------------
    // Waveform generators
    // ------------------------------------------------------------------

    private short[] pcmFor(int id) {
        switch (id) {
            case FLAG:
                return blip(0.055f, 1180f, 1180f, 0.42f);
            case UNFLAG:
                return blip(0.055f, 760f, 620f, 0.36f);
            case EXPLODE:
                return boom(0.42f, 0.85f);
            case WIN:
                return arpeggio(new float[]{523.25f, 659.25f, 783.99f, 1046.50f}, 0.11f, 0.42f);
            case REVEAL:
            default:
                return blip(0.075f, 620f, 980f, 0.46f);
        }
    }

    /** Short pitch-swept sine blip, useful for taps and clicks. */
    private static short[] blip(float seconds, float f0, float f1, float amp) {
        int n = Math.max(1, (int) (RATE * seconds));
        short[] out = new short[n];
        double phase = 0;
        int attack = Math.max(1, (int) (RATE * 0.003f));
        int release = Math.max(2, (int) (RATE * 0.018f));
        for (int i = 0; i < n; i++) {
            float t = (float) i / n;
            double freq = f0 + (f1 - f0) * t;
            phase += 2 * Math.PI * freq / RATE;
            float env = 1f;
            if (i < attack) {
                env = (float) i / attack;
            } else if (i > n - release) {
                env = (float) (n - i) / release;
            }
            out[i] = clamp((float) Math.sin(phase) * amp * env);
        }
        return out;
    }

    /** Filtered noise burst with a fast decay: the mine explosion. */
    private static short[] boom(float seconds, float amp) {
        int n = Math.max(1, (int) (RATE * seconds));
        short[] out = new short[n];
        Random random = new Random(7);
        float low = 0f;
        for (int i = 0; i < n; i++) {
            float t = (float) i / n;
            float white = (float) (random.nextDouble() * 2 - 1);
            low += (white - low) * 0.16f;
            float env = (float) Math.pow(1f - t, 2.2f);
            out[i] = clamp((low * 1.7f + white * 0.18f) * env * amp);
        }
        return out;
    }

    /** Sequence of soft notes, each with its own decay: the win jingle. */
    private static short[] arpeggio(float[] freqs, float noteSeconds, float amp) {
        int per = Math.max(1, (int) (RATE * noteSeconds));
        int n = per * freqs.length;
        short[] out = new short[n];
        for (int k = 0; k < freqs.length; k++) {
            float freq = freqs[k];
            for (int i = 0; i < per; i++) {
                float t = (float) i / per;
                float env = (float) Math.pow(1f - t, 1.4f);
                float attack = Math.min(1f, i / (RATE * 0.006f));
                double sample = Math.sin(2 * Math.PI * freq * i / RATE)
                        + 0.28 * Math.sin(4 * Math.PI * freq * i / RATE);
                out[k * per + i] = clamp((float) sample * 0.62f * amp * env * attack);
            }
        }
        return out;
    }

    private static short clamp(float value) {
        float scaled = value * Short.MAX_VALUE * 0.85f;
        if (scaled > Short.MAX_VALUE) {
            scaled = Short.MAX_VALUE;
        } else if (scaled < -Short.MAX_VALUE) {
            scaled = -Short.MAX_VALUE;
        }
        return (short) scaled;
    }
}
