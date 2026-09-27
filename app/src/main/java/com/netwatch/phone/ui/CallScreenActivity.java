package com.netwatch.phone.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import com.netwatch.phone.R;
import com.netwatch.phone.api.ApiClient;
import com.netwatch.phone.config.AppConfig;
import com.netwatch.phone.telecom.CallerIntel;
import com.netwatch.phone.telecom.CallerIntelligence;
import com.netwatch.phone.telecom.ContactWriter;
import com.netwatch.phone.telecom.NetWatchInCallService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CallScreenActivity extends Activity {
    private static final int WRITE_CONTACT_REQ=2101;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService executor=Executors.newFixedThreadPool(2);

    private String number="";
    private boolean incoming;
    private CallerIntel intel;

    private TextView stateText,numberText,nameText,intelText,statusText;
    private Button answerButton,screenButton,declineButton,muteButton,speakerButton,saveButton;
    private String pendingSaveName="";

    private final Runnable poll=new Runnable(){
        @Override public void run(){
            int state=NetWatchInCallService.activeState();
            String active=NetWatchInCallService.activeNumber();
            if(active!=null&&!active.isEmpty()&&!active.equals(number)){number=active;numberText.setText(number);runIntel();}
            incoming=state==Call.STATE_RINGING;
            stateText.setText(stateText(state));
            updateActions(state);
            if(state==Call.STATE_DISCONNECTED){
                statusText.setText("Call ended");
                handler.postDelayed(CallScreenActivity.this::finish,850);
                return;
            }
            handler.postDelayed(this,300);
        }
    };

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.rgb(5,15,27));
        getWindow().setNavigationBarColor(Color.rgb(4,11,20));

        number=getIntent().getStringExtra("number");
        if(number==null||number.isEmpty())number=NetWatchInCallService.activeNumber();
        incoming=getIntent().getBooleanExtra("incoming",NetWatchInCallService.activeState()==Call.STATE_RINGING);

        setContentView(buildUi());
        numberText.setText(number==null||number.isEmpty()?"Unknown Caller":number);
        handler.post(poll);
        runIntel();
    }

    private View buildUi(){
        FrameLayout root=new FrameLayout(this);

        ImageView bg=new ImageView(this);
        bg.setImageResource(R.drawable.mountains_sunset);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(bg,new FrameLayout.LayoutParams(-1,-1));

        View shade=new View(this);
        GradientDrawable shadeBg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x8A06111F,0xC6081626,0xF006111F});
        shade.setBackground(shadeBg);root.addView(shade,new FrameLayout.LayoutParams(-1,-1));

        ScrollView scroll=new ScrollView(this);
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setGravity(Gravity.CENTER_HORIZONTAL);content.setPadding(dp(20),dp(22),dp(20),dp(28));
        scroll.addView(content);root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));

        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.netwatch_phone_icon);logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(78),dp(78));content.addView(logo,lp);

        TextView title=text("NetWatch Screen",27,Color.WHITE,true);title.setGravity(Gravity.CENTER);title.setPadding(0,dp(8),0,0);content.addView(title);
        TextView sub=text("Private caller intelligence • your phone • your contact center",12,0xFFBDD4E7,false);sub.setGravity(Gravity.CENTER);sub.setPadding(0,0,0,dp(18));content.addView(sub);

        LinearLayout identity=card();content.addView(identity,cardParams());
        stateText=text(incoming?"Incoming call":"Call",13,0xFFBBD0E3,false);stateText.setGravity(Gravity.CENTER);identity.addView(stateText);
        nameText=text("Checking caller…",25,Color.WHITE,true);nameText.setGravity(Gravity.CENTER);nameText.setPadding(0,dp(6),0,0);identity.addView(nameText);
        numberText=text(number,17,0xFFE2EBF4,false);numberText.setGravity(Gravity.CENTER);identity.addView(numberText);
        intelText=text("NetWatch is checking local contacts, call history and your configured contact center.",12,0xFFC6D7E6,false);intelText.setGravity(Gravity.CENTER);intelText.setPadding(0,dp(10),0,0);identity.addView(intelText);

        LinearLayout ai=card();content.addView(ai,cardParams());
        TextView aiTitle=text("✦  NetWatch AI caller checker",15,Color.WHITE,true);ai.addView(aiTitle);
        statusText=text("Looking for identity, location and risk signals…",13,0xFFE1ECF5,false);statusText.setPadding(0,dp(8),0,0);ai.addView(statusText);

        saveButton=new Button(this);saveButton.setText("SAVE / CORRECT CALLER");
        saveButton.setVisibility(View.GONE);saveButton.setOnClickListener(v->promptSave());
        LinearLayout.LayoutParams sb=new LinearLayout.LayoutParams(-1,dp(52));sb.topMargin=dp(10);ai.addView(saveButton,sb);

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setGravity(Gravity.CENTER);actions.setPadding(0,dp(8),0,dp(12));
        answerButton=button("ANSWER",0xFF1FAE57);answerButton.setOnClickListener(v->answer());
        screenButton=button("SCREEN",0xFF2C76CB);screenButton.setOnClickListener(v->screen());
        declineButton=button("DECLINE",0xFFD64351);declineButton.setOnClickListener(v->declineOrEnd());
        actions.addView(answerButton,actionParams());actions.addView(screenButton,actionParams());actions.addView(declineButton,actionParams());
        content.addView(actions,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout active=new LinearLayout(this);active.setOrientation(LinearLayout.HORIZONTAL);active.setGravity(Gravity.CENTER);
        muteButton=button("MUTE",0x554F7797);muteButton.setOnClickListener(v->{boolean m=NetWatchInCallService.toggleMute();muteButton.setText(m?"UNMUTE":"MUTE");});
        speakerButton=button("SPEAKER",0x554F7797);speakerButton.setOnClickListener(v->{boolean s=NetWatchInCallService.toggleSpeaker();speakerButton.setText(s?"EARPIECE":"SPEAKER");});
        active.addView(muteButton,actionParams());active.addView(speakerButton,actionParams());
        content.addView(active,new LinearLayout.LayoutParams(-1,-2));

        return root;
    }

    private void runIntel(){
        final String n=number==null?"":number;
        executor.execute(()->{
            CallerIntel result=CallerIntelligence.identify(this,n);
            runOnUiThread(()->applyIntel(result));
        });
    }

    private void applyIntel(CallerIntel result){
        intel=result;
        nameText.setText(result.title());
        intelText.setText(result.subtitle()+"\n"+result.confidenceLine());
        if(result.savedContact){
            statusText.setText(result.aiSummary.isEmpty()?"Saved contact verified locally. No outside lookup was needed.":result.aiSummary);
            saveButton.setVisibility(View.GONE);
        }else{
            String source=result.source.isEmpty()?"Local intelligence":result.source;
            String summary=result.aiSummary.isEmpty()?"Identity evidence: "+source+". NetWatch never treats a probable identity as certain without supporting data.":result.aiSummary+"\n\nEvidence: "+source;
            statusText.setText(summary);
            saveButton.setVisibility(View.VISIBLE);
        }
    }

    private void answer(){
        if(NetWatchInCallService.answerActive()){statusText.setText("Call answered by NetWatch.");}
        else Toast.makeText(this,"Call is no longer active",Toast.LENGTH_SHORT).show();
    }

    private void declineOrEnd(){
        int state=NetWatchInCallService.activeState();
        if(state==Call.STATE_RINGING)NetWatchInCallService.declineActive();else NetWatchInCallService.disconnectActive();
        finish();
    }

    private void screen(){
        statusText.setText("Starting NetWatch Screen through your contact center…");
        final String n=number==null?"":number;
        executor.execute(()->{
            try{
                if(!AppConfig.isUsableContactCenter(this))throw new IllegalStateException("contact center not configured");
                new ApiClient(this).startScreen(n);
                runOnUiThread(()->statusText.setText("NetWatch Screen session started. Caller identity and risk checking are active; two-way AI call audio still requires the privileged /e/OS audio bridge."));
            }catch(Throwable e){
                runOnUiThread(()->statusText.setText("Local caller checking is active. Two-way AI conversation is unavailable until your contact center/audio bridge is reachable."));
            }
        });
    }

    private void promptSave(){
        EditText name=new EditText(this);
        String suggestion=intel==null?"":intel.displayName;
        name.setText(suggestion);name.setSelectAllOnFocus(true);name.setHint("Caller name");
        new AlertDialog.Builder(this).setTitle("Save or correct caller")
                .setMessage("Confirm the caller's name before NetWatch writes it to your phone book. Your correction can also be sent to your own contact center so future calls are recognized.")
                .setView(name).setNegativeButton("Cancel",null)
                .setPositiveButton("Save",(d,w)->{
                    pendingSaveName=name.getText().toString().trim();
                    if(pendingSaveName.isEmpty()){Toast.makeText(this,"Enter a name first",Toast.LENGTH_SHORT).show();return;}
                    if(checkSelfPermission(Manifest.permission.WRITE_CONTACTS)!=PackageManager.PERMISSION_GRANTED){
                        requestPermissions(new String[]{Manifest.permission.WRITE_CONTACTS},WRITE_CONTACT_REQ);
                    }else saveContactNow();
                }).show();
    }

    private void saveContactNow(){
        try{
            ContactWriter.save(this,pendingSaveName,number);
            Toast.makeText(this,"Saved to contacts",Toast.LENGTH_LONG).show();
            nameText.setText(pendingSaveName);saveButton.setVisibility(View.GONE);
            if(AppConfig.isUsableContactCenter(this)){
                final String n=number, name=pendingSaveName;
                executor.execute(()->{try{new ApiClient(this).saveCallerCorrection(n,name);}catch(Throwable ignored){}});
            }
        }catch(Throwable e){Toast.makeText(this,"Could not save contact: "+shortMessage(e),Toast.LENGTH_LONG).show();}
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if(requestCode==WRITE_CONTACT_REQ&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)saveContactNow();
    }

    private void updateActions(int state){
        boolean ringing=state==Call.STATE_RINGING;
        answerButton.setVisibility(ringing?View.VISIBLE:View.GONE);
        screenButton.setVisibility(ringing?View.VISIBLE:View.GONE);
        declineButton.setText(ringing?"DECLINE":"END");
        muteButton.setVisibility(ringing?View.GONE:View.VISIBLE);
        speakerButton.setVisibility(ringing?View.GONE:View.VISIBLE);
    }

    private String stateText(int s){
        switch(s){
            case Call.STATE_RINGING:return "Incoming call";
            case Call.STATE_DIALING:return "Calling…";
            case Call.STATE_CONNECTING:return "Connecting…";
            case Call.STATE_ACTIVE:return "On call";
            case Call.STATE_HOLDING:return "On hold";
            case Call.STATE_DISCONNECTING:return "Ending call…";
            case Call.STATE_DISCONNECTED:return "Call ended";
            default:return "Call in progress";
        }
    }

    private LinearLayout card(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(16),dp(18),dp(16));
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0x603E6685,0x3A183249,0x55101A28});
        g.setCornerRadius(dp(26));g.setStroke(dp(1),0x75FFFFFF);box.setBackground(g);return box;
    }
    private LinearLayout.LayoutParams cardParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);return p;}
    private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(24));g.setStroke(dp(1),0x66FFFFFF);b.setBackground(g);return b;}
    private LinearLayout.LayoutParams actionParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(58),1f);p.setMargins(dp(4),0,dp(4),0);return p;}
    private TextView text(String s,float size,int color,boolean bold){TextView v=new TextView(this);v.setText(s==null?"":s);v.setTextSize(size);v.setTextColor(color);v.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));return v;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private static String shortMessage(Throwable t){String m=t.getMessage();return m==null||m.trim().isEmpty()?t.getClass().getSimpleName():m;}

    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);executor.shutdownNow();super.onDestroy();}
}
