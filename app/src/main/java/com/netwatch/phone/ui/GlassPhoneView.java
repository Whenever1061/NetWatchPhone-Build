package com.netwatch.phone.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public final class GlassPhoneView extends View {
    public interface Callback {
        void placeCall(String number);
        void openSettings();
    }

    public static final class RecentCall {
        public final String name, number, detail;
        public final boolean missed;
        public RecentCall(String name, String number, String detail, boolean missed) {
            this.name = name == null ? "" : name;
            this.number = number == null ? "" : number;
            this.detail = detail == null ? "" : detail;
            this.missed = missed;
        }
    }

    public static final class ContactItem {
        public final String name, number;
        public ContactItem(String name, String number) {
            this.name = name == null ? "" : name;
            this.number = number == null ? "" : number;
        }
    }

    private enum Page { FAVORITES, RECENTS, CONTACTS, KEYPAD }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Callback callback;
    private final List<RecentCall> recents = new ArrayList<>();
    private final List<ContactItem> contacts = new ArrayList<>();
    private final StringBuilder digits = new StringBuilder();
    private Page page = Page.RECENTS;
    private float d, sd, w, h, navTop;
    private Throwable lastDrawError;

    private static final String[][] KEYS = {
            {"1",""}, {"2","ABC"}, {"3","DEF"},
            {"4","GHI"}, {"5","JKL"}, {"6","MNO"},
            {"7","PQRS"}, {"8","TUV"}, {"9","WXYZ"},
            {"*",""}, {"0","+"}, {"#",""}
    };

    public GlassPhoneView(Context context, Callback callback) {
        super(context);
        this.callback = callback;
        d = getResources().getDisplayMetrics().density;
        sd = getResources().getDisplayMetrics().scaledDensity;
        setFocusable(true);
        setClickable(true);
        setBackgroundColor(Color.rgb(7, 18, 31));
        setContentDescription("NetWatch Phone");
    }

    public void setRecentCalls(List<RecentCall> items) {
        recents.clear();
        if (items != null) recents.addAll(items);
        invalidate();
    }

    public void setContacts(List<ContactItem> items) {
        contacts.clear();
        if (items != null) contacts.addAll(items);
        invalidate();
    }

    public void showKeypad(String preset) {
        page = Page.KEYPAD;
        digits.setLength(0);
        if (preset != null) digits.append(preset);
        invalidate();
    }

    @Override protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        w = width;
        h = height;
        navTop = Math.max(dp(480), h - dp(88));
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        try {
            lastDrawError = null;
            drawBackground(c);
            drawHeader(c);
            if (page == Page.RECENTS) drawRecents(c);
            else if (page == Page.KEYPAD) drawKeypad(c);
            else if (page == Page.CONTACTS) drawContacts(c);
            else drawFavorites(c);
            drawNav(c);
        } catch (Throwable error) {
            lastDrawError = error;
            drawSafeFallback(c, error);
        }
    }

    private void drawBackground(Canvas c) {
        p.setShader(new LinearGradient(0, 0, Math.max(1,w), Math.max(1,h),
                new int[]{0xFF274F74, 0xFF102B47, 0xFF06111F},
                new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setColor(0x18FFFFFF);
        c.drawCircle(w * .16f, h * .17f, dp(110), p);
        p.setColor(0x166CC7FF);
        c.drawCircle(w * .82f, h * .30f, dp(150), p);
        p.setColor(0x125FE6C4);
        c.drawCircle(w * .54f, h * .60f, dp(180), p);

        Path mountains = new Path();
        mountains.moveTo(0, h * .45f);
        mountains.lineTo(w * .18f, h * .29f);
        mountains.lineTo(w * .31f, h * .41f);
        mountains.lineTo(w * .46f, h * .23f);
        mountains.lineTo(w * .62f, h * .41f);
        mountains.lineTo(w * .78f, h * .30f);
        mountains.lineTo(w, h * .44f);
        mountains.lineTo(w, h * .72f);
        mountains.lineTo(0, h * .72f);
        mountains.close();
        p.setColor(0x46203A52);
        c.drawPath(mountains, p);
    }

    private void drawHeader(Canvas c) {
        float y = dp(30);
        drawShield(c, dp(31), y + dp(21), dp(18));
        text(c, "NetWatch Phone", dp(60), y + dp(22), sp(26), Color.WHITE, true, Paint.Align.LEFT);
        text(c, page == Page.KEYPAD ? "Private. Protected. In your control."
                        : "Private calls for a safer, quieter you.",
                dp(60), y + dp(43), sp(12), 0xFFBED4E9, false, Paint.Align.LEFT);

        RectF more = new RectF(w - dp(57), y, w - dp(17), y + dp(40));
        glass(c, more, dp(20), 0x2DFFFFFF, 0x70FFFFFF);
        p.setColor(Color.WHITE);
        c.drawCircle(more.centerX() - dp(7), more.centerY(), dp(1.5f), p);
        c.drawCircle(more.centerX(), more.centerY(), dp(1.5f), p);
        c.drawCircle(more.centerX() + dp(7), more.centerY(), dp(1.5f), p);
    }

    private void drawRecents(Canvas c) {
        float y = dp(95);
        RectF search = new RectF(dp(18), y, w - dp(18), y + dp(49));
        glass(c, search, dp(20), 0x27FFFFFF, 0x60FFFFFF);
        searchIcon(c, dp(38), y + dp(24));
        text(c, "Search contacts, numbers, or places", dp(58), y + dp(29),
                sp(12.5f), 0xFFE2ECF5, false, Paint.Align.LEFT);

        y += dp(60);
        float gap = dp(7);
        float chipW = (w - dp(36) - gap * 2f) / 3f;
        chip(c, "All", dp(18), y, chipW, true);
        chip(c, "Missed", dp(18)+chipW+gap, y, chipW, false);
        chip(c, "Voicemail", dp(18)+(chipW+gap)*2, y, chipW, false);

        y += dp(55);
        text(c, "Today", dp(20), y, sp(15), Color.WHITE, true, Paint.Align.LEFT);
        y += dp(13);

        if (recents.isEmpty()) {
            emptyCard(c, "Your recent calls", "Make NetWatch your default phone app to show call history.", y + dp(80));
            return;
        }

        float rowH = dp(68);
        int max = Math.min(8, recents.size());
        for (int i=0; i<max && y + rowH < navTop - dp(4); i++) {
            RecentCall item = recents.get(i);
            RectF r = new RectF(dp(15), y, w-dp(15), y+rowH-dp(5));
            glass(c, r, dp(21), 0x19FFFFFF, 0x37FFFFFF);
            float cy = r.centerY();
            avatar(c, dp(47), cy, dp(21), display(item), item.missed);
            text(c, display(item), dp(79), cy-dp(3), sp(14.5f), Color.WHITE, false, Paint.Align.LEFT);
            text(c, item.detail, dp(79), cy+dp(18), sp(11), item.missed ? 0xFFFF7786 : 0xFFC8D7E6, false, Paint.Align.LEFT);
            phoneButton(c, w-dp(43), cy, dp(18), 0x25FFFFFF);
            y += rowH;
        }
    }

    private void drawContacts(Canvas c) {
        float y = dp(100);
        RectF search = new RectF(dp(18), y, w-dp(18), y+dp(49));
        glass(c, search, dp(20), 0x27FFFFFF, 0x60FFFFFF);
        searchIcon(c, dp(38), y+dp(24));
        text(c, "Search contacts", dp(58), y+dp(29), sp(12.5f), 0xFFE2ECF5, false, Paint.Align.LEFT);
        y += dp(72);
        text(c, "Contacts", dp(20), y, sp(25), Color.WHITE, true, Paint.Align.LEFT);
        y += dp(18);

        if (contacts.isEmpty()) {
            emptyCard(c, "No contacts yet", "Allow Contacts permission from the menu.", y+dp(80));
            return;
        }

        for (int i=0; i<contacts.size() && i<9 && y+dp(64)<navTop; i++) {
            ContactItem it = contacts.get(i);
            float cy = y+dp(30);
            avatar(c, dp(47), cy, dp(21), it.name, false);
            text(c, it.name, dp(79), cy-dp(3), sp(14.5f), Color.WHITE, false, Paint.Align.LEFT);
            text(c, it.number, dp(79), cy+dp(17), sp(11), 0xFFC8D7E6, false, Paint.Align.LEFT);
            phoneButton(c, w-dp(43), cy, dp(18), 0x20FFFFFF);
            line.setColor(0x25FFFFFF);
            line.setStrokeWidth(dp(1));
            c.drawLine(dp(79), y+dp(61), w-dp(18), y+dp(61), line);
            y += dp(66);
        }
    }

    private void drawFavorites(Canvas c) {
        float y = dp(111);
        text(c, "Favorites", dp(20), y, sp(27), Color.WHITE, true, Paint.Align.LEFT);
        text(c, "People you call most", dp(20), y+dp(24), sp(12), 0xFFBED4E9, false, Paint.Align.LEFT);
        y += dp(50);

        if (contacts.isEmpty()) {
            emptyCard(c, "No favorites yet", "Your contacts will appear here.", y+dp(75));
            return;
        }

        int max = Math.min(6, contacts.size());
        for (int i=0; i<max && y+dp(72)<navTop; i++) {
            ContactItem it = contacts.get(i);
            RectF r = new RectF(dp(18), y, w-dp(18), y+dp(68));
            glass(c, r, dp(23), 0x1CFFFFFF, 0x3DFFFFFF);
            avatar(c, dp(49), r.centerY(), dp(22), it.name, false);
            text(c, it.name, dp(83), r.centerY()-dp(3), sp(15), Color.WHITE, false, Paint.Align.LEFT);
            text(c, it.number, dp(83), r.centerY()+dp(18), sp(11), 0xFFC8D7E6, false, Paint.Align.LEFT);
            phoneButton(c, w-dp(46), r.centerY(), dp(19), 0x25FFFFFF);
            y += dp(77);
        }
    }

    private void drawKeypad(Canvas c) {
        float top = dp(106);
        RectF display = new RectF(dp(27), top, w-dp(27), top+dp(70));
        glass(c, display, dp(27), 0x32FFFFFF, 0x80FFFFFF);
        String shown = digits.length()==0 ? "Enter a number" : digits.toString();
        text(c, shown, display.centerX(), display.centerY()+dp(8),
                digits.length()==0 ? sp(16) : sp(26),
                digits.length()==0 ? 0xFFB9CBE0 : Color.WHITE,
                false, Paint.Align.CENTER);

        if (digits.length()>0) {
            RectF back = new RectF(w-dp(70), top+dp(19), w-dp(38), top+dp(51));
            glass(c, back, dp(14), 0x22FFFFFF, 0x45FFFFFF);
            text(c, "×", back.centerX(), back.centerY()+dp(7), sp(20), Color.WHITE, true, Paint.Align.CENTER);
        }

        float cx = w/2f;
        float colGap = Math.min(dp(112), w/3.25f);
        float startX = cx-colGap;
        float startY = top+dp(112);
        float rowGap = Math.min(dp(92), (navTop-startY-dp(78))/4.1f);
        float radius = Math.min(dp(37), colGap*.34f);

        for (int i=0;i<12;i++) {
            int col=i%3, row=i/3;
            float x=startX+col*colGap, y=startY+row*rowGap;
            key(c,x,y,radius,KEYS[i][0],KEYS[i][1]);
        }

        float callY = startY+rowGap*4f-dp(2);
        p.setShader(new LinearGradient(cx-dp(35), callY-dp(35), cx+dp(35), callY+dp(35),
                0xFF70EA6A, 0xFF12A13D, Shader.TileMode.CLAMP));
        c.drawCircle(cx, callY, dp(37), p);
        p.setShader(null);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(1.5f));
        line.setColor(0xB8FFFFFF);
        c.drawCircle(cx, callY, dp(37), line);
        line.setStyle(Paint.Style.FILL);
        phoneGlyph(c,cx,callY,Color.WHITE,1.2f);
    }

    private void drawNav(Canvas c) {
        RectF nav = new RectF(dp(11), navTop, w-dp(11), h-dp(10));
        glass(c, nav, dp(27), 0x42101A29, 0x65FFFFFF);
        String[] labels={"Favorites","Recents","Contacts","Keypad"};
        Page[] pages={Page.FAVORITES,Page.RECENTS,Page.CONTACTS,Page.KEYPAD};
        float slot=nav.width()/4f;
        for (int i=0;i<4;i++) {
            float cx=nav.left+slot*(i+.5f);
            boolean selected=page==pages[i];
            if (selected) {
                RectF sel=new RectF(cx-slot*.39f, nav.top+dp(7), cx+slot*.39f, nav.bottom-dp(7));
                glass(c,sel,dp(22),0x605294EE,0xB5D7EAFF);
            }
            navIcon(c,i,cx,nav.top+dp(27),selected?Color.WHITE:0xFFD1DBE5);
            text(c,labels[i],cx,nav.top+dp(63),sp(10),selected?Color.WHITE:0xFFD1DBE5,false,Paint.Align.CENTER);
        }
    }

    private void drawSafeFallback(Canvas c, Throwable e) {
        c.drawColor(Color.rgb(8,20,34));
        text(c,"NetWatch Phone",dp(24),dp(52),sp(28),Color.WHITE,true,Paint.Align.LEFT);
        text(c,"Glass safe mode",dp(24),dp(82),sp(16),0xFF9ED4FF,true,Paint.Align.LEFT);
        text(c,"The interface recovered instead of crashing.",dp(24),dp(116),sp(13),0xFFD6E8F8,false,Paint.Align.LEFT);
        text(c,e.getClass().getSimpleName(),dp(24),dp(150),sp(12),0xFFFFA4A4,false,Paint.Align.LEFT);
        RectF menu=new RectF(dp(24),dp(180),w-dp(24),dp(236));
        glass(c,menu,dp(22),0x33FFFFFF,0x66FFFFFF);
        text(c,"Tap here for settings",menu.centerX(),menu.centerY()+dp(5),sp(14),Color.WHITE,true,Paint.Align.CENTER);
    }

    private void glass(Canvas c, RectF r, float radius, int fill, int border) {
        p.setShader(new LinearGradient(r.left,r.top,r.right,r.bottom,
                brighten(fill,1.20f),darken(fill,.78f),Shader.TileMode.CLAMP));
        c.drawRoundRect(r,radius,radius,p);
        p.setShader(null);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(1));
        line.setColor(border);
        c.drawRoundRect(r,radius,radius,line);
        line.setStyle(Paint.Style.FILL);
    }

    private void chip(Canvas c,String label,float x,float y,float cw,boolean selected) {
        RectF r=new RectF(x,y,x+cw,y+dp(38));
        glass(c,r,dp(17),selected?0x70599FF2:0x1EFFFFFF,selected?0xCCDDF0FF:0x3AFFFFFF);
        text(c,label,r.centerX(),r.centerY()+dp(5),sp(12),Color.WHITE,false,Paint.Align.CENTER);
    }

    private void key(Canvas c,float x,float y,float radius,String main,String sub) {
        RectF r=new RectF(x-radius,y-radius,x+radius,y+radius);
        glass(c,r,radius,0x31FFFFFF,0x73FFFFFF);
        text(c,main,x,y+(sub.isEmpty()?dp(9):dp(2)),sp(29),Color.WHITE,false,Paint.Align.CENTER);
        if(!sub.isEmpty()) text(c,sub,x,y+dp(22),sp(10.5f),0xFFE7EEF6,false,Paint.Align.CENTER);
    }

    private void avatar(Canvas c,float x,float y,float radius,String label,boolean missed) {
        int seed=label==null?0:label.hashCode();
        int red=missed?190:65+Math.abs(seed%55);
        int green=missed?70:95+Math.abs((seed/7)%65);
        int blue=missed?92:130+Math.abs((seed/13)%70);
        p.setColor(Color.rgb(clamp(red),clamp(green),clamp(blue)));
        c.drawCircle(x,y,radius,p);
        text(c,initials(label),x,y+dp(5),sp(13),Color.WHITE,true,Paint.Align.CENTER);
    }

    private void phoneButton(Canvas c,float x,float y,float radius,int bg) {
        p.setColor(bg);
        c.drawCircle(x,y,radius,p);
        phoneGlyph(c,x,y,Color.WHITE,.68f);
    }

    private void phoneGlyph(Canvas c,float x,float y,int color,float scale) {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeWidth(dp(4.5f)*scale);
        line.setColor(color);
        RectF arc=new RectF(x-dp(11)*scale,y-dp(11)*scale,x+dp(11)*scale,y+dp(11)*scale);
        c.drawArc(arc,133,93,false,line);
        line.setStrokeCap(Paint.Cap.BUTT);
        line.setStyle(Paint.Style.FILL);
    }

    private void drawShield(Canvas c,float x,float y,float radius) {
        Path s=new Path();
        s.moveTo(x,y-radius);
        s.lineTo(x+radius*.78f,y-radius*.60f);
        s.lineTo(x+radius*.66f,y+radius*.42f);
        s.quadTo(x,y+radius*1.10f,x-radius*.66f,y+radius*.42f);
        s.lineTo(x-radius*.78f,y-radius*.60f);
        s.close();
        p.setShader(new LinearGradient(x-radius,y-radius,x+radius,y+radius,0xFF83E3FF,0xFF1F78D0,Shader.TileMode.CLAMP));
        c.drawPath(s,p);
        p.setShader(null);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(1.2f));
        line.setColor(0xD8E9FAFF);
        c.drawPath(s,line);
        line.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        c.drawCircle(x,y,dp(3),p);
    }

    private void searchIcon(Canvas c,float x,float y) {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(2));
        line.setColor(0xFFE6EDF5);
        c.drawCircle(x,y-dp(2),dp(7),line);
        c.drawLine(x+dp(5),y+dp(3),x+dp(11),y+dp(9),line);
        line.setStyle(Paint.Style.FILL);
    }

    private void navIcon(Canvas c,int index,float x,float y,int color) {
        p.setColor(color);
        line.setColor(color);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(2));
        if(index==0) {
            Path star=new Path();
            for(int i=0;i<10;i++){
                double a=-Math.PI/2+i*Math.PI/5;
                float r=(i%2==0)?dp(10):dp(4.5f);
                float px=x+(float)Math.cos(a)*r, py=y+(float)Math.sin(a)*r;
                if(i==0)star.moveTo(px,py);else star.lineTo(px,py);
            }
            star.close(); c.drawPath(star,p);
        } else if(index==1) {
            c.drawCircle(x,y,dp(9),line);
            c.drawLine(x,y,x,y-dp(5),line);
            c.drawLine(x,y,x+dp(4),y+dp(2),line);
        } else if(index==2) {
            c.drawCircle(x,y-dp(5),dp(4.5f),p);
            c.drawRoundRect(new RectF(x-dp(8),y+dp(2),x+dp(8),y+dp(10)),dp(5),dp(5),p);
        } else {
            for(int r=-1;r<=1;r++)for(int col=-1;col<=1;col++)c.drawCircle(x+col*dp(7),y+r*dp(7),dp(2.8f),p);
        }
        line.setStyle(Paint.Style.FILL);
    }

    private void emptyCard(Canvas c,String title,String subtitle,float y) {
        RectF r=new RectF(dp(24),y-dp(50),w-dp(24),y+dp(60));
        glass(c,r,dp(26),0x1CFFFFFF,0x3CFFFFFF);
        text(c,title,w/2f,y,sp(17),Color.WHITE,true,Paint.Align.CENTER);
        text(c,subtitle,w/2f,y+dp(27),sp(11.5f),0xFFBED4E9,false,Paint.Align.CENTER);
    }

    private void text(Canvas c,String value,float x,float y,float size,int color,boolean bold,Paint.Align align) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));
        p.setTextSize(size);
        p.setTextAlign(align);
        c.drawText(value==null?"":value,x,y,p);
    }

    private String display(RecentCall r) {
        if(r.name!=null&&!r.name.trim().isEmpty())return r.name;
        if(r.number!=null&&!r.number.trim().isEmpty())return r.number;
        return "Unknown Caller";
    }

    private String initials(String s) {
        if(s==null||s.trim().isEmpty())return "?";
        String[] a=s.trim().split("\\s+");
        String out=a[0].substring(0,1).toUpperCase();
        if(a.length>1)out+=a[a.length-1].substring(0,1).toUpperCase();
        return out;
    }

    private static int brighten(int color,float factor) {
        return Color.argb(Math.min(255,(int)(Color.alpha(color)*factor)),Color.red(color),Color.green(color),Color.blue(color));
    }
    private static int darken(int color,float factor) {
        return Color.argb(Math.max(0,(int)(Color.alpha(color)*factor)),Color.red(color),Color.green(color),Color.blue(color));
    }
    private static int clamp(int n){return Math.max(0,Math.min(255,n));}

    @Override public boolean onTouchEvent(MotionEvent e) {
        if(e.getAction()!=MotionEvent.ACTION_UP)return true;
        float x=e.getX(), y=e.getY();

        if(lastDrawError!=null) {
            if(callback!=null)callback.openSettings();
            return true;
        }

        if(y>=navTop) {
            float left=dp(11), slot=(w-dp(22))/4f;
            int i=(int)((x-left)/slot);
            if(i>=0&&i<4){ page=Page.values()[i]; invalidate(); }
            return true;
        }

        if(x>w-dp(75)&&y<dp(90)) {
            if(callback!=null)callback.openSettings();
            return true;
        }

        if(page==Page.KEYPAD)return keypadTouch(x,y);
        if(page==Page.RECENTS)return recentTouch(x,y);
        if(page==Page.CONTACTS)return contactTouch(x,y,false);
        if(page==Page.FAVORITES)return contactTouch(x,y,true);
        return true;
    }

    private boolean keypadTouch(float x,float y) {
        float top=dp(106);
        if(digits.length()>0&&x>w-dp(82)&&y>top&&y<top+dp(75)) {
            digits.deleteCharAt(digits.length()-1); invalidate(); return true;
        }

        float cx=w/2f;
        float colGap=Math.min(dp(112),w/3.25f);
        float startX=cx-colGap;
        float startY=top+dp(112);
        float rowGap=Math.min(dp(92),(navTop-startY-dp(78))/4.1f);
        float hit=Math.min(dp(44),colGap*.40f);

        for(int i=0;i<12;i++) {
            int col=i%3,row=i/3;
            float kx=startX+col*colGap, ky=startY+row*rowGap;
            if(distance(x,y,kx,ky)<=hit) {
                digits.append(KEYS[i][0]); invalidate(); return true;
            }
        }

        float callY=startY+rowGap*4f-dp(2);
        if(distance(x,y,cx,callY)<=dp(48)&&digits.length()>0&&callback!=null)callback.placeCall(digits.toString());
        return true;
    }

    private boolean recentTouch(float x,float y) {
        if(x<w-dp(85))return true;
        float start=dp(95)+dp(60)+dp(55)+dp(13);
        int i=(int)((y-start)/dp(68));
        if(i>=0&&i<recents.size()&&callback!=null)callback.placeCall(recents.get(i).number);
        return true;
    }

    private boolean contactTouch(float x,float y,boolean favorite) {
        if(x<w-dp(85))return true;
        float start=favorite?dp(161):dp(190);
        float row=favorite?dp(77):dp(66);
        int i=(int)((y-start)/row);
        if(i>=0&&i<contacts.size()&&callback!=null)callback.placeCall(contacts.get(i).number);
        return true;
    }

    private static float distance(float x1,float y1,float x2,float y2){
        float dx=x1-x2,dy=y1-y2; return (float)Math.sqrt(dx*dx+dy*dy);
    }
    private float dp(float v){return v*d;}
    private float sp(float v){return v*sd;}
}
