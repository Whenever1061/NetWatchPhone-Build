package com.netwatch.phone.ui;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import com.netwatch.phone.weather.WeatherSnapshot;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public final class GlassPhoneView extends View {
    public interface Callback {
        void placeCall(String number);
        void openSettings();
        void openSearch();
        void openWeatherDetails();
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
    private WeatherSnapshot weather = WeatherSnapshot.loading();
    private float d, sd, w, h, navTop;
    private float downY;
    private int listOffset = 0;
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
        setBackgroundColor(Color.rgb(7,18,31));
        setContentDescription("NetWatch Phone");
    }

    public void setRecentCalls(List<RecentCall> items) {
        recents.clear();
        if (items != null) recents.addAll(items);
        if (page == Page.RECENTS) listOffset = clampOffset(listOffset, recents.size(), 8);
        invalidate();
    }

    public void setContacts(List<ContactItem> items) {
        contacts.clear();
        if (items != null) contacts.addAll(items);
        if (page == Page.CONTACTS || page == Page.FAVORITES) listOffset = clampOffset(listOffset, contacts.size(), page == Page.FAVORITES ? 6 : 8);
        invalidate();
    }

    public void setWeather(WeatherSnapshot snapshot) {
        if (snapshot != null) weather = snapshot;
        invalidate();
    }

    public void showKeypad(String preset) {
        page = Page.KEYPAD;
        listOffset = 0;
        digits.setLength(0);
        if (preset != null) digits.append(preset);
        invalidate();
    }

    @Override protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        w = width; h = height;
        navTop = Math.max(dp(500), h - dp(88));
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        try {
            lastDrawError = null;
            drawDailyAlbuquerque(c);
            drawHeader(c);
            drawWeatherStrip(c);
            if (page == Page.RECENTS) drawRecents(c);
            else if (page == Page.CONTACTS) drawContacts(c);
            else if (page == Page.FAVORITES) drawFavorites(c);
            else drawKeypad(c);
            drawNav(c);
        } catch (Throwable error) {
            lastDrawError = error;
            drawSafeFallback(c, error);
        }
    }

    private void drawDailyAlbuquerque(Canvas c) {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        int scene = Math.floorMod(day, 8);
        int[] top = {0xFF315B7B,0xFF34577A,0xFF6B553F,0xFF245E68,0xFF151B34,0xFF1C3C58,0xFF293753,0xFF7A4738};
        int[] bottom = {0xFF071420,0xFF0A1726,0xFF1A1716,0xFF081A22,0xFF090B13,0xFF07111D,0xFF090F1D,0xFF130E12};
        p.setShader(new LinearGradient(0,0,0,Math.max(1,h),top[scene],bottom[scene],Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p); p.setShader(null);

        switch(scene) {
            case 0: sceneSandia(c, true); break;
            case 1: sceneBalloons(c); break;
            case 2: sceneOldTown(c); break;
            case 3: sceneBosque(c); break;
            case 4: sceneRoute66(c); break;
            case 5: sceneDowntown(c); break;
            case 6: sceneMonsoon(c); break;
            default: sceneDesertSunset(c); break;
        }
        p.setColor(0x6604111E);
        c.drawRect(0,0,w,h,p);
    }

    private void sceneSandia(Canvas c, boolean sunrise) {
        if (sunrise) {
            p.setColor(0x4CFFD176); c.drawCircle(w*.72f,h*.21f,dp(68),p);
            p.setColor(0x25FFE7A0); c.drawCircle(w*.72f,h*.21f,dp(105),p);
        }
        Path m = new Path();
        m.moveTo(0,h*.46f);
        m.lineTo(w*.14f,h*.38f); m.lineTo(w*.29f,h*.43f); m.lineTo(w*.45f,h*.24f);
        m.lineTo(w*.53f,h*.34f); m.lineTo(w*.61f,h*.28f); m.lineTo(w*.71f,h*.42f);
        m.lineTo(w*.84f,h*.34f); m.lineTo(w,h*.44f); m.lineTo(w,h*.65f); m.lineTo(0,h*.65f); m.close();
        p.setColor(0xFF314859); c.drawPath(m,p);
        p.setColor(0x553F1F18);
        Path crest=new Path(); crest.moveTo(w*.37f,h*.34f); crest.lineTo(w*.45f,h*.24f); crest.lineTo(w*.53f,h*.34f); crest.lineTo(w*.61f,h*.28f); crest.lineTo(w*.68f,h*.40f); crest.lineTo(w*.37f,h*.40f); crest.close(); c.drawPath(crest,p);
    }

    private void sceneBalloons(Canvas c) {
        sceneSandia(c,false);
        balloon(c,w*.22f,h*.22f,dp(34),0xFFCE4A44,0xFFF3C75A);
        balloon(c,w*.66f,h*.18f,dp(27),0xFF3F86C7,0xFFF1F4E8);
        balloon(c,w*.84f,h*.31f,dp(20),0xFF53A36D,0xFFF0A85B);
        balloon(c,w*.42f,h*.34f,dp(18),0xFF8D5AC7,0xFFF7D35B);
    }

    private void balloon(Canvas c,float x,float y,float r,int a,int b) {
        RectF body=new RectF(x-r*.72f,y-r,x+r*.72f,y+r*.55f);
        p.setShader(new LinearGradient(body.left,body.top,body.right,body.bottom,a,b,Shader.TileMode.CLAMP));
        c.drawOval(body,p); p.setShader(null);
        p.setColor(0xFF7C5138); c.drawRect(x-r*.16f,y+r*.56f,x+r*.16f,y+r*.78f,p);
        p.setColor(0x88FFFFFF); c.drawOval(new RectF(x-r*.16f,y-r*.78f,x+r*.02f,y-r*.26f),p);
    }

    private void sceneOldTown(Canvas c) {
        sceneSandia(c,false);
        float base=h*.55f;
        p.setColor(0xFF825F47); c.drawRect(w*.13f,base-dp(90),w*.87f,base,p);
        p.setColor(0xFF9B7255); c.drawRect(w*.18f,base-dp(125),w*.36f,base,p); c.drawRect(w*.64f,base-dp(125),w*.82f,base,p);
        p.setColor(0xFFB99A78);
        c.drawRect(w*.22f,base-dp(160),w*.32f,base-dp(125),p); c.drawRect(w*.68f,base-dp(160),w*.78f,base-dp(125),p);
        p.setColor(0xFF2C211C);
        c.drawRoundRect(new RectF(w*.46f,base-dp(67),w*.54f,base),dp(20),dp(20),p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(3)); p.setColor(0xFFD0B497);
        c.drawCircle(w*.27f,base-dp(143),dp(10),p); c.drawCircle(w*.73f,base-dp(143),dp(10),p); p.setStyle(Paint.Style.FILL);
    }

    private void sceneBosque(Canvas c) {
        sceneSandia(c,false);
        p.setShader(new LinearGradient(0,h*.50f,0,h*.88f,0xFF315D69,0xFF0C2430,Shader.TileMode.CLAMP));
        c.drawRect(0,h*.52f,w,h*.90f,p); p.setShader(null);
        p.setColor(0xFF24463A);
        for(int i=0;i<13;i++){float x=w*(i/12f); float ht=dp(70)+(i%4)*dp(20); c.drawRect(x-dp(4),h*.55f-ht,x+dp(4),h*.72f,p); c.drawCircle(x,h*.55f-ht,dp(28)+(i%3)*dp(6),p);}
        p.setColor(0x553D9BC1); Path river=new Path(); river.moveTo(w*.34f,h*.53f); river.cubicTo(w*.20f,h*.65f,w*.66f,h*.72f,w*.42f,h*.92f); river.lineTo(w*.72f,h*.92f); river.cubicTo(w*.83f,h*.72f,w*.44f,h*.62f,w*.62f,h*.53f); river.close(); c.drawPath(river,p);
    }

    private void sceneRoute66(Canvas c) {
        p.setColor(0xFF101624); c.drawRect(0,h*.45f,w,h,p);
        p.setColor(0x333B86FF); c.drawRect(w*.48f,h*.50f,w*.52f,h,p);
        p.setColor(0x22FFFFFF); c.drawRect(w*.495f,h*.55f,w*.505f,h*.62f,p); c.drawRect(w*.495f,h*.68f,w*.505f,h*.76f,p);
        RectF sign=new RectF(w*.18f,h*.20f,w*.52f,h*.37f);
        p.setColor(0xAA14324B); c.drawRoundRect(sign,dp(18),dp(18),p);
        line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(dp(4)); line.setColor(0xFFFF5C78); c.drawRoundRect(sign,dp(18),dp(18),line); line.setStyle(Paint.Style.FILL);
        text(c,"ROUTE",sign.centerX(),sign.top+dp(38),sp(16),0xFFFFE9C7,true,Paint.Align.CENTER);
        text(c,"66",sign.centerX(),sign.top+dp(88),sp(38),Color.WHITE,true,Paint.Align.CENTER);
    }

    private void sceneDowntown(Canvas c) {
        p.setColor(0xFF132839);
        float base=h*.57f;
        float[] xs={.08f,.17f,.27f,.37f,.49f,.61f,.70f,.80f,.90f};
        float[] hs={75,120,88,165,112,142,92,128,78};
        for(int i=0;i<xs.length;i++){float bw=w*.09f; c.drawRect(w*xs[i],base-dp(hs[i]),w*xs[i]+bw,base,p);}
        p.setColor(0x88F4C66D);
        for(int i=0;i<xs.length;i++) for(int r=0;r<4;r++) c.drawRect(w*xs[i]+dp(8),base-dp(hs[i])+dp(12+r*20),w*xs[i]+dp(13),base-dp(hs[i])+dp(18+r*20),p);
        sceneSandia(c,false);
    }

    private void sceneMonsoon(Canvas c) {
        p.setColor(0x99343E4E); c.drawCircle(w*.24f,h*.20f,dp(82),p); c.drawCircle(w*.48f,h*.16f,dp(110),p); c.drawCircle(w*.76f,h*.23f,dp(96),p);
        p.setColor(0xFFCDD7E0);
        Path bolt=new Path(); bolt.moveTo(w*.57f,h*.24f); bolt.lineTo(w*.47f,h*.39f); bolt.lineTo(w*.54f,h*.39f); bolt.lineTo(w*.44f,h*.54f); bolt.lineTo(w*.64f,h*.34f); bolt.lineTo(w*.56f,h*.34f); bolt.close(); c.drawPath(bolt,p);
        sceneSandia(c,false);
    }

    private void sceneDesertSunset(Canvas c) {
        p.setColor(0x66FFB45C); c.drawCircle(w*.73f,h*.24f,dp(76),p);
        sceneSandia(c,false);
        p.setColor(0xFF1C2A28);
        for(int i=0;i<9;i++){float x=w*(.06f+i*.115f); float y=h*.61f+(i%2)*dp(12); c.drawRect(x,y-dp(22),x+dp(4),y,p); c.drawOval(new RectF(x-dp(13),y-dp(31),x+dp(17),y-dp(13)),p);}
    }

    private void drawHeader(Canvas c) {
        float y=dp(30);
        drawShield(c,dp(31),y+dp(21),dp(18));
        text(c,"NetWatch Phone",dp(60),y+dp(22),sp(26),Color.WHITE,true,Paint.Align.LEFT);
        text(c,"Private. Protected. In your control.",dp(60),y+dp(43),sp(12),0xFFBED4E9,false,Paint.Align.LEFT);
        RectF more=new RectF(w-dp(57),y,w-dp(17),y+dp(40));
        glass(c,more,dp(20),0x35FFFFFF,0x76FFFFFF);
        p.setColor(Color.WHITE);
        c.drawCircle(more.centerX()-dp(7),more.centerY(),dp(1.5f),p); c.drawCircle(more.centerX(),more.centerY(),dp(1.5f),p); c.drawCircle(more.centerX()+dp(7),more.centerY(),dp(1.5f),p);
    }

    private void drawWeatherStrip(Canvas c) {
        RectF r=new RectF(dp(18),dp(84),w-dp(18),dp(134));
        glass(c,r,dp(20),0x26FFFFFF,0x55FFFFFF);
        text(c,weather.icon(),dp(36),dp(112),sp(20),Color.WHITE,false,Paint.Align.LEFT);
        text(c,"Albuquerque  "+weather.primaryLine(),dp(65),dp(105),sp(12.5f),Color.WHITE,true,Paint.Align.LEFT);
        text(c,weather.detailLine(),dp(65),dp(124),sp(10.5f),0xFFD2E2F0,false,Paint.Align.LEFT);
    }

    private void drawRecents(Canvas c) {
        float y=dp(146);
        RectF search=new RectF(dp(18),y,w-dp(18),y+dp(48)); glass(c,search,dp(20),0x27FFFFFF,0x60FFFFFF);
        searchIcon(c,dp(38),y+dp(24)); text(c,"Search all contacts",dp(58),y+dp(29),sp(12.5f),0xFFE2ECF5,false,Paint.Align.LEFT);
        y+=dp(59);
        float gap=dp(7), chipW=(w-dp(36)-gap*2)/3f;
        chip(c,"All",dp(18),y,chipW,true); chip(c,"Missed",dp(18)+chipW+gap,y,chipW,false); chip(c,"Voicemail",dp(18)+(chipW+gap)*2,y,chipW,false);
        y+=dp(54);
        text(c,"Recent calls",dp(20),y,sp(15),Color.WHITE,true,Paint.Align.LEFT);
        text(c,(recents.isEmpty()?"0":(listOffset+1)+"–"+Math.min(recents.size(),listOffset+7)+" of "+recents.size()),w-dp(20),y,sp(10.5f),0xFFB7C9DB,false,Paint.Align.RIGHT);
        y+=dp(13);
        if(recents.isEmpty()){emptyCard(c,"Your recent calls","Make NetWatch your default phone app to show call history.",y+dp(80));return;}
        float rowH=dp(68); int visible=0;
        for(int i=listOffset;i<recents.size() && visible<7 && y+rowH<navTop-dp(4);i++,visible++){
            RecentCall item=recents.get(i); RectF row=new RectF(dp(15),y,w-dp(15),y+rowH-dp(5)); glass(c,row,dp(21),0x22FFFFFF,0x3FFFFFFF);
            float cy=row.centerY(); avatar(c,dp(47),cy,dp(21),display(item),item.missed);
            text(c,display(item),dp(79),cy-dp(3),sp(14.5f),Color.WHITE,false,Paint.Align.LEFT);
            text(c,item.detail,dp(79),cy+dp(18),sp(11),item.missed?0xFFFF7786:0xFFC8D7E6,false,Paint.Align.LEFT);
            phoneButton(c,w-dp(43),cy,dp(18),0x25FFFFFF); y+=rowH;
        }
    }

    private void drawContacts(Canvas c) {
        float y=dp(146);
        RectF search=new RectF(dp(18),y,w-dp(18),y+dp(48)); glass(c,search,dp(20),0x27FFFFFF,0x60FFFFFF);
        searchIcon(c,dp(38),y+dp(24)); text(c,"Search all contacts",dp(58),y+dp(29),sp(12.5f),0xFFE2ECF5,false,Paint.Align.LEFT);
        y+=dp(68);
        text(c,"Contacts",dp(20),y,sp(25),Color.WHITE,true,Paint.Align.LEFT);
        text(c,contacts.isEmpty()?"0":(listOffset+1)+"–"+Math.min(contacts.size(),listOffset+8)+" of "+contacts.size(),w-dp(20),y,sp(10.5f),0xFFB7C9DB,false,Paint.Align.RIGHT);
        y+=dp(18);
        if(contacts.isEmpty()){emptyCard(c,"No contacts yet","Allow Contacts permission from the menu.",y+dp(80));return;}
        int visible=0; float rowH=dp(66);
        for(int i=listOffset;i<contacts.size() && visible<8 && y+rowH<navTop;i++,visible++){
            ContactItem it=contacts.get(i); float cy=y+dp(30); avatar(c,dp(47),cy,dp(21),it.name,false);
            text(c,it.name,dp(79),cy-dp(3),sp(14.5f),Color.WHITE,false,Paint.Align.LEFT);
            text(c,it.number,dp(79),cy+dp(17),sp(11),0xFFC8D7E6,false,Paint.Align.LEFT);
            phoneButton(c,w-dp(43),cy,dp(18),0x20FFFFFF);
            line.setColor(0x25FFFFFF);line.setStrokeWidth(dp(1));c.drawLine(dp(79),y+dp(61),w-dp(18),y+dp(61),line);
            y+=rowH;
        }
    }

    private void drawFavorites(Canvas c) {
        float y=dp(154);
        text(c,"Favorites",dp(20),y,sp(27),Color.WHITE,true,Paint.Align.LEFT);
        text(c,"People you call most",dp(20),y+dp(24),sp(12),0xFFBED4E9,false,Paint.Align.LEFT);
        y+=dp(50);
        if(contacts.isEmpty()){emptyCard(c,"No favorites yet","Your contacts will appear here.",y+dp(75));return;}
        int visible=0;
        for(int i=listOffset;i<contacts.size() && visible<6 && y+dp(72)<navTop;i++,visible++){
            ContactItem it=contacts.get(i); RectF r=new RectF(dp(18),y,w-dp(18),y+dp(68)); glass(c,r,dp(23),0x24FFFFFF,0x4DFFFFFF);
            avatar(c,dp(49),r.centerY(),dp(22),it.name,false);
            text(c,it.name,dp(83),r.centerY()-dp(3),sp(15),Color.WHITE,false,Paint.Align.LEFT);
            text(c,it.number,dp(83),r.centerY()+dp(18),sp(11),0xFFC8D7E6,false,Paint.Align.LEFT);
            phoneButton(c,w-dp(46),r.centerY(),dp(19),0x25FFFFFF); y+=dp(77);
        }
    }

    private void drawKeypad(Canvas c) {
        float top=dp(150);
        RectF display=new RectF(dp(27),top,w-dp(27),top+dp(70)); glass(c,display,dp(27),0x32FFFFFF,0x80FFFFFF);
        String shown=digits.length()==0?"Enter a number":digits.toString();
        text(c,shown,display.centerX(),display.centerY()+dp(8),digits.length()==0?sp(16):sp(26),digits.length()==0?0xFFB9CBE0:Color.WHITE,false,Paint.Align.CENTER);
        if(digits.length()>0){RectF back=new RectF(w-dp(70),top+dp(19),w-dp(38),top+dp(51));glass(c,back,dp(14),0x22FFFFFF,0x45FFFFFF);text(c,"×",back.centerX(),back.centerY()+dp(7),sp(20),Color.WHITE,true,Paint.Align.CENTER);}
        float cx=w/2f,colGap=Math.min(dp(112),w/3.25f),startX=cx-colGap,startY=top+dp(112),rowGap=Math.min(dp(92),(navTop-startY-dp(78))/4.1f),radius=Math.min(dp(37),colGap*.34f);
        for(int i=0;i<12;i++){int col=i%3,row=i/3;key(c,startX+col*colGap,startY+row*rowGap,radius,KEYS[i][0],KEYS[i][1]);}
        float callY=startY+rowGap*4f-dp(2);
        p.setShader(new LinearGradient(cx-dp(35),callY-dp(35),cx+dp(35),callY+dp(35),0xFF70EA6A,0xFF12A13D,Shader.TileMode.CLAMP)); c.drawCircle(cx,callY,dp(37),p); p.setShader(null);
        line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(dp(1.5f));line.setColor(0xB8FFFFFF);c.drawCircle(cx,callY,dp(37),line);line.setStyle(Paint.Style.FILL);phoneGlyph(c,cx,callY,Color.WHITE,1.2f);
    }

    private void drawNav(Canvas c) {
        RectF nav=new RectF(dp(11),navTop,w-dp(11),h-dp(10)); glass(c,nav,dp(27),0x56101A29,0x70FFFFFF);
        String[] labels={"Favorites","Recents","Contacts","Keypad"}; Page[] pages={Page.FAVORITES,Page.RECENTS,Page.CONTACTS,Page.KEYPAD}; float slot=nav.width()/4f;
        for(int i=0;i<4;i++){float cx=nav.left+slot*(i+.5f);boolean selected=page==pages[i];if(selected){RectF sel=new RectF(cx-slot*.39f,nav.top+dp(7),cx+slot*.39f,nav.bottom-dp(7));glass(c,sel,dp(22),0x705294EE,0xC5D7EAFF);}navIcon(c,i,cx,nav.top+dp(27),selected?Color.WHITE:0xFFD1DBE5);text(c,labels[i],cx,nav.top+dp(63),sp(10),selected?Color.WHITE:0xFFD1DBE5,false,Paint.Align.CENTER);}
    }

    private void drawSafeFallback(Canvas c,Throwable e){c.drawColor(Color.rgb(8,20,34));text(c,"NetWatch Phone",dp(24),dp(52),sp(28),Color.WHITE,true,Paint.Align.LEFT);text(c,"Glass safe mode",dp(24),dp(82),sp(16),0xFF9ED4FF,true,Paint.Align.LEFT);text(c,e.getClass().getSimpleName(),dp(24),dp(125),sp(12),0xFFFFA4A4,false,Paint.Align.LEFT);}

    private void glass(Canvas c,RectF r,float radius,int fill,int border){p.setShader(new LinearGradient(r.left,r.top,r.right,r.bottom,brighten(fill,1.25f),darken(fill,.78f),Shader.TileMode.CLAMP));c.drawRoundRect(r,radius,radius,p);p.setShader(null);line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(dp(1));line.setColor(border);c.drawRoundRect(r,radius,radius,line);line.setStyle(Paint.Style.FILL);}
    private void chip(Canvas c,String label,float x,float y,float cw,boolean selected){RectF r=new RectF(x,y,x+cw,y+dp(38));glass(c,r,dp(17),selected?0x70599FF2:0x1EFFFFFF,selected?0xCCDDF0FF:0x3AFFFFFF);text(c,label,r.centerX(),r.centerY()+dp(5),sp(12),Color.WHITE,false,Paint.Align.CENTER);}
    private void key(Canvas c,float x,float y,float radius,String main,String sub){RectF r=new RectF(x-radius,y-radius,x+radius,y+radius);glass(c,r,radius,0x31FFFFFF,0x73FFFFFF);text(c,main,x,y+(sub.isEmpty()?dp(9):dp(2)),sp(29),Color.WHITE,false,Paint.Align.CENTER);if(!sub.isEmpty())text(c,sub,x,y+dp(22),sp(10.5f),0xFFE7EEF6,false,Paint.Align.CENTER);}
    private void avatar(Canvas c,float x,float y,float radius,String label,boolean missed){int seed=label==null?0:label.hashCode();int red=missed?190:65+Math.abs(seed%55),green=missed?70:95+Math.abs((seed/7)%65),blue=missed?92:130+Math.abs((seed/13)%70);p.setColor(Color.rgb(clamp(red),clamp(green),clamp(blue)));c.drawCircle(x,y,radius,p);text(c,initials(label),x,y+dp(5),sp(13),Color.WHITE,true,Paint.Align.CENTER);}
    private void phoneButton(Canvas c,float x,float y,float radius,int bg){p.setColor(bg);c.drawCircle(x,y,radius,p);phoneGlyph(c,x,y,Color.WHITE,.68f);}
    private void phoneGlyph(Canvas c,float x,float y,int color,float scale){line.setStyle(Paint.Style.STROKE);line.setStrokeCap(Paint.Cap.ROUND);line.setStrokeWidth(dp(4.5f)*scale);line.setColor(color);RectF arc=new RectF(x-dp(11)*scale,y-dp(11)*scale,x+dp(11)*scale,y+dp(11)*scale);c.drawArc(arc,133,93,false,line);line.setStrokeCap(Paint.Cap.BUTT);line.setStyle(Paint.Style.FILL);}
    private void drawShield(Canvas c,float x,float y,float radius){Path s=new Path();s.moveTo(x,y-radius);s.lineTo(x+radius*.78f,y-radius*.60f);s.lineTo(x+radius*.66f,y+radius*.42f);s.quadTo(x,y+radius*1.10f,x-radius*.66f,y+radius*.42f);s.lineTo(x-radius*.78f,y-radius*.60f);s.close();p.setShader(new LinearGradient(x-radius,y-radius,x+radius,y+radius,0xFF83E3FF,0xFF1F78D0,Shader.TileMode.CLAMP));c.drawPath(s,p);p.setShader(null);line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(dp(1.2f));line.setColor(0xD8E9FAFF);c.drawPath(s,line);line.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);c.drawCircle(x,y,dp(3),p);}
    private void searchIcon(Canvas c,float x,float y){line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(dp(2));line.setColor(0xFFE6EDF5);c.drawCircle(x,y-dp(2),dp(7),line);c.drawLine(x+dp(5),y+dp(3),x+dp(11),y+dp(9),line);line.setStyle(Paint.Style.FILL);}
    private void navIcon(Canvas c,int index,float x,float y,int color){p.setColor(color);line.setColor(color);line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(dp(2));if(index==0){Path star=new Path();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=(i%2==0)?dp(10):dp(4.5f);float px=x+(float)Math.cos(a)*r,py=y+(float)Math.sin(a)*r;if(i==0)star.moveTo(px,py);else star.lineTo(px,py);}star.close();c.drawPath(star,p);}else if(index==1){c.drawCircle(x,y,dp(9),line);c.drawLine(x,y,x,y-dp(5),line);c.drawLine(x,y,x+dp(4),y+dp(2),line);}else if(index==2){c.drawCircle(x,y-dp(5),dp(4.5f),p);c.drawRoundRect(new RectF(x-dp(8),y+dp(2),x+dp(8),y+dp(10)),dp(5),dp(5),p);}else{for(int r=-1;r<=1;r++)for(int col=-1;col<=1;col++)c.drawCircle(x+col*dp(7),y+r*dp(7),dp(2.8f),p);}line.setStyle(Paint.Style.FILL);}
    private void emptyCard(Canvas c,String title,String subtitle,float y){RectF r=new RectF(dp(24),y-dp(50),w-dp(24),y+dp(60));glass(c,r,dp(26),0x1CFFFFFF,0x3CFFFFFF);text(c,title,w/2f,y,sp(17),Color.WHITE,true,Paint.Align.CENTER);text(c,subtitle,w/2f,y+dp(27),sp(11.5f),0xFFBED4E9,false,Paint.Align.CENTER);}
    private void text(Canvas c,String value,float x,float y,float size,int color,boolean bold,Paint.Align align){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));p.setTextSize(size);p.setTextAlign(align);c.drawText(value==null?"":value,x,y,p);}
    private String display(RecentCall r){if(r.name!=null&&!r.name.trim().isEmpty())return r.name;if(r.number!=null&&!r.number.trim().isEmpty())return r.number;return "Unknown Caller";}
    private String initials(String s){if(s==null||s.trim().isEmpty())return "?";String[] a=s.trim().split("\\s+");String out=a[0].substring(0,1).toUpperCase();if(a.length>1)out+=a[a.length-1].substring(0,1).toUpperCase();return out;}
    private static int brighten(int color,float factor){return Color.argb(Math.min(255,(int)(Color.alpha(color)*factor)),Color.red(color),Color.green(color),Color.blue(color));}
    private static int darken(int color,float factor){return Color.argb(Math.max(0,(int)(Color.alpha(color)*factor)),Color.red(color),Color.green(color),Color.blue(color));}
    private static int clamp(int n){return Math.max(0,Math.min(255,n));}
    private int clampOffset(int value,int size,int visible){return Math.max(0,Math.min(value,Math.max(0,size-visible)));}

    @Override public boolean onTouchEvent(MotionEvent e) {
        if(e.getAction()==MotionEvent.ACTION_DOWN){downY=e.getY();return true;}
        if(e.getAction()!=MotionEvent.ACTION_UP)return true;
        float x=e.getX(),y=e.getY(),dy=y-downY;
        if(lastDrawError!=null){if(callback!=null)callback.openSettings();return true;}

        if((page==Page.CONTACTS||page==Page.RECENTS||page==Page.FAVORITES)&&Math.abs(dy)>dp(42)){
            int visible=page==Page.FAVORITES?6:(page==Page.RECENTS?7:8);
            int size=page==Page.RECENTS?recents.size():contacts.size();
            listOffset=clampOffset(listOffset+(dy<0?visible:-visible),size,visible);invalidate();return true;
        }

        if(y>=navTop){float left=dp(11),slot=(w-dp(22))/4f;int i=(int)((x-left)/slot);if(i>=0&&i<4){page=Page.values()[i];listOffset=0;invalidate();}return true;}
        if(x>w-dp(75)&&y<dp(90)){if(callback!=null)callback.openSettings();return true;}
        if(y>=dp(82)&&y<=dp(138)){if(callback!=null)callback.openWeatherDetails();return true;}
        if((page==Page.RECENTS||page==Page.CONTACTS)&&y>=dp(140)&&y<=dp(205)){if(callback!=null)callback.openSearch();return true;}
        if(page==Page.KEYPAD)return keypadTouch(x,y);
        if(page==Page.RECENTS)return recentTouch(x,y);
        if(page==Page.CONTACTS)return contactTouch(x,y,false);
        if(page==Page.FAVORITES)return contactTouch(x,y,true);
        return true;
    }

    private boolean keypadTouch(float x,float y){float top=dp(150);if(digits.length()>0&&x>w-dp(82)&&y>top&&y<top+dp(75)){digits.deleteCharAt(digits.length()-1);invalidate();return true;}float cx=w/2f,colGap=Math.min(dp(112),w/3.25f),startX=cx-colGap,startY=top+dp(112),rowGap=Math.min(dp(92),(navTop-startY-dp(78))/4.1f),hit=Math.min(dp(44),colGap*.40f);for(int i=0;i<12;i++){int col=i%3,row=i/3;float kx=startX+col*colGap,ky=startY+row*rowGap;if(distance(x,y,kx,ky)<=hit){digits.append(KEYS[i][0]);invalidate();return true;}}float callY=startY+rowGap*4f-dp(2);if(distance(x,y,cx,callY)<=dp(48)&&digits.length()>0&&callback!=null)callback.placeCall(digits.toString());return true;}
    private boolean recentTouch(float x,float y){if(x<w-dp(85))return true;float start=dp(272);int i=listOffset+(int)((y-start)/dp(68));if(i>=0&&i<recents.size()&&callback!=null)callback.placeCall(recents.get(i).number);return true;}
    private boolean contactTouch(float x,float y,boolean favorite){if(x<w-dp(85))return true;float start=favorite?dp(204):dp(232);float row=favorite?dp(77):dp(66);int i=listOffset+(int)((y-start)/row);if(i>=0&&i<contacts.size()&&callback!=null)callback.placeCall(contacts.get(i).number);return true;}
    private static float distance(float x1,float y1,float x2,float y2){float dx=x1-x2,dy=y1-y2;return(float)Math.sqrt(dx*dx+dy*dy);}
    private float dp(float v){return v*d;} private float sp(float v){return v*sd;}
}
