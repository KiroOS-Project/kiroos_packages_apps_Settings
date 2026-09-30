package com.android.settings.deviceinfo;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceViewHolder;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.TextView;

import com.android.settings.R;

import java.util.Locale;

public class KiroOSColorOSCardPreference extends Preference
        implements com.android.settings.kiroos.KiroOwnCard {

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
        TextView deviceTitle = (TextView) root.findViewById(R.id.kiroos_device_title);
        if (deviceTitle != null) {
            // the model this build runs on (S6, S6 edge or S6 edge+), not a fixed name
            deviceTitle.setText(com.android.settings.kiroos.KiroOSDevice.displayName());
        }

        // Installed version and channel come from the build properties.
        String channel = KiroOSUpdateManager.installedChannel();
        if (topBadge != null) {
            topBadge.setVisibility(TextUtils.isEmpty(channel) ? View.GONE : View.VISIBLE);
            topBadge.setText(channel.toUpperCase(Locale.getDefault()));
        }
        if (versionSub != null) {
            // The card already says "KiroOS": show just the version number under it.
            String version = KiroOSUpdateManager.installedVersion();
            if (version.startsWith("KiroOS ")) version = version.substring(7);
            versionSub.setText(getContext().getString(R.string.kiroos_about_version, version));
        }

        // Update status comes from the last real check (screen or background job): never claim
        // "up to date" on its own, only show what the last answer of the server said and when.
        Context context = getContext();
        KiroOSUpdateJobService.schedule(context);
        KiroOSUpdateManager.UpdateInfo cached = KiroOSUpdateManager.loadCachedInfo(context);
        boolean available = cached != null && KiroOSUpdateManager.isNewer(cached);
        View statusDot = root.findViewById(R.id.kiroos_status_dot);
        if (changelogTitle != null) changelogTitle.setVisibility(available ? View.VISIBLE : View.GONE);
        if (changelogSummary != null) {
            changelogSummary.setVisibility(available && !TextUtils.isEmpty(cached.changelog)
                    ? View.VISIBLE : View.GONE);
        }
        if (statusDot != null) {
            statusDot.animate().cancel();
            statusDot.setAlpha(1f);
            statusDot.setScaleX(1f);
            statusDot.setScaleY(1f);
            statusDot.setBackgroundTintList(ColorStateList.valueOf(
                    available ? getContext().getColor(R.color.kiroos_upd_text)
                            : getContext().getColor(R.color.kiroos_upd_text4)));
        }
        if (available) {
            if (statusBadge != null) {
                statusBadge.setText(R.string.kiroos_status_update_available);
                statusBadge.setTextColor(getContext().getColor(R.color.kiroos_upd_text));
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
                checkUpdatesBtn.setBackgroundResource(R.drawable.kiroos_upd_button);
                checkUpdatesBtn.setTextColor(getContext().getColor(R.color.kiroos_upd_button_fg));
            }
            if (statusDot != null) pulse(statusDot);
            // Announce the new update once per version: the card settles in like a widget.
            String seenKey = "kiroos_card_seen_" + cached.displayName();
            android.content.SharedPreferences prefs = context.getSharedPreferences(
                    "kiroos_about_card", Context.MODE_PRIVATE);
            if (heroCard != null && !prefs.getBoolean(seenKey, false)) {
                prefs.edit().putBoolean(seenKey, true).apply();
                heroCard.setScaleX(0.94f);
                heroCard.setScaleY(0.94f);
                heroCard.setAlpha(0f);
                heroCard.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(120)
                        .setDuration(520).setInterpolator(new OvershootInterpolator(1.4f)).start();
            }
        } else if (statusBadge != null) {
            statusBadge.setTextColor(getContext().getColor(R.color.kiroos_upd_text2));
            if (checkUpdatesBtn != null) {
                checkUpdatesBtn.setText(R.string.kiroos_btn_check_updates_text);
                checkUpdatesBtn.setBackgroundResource(R.drawable.kiroos_about_button_secondary);
                checkUpdatesBtn.setTextColor(getContext().getColor(R.color.kiroos_upd_text));
            }
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
                    launchInternalUpdater(heroCard);
                }
            });
        }

        if (checkUpdatesBtn != null) {
            checkUpdatesBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launchInternalUpdater(heroCard != null ? heroCard : v);
                }
            });
        }
    }

    /** Soft "breathing" of the status dot while an update is waiting. */
    private static void pulse(final View dot) {
        final ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(dot,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.35f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.35f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.55f));
        anim.setDuration(900);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setRepeatMode(ValueAnimator.REVERSE);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        Object old = dot.getTag(R.id.kiroos_status_dot);
        if (old instanceof ObjectAnimator) ((ObjectAnimator) old).cancel();
        dot.setTag(R.id.kiroos_status_dot, anim);
        dot.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                anim.cancel();
                v.removeOnAttachStateChangeListener(this);
            }
        });
        anim.start();
    }

    /** Opens the update screen growing out of the card, like opening a widget. */
    private void launchInternalUpdater(View from) {
        Context context = getContext();
        Intent intent = new Intent(context, KiroOSUpdateActivity.class);
        if (from != null && from.isAttachedToWindow() && from.getWidth() > 0
                && from.getHeight() > 0) {
            try {
                ActivityOptions opts = ActivityOptions.makeClipRevealAnimation(from, 0, 0,
                        from.getWidth(), from.getHeight());
                context.startActivity(intent, opts.toBundle());
                return;
            } catch (RuntimeException e) {
                // the animation is optional: open the screen without it
                android.util.Log.w("KiroOSUpdate", "Clip reveal failed", e);
            }
        }
        context.startActivity(intent);
    }
}
