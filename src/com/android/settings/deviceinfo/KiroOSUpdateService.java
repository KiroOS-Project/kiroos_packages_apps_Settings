package com.android.settings.deviceinfo;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import com.android.settings.R;

/**
 * Keeps the Settings process in the foreground while an update is downloaded and
 * verified, showing a progress notification.
 */
public class KiroOSUpdateService extends Service {

    private static final int NOTIFICATION_ID = 0x4b10;

    private static KiroOSUpdateService sRunning;
    private Notification.Builder mBuilder;
    private int mLastPercent = -1;

    static void start(Context context) {
        context.startService(new Intent(context, KiroOSUpdateService.class));
    }

    static void update(Context context, KiroOSUpdateManager.State state) {
        KiroOSUpdateService s = sRunning;
        if (s == null) return;
        if (state == KiroOSUpdateManager.State.VERIFYING) {
            s.mLastPercent = -1;
            s.mBuilder.setContentText(s.getString(R.string.kiroos_ota_verifying));
            s.mBuilder.setProgress(100, 0, true);
            s.startForeground(NOTIFICATION_ID, s.mBuilder.build());
        } else if (state != KiroOSUpdateManager.State.DOWNLOADING) {
            s.stopForeground(true);
            s.stopSelf();
        }
    }

    static void progress(Context context, long done, long total) {
        KiroOSUpdateService s = sRunning;
        if (s == null || total <= 0) return;
        int pct = (int) (done * 100 / total);
        if (pct == s.mLastPercent) return;
        s.mLastPercent = pct;
        s.mBuilder.setProgress(100, pct, false);
        s.startForeground(NOTIFICATION_ID, s.mBuilder.build());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sRunning = this;
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, KiroOSUpdateActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder = new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(getString(R.string.kiroos_ota_notification_title))
                .setContentText(getString(R.string.kiroos_ota_downloading))
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(pi)
                .setProgress(100, 0, true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, mBuilder.build());
        KiroOSUpdateManager.State s = KiroOSUpdateManager.get(this).getState();
        if (s != KiroOSUpdateManager.State.DOWNLOADING && s != KiroOSUpdateManager.State.VERIFYING) {
            stopForeground(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        sRunning = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
