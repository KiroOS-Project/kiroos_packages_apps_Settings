package com.android.settings.deviceinfo;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceViewHolder;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;

import com.android.settings.R;

import java.io.File;

public class KiroOSColorOSCardPreference extends Preference {

    public KiroOSColorOSCardPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public KiroOSColorOSCardPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public KiroOSColorOSCardPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public KiroOSColorOSCardPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        setLayoutResource(R.layout.kiroos_coloros_card);
        setSelectable(false);
    }

    private boolean isUpdatePackageAvailable() {
        String[] candidatePaths = new String[]{
                "/sdcard/KiroOS-1.0-20260912-zerofltexx.zip",
                "/storage/emulated/0/KiroOS-1.0-20260912-zerofltexx.zip",
                "/data/media/0/KiroOS-1.0-20260912-zerofltexx.zip",
                "/sdcard/Download/KiroOS-1.0-20260912-zerofltexx.zip",
                "/storage/emulated/0/Download/KiroOS-1.0-20260912-zerofltexx.zip"
        };
        for (String p : candidatePaths) {
            File f = new File(p);
            if (f.exists() && f.length() > 1000000) {
                return true;
            }
        }
        try {
            File ext = Environment.getExternalStorageDirectory();
            if (ext != null && ext.exists()) {
                File direct = new File(ext, "KiroOS-1.0-20260912-zerofltexx.zip");
                if (direct.exists() && direct.length() > 1000000) return true;
                File[] files = ext.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile() && f.getName().startsWith("KiroOS-") && f.getName().endsWith(".zip") && f.length() > 1000000) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return true; // Target package is staged for update
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View root = holder.itemView;

        Button checkUpdatesBtn = (Button) root.findViewById(R.id.kiroos_btn_check_updates);
        final View heroCard = root.findViewById(R.id.kiroos_hero_card_view);
        TextView topBadge = (TextView) root.findViewById(R.id.kiroos_top_badge);
        TextView statusBadge = (TextView) root.findViewById(R.id.kiroos_status_badge);
        TextView versionSub = (TextView) root.findViewById(R.id.kiroos_software_version_sub);
        TextView changelogTitle = (TextView) root.findViewById(R.id.kiroos_changelog_title);
        TextView changelogSummary = (TextView) root.findViewById(R.id.kiroos_changelog_summary);

        boolean updateReady = isUpdatePackageAvailable();

        if (updateReady) {
            if (topBadge != null) {
                topBadge.setText(R.string.kiroos_badge_dev_beta);
            }
            if (statusBadge != null) {
                statusBadge.setText(R.string.kiroos_status_update_available);
                statusBadge.setTextColor(0xFF00E5FF);
            }
            if (versionSub != null) {
                versionSub.setText(R.string.kiroos_software_version_beta2);
            }
            if (changelogTitle != null) {
                changelogTitle.setText(R.string.kiroos_changelog_title_beta2);
            }
            if (changelogSummary != null) {
                changelogSummary.setText(R.string.kiroos_release_notes_beta2);
            }
            if (checkUpdatesBtn != null) {
                checkUpdatesBtn.setText(R.string.kiroos_btn_install_now);
            }
        }

        // Fluid touch spring animation on Hero Card
        if (heroCard != null) {
            heroCard.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        v.animate().scaleX(0.982f).scaleY(0.982f).setDuration(120)
                                .setInterpolator(new AccelerateDecelerateInterpolator()).start();
                    } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(180)
                                .setInterpolator(new AccelerateDecelerateInterpolator()).start();
                    }
                    return false;
                }
            });

            heroCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launchInternalUpdater();
                }
            });
        }

        if (checkUpdatesBtn != null) {
            checkUpdatesBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launchInternalUpdater();
                }
            });
        }
    }

    private void launchInternalUpdater() {
        Context context = getContext();
        Intent intent = new Intent(context, KiroOSUpdateActivity.class);
        context.startActivity(intent);
    }
}
