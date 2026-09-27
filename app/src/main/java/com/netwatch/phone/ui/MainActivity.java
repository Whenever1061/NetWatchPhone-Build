package com.netwatch.phone.ui;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.netwatch.phone.config.AppConfig;

public final class MainActivity extends Activity {
    private static final int ROLE_DIALER_REQ = 1001;
    private static final int ROLE_SCREEN_REQ = 1002;
    private static final int PERM_REQ = 1003;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        requestRuntimePermissions();
    }

    private View buildUi() {
        int pad = dp(18);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("NetWatch Phone");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Phone app → your API → your contact center\nOne configured network home.");
        sub.setTextSize(16);
        sub.setPadding(0, dp(8), 0, dp(18));
        root.addView(sub);

        Button dialerRole = button("Make default phone app");
        dialerRole.setOnClickListener(v -> requestRole(RoleManager.ROLE_DIALER, ROLE_DIALER_REQ));
        root.addView(dialerRole);

        Button screeningRole = button("Enable call screening");
        screeningRole.setOnClickListener(v -> requestRole(RoleManager.ROLE_CALL_SCREENING, ROLE_SCREEN_REQ));
        root.addView(screeningRole);

        TextView apiLabel = new TextView(this);
        apiLabel.setText("Contact-center API");
        apiLabel.setPadding(0, dp(18), 0, dp(4));
        root.addView(apiLabel);

        EditText api = new EditText(this);
        api.setSingleLine(true);
        api.setText(AppConfig.getContactCenterUrl(this));
        root.addView(api, new LinearLayout.LayoutParams(-1, -2));

        Button save = button("Save API home");
        save.setOnClickListener(v -> {
            boolean ok = AppConfig.setContactCenterUrl(this, api.getText().toString());
            Toast.makeText(this, ok ? "API home saved" : "Invalid URL", Toast.LENGTH_SHORT).show();
        });
        root.addView(save);

        EditText number = new EditText(this);
        number.setHint("Phone number");
        number.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        number.setPadding(0, dp(22), 0, 0);
        root.addView(number, new LinearLayout.LayoutParams(-1, -2));

        Button call = button("Call");
        call.setOnClickListener(v -> placeCall(number.getText().toString()));
        root.addView(call);

        return root;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(5), 0, dp(5));
        b.setLayoutParams(lp);
        return b;
    }

    private void requestRole(String role, int requestCode) {
        RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
        if (rm != null && rm.isRoleAvailable(role)) {
            if (rm.isRoleHeld(role)) {
                Toast.makeText(this, "Already enabled", Toast.LENGTH_SHORT).show();
            } else {
                startActivityForResult(rm.createRequestRoleIntent(role), requestCode);
            }
        }
    }

    private void requestRuntimePermissions() {
        String[] p = {Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG, Manifest.permission.CALL_PHONE,
                Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.READ_CONTACTS};
        java.util.ArrayList<String> missing = new java.util.ArrayList<>();
        for (String perm : p) if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) missing.add(perm);
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), PERM_REQ);
    }

    private void placeCall(String number) {
        String n = number == null ? "" : number.trim();
        if (n.isEmpty()) return;
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestRuntimePermissions();
            return;
        }
        TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
        if (tm != null) tm.placeCall(Uri.fromParts("tel", n, null), new Bundle());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
