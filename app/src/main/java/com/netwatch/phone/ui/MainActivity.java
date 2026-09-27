package com.netwatch.phone.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telecom.TelecomManager;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.netwatch.phone.BuildConfig;
import com.netwatch.phone.config.AppConfig;
import com.netwatch.phone.update.GitHubUpdater;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity implements GlassPhoneView.Callback {
    private static final int ROLE_DIALER_REQ = 1001;
    private static final int ROLE_SCREEN_REQ = 1002;
    private static final int PERM_CORE_REQ = 1003;
    private static final int PERM_DIALER_REQ = 1004;
    private GlassPhoneView phoneView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindowSafely();

        try {
            phoneView = new GlassPhoneView(this, this);
            setContentView(phoneView);
        } catch (Throwable fatalUi) {
            showEmergencyUi(fatalUi);
            return;
        }

        handleDialIntent(getIntent());

        phoneView.postDelayed(() -> {
            requestCorePermissionsSafely();
            refreshDeviceDataSafely();
        }, 450);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDialIntent(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        if (phoneView != null) phoneView.postDelayed(this::refreshDeviceDataSafely, 150);
    }

    private void configureWindowSafely() {
        try {
            Window w = getWindow();
            w.setStatusBarColor(Color.rgb(8, 20, 34));
            w.setNavigationBarColor(Color.rgb(5, 14, 26));
        } catch (Throwable ignored) { }
    }

    private void showEmergencyUi(Throwable problem) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(24);
        root.setPadding(p, p, p, p);
        root.setBackgroundColor(Color.rgb(8, 20, 34));

        TextView title = new TextView(this);
        title.setText("NetWatch Phone");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        root.addView(title);

        TextView msg = new TextView(this);
        msg.setText("Safe mode loaded. The glass renderer hit an error, but the app stayed open.\n\n"
                + problem.getClass().getSimpleName() + ": " + String.valueOf(problem.getMessage()));
        msg.setTextColor(0xFFD6E8FA);
        msg.setTextSize(15);
        msg.setPadding(0, dp(18), 0, dp(18));
        root.addView(msg);

        Button settings = new Button(this);
        settings.setText("Open NetWatch settings");
        settings.setOnClickListener(v -> openSettings());
        root.addView(settings);
        setContentView(root);
    }

    private void handleDialIntent(Intent intent) {
        if (intent == null || phoneView == null) return;
        Uri data = intent.getData();
        if (Intent.ACTION_DIAL.equals(intent.getAction()) && data != null) {
            phoneView.showKeypad(data.getSchemeSpecificPart());
        }
    }

    @Override public void placeCall(String rawNumber) {
        String n = rawNumber == null ? "" : rawNumber.trim();
        if (n.isEmpty()) return;
        try {
            if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                requestCorePermissionsSafely();
                Toast.makeText(this, "Allow Phone permission, then tap Call again.", Toast.LENGTH_SHORT).show();
                return;
            }
            TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
            if (tm != null) tm.placeCall(Uri.fromParts("tel", n, null), new Bundle());
        } catch (Throwable error) {
            Toast.makeText(this, "Could not place call: " + shortMessage(error), Toast.LENGTH_LONG).show();
        }
    }

    @Override public void openSettings() {
        final int pad = dp(18);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        TextView version = new TextView(this);
        version.setText("NetWatch Phone " + BuildConfig.VERSION_NAME + "\nSigned GitHub update channel");
        version.setPadding(0, 0, 0, pad / 2);
        box.addView(version, new LinearLayout.LayoutParams(-1, -2));

        EditText api = new EditText(this);
        api.setSingleLine(true);
        api.setHint("Contact-center API");
        api.setText(AppConfig.getContactCenterUrl(this));
        box.addView(api, new LinearLayout.LayoutParams(-1, -2));

        Button update = new Button(this);
        update.setText("Check GitHub for updates");
        update.setOnClickListener(v -> GitHubUpdater.check(this, true));
        box.addView(update, new LinearLayout.LayoutParams(-1, -2));

        Button dialer = new Button(this);
        dialer.setText("Make NetWatch the default phone app");
        dialer.setOnClickListener(v -> requestRoleSafely(RoleManager.ROLE_DIALER, ROLE_DIALER_REQ));
        box.addView(dialer, new LinearLayout.LayoutParams(-1, -2));

        Button screening = new Button(this);
        screening.setText("Enable NetWatch call screening");
        screening.setOnClickListener(v -> requestRoleSafely(RoleManager.ROLE_CALL_SCREENING, ROLE_SCREEN_REQ));
        box.addView(screening, new LinearLayout.LayoutParams(-1, -2));

        new AlertDialog.Builder(this)
                .setTitle("NetWatch Phone")
                .setMessage("Phone → your API → your contact center.")
                .setView(box)
                .setNegativeButton("Close", null)
                .setPositiveButton("Save API", (d, which) -> {
                    boolean ok = AppConfig.setContactCenterUrl(this, api.getText().toString());
                    Toast.makeText(this, ok ? "API home saved" : "Invalid URL", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void requestRoleSafely(String role, int requestCode) {
        try {
            RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (rm == null || !rm.isRoleAvailable(role)) {
                Toast.makeText(this, "That Android role is unavailable.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (rm.isRoleHeld(role)) {
                if (RoleManager.ROLE_DIALER.equals(role)) requestDialerPermissionsSafely();
                Toast.makeText(this, "Already enabled", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivityForResult(rm.createRequestRoleIntent(role), requestCode);
        } catch (Throwable error) {
            Toast.makeText(this, "Role request failed: " + shortMessage(error), Toast.LENGTH_LONG).show();
        }
    }

    private boolean holdsDialerRole() {
        try {
            RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            return rm != null && rm.isRoleAvailable(RoleManager.ROLE_DIALER) && rm.isRoleHeld(RoleManager.ROLE_DIALER);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void requestCorePermissionsSafely() {
        try {
            String[] p = { Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS };
            ArrayList<String> missing = new ArrayList<>();
            for (String perm : p) if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) missing.add(perm);
            if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), PERM_CORE_REQ);
            if (holdsDialerRole()) requestDialerPermissionsSafely();
        } catch (Throwable error) {
            Toast.makeText(this, "Permission setup skipped: " + shortMessage(error), Toast.LENGTH_LONG).show();
        }
    }

    private void requestDialerPermissionsSafely() {
        try {
            String[] p = {
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.WRITE_CALL_LOG,
                    Manifest.permission.ANSWER_PHONE_CALLS
            };
            ArrayList<String> missing = new ArrayList<>();
            for (String perm : p) if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) missing.add(perm);
            if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), PERM_DIALER_REQ);
        } catch (Throwable error) {
            Toast.makeText(this, "Dialer permissions skipped: " + shortMessage(error), Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ROLE_DIALER_REQ && holdsDialerRole()) {
            requestDialerPermissionsSafely();
            refreshDeviceDataSafely();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERM_CORE_REQ || requestCode == PERM_DIALER_REQ) refreshDeviceDataSafely();
    }

    private void refreshDeviceDataSafely() {
        if (phoneView == null) return;
        try { phoneView.setRecentCalls(readRecentCalls()); } catch (Throwable ignored) { phoneView.setRecentCalls(new ArrayList<>()); }
        try { phoneView.setContacts(readContacts()); } catch (Throwable ignored) { phoneView.setContacts(new ArrayList<>()); }
    }

    private List<GlassPhoneView.RecentCall> readRecentCalls() {
        List<GlassPhoneView.RecentCall> out = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return out;
        String[] projection = {CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE};
        try (Cursor c = getContentResolver().query(CallLog.Calls.CONTENT_URI, projection, null, null, CallLog.Calls.DATE + " DESC")) {
            if (c == null) return out;
            int numberCol = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER);
            int nameCol = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME);
            int typeCol = c.getColumnIndexOrThrow(CallLog.Calls.TYPE);
            int dateCol = c.getColumnIndexOrThrow(CallLog.Calls.DATE);
            while (c.moveToNext() && out.size() < 14) {
                String number = c.getString(numberCol);
                String name = c.getString(nameCol);
                int type = c.getInt(typeCol);
                long date = c.getLong(dateCol);
                boolean missed = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE;
                String direction = type == CallLog.Calls.OUTGOING_TYPE ? "↗ Mobile" : (missed ? "Missed" : "↙ Mobile");
                out.add(new GlassPhoneView.RecentCall(name, number, direction + " • " + friendlyTime(date), missed));
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private List<GlassPhoneView.ContactItem> readContacts() {
        List<GlassPhoneView.ContactItem> out = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return out;
        String[] projection = {ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER};
        try (Cursor c = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC")) {
            if (c == null) return out;
            int nameCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            int numberCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER);
            String last = null;
            while (c.moveToNext() && out.size() < 40) {
                String name = c.getString(nameCol), number = c.getString(numberCol);
                if (name == null || number == null) continue;
                String key = name + "|" + number;
                if (key.equals(last)) continue;
                last = key;
                out.add(new GlassPhoneView.ContactItem(name, number));
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private String friendlyTime(long millis) {
        long age = System.currentTimeMillis() - millis;
        if (age >= 0 && age < 24L * 60L * 60L * 1000L)
            return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(millis));
        if (age >= 0 && age < 48L * 60L * 60L * 1000L) return "Yesterday";
        return new SimpleDateFormat("EEE", Locale.getDefault()).format(new Date(millis));
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static String shortMessage(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.trim().isEmpty()) ? t.getClass().getSimpleName() : m;
    }
}
