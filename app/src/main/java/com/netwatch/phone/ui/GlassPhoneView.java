package com.netwatch.phone.ui;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import com.netwatch.phone.R;
import com.netwatch.phone.weather.WeatherSnapshot;
import java.util.ArrayList;
import java.util.List;

/**
 * Main NetWatch Phone surface.
 * Uses only Android platform drawing APIs. 0.6 adds the real NetWatch Albuquerque
 * background, inertial scrolling, focus bubbles and a calm gold selection halo.
 */
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
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Callback callback;
    private final List<RecentCall> recents = new ArrayList<>();
    private final List<ContactItem> contacts = new ArrayList<>();
    private final StringBuilder digits = new StringBuilder();
    private final Bitmap background;
    private final Bitmap logo;

    private WeatherSnapshot weather = WeatherSnapshot.loading();
    private Page page = Page.RECENTS;

    private float d, sd, w, h, navTop;
    private float listTop;
    private float scrollPx;
    private float maxScroll;
    private float downY, downX, lastY, lastTouchY;
    private long lastMoveMs;
    private float flingVelocity;
    private boolean dragging;
    private boolean moved;
    private int selectedIndex = -1;
    private int hoverIndex = -1;
    private long pageChangedAt;

    private static final float ROW_DP = 76f;
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
        background = BitmapFactory.decodeResource(getResources(), R.drawable.mountains_sunset);
        logo = BitmapFactory.decodeResource(getResources(), R.drawable.netwatch_phone_icon);
        setClickable(true);
        setFocusable(true);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("NetWatch Phone");
    }

    public void setRecentCalls(List<RecentCall> items) {
        recents.clear();
        if (items != null) recents.addAll(items);
        clampScroll();
        invalidate();
    }

    public void setContacts(List<ContactItem> items) {
        contacts.clear();
        if (items != null) contacts.addAll(items);
        clampScroll();
        invalidate();
    }

    public void setWeather(WeatherSnapshot snapshot) {
        if (snapshot != null) weather = snapshot;
        invalidate();
    }

    public void showKeypad(String preset) {
        changePage(Page.KEYPAD);
        digits.setLength(0);
        if (preset != null) digits.append(preset);
        invalidate();
    }

    @Override protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        w = width;
        h = height;
        navTop = Math.max(dp(560), h - dp(92));
        recalcMaxScroll();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        drawBackground(c);
        drawHeader(c);
        drawWeather(c);

        if (page == Page.RECENTS) drawRecents(c);
        else if (page == Page.CONTACTS) drawContacts(c);
        else if (page == Page.FAVORITES) drawFavorites(c);
        else drawKeypad(c);

        drawNav(c);
        animateIfNeeded();
    }

    private void drawBackground(Canvas c) {
        p.setShader(new LinearGradient(0, 0, 0, Math.max(1, h), 0xFF173D62, 0xFF06111F, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        if (background != null && w > 1) {
            float photoH = w * background.getHeight() / (float) background.getWidth();
            float parallax = Math.max(-dp(12), Math.min(dp(12), -scrollPx * .025f));
            Rect src = new Rect(0, 0, background.getWidth(), background.getHeight());
            RectF dst = new RectF(-dp(8), parallax, w + dp(8), photoH + parallax);
            c.drawBitmap(background, src, dst, p);

            p.setShader(new LinearGradient(0, photoH * .22f, 0, Math.max(photoH + dp(360), h),
                    new int[]{0x1805101E, 0x9A071421, 0xF706111F},
                    new float[]{0f, .47f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
        }

        int day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR);
        int[] tints = {0x102C7EC8,0x1059A6CC,0x10C67A4B,0x107B64C6,0x10D39747,0x104CD2C1};
        p.setColor(tints[Math.floorMod(day, tints.length)]);
        c.drawRect(0,0,w,h,p);
    }

    private void drawHeader(Canvas c) {
        float top = dp(28);
        if (logo != null) {
            Rect src = new Rect(0,0,logo.getWidth(),logo.getHeight());
            RectF dst = new RectF(dp(17),top-dp(2),dp(61),top+dp(42));
            p.setShadowLayer(dp(10),0,0,0x7044B7FF);
            c.drawBitmap(logo,src,dst,p);
            p.clearShadowLayer();
        } else {
            drawShield(c,dp(39),top+dp(20),dp(19));
        }

        text(c,"NetWatch Phone",dp(70),top+dp(19),sp(26),Color.WHITE,true,Paint.Align.LEFT);
        text(c,"Private. Protected. In your control.",dp(70),top+dp(41),sp(12),0xFFD1DFED,false,Paint.Align.LEFT);

        RectF more = new RectF(w-dp(59),top-dp(2),w-dp(17),top+dp(40));
        glass(c,more,dp(21),0x2EFFFFFF,0x70FFFFFF);
        p.setColor(Color.WHITE);
        c.drawCircle(more.centerX()-dp(7),more.centerY(),dp(1.5f),p);
        c.drawCircle(more.centerX(),more.centerY(),dp(1.5f),p);
        c.drawCircle(more.centerX()+dp(7),more.centerY(),dp(1.5f),p);
    }

    private void drawWeather(Canvas c) {
        RectF r = new RectF(dp(18),dp(82),w-dp(18),dp(134));
        glass(c,r,dp(21),0x2DFFFFFF,0x5FFFFFFF);
        text(c,weather.icon(),dp(37),dp(113),sp(21),Color.WHITE,false,Paint.Align.LEFT);
        text(c,"Albuquerque  "+weather.primaryLine(),dp(68),dp(104),sp(12.5f),Color.WHITE,true,Paint.Align.LEFT);
        text(c,weather.detailLine(),dp(68),dp(124),sp(10.2f),0xFFD4E1EE,false,Paint.Align.LEFT);
    }

    private void drawRecents(Canvas c) {
        listTop = dp(233);
        drawSearch(c,"Search contacts, numbers, or places");
        text(c,"Recents",dp(20),dp(221),sp(27),Color.WHITE,true,Paint.Align.LEFT);
        drawList(c,true);
    }

    private void drawContacts(Canvas c) {
        listTop = dp(233);
        drawSearch(c,"Search all contacts");
        text(c,"Contacts",dp(20),dp(221),sp(27),Color.WHITE,true,Paint.Align.LEFT);
        drawList(c,false);
    }

    private void drawFavorites(Canvas c) {
        listTop = dp(208);
        text(c,"Favorites",dp(20),dp(177),sp(29),Color.WHITE,true,Paint.Align.LEFT);
        text(c,"People you call most",dp(20),dp(199),sp(12.5f),0xFFD0DFEC,false,Paint.Align.LEFT);
        drawList(c,false);
    }

    private void drawSearch(Canvas c, String hint) {
        RectF r = new RectF(dp(17),dp(148),w-dp(17),dp(202));
        glass(c,r,dp(27),0x2AFFFFFF,0x58FFFFFF);
        drawSearchIcon(c,dp(42),dp(175));
        text(c,hint,dp(66),dp(181),sp(13.5f),0xFFE4EDF5,false,Paint.Align.LEFT);
    }

    private void drawList(Canvas c, boolean recentMode) {
        List<?> items = recentMode ? recents : contacts;
        float rowH = dp(ROW_DP);
        float usable = Math.max(rowH, navTop - listTop - dp(8));
        recalcMaxScroll();

        if (items.isEmpty()) {
            RectF empty = new RectF(dp(24),listTop+dp(30),w-dp(24),listTop+dp(145));
            glass(c,empty,dp(28),0x22FFFFFF,0x44FFFFFF);
            text(c,recentMode?"No recent calls yet":"No contacts available",w/2,empty.centerY()-dp(4),sp(17),Color.WHITE,true,Paint.Align.CENTER);
            text(c,recentMode?"Your calls will appear here.":"Grant Contacts permission or use Search.",w/2,empty.centerY()+dp(24),sp(11.5f),0xFFC8D8E8,false,Paint.Align.CENTER);
            return;
        }

        int first = Math.max(0,(int)Math.floor(scrollPx/rowH));
        float rem = scrollPx - first*rowH;
        int count = Math.min(items.size()-first,(int)Math.ceil(usable/rowH)+2);

        float focusY = dragging ? lastTouchY : listTop + usable*.46f;
        long now = SystemClock.uptimeMillis();
        float pulse = .5f + .5f*(float)Math.sin(now/780.0);

        for (int j=0;j<count;j++) {
            int index=first+j;
            float top=listTop+j*rowH-rem;
            float cy=top+rowH*.46f;
            if (cy<listTop-dp(70)||cy>navTop+dp(70)) continue;

            float dist=Math.abs(cy-focusY);
            float proximity=(float)Math.exp(-(dist*dist)/(2f*rowH*rowH*.72f));
            boolean selected=index==selectedIndex;
            boolean hover=index==hoverIndex;
            float focus=Math.max(proximity,(selected||hover)?1f:0f);
            float scale=1f+.055f*focus;

            c.save();
            c.scale(scale,scale,w/2,cy);

            RectF row=new RectF(dp(15),top+dp(3),w-dp(15),top+rowH-dp(5));
            if (focus>.52f) {
                int alpha=(int)(50+70*pulse*focus);
                p.setShadowLayer(dp(12)+dp(6)*focus,0,0,Color.argb(alpha,255,190,74));
                glass(c,row,dp(25),0x33FFFFFF,Color.argb(150,255,201,102));
                p.clearShadowLayer();

                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(dp(1.7f));
                stroke.setColor(Color.argb((int)(120+80*pulse),255,205,112));
                RectF halo=new RectF(row.left-dp(2),row.top-dp(2),row.right+dp(2),row.bottom+dp(2));
                c.drawRoundRect(halo,dp(27),dp(27),stroke);
                stroke.setStyle(Paint.Style.FILL);
            } else {
                glass(c,row,dp(24),0x1CFFFFFF,0x30FFFFFF);
            }

            String name, number, detail;
            boolean missed=false;
            if (recentMode) {
                RecentCall item=recents.get(index);
                name=item.name.trim().isEmpty()?(item.number.trim().isEmpty()?"Unknown Caller":item.number):item.name;
                number=item.number; detail=item.detail; missed=item.missed;
            } else {
                ContactItem item=contacts.get(index);
                name=item.name; number=item.number; detail=item.number;
            }

            drawAvatar(c,dp(49),cy,dp(22),name,missed,focus,pulse);
            text(c,name,dp(83),cy-dp(5),sp(15.5f),Color.WHITE,true,Paint.Align.LEFT);
            text(c,detail,dp(83),cy+dp(17),sp(11.2f),missed?0xFFFF8792:0xFFD2DFEB,false,Paint.Align.LEFT);
            drawPhoneCircle(c,w-dp(46),cy,dp(19),focus,pulse);

            c.restore();
        }

        if (items.size()>1) {
            int shownStart=Math.min(items.size(),first+1);
            int shownEnd=Math.min(items.size(),first+Math.max(1,(int)Math.floor(usable/rowH)));
            text(c,shownStart+"–"+shownEnd+" of "+items.size(),w-dp(18),listTop-dp(8),sp(10.5f),0xFFB8C8D8,false,Paint.Align.RIGHT);
        }
    }

    private void drawAvatar(Canvas c,float x,float y,float r,String label,boolean alert,float focus,float pulse) {
        int seed=label==null?0:label.hashCode();
        int base=alert?0xFFC35567:Color.rgb(60+Math.abs(seed%55),84+Math.abs((seed/7)%72),118+Math.abs((seed/13)%70));
        if(focus>.52f) {
            p.setColor(Color.argb((int)(90+80*pulse),255,198,78));
            c.drawCircle(x,y,r+dp(4),p);
        }
        p.setColor(base); c.drawCircle(x,y,r,p);
        stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(dp(1)); stroke.setColor(0x88FFFFFF); c.drawCircle(x,y,r,stroke); stroke.setStyle(Paint.Style.FILL);
        text(c,initials(label),x,y+dp(5.5f),sp(13),Color.WHITE,true,Paint.Align.CENTER);
    }

    private void drawPhoneCircle(Canvas c,float x,float y,float r,float focus,float pulse) {
        if(focus>.5f) {
            p.setShadowLayer(dp(10),0,0,Color.argb((int)(70+80*pulse),255,190,65));
            p.setColor(0x665889B6); c.drawCircle(x,y,r+dp(1),p); p.clearShadowLayer();
        } else { p.setColor(0x285A84A7); c.drawCircle(x,y,r,p); }
        drawPhoneGlyph(c,x,y,Color.WHITE,.72f,false);
    }

    private void drawKeypad(Canvas c) {
        RectF numberBox=new RectF(dp(28),dp(155),w-dp(28),dp(225));
        glass(c,numberBox,dp(30),0x35FFFFFF,0x78FFFFFF);
        String shown=digits.length()==0?"Enter a number":digits.toString();
        text(c,shown,numberBox.centerX(),numberBox.centerY()+dp(8),digits.length()==0?sp(17):sp(27),digits.length()==0?0xFFD1D9E2:Color.WHITE,false,Paint.Align.CENTER);

        float cx=w/2f, col=dp(111), startX=cx-col, startY=dp(286), row=dp(96), radius=dp(37);
        long now=SystemClock.uptimeMillis();
        float pulse=.5f+.5f*(float)Math.sin(now/900.0);
        for(int i=0;i<KEYS.length;i++){
            float x=startX+(i%3)*col, y=startY+(i/3)*row;
            boolean hot=(hoverIndex==100+i);
            if(hot){p.setShadowLayer(dp(14),0,0,Color.argb((int)(90+70*pulse),255,194,72));}
            glass(c,new RectF(x-radius,y-radius,x+radius,y+radius),radius,hot?0x43FFFFFF:0x31FFFFFF,hot?0xB6FFD48C:0x72FFFFFF);
            p.clearShadowLayer();
            text(c,KEYS[i][0],x,y+(KEYS[i][1].isEmpty()?dp(10):dp(3)),sp(30),Color.WHITE,false,Paint.Align.CENTER);
            if(!KEYS[i][1].isEmpty())text(c,KEYS[i][1],x,y+dp(23),sp(10.5f),0xFFE4ECF4,false,Paint.Align.CENTER);
        }

        float callY=startY+row*4-dp(4);
        p.setShadowLayer(dp(18),0,dp(4),0x882CFF6A);
        p.setShader(new LinearGradient(cx-dp(40),callY-dp(40),cx+dp(40),callY+dp(40),0xFF69EA67,0xFF18AE46,Shader.TileMode.CLAMP));
        c.drawCircle(cx,callY,dp(38),p); p.setShader(null); p.clearShadowLayer();
        drawPhoneGlyph(c,cx,callY,Color.WHITE,1.18f,false);
    }

    private void drawNav(Canvas c) {
        RectF nav=new RectF(dp(11),navTop,w-dp(11),h-dp(9));
        glass(c,nav,dp(30),0x52051020,0x72FFFFFF);
        String[] labels={"Favorites","Recents","Contacts","Keypad"};
        Page[] pages={Page.FAVORITES,Page.RECENTS,Page.CONTACTS,Page.KEYPAD};
        float slot=nav.width()/4f;
        float transition=Math.min(1f,(SystemClock.uptimeMillis()-pageChangedAt)/280f);
        for(int i=0;i<4;i++){
            float x=nav.left+slot*(i+.5f);
            boolean selected=page==pages[i];
            if(selected){
                float grow=.92f+.08f*easeOut(transition);
                RectF pill=new RectF(x-slot*.40f*grow,nav.top+dp(7),x+slot*.40f*grow,nav.bottom-dp(7));
                p.setShadowLayer(dp(12),0,0,0x6640A1FF);
                glass(c,pill,dp(25),0x62508FD9,0xBBD8ECFF); p.clearShadowLayer();
            }
            drawNavIcon(c,i,x,nav.top+dp(28),selected?Color.WHITE:0xFFD1D9E2);
            text(c,labels[i],x,nav.top+dp(67),sp(10.4f),selected?Color.WHITE:0xFFD1D9E2,false,Paint.Align.CENTER);
        }
    }

    private void changePage(Page next) {
        if(page==next)return;
        page=next;
        scrollPx=0; flingVelocity=0; selectedIndex=-1; hoverIndex=-1;
        pageChangedAt=SystemClock.uptimeMillis();
        recalcMaxScroll();
        invalidate();
    }

    private void recalcMaxScroll() {
        int size=(page==Page.RECENTS)?recents.size():(page==Page.CONTACTS||page==Page.FAVORITES)?contacts.size():0;
        if(page==Page.KEYPAD){maxScroll=0;scrollPx=0;return;}
        float visible=Math.max(dp(ROW_DP),navTop-listTop-dp(8));
        maxScroll=Math.max(0,size*dp(ROW_DP)-visible);
        clampScroll();
    }

    private void clampScroll() {
        if(scrollPx<0)scrollPx=0;
        if(scrollPx>maxScroll)scrollPx=maxScroll;
    }

    private void animateIfNeeded() {
        if(!dragging && Math.abs(flingVelocity)>.12f && (page==Page.RECENTS||page==Page.CONTACTS||page==Page.FAVORITES)) {
            scrollPx+=flingVelocity;
            flingVelocity*=.91f;
            if(scrollPx<0){scrollPx=0;flingVelocity=0;}
            if(scrollPx>maxScroll){scrollPx=maxScroll;flingVelocity=0;}
            postInvalidateOnAnimation();
        } else if(hoverIndex>=0 || selectedIndex>=0 || page==Page.KEYPAD) {
            postInvalidateDelayed(48);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x=e.getX(), y=e.getY();
        if(e.getAction()==MotionEvent.ACTION_DOWN){
            downX=x; downY=y; lastY=y; lastTouchY=y; lastMoveMs=SystemClock.uptimeMillis();
            dragging=false; moved=false; flingVelocity=0;
            hoverIndex=hitVisualIndex(x,y);
            invalidate();
            return true;
        }
        if(e.getAction()==MotionEvent.ACTION_MOVE){
            float dy=y-lastY;
            if(Math.abs(y-downY)>dp(7)||Math.abs(x-downX)>dp(7)){dragging=true;moved=true;}
            if(dragging && isListPage()){
                scrollPx-=dy;
                if(scrollPx<0)scrollPx*=.35f;
                if(scrollPx>maxScroll)scrollPx=maxScroll+(scrollPx-maxScroll)*.35f;
                long now=SystemClock.uptimeMillis();
                long dt=Math.max(1,now-lastMoveMs);
                flingVelocity=(-dy)*(16f/dt);
                lastMoveMs=now;
            }
            lastY=y; lastTouchY=y; hoverIndex=hitVisualIndex(x,y);
            postInvalidateOnAnimation();
            return true;
        }
        if(e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL){
            if(isListPage()){ if(scrollPx<0)scrollPx=0;if(scrollPx>maxScroll)scrollPx=maxScroll; }
            if(!moved && e.getAction()==MotionEvent.ACTION_UP) handleTap(x,y);
            else if(isListPage()) postInvalidateOnAnimation();
            dragging=false; hoverIndex=-1; invalidate();
            return true;
        }
        return true;
    }

    private boolean isListPage(){return page==Page.RECENTS||page==Page.CONTACTS||page==Page.FAVORITES;}

    private void handleTap(float x,float y){
        if(y>=navTop){
            float slot=(w-dp(22))/4f;
            int i=(int)((x-dp(11))/slot);
            if(i==0)changePage(Page.FAVORITES); else if(i==1)changePage(Page.RECENTS); else if(i==2)changePage(Page.CONTACTS); else if(i==3)changePage(Page.KEYPAD);
            return;
        }
        if(x>w-dp(75)&&y<dp(88)){if(callback!=null)callback.openSettings();return;}
        if(y>=dp(82)&&y<=dp(138)){if(callback!=null)callback.openWeatherDetails();return;}
        if((page==Page.RECENTS||page==Page.CONTACTS)&&y>=dp(145)&&y<=dp(208)){if(callback!=null)callback.openSearch();return;}

        if(page==Page.KEYPAD){handleKeypadTap(x,y);return;}
        int index=hitListIndex(y);
        if(index<0)return;
        selectedIndex=index;
        if(x>w-dp(90)&&callback!=null){
            String number=page==Page.RECENTS?recents.get(index).number:contacts.get(index).number;
            callback.placeCall(number);
        } else {
            invalidate();
        }
    }

    private void handleKeypadTap(float x,float y){
        float cx=w/2f,col=dp(111),startX=cx-col,startY=dp(286),row=dp(96);
        for(int i=0;i<KEYS.length;i++){
            float kx=startX+(i%3)*col, ky=startY+(i/3)*row;
            if(distance(x,y,kx,ky)<=dp(45)){digits.append(KEYS[i][0]);invalidate();return;}
        }
        float callY=startY+row*4-dp(4);
        if(distance(x,y,cx,callY)<=dp(48)&&digits.length()>0&&callback!=null)callback.placeCall(digits.toString());
        if(y>=dp(155)&&y<=dp(225)&&x>w-dp(95)&&digits.length()>0){digits.deleteCharAt(digits.length()-1);invalidate();}
    }

    private int hitVisualIndex(float x,float y){
        if(page==Page.KEYPAD){
            float cx=w/2f,col=dp(111),startX=cx-col,startY=dp(286),row=dp(96);
            for(int i=0;i<KEYS.length;i++){float kx=startX+(i%3)*col,ky=startY+(i/3)*row;if(distance(x,y,kx,ky)<=dp(48))return 100+i;}
            return -1;
        }
        return hitListIndex(y);
    }

    private int hitListIndex(float y){
        if(!isListPage()||y<listTop||y>navTop)return -1;
        int size=page==Page.RECENTS?recents.size():contacts.size();
        int index=(int)Math.floor((y-listTop+scrollPx)/dp(ROW_DP));
        return index>=0&&index<size?index:-1;
    }

    private void glass(Canvas c,RectF r,float radius,int fill,int border){
        p.setShader(new LinearGradient(r.left,r.top,r.right,r.bottom,
                new int[]{adjustAlpha(fill,1.28f),fill,adjustAlpha(fill,.76f)},
                new float[]{0f,.48f,1f},Shader.TileMode.CLAMP));
        c.drawRoundRect(r,radius,radius,p); p.setShader(null);
        stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(dp(1)); stroke.setColor(border); c.drawRoundRect(r,radius,radius,stroke); stroke.setStyle(Paint.Style.FILL);
    }

    private int adjustAlpha(int color,float factor){int a=Math.max(0,Math.min(255,(int)(Color.alpha(color)*factor)));return Color.argb(a,Color.red(color),Color.green(color),Color.blue(color));}

    private void drawShield(Canvas c,float x,float y,float r){
        Path q=new Path();q.moveTo(x,y-r);q.lineTo(x+r*.75f,y-r*.6f);q.lineTo(x+r*.64f,y+r*.4f);q.quadTo(x,y+r*1.08f,x-r*.64f,y+r*.4f);q.lineTo(x-r*.75f,y-r*.6f);q.close();
        p.setShader(new LinearGradient(x-r,y-r,x+r,y+r,0xFF78D9FF,0xFF246DC4,Shader.TileMode.CLAMP));c.drawPath(q,p);p.setShader(null);
    }

    private void drawSearchIcon(Canvas c,float x,float y){
        stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(dp(2.3f));stroke.setColor(0xFFE8EEF5);c.drawCircle(x,y-dp(2),dp(8),stroke);c.drawLine(x+dp(6),y+dp(4),x+dp(13),y+dp(11),stroke);stroke.setStyle(Paint.Style.FILL);
    }

    private void drawPhoneGlyph(Canvas c,float x,float y,int color,float scale,boolean hang){
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(dp(5)*scale);p.setColor(color);
        RectF arc=new RectF(x-dp(12)*scale,y-dp(12)*scale,x+dp(12)*scale,y+dp(12)*scale);
        c.drawArc(arc,hang?205:133,hang?130:93,false,p);p.setStrokeCap(Paint.Cap.BUTT);p.setStyle(Paint.Style.FILL);
    }

    private void drawNavIcon(Canvas c,int index,float x,float y,int color){
        p.setColor(color);stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(dp(2.1f));stroke.setColor(color);
        if(index==0){
            Path star=new Path();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=i%2==0?dp(10):dp(4.5f);float px=x+(float)Math.cos(a)*r,py=y+(float)Math.sin(a)*r;if(i==0)star.moveTo(px,py);else star.lineTo(px,py);}star.close();c.drawPath(star,p);
        }else if(index==1){
            c.drawCircle(x,y,dp(10),stroke);c.drawLine(x,y,x,y-dp(6),stroke);c.drawLine(x,y,x+dp(5),y+dp(2),stroke);
        }else if(index==2){
            c.drawCircle(x,y-dp(5),dp(5),p);c.drawRoundRect(new RectF(x-dp(9),y+dp(2),x+dp(9),y+dp(11)),dp(6),dp(6),p);
        }else{
            for(int rr=-1;rr<=1;rr++)for(int cc=-1;cc<=1;cc++)c.drawCircle(x+cc*dp(8),y+rr*dp(8),dp(3.5f),p);
        }
        stroke.setStyle(Paint.Style.FILL);
    }

    private String initials(String value){
        if(value==null||value.trim().isEmpty())return "?";
        String[] a=value.trim().split("\\s+");String s=a[0].substring(0,1).toUpperCase();
        if(a.length>1)s+=a[a.length-1].substring(0,1).toUpperCase();return s;
    }

    private void text(Canvas c,String value,float x,float y,float size,int color,boolean bold,Paint.Align align){
        p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));p.setTextSize(size);p.setTextAlign(align);c.drawText(value==null?"":value,x,y,p);p.setTextAlign(Paint.Align.LEFT);
    }

    private static float easeOut(float t){float x=Math.max(0,Math.min(1,t));return 1-(1-x)*(1-x)*(1-x);}
    private static float distance(float x1,float y1,float x2,float y2){float dx=x1-x2,dy=y1-y2;return(float)Math.sqrt(dx*dx+dy*dy);}
    private float dp(float v){return v*d;}
    private float sp(float v){return v*sd;}
}
