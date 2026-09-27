package com.netwatch.phone.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.widget.*;
import com.netwatch.phone.BuildConfig;
import com.netwatch.phone.R;
import com.netwatch.phone.telecom.NetWatchRinger;
import com.netwatch.phone.update.GitHubUpdater;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** NetWatch Phone update dashboard. */
public final class UpdateCenterActivity extends Activity {
    private static final String MANIFEST=
            "https://github.com/Whenever1061/NetWatchPhone-Build/releases/download/netwatch-latest/update.json";
    private static final String PREFS="netwatch_update_center";
    private final ExecutorService executor=Executors.newSingleThreadExecutor();

    private TextView available,lastChecked,integrity,status,releaseNotes;
    private Button checkButton,installButton;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(5,15,27));
        getWindow().setNavigationBarColor(Color.rgb(4,11,20));

        FrameLayout frame=new FrameLayout(this);
        ImageView bg=new ImageView(this);
        bg.setImageResource(R.drawable.mountains_sunset);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(bg,new FrameLayout.LayoutParams(-1,-1));
        android.view.View shade=new android.view.View(this);
        shade.setBackgroundColor(0x66071522);
        frame.addView(shade,new FrameLayout.LayoutParams(-1,-1));

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(18),dp(18),dp(30));
        scroll.addView(root);frame.addView(scroll,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout hero=new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(4),dp(4),dp(4),dp(14));
        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.ic_netwatch_launcher);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        hero.addView(logo,new LinearLayout.LayoutParams(dp(72),dp(72)));
        LinearLayout heroText=new LinearLayout(this);heroText.setOrientation(LinearLayout.VERTICAL);heroText.setPadding(dp(12),0,0,0);
        heroText.addView(label("Update Center",27,Color.WHITE,true));
        heroText.addView(label("NetWatch Phone • signed releases • integrity checks",11.5f,0xFFD0E4F5,false));
        hero.addView(heroText,new LinearLayout.LayoutParams(0,-2,1f));
        root.addView(hero);

        LinearLayout installed=card();
        installed.addView(iconTitle(R.drawable.ic_privacy,"Installed system"));
        installed.addView(row("Current version",BuildConfig.VERSION_NAME+"  (build "+BuildConfig.VERSION_CODE+")"));
        installed.addView(row("Update channel","Stable • signed GitHub release"));
        installed.addView(row("Last app update",installedDate()));
        installed.addView(row("Package","com.netwatch.phone"));
        root.addView(installed,cardParams());

        LinearLayout network=card();
        network.addView(iconTitle(R.drawable.ic_update,"Release status"));
        available=valueRow(network,"Available version","Checking…");
        lastChecked=valueRow(network,"Last checked",lastCheckedText());
        integrity=valueRow(network,"Integrity","Permanent signing + SHA-256 validation");
        status=valueRow(network,"Status","Ready to check");
        root.addView(network,cardParams());

        LinearLayout notes=card();
        notes.addView(iconTitle(R.drawable.ic_download_update,"What's new"));
        releaseNotes=label("Checking the signed update manifest…",13,0xFFEAF4FC,false);
        releaseNotes.setPadding(0,dp(8),0,dp(6));
        releaseNotes.setMovementMethod(new ScrollingMovementMethod());
        notes.addView(releaseNotes);
        root.addView(notes,cardParams());

        LinearLayout identity=card();
        identity.addView(iconTitle(R.drawable.ic_netwatch_launcher,"NetWatch visual & sound system"));
        identity.addView(bullet("Portrait Sandia / Albuquerque background now fills phone screens without losing the mountain scene"));
        identity.addView(bullet("Lighter glass overlays keep the photograph visible behind NetWatch controls"));
        identity.addView(bullet("Five local NetWatch ringtones can be previewed and selected without cloud audio"));
        identity.addView(bullet("Caller profiles and individual recent-call deletion remain built in"));
        root.addView(identity,cardParams());

        Button tones=new Button(this);
        tones.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_sound,0,0,0);
        tones.setCompoundDrawablePadding(dp(10));
        tones.setText("RINGTONE PACK  •  "+NetWatchRinger.selectedName(this).toUpperCase(Locale.getDefault()));
        tones.setOnClickListener(v->startActivity(new Intent(this,RingtonePackActivity.class)));
        root.addView(tones,new LinearLayout.LayoutParams(-1,dp(58)));

        checkButton=new Button(this);
        checkButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_update,0,0,0);
        checkButton.setCompoundDrawablePadding(dp(10));
        checkButton.setText("CHECK FOR UPDATE");
        checkButton.setOnClickListener(v->refresh(true));
        LinearLayout.LayoutParams cb=new LinearLayout.LayoutParams(-1,dp(58));cb.topMargin=dp(8);root.addView(checkButton,cb);

        installButton=new Button(this);
        installButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_download,0,0,0);
        installButton.setCompoundDrawablePadding(dp(10));
        installButton.setText("CHECK, VERIFY & INSTALL");
        installButton.setEnabled(false);
        installButton.setOnClickListener(v->GitHubUpdater.check(this,true));
        LinearLayout.LayoutParams ib=new LinearLayout.LayoutParams(-1,dp(58));ib.topMargin=dp(8);root.addView(installButton,ib);

        TextView security=label("NetWatch downloads only from the stable release channel. The package SHA-256 is verified before Android receives the installer session.",11,0xFFAAC3D7,false);
        security.setPadding(dp(4),dp(16),dp(4),0);root.addView(security);

        setContentView(frame);
        refresh(false);
    }

    @Override protected void onResume(){super.onResume();}

    private void refresh(boolean user){
        checkButton.setEnabled(false);
        status.setText("Checking signed GitHub release…");
        executor.execute(()->{
            try{
                HttpURLConnection c=(HttpURLConnection)new URL(MANIFEST).openConnection();
                c.setConnectTimeout(7000);c.setReadTimeout(7000);c.setInstanceFollowRedirects(true);
                c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","NetWatchPhone/"+BuildConfig.VERSION_NAME);
                int code=c.getResponseCode();
                if(code<200||code>=300)throw new IOException("GitHub HTTP "+code);
                StringBuilder b=new StringBuilder();
                try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){for(String line;(line=r.readLine())!=null;)b.append(line);}
                c.disconnect();
                JSONObject j=new JSONObject(b.toString());
                int remoteCode=j.optInt("version_code",0);
                String remoteName=j.optString("version_name","unknown");
                String sha=j.optString("sha256","");
                String notes=j.optString("notes","");
                long now=System.currentTimeMillis();
                getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putLong("last_checked",now)
                        .putString("last_available",remoteName).putString("last_status","OK").apply();
                runOnUiThread(()->{
                    available.setText(remoteName+"  (build "+remoteCode+")");
                    lastChecked.setText(formatTime(now));
                    integrity.setText(sha.length()==64?"Manifest SHA-256 present ✓":"Manifest integrity field missing");
                    boolean newer=remoteCode>BuildConfig.VERSION_CODE;
                    status.setText(newer?"Update available":"NetWatch Phone is current");
                    releaseNotes.setText(notes.isEmpty()?"NetWatch refreshes the Sandia background and adds the local ringtone pack.":notes);
                    installButton.setEnabled(newer);
                    checkButton.setEnabled(true);
                    if(user)Toast.makeText(this,newer?"NetWatch "+remoteName+" is available":"You are current",Toast.LENGTH_LONG).show();
                });
            }catch(Throwable e){
                long now=System.currentTimeMillis();
                getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putLong("last_checked",now).putString("last_status","FAILED").apply();
                runOnUiThread(()->{
                    lastChecked.setText(formatTime(now));
                    status.setText("Check failed: "+shortMessage(e));
                    integrity.setText("No package was downloaded");
                    installButton.setEnabled(false);checkButton.setEnabled(true);
                    if(user)Toast.makeText(this,"Update check failed: "+shortMessage(e),Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private LinearLayout card(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(17),dp(15),dp(17),dp(15));
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0x76365F83,0x5A173551,0x66101D2C});
        g.setCornerRadius(dp(24));g.setStroke(dp(1),0x6FFFFFFF);box.setBackground(g);return box;
    }
    private LinearLayout iconTitle(int drawable,String text){
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,0,0,dp(8));
        ImageView icon=new ImageView(this);icon.setImageResource(drawable);icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);r.addView(icon,new LinearLayout.LayoutParams(dp(30),dp(30)));
        TextView t=label(text,16,Color.WHITE,true);t.setPadding(dp(9),0,0,0);r.addView(t,new LinearLayout.LayoutParams(0,-2,1f));return r;
    }
    private LinearLayout.LayoutParams cardParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);return p;}
    private LinearLayout row(String a,String b){
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setPadding(0,dp(5),0,dp(5));
        TextView left=label(a,12,0xFFC5D8E8,false);TextView right=label(b,12,Color.WHITE,true);right.setGravity(Gravity.END);
        r.addView(left,new LinearLayout.LayoutParams(0,-2,1f));r.addView(right,new LinearLayout.LayoutParams(0,-2,1.45f));return r;
    }
    private TextView valueRow(LinearLayout parent,String a,String b){LinearLayout r=row(a,b);TextView v=(TextView)r.getChildAt(1);parent.addView(r);return v;}
    private TextView bullet(String s){TextView v=label("•  "+s,12.5f,0xFFE2EEF7,false);v.setPadding(0,dp(4),0,dp(4));return v;}
    private TextView label(String s,float size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));return v;}
    private String installedDate(){try{PackageInfo p=getPackageManager().getPackageInfo(getPackageName(),0);return formatTime(p.lastUpdateTime);}catch(Throwable e){return "Unknown";}}
    private String lastCheckedText(){long t=getSharedPreferences(PREFS,Context.MODE_PRIVATE).getLong("last_checked",0);return t==0?"Never":formatTime(t);}
    private String formatTime(long t){return DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,Locale.getDefault()).format(new Date(t));}
    private static String shortMessage(Throwable t){String m=t.getMessage();return m==null||m.trim().isEmpty()?t.getClass().getSimpleName():m;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){executor.shutdownNow();super.onDestroy();}
}
