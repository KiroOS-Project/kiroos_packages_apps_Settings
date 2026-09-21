package com.android.settings.deviceinfo;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RecoverySystem;
import android.os.StatFs;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * KiroOS OTA engine: checks the update server, downloads the package for real,
 * verifies MD5 + OTA signature and hands it to the recovery for installation.
 *
 * Server format (update.json):
 * {
 *   "version": "KiroOS 1.0",
 *   "channel": "Developer Beta 2",
 *   "timestamp": 1789381335,            // ro.build.date.utc of the new build
 *   "filename": "KiroOS-1.0-20260917-zerofltexx.zip",
 *   "url": "KiroOS-1.0-20260917-zerofltexx.zip",   // absolute or relative to update.json
 *   "size": 725581804,
 *   "md5": "...",
 *   "changelog": "...",                 // English, also used when no translation matches
 *   "changelogs": { "en": "...", "it": "...", "pt-BR": "..." },   // optional, per language
 *   "server_url": "https://...",         // optional: moves every phone to a new endpoint
 *   "incrementals": [                    // optional: packages with only the changes
 *     { "source_timestamp": 1789300000,  // ro.build.date.utc the package applies to
 *       "filename": "...", "url": "...", "size": 12345678, "md5": "..." }
 *   ]
 * }
 * The incremental package for the installed build is preferred. If installing it
 * fails (the recovery aborts and the phone boots the old build again), the next
 * check falls back to the full package.
 */
public class KiroOSUpdateManager {

    private static final String TAG = "KiroOSUpdate";

    public static final String PREFS = "kiroos_ota";
    public static final String PREF_SERVER_URL = "server_url";
    public static final String PREF_SHARE_STATS = "share_stats";
    private static final String PREF_INSTALL_ID = "install_id";
    public static final String PREF_LAST_CHECK = "last_check";
    public static final String PREF_INFO_JSON = "info_json";
    /** "sourceTimestamp:targetTimestamp" of the last incremental package sent to recovery. */
    public static final String PREF_DELTA_ATTEMPT = "delta_attempt";
    /** timestamp of the last update a notification was shown for */
    public static final String PREF_NOTIFIED = "notified_timestamp";

    public static final String PROP_OTA_URL = "ro.kiroos.ota.url";
    public static final String PROP_CHANNEL = "ro.kiro.release.channel";
    public static final String PROP_VERSION = "ro.kiro.display.version";
    public static final String PROP_BUILD_UTC = "ro.build.date.utc";

    /** Same directory used by the LineageOS updater (init.local.rc + ota_package_file). */
    private static final File DOWNLOAD_DIR = new File("/data/lineageos_updates");

    public static final int MIN_BATTERY = 30;
    public static final int MIN_BATTERY_CHARGING = 20;

    public enum State {
        IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, VERIFYING, READY, INSTALLING, ERROR
    }

    public static final int ERR_NONE = 0;
    public static final int ERR_SERVER = 1;
    public static final int ERR_DOWNLOAD = 2;
    public static final int ERR_MD5 = 3;
    public static final int ERR_SIGNATURE = 4;
    public static final int ERR_SPACE = 5;
    public static final int ERR_INSTALL = 6;

    public static class UpdateInfo {
        public String version;
        public String channel;
        public long timestamp;
        public String filename;
        public String url;
        public long size;
        public String md5;
        /** Release notes in the phone language (falls back to English). */
        public String changelog;
        /** All translations as sent by the server, kept so a language change is picked up. */
        JSONObject changelogs;
        public boolean incremental;
        public long fullSize;

        /** Brand name and build date, e.g. "KiroOS 1.0 (17 Sep 2026)". The channel is shown apart. */
        public String displayName() {
            String date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                    .format(new java.util.Date(timestamp * 1000L));
            return version + " (" + date + ")";
        }

        String deltaKey() {
            return installedTimestamp() + ":" + timestamp;
        }

        static UpdateInfo fromJson(JSONObject o, URL base) throws Exception {
            return fromJson(o, base, null);
        }

