package com.netwatch.phone.api;

import android.content.Context;
import com.netwatch.phone.BuildConfig;
import com.netwatch.phone.config.AppConfig;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class ApiClient {
    private final String baseUrl;
    private final String allowedHost;

    public ApiClient(Context context){
        if(!AppConfig.isUsableContactCenter(context))throw new IllegalStateException("Contact center is not configured for this phone");
        baseUrl=AppConfig.getContactCenterUrl(context.getApplicationContext());
        allowedHost=URI.create(baseUrl).getHost();
        if(allowedHost==null)throw new IllegalStateException("Contact-center URL has no host");
    }

    public ScreenDecision lookupIncoming(String number,boolean inContacts)throws Exception{
        JSONObject request=new JSONObject();
        request.put("number",number==null?"":number);
        request.put("in_contacts",inContacts);
        request.put("client","netwatch-phone");
        request.put("client_version",BuildConfig.VERSION_NAME);
        JSONObject response=post("/v1/calls/incoming",request,2600);
        ScreenDecision.Action action;
        try{action=ScreenDecision.Action.valueOf(response.optString("action","ALLOW").toUpperCase());}
        catch(IllegalArgumentException ex){action=ScreenDecision.Action.ALLOW;}
        return new ScreenDecision(action,response.optString("display_name",""),response.optString("reason",""),response.optString("greeting",""));
    }

    public JSONObject postCallEvent(String event,String number)throws Exception{
        JSONObject body=new JSONObject();
        body.put("event",event);body.put("number",number==null?"":number);body.put("client_version",BuildConfig.VERSION_NAME);
        return post("/v1/calls/event",body,2200);
    }

    public JSONObject startScreen(String number)throws Exception{
        JSONObject body=new JSONObject();
        body.put("number",number==null?"":number);body.put("client_version",BuildConfig.VERSION_NAME);
        return post("/v1/screen/start",body,2800);
    }

    public JSONObject health()throws Exception{return get("/health",1800);}

    private JSONObject get(String path,int timeoutMs)throws Exception{
        URL url=checked(path);
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        conn.setConnectTimeout(timeoutMs);conn.setReadTimeout(timeoutMs);conn.setRequestProperty("Accept","application/json");
        return finish(conn);
    }

    private JSONObject post(String path,JSONObject body,int timeoutMs)throws Exception{
        URL url=checked(path);
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        conn.setRequestMethod("POST");conn.setConnectTimeout(timeoutMs);conn.setReadTimeout(timeoutMs);conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type","application/json; charset=utf-8");conn.setRequestProperty("Accept","application/json");
        byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);conn.setFixedLengthStreamingMode(bytes.length);
        try(OutputStream out=conn.getOutputStream()){out.write(bytes);}
        return finish(conn);
    }

    private URL checked(String path)throws Exception{
        URL url=new URL(baseUrl+path);
        if(!allowedHost.equalsIgnoreCase(url.getHost()))throw new SecurityException("Blocked network host: "+url.getHost());
        return url;
    }

    private JSONObject finish(HttpURLConnection conn)throws Exception{
        int status=conn.getResponseCode();
        InputStream stream=status>=200&&status<300?conn.getInputStream():conn.getErrorStream();
        StringBuilder text=new StringBuilder();
        if(stream!=null)try(BufferedReader r=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){for(String line;(line=r.readLine())!=null;)text.append(line);}
        conn.disconnect();
        if(status<200||status>=300)throw new IllegalStateException("API HTTP "+status+": "+text);
        return text.length()==0?new JSONObject():new JSONObject(text.toString());
    }
}
