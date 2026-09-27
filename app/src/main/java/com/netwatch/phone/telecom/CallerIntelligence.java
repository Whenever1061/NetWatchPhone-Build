package com.netwatch.phone.telecom;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CallLog;
import com.netwatch.phone.api.ApiClient;
import com.netwatch.phone.config.AppConfig;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

public final class CallerIntelligence {
    private static final Map<String,String> AREA=new HashMap<>();
    static{
        AREA.put("505","Albuquerque / Santa Fe, New Mexico");
        AREA.put("575","New Mexico outside the 505 region");
        AREA.put("915","El Paso, Texas");
        AREA.put("520","Tucson / southern Arizona");
        AREA.put("480","Mesa / Scottsdale, Arizona");
        AREA.put("602","Phoenix, Arizona");
        AREA.put("623","Phoenix west valley, Arizona");
        AREA.put("303","Denver, Colorado"); AREA.put("720","Denver metro, Colorado"); AREA.put("983","Denver metro, Colorado");
        AREA.put("314","St. Louis, Missouri"); AREA.put("636","St. Louis region, Missouri");
        AREA.put("612","Minneapolis, Minnesota"); AREA.put("651","St. Paul, Minnesota"); AREA.put("763","Twin Cities, Minnesota"); AREA.put("952","Twin Cities, Minnesota");
        AREA.put("916","Sacramento, California"); AREA.put("279","Sacramento, California");
        AREA.put("415","San Francisco, California"); AREA.put("628","San Francisco, California");
        AREA.put("408","San Jose, California"); AREA.put("669","San Jose, California");
        AREA.put("213","Los Angeles, California"); AREA.put("323","Los Angeles, California");
        AREA.put("206","Seattle, Washington"); AREA.put("425","Seattle eastside, Washington");
        AREA.put("702","Las Vegas, Nevada"); AREA.put("725","Las Vegas, Nevada");
        AREA.put("405","Oklahoma City, Oklahoma"); AREA.put("918","Tulsa, Oklahoma");
        AREA.put("210","San Antonio, Texas"); AREA.put("512","Austin, Texas"); AREA.put("737","Austin, Texas");
        AREA.put("214","Dallas, Texas"); AREA.put("469","Dallas, Texas"); AREA.put("972","Dallas, Texas");
        AREA.put("713","Houston, Texas"); AREA.put("281","Houston, Texas"); AREA.put("832","Houston, Texas");
    }

    private CallerIntelligence(){}

    public static CallerIntel identify(Context context,String raw){
        String number=raw==null?"":raw.trim();
        String saved=ContactLookup.findName(context,number);
        if(!saved.isEmpty()){
            return new CallerIntel(number,saved,localLocation(number),"","Saved contact","Low",100,
                    "Phone book","Saved contact match. No outside identity lookup was needed.",true);
        }

        String location=localLocation(number);
        int history=historyCount(context,number);
        int confidence=location.isEmpty()?18:38;
        String risk=history>=3?"Known repeat":"Unknown";
        String source=history>=3?"Local call history + numbering plan":"Local numbering plan";
        String aiSummary=history>=3
                ?"This number has appeared in your local call history before. NetWatch has no verified personal identity yet."
                :"NetWatch has not verified this caller's personal identity. Treat the location as numbering-plan information, not proof of where the caller is physically located.";

        if(AppConfig.isUsableContactCenter(context)){
            try{
                JSONObject j=new ApiClient(context).lookupCallerIntel(number);
                String name=j.optString("display_name","");
                String remoteLocation=j.optString("location","");
                String carrier=j.optString("carrier","");
                String lineType=j.optString("line_type","");
                String remoteRisk=j.optString("risk","");
                String remoteSource=j.optString("source","NetWatch contact center");
                String remoteSummary=j.optString("ai_summary","");
                int remoteConfidence=j.optInt("confidence",0);
                if(!remoteLocation.isEmpty())location=remoteLocation;
                if(!remoteRisk.isEmpty())risk=remoteRisk;
                if(!remoteSummary.isEmpty())aiSummary=remoteSummary;
                if(remoteConfidence>0)confidence=remoteConfidence;else confidence=Math.max(confidence,55);
                return new CallerIntel(number,name,location,carrier,lineType,risk,confidence,remoteSource,aiSummary,false);
            }catch(Throwable ignored){
                source+=" • contact center offline";
                aiSummary+=" Your contact center was unreachable, so this result is local-only.";
            }
        }else{
            source+=" • local mode";
            aiSummary+=" Configure your contact center to add online carrier/registration checks and optional local AI.";
        }
        return new CallerIntel(number,"",location,"","",risk,confidence,source,aiSummary,false);
    }

    public static String localLocation(String raw){
        String n=digits(raw);
        if(n.startsWith("1")&&n.length()>=11)n=n.substring(1);
        if(n.length()<10)return "";
        String area=n.substring(0,3);
        String known=AREA.get(area);
        return known==null?"North American area code "+area:known;
    }

    private static int historyCount(Context c,String raw){
        if(raw==null||raw.isEmpty())return 0;
        if(c.checkSelfPermission(Manifest.permission.READ_CALL_LOG)!=PackageManager.PERMISSION_GRANTED)return 0;
        int total=0;String normalized=digits(raw);
        if(normalized.isEmpty())return 0;
        try(Cursor cur=c.getContentResolver().query(CallLog.Calls.CONTENT_URI,new String[]{CallLog.Calls.NUMBER},null,null,CallLog.Calls.DATE+" DESC")){
            if(cur==null)return 0;int col=cur.getColumnIndexOrThrow(CallLog.Calls.NUMBER);
            while(cur.moveToNext()&&total<8){String candidate=digits(cur.getString(col));if(!candidate.isEmpty()&&(candidate.endsWith(normalized)||normalized.endsWith(candidate)))total++;}
        }catch(Throwable ignored){}
        return total;
    }

    private static String digits(String s){if(s==null)return "";return s.replaceAll("[^0-9]","");}
}
