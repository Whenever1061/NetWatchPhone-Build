package com.netwatch.phone.telecom;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

public final class ContactLookup {
    private ContactLookup() {}

    public static boolean isKnown(Context context, String number) {
        return !findName(context,number).isEmpty();
    }

    public static String findName(Context context,String number){
        if (number == null || number.isEmpty()) return "";
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        try (Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name=cursor.getString(0);
                return name==null?"":name;
            }
        } catch (Throwable ignored) { }
        return "";
    }
}
