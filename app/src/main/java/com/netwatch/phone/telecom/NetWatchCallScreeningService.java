package com.netwatch.phone.telecom;

import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;
import com.netwatch.phone.api.ApiClient;
import com.netwatch.phone.api.ScreenDecision;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class NetWatchCallScreeningService extends CallScreeningService {
    private static final String TAG = "NetWatchScreen";
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public void onScreenCall(Call.Details details) {
        final String number = details.getHandle() == null ? "" : details.getHandle().getSchemeSpecificPart();
        executor.execute(() -> {
            ScreenDecision decision;
            try {
                boolean known = ContactLookup.isKnown(this, number);
                decision = new ApiClient(this).lookupIncoming(number, known);
            } catch (Exception ex) {
                Log.w(TAG, "Contact center unavailable; fail-open", ex);
                decision = ScreenDecision.allow("contact_center_unavailable");
            }
            respond(details, decision);
        });
    }

    private void respond(Call.Details details, ScreenDecision decision) {
        CallResponse.Builder b = new CallResponse.Builder();
        switch (decision.action) {
            case BLOCK:
                b.setDisallowCall(true)
                 .setRejectCall(true)
                 .setSkipCallLog(false)
                 .setSkipNotification(false);
                break;
            case SILENCE:
                b.setSilenceCall(true);
                break;
            case SCREEN:
                b.setSilenceCall(true);
                break;
            case ALLOW:
            default:
                break;
        }
        respondToCall(details, b.build());
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
