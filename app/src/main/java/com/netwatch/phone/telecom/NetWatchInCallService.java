package com.netwatch.phone.telecom;

import android.telecom.Call;
import android.telecom.InCallService;
import android.util.Log;
import com.netwatch.phone.api.ApiClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class NetWatchInCallService extends InCallService {
    private static final String TAG = "NetWatchInCall";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        String number = numberOf(call);
        executor.execute(() -> send("added", number));
    }

    @Override
    public void onCallRemoved(Call call) {
        String number = numberOf(call);
        executor.execute(() -> send("removed", number));
        super.onCallRemoved(call);
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
        executor.shutdownNow();
        super.onDestroy();
    }
}
