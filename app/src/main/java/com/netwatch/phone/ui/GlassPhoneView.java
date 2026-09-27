package com.netwatch.phone.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import com.netwatch.phone.R;
import com.netwatch.phone.weather.WeatherSnapshot;
import java.util.ArrayList;
import java.util.List;

/** Main NetWatch Phone surface. Optimized for smooth scrolling on mid-range phones. */
public final class GlassPhoneView extends View {
    public interface Callback {
        void placeCall(String number);
        void openProfile(String name,String number);
        void deleteRecent(long callId,String name,String number);
        void openSettings();
        void openSearch();
        void openWeatherDetails();
    }

    public static final class RecentCall {
        public final long id;
        public final String name,number,detail;
        public final boolean missed;
        public RecentCall(long id,String name,String number,String detail,boolean missed){this.id=id;this.name=name==null?"":name;this.number=number==null?"":number;this.detail=detail==null?"":detail;this.missed=missed;}
    }

    public static final class ContactItem {
        public final String name,number;
        public ContactItem(String name,String number){this.name=name==null?"":name;this.number=number==null?"":number;}
    }

    private enum Page { FAVORITES,RECENTS,CONTACTS,KEYPAD }
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Callback callback;
    private final List<RecentCall> recents=new ArrayList<>();
    private final List<ContactItem> contacts=new ArrayList<>();
    private final StringBuilder digits=new StringBuilder();
    private final Bitmap background;
    private final Bitmap logo;
    private final Rect backgroundSrc;
    private final ContactPhotoCache photoCache;
    private WeatherSnapshot weather=WeatherSnapshot.loading();
    private Page page=Page.RECENTS;
    private float d,sd,w,h,navTop,listTop,scrollPx,maxScroll,downY,downX,lastY,flingVelocity;
    private long lastMoveMs,pageChangedAt;
    private boolean dragging,moved;
    private int selectedIndex=-1,hoverIndex=-1;
    private static final float ROW_DP=78f;
    private static final String[][] KEYS={{"1",""},{"2","ABC"},{"3","DEF"},{"4","GHI"},{"5","JKL"},{"6","MNO"},{"7","PQRS"},{"8","TUV"},{"9","WXYZ"},{"*",""},{"0","+"},{"#",""}};

    public GlassPhoneView(Context context,Callback callback){
        super(context);this.callback=callback;d=getResources().getDisplayMetrics().density;sd=getResources().getDisplayMetrics().scaledDensity;
        background=BitmapFactory.decodeResource(getResources(),R.drawable.mountains_sunset);
        backgroundSrc=background==null?null:new Rect(0,0,background.getWidth(),background.getHeight());
        logo=BitmapFactory.decodeResource(getResources(),R.drawable.netwatch_phone_icon);
        photoCache=new ContactPhotoCache(context);
        setClickable(true);setFocusable(true);setLayerType(View.LAYER_TYPE_HARDWARE,null);setContentDescription("NetWatch Phone");
    }

    public void setRecentCalls(List<RecentCall> items){recents.clear();if(items!=null)recents.addAll(items);selectedIndex=-1;hoverIndex=-1;recalcMaxScroll();invalidate();}
    public void setContacts(List<ContactItem> items){contacts.clear();if(items!=null)contacts.addAll(items);selectedIndex=-1;hoverIndex=-1;recalcMaxScroll();invalidate();}
    public void setWeather(WeatherSnapshot snapshot){if(snapshot!=null)weather=snapshot;invalidate();}
    public void showKeypad(String preset){changePage(Page.KEYPAD);digits.setLength(0);if(preset!=null)digits.append(preset);invalidate();}

