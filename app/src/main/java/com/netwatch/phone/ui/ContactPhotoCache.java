package com.netwatch.phone.ui;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small asynchronous cache so contact photos never block scrolling. */
final class ContactPhotoCache {
    private final Context context;
    private final ConcurrentHashMap<String,Bitmap> cache=new ConcurrentHashMap<>();
    private final Set<String> missing=Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> loading=Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ExecutorService executor=Executors.newSingleThreadExecutor();

    ContactPhotoCache(Context context){this.context=context.getApplicationContext();}

    Bitmap get(String number,Runnable changed){
        String key=normalize(number);
        if(key.isEmpty()||missing.contains(key))return null;
        Bitmap b=cache.get(key);if(b!=null)return b;
        if(loading.add(key))executor.execute(()->load(key,number,changed));
        return null;
    }

    private void load(String key,String raw,Runnable changed){
        Bitmap photo=null;
        try{
            Uri lookup=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(raw));
            try(Cursor c=context.getContentResolver().query(lookup,new String[]{ContactsContract.PhoneLookup.PHOTO_URI},null,null,null)){
                if(c!=null&&c.moveToFirst()){
                    String value=c.getString(0);
                    if(value!=null&&!value.isEmpty())try(InputStream in=context.getContentResolver().openInputStream(Uri.parse(value))){if(in!=null)photo=BitmapFactory.decodeStream(in);}
                }
            }
        }catch(Throwable ignored){}
        if(photo!=null)cache.put(key,photo);else missing.add(key);
        loading.remove(key);
        if(changed!=null)changed.run();
    }

    void shutdown(){executor.shutdownNow();cache.clear();loading.clear();missing.clear();}
    private static String normalize(String s){return s==null?"":s.replaceAll("[^0-9+]","");}
}