        /** @param failedDelta PREF_DELTA_ATTEMPT value: skips an incremental that already failed */
        static UpdateInfo fromJson(JSONObject o, URL base, String failedDelta) throws Exception {
            UpdateInfo i = new UpdateInfo();
            i.version = o.optString("version", "KiroOS");
            i.channel = o.optString("channel", "");
            i.timestamp = o.getLong("timestamp");
            i.changelogs = o.optJSONObject("changelogs");
            i.changelog = localizedChangelog(i.changelogs, o.optString("changelog", ""));
            i.incremental = o.optBoolean("incremental", false);
            readPackage(i, o, base);
            i.fullSize = o.optLong("full_size", i.size);

            JSONArray deltas = o.optJSONArray("incrementals");
            if (deltas != null && !i.deltaKey().equals(failedDelta)) {
                long installed = installedTimestamp();
                for (int n = 0; n < deltas.length(); n++) {
                    JSONObject d = deltas.getJSONObject(n);
                    if (d.optLong("source_timestamp", -1) == installed) {
                        i.fullSize = i.size;
                        readPackage(i, d, base);
                        i.incremental = true;
                        break;
                    }
                }
            }
            return i;
        }

        /** Picks language-country, then language, then English, then the plain changelog. */
        static String localizedChangelog(JSONObject all, String fallback) {
            if (all == null) return fallback;
            Locale l = Locale.getDefault();
            String[] keys = { l.getLanguage() + "-" + l.getCountry(), l.getLanguage(), "en" };
            for (String k : keys) {
                String text = all.optString(k, "");
                if (!text.isEmpty()) return text;
            }
            return fallback;
        }

        private static void readPackage(UpdateInfo i, JSONObject o, URL base) throws Exception {
            i.filename = new File(o.getString("filename")).getName();
            String u = o.optString("url", i.filename);
            i.url = base != null ? new URL(base, u).toString() : u;
            i.size = o.getLong("size");
            i.md5 = o.optString("md5", "").toLowerCase(Locale.US);
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("version", version);
            o.put("channel", channel);
            o.put("timestamp", timestamp);
            o.put("filename", filename);
            o.put("url", url);
            o.put("size", size);
            o.put("md5", md5);
            o.put("changelog", changelog);
            if (changelogs != null) o.put("changelogs", changelogs);
            o.put("incremental", incremental);
            o.put("full_size", fullSize);
            return o;
        }
    }

    public interface Listener {
        void onStateChanged(State state);
        /** Called on the main thread; speed in bytes/s, eta in seconds (-1 if unknown). */
        void onProgress(long done, long total, long speed, long eta);
    }

    private static KiroOSUpdateManager sInstance;

    private final Context mContext;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final List<Listener> mListeners = new ArrayList<Listener>();

    private volatile State mState = State.IDLE;
    private volatile int mError = ERR_NONE;
    private volatile boolean mCancel;
    private volatile boolean mServerUnreachable;
    private UpdateInfo mInfo;
    private long mDone, mTotal, mSpeed, mEta = -1;

