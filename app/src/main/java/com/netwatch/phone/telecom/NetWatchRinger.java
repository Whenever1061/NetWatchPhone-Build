package com.netwatch.phone.telecom;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/** Local synthesized ringtone pack. No cloud audio or copyrighted vendor tones are required. */
public final class NetWatchRinger {
    private static final String PREFS="netwatch_ringtone_pack";
    private static final String KEY_SELECTED="selected";
    private static final String[] NAMES={
            "Open Horizon",
            "Sandia Dawn",
            "Night Watch",
            "Mesa Pulse",
            "Secure Line"
    };
    private static AudioTrack track;
    private NetWatchRinger(){}

    public static String[] names(){return NAMES.clone();}
    public static int selected(Context context){int i=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getInt(KEY_SELECTED,0);return Math.max(0,Math.min(NAMES.length-1,i));}
    public static String selectedName(Context context){return NAMES[selected(context)];}
    public static void select(Context context,int index){int i=Math.max(0,Math.min(NAMES.length-1,index));context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putInt(KEY_SELECTED,i).apply();}
    public static synchronized void preview(Context context,int index){stop();play(context,Math.max(0,Math.min(NAMES.length-1,index)),false);}
    public static synchronized void start(Context context){if(track!=null&&track.getPlayState()==AudioTrack.PLAYSTATE_PLAYING)return;stop();play(context,selected(context),true);}

    private static void play(Context context,int style,boolean loop){
        try{
            final int sr=44100;
            final double seconds=8.0;
            final int count=(int)(sr*seconds);
            short[] pcm=new short[count];
            for(int i=0;i<count;i++){
                double t=i/(double)sr;
                double sample=sample(style,t);
                if(t>7.72)sample*=Math.max(0,(8.0-t)/.28);
                sample=Math.max(-.92,Math.min(.92,sample));
                pcm[i]=(short)(sample*32767);
            }

            AudioAttributes attrs=new AudioAttributes.Builder()
                    .setLegacyStreamType(loop?AudioManager.STREAM_RING:AudioManager.STREAM_MUSIC)
                    .build();
            AudioTrack a=new AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sr)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(pcm.length*2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            int written=a.write(pcm,0,pcm.length);
            if(written<=0)throw new IllegalStateException("AudioTrack write failed");
            if(loop)a.setLoopPoints(0,pcm.length,-1);
            a.setVolume(1.0f);
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
            default:return openHorizon(t);
        }
    }

    /** Original NetWatch melody: bright, clean and spacious without copying a commercial ringtone. */
    private static double openHorizon(double t){
        double cycle=t%4.0;
        double s=0;
        s+=noteWindow(cycle,0.00,.58,659.25,.42);
        s+=noteWindow(cycle,.48,.72,783.99,.38);
        s+=noteWindow(cycle,1.08,.76,987.77,.36);
        s+=noteWindow(cycle,1.82,.68,783.99,.33);
        s+=noteWindow(cycle,2.52,.70,1046.50,.34);
        s+=noteWindow(cycle,3.18,.68,783.99,.29);
        return s;
    }

    private static double sandiaDawn(double t){double cycle=t%4.2,s=0;s+=noteWindow(cycle,0,.62,523.25,.36);s+=noteWindow(cycle,.55,.68,659.25,.34);s+=noteWindow(cycle,1.18,.70,783.99,.32);s+=noteWindow(cycle,1.86,.80,1046.50,.30);s+=noteWindow(cycle,2.78,.90,783.99,.27);return s;}
    private static double nightWatch(double t){double cycle=t%4.5,s=0;s+=noteWindow(cycle,0,.90,329.63,.31);s+=noteWindow(cycle,1.10,.90,440.00,.28);s+=noteWindow(cycle,2.20,.96,523.25,.27);s+=noteWindow(cycle,3.30,.90,440.00,.25);return s;}
    private static double mesaPulse(double t){double beat=t%.75,env=Math.exp(-beat*8.0),s=.22*env*Math.sin(2*Math.PI*220.0*beat);double cycle=t%3.0;s+=noteWindow(cycle,.22,.48,523.25,.21);s+=noteWindow(cycle,1.12,.48,659.25,.20);s+=noteWindow(cycle,2.02,.48,783.99,.19);return s;}
    private static double secureLine(double t){double cycle=t%2.0,s=0;s+=noteWindow(cycle,0,.36,880.0,.31);s+=noteWindow(cycle,.58,.40,659.25,.27);s+=noteWindow(cycle,1.16,.34,880.0,.25);return s;}

    private static double noteWindow(double t,double start,double length,double f,double volume){
        double dt=t-start;if(dt<0||dt>length)return 0;
        double attack=Math.min(1,dt/.025),release=Math.min(1,(length-dt)/.09),env=attack*release;
        double fundamental=Math.sin(2*Math.PI*f*dt);
        double shimmer=.18*Math.sin(2*Math.PI*f*2.0*dt)+.07*Math.sin(2*Math.PI*f*3.0*dt);
        return volume*env*(fundamental+shimmer);
    }

    public static synchronized void stop(){try{if(track!=null){track.stop();track.flush();track.release();}}catch(Throwable ignored){}track=null;}
}
