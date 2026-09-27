package com.netwatch.phone.telecom;

import android.content.ContentProviderOperation;
import android.content.Context;
import android.provider.ContactsContract;
import java.util.ArrayList;

public final class ContactWriter {
    private ContactWriter(){}

    public static void save(Context context,String name,String number)throws Exception{
        String n=name==null?"":name.trim();
        String phone=number==null?"":number.trim();
        if(n.isEmpty()||phone.isEmpty())throw new IllegalArgumentException("Name and number are required");

        ArrayList<ContentProviderOperation> ops=new ArrayList<>();
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE,null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME,null).build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,0)
                .withValue(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,n).build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,0)
                .withValue(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER,phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build());
        context.getContentResolver().applyBatch(ContactsContract.AUTHORITY,ops);
    }
}
