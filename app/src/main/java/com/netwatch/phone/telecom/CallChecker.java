package com.netwatch.phone.telecom;

import android.content.Context;
import com.netwatch.phone.api.ApiClient;
import com.netwatch.phone.api.ScreenDecision;
import com.netwatch.phone.config.AppConfig;

public final class CallChecker {
    public static final class Result {
        public final ScreenDecision decision;
        public final boolean remote;
        public final String summary;
        Result(ScreenDecision d, boolean remote, String summary){this.decision=d;this.remote=remote;this.summary=summary;}
    }

    private CallChecker(){}

    public static Result check(Context context,String number){
        boolean known=ContactLookup.isKnown(context,number);
        if(AppConfig.isUsableContactCenter(context)){
            try{
                ScreenDecision d=new ApiClient(context).lookupIncoming(number,known);
                String why=d.reason==null||d.reason.isEmpty()?"Contact center checked this caller":d.reason;
                return new Result(d,true,"Contact center: "+why);
            }catch(Throwable ignored){}
        }
        ScreenDecision local=localDecision(number,known);
        String why=known?"Saved contact — local checker allows the call":"Unknown caller — local checker recommends screening";
        if(AppConfig.isEmulatorPlaceholder(context))why+=". Replace 10.0.2.2 with your real API address.";
        else if(!AppConfig.isUsableContactCenter(context))why+=". Your contact-center API is not configured yet.";
        else why+=". Contact center was unreachable, so NetWatch failed over locally.";
        return new Result(local,false,why);
    }

    private static ScreenDecision localDecision(String number,boolean known){
        if(known)return new ScreenDecision(ScreenDecision.Action.ALLOW,"","saved_contact","");
        String n=number==null?"":number.trim();
        if(n.isEmpty()||n.equalsIgnoreCase("unknown")||n.equalsIgnoreCase("private"))
            return new ScreenDecision(ScreenDecision.Action.SCREEN,"Unknown Caller","private_or_unavailable","Hi. NetWatch is screening this call. What are you calling about?");
        return new ScreenDecision(ScreenDecision.Action.SCREEN,"","not_in_contacts","Hi. NetWatch is screening this call. What are you calling about?");
    }
}
