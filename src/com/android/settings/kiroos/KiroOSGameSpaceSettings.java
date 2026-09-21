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
import android.graphics.Point;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.util.Log;
import android.view.Display;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

/**
 * Settings > Game Space.
 *
 * 720p mode: renders the whole UI at 720x1280 / 320 dpi (same as "wm size 720x1280" and
 * "wm density 320"), so the Mali-T760 fills 44% of the pixels of WQHD: higher frame rates and
 * less heat in games. Turning it off restores the panel's native size and density.
 * Game performance: switches the battery profile to "extreme" while Game Space is on and
 * restores the previous one afterwards. No interruptions: blocks heads-up notifications.
 */
public class KiroOSGameSpaceSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "KiroOSGameSpace";

    private static final String KEY_DOWNSCALE = "kiroos_game_downscale";
    private static final String KEY_PERFORMANCE = "kiroos_game_performance";
    private static final String KEY_NO_HEADS_UP = "kiroos_game_no_heads_up";

    static final String PROP_PERF = "persist.sys.kiroos.perf";
    /** profile in use before Game Space raised it, restored when it is turned off */
    static final String PROP_PERF_SAVED = "persist.sys.kiroos.perf_game";

    static final int GAME_WIDTH = 720;
    static final int GAME_HEIGHT = 1280;
    static final int GAME_DENSITY = 320;

    private SwitchPreference mDownscale;
    private SwitchPreference mPerformance;
    private SwitchPreference mNoHeadsUp;

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.DISPLAY;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.kiroos_game_space_settings);

        mDownscale = (SwitchPreference) findPreference(KEY_DOWNSCALE);
        mDownscale.setChecked(isDownscaled());
        mDownscale.setOnPreferenceChangeListener(this);

        mPerformance = (SwitchPreference) findPreference(KEY_PERFORMANCE);
        mPerformance.setChecked(!SystemProperties.get(PROP_PERF_SAVED, "").isEmpty());
        mPerformance.setOnPreferenceChangeListener(this);

        mNoHeadsUp = (SwitchPreference) findPreference(KEY_NO_HEADS_UP);
        mNoHeadsUp.setChecked(Settings.Global.getInt(getContentResolver(),
                Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, 1) == 0);
        mNoHeadsUp.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final boolean on = (Boolean) newValue;
        if (preference == mDownscale) {
            return setDownscaled(on);
        } else if (preference == mPerformance) {
            setGamePerformance(on);
            return true;
        } else if (preference == mNoHeadsUp) {
            final ContentResolver cr = getContentResolver();
            Settings.Global.putInt(cr, Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, on ? 0 : 1);
            return true;
        }
        return false;
    }

    /** True when the forced display size is the Game Space 720p one. */
    static boolean isDownscaled() {
        try {
            final IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
            final Point initial = new Point();
            final Point base = new Point();
            wm.getInitialDisplaySize(Display.DEFAULT_DISPLAY, initial);
            wm.getBaseDisplaySize(Display.DEFAULT_DISPLAY, base);
            return base.x == GAME_WIDTH && base.y == GAME_HEIGHT && initial.x != GAME_WIDTH;
        } catch (RemoteException e) {
            return false;
        }
    }

    static boolean setDownscaled(boolean on) {
        try {
            final IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
            final int userId = UserHandle.myUserId();
            if (on) {
                wm.setForcedDisplaySize(Display.DEFAULT_DISPLAY, GAME_WIDTH, GAME_HEIGHT);
                wm.setForcedDisplayDensityForUser(Display.DEFAULT_DISPLAY, GAME_DENSITY, userId);
            } else {
                wm.clearForcedDisplaySize(Display.DEFAULT_DISPLAY);
                wm.clearForcedDisplayDensityForUser(Display.DEFAULT_DISPLAY, userId);
            }
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to change the display size", e);
            return false;
        }
    }

    static void setGamePerformance(boolean on) {
        if (on) {
            final String current = SystemProperties.get(PROP_PERF, "balanced");
            if (SystemProperties.get(PROP_PERF_SAVED, "").isEmpty()) {
                SystemProperties.set(PROP_PERF_SAVED, current);
            }
            SystemProperties.set(PROP_PERF, "extreme");
        } else {
            final String saved = SystemProperties.get(PROP_PERF_SAVED, "");
            SystemProperties.set(PROP_PERF, saved.isEmpty() ? "balanced" : saved);
            SystemProperties.set(PROP_PERF_SAVED, "");
        }
    }
}
