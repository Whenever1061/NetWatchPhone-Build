package com.netwatch.phone.ui;

import android.app.Activity;
import android.content.Context;
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

/** A full system-style update dashboard for NetWatch Phone. */
public final class UpdateCenterActivity extends Activity {
    private static final String MANIFEST =
            "https://github.com/Whenever1061/NetWatchPhone-Build/releases/download/netwatch-latest/update.json";
    private static final String PREFS="netwatch_update_center";
    private final ExecutorService executor=Executors.newSingleThreadExecutor();

    private TextView available, lastChecked, integrity, status, releaseNotes;
    private Button checkButton, installButton;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(6,17,31));
        getWindow().setNavigationBarColor(Color.rgb(4,12,22));

        FrameLayout frame=new FrameLayout(this);
        ImageView bg=new ImageView(this);bg.setImageResource(R.drawable.mountains_sunset);bg.setScaleType(ImageView.ScaleType.CENTER_CROP);frame.addView(bg,new FrameLayout.LayoutParams(-1,-1));
        android.view.View shade=new android.view.View(this);shade.setBackgroundColor(0xDC071522);frame.addView(shade,new FrameLayout.LayoutParams(-1,-1));

        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(20),dp(18),dp(30));
        scroll.addView(root);frame.addView(scroll,new FrameLayout.LayoutParams(-1,-1));

        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.netwatch_phone_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams lpLogo=new LinearLayout.LayoutParams(dp(92),dp(92));
        lpLogo.gravity=Gravity.CENTER_HORIZONTAL;
        root.addView(logo,lpLogo);

        TextView title=label("NetWatch Update Center",27,Color.WHITE,true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0,dp(8),0,dp(2)); root.addView(title);
        TextView subtitle=label("Signed releases • integrity checks • update history",12,0xFFBDD2E5,false);
        subtitle.setGravity(Gravity.CENTER); subtitle.setPadding(0,0,0,dp(18)); root.addView(subtitle);

        LinearLayout installed=card();
        installed.addView(sectionTitle("Installed system"));
        installed.addView(row("Current version",BuildConfig.VERSION_NAME+"  (build "+BuildConfig.VERSION_CODE+")"));
        installed.addView(row("Update channel","Stable • GitHub signed release"));
        installed.addView(row("Last app update",installedDate()));
        installed.addView(row("Package","com.netwatch.phone"));
        root.addView(installed,cardParams());

        LinearLayout network=card();
        network.addView(sectionTitle("Release status"));
        available=valueRow(network,"Available version","Checking…");
        lastChecked=valueRow(network,"Last checked",lastCheckedText());
        integrity=valueRow(network,"Integrity","Permanent signing + SHA-256 validation");
        status=valueRow(network,"Status","Ready to check");
        root.addView(network,cardParams());

        LinearLayout notes=card();
        notes.addView(sectionTitle("Release information"));
        releaseNotes=label("Checking the signed update manifest…",13,0xFFE3EDF6,false);
        releaseNotes.setPadding(0,dp(6),0,dp(6));
        releaseNotes.setMovementMethod(new ScrollingMovementMethod());
        notes.addView(releaseNotes);
        root.addView(notes,cardParams());

        LinearLayout future=card();
        future.addView(sectionTitle("Potential next updates"));
        future.addView(bullet("Two-way NetWatch AI call audio bridge on /e/OS"));
        future.addView(bullet("Additional caller-intelligence providers through your contact center"));
        future.addView(bullet("Voicemail transcription and local summaries"));
        future.addView(bullet("More Albuquerque background packs and theme controls"));
        future.addView(bullet("Optional ringtone packs and per-contact tones"));
        root.addView(future,cardParams());

        checkButton=new Button(this);checkButton.setText("CHECK FOR UPDATE");
        checkButton.setOnClickListener(v->refresh(true));root.addView(checkButton,new LinearLayout.LayoutParams(-1,dp(56)));

        installButton=new Button(this);installButton.setText("CHECK, VERIFY & INSTALL");
        installButton.setEnabled(false);
        installButton.setOnClickListener(v->GitHubUpdater.check(this,true));
        LinearLayout.LayoutParams ib=new LinearLayout.LayoutParams(-1,dp(56));ib.topMargin=dp(8);root.addView(installButton,ib);

        TextView security=label("NetWatch downloads only from the repository's stable release channel. The APK hash is verified before Android receives the installer session.",11,0xFF9FB5C8,false);
        security.setPadding(dp(4),dp(16),dp(4),0);root.addView(security);

        setContentView(frame);
        refresh(false);
    }

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
                    releaseNotes.setText(notes.isEmpty()
                            ?"0.6 adds the NetWatch icon, signature Albuquerque background, animated contact focus, richer updater and caller intelligence."
                            :notes);
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
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0x553B6385,0x33203852,0x44101D2C});
        g.setCornerRadius(dp(24));g.setStroke(dp(1),0x55FFFFFF);box.setBackground(g);return box;
    }
    private LinearLayout.LayoutParams cardParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);return p;}
    private TextView sectionTitle(String s){TextView v=label(s,16,Color.WHITE,true);v.setPadding(0,0,0,dp(8));return v;}
    private LinearLayout row(String a,String b){
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setPadding(0,dp(5),0,dp(5));
        TextView left=label(a,12,0xFFBDD0E1,false);TextView right=label(b,12,Color.WHITE,true);right.setGravity(Gravity.END);
        r.addView(left,new LinearLayout.LayoutParams(0,-2,1f));r.addView(right,new LinearLayout.LayoutParams(0,-2,1.4f));return r;
    }
    private TextView valueRow(LinearLayout parent,String a,String b){LinearLayout r=row(a,b);TextView v=(TextView)r.getChildAt(1);parent.addView(r);return v;}
    private TextView bullet(String s){TextView v=label("•  "+s,12.5f,0xFFD7E4EF,false);v.setPadding(0,dp(4),0,dp(4));return v;}
    private TextView label(String s,float size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));return v;}
    private String installedDate(){try{PackageInfo p=getPackageManager().getPackageInfo(getPackageName(),0);return formatTime(p.lastUpdateTime);}catch(Throwable e){return "Unknown";}}
    private String lastCheckedText(){long t=getSharedPreferences(PREFS,Context.MODE_PRIVATE).getLong("last_checked",0);return t==0?"Never":formatTime(t);}
    private String formatTime(long t){return DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,Locale.getDefault()).format(new Date(t));}
    private static String shortMessage(Throwable t){String m=t.getMessage();return m==null||m.trim().isEmpty()?t.getClass().getSimpleName():m;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){executor.shutdownNow();super.onDestroy();}
}
