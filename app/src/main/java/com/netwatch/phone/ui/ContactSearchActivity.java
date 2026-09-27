package com.netwatch.phone.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.netwatch.phone.R;
import java.util.ArrayList;
import java.util.Locale;

public final class ContactSearchActivity extends Activity {
    public static final String RESULT_NUMBER="netwatch_contact_number";
    private final ArrayList<Row> all=new ArrayList<>(),filtered=new ArrayList<>();
    private ContactAdapter adapter;private TextView count;private ListView list;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(6,17,31));getWindow().setNavigationBarColor(Color.rgb(4,12,22));

        FrameLayout frame=new FrameLayout(this);
        ImageView bg=new ImageView(this);bg.setImageResource(R.drawable.mountains_sunset);bg.setScaleType(ImageView.ScaleType.CENTER_CROP);frame.addView(bg,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);shade.setBackgroundColor(0xCA071522);frame.addView(shade,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(18),dp(18),dp(10));frame.addView(root,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.netwatch_phone_icon);head.addView(logo,new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout texts=new LinearLayout(this);texts.setOrientation(LinearLayout.VERTICAL);texts.setPadding(dp(10),0,0,0);
        TextView title=text("Search contacts",27,Color.WHITE,true);texts.addView(title);
        TextView subtitle=text("Every contact on this phone — search by name or number.",12.5f,0xFFBDD3E7,false);texts.addView(subtitle);
        head.addView(texts,new LinearLayout.LayoutParams(0,-2,1f));root.addView(head);

        EditText search=new EditText(this);search.setHint("Type a name or phone number");search.setHintTextColor(0xFFB3C5D6);search.setTextColor(Color.WHITE);search.setSingleLine(true);search.setTextSize(16);search.setPadding(dp(17),dp(8),dp(17),dp(8));
        GradientDrawable searchBg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0x604C7292,0x3A203D54});searchBg.setCornerRadius(dp(25));searchBg.setStroke(dp(1),0x66FFFFFF);search.setBackground(searchBg);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(54));sp.topMargin=dp(14);root.addView(search,sp);

        count=text("",12,0xFFBDD3E7,false);count.setPadding(dp(4),dp(8),0,dp(6));root.addView(count);

        list=new ListView(this);list.setDividerHeight(dp(5));list.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));list.setCacheColorHint(Color.TRANSPARENT);list.setBackgroundColor(Color.TRANSPARENT);list.setClipToPadding(false);list.setPadding(0,0,0,dp(18));
        adapter=new ContactAdapter();list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1f));

        setContentView(frame);
        if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){Toast.makeText(this,"Contacts permission is required",Toast.LENGTH_LONG).show();finish();return;}
        load();filter("");

        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){filter(s==null?"":s.toString());}public void afterTextChanged(Editable e){}});
        list.setOnItemClickListener((p,v,pos,id)->{if(pos<0||pos>=filtered.size())return;Intent result=new Intent();result.putExtra(RESULT_NUMBER,filtered.get(pos).number);setResult(RESULT_OK,result);finish();});
        list.setOnScrollListener(new AbsListView.OnScrollListener(){public void onScrollStateChanged(AbsListView v,int state){animateVisible();}public void onScroll(AbsListView v,int first,int visible,int total){animateVisible();}});
        search.requestFocus();getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void animateVisible(){
        if(list==null)return;float center=list.getHeight()/2f;
        int best=-1;float bestDist=Float.MAX_VALUE;
        for(int i=0;i<list.getChildCount();i++){
            View child=list.getChildAt(i);float cy=(child.getTop()+child.getBottom())/2f;float dist=Math.abs(cy-center);float proximity=Math.max(0,1-dist/Math.max(dp(200),center));
            child.animate().cancel();child.setScaleX(1f+.045f*proximity);child.setScaleY(1f+.045f*proximity);child.setAlpha(.72f+.28f*proximity);
            if(dist<bestDist){bestDist=dist;best=i;}
        }
        for(int i=0;i<list.getChildCount();i++)applyRowGlow(list.getChildAt(i),i==best);
    }

    private void applyRowGlow(View v,boolean focused){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,focused?new int[]{0x74516F88,0x55304B61}:new int[]{0x443A5870,0x3023384A});
        g.setCornerRadius(dp(22));g.setStroke(dp(focused?2:1),focused?0xCCF4C86F:0x46FFFFFF);v.setBackground(g);
        if(focused)v.setElevation(dp(5));else v.setElevation(0);
    }

    private void load(){
        all.clear();String[] proj={ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER};
        try(Cursor c=getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,proj,null,null,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" COLLATE NOCASE ASC")){
            if(c==null)return;int n=c.getColumnIndexOrThrow(proj[0]),p=c.getColumnIndexOrThrow(proj[1]),z=c.getColumnIndex(proj[2]);java.util.HashSet<String> seen=new java.util.HashSet<>();
            while(c.moveToNext()){String name=c.getString(n),num=c.getString(p),norm=z>=0?c.getString(z):"";if(name==null||num==null)continue;String key=name+"|"+num;if(!seen.add(key))continue;all.add(new Row(name,num,norm));}
        }catch(Throwable ignored){}
    }

    private void filter(String raw){
        String q=raw==null?"":raw.trim().toLowerCase(Locale.ROOT),digits=q.replaceAll("[^0-9+]","");
        filtered.clear();if(q.isEmpty())filtered.addAll(all);else for(Row r:all){String n=r.name.toLowerCase(Locale.ROOT),p=r.number.toLowerCase(Locale.ROOT),compact=r.number.replaceAll("[^0-9+]",""),norm=r.normalized==null?"":r.normalized.toLowerCase(Locale.ROOT);if(n.contains(q)||p.contains(q)||(!digits.isEmpty()&&compact.contains(digits))||norm.contains(q))filtered.add(r);}
        count.setText(filtered.size()+(filtered.size()==1?" contact":" contacts"));adapter.notifyDataSetChanged();list.post(this::animateVisible);
    }

    private final class ContactAdapter extends BaseAdapter{
        public int getCount(){return filtered.size();}public Object getItem(int p){return filtered.get(p);}public long getItemId(int p){return p;}
        public View getView(int pos,View convert,ViewGroup parent){
            LinearLayout row;TextView name,num;
            if(convert instanceof LinearLayout){row=(LinearLayout)convert;name=(TextView)row.getChildAt(0);num=(TextView)row.getChildAt(1);}
            else{row=new LinearLayout(ContactSearchActivity.this);row.setOrientation(LinearLayout.VERTICAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(12),dp(16),dp(12));name=text("",16,Color.WHITE,true);num=text("",13,0xFFBED2E4,false);num.setPadding(0,dp(3),0,0);row.addView(name);row.addView(num);applyRowGlow(row,false);}
            Row r=filtered.get(pos);name.setText(r.name);num.setText(r.number);return row;
        }
    }

    private TextView text(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setTypeface(Typeface.create("sans",b?Typeface.BOLD:Typeface.NORMAL));return v;}
    private static final class Row{final String name,number,normalized;Row(String n,String p,String z){name=n;number=p;normalized=z;}}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
