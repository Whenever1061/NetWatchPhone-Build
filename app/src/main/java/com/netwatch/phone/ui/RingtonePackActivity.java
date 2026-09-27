package com.netwatch.phone.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import com.netwatch.phone.R;
import com.netwatch.phone.telecom.NetWatchRinger;

/** Preview and select the ringtone used by NetWatch for incoming calls. */
public final class RingtonePackActivity extends Activity {
    private LinearLayout list;
    private TextView selectedText;

    @Override protected void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(5,15,27));getWindow().setNavigationBarColor(Color.rgb(4,11,20));setContentView(buildUi());}
    @Override protected void onStop(){NetWatchRinger.stop();super.onStop();}

    private View buildUi(){
        FrameLayout frame=new FrameLayout(this);ImageView bg=new ImageView(this);bg.setImageResource(R.drawable.mountains_sunset);bg.setScaleType(ImageView.ScaleType.CENTER_CROP);frame.addView(bg,new FrameLayout.LayoutParams(-1,-1));View shade=new View(this);shade.setBackgroundColor(0x4A071522);frame.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(20),dp(18),dp(28));scroll.addView(root);frame.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.HORIZONTAL);hero.setGravity(Gravity.CENTER_VERTICAL);ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_sound);logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);hero.addView(logo,new LinearLayout.LayoutParams(dp(58),dp(58)));LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);words.setPadding(dp(12),0,0,0);words.addView(text("NetWatch Ringtone Pack",26,Color.WHITE,true));words.addView(text("Local sounds • private • no cloud audio",11.5f,0xFFF0F6FB,false));hero.addView(words,new LinearLayout.LayoutParams(0,-2,1));root.addView(hero);
        TextView curator=text("Curated by Ricky",12.5f,0xFFFFD27A,true);curator.setPadding(dp(4),dp(8),dp(4),0);root.addView(curator);
        selectedText=text("Selected: "+NetWatchRinger.selectedName(this),13,0xFFFFD27A,true);selectedText.setPadding(dp(4),dp(12),dp(4),dp(12));root.addView(selectedText);
        TextView tip=text("Tap PREVIEW to hear a ringtone. Tap USE to make it the NetWatch incoming-call sound.",12,0xFFF0F6FB,false);tip.setPadding(dp(4),0,dp(4),dp(12));root.addView(tip);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list,new LinearLayout.LayoutParams(-1,-2));rebuildList();
        Button stop=button("STOP PREVIEW",0x664B6880);stop.setOnClickListener(v->NetWatchRinger.stop());LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(54));sp.topMargin=dp(8);root.addView(stop,sp);return frame;
    }

    private void rebuildList(){
        list.removeAllViews();String[] names=NetWatchRinger.names();int selected=NetWatchRinger.selected(this);String[] notes={"Clean, clear three-step phone cadence with no rumble or harsh overtones","Warm rising bells inspired by sunrise over the Sandias","Lower, calmer night-time alert","Rhythmic electronic pulse","Simple two-tone secure-line signal"};
        for(int i=0;i<names.length;i++){final int index=i;LinearLayout card=card(selected==i);card.addView(text(names[i]+(selected==i?"   ✓":""),17,Color.WHITE,true));TextView desc=text(notes[i],11.5f,0xFFE2EEF7,false);desc.setPadding(0,dp(3),0,dp(10));card.addView(desc);LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);Button preview=button("PREVIEW",0x663C77A8);preview.setOnClickListener(v->NetWatchRinger.preview(this,index));Button use=button(selected==i?"IN USE":"USE",selected==i?0xAA2A9E55:0x665687B0);use.setEnabled(selected!=i);use.setOnClickListener(v->{NetWatchRinger.stop();NetWatchRinger.select(this,index);selectedText.setText("Selected: "+NetWatchRinger.selectedName(this));rebuildList();NetWatchRinger.preview(this,index);});LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,dp(50),1);half.setMargins(dp(3),0,dp(3),0);actions.addView(preview,half);actions.addView(use,half);card.addView(actions);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(10);list.addView(card,cp);}
    }

    private LinearLayout card(boolean active){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(14),dp(16),dp(14));GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,active?new int[]{0x7A5E6535,0x55345E78,0x44132535}:new int[]{0x503B6385,0x351A3852,0x35101D2C});g.setCornerRadius(dp(23));g.setStroke(dp(active?2:1),active?0xFFFFD27A:0x70FFFFFF);box.setBackground(g);return box;}
    private Button button(String label,int color){Button b=new Button(this);b.setText(label);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.DEFAULT_BOLD);GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(22));g.setStroke(dp(1),0x66FFFFFF);b.setBackground(g);return b;}
    private TextView text(String s,float size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));return v;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
