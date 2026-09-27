#!/usr/bin/env python3
"""
NetWatch Phone contact-center reference service.

The phone talks only to this single service. This service can optionally enrich
callers with a phone-validation provider (API key held on your server, never in
the APK) and optionally ask your local Ollama instance for a short AI summary.

Environment:
  ABSTRACT_PHONE_API_KEY  Optional phone-validation provider key.
  NETWATCH_OLLAMA_URL     Optional local Ollama URL, e.g. http://127.0.0.1:11434
  NETWATCH_AI_MODEL       Optional Ollama model; default llama3.2:3b
"""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen
import json, os, re, time, uuid

HOST=os.getenv("NETWATCH_CONTACT_CENTER_HOST","0.0.0.0")
PORT=int(os.getenv("NETWATCH_CONTACT_CENTER_PORT","8767"))
DATA=Path(os.getenv("NETWATCH_CONTACT_CENTER_DATA","data"))
DATA.mkdir(parents=True,exist_ok=True)
CORRECTIONS=DATA/"caller_corrections.json"
ABSTRACT_KEY=os.getenv("ABSTRACT_PHONE_API_KEY","").strip()
OLLAMA_URL=os.getenv("NETWATCH_OLLAMA_URL","").rstrip("/")
OLLAMA_MODEL=os.getenv("NETWATCH_AI_MODEL","llama3.2:3b")

def digits(v): return re.sub(r"\D","",v or "")

def load_corrections():
    try: return json.loads(CORRECTIONS.read_text())
    except Exception: return {}

def save_corrections(d):
    tmp=CORRECTIONS.with_suffix(".tmp")
    tmp.write_text(json.dumps(d,indent=2,sort_keys=True))
    tmp.replace(CORRECTIONS)

def abstract_lookup(number):
    if not ABSTRACT_KEY: return {}
    q=urlencode({"api_key":ABSTRACT_KEY,"phone":number})
    req=Request("https://phonevalidation.abstractapi.com/v1/?"+q,
                headers={"User-Agent":"NetWatchContactCenter/0.6"})
    with urlopen(req,timeout=4) as r:
        return json.loads(r.read().decode("utf-8"))

def local_area(number):
    n=digits(number)
    if len(n)==11 and n.startswith("1"): n=n[1:]
    if len(n)<10: return ""
    a=n[:3]
    known={
      "505":"Albuquerque / Santa Fe, New Mexico","575":"New Mexico outside the 505 region",
      "915":"El Paso, Texas","520":"Tucson / southern Arizona","480":"Mesa / Scottsdale, Arizona",
      "602":"Phoenix, Arizona","303":"Denver, Colorado","720":"Denver metro, Colorado",
      "314":"St. Louis, Missouri","612":"Minneapolis, Minnesota","651":"St. Paul, Minnesota",
      "916":"Sacramento, California","415":"San Francisco, California","206":"Seattle, Washington"
    }
    return known.get(a,f"North American area code {a}")

def text_value(value):
    if isinstance(value,str): return value
    if isinstance(value,dict):
        for key in ("name","value","company"):
            if isinstance(value.get(key),str): return value[key]
    return ""

def ai_summary(info):
    default=(
      f"NetWatch found {info.get('location') or 'no confirmed registration location'}. "
      f"Carrier: {info.get('carrier') or 'unknown'}. Line type: {info.get('line_type') or 'unknown'}. "
      f"Risk: {info.get('risk') or 'unknown'}. "
      "Location is registration/numbering information and does not prove the caller's current physical location."
    )
    if not OLLAMA_URL: return default
    prompt=(
      "You are NetWatch Phone's caller-safety assistant. Give a concise, neutral two-sentence summary. "
      "Do not claim a person's identity unless display_name is present. Do not claim registered location is live physical location. "
      f"Data: {json.dumps(info,ensure_ascii=False)}"
    )
    body=json.dumps({"model":OLLAMA_MODEL,"prompt":prompt,"stream":False}).encode()
    req=Request(OLLAMA_URL+"/api/generate",data=body,headers={"Content-Type":"application/json"})
    try:
        with urlopen(req,timeout=7) as r:
            data=json.loads(r.read().decode())
            return (data.get("response") or default).strip()
    except Exception:
        return default

