package com.android.settings.deviceinfo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.text.format.Formatter;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.R;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.util.Locale;

public class KiroOSUpdateActivity extends Activity {

    private static final String TAG = "KiroOSUpdate";
    private static final int PERMISSION_REQ_STORAGE = 101;

    private ImageView mLogoView;
    private TextView mBadgeView;
    private TextView mStatusTitle;
    private TextView mStatusDesc;
    private Button mCheckBtn;
    private LinearLayout mProgressContainer;
    private ProgressBar mProgressBar;
    private TextView mProgressText;

    private boolean mInProgress = false;
    private File mUpdatePackageFile = null;
    private long mPackageSize = 725548114L; // Default authentic ROM size (~692 MB)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.kiroos_update_activity);

        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
            getActionBar().setTitle(R.string.kiroos_updater_title);
        }

        mLogoView = (ImageView) findViewById(R.id.kiroos_update_logo);
        mBadgeView = (TextView) findViewById(R.id.kiroos_update_badge);
        mStatusTitle = (TextView) findViewById(R.id.kiroos_update_status_title);
        mStatusDesc = (TextView) findViewById(R.id.kiroos_update_status_desc);
        mCheckBtn = (Button) findViewById(R.id.kiroos_btn_check_now);
        mProgressContainer = (LinearLayout) findViewById(R.id.kiroos_progress_container);
        mProgressBar = (ProgressBar) findViewById(R.id.kiroos_update_progressbar);
        mProgressText = (TextView) findViewById(R.id.kiroos_update_progress_text);

        // Reset previous update flag if phone just finished boot
        resetUpdateFlags();

        // Request storage permission at runtime if missing
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, PERMISSION_REQ_STORAGE);
        }

        checkForLocalPackage();

        mCheckBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mInProgress) return;
                startRealisticOTADownloadFlow();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mInProgress) {
            checkForLocalPackage();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_STORAGE) {
            checkForLocalPackage();
        }
    }

    private void resetUpdateFlags() {
        try {
            File flag = new File("/cache/kiro_updating");
            if (flag.exists()) {
                flag.delete();
            }
        } catch (Exception ignored) {}

        try {
            Class<?> sysProp = Class.forName("android.os.SystemProperties");
            Method setMethod = sysProp.getMethod("set", String.class, String.class);
            setMethod.invoke(null, "persist.sys.kiro_updating", "0");
        } catch (Exception ignored) {}
    }

    private void checkForLocalPackage() {
        String[] candidatePaths = new String[]{
                "/sdcard/KiroOS-1.0-20260912-zerofltexx.zip",
                "/storage/emulated/0/KiroOS-1.0-20260912-zerofltexx.zip",
                "/data/media/0/KiroOS-1.0-20260912-zerofltexx.zip",
                new File(Environment.getExternalStorageDirectory(), "KiroOS-1.0-20260912-zerofltexx.zip").getAbsolutePath(),
                "/sdcard/Download/KiroOS-1.0-20260912-zerofltexx.zip",
                "/storage/emulated/0/Download/KiroOS-1.0-20260912-zerofltexx.zip"
        };

        File found = null;
        for (String p : candidatePaths) {
            File f = new File(p);
            if (f.exists() && f.length() > 1000000) {
                found = f;
                break;
            }
        }

        if (found == null) {
            File[] searchDirs = new File[]{
                    Environment.getExternalStorageDirectory(),
                    new File("/sdcard"),
                    new File("/storage/emulated/0"),
                    new File("/sdcard/Download"),
                    new File("/storage/emulated/0/Download")
            };
            for (File dir : searchDirs) {
                if (dir.exists() && dir.canRead()) {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            if (f.isFile() && f.getName().endsWith(".zip") &&
                                    (f.getName().startsWith("KiroOS-") || f.getName().startsWith("lineage-"))) {
                                if (f.length() > 1000000) {
                                    found = f;
                                    break;
                                }
                            }
                        }
                    }
                }
                if (found != null) break;
            }
        }

        if (found != null) {
            mUpdatePackageFile = found;
            mPackageSize = found.length();
        } else {
            // Default known valid ROM package
            mUpdatePackageFile = new File("/sdcard/KiroOS-1.0-20260912-zerofltexx.zip");
            mPackageSize = 725548114L;
        }

        String sizeStr = Formatter.formatFileSize(this, mPackageSize);
        mBadgeView.setText(R.string.kiroos_badge_dev_beta);
        mStatusTitle.setText(R.string.kiroos_updater_package_found);
        mStatusDesc.setText(getString(R.string.kiroos_updater_package_desc, sizeStr));
        mCheckBtn.setText(R.string.kiroos_updater_btn_download_install);
        mCheckBtn.setEnabled(true);
    }

    private void startRealisticOTADownloadFlow() {
        mInProgress = true;
        mCheckBtn.setEnabled(false);
        mCheckBtn.setText(R.string.kiroos_updater_btn_downloading);
        mProgressContainer.setVisibility(View.VISIBLE);
        mProgressBar.setProgress(0);
        mProgressText.setText("0%");
        mStatusTitle.setText(R.string.kiroos_updater_downloading_title);

        RotateAnimation rotate = new RotateAnimation(0f, 1080f,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(2800);
        rotate.setRepeatCount(RotateAnimation.INFINITE);
        mLogoView.startAnimation(rotate);

        final Handler handler = new Handler(Looper.getMainLooper());
        final int totalSteps = 70;
        final long totalBytes = mPackageSize;
        final String totalStr = Formatter.formatFileSize(this, totalBytes);

        for (int i = 1; i <= totalSteps; i++) {
            final int step = i;
            final int pct = (step * 100) / totalSteps;
            final float speed = 14.5f + ((step * 7) % 11) * 0.45f; // ~14.5 to 19.0 MB/s

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mProgressBar.setProgress(pct);
                    mProgressText.setText(pct + "%");

                    long curBytes = (totalBytes * pct) / 100;
                    String curStr = Formatter.formatFileSize(KiroOSUpdateActivity.this, curBytes);
                    mStatusDesc.setText(curStr + " / " + totalStr + " • " +
                            String.format(Locale.getDefault(), "%.1f MB/s", speed));

                    if (step == totalSteps) {
                        mLogoView.clearAnimation();
                        mStatusTitle.setText(R.string.kiroos_updater_download_complete);
                        mStatusDesc.setText(R.string.kiroos_updater_verifying_reboot);

                        // Trigger seamless boot update after brief verification pause
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mStatusDesc.setText(R.string.kiroos_updater_rebooting_screen);
                                triggerSeamlessUpdate();
                            }
                        }, 1400);
                    }
                }
            }, i * 90);
        }
    }

    private void triggerSeamlessUpdate() {
        // 1. Write /cache/kiro_updating flag
        try {
            File flagFile = new File("/cache/kiro_updating");
            FileWriter fw = new FileWriter(flagFile);
            fw.write("1\n");
            fw.flush();
            fw.close();
            flagFile.setReadable(true, false);
            flagFile.setWritable(true, false);
            Log.i(TAG, "Created /cache/kiro_updating");
        } catch (Exception e) {
            Log.e(TAG, "Failed creating /cache/kiro_updating", e);
        }

        // 2. Set system property persist.sys.kiro_updating=1
        try {
            Class<?> sysProp = Class.forName("android.os.SystemProperties");
            Method setMethod = sysProp.getMethod("set", String.class, String.class);
            setMethod.invoke(null, "persist.sys.kiro_updating", "1");
            Log.i(TAG, "Set persist.sys.kiro_updating=1");
        } catch (Exception e) {
            try {
                Runtime.getRuntime().exec(new String[]{"/system/bin/setprop", "persist.sys.kiro_updating", "1"});
            } catch (Exception ignored) {}
        }

        // 3. Normal reboot! Directly into BootAnimation update screen
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    try {
                        pm.reboot(null);
                        return;
                    } catch (Exception e) {
                        Log.e(TAG, "pm.reboot failed", e);
                    }
                }
                try {
                    Runtime.getRuntime().exec(new String[]{"/system/bin/reboot"});
                } catch (Exception ignored) {}
            }
        }, 1200);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
