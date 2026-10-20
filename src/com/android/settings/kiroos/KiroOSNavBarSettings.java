/*
 * Copyright (C) 2026 KiroOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.kiroos;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.PreferenceViewHolder;
import android.util.TypedValue;
import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;

import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import cyanogenmod.providers.CMSettings;

/**
 * Settings > Display > Navigation bar (KiroOS Beta 5): the phone's own keys (Home, and Back and
 * Recents under the screen) or the bar on the screen, each drawn by hand. It changes at once,
 * with no restart: dev_force_show_navbar adds or removes the bar in SystemUI, and the window
 * manager turns the capacitive keys off while the bar is shown (KeyDisabler).
 */
public class KiroOSNavBarSettings extends SettingsPreferenceFragment {

    /** true when the bar on the screen is on */
    public static boolean isBarOn(Context c) {
        return CMSettings.Global.getInt(c.getContentResolver(),
                CMSettings.Global.DEV_FORCE_SHOW_NAVBAR, 0) == 1;
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.DISPLAY;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(
                getPrefContext());
        screen.addPreference(new Picker(getPrefContext()));
        setPreferenceScreen(screen);
    }

    private static final class Picker extends Preference {
        Picker(Context context) {
            super(context);
            setLayoutResource(R.layout.kiroos_navbar_picker);
            setSelectable(false);
        }

        @Override
        public void onBindViewHolder(PreferenceViewHolder holder) {
            super.onBindViewHolder(holder);
            final View root = holder.itemView;
            root.findViewById(R.id.navbar_option_keys).setOnClickListener(
                    new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    set(false);
                    show(root);
                }
            });
            root.findViewById(R.id.navbar_option_bar).setOnClickListener(
                    new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    set(true);
                    show(root);
                }
            });
            show(root);
        }

        private void set(boolean bar) {
            if (bar == isBarOn(getContext())) return;
            CMSettings.Global.putInt(getContext().getContentResolver(),
                    CMSettings.Global.DEV_FORCE_SHOW_NAVBAR, bar ? 1 : 0);
        }

        private void show(View root) {
            final Context c = getContext();
            final boolean bar = isBarOn(c);
            ((RadioButton) root.findViewById(R.id.navbar_radio_keys)).setChecked(!bar);
            ((RadioButton) root.findViewById(R.id.navbar_radio_bar)).setChecked(bar);
            card(root.findViewById(R.id.navbar_card_keys), !bar);
            card(root.findViewById(R.id.navbar_card_bar), bar);
            ((TextView) root.findViewById(R.id.navbar_description)).setText(bar
                    ? R.string.kiroos_navbar_bar_summary : R.string.kiroos_navbar_keys_summary);
        }

        /** the drawing's card: outlined in the accent colour when chosen */
        private void card(View v, boolean on) {
            final Context c = getContext();
            final float d = c.getResources().getDisplayMetrics().density;
            final TypedValue accent = new TypedValue();
            c.getTheme().resolveAttribute(android.R.attr.colorAccent, accent, true);
            final GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(20 * d);
            if (on) {
                g.setColor((accent.data & 0x00FFFFFF) | 0x1F000000);
                g.setStroke(Math.round(1.5f * d), accent.data);
            } else {
                g.setColor(0x0A808080);
                g.setStroke(Math.max(1, Math.round(d)), 0x1F808080);
            }
            v.setBackground(g);
        }
    }
}
