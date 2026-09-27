package com.netwatch.phone.telecom;

public final class CallerIntel {
    public final String number;
    public final String displayName;
    public final String location;
    public final String carrier;
    public final String lineType;
    public final String risk;
    public final int confidence;
    public final String source;
    public final String aiSummary;
    public final boolean savedContact;

    public CallerIntel(String number,String displayName,String location,String carrier,String lineType,
                       String risk,int confidence,String source,String aiSummary,boolean savedContact){
        this.number=number==null?"":number;
        this.displayName=displayName==null?"":displayName;
        this.location=location==null?"":location;
        this.carrier=carrier==null?"":carrier;
        this.lineType=lineType==null?"":lineType;
        this.risk=risk==null?"Unknown":risk;
        this.confidence=Math.max(0,Math.min(100,confidence));
        this.source=source==null?"":source;
        this.aiSummary=aiSummary==null?"":aiSummary;
        this.savedContact=savedContact;
    }

    public String title(){
        if(!displayName.trim().isEmpty())return displayName;
        return "Unknown caller";
    }

    public String subtitle(){
        StringBuilder b=new StringBuilder();
        if(!location.isEmpty())b.append(location);
        if(!lineType.isEmpty()){if(b.length()>0)b.append(" • ");b.append(lineType);}
        if(!carrier.isEmpty()){if(b.length()>0)b.append(" • ");b.append(carrier);}
        return b.length()==0?"Caller identity is not yet verified":b.toString();
    }

    public String confidenceLine(){
        return "Confidence "+confidence+"% • Risk "+risk+(source.isEmpty()?"":" • "+source);
    }
}
