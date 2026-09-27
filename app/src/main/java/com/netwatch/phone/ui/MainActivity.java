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
import android.view.WindowInsetsController;
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
    private static final int PERM_REQ = 1003;
    private GlassPhoneView phoneView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        phoneView = new GlassPhoneView(this, this);
        setContentView(phoneView);
        handleDialIntent(getIntent());
        requestRuntimePermissions();
        refreshDeviceData();
        GitHubUpdater.check(this, false);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDialIntent(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshDeviceData();
    }

    private void configureWindow() {
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = w.getInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else {
            w.getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private void handleDialIntent(Intent intent) {
        if (intent == null || phoneView == null) return;
        Uri data = intent.getData();
        if (Intent.ACTION_DIAL.equals(intent.getAction()) && data != null) phoneView.showKeypad(data.getSchemeSpecificPart());
    }

    @Override public void placeCall(String rawNumber) {
        String n = rawNumber == null ? "" : rawNumber.trim();
        if (n.isEmpty()) return;
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestRuntimePermissions();
            return;
        }
        TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
        if (tm != null) tm.placeCall(Uri.fromParts("tel", n, null), new Bundle());
    }

    @Override public void openSettings() {
        final int pad = Math.round(18 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        TextView version = new TextView(this);
        version.setText("NetWatch Phone " + BuildConfig.VERSION_NAME + "  •  GitHub update channel");
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
        dialer.setText("Make default phone app");
        dialer.setOnClickListener(v -> requestRole(RoleManager.ROLE_DIALER, ROLE_DIALER_REQ));
        box.addView(dialer, new LinearLayout.LayoutParams(-1, -2));

        Button screening = new Button(this);
        screening.setText("Enable call screening");
        screening.setOnClickListener(v -> requestRole(RoleManager.ROLE_CALL_SCREENING, ROLE_SCREEN_REQ));
        box.addView(screening, new LinearLayout.LayoutParams(-1, -2));

        new AlertDialog.Builder(this)
                .setTitle("NetWatch Phone")
                .setMessage("Phone → your API → your contact center. Updates come only from your signed GitHub release channel.")
                .setView(box)
                .setNegativeButton("Close", null)
                .setPositiveButton("Save API", (d, which) -> {
                    boolean ok = AppConfig.setContactCenterUrl(this, api.getText().toString());
                    Toast.makeText(this, ok ? "API home saved" : "Invalid URL", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void requestRole(String role, int requestCode) {
        RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
        if (rm == null || !rm.isRoleAvailable(role)) return;
        if (rm.isRoleHeld(role)) { Toast.makeText(this, "Already enabled", Toast.LENGTH_SHORT).show(); return; }
        startActivityForResult(rm.createRequestRoleIntent(role), requestCode);
    }

    private void requestRuntimePermissions() {
        String[] p = {Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG, Manifest.permission.CALL_PHONE,
                Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.READ_CONTACTS};
        ArrayList<String> missing = new ArrayList<>();
        for (String perm : p) if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) missing.add(perm);
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), PERM_REQ);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERM_REQ) refreshDeviceData();
    }

    private void refreshDeviceData() {
        if (phoneView == null) return;
        phoneView.setRecentCalls(readRecentCalls());
        phoneView.setContacts(readContacts());
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
            while (c.moveToNext() && out.size() < 12) {
                String number = c.getString(numberCol), name = c.getString(nameCol);
                int type = c.getInt(typeCol);
                long date = c.getLong(dateCol);
                boolean missed = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE;
                String direction = type == CallLog.Calls.OUTGOING_TYPE ? "↗ Mobile" : (missed ? "Missed" : "↙ Mobile");
                out.add(new GlassPhoneView.RecentCall(name, number, direction + " • " + friendlyTime(date), missed));
            }
        } catch (Exception ignored) { }
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
            while (c.moveToNext() && out.size() < 30) {
                String name = c.getString(nameCol), number = c.getString(numberCol);
                if (name == null || number == null) continue;
                String key = name + "|" + number;
                if (key.equals(last)) continue;
                last = key;
                out.add(new GlassPhoneView.ContactItem(name, number));
            }
        } catch (Exception ignored) { }
        return out;
    }

    private String friendlyTime(long millis) {
        long age = System.currentTimeMillis() - millis;
        if (age >= 0 && age < 24L * 60L * 60L * 1000L) return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(millis));
        if (age < 48L * 60L * 60L * 1000L) return "Yesterday";
        return new SimpleDateFormat("EEE", Locale.getDefault()).format(new Date(millis));
    }
}
