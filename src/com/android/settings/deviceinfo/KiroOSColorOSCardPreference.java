package com.android.settings.deviceinfo;

import android.content.Context;
import android.content.Intent;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceViewHolder;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;

import com.android.settings.R;

import java.util.Locale;

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

        // Installed version and channel come from the build properties.
        String channel = KiroOSUpdateManager.installedChannel();
        if (topBadge != null) {
            topBadge.setVisibility(TextUtils.isEmpty(channel) ? View.GONE : View.VISIBLE);
            topBadge.setText(channel.toUpperCase(Locale.getDefault()));
        }
        if (versionSub != null) {
            versionSub.setText(KiroOSUpdateManager.installedVersion());
        }

        // Update status comes from the last real check (screen or background job): never claim
        // "up to date" on its own, only show what the last answer of the server said and when.
        Context context = getContext();
        KiroOSUpdateJobService.schedule(context);
        KiroOSUpdateManager.UpdateInfo cached = KiroOSUpdateManager.loadCachedInfo(context);
        boolean available = cached != null && KiroOSUpdateManager.isNewer(cached);
        if (changelogTitle != null) changelogTitle.setVisibility(available ? View.VISIBLE : View.GONE);
        if (changelogSummary != null) {
            changelogSummary.setVisibility(available && !TextUtils.isEmpty(cached.changelog)
                    ? View.VISIBLE : View.GONE);
        }
        if (available) {
            if (statusBadge != null) {
                statusBadge.setText(R.string.kiroos_status_update_available);
                statusBadge.setTextColor(0xFF00E5FF);
            }
            if (changelogTitle != null) {
                changelogTitle.setText(context.getString(R.string.kiroos_ota_release_notes,
                        cached.displayName()));
            }
            if (changelogSummary != null && !TextUtils.isEmpty(cached.changelog)) {
                changelogSummary.setText(cached.changelog);
            }
            if (checkUpdatesBtn != null) {
                checkUpdatesBtn.setText(context.getString(R.string.kiroos_ota_download,
                        Formatter.formatFileSize(context, cached.size)));
            }
        } else if (statusBadge != null) {
            long last = KiroOSUpdateManager.lastCheck(context);
            if (last == 0) {
                statusBadge.setText(R.string.kiroos_status_not_checked);
            } else {
                statusBadge.setText(context.getString(R.string.kiroos_ota_last_check,
                        android.text.format.DateUtils.formatDateTime(context, last,
                                android.text.format.DateUtils.FORMAT_SHOW_DATE
                                        | android.text.format.DateUtils.FORMAT_SHOW_TIME)));
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