    public static synchronized KiroOSUpdateManager get(Context context) {
        if (sInstance == null) {
            sInstance = new KiroOSUpdateManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private KiroOSUpdateManager(Context context) {
        mContext = context;
        mInfo = loadCachedInfo(context);
        if (mInfo != null && mInfo.incremental && mInfo.deltaKey().equals(
                prefs(context).getString(PREF_DELTA_ATTEMPT, null))) {
            // The incremental package was sent to recovery but we are still on the old
            // build: it did not apply. Wait for a new check, which offers the full package.
            mInfo = null;
        }
        if (mInfo != null && isNewer(mInfo)) {
            mState = State.AVAILABLE;
        } else {
            mInfo = null;
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Brand name only ("KiroOS 1.0"): the release channel is shown as a separate badge. */
    public static String installedVersion() {
        return SystemProperties.get(PROP_VERSION, "KiroOS");
    }

    public static String installedChannel() {
        return SystemProperties.get(PROP_CHANNEL, "");
    }

    public static long installedTimestamp() {
        return SystemProperties.getLong(PROP_BUILD_UTC, 0);
    }

    public static boolean isNewer(UpdateInfo info) {
        return info != null && info.timestamp > installedTimestamp();
    }

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String serverUrl(Context c) {
        String url = prefs(c).getString(PREF_SERVER_URL, null);
        if (TextUtils.isEmpty(url)) {
            url = SystemProperties.get(PROP_OTA_URL, "");
        }
        return url;
    }

    public static void setServerUrl(Context c, String url) {
        prefs(c).edit().putString(PREF_SERVER_URL, url == null ? null : url.trim()).apply();
    }

    // ------------------------------------------------------------------ device statistics

    /**
     * With the update check the phone tells the KiroOS server which build it runs, so the
     * developer can see how many real devices use each beta. Only a random install id (no
     * IMEI / serial / account), model, KiroOS and Android version, language, SIM/network
     * country and carrier. Can be turned off on the update screen.
     */
    public static boolean statsEnabled(Context c) {
        return prefs(c).getBoolean(PREF_SHARE_STATS, true);
    }

    public static void setStatsEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean(PREF_SHARE_STATS, on).apply();
    }

    private static String installId(Context c) {
        String id = prefs(c).getString(PREF_INSTALL_ID, null);
        if (TextUtils.isEmpty(id)) {
            id = java.util.UUID.randomUUID().toString();
            prefs(c).edit().putString(PREF_INSTALL_ID, id).apply();
        }
        return id;
    }

    /** HTTP header values must be plain ASCII: drop anything else and cap the length. */
    private static String headerValue(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < 64; i++) {
            char ch = s.charAt(i);
            if (ch >= 0x20 && ch < 0x7f) b.append(ch);
        }
        return b.toString().trim();
    }

    private void addDeviceHeaders(HttpURLConnection conn) {
        if (!statsEnabled(mContext)) return;
        String country = "";
        String carrier = "";
        try {
            android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager)
                    mContext.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                country = tm.getSimCountryIso();
                if (TextUtils.isEmpty(country)) country = tm.getNetworkCountryIso();
                carrier = tm.getNetworkOperatorName();
            }
        } catch (RuntimeException e) {
            // no telephony: leave empty
        }
        // A real S6 runs on the Exynos 7420; emulators and other hardware say otherwise.
        boolean real = !"1".equals(SystemProperties.get("ro.kernel.qemu"))
                && SystemProperties.get("ro.hardware", "").contains("exynos7420");
        conn.setRequestProperty("X-KiroOS-Id", installId(mContext));
        conn.setRequestProperty("X-KiroOS-Version", headerValue(installedVersion()));
        conn.setRequestProperty("X-KiroOS-Channel", headerValue(installedChannel()));
        conn.setRequestProperty("X-KiroOS-Build", String.valueOf(installedTimestamp()));
        conn.setRequestProperty("X-KiroOS-Model", headerValue(android.os.Build.MODEL));
        conn.setRequestProperty("X-KiroOS-Device", headerValue(android.os.Build.DEVICE));
        conn.setRequestProperty("X-KiroOS-Android", headerValue(android.os.Build.VERSION.RELEASE));
        conn.setRequestProperty("X-KiroOS-Patch",
                headerValue(android.os.Build.VERSION.SECURITY_PATCH));
        conn.setRequestProperty("X-KiroOS-Locale",
                headerValue(java.util.Locale.getDefault().toLanguageTag()));
        conn.setRequestProperty("X-KiroOS-Country", headerValue(country).toUpperCase(Locale.ROOT));
        conn.setRequestProperty("X-KiroOS-Carrier", headerValue(carrier));
        conn.setRequestProperty("X-KiroOS-Real", real ? "1" : "0");
    }

    /** Cached result of the last check (used by the About phone card). */
    public static UpdateInfo loadCachedInfo(Context c) {
        String json = prefs(c).getString(PREF_INFO_JSON, null);
        if (json == null) return null;
        try {
            return UpdateInfo.fromJson(new JSONObject(json), null);
        } catch (Exception e) {
            return null;
        }
    }

    public static long lastCheck(Context c) {
        return prefs(c).getLong(PREF_LAST_CHECK, 0);
    }

    private static File packageFile(UpdateInfo info) {
        return new File(DOWNLOAD_DIR, info.filename);
    }

    /** True when the last check could not reach the server (a cached update may still be shown). */
    public boolean isServerUnreachable() { return mServerUnreachable; }

    public State getState() { return mState; }
    public int getError() { return mError; }
    public UpdateInfo getInfo() { return mInfo; }

    public void addListener(Listener l) {
        if (!mListeners.contains(l)) mListeners.add(l);
        l.onStateChanged(mState);
        if (mState == State.DOWNLOADING || mState == State.VERIFYING) {
            l.onProgress(mDone, mTotal, mSpeed, mEta);
        }
    }

    public void removeListener(Listener l) {
        mListeners.remove(l);
    }

