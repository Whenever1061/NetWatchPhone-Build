package com.netwatch.phone.ui;

import android.app.Activity;
import android.graphics.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import com.netwatch.phone.api.ApiClient;
import com.netwatch.phone.telecom.NetWatchInCallService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CallScreenActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private CallView view; private boolean incoming; private String number;
    private final Runnable poll=new Runnable(){@Override public void run(){if(view==null)return;int state=NetWatchInCallService.activeState();String n=NetWatchInCallService.activeNumber();if(n!=null&&!n.isEmpty())number=n;if(state==Call.STATE_RINGING)incoming=true;view.number=(number==null||number.isEmpty())?"Unknown Caller":number;view.state=state;view.muted=NetWatchInCallService.isMuted();view.speaker=NetWatchInCallService.isSpeakerOn();view.invalidate();if(state==Call.STATE_DISCONNECTED&&!incoming){handler.postDelayed(CallScreenActivity.this::finish,700);return;}handler.postDelayed(this,350);}};

    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON|android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().setStatusBarColor(Color.rgb(8,20,34));getWindow().setNavigationBarColor(Color.rgb(5,14,26));incoming=getIntent().getBooleanExtra("incoming",false);number=getIntent().getStringExtra("number");if(number==null||number.isEmpty())number=NetWatchInCallService.activeNumber();view=new CallView();setContentView(view);handler.post(poll);}
    @Override protected void onNewIntent(android.content.Intent i){super.onNewIntent(i);setIntent(i);incoming=i.getBooleanExtra("incoming",NetWatchInCallService.activeState()==Call.STATE_RINGING);String n=i.getStringExtra("number");if(n!=null&&!n.isEmpty())number=n;}
    private void answer(){if(!NetWatchInCallService.answerActive())Toast.makeText(this,"Call is no longer active",Toast.LENGTH_SHORT).show();else incoming=false;}
    private void decline(){NetWatchInCallService.declineActive();finish();}
    private void end(){NetWatchInCallService.disconnectActive();finish();}
    private void screen(){view.status="Connecting to your contact center…";view.invalidate();String n=number==null?"":number;executor.execute(()->{try{new ApiClient(this).startScreen(n);runOnUiThread(()->{view.status="NetWatch AI is ready. Two-way audio bridge is the next /e/OS service step.";view.invalidate();});}catch(Throwable e){runOnUiThread(()->{view.status="Contact center unavailable";view.invalidate();});}});}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);executor.shutdownNow();super.onDestroy();}

    private final class CallView extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG),s=new Paint(Paint.ANTI_ALIAS_FLAG);final float d=getResources().getDisplayMetrics().density,sd=getResources().getDisplayMetrics().scaledDensity;float w,h;String number=CallScreenActivity.this.number==null?"Unknown Caller":CallScreenActivity.this.number;int state=NetWatchInCallService.activeState();boolean muted,speaker;String status="This call is ready for NetWatch Screen.";
        CallView(){super(CallScreenActivity.this);setClickable(true);}
        @Override protected void onSizeChanged(int W,int H,int ow,int oh){w=W;h=H;}
        @Override protected void onDraw(Canvas c){p.setShader(new LinearGradient(0,0,Math.max(1,w),Math.max(1,h),0xFF214D74,0xFF07101E,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);p.setColor(0x20FFFFFF);c.drawCircle(w*.18f,h*.19f,dp(150),p);p.setColor(0x157CCBFF);c.drawCircle(w*.86f,h*.32f,dp(180),p);txt(c,"NetWatch Call",dp(28),dp(62),sp(27),Color.WHITE,true,Paint.Align.LEFT);txt(c,"Private call control. No Google dialer handoff.",dp(28),dp(86),sp(12),0xFFC1D5E8,false,Paint.Align.LEFT);p.setColor(0x396DA7DD);c.drawCircle(w/2,dp(180),dp(50),p);txt(c,incoming&&state==Call.STATE_RINGING?"Incoming call":stateText(state),w/2,dp(258),sp(14),0xFFD1E0EE,false,Paint.Align.CENTER);txt(c,number,w/2,dp(296),sp(26),Color.WHITE,true,Paint.Align.CENTER);if(incoming&&state==Call.STATE_RINGING)drawIncoming(c);else drawActive(c);}
        private void drawIncoming(Canvas c){RectF card=new RectF(dp(20),dp(330),w-dp(20),h-dp(210));glass(c,card,dp(28),0x26FFFFFF,0x7CDDEEFF);txt(c,"✦  NetWatch Screen",dp(42),dp(370),sp(15),Color.WHITE,true,Paint.Align.LEFT);wrap(c,status,dp(42),dp(405),w-dp(84));float cy=h-dp(120);action(c,w*.22f,cy,0xFF22B85A,"Answer",0);action(c,w*.50f,cy,0xFF2D7BD5,"Screen",1);action(c,w*.78f,cy,0xFFDC3F4F,"Decline",2);}
        private void drawActive(Canvas c){RectF card=new RectF(dp(24),dp(340),w-dp(24),dp(485));glass(c,card,dp(28),0x24FFFFFF,0x64DDEEFF);txt(c,stateText(state),w/2,dp(386),sp(20),Color.WHITE,true,Paint.Align.CENTER);txt(c,"NetWatch owns this in-call screen",w/2,dp(416),sp(12),0xFFC5D8E9,false,Paint.Align.CENTER);txt(c,muted?"Microphone muted":"Microphone live",w/2,dp(446),sp(11.5f),muted?0xFFFFC36B:0xFF8FE4A6,false,Paint.Align.CENTER);float y=h-dp(190);control(c,w*.27f,y,muted?0xFF315F92:0x355F8CB0,muted?"Unmute":"Mute","M");control(c,w*.73f,y,speaker?0xFF315F92:0x355F8CB0,speaker?"Earpiece":"Speaker","S");float ey=h-dp(90);p.setColor(0xFFE03D4D);c.drawCircle(w/2,ey,dp(40),p);phone(c,w/2,ey,Color.WHITE,true);txt(c,"End call",w/2,ey+dp(67),sp(14),Color.WHITE,true,Paint.Align.CENTER);}
        private void action(Canvas c,float x,float y,int color,String label,int icon){p.setColor(color);c.drawCircle(x,y,dp(38),p);if(icon==1)txt(c,"✦",x,y+dp(9),sp(27),Color.WHITE,true,Paint.Align.CENTER);else phone(c,x,y,Color.WHITE,icon==2);txt(c,label,x,y+dp(66),sp(14),Color.WHITE,true,Paint.Align.CENTER);}
        private void control(Canvas c,float x,float y,int color,String label,String glyph){p.setColor(color);c.drawCircle(x,y,dp(38),p);txt(c,glyph,x,y+dp(8),sp(21),Color.WHITE,true,Paint.Align.CENTER);txt(c,label,x,y+dp(63),sp(13),Color.WHITE,false,Paint.Align.CENTER);}
        private String stateText(int st){switch(st){case Call.STATE_DIALING:return "Calling…";case Call.STATE_CONNECTING:return "Connecting…";case Call.STATE_ACTIVE:return "On call";case Call.STATE_HOLDING:return "On hold";case Call.STATE_RINGING:return "Incoming call";case Call.STATE_DISCONNECTING:return "Ending call…";case Call.STATE_DISCONNECTED:return "Call ended";default:return "Call in progress";}}
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()!=MotionEvent.ACTION_UP)return true;float x=e.getX(),y=e.getY();if(incoming&&state==Call.STATE_RINGING){float cy=h-dp(120);if(Math.abs(y-cy)<=dp(60)){if(x<w*.36f)answer();else if(x<w*.64f)screen();else decline();}return true;}float cy=h-dp(190);if(Math.abs(y-cy)<=dp(55)){if(x<w*.5f){muted=NetWatchInCallService.toggleMute();invalidate();}else{speaker=NetWatchInCallService.toggleSpeaker();invalidate();}return true;}float ey=h-dp(90);if(Math.abs(y-ey)<=dp(60)){end();return true;}return true;}
        private void phone(Canvas c,float x,float y,int color,boolean hang){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(5));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(color);c.drawArc(new RectF(x-dp(12),y-dp(12),x+dp(12),y+dp(12)),hang?205:133,hang?130:93,false,p);p.setStyle(Paint.Style.FILL);p.setStrokeCap(Paint.Cap.BUTT);}
        private void glass(Canvas c,RectF r,float rad,int fill,int border){p.setShader(new LinearGradient(r.left,r.top,r.right,r.bottom,fill|0x12000000,fill,Shader.TileMode.CLAMP));c.drawRoundRect(r,rad,rad,p);p.setShader(null);s.setStyle(Paint.Style.STROKE);s.setStrokeWidth(dp(1));s.setColor(border);c.drawRoundRect(r,rad,rad,s);s.setStyle(Paint.Style.FILL);}
        private void wrap(Canvas c,String text,float x,float y,float max){p.setTextSize(sp(12));p.setColor(0xFFE6F1F9);String[] ws=(text==null?"":text).split("\\s+");String line="";float yy=y;for(String word:ws){String t=line.isEmpty()?word:line+" "+word;if(p.measureText(t)>max&&!line.isEmpty()){c.drawText(line,x,yy,p);yy+=dp(18);line=word;}else line=t;}if(!line.isEmpty())c.drawText(line,x,yy,p);}
        private void txt(Canvas c,String t,float x,float y,float size,int color,boolean bold,Paint.Align a){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));p.setTextSize(size);p.setTextAlign(a);c.drawText(t==null?"":t,x,y,p);p.setTextAlign(Paint.Align.LEFT);}
        float dp(float v){return v*d;}float sp(float v){return v*sd;}
    }
}
