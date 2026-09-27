package com.netwatch.phone.telecom;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

public final class ContactLookup {
    private ContactLookup() {}

    public static boolean isKnown(Context context, String number) {
        if (number == null || number.isEmpty()) return false;
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        try (Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{ContactsContract.PhoneLookup._ID},
                null, null, null)) {
            return cursor != null && cursor.moveToFirst();
        } catch (SecurityException ex) {
            return false;
        }
    }
}
