package com.netwatch.phone.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;
import com.netwatch.phone.BuildConfig;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** GitHub release updater for the personal NetWatch Phone build. */
public final class GitHubUpdater {
    private static final String UPDATE_JSON =
            "https://github.com/Whenever1061/NetWatchPhone-Build/releases/download/netwatch-latest/update.json";

    private GitHubUpdater() { }

    public static void check(Activity activity, boolean userInitiated) {
        new Thread(() -> {
            try {
                JSONObject manifest = new JSONObject(readText(UPDATE_JSON, 6000));
                int remoteCode = manifest.optInt("version_code", 0);
                String remoteName = manifest.optString("version_name", "unknown");
                String apkUrl = manifest.optString("apk_url", "");
                String sha256 = manifest.optString("sha256", "").toLowerCase(Locale.ROOT);
                if (remoteCode <= BuildConfig.VERSION_CODE) {
                    if (userInitiated) toast(activity, "NetWatch Phone " + BuildConfig.VERSION_NAME + " is current");
                    return;
                }
                if (apkUrl.isEmpty() || sha256.length() != 64) throw new IllegalStateException("Bad update manifest");
                activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                        .setTitle("NetWatch Phone " + remoteName)
                        .setMessage("A newer signed build is available from your GitHub update channel.\n\nInstalled: "
                                + BuildConfig.VERSION_NAME + "\nAvailable: " + remoteName)
                        .setNegativeButton("Later", null)
                        .setPositiveButton("Download update", (d, which) -> downloadAndInstall(activity, apkUrl, sha256, remoteName))
                        .show());
            } catch (Exception ex) {
                if (userInitiated) toast(activity, "Update check failed: " + shortMessage(ex));
            }
        }, "NetWatchUpdateCheck").start();
    }

    private static void downloadAndInstall(Activity activity, String apkUrl, String expectedSha, String versionName) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            toast(activity, "Allow NetWatch Phone to install updates, then tap Check for updates again");
            Intent permission = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(permission);
            return;
        }

        toast(activity, "Downloading NetWatch Phone " + versionName + "…");
        new Thread(() -> {
            File apk = null;
            try {
                File root = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (root == null) throw new IllegalStateException("No update storage available");
                File dir = new File(root, "netwatch-updates");
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Could not create update folder");
                apk = new File(dir, "NetWatchPhone-" + versionName + ".apk");
                download(apkUrl, apk, 30000);
                String actual = sha256(apk);
                if (!actual.equalsIgnoreCase(expectedSha)) {
                    apk.delete();
                    throw new SecurityException("Downloaded APK checksum does not match GitHub manifest");
                }
                installWithPackageInstaller(activity, apk);
            } catch (Exception ex) {
                if (apk != null && apk.exists()) apk.delete();
                toast(activity, "Update failed: " + shortMessage(ex));
            }
        }, "NetWatchUpdateDownload").start();
    }

    private static void installWithPackageInstaller(Activity activity, File apk) throws Exception {
        PackageInstaller installer = activity.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(activity.getPackageName());
        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(sessionId);
        try (InputStream in = new BufferedInputStream(new FileInputStream(apk));
             OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
            byte[] buf = new byte[64 * 1024];
            for (int n; (n = in.read(buf)) >= 0;) out.write(buf, 0, n);
            session.fsync(out);
        }
        Intent status = new Intent(activity, UpdateInstallReceiver.class);
        status.setAction("com.netwatch.phone.UPDATE_INSTALL_STATUS");
        PendingIntent pending = PendingIntent.getBroadcast(activity, sessionId, status,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        session.commit(pending.getIntentSender());
        session.close();
    }

    private static void download(String source, File dest, int timeout) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(source).openConnection();
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "NetWatchPhone/" + BuildConfig.VERSION_NAME);
        int status = conn.getResponseCode();
        if (status < 200 || status >= 300) throw new IllegalStateException("GitHub HTTP " + status);
        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[64 * 1024];
            for (int n; (n = in.read(buf)) >= 0;) out.write(buf, 0, n);
        } finally {
            conn.disconnect();
        }
    }

    private static String readText(String source, int timeout) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(source).openConnection();
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "NetWatchPhone/" + BuildConfig.VERSION_NAME);
        int status = conn.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder text = new StringBuilder();
        if (stream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null;) text.append(line);
            }
        }
        conn.disconnect();
        if (status < 200 || status >= 300) throw new IllegalStateException("GitHub HTTP " + status);
        return text.toString();
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf = new byte[64 * 1024];
            for (int n; (n = in.read(buf)) >= 0;) digest.update(buf, 0, n);
        }
        StringBuilder out = new StringBuilder();
        for (byte b : digest.digest()) out.append(String.format(Locale.ROOT, "%02x", b));
        return out.toString();
    }

    private static void toast(Activity activity, String message) {
        activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }

    private static String shortMessage(Throwable error) {
        String msg = error.getMessage();
        return msg == null || msg.trim().isEmpty() ? error.getClass().getSimpleName() : msg;
    }
}
