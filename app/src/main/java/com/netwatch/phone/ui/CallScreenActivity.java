package com.netwatch.phone.ui;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.Toast;
import com.netwatch.phone.api.ApiClient;
import com.netwatch.phone.telecom.NetWatchInCallService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CallScreenActivity extends Activity {
    private ExecutorService executor;
    private CallScreenView screenView;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = w.getInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
        executor = Executors.newSingleThreadExecutor();
        String number = getIntent().getStringExtra("number");
        screenView = new CallScreenView(number == null ? "Unknown Caller" : number);
        setContentView(screenView);
    }

    private void answer() {
        if (!NetWatchInCallService.answerActive()) Toast.makeText(this, "Call is no longer active", Toast.LENGTH_SHORT).show();
        else finish();
    }

    private void decline() {
        NetWatchInCallService.declineActive();
        finish();
    }

    private void screen() {
        final String n = screenView.number;
        screenView.status = "Connecting to your contact center…";
        screenView.invalidate();
        executor.execute(() -> {
            try {
                new ApiClient(this).startScreen(n);
                runOnUiThread(() -> {
                    screenView.status = "NetWatch AI is ready. Audio bridge is waiting for the /e/OS privileged service.";
                    screenView.invalidate();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    screenView.status = "Contact center unavailable";
                    screenView.invalidate();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }

    private final class CallScreenView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float d = getResources().getDisplayMetrics().density;
        final String number;
        String status = "This call is ready for NetWatch Screen.";
        float w, h;

        CallScreenView(String number) { super(CallScreenActivity.this); this.number = number; setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { this.w = w; this.h = h; }

        @Override protected void onDraw(Canvas c) {
            p.setShader(new LinearGradient(0, 0, w, h, 0xFF214D74, 0xFF07101E, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p); p.setShader(null);
            p.setColor(0x22FFFFFF); c.drawCircle(w * .18f, h * .20f, dp(150), p);
            p.setColor(0x1782CAFF); c.drawCircle(w * .84f, h * .26f, dp(165), p);
            drawShield(c, dp(31), dp(58), dp(18));
            text(c, "NetWatch Screen", dp(62), dp(57), sp(26), Color.WHITE, true, Paint.Align.LEFT);
            text(c, "AI screening for a more peaceful you.", dp(62), dp(78), sp(12), 0xFFC1D5E8, false, Paint.Align.LEFT);
            p.setColor(0x396DA7DD); c.drawCircle(w / 2f, dp(166), dp(46), p);
            p.setColor(0xFFE7EFF8); c.drawCircle(w / 2f, dp(151), dp(14), p);
            RectF person = new RectF(w/2f-dp(27), dp(169), w/2f+dp(27), dp(195)); c.drawOval(person, p);
            text(c, "Incoming call", w/2f, dp(229), sp(13), 0xFFD1E0EE, false, Paint.Align.CENTER);
            text(c, "Unknown Caller", w/2f, dp(261), sp(26), Color.WHITE, true, Paint.Align.CENTER);
            text(c, number + "  •  Possibly spam", w/2f, dp(286), sp(13), 0xFFC7D9EA, false, Paint.Align.CENTER);
            RectF card = new RectF(dp(18), dp(316), w-dp(18), h-dp(198));
            glass(c, card, dp(28), 0x2AFFFFFF, 0x8EDDEEFF);
            text(c, "✦  NetWatch Screen", dp(38), dp(354), sp(15), Color.WHITE, true, Paint.Align.LEFT);
            text(c, "●  Live", w-dp(72), dp(354), sp(12), 0xFF8CF7FF, false, Paint.Align.LEFT);
            drawWave(c, dp(42), dp(392), w-dp(42));
            RectF caller = new RectF(dp(32), dp(424), w-dp(32), dp(524));
            glass(c, caller, dp(22), 0x20FFFFFF, 0x4FFFFFFF);
            text(c, "Caller", dp(55), dp(453), sp(13), 0xFFE8F1F8, true, Paint.Align.LEFT);
            text(c, "Unknown caller is waiting while", dp(55), dp(477), sp(12), 0xFFE6EEF6, false, Paint.Align.LEFT);
            text(c, "NetWatch checks the call.", dp(55), dp(496), sp(12), 0xFFE6EEF6, false, Paint.Align.LEFT);
            RectF ai = new RectF(dp(32), dp(538), w-dp(32), dp(638));
            glass(c, ai, dp(22), 0x2A4B87C9, 0x7ABFE5FF);
            text(c, "NetWatch AI", dp(55), dp(567), sp(13), 0xFFCDEBFF, true, Paint.Align.LEFT);
            drawWrapped(c, status, dp(55), dp(590), w-dp(80), sp(12), 0xFFE8F3FB);
            float cy = h - dp(116);
            drawAction(c, w*.22f, cy, 0xFF22B85A, "Answer", "Take the call", 0);
            drawAction(c, w*.50f, cy, 0xFF2D7BD5, "Screen", "Let AI handle it", 1);
            drawAction(c, w*.78f, cy, 0xFFDC3F4F, "Decline", "Block & report", 2);
        }

        private void drawWave(Canvas c, float left, float cy, float right) {
            p.setStrokeWidth(dp(2)); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(0xFF68D6FF);
            int bars = 34; float step = (right-left)/(bars-1);
            for (int i=0;i<bars;i++) { float phase = (float)(Math.sin(i*.78)*.5+.5); float amp = dp(3) + dp(18)*phase; float x = left+i*step; c.drawLine(x, cy-amp, x, cy+amp, p); }
            p.setStrokeCap(Paint.Cap.BUTT);
        }
        private void drawAction(Canvas c, float x, float y, int color, String label, String sub, int icon) {
            p.setShadowLayer(dp(12),0,dp(3), color & 0x99FFFFFF); p.setColor(color); c.drawCircle(x,y,dp(37),p); p.clearShadowLayer();
            stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(dp(1.2f)); stroke.setColor(0xBBFFFFFF); c.drawCircle(x,y,dp(37),stroke); stroke.setStyle(Paint.Style.FILL);
            if (icon==1) text(c,"✦",x,y+dp(9),sp(27),Color.WHITE,true,Paint.Align.CENTER); else drawPhone(c,x,y,Color.WHITE, icon==2);
            text(c,label,x,y+dp(64),sp(14),Color.WHITE,true,Paint.Align.CENTER); text(c,sub,x,y+dp(83),sp(9.5f),0xFFC5D4E4,false,Paint.Align.CENTER);
        }
        private void drawPhone(Canvas c,float x,float y,int color,boolean hangup) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(5)); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(color);
            RectF a=new RectF(x-dp(12),y-dp(12),x+dp(12),y+dp(12)); c.drawArc(a, hangup?205:133, hangup?130:93,false,p);
            p.setStyle(Paint.Style.FILL); p.setStrokeCap(Paint.Cap.BUTT);
        }
        private void drawShield(Canvas c,float x,float y,float r) {
            Path path=new Path(); path.moveTo(x,y-r); path.lineTo(x+r*.78f,y-r*.63f); path.lineTo(x+r*.67f,y+r*.43f); path.quadTo(x,y+r*1.12f,x-r*.67f,y+r*.43f); path.lineTo(x-r*.78f,y-r*.63f); path.close();
            p.setShader(new LinearGradient(x-r,y-r,x+r,y+r,0xFF7ED9FF,0xFF1D79D5,Shader.TileMode.CLAMP)); p.setShadowLayer(dp(10),0,0,0x995FCBFF); c.drawPath(path,p); p.clearShadowLayer(); p.setShader(null);
            stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(dp(1.4f)); stroke.setColor(0xCCDAF5FF); c.drawPath(path,stroke); stroke.setStyle(Paint.Style.FILL);
        }
        private void glass(Canvas c,RectF r,float radius,int fill,int border) {
            p.setShader(new LinearGradient(r.left,r.top,r.right,r.bottom, fill|0x12000000, fill, Shader.TileMode.CLAMP)); c.drawRoundRect(r,radius,radius,p); p.setShader(null);
            stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(dp(1)); stroke.setColor(border); c.drawRoundRect(r,radius,radius,stroke); stroke.setStyle(Paint.Style.FILL);
        }
        private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold,Paint.Align align) {
            p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTextAlign(align); p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL)); p.setTextSize(size); c.drawText(s==null?"":s,x,y,p); p.setTextAlign(Paint.Align.LEFT);
        }
        private void drawWrapped(Canvas c,String s,float x,float y,float maxW,float size,int color) {
            p.setTypeface(Typeface.create("sans",Typeface.NORMAL)); p.setTextSize(size); p.setColor(color);
            String[] words=(s==null?"":s).split("\\s+"); String line=""; float yy=y;
            for(String word:words){String test=line.isEmpty()?word:line+" "+word; if(p.measureText(test)>maxW && !line.isEmpty()){c.drawText(line,x,yy,p); yy+=dp(18); line=word;} else line=test;}
            if(!line.isEmpty()) c.drawText(line,x,yy,p);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getAction()!=MotionEvent.ACTION_UP) return true;
            float y=h-dp(116); float x=e.getX(); if(Math.abs(e.getY()-y)>dp(58)) return true;
            if(x<w*.36f) answer(); else if(x<w*.64f) screen(); else decline(); return true;
        }
        float dp(float v){return v*d;} float sp(float v){return v*getResources().getDisplayMetrics().scaledDensity;}
    }
}
