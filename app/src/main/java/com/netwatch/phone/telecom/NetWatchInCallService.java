package com.netwatch.phone.telecom;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.telecom.VideoProfile;
import android.util.Log;
import com.netwatch.phone.api.ApiClient;
import com.netwatch.phone.config.AppConfig;
import com.netwatch.phone.ui.CallScreenActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class NetWatchInCallService extends InCallService {
    private static final String TAG="NetWatchInCall";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static volatile Call activeCall;
    private static volatile NetWatchInCallService instance;
    private static volatile boolean muted,speaker;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();

    private final Call.Callback callback=new Call.Callback(){
        @Override public void onStateChanged(Call call,int state){
            super.onStateChanged(call,state);
            if(state==Call.STATE_RINGING)NetWatchRinger.start(NetWatchInCallService.this);
            else NetWatchRinger.stop();
            if(state==Call.STATE_DISCONNECTED)NetWatchRinger.stop();
        }
    };

    @Override public void onCreate(){super.onCreate();instance=this;}

    @Override public void onCallAdded(Call call){
        super.onCallAdded(call);
        activeCall=call;
        if(call!=null)call.registerCallback(callback);
        if(call!=null&&call.getState()==Call.STATE_RINGING)NetWatchRinger.start(this);else NetWatchRinger.stop();
        String number=numberOf(call);
        if(AppConfig.isUsableContactCenter(this))executor.execute(()->send("added",number));
        showCallUi(call);
    }

    @Override public void onCallRemoved(Call call){
        NetWatchRinger.stop();
        String number=numberOf(call);
        if(AppConfig.isUsableContactCenter(this))executor.execute(()->send("removed",number));
        try{if(call!=null)call.unregisterCallback(callback);}catch(Throwable ignored){}
        if(activeCall==call)activeCall=null;
        super.onCallRemoved(call);
    }

    private void showCallUi(Call call){
        try{
            Intent i=new Intent(this,CallScreenActivity.class);
            i.putExtra("number",numberOf(call));
            i.putExtra("incoming",call!=null&&call.getState()==Call.STATE_RINGING);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        }catch(Throwable ex){Log.w(TAG,"Could not open NetWatch in-call UI",ex);}
    }

    public static boolean answerActive(){Call call=activeCall;if(call==null)return false;try{NetWatchRinger.stop();call.answer(VideoProfile.STATE_AUDIO_ONLY);return true;}catch(Throwable ex){return false;}}
    public static boolean declineActive(){Call call=activeCall;if(call==null)return false;NetWatchRinger.stop();try{call.reject(false,null);return true;}catch(Throwable ex){try{call.disconnect();return true;}catch(Throwable ignored){return false;}}}
    public static boolean disconnectActive(){Call call=activeCall;if(call==null)return false;NetWatchRinger.stop();try{call.disconnect();return true;}catch(Throwable ignored){return false;}}
    public static int activeState(){Call call=activeCall;return call==null?Call.STATE_DISCONNECTED:call.getState();}
    public static boolean hasActiveCall(){return activeCall!=null&&activeCall.getState()!=Call.STATE_DISCONNECTED;}
    public static String activeNumber(){return numberOf(activeCall);}
    public static boolean toggleMute(){NetWatchInCallService s=instance;if(s==null)return muted;try{muted=!muted;s.setMuted(muted);}catch(Throwable ignored){}return muted;}
    @SuppressWarnings("deprecation") public static boolean toggleSpeaker(){NetWatchInCallService s=instance;if(s==null)return speaker;try{speaker=!speaker;s.setAudioRoute(speaker?CallAudioState.ROUTE_SPEAKER:CallAudioState.ROUTE_EARPIECE);}catch(Throwable ignored){}return speaker;}
    public static boolean isMuted(){return muted;}
    public static boolean isSpeakerOn(){return speaker;}

    /** Send a normal in-call DTMF digit for bank/IVR menus. */
    public static boolean sendDtmf(char digit){
        if("0123456789*#".indexOf(digit)<0)return false;
        Call call=activeCall;
        if(call==null||call.getState()==Call.STATE_RINGING||call.getState()==Call.STATE_DISCONNECTED)return false;
        try{
            call.playDtmfTone(digit);
            MAIN.postDelayed(()->{try{Call current=activeCall;if(current!=null)current.stopDtmfTone();}catch(Throwable ignored){}},180);
            return true;
        }catch(Throwable ex){return false;}
    }

    private void send(String event,String number){try{new ApiClient(this).postCallEvent(event,number);}catch(Throwable ex){Log.w(TAG,"Could not post call event",ex);}}
    private static String numberOf(Call call){try{if(call==null||call.getDetails()==null||call.getDetails().getHandle()==null)return "";return call.getDetails().getHandle().getSchemeSpecificPart();}catch(Throwable ignored){return "";}}
    @Override public void onDestroy(){NetWatchRinger.stop();try{if(activeCall!=null)activeCall.unregisterCallback(callback);}catch(Throwable ignored){}activeCall=null;instance=null;executor.shutdownNow();super.onDestroy();}
}
