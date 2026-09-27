package com.netwatch.phone.telecom;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/** Local synthesized ringtone pack. No cloud audio or media files are required. */
public final class NetWatchRinger {
    private static final String PREFS="netwatch_ringtone_pack";
    private static final String KEY_SELECTED="selected";
    private static final String[] NAMES={
            "NetWatch Signal",
            "Sandia Dawn",
            "Night Watch",
            "Mesa Pulse",
            "Secure Line"
    };
    private static AudioTrack track;

    private NetWatchRinger(){}

    public static String[] names(){return NAMES.clone();}

    public static int selected(Context context){
        int i=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getInt(KEY_SELECTED,0);
        return Math.max(0,Math.min(NAMES.length-1,i));
    }

    public static String selectedName(Context context){return NAMES[selected(context)];}

    public static void select(Context context,int index){
        int i=Math.max(0,Math.min(NAMES.length-1,index));
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putInt(KEY_SELECTED,i).apply();
    }

    public static synchronized void preview(Context context,int index){
        stop();
        play(context,Math.max(0,Math.min(NAMES.length-1,index)),false);
    }

    public static synchronized void start(Context context){
        if(track!=null&&track.getPlayState()==AudioTrack.PLAYSTATE_PLAYING)return;
        stop();
        play(context,selected(context),true);
    }

    private static void play(Context context,int style,boolean loop){
        try{
            final int sr=22050;
            final double seconds=6.0;
            final int count=(int)(sr*seconds);
            short[] pcm=new short[count];
            for(int i=0;i<count;i++){
                double t=i/(double)sr;
                double sample=sample(style,t);
                if(t>5.65)sample*=Math.max(0,(6.0-t)/.35);
                sample=Math.max(-.82,Math.min(.82,sample));
                pcm[i]=(short)(sample*32767);
            }

            AudioTrack a=new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sr)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.length*2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            a.write(pcm,0,pcm.length);
            if(loop)a.setLoopPoints(0,pcm.length,-1);
            a.setVolume(.82f);
            a.play();
            track=a;
        }catch(Throwable error){stop();}
    }

    private static double sample(int style,double t){
        switch(style){
            case 1:return sandiaDawn(t);
            case 2:return nightWatch(t);
            case 3:return mesaPulse(t);
            case 4:return secureLine(t);
            default:return netWatchSignal(t);
        }
    }

    private static double netWatchSignal(double t){
        double[] notes={659.25,783.99,987.77,783.99};
        double[] starts={0.0,1.35,2.70,4.05};
        double s=bellPhrase(t,notes,starts,1.9,.27);
        double dt=t>=2.7?t-2.7:t;
        if((t>=0&&t<2.8)||(t>=2.7&&t<5.5))s+=.055*Math.exp(-dt/1.7)*Math.sin(2*Math.PI*164.81*dt);
        return s;
    }

    private static double sandiaDawn(double t){
        double[] notes={523.25,659.25,783.99,1046.50,783.99};
        double[] starts={0.0,.72,1.44,2.16,3.45};
        double s=bellPhrase(t,notes,starts,1.55,.24);
        s+=.045*Math.sin(2*Math.PI*130.81*t)*Math.exp(-Math.max(0,t-3.9)/1.5);
        return s;
    }

    private static double nightWatch(double t){
        double[] notes={293.66,440.00,587.33,440.00};
        double[] starts={0.0,1.25,2.50,3.75};
        double s=bellPhrase(t,notes,starts,2.1,.23);
        s+=.035*Math.sin(2*Math.PI*73.42*t)+.025*Math.sin(2*Math.PI*146.83*t);
        return s;
    }

    private static double mesaPulse(double t){
        double beat=t%1.0;
        double env=Math.exp(-beat*7.0);
        double s=.30*env*Math.sin(2*Math.PI*196.00*beat);
        if((t%.5)<.16)s+=.13*Math.exp(-(t%.5)*12.0)*Math.sin(2*Math.PI*392.00*(t%.5));
        s+=bellPhrase(t,new double[]{523.25,659.25,523.25},new double[]{.35,2.35,4.35},1.25,.18);
        return s;
    }

    private static double secureLine(double t){
        double cycle=t%1.5;
        double s=0;
        if(cycle<.34){double e=Math.sin(Math.PI*Math.min(1,cycle/.34));s+=.30*e*Math.sin(2*Math.PI*880.00*cycle);}
        if(cycle>.52&&cycle<.92){double x=cycle-.52,e=Math.sin(Math.PI*Math.min(1,x/.40));s+=.24*e*Math.sin(2*Math.PI*659.25*x);}
        s+=.025*Math.sin(2*Math.PI*110.00*t);
        return s;
    }

    private static double bellPhrase(double t,double[] notes,double[] starts,double length,double volume){
        double sample=0.0;
        for(int n=0;n<notes.length;n++){
            double dt=t-starts[n];
            if(dt>=0&&dt<length){
                double attack=Math.min(dt/.025,1.0);
                double env=attack*Math.exp(-dt/(length*.55));
                double f=notes[n];
                sample+=env*(Math.sin(2*Math.PI*f*dt)+.30*Math.sin(2*Math.PI*f*2.01*dt)+.12*Math.sin(2*Math.PI*f*3.99*dt))*volume;
            }
        }
        return sample;
    }

    public static synchronized void stop(){
        try{if(track!=null){track.pause();track.flush();track.release();}}catch(Throwable ignored){}
        track=null;
    }
}
