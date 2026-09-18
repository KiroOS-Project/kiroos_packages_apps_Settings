package com.android.settings.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.util.Log;

public class NativeEqualizerReceiver extends BroadcastReceiver {
    private static final String TAG = "NativeEqualizerReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Log.d(TAG, "onReceive action: " + action);

        Intent serviceIntent = new Intent(context, NativeEqualizerService.class);
        serviceIntent.setAction(action);

        if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(action) ||
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
            int sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
            serviceIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId);
            serviceIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME,
                    intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME));
        }

        try {
            context.startService(serviceIntent);
        } catch (Exception e) {
            Log.w(TAG, "Failed starting NativeEqualizerService from receiver", e);
        }
    }
}