    @Override protected void onDetachedFromWindow(){photoCache.shutdown();super.onDetachedFromWindow();}
    @Override protected void onSizeChanged(int width,int height,int oldw,int oldh){w=width;h=height;navTop=Math.max(dp(560),h-dp(92));recalcMaxScroll();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);drawBackground(c);drawHeader(c);drawWeather(c);if(page==Page.RECENTS)drawRecents(c);else if(page==Page.CONTACTS)drawContacts(c);else if(page==Page.FAVORITES)drawFavorites(c);else drawKeypad(c);drawNav(c);animateIfNeeded();}

    private boolean motionActive(){return dragging||Math.abs(flingVelocity)>.18f;}

    private RectF backgroundRect(){
        if(background==null||w<=1||h<=1)return null;
        float bw=background.getWidth(),bh=background.getHeight(),scale=Math.max(w/bw,h/bh),drawW=bw*scale,drawH=bh*scale;
        float parallax=motionActive()?0:Math.max(-dp(8),Math.min(dp(8),-scrollPx*.008f));
        return new RectF((w-drawW)/2f,(h-drawH)/2f+parallax,(w+drawW)/2f,(h+drawH)/2f+parallax);
    }

    private void drawBackground(Canvas c){
        p.setShader(new LinearGradient(0,0,0,Math.max(1,h),0xFF173D62,0xFF06111F,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
        RectF dst=backgroundRect();if(background!=null&&dst!=null){p.setAlpha(255);c.drawBitmap(background,backgroundSrc,dst,p);p.setAlpha(255);p.setShader(new LinearGradient(0,0,0,Math.max(1,h),new int[]{0x00040D18,0x08071421,0x2506111F},new float[]{0f,.58f,1f},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);}
    }

    private void drawHeader(Canvas c){float top=dp(28);if(logo!=null){p.setColor(0x3006111F);c.drawCircle(dp(42),top+dp(20),dp(27),p);Rect src=new Rect(0,0,logo.getWidth(),logo.getHeight());RectF dst=new RectF(dp(17),top-dp(5),dp(67),top+dp(45));c.drawBitmap(logo,src,dst,p);}else drawShield(c,dp(42),top+dp(20),dp(21));text(c,"NetWatch Phone",dp(78),top+dp(19),sp(25),Color.WHITE,true,Paint.Align.LEFT);text(c,"Private. Protected. In your control.",dp(78),top+dp(41),sp(11.5f),0xFFF4F9FD,false,Paint.Align.LEFT);RectF more=new RectF(w-dp(59),top-dp(2),w-dp(17),top+dp(40));glass(c,more,dp(21),0x14FFFFFF,0x74FFFFFF);p.setColor(Color.WHITE);c.drawCircle(more.centerX()-dp(7),more.centerY(),dp(1.5f),p);c.drawCircle(more.centerX(),more.centerY(),dp(1.5f),p);c.drawCircle(more.centerX()+dp(7),more.centerY(),dp(1.5f),p);}
    private void drawWeather(Canvas c){RectF r=new RectF(dp(18),dp(82),w-dp(18),dp(134));glass(c,r,dp(21),0x12FFFFFF,0x55FFFFFF);text(c,weather.icon(),dp(37),dp(113),sp(21),Color.WHITE,false,Paint.Align.LEFT);text(c,"Albuquerque  "+weather.primaryLine(),dp(68),dp(104),sp(12.5f),Color.WHITE,true,Paint.Align.LEFT);text(c,weather.detailLine(),dp(68),dp(124),sp(10.2f),0xFFF4F9FD,false,Paint.Align.LEFT);}
    private void drawRecents(Canvas c){listTop=dp(233);drawSearch(c,"Search contacts, numbers, or places");text(c,"Recents",dp(20),dp(221),sp(27),Color.WHITE,true,Paint.Align.LEFT);drawList(c,true);}
    private void drawContacts(Canvas c){listTop=dp(233);drawSearch(c,"Search all contacts");text(c,"Contacts",dp(20),dp(221),sp(27),Color.WHITE,true,Paint.Align.LEFT);drawList(c,false);}
    private void drawFavorites(Canvas c){listTop=dp(208);text(c,"Favorites",dp(20),dp(177),sp(29),Color.WHITE,true,Paint.Align.LEFT);text(c,"People you call most",dp(20),dp(199),sp(12.5f),0xFFF4F9FD,false,Paint.Align.LEFT);drawList(c,false);}
    private void drawSearch(Canvas c,String hint){RectF r=new RectF(dp(17),dp(148),w-dp(17),dp(202));if(!motionActive())drawLensBackground(c,r,dp(27),1.018f);glass(c,r,dp(27),0x0BFFFFFF,0x50FFFFFF);drawSearchIcon(c,dp(42),dp(175));text(c,hint,dp(66),dp(181),sp(13.5f),0xFFF7FBFE,false,Paint.Align.LEFT);}

    private void drawList(Canvas c,boolean recentMode){
        List<?> items=recentMode?recents:contacts;float rowH=dp(ROW_DP),usable=Math.max(rowH,navTop-listTop-dp(8));
        if(items.isEmpty()){RectF empty=new RectF(dp(24),listTop+dp(30),w-dp(24),listTop+dp(145));if(!motionActive())drawLensBackground(c,empty,dp(28),1.025f);glass(c,empty,dp(28),0x09FFFFFF,0x42FFFFFF);text(c,recentMode?"No recent calls yet":"No contacts available",w/2,empty.centerY()-dp(4),sp(17),Color.WHITE,true,Paint.Align.CENTER);text(c,recentMode?"Your calls will appear here.":"Grant Contacts permission or use Search.",w/2,empty.centerY()+dp(24),sp(11.5f),0xFFF0F7FC,false,Paint.Align.CENTER);return;}
        int first=Math.max(0,(int)Math.floor(scrollPx/rowH));float rem=scrollPx-first*rowH;int count=Math.min(items.size()-first,(int)Math.ceil(usable/rowH)+1),activeIndex=motionActive()?-1:(hoverIndex>=0?hoverIndex:selectedIndex);
        for(int j=0;j<count;j++){
            int index=first+j;float top=listTop+j*rowH-rem,cy=top+rowH*.46f;if(cy<listTop-dp(70)||cy>navTop+dp(70))continue;boolean active=index==activeIndex;float focus=active?1f:0f,scale=active?1.025f:1f;
            c.save();c.scale(scale,scale,w/2,cy);RectF row=new RectF(dp(15),top+dp(3),w-dp(15),top+rowH-dp(5));
            if(!motionActive())drawLensBackground(c,row,dp(active?25:24),active?1.055f:1.028f);
            if(active){glass(c,row,dp(25),0x14FFFFFF,0xD8FFD88A);stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(dp(2));stroke.setColor(0xAA55B8FF);c.drawRoundRect(row,dp(25),dp(25),stroke);stroke.setStyle(Paint.Style.FILL);}else glass(c,row,dp(24),motionActive()?0x10FFFFFF:0x06FFFFFF,0x45FFFFFF);
            String name,number,detail;boolean missed=false;if(recentMode){RecentCall item=recents.get(index);name=item.name.trim().isEmpty()?(item.number.trim().isEmpty()?"Unknown Caller":item.number):item.name;number=item.number;detail=item.detail;missed=item.missed;}else{ContactItem item=contacts.get(index);name=item.name;number=item.number;detail=item.number;}
            Bitmap photo=photoCache.get(number,this::postInvalidateOnAnimation);drawAvatar(c,dp(49),cy,dp(23),name,photo,missed,focus);
            text(c,name,dp(84),cy-dp(5),sp(15.5f),Color.WHITE,true,Paint.Align.LEFT);text(c,detail,dp(84),cy+dp(17),sp(11.2f),missed?0xFFFFA7AE:0xFFF7FBFE,false,Paint.Align.LEFT);
            if(recentMode){drawDeleteCircle(c,w-dp(91),cy,dp(17));drawPhoneCircle(c,w-dp(43),cy,dp(18),focus);}else drawPhoneCircle(c,w-dp(46),cy,dp(19),focus);c.restore();
        }
        if(items.size()>1){int shownStart=Math.min(items.size(),first+1),shownEnd=Math.min(items.size(),first+Math.max(1,(int)Math.floor(usable/rowH)));text(c,shownStart+"–"+shownEnd+" of "+items.size(),w-dp(18),listTop-dp(8),sp(10.5f),0xFFE7F1F8,false,Paint.Align.RIGHT);}
    }

    private void drawLensBackground(Canvas c,RectF r,float radius,float zoom){if(background==null||backgroundSrc==null)return;RectF dst=backgroundRect();if(dst==null)return;c.save();Path clip=new Path();clip.addRoundRect(r,radius,radius,Path.Direction.CW);c.clipPath(clip);c.scale(zoom,zoom,r.centerX(),r.centerY());p.setAlpha(255);c.drawBitmap(background,backgroundSrc,dst,p);c.restore();p.setShader(new LinearGradient(r.left,r.top,r.right,r.bottom,new int[]{0x1CFFFFFF,0x02FFFFFF,0x0AA9D8FF},new float[]{0f,.48f,1f},Shader.TileMode.CLAMP));c.drawRoundRect(r,radius,radius,p);p.setShader(null);}

    private void drawAvatar(Canvas c,float x,float y,float r,String label,Bitmap photo,boolean alert,float focus){if(focus>.52f){p.setColor(0x8855B8FF);c.drawCircle(x,y,r+dp(4),p);}if(photo!=null){c.save();c.clipRect(x-r,y-r,x+r,y+r);float bw=photo.getWidth(),bh=photo.getHeight(),s=Math.max((r*2)/bw,(r*2)/bh),dw=bw*s,dh=bh*s;Rect src=new Rect(0,0,photo.getWidth(),photo.getHeight());RectF dst=new RectF(x-dw/2,y-dh/2,x+dw/2,y+dh/2);c.drawBitmap(photo,src,dst,p);c.restore();}else{int seed=label==null?0:label.hashCode();int base=alert?0xFFC35567:Color.rgb(70+Math.abs(seed%55),94+Math.abs((seed/7)%72),128+Math.abs((seed/13)%70));p.setColor(base);c.drawCircle(x,y,r,p);text(c,initials(label),x,y+dp(5.5f),sp(13),Color.WHITE,true,Paint.Align.CENTER);}stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(dp(focus>.5f?2f:1.1f));stroke.setColor(focus>.5f?0xFFFFD98A:0xD6FFFFFF);c.drawCircle(x,y,r,stroke);stroke.setStyle(Paint.Style.FILL);}

    private void drawDeleteCircle(Canvas c,float x,float y,float r){p.setColor(0x205C1822);c.drawCircle(x,y,r,p);stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(dp(1));stroke.setColor(0xA8FF8993);c.drawCircle(x,y,r,stroke);stroke.setStyle(Paint.Style.FILL);p.setColor(0xFFFF8993);c.drawRect(x-dp(6),y-dp(5),x+dp(6),y+dp(8),p);c.drawRect(x-dp(8),y-dp(9),x+dp(8),y-dp(6),p);p.setColor(0xFF5C1822);c.drawRect(x-dp(2),y-dp(3),x-dp(1),y+dp(6),p);c.drawRect(x+dp(2),y-dp(3),x+dp(3),y+dp(6),p);}
    private void drawPhoneCircle(Canvas c,float x,float y,float r,float focus){p.setColor(focus>.5f?0x704D91CA:0x205A84A7);c.drawCircle(x,y,r,p);drawPhoneGlyph(c,x,y,Color.WHITE,.72f,false);}

    private void drawKeypad(Canvas c){RectF numberBox=new RectF(dp(28),dp(155),w-dp(28),dp(225));drawLensBackground(c,numberBox,dp(30),1.02f);glass(c,numberBox,dp(30),0x0EFFFFFF,0x65FFFFFF);String shown=digits.length()==0?"Enter a number":digits.toString();text(c,shown,numberBox.centerX(),numberBox.centerY()+dp(8),digits.length()==0?sp(17):sp(27),digits.length()==0?0xFFF1F7FC:Color.WHITE,false,Paint.Align.CENTER);float cx=w/2f,col=dp(111),startX=cx-col,startY=dp(286),row=dp(96),radius=dp(37);for(int i=0;i<KEYS.length;i++){float x=startX+(i%3)*col,y=startY+(i/3)*row;boolean hot=hoverIndex==100+i;glass(c,new RectF(x-radius,y-radius,x+radius,y+radius),radius,hot?0x22FFFFFF:0x0EFFFFFF,hot?0xC6FFD48C:0x64FFFFFF);text(c,KEYS[i][0],x,y+(KEYS[i][1].isEmpty()?dp(10):dp(3)),sp(30),Color.WHITE,false,Paint.Align.CENTER);if(!KEYS[i][1].isEmpty())text(c,KEYS[i][1],x,y+dp(23),sp(10.5f),0xFFF7FBFE,false,Paint.Align.CENTER);}float callY=startY+row*4-dp(4);p.setShader(new LinearGradient(cx-dp(40),callY-dp(40),cx+dp(40),callY+dp(40),0xFF69EA67,0xFF18AE46,Shader.TileMode.CLAMP));c.drawCircle(cx,callY,dp(38),p);p.setShader(null);drawPhoneGlyph(c,cx,callY,Color.WHITE,1.18f,false);}
    private void drawNav(Canvas c){RectF nav=new RectF(dp(11),navTop,w-dp(11),h-dp(9));if(!motionActive())drawLensBackground(c,nav,dp(30),1.015f);glass(c,nav,dp(30),0x16051020,0x66FFFFFF);String[] labels={"Favorites","Recents","Contacts","Keypad"};Page[] pages={Page.FAVORITES,Page.RECENTS,Page.CONTACTS,Page.KEYPAD};float slot=nav.width()/4f,transition=Math.min(1f,(SystemClock.uptimeMillis()-pageChangedAt)/220f);for(int i=0;i<4;i++){float x=nav.left+slot*(i+.5f);boolean selected=page==pages[i];if(selected){float grow=.94f+.06f*easeOut(transition);RectF pill=new RectF(x-slot*.40f*grow,nav.top+dp(7),x+slot*.40f*grow,nav.bottom-dp(7));glass(c,pill,dp(25),0x35508FD9,0xC5E7F4FF);}drawNavIcon(c,i,x,nav.top+dp(28),selected?Color.WHITE:0xFFF0F5F9);text(c,labels[i],x,nav.top+dp(67),sp(10.4f),selected?Color.WHITE:0xFFF0F5F9,false,Paint.Align.CENTER);}}

    private void changePage(Page next){if(page==next)return;page=next;scrollPx=0;flingVelocity=0;selectedIndex=-1;hoverIndex=-1;pageChangedAt=SystemClock.uptimeMillis();recalcMaxScroll();invalidate();}
    private void recalcMaxScroll(){int size=page==Page.RECENTS?recents.size():(page==Page.CONTACTS||page==Page.FAVORITES?contacts.size():0);if(page==Page.KEYPAD){maxScroll=0;scrollPx=0;return;}float visible=Math.max(dp(ROW_DP),navTop-listTop-dp(8));maxScroll=Math.max(0,size*dp(ROW_DP)-visible);clampScroll();}
    private void clampScroll(){if(scrollPx<0)scrollPx=0;if(scrollPx>maxScroll)scrollPx=maxScroll;}
    private void animateIfNeeded(){if(!dragging&&Math.abs(flingVelocity)>.18f&&isListPage()){scrollPx+=flingVelocity;flingVelocity*=.89f;if(scrollPx<=0){scrollPx=0;flingVelocity=0;}if(scrollPx>=maxScroll){scrollPx=maxScroll;flingVelocity=0;}postInvalidateOnAnimation();}}

    @Override public boolean onTouchEvent(MotionEvent e){float x=e.getX(),y=e.getY();if(e.getAction()==MotionEvent.ACTION_DOWN){downX=x;downY=y;lastY=y;lastMoveMs=SystemClock.uptimeMillis();dragging=false;moved=false;flingVelocity=0;hoverIndex=page==Page.KEYPAD?hitVisualIndex(x,y):-1;invalidate();return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){float dy=y-lastY;if(Math.abs(y-downY)>dp(7)||Math.abs(x-downX)>dp(7)){dragging=true;moved=true;}if(dragging&&isListPage()){hoverIndex=-1;selectedIndex=-1;scrollPx-=dy;if(scrollPx<0)scrollPx*=.30f;if(scrollPx>maxScroll)scrollPx=maxScroll+(scrollPx-maxScroll)*.30f;long now=SystemClock.uptimeMillis(),dt=Math.max(1,now-lastMoveMs);flingVelocity=(-dy)*(16f/dt);lastMoveMs=now;}else if(page==Page.KEYPAD)hoverIndex=hitVisualIndex(x,y);lastY=y;postInvalidateOnAnimation();return true;}if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){if(isListPage()){clampScroll();}if(!moved&&e.getAction()==MotionEvent.ACTION_UP)handleTap(x,y);dragging=false;hoverIndex=-1;invalidate();return true;}return true;}
    private boolean isListPage(){return page==Page.RECENTS||page==Page.CONTACTS||page==Page.FAVORITES;}

    private void handleTap(float x,float y){if(y>=navTop){float slot=(w-dp(22))/4f;int i=(int)((x-dp(11))/slot);if(i==0)changePage(Page.FAVORITES);else if(i==1)changePage(Page.RECENTS);else if(i==2)changePage(Page.CONTACTS);else if(i==3)changePage(Page.KEYPAD);return;}if(x>w-dp(75)&&y<dp(88)){getContext().startActivity(new Intent(getContext(),UpdateCenterActivity.class));return;}if(y>=dp(82)&&y<=dp(138)){if(callback!=null)callback.openWeatherDetails();return;}if((page==Page.RECENTS||page==Page.CONTACTS)&&y>=dp(145)&&y<=dp(208)){if(callback!=null)callback.openSearch();return;}if(page==Page.KEYPAD){handleKeypadTap(x,y);return;}int index=hitListIndex(y);if(index<0||callback==null)return;selectedIndex=index;if(page==Page.RECENTS){RecentCall item=recents.get(index);String name=item.name.trim().isEmpty()?item.number:item.name;if(x>w-dp(66))callback.placeCall(item.number);else if(x>w-dp(116))callback.deleteRecent(item.id,name,item.number);else callback.openProfile(name,item.number);return;}ContactItem item=contacts.get(index);if(x>w-dp(90))callback.placeCall(item.number);else callback.openProfile(item.name,item.number);}

    private void handleKeypadTap(float x,float y){float cx=w/2f,col=dp(111),startX=cx-col,startY=dp(286),row=dp(96);for(int i=0;i<KEYS.length;i++){float kx=startX+(i%3)*col,ky=startY+(i/3)*row;if(distance(x,y,kx,ky)<=dp(45)){digits.append(KEYS[i][0]);invalidate();return;}}float callY=startY+row*4-dp(4);if(distance(x,y,cx,callY)<=dp(48)&&digits.length()>0&&callback!=null)callback.placeCall(digits.toString());if(y>=dp(155)&&y<=dp(225)&&x>w-dp(95)&&digits.length()>0){digits.deleteCharAt(digits.length()-1);invalidate();}}
    private int hitVisualIndex(float x,float y){if(page==Page.KEYPAD){float cx=w/2f,col=dp(111),startX=cx-col,startY=dp(286),row=dp(96);for(int i=0;i<KEYS.length;i++){float kx=startX+(i%3)*col,ky=startY+(i/3)*row;if(distance(x,y,kx,ky)<=dp(48))return 100+i;}return -1;}return hitListIndex(y);}
    private int hitListIndex(float y){if(!isListPage()||y<listTop||y>navTop)return -1;int size=page==Page.RECENTS?recents.size():contacts.size();int index=(int)Math.floor((y-listTop+scrollPx)/dp(ROW_DP));return index>=0&&index<size?index:-1;}

    private void glass(Canvas c,RectF r,float radius,int fill,int border){p.setShader(new LinearGradient(r.left,r.top,r.right,r.bottom,new int[]{adjustAlpha(fill,1.18f),fill,adjustAlpha(fill,.60f)},new float[]{0f,.48f,1f},Shader.TileMode.CLAMP));c.drawRoundRect(r,radius,radius,p);p.setShader(null);stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(dp(1));stroke.setColor(border);c.drawRoundRect(r,radius,radius,stroke);stroke.setStyle(Paint.Style.FILL);}
    private int adjustAlpha(int color,float factor){int a=Math.max(0,Math.min(255,(int)(Color.alpha(color)*factor)));return Color.argb(a,Color.red(color),Color.green(color),Color.blue(color));}
    private void drawShield(Canvas c,float x,float y,float r){Path q=new Path();q.moveTo(x,y-r);q.lineTo(x+r*.75f,y-r*.6f);q.lineTo(x+r*.64f,y+r*.4f);q.quadTo(x,y+r*1.08f,x-r*.64f,y+r*.4f);q.lineTo(x-r*.75f,y-r*.6f);q.close();p.setShader(new LinearGradient(x-r,y-r,x+r,y+r,0xFF78D9FF,0xFF246DC4,Shader.TileMode.CLAMP));c.drawPath(q,p);p.setShader(null);}
    private void drawSearchIcon(Canvas c,float x,float y){stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(dp(2.3f));stroke.setColor(0xFFF7FBFE);c.drawCircle(x,y-dp(2),dp(8),stroke);c.drawLine(x+dp(6),y+dp(4),x+dp(13),y+dp(11),stroke);stroke.setStyle(Paint.Style.FILL);}
    private void drawPhoneGlyph(Canvas c,float x,float y,int color,float scale,boolean hang){p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(dp(5)*scale);p.setColor(color);RectF arc=new RectF(x-dp(12)*scale,y-dp(12)*scale,x+dp(12)*scale,y+dp(12)*scale);c.drawArc(arc,hang?205:133,hang?130:93,false,p);p.setStrokeCap(Paint.Cap.BUTT);p.setStyle(Paint.Style.FILL);}
    private void drawNavIcon(Canvas c,int index,float x,float y,int color){p.setColor(color);stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(dp(2.1f));stroke.setColor(color);if(index==0){Path star=new Path();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=i%2==0?dp(10):dp(4.5f),px=x+(float)Math.cos(a)*r,py=y+(float)Math.sin(a)*r;if(i==0)star.moveTo(px,py);else star.lineTo(px,py);}star.close();c.drawPath(star,p);}else if(index==1){c.drawCircle(x,y,dp(10),stroke);c.drawLine(x,y,x,y-dp(6),stroke);c.drawLine(x,y,x+dp(5),y+dp(2),stroke);}else if(index==2){c.drawCircle(x,y-dp(5),dp(5),p);c.drawRoundRect(new RectF(x-dp(9),y+dp(2),x+dp(9),y+dp(11)),dp(6),dp(6),p);}else{for(int rr=-1;rr<=1;rr++)for(int cc=-1;cc<=1;cc++)c.drawCircle(x+cc*dp(8),y+rr*dp(8),dp(3.5f),p);}stroke.setStyle(Paint.Style.FILL);}
    private String initials(String value){if(value==null||value.trim().isEmpty())return"?";String[] a=value.trim().split("\\s+");String s=a[0].substring(0,1).toUpperCase();if(a.length>1)s+=a[a.length-1].substring(0,1).toUpperCase();return s;}
    private void text(Canvas c,String value,float x,float y,float size,int color,boolean bold,Paint.Align align){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));p.setTextSize(size);p.setTextAlign(align);p.setShadowLayer(dp(.8f),0,dp(.5f),0x99000000);c.drawText(value==null?"":value,x,y,p);p.clearShadowLayer();p.setTextAlign(Paint.Align.LEFT);}
    private static float easeOut(float t){float x=Math.max(0,Math.min(1,t));return 1-(1-x)*(1-x)*(1-x);}
    private static float distance(float x1,float y1,float x2,float y2){float dx=x1-x2,dy=y1-y2;return(float)Math.sqrt(dx*dx+dy*dy);}
    private float dp(float v){return v*d;}
    private float sp(float v){return v*sd;}
}
