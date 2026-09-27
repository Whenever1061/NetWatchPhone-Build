package com.netwatch.phone.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.netwatch.phone.telecom.NetWatchInCallService;

/**
 * Tiny launcher router. If a call is active, tapping the NetWatch Phone icon
 * always returns to the live call controls instead of stranding the user on
 * the normal dialer screen.
 */
public final class LauncherActivity extends Activity {
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        Intent next;
        if(NetWatchInCallService.hasActiveCall()){
            next=new Intent(this,CallScreenActivity.class);
            next.putExtra("number",NetWatchInCallService.activeNumber());
            next.putExtra("incoming",NetWatchInCallService.activeState()==android.telecom.Call.STATE_RINGING);
        }else{
            next=new Intent(this,MainActivity.class);
        }
        next.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(next);
        finish();
    }
}
