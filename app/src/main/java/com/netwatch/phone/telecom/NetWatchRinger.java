package com.netwatch.phone.telecom;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/** Local synthesized ringtone pack. No cloud audio or media files are required. */
public final class NetWatchRinger {
    private static final String PREFS="netwatch_ringtone_pack";
    private static final String KEY_SELECTED="selected";
    private static final String[] NAMES={
            "Clear Horizon",
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
            final int sr=32000;final double seconds=6.0;final int count=(int)(sr*seconds);short[] pcm=new short[count];
            for(int i=0;i<count;i++){double t=i/(double)sr,sample=sample(style,t);if(t>5.72)sample*=Math.max(0,(6.0-t)/.28);sample=Math.max(-.72,Math.min(.72,sample));pcm[i]=(short)(sample*32767);}
            AudioTrack a=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(pcm.length*2).setTransferMode(AudioTrack.MODE_STATIC).build();
            a.write(pcm,0,pcm.length);if(loop)a.setLoopPoints(0,pcm.length,-1);a.setVolume(.76f);a.play();track=a;
        }catch(Throwable error){stop();}
    }

    private static double sample(int style,double t){switch(style){case 1:return sandiaDawn(t);case 2:return nightWatch(t);case 3:return mesaPulse(t);case 4:return secureLine(t);default:return clearHorizon(t);}}

    /** Clean, recognizable two-note phone cadence with no rumble or harsh overtones. */
    private static double clearHorizon(double t){
        double cycle=t%2.15,s=0;
        if(cycle<.46)s+=tone(659.25,cycle,.46,.34);
        if(cycle>.62&&cycle<1.10)s+=tone(783.99,cycle-.62,.48,.31);
        if(cycle>1.24&&cycle<1.70)s+=tone(659.25,cycle-1.24,.46,.27);
        return s;
    }

    private static double sandiaDawn(double t){double[] notes={523.25,659.25,783.99,1046.50,783.99};double[] starts={0.0,.72,1.44,2.16,3.45};return bellPhrase(t,notes,starts,1.35,.19);}
    private static double nightWatch(double t){double[] notes={329.63,440.00,523.25,440.00};double[] starts={0.0,1.25,2.50,3.75};return bellPhrase(t,notes,starts,1.65,.18);}
    private static double mesaPulse(double t){double beat=t%1.0,env=Math.exp(-beat*8.5),s=.18*env*Math.sin(2*Math.PI*196.00*beat);if((t%.5)<.13)s+=.08*Math.exp(-(t%.5)*14.0)*Math.sin(2*Math.PI*392.00*(t%.5));return s+bellPhrase(t,new double[]{523.25,659.25,523.25},new double[]{.35,2.35,4.35},1.0,.12);}
    private static double secureLine(double t){double cycle=t%1.7,s=0;if(cycle<.30)s+=tone(880.00,cycle,.30,.24);if(cycle>.56&&cycle<.90)s+=tone(659.25,cycle-.56,.34,.20);return s;}
    private static double tone(double f,double dt,double length,double volume){if(dt<0||dt>length)return 0;double edge=Math.sin(Math.PI*Math.min(1,dt/.05))*Math.sin(Math.PI*Math.min(1,(length-dt)/.06));return volume*edge*Math.sin(2*Math.PI*f*dt);}
    private static double bellPhrase(double t,double[] notes,double[] starts,double length,double volume){double sample=0;for(int n=0;n<notes.length;n++){double dt=t-starts[n];if(dt>=0&&dt<length){double attack=Math.min(dt/.025,1.0),env=attack*Math.exp(-dt/(length*.50)),f=notes[n];sample+=env*(Math.sin(2*Math.PI*f*dt)+.13*Math.sin(2*Math.PI*f*2.0*dt))*volume;}}return sample;}

    public static synchronized void stop(){try{if(track!=null){track.pause();track.flush();track.release();}}catch(Throwable ignored){}track=null;}
}
