package com.android.settings.deviceinfo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemProperties;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.PathInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.R;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

public class KiroOSUpdateActivity extends Activity implements KiroOSUpdateManager.Listener {

    private KiroOSUpdateManager mManager;

    private ImageView mLogoView;
    private KiroOSRingView mRing;
    private TextView mRingPercent;
    private TextView mVersionHeader;
    private TextView mBadgeView;
    private TextView mStatusTitle;
    private TextView mStatusDesc;
    private Button mActionBtn;
    private LinearLayout mProgressContainer;
    private ProgressBar mProgressBar;
    private TextView mProgressText;
    private View mNotesCard;
    private TextView mNotesTitle;
    private TextView mNotesContent;

    private boolean mCheckedOnOpen;
    private KiroOSUpdateManager.State mLastState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.kiroos_update_activity);

        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
            getActionBar().setTitle(R.string.kiroos_updater_title);
            // Flat black bar that blends with the page, like the rest of this screen.
            getActionBar().setElevation(0);
            getActionBar().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF000000));
        }
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().setNavigationBarColor(0xFF000000);

        mManager = KiroOSUpdateManager.get(this);
        KiroOSUpdateJobService.schedule(this);

        mLogoView = (ImageView) findViewById(R.id.kiroos_update_logo);
        mRing = (KiroOSRingView) findViewById(R.id.kiroos_update_ring);
        mRingPercent = (TextView) findViewById(R.id.kiroos_update_ring_percent);
        mVersionHeader = (TextView) findViewById(R.id.kiroos_update_version_header_text);
        mBadgeView = (TextView) findViewById(R.id.kiroos_update_badge);
        mStatusTitle = (TextView) findViewById(R.id.kiroos_update_status_title);
        mStatusDesc = (TextView) findViewById(R.id.kiroos_update_status_desc);
        mActionBtn = (Button) findViewById(R.id.kiroos_btn_check_now);
        mProgressContainer = (LinearLayout) findViewById(R.id.kiroos_progress_container);
        mProgressBar = (ProgressBar) findViewById(R.id.kiroos_update_progressbar);
        mProgressText = (TextView) findViewById(R.id.kiroos_update_progress_text);
        mNotesCard = findViewById(R.id.kiroos_release_notes_card);
        mNotesTitle = (TextView) findViewById(R.id.kiroos_release_notes_title);
        mNotesContent = (TextView) findViewById(R.id.kiroos_release_notes_content);

        mProgressBar.setMax(1000);

        // Long press on the version header to change the update server address.
        mVersionHeader.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showServerDialog();
                return true;
            }
        });

        mActionBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onActionClicked();
            }
        });

        populateSpecs((LinearLayout) findViewById(R.id.kiroos_spec_rows));

        android.widget.Switch stats = (android.widget.Switch) findViewById(R.id.kiroos_stats_switch);
        stats.setChecked(KiroOSUpdateManager.statsEnabled(this));
        stats.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                KiroOSUpdateManager.setStatsEnabled(KiroOSUpdateActivity.this, on);
            }
        });

        if (savedInstanceState == null) {
            playEntrance();
        }
    }

    // ------------------------------------------------------------------ motion

    /**
     * While the window grows out of the About card, the content settles in from below one
     * block at a time (ring, version, status, cards, button), like a widget opening.
     */
    private void playEntrance() {
        View scroll = findViewById(R.id.kiroos_update_scroll);
        if (!(scroll instanceof ViewGroup) || ((ViewGroup) scroll).getChildCount() == 0) return;
        ViewGroup content = (ViewGroup) ((ViewGroup) scroll).getChildAt(0);
        java.util.ArrayList<View> seq = new java.util.ArrayList<>();
        if (content.getChildAt(0) instanceof ViewGroup) {
            ViewGroup hero = (ViewGroup) content.getChildAt(0);
            for (int i = 0; i < hero.getChildCount(); i++) seq.add(hero.getChildAt(i));
        }
        for (int i = 1; i < content.getChildCount(); i++) seq.add(content.getChildAt(i));
        View button = mActionBtn != null ? (View) mActionBtn.getParent() : null;
        if (button != null) seq.add(button);

        final float dy = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28,
                getResources().getDisplayMetrics());
        PathInterpolator ease = new PathInterpolator(0.2f, 0.9f, 0.1f, 1f);
        for (int i = 0; i < seq.size(); i++) {
            View v = seq.get(i);
            v.setAlpha(0f);
            v.setTranslationY(dy);
            if (i == 0) {
                v.setScaleX(0.82f);
                v.setScaleY(0.82f);
            }
            v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setStartDelay(110 + i * 45L).setDuration(480).setInterpolator(ease)
                    .withLayer().start();
        }
    }

    /** A new update was just found: the ring pops and the news slide up into place. */
    private void playUpdateFound() {
        View ringFrame = (View) mRing.getParent();
        ringFrame.animate().cancel();
        ringFrame.setScaleX(0.9f);
        ringFrame.setScaleY(0.9f);
        ringFrame.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(560)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();

        final float dy = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20,
                getResources().getDisplayMetrics());
        PathInterpolator ease = new PathInterpolator(0.2f, 0.9f, 0.1f, 1f);
        View[] seq = { mStatusTitle, mStatusDesc, mNotesCard, (View) mActionBtn.getParent() };
        for (int i = 0; i < seq.length; i++) {
            View v = seq[i];
            if (v.getVisibility() != View.VISIBLE) continue;
            v.animate().cancel();
            v.setAlpha(0f);
            v.setTranslationY(dy);
            v.animate().alpha(1f).translationY(0f).setStartDelay(80 + i * 60L)
                    .setDuration(440).setInterpolator(ease).withLayer().start();
        }
    }

    @Override
    public void finish() {
        super.finish();
        // Closing shrinks the page back instead of the default slide.
        overridePendingTransition(0, R.anim.kiroos_upd_close);
    }

    // ------------------------------------------------------------------ hardware card

    /** One row per spec: label at the start of the line, value next to it (wraps in its column). */
    private void populateSpecs(LinearLayout rows) {
        if (rows == null) return;
        String abis = TextUtils.join(", ", Build.SUPPORTED_ABIS);
        addSpec(rows, R.string.kiroos_spec_model, "Samsung Galaxy S6 (" + Build.MODEL + ")");
        addSpec(rows, R.string.kiroos_spec_codename, Build.DEVICE);
        addSpec(rows, R.string.kiroos_spec_platform, "Samsung Exynos 7420 Octa (14 nm)");
        addSpec(rows, R.string.kiroos_spec_cpu, "4× Cortex-A57 @ " + cpuMaxFreq(4)
                + " + 4× Cortex-A53 @ " + cpuMaxFreq(0));
        addSpec(rows, R.string.kiroos_spec_gpu, "ARM Mali-T760 MP8");
        addSpec(rows, R.string.kiroos_spec_arch, "ARMv8-A 64-bit (" + abis + ")");
        long memTotal = memTotalBytes();
        if (memTotal > 0) {
            addSpec(rows, R.string.kiroos_spec_ram, Formatter.formatShortFileSize(this, memTotal));
        }
        try {
            StatFs data = new StatFs(Environment.getDataDirectory().getPath());
            addSpec(rows, R.string.kiroos_spec_storage, getString(R.string.kiroos_spec_storage_value,
                    Formatter.formatShortFileSize(this, data.getTotalBytes()),
                    Formatter.formatShortFileSize(this, data.getAvailableBytes())));
        } catch (IllegalArgumentException e) {
            // data partition not readable: skip the row
        }
        addSpec(rows, R.string.kiroos_spec_display, "5.1\" Super AMOLED, 1440 × 2560 (577 ppi)");
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(size);
        addSpec(rows, R.string.kiroos_spec_resolution, Math.min(size.x, size.y) + " × "
                + Math.max(size.x, size.y) + " (" + getResources().getDisplayMetrics().densityDpi
                + " dpi)");
        addSpec(rows, R.string.kiroos_spec_android, Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")");
        addSpec(rows, R.string.kiroos_spec_security, Build.VERSION.SECURITY_PATCH);
        addSpec(rows, R.string.kiroos_spec_kernel, System.getProperty("os.version", ""));
        addSpec(rows, R.string.kiroos_spec_baseband, SystemProperties.get("gsm.version.baseband", ""));
        long buildUtc = KiroOSUpdateManager.installedTimestamp();
        addSpec(rows, R.string.kiroos_spec_build, SystemProperties.get("ro.build.display.id", "")
                + (buildUtc > 0 ? " (" + DateUtils.formatDateTime(this, buildUtc * 1000L,
                        DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR) + ")" : ""));
        addSpec(rows, R.string.kiroos_spec_channel, KiroOSUpdateManager.installedChannel());
    }

    private void addSpec(LinearLayout rows, int labelRes, String value) {
        if (TextUtils.isEmpty(value)) return;
        float dp = getResources().getDisplayMetrics().density;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = (int) (10 * dp);
        row.setLayoutParams(rowLp);

        TextView label = new TextView(this);
        label.setText(labelRes);
        label.setTextColor(0xFF8E8E93);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setGravity(Gravity.START);
        label.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0.38f));

        TextView val = new TextView(this);
        val.setText(value);
        val.setTextColor(0xFFFFFFFF);
        val.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        val.setGravity(Gravity.START);
        val.setTextIsSelectable(true);
        LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0.62f);
        valLp.setMarginStart((int) (8 * dp));
        val.setLayoutParams(valLp);

        row.addView(label);
        row.addView(val);
        rows.addView(row);
    }

    private static String cpuMaxFreq(int cpu) {
        String khz = readFirstLine("/sys/devices/system/cpu/cpu" + cpu + "/cpufreq/cpuinfo_max_freq");
        try {
            return String.format(Locale.US, "%.2f GHz", Long.parseLong(khz.trim()) / 1000000.0);
        } catch (Exception e) {
            return cpu >= 4 ? "2.10 GHz" : "1.50 GHz";
        }
    }

    private static long memTotalBytes() {
        try {
            BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"));
            try {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("MemTotal:")) {
                        return Long.parseLong(line.replaceAll("[^0-9]", "")) * 1024L;
                    }
                }
            } finally {
                r.close();
            }
        } catch (Exception e) {
            // ignore
        }
        return 0;
    }

    private static String readFirstLine(String path) {
        try {
            BufferedReader r = new BufferedReader(new FileReader(path));
            try {
                String line = r.readLine();
                return line != null ? line : "";
            } finally {
                r.close();
            }
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mManager.addListener(this);
        KiroOSUpdateManager.State s = mManager.getState();
        if (!mCheckedOnOpen && (s == KiroOSUpdateManager.State.IDLE
                || s == KiroOSUpdateManager.State.UP_TO_DATE
                || s == KiroOSUpdateManager.State.ERROR)) {
            mCheckedOnOpen = true;
            mManager.check();
        }
    }

    @Override
    protected void onPause() {
        mManager.removeListener(this);
        super.onPause();
    }

    private void onActionClicked() {
        switch (mManager.getState()) {
            case AVAILABLE:
                mManager.startDownload();
                break;
            case DOWNLOADING:
            case VERIFYING:
                mManager.cancel();
                break;
            case READY:
                confirmInstall();
                break;
            case INSTALLING:
            case CHECKING:
                break;
            default:
                mManager.check();
                break;
        }
    }

    private void confirmInstall() {
        int required = mManager.batteryRequirement();
        if (required > 0) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.kiroos_ota_error)
                    .setMessage(getString(R.string.kiroos_ota_err_battery, required))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.kiroos_ota_install)
                .setMessage(R.string.kiroos_ota_ready_desc)
                .setPositiveButton(R.string.kiroos_ota_install, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mManager.install();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showServerDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        input.setText(KiroOSUpdateManager.serverUrl(this));
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle(R.string.kiroos_ota_server_title)
                .setView(box)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        KiroOSUpdateManager.setServerUrl(KiroOSUpdateActivity.this,
                                input.getText().toString());
                        Toast.makeText(KiroOSUpdateActivity.this, R.string.kiroos_ota_server_saved,
                                Toast.LENGTH_SHORT).show();
                        mManager.check();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ Listener

    @Override
    public void onStateChanged(KiroOSUpdateManager.State state) {
        KiroOSUpdateManager.UpdateInfo info = mManager.getInfo();
        final boolean justFound = state == KiroOSUpdateManager.State.AVAILABLE
                && mLastState == KiroOSUpdateManager.State.CHECKING;
        mLastState = state;
        if (justFound) {
            mNotesCard.post(new Runnable() {
                @Override
                public void run() {
                    playUpdateFound();
                }
            });
        }

        mVersionHeader.setText(KiroOSUpdateManager.installedVersion());
        String badge = KiroOSUpdateManager.installedChannel();
        mBadgeView.setVisibility(TextUtils.isEmpty(badge) ? View.GONE : View.VISIBLE);
        mBadgeView.setText(badge.toUpperCase(Locale.getDefault()));

        boolean hasInfo = info != null && (state == KiroOSUpdateManager.State.AVAILABLE
                || state == KiroOSUpdateManager.State.DOWNLOADING
                || state == KiroOSUpdateManager.State.VERIFYING
                || state == KiroOSUpdateManager.State.READY
                || state == KiroOSUpdateManager.State.INSTALLING);
        mNotesCard.setVisibility(hasInfo && !TextUtils.isEmpty(info.changelog) ? View.VISIBLE : View.GONE);
        if (hasInfo) {
            mNotesTitle.setText(getString(R.string.kiroos_ota_release_notes, info.displayName()));
            mNotesContent.setText(info.changelog);
        }

        mProgressContainer.setVisibility(View.GONE);
        mActionBtn.setEnabled(true);
        setSpinning(false);
        final boolean transferring = state == KiroOSUpdateManager.State.DOWNLOADING
                || state == KiroOSUpdateManager.State.VERIFYING;
        if (!transferring) {
            mRing.setProgress(-1f);
            mRingPercent.setVisibility(View.GONE);
            mLogoView.setVisibility(View.VISIBLE);
        }

        switch (state) {
            case IDLE:
            case CHECKING:
                mStatusTitle.setText(R.string.kiroos_ota_checking);
                mStatusDesc.setText(R.string.kiroos_ota_checking_desc);
                mActionBtn.setText(R.string.kiroos_ota_checking);
                mActionBtn.setEnabled(false);
                setSpinning(true);
                break;
            case UP_TO_DATE:
                mStatusTitle.setText(R.string.kiroos_ota_up_to_date);
                mStatusDesc.setText(getString(R.string.kiroos_ota_last_check,
                        DateUtils.formatDateTime(this, KiroOSUpdateManager.lastCheck(this),
                                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME)));
                mActionBtn.setText(R.string.kiroos_ota_check);
                break;
            case AVAILABLE:
                mStatusTitle.setText(getString(R.string.kiroos_ota_available, info.displayName()));
                if (info.incremental) {
                    mStatusDesc.setText(getString(R.string.kiroos_ota_size_incremental,
                            Formatter.formatFileSize(this, info.size),
                            Formatter.formatFileSize(this, info.fullSize)));
                } else {
                    mStatusDesc.setText(getString(R.string.kiroos_ota_size,
                            Formatter.formatFileSize(this, info.size)));
                }
                if (mManager.isServerUnreachable()) {
                    mStatusDesc.append("\n" + getString(R.string.kiroos_ota_server_offline));
                }
                mActionBtn.setText(getString(R.string.kiroos_ota_download,
                        Formatter.formatFileSize(this, info.size)));
                break;
            case DOWNLOADING:
                mStatusTitle.setText(R.string.kiroos_ota_downloading);
                mStatusDesc.setText(info != null ? info.displayName() : "");
                mProgressContainer.setVisibility(View.VISIBLE);
                mActionBtn.setText(R.string.kiroos_ota_cancel);
                setSpinning(true);
                break;
            case VERIFYING:
                mStatusTitle.setText(R.string.kiroos_ota_verifying);
                mStatusDesc.setText(R.string.kiroos_ota_verifying_desc);
                mProgressContainer.setVisibility(View.VISIBLE);
                mActionBtn.setText(R.string.kiroos_ota_cancel);
                setSpinning(true);
                break;
            case READY:
                mStatusTitle.setText(R.string.kiroos_ota_ready);
                mStatusDesc.setText(R.string.kiroos_ota_ready_desc);
                mActionBtn.setText(R.string.kiroos_ota_install);
                break;
            case INSTALLING:
                mStatusTitle.setText(R.string.kiroos_ota_installing);
                mStatusDesc.setText(R.string.kiroos_ota_ready_desc);
                mActionBtn.setText(R.string.kiroos_ota_installing);
                mActionBtn.setEnabled(false);
                setSpinning(true);
                break;
            case ERROR:
                mStatusTitle.setText(R.string.kiroos_ota_error);
                mStatusDesc.setText(errorText(mManager.getError()));
                mActionBtn.setText(R.string.kiroos_ota_retry);
                break;
        }
    }

    @Override
    public void onProgress(long done, long total, long speed, long eta) {
        if (total <= 0) return;
        int permille = (int) (done * 1000 / total);
        mProgressBar.setProgress(permille);
        // The ring around the logo fills up and shows the percentage in its center.
        mRing.setProgress(permille / 1000f);
        mLogoView.setVisibility(View.GONE);
        mRingPercent.setVisibility(View.VISIBLE);
        mRingPercent.setText(String.format(Locale.getDefault(), "%d%%", permille / 10));
        String pct = String.format(Locale.getDefault(), "%.1f%%", permille / 10f);
        if (mManager.getState() == KiroOSUpdateManager.State.DOWNLOADING) {
            String etaStr = eta >= 0 ? DateUtils.formatElapsedTime(eta) : "--:--";
            mProgressText.setText(pct + "  •  " + getString(R.string.kiroos_ota_download_progress,
                    Formatter.formatFileSize(this, done), Formatter.formatFileSize(this, total),
                    Formatter.formatFileSize(this, speed), etaStr));
        } else {
            mProgressText.setText(pct);
        }
    }

    private String errorText(int error) {
        switch (error) {
            case KiroOSUpdateManager.ERR_SERVER:
                return getString(R.string.kiroos_ota_err_server, KiroOSUpdateManager.serverUrl(this));
            case KiroOSUpdateManager.ERR_MD5:
                return getString(R.string.kiroos_ota_err_md5);
            case KiroOSUpdateManager.ERR_SIGNATURE:
                return getString(R.string.kiroos_ota_err_signature);
            case KiroOSUpdateManager.ERR_SPACE:
                return getString(R.string.kiroos_ota_err_space);
            case KiroOSUpdateManager.ERR_INSTALL:
                return getString(R.string.kiroos_ota_err_install);
            default:
                return getString(R.string.kiroos_ota_err_download);
        }
    }

    /** Checking / installing: a short arc turns around the logo (the logo itself stays still). */
    private void setSpinning(boolean spin) {
        mRing.setSpinning(spin);
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
