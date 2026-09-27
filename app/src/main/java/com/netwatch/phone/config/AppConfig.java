package com.netwatch.phone.config;

import android.content.Context;
import com.netwatch.phone.BuildConfig;
import java.net.URI;

public final class AppConfig {
    private static final String PREFS = "netwatch_phone";
    private static final String API_URL = "contact_center_url";

    private AppConfig() {}

    public static String getContactCenterUrl(Context context) {
        String value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(API_URL, BuildConfig.DEFAULT_CONTACT_CENTER_URL);
        return normalize(value);
    }

    public static boolean setContactCenterUrl(Context context, String value) {
        try {
            String normalized = normalize(value);
            URI uri = URI.create(normalized);
            if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
                return false;
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(API_URL, normalized).apply();
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String normalize(String value) {
        String v = value == null ? "" : value.trim();
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v.isEmpty() ? BuildConfig.DEFAULT_CONTACT_CENTER_URL : v;
    }
}
