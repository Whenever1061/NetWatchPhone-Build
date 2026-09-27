package com.netwatch.phone.ui;

import android.Manifest;
import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.view.MotionEvent;
import android.view.Window;
import android.widget.*;
import com.netwatch.phone.BuildConfig;
import com.netwatch.phone.api.ApiClient;
import com.netwatch.phone.config.AppConfig;
import com.netwatch.phone.weather.WeatherClient;
import com.netwatch.phone.weather.WeatherSnapshot;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements GlassPhoneView.Callback {
    private static final int ROLE_DIALER_REQ=1001,ROLE_SCREEN_REQ=1002,PERM_CORE_REQ=1003,PERM_DIALER_REQ=1004,SEARCH_REQ=1005;
    private GlassPhoneView phoneView;
    private String pendingNumber="";
    private final ExecutorService executor=Executors.newCachedThreadPool();
    private WeatherSnapshot lastWeather=WeatherSnapshot.loading();
    private ToneGenerator dialTone;
    private boolean keypadSoundMode=false;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        configureWindow();
        try{
            dialTone=new ToneGenerator(AudioManager.STREAM_DTMF,72);
            phoneView=new GlassPhoneView(this,this);
            phoneView.setOnTouchListener((v,e)->{maybePlayDialTone(e);return false;});
            setContentView(phoneView);
        }catch(Throwable e){showEmergencyUi(e);return;}
        handleDialIntent(getIntent());
        phoneView.postDelayed(()->{requestCorePermissionsSafely();refreshDeviceDataSafely();refreshWeather(false);},350);
    }

    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleDialIntent(intent);}
    @Override protected void onResume(){super.onResume();if(phoneView!=null){phoneView.postDelayed(this::refreshDeviceDataSafely,120);phoneView.postDelayed(()->refreshWeather(false),220);}}
    @Override protected void onDestroy(){
        executor.shutdownNow();
        try{if(dialTone!=null){dialTone.stopTone();dialTone.release();dialTone=null;}}catch(Throwable ignored){}
        super.onDestroy();
    }

    private void configureWindow(){try{Window w=getWindow();w.setStatusBarColor(Color.rgb(5,15,27));w.setNavigationBarColor(Color.rgb(4,11,20));}catch(Throwable ignored){}}
    private void handleDialIntent(Intent intent){
        if(intent==null||phoneView==null)return;
        Uri data=intent.getData();
        if(Intent.ACTION_DIAL.equals(intent.getAction())&&data!=null){keypadSoundMode=true;phoneView.showKeypad(data.getSchemeSpecificPart());}
    }

    private void maybePlayDialTone(MotionEvent e){
        if(phoneView==null||e==null||e.getAction()!=MotionEvent.ACTION_UP)return;
        float x=e.getX(),y=e.getY();
        float h=phoneView.getHeight(),w=phoneView.getWidth();
        float navTop=Math.max(dp(560),h-dp(92));
        if(y>=navTop){
            float slot=(w-dp(22))/4f;
            int i=(int)((x-dp(11))/Math.max(1f,slot));
            keypadSoundMode=i==3;
            return;
        }
        if(!keypadSoundMode||dialTone==null)return;
        float cx=w/2f,col=dp(111),startX=cx-col,startY=dp(286),row=dp(96);
        int[] tones={ToneGenerator.TONE_DTMF_1,ToneGenerator.TONE_DTMF_2,ToneGenerator.TONE_DTMF_3,
                ToneGenerator.TONE_DTMF_4,ToneGenerator.TONE_DTMF_5,ToneGenerator.TONE_DTMF_6,
                ToneGenerator.TONE_DTMF_7,ToneGenerator.TONE_DTMF_8,ToneGenerator.TONE_DTMF_9,
                ToneGenerator.TONE_DTMF_S,ToneGenerator.TONE_DTMF_0,ToneGenerator.TONE_DTMF_P};
        for(int i=0;i<12;i++){
            float kx=startX+(i%3)*col,ky=startY+(i/3)*row;
            float dx=x-kx,dy=y-ky;
            if(dx*dx+dy*dy<=dp(45)*dp(45)){
                try{dialTone.stopTone();dialTone.startTone(tones[i],135);}catch(Throwable ignored){}
                return;
            }
        }
    }

    @Override public void placeCall(String raw){
        String n=raw==null?"":raw.trim();if(n.isEmpty())return;
        if(!holdsDialerRole()){
            pendingNumber=n;
            requestRoleSafely(RoleManager.ROLE_DIALER,ROLE_DIALER_REQ);
            return;
        }
        try{
            if(checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED){requestCorePermissionsSafely();Toast.makeText(this,"Allow Phone permission, then tap Call again.",Toast.LENGTH_SHORT).show();return;}
            TelecomManager tm=(TelecomManager)getSystemService(TELECOM_SERVICE);
            if(tm==null)throw new IllegalStateException("Telecom unavailable");
            tm.placeCall(Uri.fromParts("tel",n,null),new Bundle());
        }catch(Throwable e){Toast.makeText(this,"Could not place call: "+shortMessage(e),Toast.LENGTH_LONG).show();}
    }

    @Override public void openWeatherDetails(){
        WeatherSnapshot w=lastWeather;
        String body=w.ok
                ? w.condition()+"\n\nTemperature: "+Math.round(w.tempF)+"°F"
                +"\nFeels like: "+Math.round(w.feelsF)+"°F"
                +"\nHigh / Low: "+Math.round(w.highF)+"° / "+Math.round(w.lowF)+"°"
                +"\nHumidity: "+Math.round(w.humidity)+"%"
                +"\nWind: "+Math.round(w.windMph)+" mph"
                +"\nRain chance: "+Math.round(w.rainChance)+"%"
                +"\nSunrise: "+formatSunTime(w.sunrise)
                +"\nSunset: "+formatSunTime(w.sunset)
                +"\n\nLocation: Albuquerque, NM (fixed city coordinates; no precise device location sent)"
                : "Weather is unavailable right now. NetWatch will keep the last cached Albuquerque forecast when possible.";
        new AlertDialog.Builder(this).setTitle("Albuquerque weather").setMessage(body)
                .setNegativeButton("Close",null).setPositiveButton("Refresh",(d,x)->refreshWeather(true)).show();
    }

    @Override public void openSearch(){
        if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){requestCorePermissionsSafely();return;}
        startActivityForResult(new Intent(this,ContactSearchActivity.class),SEARCH_REQ);
    }

    @Override public void openSettings(){
        int pad=dp(18);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(pad,pad/2,pad,0);

        ImageView logo=new ImageView(this);logo.setImageResource(com.netwatch.phone.R.drawable.ic_netwatch_launcher);logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams logoP=new LinearLayout.LayoutParams(dp(74),dp(74));logoP.gravity=android.view.Gravity.CENTER_HORIZONTAL;box.addView(logo,logoP);

        TextView version=new TextView(this);
        version.setText("NetWatch Phone "+BuildConfig.VERSION_NAME+"\nSandia glass • NetWatch icon system • dial tones • caller intelligence");
        version.setGravity(android.view.Gravity.CENTER);version.setPadding(0,dp(8),0,pad/2);box.addView(version,new LinearLayout.LayoutParams(-1,-2));

        EditText api=new EditText(this);api.setSingleLine(true);api.setHint("Your contact-center API, e.g. http://192.168.1.20:8767");api.setText(AppConfig.getContactCenterUrl(this));box.addView(api,new LinearLayout.LayoutParams(-1,-2));
        if(AppConfig.isEmulatorPlaceholder(this)){
            TextView warn=new TextView(this);warn.setText("10.0.2.2 is an emulator-only address. On this Fairphone, enter the real LAN or public URL for your contact center.");warn.setTextColor(0xFFFFB86B);warn.setPadding(0,dp(7),0,dp(7));box.addView(warn);
        }

        Button save=new Button(this);save.setText("Save & test contact center");save.setOnClickListener(v->{if(!AppConfig.setContactCenterUrl(this,api.getText().toString())){Toast.makeText(this,"Invalid API URL",Toast.LENGTH_SHORT).show();return;}testApi();});box.addView(save);
        Button weather=new Button(this);weather.setText("Refresh Albuquerque weather");weather.setOnClickListener(v->refreshWeather(true));box.addView(weather);
        Button update=new Button(this);update.setText("Open NetWatch Update Center");update.setOnClickListener(v->startActivity(new Intent(this,UpdateCenterActivity.class)));box.addView(update);
        Button dialer=new Button(this);dialer.setText(holdsDialerRole()?"NetWatch is the default phone app":"Make NetWatch the default phone app");dialer.setOnClickListener(v->requestRoleSafely(RoleManager.ROLE_DIALER,ROLE_DIALER_REQ));box.addView(dialer);
        Button screening=new Button(this);screening.setText("Enable NetWatch call screening");screening.setOnClickListener(v->requestRoleSafely(RoleManager.ROLE_CALL_SCREENING,ROLE_SCREEN_REQ));box.addView(screening);
        Button appInfo=new Button(this);appInfo.setText("Open App Info / restricted settings");appInfo.setOnClickListener(v->openAppInfo());box.addView(appInfo);

        new AlertDialog.Builder(this).setTitle("NetWatch Phone").setMessage("Phone → your API → your contact center. Dial-pad tones are generated locally. Weather sends only fixed Albuquerque city coordinates.")
                .setView(box).setPositiveButton("Done",null).show();
    }

    private void testApi(){
        if(!AppConfig.isUsableContactCenter(this)){Toast.makeText(this,"Set your real contact-center address first.",Toast.LENGTH_LONG).show();return;}
        Toast.makeText(this,"Testing contact center…",Toast.LENGTH_SHORT).show();
        executor.execute(()->{try{new ApiClient(this).health();runOnUiThread(()->Toast.makeText(this,"Contact center is reachable ✓",Toast.LENGTH_LONG).show());}
        catch(Throwable e){runOnUiThread(()->Toast.makeText(this,"Contact center test failed: "+shortMessage(e),Toast.LENGTH_LONG).show());}});
    }

    private void refreshWeather(boolean force){
        executor.execute(()->{final WeatherSnapshot snapshot=WeatherClient.load(this,force);runOnUiThread(()->{lastWeather=snapshot;if(phoneView!=null)phoneView.setWeather(snapshot);if(force)Toast.makeText(this,snapshot.ok?"Weather updated":"Weather unavailable",Toast.LENGTH_SHORT).show();});});
    }

    private void requestRoleSafely(String role,int requestCode){
        try{
            RoleManager rm=(RoleManager)getSystemService(Context.ROLE_SERVICE);
            if(rm==null||!rm.isRoleAvailable(role)){Toast.makeText(this,"That Android role is unavailable.",Toast.LENGTH_SHORT).show();return;}
            if(rm.isRoleHeld(role)){if(RoleManager.ROLE_DIALER.equals(role))requestDialerPermissionsSafely();Toast.makeText(this,"Already enabled",Toast.LENGTH_SHORT).show();return;}
            startActivityForResult(rm.createRequestRoleIntent(role),requestCode);
        }catch(Throwable e){Toast.makeText(this,"Role request failed: "+shortMessage(e),Toast.LENGTH_LONG).show();}
    }

    private boolean holdsDialerRole(){try{RoleManager rm=(RoleManager)getSystemService(Context.ROLE_SERVICE);return rm!=null&&rm.isRoleAvailable(RoleManager.ROLE_DIALER)&&rm.isRoleHeld(RoleManager.ROLE_DIALER);}catch(Throwable e){return false;}}

    private void showRestrictedSettingsHelp(){
        new AlertDialog.Builder(this).setTitle("Android blocked the default Phone role")
                .setMessage("Because NetWatch was sideloaded, Android may require one-time approval. Open App Info, tap the ⋮ menu, choose “Allow restricted settings,” then return and make NetWatch the default Phone app.")
                .setNegativeButton("Later",null).setPositiveButton("Open App Info",(d,w)->openAppInfo()).show();
    }
    private void openAppInfo(){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Throwable ignored){}}

    private void requestCorePermissionsSafely(){
        try{
            String[] p={Manifest.permission.CALL_PHONE,Manifest.permission.READ_CONTACTS};ArrayList<String> missing=new ArrayList<>();
            for(String perm:p)if(checkSelfPermission(perm)!=PackageManager.PERMISSION_GRANTED)missing.add(perm);
            if(!missing.isEmpty())requestPermissions(missing.toArray(new String[0]),PERM_CORE_REQ);
            if(holdsDialerRole())requestDialerPermissionsSafely();
        }catch(Throwable ignored){}
    }

    private void requestDialerPermissionsSafely(){
        try{
            String[] p={Manifest.permission.READ_PHONE_STATE,Manifest.permission.READ_CALL_LOG,Manifest.permission.WRITE_CALL_LOG,Manifest.permission.ANSWER_PHONE_CALLS};ArrayList<String> missing=new ArrayList<>();
            for(String perm:p)if(checkSelfPermission(perm)!=PackageManager.PERMISSION_GRANTED)missing.add(perm);
            if(!missing.isEmpty())requestPermissions(missing.toArray(new String[0]),PERM_DIALER_REQ);
        }catch(Throwable ignored){}
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==ROLE_DIALER_REQ){
            if(holdsDialerRole()){
                requestDialerPermissionsSafely();refreshDeviceDataSafely();
                if(!pendingNumber.isEmpty()){String n=pendingNumber;pendingNumber="";phoneView.postDelayed(()->placeCall(n),450);}
            }else showRestrictedSettingsHelp();
        }else if(requestCode==SEARCH_REQ&&resultCode==RESULT_OK&&data!=null){
            String n=data.getStringExtra(ContactSearchActivity.RESULT_NUMBER);if(n!=null&&!n.isEmpty())placeCall(n);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==PERM_CORE_REQ||requestCode==PERM_DIALER_REQ)refreshDeviceDataSafely();}

    private void refreshDeviceDataSafely(){
        if(phoneView==null)return;
        try{phoneView.setRecentCalls(readRecentCalls());}catch(Throwable e){phoneView.setRecentCalls(new ArrayList<>());}
        try{phoneView.setContacts(readContacts());}catch(Throwable e){phoneView.setContacts(new ArrayList<>());}
    }

    private List<GlassPhoneView.RecentCall> readRecentCalls(){
        List<GlassPhoneView.RecentCall> out=new ArrayList<>();
        if(checkSelfPermission(Manifest.permission.READ_CALL_LOG)!=PackageManager.PERMISSION_GRANTED)return out;
        String[] projection={CallLog.Calls.NUMBER,CallLog.Calls.CACHED_NAME,CallLog.Calls.TYPE,CallLog.Calls.DATE};
        try(Cursor c=getContentResolver().query(CallLog.Calls.CONTENT_URI,projection,null,null,CallLog.Calls.DATE+" DESC")){
            if(c==null)return out;int nc=c.getColumnIndexOrThrow(CallLog.Calls.NUMBER),namec=c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME),tc=c.getColumnIndexOrThrow(CallLog.Calls.TYPE),dc=c.getColumnIndexOrThrow(CallLog.Calls.DATE);
            while(c.moveToNext()&&out.size()<200){String number=c.getString(nc),name=c.getString(namec);int type=c.getInt(tc);long date=c.getLong(dc);boolean missed=type==CallLog.Calls.MISSED_TYPE||type==CallLog.Calls.REJECTED_TYPE;String direction=type==CallLog.Calls.OUTGOING_TYPE?"↗ Mobile":(missed?"Missed":"↙ Mobile");out.add(new GlassPhoneView.RecentCall(name,number,direction+" • "+friendlyTime(date),missed));}
        }catch(Throwable ignored){}
        return out;
    }

    private List<GlassPhoneView.ContactItem> readContacts(){
        List<GlassPhoneView.ContactItem> out=new ArrayList<>();
        if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return out;
        String[] projection={ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER};
        try(Cursor c=getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,projection,null,null,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" COLLATE NOCASE ASC")){
            if(c==null)return out;int namec=c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),nc=c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER);HashSet<String> seen=new HashSet<>();
            while(c.moveToNext()){String name=c.getString(namec),number=c.getString(nc);if(name==null||number==null)continue;String key=name+"|"+number;if(!seen.add(key))continue;out.add(new GlassPhoneView.ContactItem(name,number));}
        }catch(Throwable ignored){}
        return out;
    }

    private String formatSunTime(String iso){if(iso==null||iso.isEmpty())return "—";int t=iso.indexOf('T');return t>=0&&t+1<iso.length()?iso.substring(t+1):iso;}
    private String friendlyTime(long millis){long age=System.currentTimeMillis()-millis;if(age>=0&&age<24L*60L*60L*1000L)return new SimpleDateFormat("h:mm a",Locale.getDefault()).format(new Date(millis));if(age>=0&&age<48L*60L*60L*1000L)return"Yesterday";return new SimpleDateFormat("EEE",Locale.getDefault()).format(new Date(millis));}

    private void showEmergencyUi(Throwable problem){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(24),dp(24),dp(24),dp(24));root.setBackgroundColor(Color.rgb(8,20,34));TextView t=new TextView(this);t.setText("NetWatch Phone safe mode\n\n"+problem.getClass().getSimpleName()+": "+String.valueOf(problem.getMessage()));t.setTextColor(Color.WHITE);t.setTextSize(18);root.addView(t);Button b=new Button(this);b.setText("Open NetWatch settings");b.setOnClickListener(v->openSettings());root.addView(b);setContentView(root);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private static String shortMessage(Throwable t){String m=t.getMessage();return m==null||m.trim().isEmpty()?t.getClass().getSimpleName():m;}
}
