package com.android.settings.notification;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.android.settings.R;

import java.util.ArrayList;
import java.util.List;

public class NativeEqualizerActivity extends Activity {

    private static final String TAG = "NativeEqualizerActivity";
    private static final String PREF_NAME = "kiro_equalizer_prefs";
    private static final String PREF_KEY_ENABLED = "eq_enabled";
    private static final String PREF_KEY_PRESET = "eq_preset";
    private static final String PREF_KEY_BASS = "eq_bass";
    private static final String PREF_KEY_BAND_PREFIX = "eq_band_";

    private static Equalizer sEqualizer;
    private static BassBoost sBassBoost;
    private Equalizer mEqualizer;
    private BassBoost mBassBoost;
    private SharedPreferences mPrefs;

    private Switch mMasterSwitch;
    private Spinner mPresetSpinner;
    private SeekBar mBassSeekBar;
    private TextView mBassValueText;
    private LinearLayout mBandsContainer;

    private boolean mIsUpdatingUi = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startService(new Intent(this, NativeEqualizerService.class));
        setContentView(R.layout.kiro_equalizer_activity);
        setTitle(R.string.native_equalizer_title);

        mPrefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        int audioSession = getIntent().getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);

        try {
            if (sEqualizer == null) {
                sEqualizer = new Equalizer(1000, 0);
            }
            if (sBassBoost == null) {
                sBassBoost = new BassBoost(1000, 0);
            }
            mEqualizer = sEqualizer;
            mBassBoost = sBassBoost;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize AudioFX engine", e);
        }

        mMasterSwitch = (Switch) findViewById(R.id.eq_master_switch);
        mPresetSpinner = (Spinner) findViewById(R.id.eq_preset_spinner);
        mBassSeekBar = (SeekBar) findViewById(R.id.eq_bass_seekbar);
        mBassValueText = (TextView) findViewById(R.id.eq_bass_value);
        mBandsContainer = (LinearLayout) findViewById(R.id.eq_bands_container);

        setupControls();
        restoreState();
    }

    private void setupControls() {
        boolean enabled = mPrefs.getBoolean(PREF_KEY_ENABLED, true);
        mMasterSwitch.setChecked(enabled);
        mMasterSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mPrefs.edit().putBoolean(PREF_KEY_ENABLED, isChecked).apply();
                applyEnabledState(isChecked);
                NativeEqualizerService.updateEffects(NativeEqualizerActivity.this);
            }
        });

        if (mBassSeekBar != null) {
            int savedBass = mPrefs.getInt(PREF_KEY_BASS, 0);
            mBassSeekBar.setProgress(savedBass);
            updateBassText(savedBass);
            mBassSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    updateBassText(progress);
                    if (fromUser && mBassBoost != null) {
                        try {
                            mBassBoost.setStrength((short) progress);
                            mPrefs.edit().putInt(PREF_KEY_BASS, progress).apply();
                        } catch (Exception e) {
                            Log.w(TAG, "Error setting bass strength", e);
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (mEqualizer != null) {
            setupPresets();
            setupBands();
        }
    }

    private void updateBassText(int progress) {
        if (mBassValueText != null) {
            int pct = (progress * 100) / 1000;
            mBassValueText.setText(pct + "%");
        }
    }

    private void setupPresets() {
        if (mEqualizer == null || mPresetSpinner == null) return;
        short numPresets = mEqualizer.getNumberOfPresets();
        List<String> presetNames = new ArrayList<>();
        for (short i = 0; i < numPresets; i++) {
            presetNames.add(mEqualizer.getPresetName(i));
        }
        presetNames.add("Personalizzato");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, presetNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mPresetSpinner.setAdapter(adapter);

        int savedPreset = mPrefs.getInt(PREF_KEY_PRESET, 0);
        if (savedPreset >= 0 && savedPreset < presetNames.size()) {
            mPresetSpinner.setSelection(savedPreset);
        }

        mPresetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mIsUpdatingUi) return;
                short num = mEqualizer.getNumberOfPresets();
                if (position < num) {
                    try {
                        mEqualizer.usePreset((short) position);
                        mPrefs.edit().putInt(PREF_KEY_PRESET, position).apply();
                        refreshBandSliders();
                        NativeEqualizerService.updateEffects(NativeEqualizerActivity.this);
                    } catch (Exception e) {
                        Log.w(TAG, "Error applying preset", e);
                    }
                } else {
                    mPrefs.edit().putInt(PREF_KEY_PRESET, position).apply();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupBands() {
        if (mEqualizer == null || mBandsContainer == null) return;
        mBandsContainer.removeAllViews();

        short bands = mEqualizer.getNumberOfBands();
        final short[] levelRange = mEqualizer.getBandLevelRange(); // e.g. -1500 to +1500
        final int minLevel = levelRange[0];
        final int maxLevel = levelRange[1];
        final int range = maxLevel - minLevel;

        for (short i = 0; i < bands; i++) {
            final short bandIndex = i;
            int centerFreq = mEqualizer.getCenterFreq(bandIndex);
            String freqStr = (centerFreq >= 1000000) ? (centerFreq / 1000000) + " kHz" :
                    (centerFreq >= 1000) ? (centerFreq / 1000) + " Hz" : centerFreq + " Hz";

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, 8, 0, 8);

            LinearLayout labelRow = new LinearLayout(this);
            labelRow.setOrientation(LinearLayout.HORIZONTAL);

            TextView freqLabel = new TextView(this);
            freqLabel.setText(freqStr);
            freqLabel.setTextColor(getColor(R.color.kiro_settings_text));
            freqLabel.setTextSize(15);
            LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            labelRow.addView(freqLabel, p1);

            final TextView dbLabel = new TextView(this);
            int currentLevel = mEqualizer.getBandLevel(bandIndex);
            dbLabel.setText(formatDb(currentLevel));
            dbLabel.setTextColor(getColor(R.color.kiro_settings_positive));
            dbLabel.setTextSize(15);
            labelRow.addView(dbLabel);

            row.addView(labelRow);

            SeekBar bar = new SeekBar(this);
            bar.setMax(range);
            bar.setProgress(currentLevel - minLevel);
            bar.setTag(bandIndex);

            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int newLevel = progress + minLevel;
                    dbLabel.setText(formatDb(newLevel));
                    if (fromUser && mEqualizer != null) {
                        try {
                            mEqualizer.setBandLevel(bandIndex, (short) newLevel);
                            mPrefs.edit().putInt(PREF_KEY_BAND_PREFIX + bandIndex, newLevel).apply();
                            // Set spinner to Custom
                            if (mPresetSpinner != null) {
                                int customPos = mEqualizer.getNumberOfPresets();
                                mPresetSpinner.setSelection(customPos);
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error setting band level", e);
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            row.addView(bar);
            mBandsContainer.addView(row);
        }
    }

    private void refreshBandSliders() {
        if (mEqualizer == null || mBandsContainer == null) return;
        mIsUpdatingUi = true;
        short[] levelRange = mEqualizer.getBandLevelRange();
        int minLevel = levelRange[0];
        int count = mBandsContainer.getChildCount();
        for (int i = 0; i < count; i++) {
            View v = mBandsContainer.getChildAt(i);
            if (v instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) v;
                SeekBar bar = (SeekBar) row.getChildAt(1);
                LinearLayout labelRow = (LinearLayout) row.getChildAt(0);
                TextView dbLabel = (TextView) labelRow.getChildAt(1);
                short band = (Short) bar.getTag();
                int level = mEqualizer.getBandLevel(band);
                bar.setProgress(level - minLevel);
                dbLabel.setText(formatDb(level));
            }
        }
        mIsUpdatingUi = false;
    }

    private String formatDb(int level) {
        int db = level / 100;
        return (db > 0 ? "+" : "") + db + " dB";
    }

    private void restoreState() {
        boolean enabled = mPrefs.getBoolean(PREF_KEY_ENABLED, true);
        applyEnabledState(enabled);

        if (mBassBoost != null) {
            try {
                int bass = mPrefs.getInt(PREF_KEY_BASS, 0);
                mBassBoost.setStrength((short) bass);
            } catch (Exception e) {
                Log.w(TAG, "Error restoring bass", e);
            }
        }

        if (mEqualizer != null) {
            short numPresets = mEqualizer.getNumberOfPresets();
            int savedPreset = mPrefs.getInt(PREF_KEY_PRESET, 0);
            if (savedPreset < numPresets) {
                try {
                    mEqualizer.usePreset((short) savedPreset);
                    refreshBandSliders();
                        NativeEqualizerService.updateEffects(NativeEqualizerActivity.this);
                } catch (Exception e) {
                    Log.w(TAG, "Error restoring preset", e);
                }
            } else {
                short bands = mEqualizer.getNumberOfBands();
                for (short i = 0; i < bands; i++) {
                    int defaultLevel = mEqualizer.getBandLevel(i);
                    int level = mPrefs.getInt(PREF_KEY_BAND_PREFIX + i, defaultLevel);
                    try {
                        mEqualizer.setBandLevel(i, (short) level);
                    } catch (Exception e) {
                        Log.w(TAG, "Error restoring band " + i, e);
                    }
                }
                refreshBandSliders();
                        NativeEqualizerService.updateEffects(NativeEqualizerActivity.this);
            }
        }
    }

    private void applyEnabledState(boolean enabled) {
        if (mEqualizer != null) {
            try {
                mEqualizer.setEnabled(enabled);
            } catch (Exception e) {
                Log.w(TAG, "Error toggling equalizer", e);
            }
        }
        if (mBassBoost != null) {
            try {
                mBassBoost.setEnabled(enabled);
            } catch (Exception e) {
                Log.w(TAG, "Error toggling bass boost", e);
            }
        }
        if (mPresetSpinner != null) mPresetSpinner.setEnabled(enabled);
        if (mBassSeekBar != null) mBassSeekBar.setEnabled(enabled);
        if (mBandsContainer != null) {
            int count = mBandsContainer.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = mBandsContainer.getChildAt(i);
                child.setEnabled(enabled);
                if (child instanceof ViewGroup) {
                    ViewGroup vg = (ViewGroup) child;
                    for (int j = 0; j < vg.getChildCount(); j++) {
                        vg.getChildAt(j).setEnabled(enabled);
                    }
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Keep equalizer and bass boost running permanently in background on session 0
    }
}
