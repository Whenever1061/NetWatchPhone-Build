package com.netwatch.phone.telecom;

import android.content.Intent;
import android.telecom.Call;
import android.telecom.InCallService;
import android.telecom.VideoProfile;
import android.util.Log;
import com.netwatch.phone.api.ApiClient;
import com.netwatch.phone.ui.CallScreenActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class NetWatchInCallService extends InCallService {
    private static final String TAG = "NetWatchInCall";
    private static volatile Call activeCall;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        activeCall = call;
        String number = numberOf(call);
        executor.execute(() -> send("added", number));
        if (call != null && call.getState() == Call.STATE_RINGING) showIncomingUi(number);
    }

    @Override
    public void onCallRemoved(Call call) {
        String number = numberOf(call);
        executor.execute(() -> send("removed", number));
        if (activeCall == call) activeCall = null;
        super.onCallRemoved(call);
    }

    private void showIncomingUi(String number) {
        try {
            Intent i = new Intent(this, CallScreenActivity.class);
            i.putExtra("number", number);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Exception ex) {
            Log.w(TAG, "Could not open incoming-call UI", ex);
        }
    }

    public static boolean answerActive() {
        Call call = activeCall;
        if (call == null) return false;
        try {
            call.answer(VideoProfile.STATE_AUDIO_ONLY);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public static boolean declineActive() {
        Call call = activeCall;
        if (call == null) return false;
        try {
            call.reject(false, null);
            return true;
        } catch (Exception ex) {
            try {
                call.disconnect();
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    private void send(String event, String number) {
        try {
            new ApiClient(this).postCallEvent(event, number);
        } catch (Exception ex) {
            Log.w(TAG, "Could not post call event", ex);
        }
    }

    private static String numberOf(Call call) {
        if (call == null || call.getDetails() == null || call.getDetails().getHandle() == null) return "";
        return call.getDetails().getHandle().getSchemeSpecificPart();
    }

    @Override
    public void onDestroy() {
        activeCall = null;
        executor.shutdownNow();
        super.onDestroy();
    }
}
