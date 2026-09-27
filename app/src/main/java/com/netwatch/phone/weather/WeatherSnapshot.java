package com.netwatch.phone.weather;

public final class WeatherSnapshot {
    public final boolean ok;
    public final double tempF, feelsF, highF, lowF, humidity, windMph, rainChance;
    public final int weatherCode;
    public final String sunrise, sunset, error;

    public WeatherSnapshot(boolean ok, double tempF, double feelsF, double highF, double lowF,
                           double humidity, double windMph, double rainChance, int weatherCode,
                           String sunrise, String sunset, String error) {
        this.ok=ok; this.tempF=tempF; this.feelsF=feelsF; this.highF=highF; this.lowF=lowF;
        this.humidity=humidity; this.windMph=windMph; this.rainChance=rainChance; this.weatherCode=weatherCode;
        this.sunrise=sunrise==null?"":sunrise; this.sunset=sunset==null?"":sunset; this.error=error==null?"":error;
    }

    public static WeatherSnapshot loading() {
        return new WeatherSnapshot(false,0,0,0,0,0,0,0,-1,"","","Loading weather…");
    }

    public String primaryLine() {
        if(!ok) return error.isEmpty() ? "Weather unavailable" : error;
        return Math.round(tempF)+"°F  •  "+condition();
    }

    public String detailLine() {
        if(!ok) return "City-only weather; no device location is sent.";
        return "Feels "+Math.round(feelsF)+"°  H "+Math.round(highF)+"°  L "+Math.round(lowF)+"°  •  Hum "+Math.round(humidity)+"%  •  Wind "+Math.round(windMph)+" mph  •  Rain "+Math.round(rainChance)+"%";
    }

    public String icon() {
        if(!ok) return "◌";
        if(weatherCode==0) return "☀";
        if(weatherCode<=3) return "☁";
        if(weatherCode==45||weatherCode==48) return "≋";
        if((weatherCode>=51&&weatherCode<=67)||(weatherCode>=80&&weatherCode<=82)) return "☂";
        if(weatherCode>=71&&weatherCode<=77) return "❄";
        if(weatherCode>=95) return "ϟ";
        return "☁";
    }

    public String condition() {
        if(weatherCode==0) return "Clear";
        if(weatherCode==1) return "Mostly clear";
        if(weatherCode==2) return "Partly cloudy";
        if(weatherCode==3) return "Cloudy";
        if(weatherCode==45||weatherCode==48) return "Fog";
        if(weatherCode>=51&&weatherCode<=57) return "Drizzle";
        if(weatherCode>=61&&weatherCode<=67) return "Rain";
        if(weatherCode>=71&&weatherCode<=77) return "Snow";
        if(weatherCode>=80&&weatherCode<=82) return "Showers";
        if(weatherCode>=95) return "Thunderstorms";
        return "Weather";
    }
}
