package com.android.settings.deviceinfo;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.android.settings.R;

/**
 * Checks the KiroOS update server in the background (every few hours, when a network is
 * available) and posts a notification when a new build is published. The result is cached,
 * so the update stays visible even if the server is offline afterwards.
 */
public class KiroOSUpdateJobService extends JobService {

    private static final String TAG = "KiroOSUpdateJob";
    private static final int JOB_ID = 0x4b1e;
    private static final int NOTIFICATION_ID = 0x4b11;
    private static final long PERIOD_MS = 4 * 60 * 60 * 1000L;

    /** Schedules the periodic check (no-op if it is already scheduled). */
    public static void schedule(Context context) {
        JobScheduler js = context.getSystemService(JobScheduler.class);
        if (js == null) return;
        for (JobInfo job : js.getAllPendingJobs()) {
            if (job.getId() == JOB_ID) return;
        }
        js.schedule(new JobInfo.Builder(JOB_ID,
                new ComponentName(context, KiroOSUpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(PERIOD_MS)
                .setPersisted(true)
                .build());
    }

    /** Schedules the job after boot and after an OTA (package replaced). */
    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            schedule(context);
        }
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    KiroOSUpdateManager manager = KiroOSUpdateManager.get(KiroOSUpdateJobService.this);
                    manager.checkBlocking();
                    KiroOSUpdateManager.UpdateInfo info = manager.getInfo();
                    if (manager.getState() == KiroOSUpdateManager.State.AVAILABLE && info != null) {
                        notifyUpdate(info);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Background check failed", e);
                }
                jobFinished(params, false);
            }
        }, "KiroOSUpdateJob").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    private void notifyUpdate(KiroOSUpdateManager.UpdateInfo info) {
        long notified = KiroOSUpdateManager.prefs(this).getLong(KiroOSUpdateManager.PREF_NOTIFIED, 0);
        if (notified == info.timestamp) return;
        KiroOSUpdateManager.prefs(this).edit()
                .putLong(KiroOSUpdateManager.PREF_NOTIFIED, info.timestamp).apply();

        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, KiroOSUpdateActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_kiroos_logo)
                .setContentTitle(getString(R.string.kiroos_status_update_available))
                .setContentText(info.displayName())
                .setStyle(new Notification.BigTextStyle().bigText(
                        info.displayName() + (info.changelog.isEmpty() ? "" : "\n" + info.changelog)))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, n);
    }
}
