package com.netwatch.phone.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public final class ContactSearchActivity extends Activity {
    public static final String RESULT_NUMBER = "netwatch_contact_number";

    private final ArrayList<Row> all = new ArrayList<>();
    private final ArrayList<Row> filtered = new ArrayList<>();
    private ContactAdapter adapter;
    private TextView count;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(Color.rgb(11, 30, 49));
        w.setNavigationBarColor(Color.rgb(6, 18, 31));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(10));
        root.setBackgroundColor(Color.rgb(13, 38, 60));

        TextView title = new TextView(this);
        title.setText("Search contacts");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("Every contact on this phone — search by name or number.");
        subtitle.setTextColor(0xFFBFD3E6);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(3), 0, dp(14));
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        EditText search = new EditText(this);
        search.setHint("Type a name or phone number");
        search.setHintTextColor(0xFF9FB4C8);
        search.setTextColor(Color.WHITE);
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setPadding(dp(16), dp(9), dp(16), dp(9));
        search.setBackgroundColor(0x334D7799);
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(54)));

        count = new TextView(this);
        count.setTextColor(0xFFBFD3E6);
        count.setTextSize(12);
        count.setPadding(dp(4), dp(10), 0, dp(8));
        root.addView(count, new LinearLayout.LayoutParams(-1, -2));

        ListView list = new ListView(this);
        list.setDividerHeight(1);
        list.setDivider(new android.graphics.drawable.ColorDrawable(0x224C789A));
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setBackgroundColor(Color.TRANSPARENT);
        adapter = new ContactAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Contacts permission is required", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        loadAllContacts();
        applyFilter("");

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= filtered.size()) return;
            Row row = filtered.get(position);
            Intent result = new Intent();
            result.putExtra(RESULT_NUMBER, row.number);
            setResult(RESULT_OK, result);
            finish();
        });

        search.requestFocus();
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void loadAllContacts() {
        all.clear();
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
        };
        try (Cursor c = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC")) {
            if (c == null) return;
            int nameCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            int numberCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER);
            int normalizedCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER);
            String lastKey = null;
            while (c.moveToNext()) {
                String name = c.getString(nameCol);
                String number = c.getString(numberCol);
                String normalized = normalizedCol >= 0 ? c.getString(normalizedCol) : null;
                if (name == null || number == null) continue;
                String key = name + "|" + number;
                if (key.equals(lastKey)) continue;
                lastKey = key;
                all.add(new Row(name, number, normalized));
            }
        } catch (Throwable ignored) { }
    }

    private void applyFilter(String raw) {
        String q = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        String digits = q.replaceAll("[^0-9+]", "");
        filtered.clear();
        if (q.isEmpty()) {
            filtered.addAll(all);
        } else {
            for (Row row : all) {
                String name = row.name.toLowerCase(Locale.ROOT);
                String number = row.number.toLowerCase(Locale.ROOT);
                String compact = row.number.replaceAll("[^0-9+]", "");
                String norm = row.normalized == null ? "" : row.normalized.toLowerCase(Locale.ROOT);
                if (name.contains(q) || number.contains(q) || (!digits.isEmpty() && compact.contains(digits)) || norm.contains(q)) {
                    filtered.add(row);
                }
            }
        }
        count.setText(filtered.size() + (filtered.size() == 1 ? " contact" : " contacts"));
        adapter.notifyDataSetChanged();
    }

    private final class ContactAdapter extends BaseAdapter {
        @Override public int getCount() { return filtered.size(); }
        @Override public Object getItem(int position) { return filtered.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView name;
            TextView number;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
                name = (TextView) row.getChildAt(0);
                number = (TextView) row.getChildAt(1);
            } else {
                row = new LinearLayout(ContactSearchActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(14), dp(10), dp(14), dp(10));
                name = new TextView(ContactSearchActivity.this);
                name.setTextColor(Color.WHITE);
                name.setTextSize(16);
                name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                number = new TextView(ContactSearchActivity.this);
                number.setTextColor(0xFFBFD3E6);
                number.setTextSize(13);
                number.setPadding(0, dp(3), 0, 0);
                row.addView(name, new LinearLayout.LayoutParams(-1, -2));
                row.addView(number, new LinearLayout.LayoutParams(-1, -2));
            }
            Row item = filtered.get(position);
            name.setText(item.name);
            number.setText(item.number);
            return row;
        }
    }

    private static final class Row {
        final String name;
        final String number;
        final String normalized;
        Row(String name, String number, String normalized) {
            this.name = name;
            this.number = number;
            this.normalized = normalized;
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
