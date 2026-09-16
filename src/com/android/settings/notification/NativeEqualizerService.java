package com.android.settings.notification;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.os.IBinder;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class NativeEqualizerService extends Service {
    private static final String TAG = "NativeEqualizerService";
    public static final String PREF_NAME = "kiro_equalizer_prefs";
    public static final String PREF_KEY_ENABLED = "eq_enabled";
    public static final String PREF_KEY_PRESET = "eq_preset";
    public static final String PREF_KEY_BASS = "eq_bass";
    public static final String PREF_KEY_BAND_PREFIX = "eq_band_";

    public static final String ACTION_UPDATE_EFFECTS = "com.android.settings.action.UPDATE_EQUALIZER";

    private static Equalizer sGlobalEqualizer;
    private static BassBoost sGlobalBassBoost;

    private static final Map<Integer, Equalizer> sSessionEqualizers = new HashMap<>();
    private static final Map<Integer, BassBoost> sSessionBassBoosts = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "NativeEqualizerService created");
        applyEffectsToSession(0);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            int sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);

            if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
                if (sessionId > 0) {
                    applyEffectsToSession(sessionId);
                }
            } else if (AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
                if (sessionId > 0) {
                    removeSession(sessionId);
                }
            } else {
                // Regular update / refresh
                applyEffectsToAll();
            }
        } else {
            applyEffectsToAll();
        }
        return START_STICKY;
    }

    public static synchronized void updateEffects(Context context) {
        try {
            Intent intent = new Intent(context, NativeEqualizerService.class);
            intent.setAction(ACTION_UPDATE_EFFECTS);
            context.startService(intent);
        } catch (Exception e) {
            Log.w(TAG, "Failed to send update intent to NativeEqualizerService", e);
        }
    }

    private synchronized void applyEffectsToAll() {
        applyEffectsToSession(0);
        for (Integer sessionId : sSessionEqualizers.keySet()) {
            applyEffectsToSession(sessionId);
        }
    }

    private synchronized void applyEffectsToSession(int sessionId) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            boolean enabled = prefs.getBoolean(PREF_KEY_ENABLED, true);
            int bass = prefs.getInt(PREF_KEY_BASS, 0);
            int savedPreset = prefs.getInt(PREF_KEY_PRESET, 0);

            Equalizer eq;
            BassBoost bb;

            if (sessionId == 0) {
                if (sGlobalEqualizer == null) {
                    sGlobalEqualizer = new Equalizer(1000, 0);
                }
                if (sGlobalBassBoost == null) {
                    sGlobalBassBoost = new BassBoost(1000, 0);
                }
                eq = sGlobalEqualizer;
                bb = sGlobalBassBoost;
            } else {
                if (!sSessionEqualizers.containsKey(sessionId)) {
                    sSessionEqualizers.put(sessionId, new Equalizer(1000, sessionId));
                }
                if (!sSessionBassBoosts.containsKey(sessionId)) {
                    sSessionBassBoosts.put(sessionId, new BassBoost(1000, sessionId));
                }
                eq = sSessionEqualizers.get(sessionId);
                bb = sSessionBassBoosts.get(sessionId);
            }

            if (bb != null) {
                bb.setEnabled(enabled);
                bb.setStrength((short) bass);
            }

            if (eq != null) {
                eq.setEnabled(enabled);
                short numPresets = eq.getNumberOfPresets();
                if (savedPreset < numPresets) {
                    eq.usePreset((short) savedPreset);
                } else {
                    short bands = eq.getNumberOfBands();
                    for (short i = 0; i < bands; i++) {
                        int defaultLevel = eq.getBandLevel(i);
                        int level = prefs.getInt(PREF_KEY_BAND_PREFIX + i, defaultLevel);
                        eq.setBandLevel(i, (short) level);
                    }
                }
            }
            Log.d(TAG, "Applied DSP effects for audioSession: " + sessionId + " (enabled=" + enabled + ")");
        } catch (Exception e) {
            Log.w(TAG, "Error configuring DSP effects for session " + sessionId, e);
        }
    }

    private synchronized void removeSession(int sessionId) {
        Equalizer eq = sSessionEqualizers.remove(sessionId);
        if (eq != null) {
            try { eq.release(); } catch (Exception ignored) {}
        }
        BassBoost bb = sSessionBassBoosts.remove(sessionId);
        if (bb != null) {
            try { bb.release(); } catch (Exception ignored) {}
        }
        Log.d(TAG, "Released audioSession: " + sessionId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.w(TAG, "NativeEqualizerService onDestroy called");
    }
}
