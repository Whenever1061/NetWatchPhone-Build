package com.netwatch.phone.ui;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.view.Gravity;
import android.widget.*;
import com.netwatch.phone.R;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** In-app profile for a saved contact or recent caller. */
public final class CallerProfileActivity extends Activity {
    public static final String EXTRA_NAME="name";
    public static final String EXTRA_NUMBER="number";

    private String name="", number="";
    private Uri contactUri;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(5,15,27));
        getWindow().setNavigationBarColor(Color.rgb(4,11,20));
        name=safe(getIntent().getStringExtra(EXTRA_NAME));
        number=safe(getIntent().getStringExtra(EXTRA_NUMBER));
        if(name.trim().isEmpty())name=number.trim().isEmpty()?"Unknown caller":number;
        setContentView(buildUi());
    }

    private android.view.View buildUi(){
        FrameLayout frame=new FrameLayout(this);
        ImageView bg=new ImageView(this);bg.setImageResource(R.drawable.mountains_sunset);bg.setScaleType(ImageView.ScaleType.CENTER_CROP);frame.addView(bg,new FrameLayout.LayoutParams(-1,-1));
        android.view.View shade=new android.view.View(this);shade.setBackgroundColor(0xC7071522);frame.addView(shade,new FrameLayout.LayoutParams(-1,-1));

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER_HORIZONTAL);root.setPadding(dp(18),dp(18),dp(18),dp(30));
        scroll.addView(root);frame.addView(scroll,new FrameLayout.LayoutParams(-1,-1));

        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_netwatch_launcher);logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);root.addView(logo,new LinearLayout.LayoutParams(dp(76),dp(76)));
        TextView header=text("Caller Profile",13,0xFFBFD7EA,true);header.setPadding(0,dp(5),0,dp(5));root.addView(header);
        TextView title=text(name,29,Color.WHITE,true);title.setGravity(Gravity.CENTER);root.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView phone=text(number.isEmpty()?"No number available":number,17,0xFFE5EEF6,false);phone.setGravity(Gravity.CENTER);phone.setPadding(0,dp(2),0,dp(16));root.addView(phone,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout identity=card();
        identity.addView(section("Profile"));
        contactUri=findContactUri(number);
        identity.addView(row("Contact status",contactUri==null?"Not saved in Contacts":"Saved contact"));
        identity.addView(row("Phone number",number.isEmpty()?"—":number));
        root.addView(identity,cardParams());

        CallSummary summary=readCallSummary(number);
        LinearLayout history=card();
        history.addView(section("Call history"));
        history.addView(row("Recent calls",String.valueOf(summary.count)));
        history.addView(row("Last call",summary.lastCall));
        history.addView(row("Last direction",summary.lastDirection));
        root.addView(history,cardParams());

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setGravity(Gravity.CENTER);
        Button call=action("CALL",0xFF1FAE57);call.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_call,0,0,0);call.setCompoundDrawablePadding(dp(7));call.setOnClickListener(v->dial());
        Button message=action("MESSAGE",0xFF2C76CB);message.setOnClickListener(v->message());
        LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,dp(58),1f);half.setMargins(dp(4),dp(4),dp(4),dp(4));actions.addView(call,half);actions.addView(message,half);
        root.addView(actions,new LinearLayout.LayoutParams(-1,-2));

        if(contactUri!=null){
            Button contact=action("OPEN CONTACT CARD",0x665687B0);contact.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_contacts,0,0,0);contact.setCompoundDrawablePadding(dp(8));contact.setOnClickListener(v->openContact());
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(56));cp.setMargins(dp(4),dp(8),dp(4),0);root.addView(contact,cp);
        }

        TextView note=text("This profile stays inside NetWatch Phone. Saved-contact details come from your device Contacts; call history comes from your device call log.",11.5f,0xFFAFC5D7,false);note.setGravity(Gravity.CENTER);note.setPadding(dp(8),dp(18),dp(8),0);root.addView(note);
        return frame;
    }

    private void dial(){
        if(number.isEmpty())return;
        try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",number,null)));}catch(Throwable ignored){}
    }
    private void message(){
        if(number.isEmpty())return;
        try{startActivity(new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+Uri.encode(number))));}catch(Throwable ignored){}
    }
    private void openContact(){
        try{if(contactUri!=null)startActivity(new Intent(Intent.ACTION_VIEW,contactUri));}catch(Throwable ignored){}
    }

    private Uri findContactUri(String n){
        if(n==null||n.trim().isEmpty())return null;
        try(Cursor c=getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.CONTACT_ID,ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY},
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER+"=? OR "+ContactsContract.CommonDataKinds.Phone.NUMBER+"=?",
                new String[]{normalize(n),n},null)){
            if(c!=null&&c.moveToFirst()){
                long id=c.getLong(0);String key=c.getString(1);
                return ContactsContract.Contacts.getLookupUri(id,key);
            }
        }catch(Throwable ignored){}
        return null;
    }

    private CallSummary readCallSummary(String n){
        CallSummary s=new CallSummary();
        if(n==null||n.trim().isEmpty())return s;
        String selection=CallLog.Calls.NUMBER+"=?";
        try(Cursor c=getContentResolver().query(CallLog.Calls.CONTENT_URI,
                new String[]{CallLog.Calls.TYPE,CallLog.Calls.DATE},selection,new String[]{n},CallLog.Calls.DATE+" DESC")){
            if(c==null)return s;
            while(c.moveToNext()){
                s.count++;
                if(s.count==1){
                    int type=c.getInt(0);long when=c.getLong(1);
                    s.lastCall=new SimpleDateFormat("MMM d, yyyy • h:mm a",Locale.getDefault()).format(new Date(when));
                    s.lastDirection=type==CallLog.Calls.OUTGOING_TYPE?"Outgoing":(type==CallLog.Calls.MISSED_TYPE||type==CallLog.Calls.REJECTED_TYPE?"Missed":"Incoming");
                }
            }
        }catch(Throwable ignored){}
        return s;
    }

    private static final class CallSummary{int count=0;String lastCall="No recent calls";String lastDirection="—";}
    private LinearLayout card(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(17),dp(15),dp(17),dp(15));GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0x553B6385,0x33203852,0x44101D2C});g.setCornerRadius(dp(24));g.setStroke(dp(1),0x55FFFFFF);box.setBackground(g);return box;}
    private LinearLayout.LayoutParams cardParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);return p;}
    private TextView section(String s){TextView v=text(s,16,Color.WHITE,true);v.setPadding(0,0,0,dp(8));return v;}
    private LinearLayout row(String a,String b){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setPadding(0,dp(6),0,dp(6));TextView left=text(a,12,0xFFBDD0E1,false);TextView right=text(b,12,Color.WHITE,true);right.setGravity(Gravity.END);r.addView(left,new LinearLayout.LayoutParams(0,-2,1f));r.addView(right,new LinearLayout.LayoutParams(0,-2,1.4f));return r;}
    private Button action(String label,int color){Button b=new Button(this);b.setText(label);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.DEFAULT_BOLD);GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(24));g.setStroke(dp(1),0x66FFFFFF);b.setBackground(g);return b;}
    private TextView text(String s,float size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));return v;}
    private static String safe(String s){return s==null?"":s;}
    private static String normalize(String s){return s==null?"":s.replaceAll("[^0-9+]","");}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
