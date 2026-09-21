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

import android.content.ContentResolver;
import android.os.Bundle;
import android.os.SystemProperties;
import android.provider.Settings;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;

import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

/**
 * Settings > Display > Edge screen (S6 edge / S6 edge+ only).
 *
 * Touch area: which curved side the touch IC ignores for palm rejection
 * (persist.sys.kiroos.edge_zone, applied by init.kiroos.rc via set_dead_zone).
 * Edge lighting: SystemUI makes the curved sides glow when a notification arrives
 * (Settings.System kiroos_edge_lighting / kiroos_edge_lighting_color).
 */
public class KiroOSEdgeSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    static final String PROP_EDGE_ZONE = "persist.sys.kiroos.edge_zone";
    /** set_dead_zone values: 3 both edges, 5 left only, 4 right only, 6 off */
    static final String ZONE_BOTH = "3";

    public static final String EDGE_LIGHTING = "kiroos_edge_lighting";
    public static final String EDGE_LIGHTING_COLOR = "kiroos_edge_lighting_color";
    public static final int DEFAULT_EDGE_LIGHTING_COLOR = 0xFF2A78D6;

    private static final String KEY_ZONE = "kiroos_edge_zone";
    private static final String KEY_LIGHTING = "kiroos_edge_lighting";
    private static final String KEY_LIGHTING_COLOR = "kiroos_edge_lighting_color";

    private ListPreference mZone;
    private SwitchPreference mLighting;
    private ListPreference mLightingColor;

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.DISPLAY;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.kiroos_edge_settings);
        final ContentResolver cr = getContentResolver();

        mZone = (ListPreference) findPreference(KEY_ZONE);
        mZone.setValue(SystemProperties.get(PROP_EDGE_ZONE, ZONE_BOTH));
        mZone.setSummary(mZone.getEntry());
        mZone.setOnPreferenceChangeListener(this);

        mLighting = (SwitchPreference) findPreference(KEY_LIGHTING);
        mLighting.setChecked(Settings.System.getInt(cr, EDGE_LIGHTING, 1) != 0);
        mLighting.setOnPreferenceChangeListener(this);

        mLightingColor = (ListPreference) findPreference(KEY_LIGHTING_COLOR);
        mLightingColor.setValue(String.valueOf(Settings.System.getInt(cr, EDGE_LIGHTING_COLOR,
                DEFAULT_EDGE_LIGHTING_COLOR)));
        if (mLightingColor.getEntry() == null) {
            mLightingColor.setValue(String.valueOf(DEFAULT_EDGE_LIGHTING_COLOR));
        }
        mLightingColor.setSummary(mLightingColor.getEntry());
        mLightingColor.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final ContentResolver cr = getContentResolver();
        if (preference == mZone) {
            final String value = (String) newValue;
            SystemProperties.set(PROP_EDGE_ZONE, value);
            mZone.setSummary(mZone.getEntries()[mZone.findIndexOfValue(value)]);
            return true;
        } else if (preference == mLighting) {
            Settings.System.putInt(cr, EDGE_LIGHTING, (Boolean) newValue ? 1 : 0);
            return true;
        } else if (preference == mLightingColor) {
            final String value = (String) newValue;
            Settings.System.putInt(cr, EDGE_LIGHTING_COLOR, (int) Long.parseLong(value));
            mLightingColor.setSummary(
                    mLightingColor.getEntries()[mLightingColor.findIndexOfValue(value)]);
            return true;
        }
        return false;
    }
}
