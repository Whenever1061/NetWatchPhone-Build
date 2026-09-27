package com.netwatch.phone.telecom;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

public final class NetWatchRinger {
    private static AudioTrack track;
    private NetWatchRinger(){}

    public static synchronized void start(android.content.Context context){
        if(track!=null&&track.getPlayState()==AudioTrack.PLAYSTATE_PLAYING)return;
        stop();
        try{
            final int sr=22050;
            final double seconds=6.0;
            final int count=(int)(sr*seconds);
            short[] pcm=new short[count];
            double[] notes={659.25,783.99,987.77,783.99};
            double[] starts={0.0,1.35,2.70,4.05};
            for(int i=0;i<count;i++){
                double t=i/(double)sr;
                double sample=0.0;
                for(int n=0;n<notes.length;n++){
                    double dt=t-starts[n];
                    if(dt>=0&&dt<1.9){
                        double attack=Math.min(dt/.025,1.0);
                        double env=attack*Math.exp(-dt/1.0);
                        double f=notes[n];
                        sample+=env*(Math.sin(2*Math.PI*f*dt)+.30*Math.sin(2*Math.PI*f*2.01*dt)+.12*Math.sin(2*Math.PI*f*3.99*dt))*.27;
                    }
                }
                if((t>=0&&t<2.8)||(t>=2.7&&t<5.5)){
                    double dt=t>=2.7?t-2.7:t;
                    sample+=.055*Math.exp(-dt/1.7)*Math.sin(2*Math.PI*164.81*dt);
                }
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
            a.setLoopPoints(0,pcm.length,-1);
            a.setVolume(.82f);
            a.play();
            track=a;
        }catch(Throwable error){stop();}
    }

    public static synchronized void stop(){
        try{if(track!=null){track.pause();track.flush();track.release();}}catch(Throwable ignored){}
        track=null;
    }
}