def caller_intel(number):
    corrections=load_corrections()
    key=digits(number)
    display=corrections.get(key,"")
    info={
      "display_name":display,
      "location":local_area(number),
      "carrier":"",
      "line_type":"",
      "risk":"Unknown",
      "confidence":92 if display else 35,
      "source":"NetWatch correction book" if display else "Local numbering plan"
    }
    if ABSTRACT_KEY:
        try:
            a=abstract_lookup(number)
            location=text_value(a.get("registered_location")) or text_value(a.get("location"))
            carrier=text_value(a.get("carrier"))
            line_type=text_value(a.get("line_type"))
            if location: info["location"]=location
            if carrier: info["carrier"]=carrier
            if line_type: info["line_type"]=line_type
            score=a.get("risk_score")
            if isinstance(score,(int,float)):
                info["risk"]="High" if score>=0.7 else "Medium" if score>=0.35 else "Low"
            if a.get("valid") is True: info["confidence"]=max(info["confidence"],65)
            info["source"]=("NetWatch correction book + " if display else "")+"phone validation provider"
        except Exception:
            info["source"]+=" • online lookup unavailable"
    info["ai_summary"]=ai_summary(info)
    return info

class Handler(BaseHTTPRequestHandler):
    server_version="NetWatchContactCenter/0.6"
    def log_message(self,fmt,*args):
        print(time.strftime("%Y-%m-%d %H:%M:%S"),fmt%args)

    def _json(self,code,payload):
        data=json.dumps(payload).encode()
        self.send_response(code);self.send_header("Content-Type","application/json")
        self.send_header("Content-Length",str(len(data)));self.end_headers();self.wfile.write(data)

    def _body(self):
        n=int(self.headers.get("Content-Length","0") or 0)
        return json.loads(self.rfile.read(n).decode() or "{}") if n else {}

    def do_GET(self):
        if self.path=="/health":
            self._json(200,{"ok":True,"version":"0.6","online_lookup":bool(ABSTRACT_KEY),"local_ai":bool(OLLAMA_URL)})
        else:self._json(404,{"error":"not_found"})

    def do_POST(self):
        try: body=self._body()
        except Exception:
            self._json(400,{"error":"bad_json"});return

        if self.path=="/v1/caller/intel":
            self._json(200,caller_intel(body.get("number","")));return

        if self.path=="/v1/caller/correct":
            number=digits(body.get("number",""))
            name=(body.get("name") or body.get("display_name") or "").strip()
            if not number or not name:self._json(400,{"error":"number_and_name_required"});return
            d=load_corrections();d[number]=name;save_corrections(d)
            self._json(200,{"ok":True,"number":number,"display_name":name});return

        if self.path=="/v1/calls/incoming":
            number=body.get("number","");in_contacts=bool(body.get("in_contacts"))
            intel=caller_intel(number)
            if in_contacts or intel.get("display_name"):
                action="ALLOW";reason="known_or_corrected_contact"
            else:
                action="SCREEN";reason="unknown_caller"
            self._json(200,{"action":action,"display_name":intel.get("display_name",""),"reason":reason,
                            "greeting":"Hi. NetWatch is screening this call. What are you calling about?"});return

        if self.path=="/v1/screen/start":
            self._json(200,{"ok":True,"session_id":str(uuid.uuid4()),"state":"ready",
                            "note":"Caller intelligence is ready. Two-way call audio requires the privileged device audio bridge."});return

        if self.path=="/v1/calls/event":
            self._json(200,{"ok":True});return

        self._json(404,{"error":"not_found"})

if __name__=="__main__":
    print(f"NetWatch contact center listening on http://{HOST}:{PORT}")
    print("Online phone lookup:", "enabled" if ABSTRACT_KEY else "disabled (set ABSTRACT_PHONE_API_KEY)")
    print("Local AI summary:", "enabled" if OLLAMA_URL else "disabled (set NETWATCH_OLLAMA_URL for Ollama)")
    ThreadingHTTPServer((HOST,PORT),Handler).serve_forever()