    private void setState(final State s, final int error) {
        mState = s;
        mError = error;
        mMain.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : new ArrayList<Listener>(mListeners)) l.onStateChanged(s);
                KiroOSUpdateService.update(mContext, s);
            }
        });
    }

    private void postProgress(long done, long total, long speed, long eta) {
        mDone = done;
        mTotal = total;
        mSpeed = speed;
        mEta = eta;
        final long d = done, t = total, sp = speed, e = eta;
        mMain.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : new ArrayList<Listener>(mListeners)) l.onProgress(d, t, sp, e);
                KiroOSUpdateService.progress(mContext, d, t);
            }
        });
    }

    private boolean isBusy() {
        return mState == State.CHECKING || mState == State.DOWNLOADING
                || mState == State.VERIFYING || mState == State.INSTALLING;
    }

    // ------------------------------------------------------------------ check

    public void check() {
        if (isBusy()) return;
        setState(State.CHECKING, ERR_NONE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                doCheck();
            }
        }, "KiroOSUpdateCheck").start();
    }

    /** Runs a check on the calling thread (used by the background job). */
    void checkBlocking() {
        synchronized (this) {
            if (isBusy()) return;
            setState(State.CHECKING, ERR_NONE);
        }
        doCheck();
    }

    private void doCheck() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(serverUrl(mContext));
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setUseCaches(false);
            addDeviceHeaders(conn);
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + conn.getResponseCode());
            }
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            try {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            } finally {
                r.close();
            }
            JSONObject json = new JSONObject(sb.toString());
            UpdateInfo info = UpdateInfo.fromJson(json, url,
                    prefs(mContext).getString(PREF_DELTA_ATTEMPT, null));
            mServerUnreachable = false;

            SharedPreferences.Editor ed = prefs(mContext).edit();
            ed.putLong(PREF_LAST_CHECK, System.currentTimeMillis());
            // The server can move all phones to a new permanent address.
            String moved = json.optString("server_url", "").trim();
            if (moved.startsWith("http") && !moved.equals(url.toString())) {
                ed.putString(PREF_SERVER_URL, moved);
            }
            if (isNewer(info)) {
                ed.putString(PREF_INFO_JSON, info.toJson().toString()).apply();
                mInfo = info;
                // A complete file left from a previous download is re-verified (not re-downloaded)
                // when the user presses the button again.
                cleanupDownloads(info.filename);
                setState(State.AVAILABLE, ERR_NONE);
            } else {
                ed.remove(PREF_INFO_JSON).apply();
                mInfo = null;
                cleanupDownloads(null);
                setState(State.UP_TO_DATE, ERR_NONE);
            }
        } catch (Exception e) {
            Log.w(TAG, "Update check failed", e);
            mServerUnreachable = true;
            // Never report "up to date" without an answer: keep showing an update found
            // earlier (it can be downloaded when the server is back), otherwise an error.
            UpdateInfo cached = mInfo != null ? mInfo : loadCachedInfo(mContext);
            if (cached != null && isNewer(cached)) {
                mInfo = cached;
                setState(State.AVAILABLE, ERR_NONE);
            } else {
                setState(State.ERROR, ERR_SERVER);
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void cleanupDownloads(String keep) {
        File[] files = DOWNLOAD_DIR.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (keep == null || !f.getName().equals(keep)) {
                if (f.getName().endsWith(".zip")) f.delete();
            }
        }
    }

    // ------------------------------------------------------------------ download

    public void startDownload() {
        final UpdateInfo info = mInfo;
        if (info == null || isBusy()) return;
        mCancel = false;
        setState(State.DOWNLOADING, ERR_NONE);
        KiroOSUpdateService.start(mContext);
        new Thread(new Runnable() {
            @Override
            public void run() {
                doDownload(info);
            }
        }, "KiroOSUpdateDownload").start();
    }

    public void cancel() {
        mCancel = true;
    }

    private void doDownload(UpdateInfo info) {
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        WifiManager wm = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        WifiManager.WifiLock wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, TAG);
        wl.acquire();
        wifiLock.acquire();
        HttpURLConnection conn = null;
        File out = packageFile(info);
        try {
            long existing = out.exists() ? out.length() : 0;
            if (existing > info.size) {
                out.delete();
                existing = 0;
            }
            StatFs stat = new StatFs(DOWNLOAD_DIR.getPath());
            if (stat.getAvailableBytes() < (info.size - existing) + 50L * 1024 * 1024) {
                setState(State.ERROR, ERR_SPACE);
                return;
            }

            if (existing < info.size) {
                conn = (HttpURLConnection) new URL(info.url).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);
                if (existing > 0) {
                    conn.setRequestProperty("Range", "bytes=" + existing + "-");
                }
                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    existing = 0; // server ignored Range: restart from scratch
                } else if (code != HttpURLConnection.HTTP_PARTIAL) {
                    throw new IOException("HTTP " + code);
                }

                InputStream in = conn.getInputStream();
                RandomAccessFile raf = new RandomAccessFile(out, "rw");
                try {
                    raf.setLength(existing);
                    raf.seek(existing);
                    byte[] buf = new byte[256 * 1024];
                    long done = existing;
                    long lastUi = 0, winStart = System.currentTimeMillis(), winBytes = 0, speed = 0;
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        if (mCancel) {
                            setState(State.AVAILABLE, ERR_NONE);
                            return;
                        }
                        raf.write(buf, 0, n);
                        done += n;
                        winBytes += n;
                        long now = System.currentTimeMillis();
                        if (now - winStart >= 1000) {
                            long inst = winBytes * 1000 / (now - winStart);
                            speed = speed == 0 ? inst : (speed * 7 + inst * 3) / 10;
                            winStart = now;
                            winBytes = 0;
                        }
                        if (now - lastUi >= 250) {
                            lastUi = now;
                            long eta = speed > 0 ? (info.size - done) / speed : -1;
                            postProgress(done, info.size, speed, eta);
                        }
                    }
                    postProgress(done, info.size, speed, 0);
                } finally {
                    raf.close();
                    in.close();
                }
            }

            if (out.length() != info.size) {
                throw new IOException("Size mismatch " + out.length() + " != " + info.size);
            }
            out.setReadable(true, false);
            verify(info, out);
        } catch (Exception e) {
            Log.w(TAG, "Download failed", e);
            setState(State.ERROR, ERR_DOWNLOAD);
        } finally {
            if (conn != null) conn.disconnect();
            if (wifiLock.isHeld()) wifiLock.release();
            if (wl.isHeld()) wl.release();
        }
    }

    // ------------------------------------------------------------------ verify

    private void verify(final UpdateInfo info, File file) throws Exception {
        setState(State.VERIFYING, ERR_NONE);
        final long total = file.length();

        // 1) MD5 (0-50%)
        if (!TextUtils.isEmpty(info.md5)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            InputStream in = new FileInputStream(file);
            try {
                byte[] buf = new byte[1024 * 1024];
                long done = 0, lastUi = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (mCancel) {
                        setState(State.AVAILABLE, ERR_NONE);
                        return;
                    }
                    md.update(buf, 0, n);
                    done += n;
                    long now = System.currentTimeMillis();
                    if (now - lastUi >= 250) {
                        lastUi = now;
                        postProgress(done / 2, total, 0, -1);
                    }
                }
            } finally {
                in.close();
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest()) hex.append(String.format(Locale.US, "%02x", b));
            if (!hex.toString().equals(info.md5)) {
                Log.e(TAG, "MD5 mismatch: " + hex + " != " + info.md5);
                file.delete();
                setState(State.ERROR, ERR_MD5);
                return;
            }
        }

        // 2) OTA signature, same check the recovery will do (50-100%)
        try {
            RecoverySystem.verifyPackage(file, new RecoverySystem.ProgressListener() {
                @Override
                public void onProgress(int percent) {
                    postProgress(total / 2 + total * percent / 200, total, 0, -1);
                }
            }, null);
        } catch (java.security.GeneralSecurityException e) {
            Log.e(TAG, "Signature verification failed", e);
            file.delete();
            setState(State.ERROR, ERR_SIGNATURE);
            return;
        }
        setState(State.READY, ERR_NONE);
    }

    // ------------------------------------------------------------------ install

    /** @return 0 if the battery is fine, otherwise the minimum level required. */
    public int batteryRequirement() {
        Intent b = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) return 0;
        int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = scale > 0 ? level * 100 / scale : level;
        boolean plugged = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        int min = plugged ? MIN_BATTERY_CHARGING : MIN_BATTERY;
        return pct >= min ? 0 : min;
    }

    public void install() {
        final UpdateInfo info = mInfo;
        if (info == null || mState != State.READY) return;
        setState(State.INSTALLING, ERR_NONE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // commit(): must be on disk before the reboot
                    if (info.incremental) {
                        prefs(mContext).edit().putString(PREF_DELTA_ATTEMPT, info.deltaKey()).commit();
                    }
                    // Writes /cache/recovery/command + BCB and reboots into the KiroOS recovery.
                    RecoverySystem.installPackage(mContext, packageFile(info));
                } catch (IOException e) {
                    Log.e(TAG, "installPackage failed", e);
                    setState(State.ERROR, ERR_INSTALL);
                }
            }
        }, "KiroOSUpdateInstall").start();
    }
}
