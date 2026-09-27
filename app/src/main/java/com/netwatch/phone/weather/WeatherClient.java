package com.netwatch.phone.weather;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Albuquerque-only weather helper. It sends fixed city coordinates, never device precise location. */
public final class WeatherClient {
    private static final String PREFS="netwatch_weather";
    private static final String CACHE="snapshot";
    private static final String CACHE_TIME="snapshot_time";
    private static final long MAX_AGE=30L*60L*1000L;
    private static final String URL_TEXT=
        "https://api.open-meteo.com/v1/forecast?latitude=35.0844&longitude=-106.6504"
        +"&current=temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,wind_speed_10m"
        +"&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset"
        +"&temperature_unit=fahrenheit&wind_speed_unit=mph&precipitation_unit=inch&timezone=America%2FDenver&forecast_days=1";

    private WeatherClient(){}

    public static WeatherSnapshot load(Context context,boolean force){
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        long age=System.currentTimeMillis()-p.getLong(CACHE_TIME,0);
        String cached=p.getString(CACHE,"");
        if(!force&&!cached.isEmpty()&&age<MAX_AGE){WeatherSnapshot s=parse(cached);if(s.ok)return s;}
        try{
            HttpURLConnection c=(HttpURLConnection)new URL(URL_TEXT).openConnection();
            c.setConnectTimeout(5000);c.setReadTimeout(6000);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","NetWatchPhone/0.5");
            int status=c.getResponseCode();if(status<200||status>=300)throw new IllegalStateException("weather HTTP "+status);
            StringBuilder b=new StringBuilder();
            try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){for(String line;(line=r.readLine())!=null;)b.append(line);}finally{c.disconnect();}
            String raw=b.toString();WeatherSnapshot s=parse(raw);if(s.ok)p.edit().putString(CACHE,raw).putLong(CACHE_TIME,System.currentTimeMillis()).apply();return s;
        }catch(Throwable e){if(!cached.isEmpty()){WeatherSnapshot s=parse(cached);if(s.ok)return s;}return new WeatherSnapshot(false,0,0,0,0,0,0,0,-1,"","","Weather offline");}
    }

    private static WeatherSnapshot parse(String raw){
        try{
            JSONObject root=new JSONObject(raw),current=root.getJSONObject("current"),daily=root.getJSONObject("daily");
            double temp=current.optDouble("temperature_2m",0),feels=current.optDouble("apparent_temperature",temp),humidity=current.optDouble("relative_humidity_2m",0),wind=current.optDouble("wind_speed_10m",0);
            int code=current.optInt("weather_code",-1);
            double high=firstDouble(daily.optJSONArray("temperature_2m_max"),temp),low=firstDouble(daily.optJSONArray("temperature_2m_min"),temp),rain=firstDouble(daily.optJSONArray("precipitation_probability_max"),0);
            String sunrise=firstString(daily.optJSONArray("sunrise")),sunset=firstString(daily.optJSONArray("sunset"));
            return new WeatherSnapshot(true,temp,feels,high,low,humidity,wind,rain,code,sunrise,sunset,"");
        }catch(Throwable e){return new WeatherSnapshot(false,0,0,0,0,0,0,0,-1,"","","Weather unavailable");}
    }

    private static double firstDouble(JSONArray a,double fallback){return a!=null&&a.length()>0?a.optDouble(0,fallback):fallback;}
    private static String firstString(JSONArray a){return a!=null&&a.length()>0?a.optString(0,""):"";}
}
