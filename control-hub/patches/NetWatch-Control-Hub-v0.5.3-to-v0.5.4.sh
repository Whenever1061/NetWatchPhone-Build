#!/usr/bin/env bash
set -euo pipefail
NETWATCH_CONTROL_HUB_PATCH=1
TARGET_VERSION=0.5.4
SOURCE_VERSION=0.5.3
TARGET="${NETWATCH_INSTALLED_DIR:-${1:-$HOME/.local/share/netwatch-system-updater}}"

[[ -f "$TARGET/package.json" ]] || { echo "NetWatch Control Hub not found: $TARGET"; exit 2; }
CURRENT="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["version"])' "$TARGET/package.json")"
[[ "$CURRENT" == "$TARGET_VERSION" ]] && exit 0
[[ "$CURRENT" == "$SOURCE_VERSION" ]] || { echo "Expected v$SOURCE_VERSION, found v$CURRENT"; exit 3; }

STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="$TARGET/.netwatch_patch_backups/$STAMP-v$CURRENT"
mkdir -p "$BACKUP/src"
for f in package.json src/index.html src/main.js src/preload.js src/renderer.js src/self-updater.js src/styles.css; do
  [[ -f "$TARGET/$f" ]] && cp -a "$TARGET/$f" "$BACKUP/$f"
done

cat > "$TARGET/src/weather-service.js" <<'JS'
const https = require('https');
const CACHE_MS = 10 * 60 * 1000;
const DEFAULT_LOCATION = { latitude: 35.0844, longitude: -106.6504, name: 'Albuquerque' };
let cache = null, cacheAt = 0;
function num(name, fallback){ const v=Number(process.env[name]); return Number.isFinite(v)?v:fallback; }
function requestJson(url){ return new Promise((resolve,reject)=>{ const req=https.get(url,{headers:{'User-Agent':'NetWatch-Control-Hub-Weather/0.5.4'}},res=>{ if(res.statusCode!==200){res.resume();return reject(new Error(`Weather service returned HTTP ${res.statusCode}`));} const chunks=[];res.on('data',c=>chunks.push(c));res.on('end',()=>{try{resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')))}catch{reject(new Error('Weather service returned invalid data'))}});}); req.setTimeout(12000,()=>req.destroy(new Error('Weather request timed out')));req.on('error',reject);}); }
function text(c){if(c===0)return'Clear';if([1,2].includes(c))return'Partly cloudy';if(c===3)return'Cloudy';if([45,48].includes(c))return'Fog';if([51,53,55,56,57].includes(c))return'Drizzle';if([61,63,65,66,67,80,81,82].includes(c))return'Rain';if([71,73,75,77,85,86].includes(c))return'Snow';if([95,96,99].includes(c))return'Thunderstorms';return'Weather';}
function icon(c,d){if(c===0)return d?'☀':'☾';if([1,2].includes(c))return d?'🌤':'☁';if(c===3)return'☁';if([45,48].includes(c))return'≋';if([51,53,55,56,57].includes(c))return'🌦';if([61,63,65,66,67,80,81,82].includes(c))return'🌧';if([71,73,75,77,85,86].includes(c))return'❄';if([95,96,99].includes(c))return'⛈';return'◌';}
async function getWeather({force=false}={}){ if(!force&&cache&&Date.now()-cacheAt<CACHE_MS)return cache; const latitude=num('NETWATCH_WEATHER_LAT',DEFAULT_LOCATION.latitude),longitude=num('NETWATCH_WEATHER_LON',DEFAULT_LOCATION.longitude),location=String(process.env.NETWATCH_WEATHER_NAME||DEFAULT_LOCATION.name).trim()||DEFAULT_LOCATION.name; const p=new URLSearchParams({latitude:String(latitude),longitude:String(longitude),current:'temperature_2m,apparent_temperature,is_day,precipitation,weather_code,cloud_cover,wind_speed_10m',temperature_unit:'fahrenheit',wind_speed_unit:'mph',precipitation_unit:'inch',timezone:'auto'}); const d=await requestJson(`https://api.open-meteo.com/v1/forecast?${p}`),c=d.current||{},code=Number(c.weather_code); cache={location,temperature:Number(c.temperature_2m),feelsLike:Number(c.apparent_temperature),windMph:Number(c.wind_speed_10m),condition:text(code),icon:icon(code,Number(c.is_day)===1),code,fetchedAt:new Date().toISOString(),source:'Open-Meteo'};cacheAt=Date.now();return cache; }
module.exports={getWeather};
JS

python3 - "$TARGET" <<'PY'
from pathlib import Path
import json,sys
root=Path(sys.argv[1])
p=root/'package.json'; d=json.loads(p.read_text()); d['version']='0.5.4'; d['description']='NetWatch Crystal Glass Control Hub — transparent system command center with live pulse, GitHub self-updater, and local weather'; p.write_text(json.dumps(d,indent=2)+'\n')
p=root/'src/index.html'; s=p.read_text().replace('data-netwatch-ui="0.5.3-crystal-updater"','data-netwatch-ui="0.5.4-weather-github"').replace('<strong>NetWatch v0.5.3</strong>','<strong>NetWatch v0.5.4</strong>')
needle='<div class="top-status">\n          <span class="top-health">'
insert='<div class="top-status">\n          <button class="top-weather" id="topWeather" type="button" title="Refresh local weather"><span class="top-weather-icon" id="topWeatherIcon">◌</span><span class="top-weather-copy"><b id="topWeatherTemp">--°</b><small id="topWeatherPlace">Albuquerque</small></span></button>\n          <span class="top-health">'
if 'id="topWeather"' not in s: s=s.replace(needle,insert)
p.write_text(s)
p=root/'src/main.js'; s=p.read_text()
if 'require("./weather-service")' not in s: s=s.replace('const { SelfUpdater } = require("./self-updater");','const { SelfUpdater } = require("./self-updater");\nconst { getWeather } = require("./weather-service");')
if '"weather:get"' not in s: s=s.replace('  ipcMain.handle("system:snapshot", async () => getSystemSnapshot());','  ipcMain.handle("weather:get", async (_event, force) => getWeather({ force: Boolean(force) }));\n  ipcMain.handle("system:snapshot", async () => getSystemSnapshot());')
p.write_text(s)
p=root/'src/preload.js'; s=p.read_text(); needle='contextBridge.exposeInMainWorld("systemUpdater", {'
if 'getWeather:' not in s: s=s.replace(needle,needle+'\n  getWeather: (force = false) => ipcRenderer.invoke("weather:get", Boolean(force)),',1)
p.write_text(s)
p=root/'src/renderer.js'; s=p.read_text().replace('document.documentElement.dataset.netwatchUi = "0.5.3-crystal-updater";','document.documentElement.dataset.netwatchUi = "0.5.4-weather-github";').replace('// ===== NETWATCH SELF UPDATER v0.5.3 =====','// ===== NETWATCH SELF UPDATER v0.5.4 =====')
if 'NETWATCH 0.5.4 TOP WEATHER' not in s:
  weather='''\n// ===== NETWATCH 0.5.4 TOP WEATHER =====\nlet weatherTimer = null;\nfunction renderTopWeather(w){const p=$("#topWeather");if(!p||!w)return;$("#topWeatherIcon")&&($("#topWeatherIcon").textContent=w.icon||"◌");$("#topWeatherTemp")&&($("#topWeatherTemp").textContent=w.temperature==null?"--°":`${Math.round(w.temperature)}°`);$("#topWeatherPlace")&&($("#topWeatherPlace").textContent=w.location||"Local weather");p.title=[w.condition,w.feelsLike==null?null:`Feels like ${Math.round(w.feelsLike)}°F`,w.windMph==null?null:`Wind ${Math.round(w.windMph)} mph`].filter(Boolean).join(" • ")||"Refresh local weather";p.classList.remove("weather-loading","weather-error");}\nasync function loadTopWeather(force=false){const p=$("#topWeather");if(!p||!api.getWeather)return;p.classList.add("weather-loading");try{renderTopWeather(await api.getWeather(force));}catch(e){p.classList.remove("weather-loading");p.classList.add("weather-error");$("#topWeatherIcon")&&($("#topWeatherIcon").textContent="!");p.title=`Weather unavailable: ${e.message}`;}}\ndocument.addEventListener("DOMContentLoaded",()=>{loadTopWeather(false);$("#topWeather")?.addEventListener("click",()=>loadTopWeather(true));weatherTimer=setInterval(()=>loadTopWeather(false),600000);});\nwindow.addEventListener("beforeunload",()=>{if(weatherTimer)clearInterval(weatherTimer);});\n// ===== END NETWATCH 0.5.4 TOP WEATHER =====\n'''
  s=s.replace('// ===== NETWATCH SELF UPDATER v0.5.4 =====',weather+'\n// ===== NETWATCH SELF UPDATER v0.5.4 =====',1)
p.write_text(s)
p=root/'src/styles.css'; s=p.read_text()
if 'NETWATCH 0.5.4 WEATHER + GITHUB CHANNEL' not in s:
 s+='''\n/* ===== NETWATCH 0.5.4 WEATHER + GITHUB CHANNEL ===== */\n.top-weather{appearance:none;border:1px solid rgba(230,248,255,.18);border-radius:15px;min-height:40px;padding:5px 10px;display:flex;align-items:center;gap:8px;color:#f5fbff;cursor:pointer;background:linear-gradient(145deg,rgba(255,255,255,.10),rgba(255,255,255,.025)),rgba(7,25,48,.12);box-shadow:inset 0 1px 0 rgba(255,255,255,.18),0 8px 26px rgba(0,8,24,.09);backdrop-filter:blur(18px) saturate(165%);-webkit-backdrop-filter:blur(18px) saturate(165%)}\n.top-weather:hover{background:rgba(255,255,255,.12);border-color:rgba(118,226,255,.34)}.top-weather-icon{font-size:22px;line-height:1}.top-weather-copy{display:grid;text-align:left;line-height:1.05}.top-weather-copy b{font-size:12px}.top-weather-copy small{font-size:8px!important;max-width:88px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.top-weather.weather-loading .top-weather-icon{animation:nwWeatherSpin 1.4s linear infinite}.top-weather.weather-error{border-color:rgba(255,173,100,.34)}@keyframes nwWeatherSpin{to{transform:rotate(360deg)}}@media(max-width:1120px){.top-weather-copy small{display:none}.top-health b{display:none}}@media(max-width:760px){.top-weather{display:none}}\n/* ===== END NETWATCH 0.5.4 ===== */\n'''
p.write_text(s)
p=root/'src/self-updater.js'; s=p.read_text(); marker="const execFileAsync = promisify(execFile);"
if 'DEFAULT_MANIFEST_URL' not in s: s=s.replace(marker,marker+"\n\nconst DEFAULT_MANIFEST_URL = 'https://raw.githubusercontent.com/Whenever1061/NetWatchPhone-Build/main/control-hub/update.json';").replace("manifestUrl: process.env.NETWATCH_CONTROL_HUB_UPDATE_URL || saved.manifestUrl || '',","manifestUrl: process.env.NETWATCH_CONTROL_HUB_UPDATE_URL || saved.manifestUrl || DEFAULT_MANIFEST_URL,")
p.write_text(s)
PY

node --check "$TARGET/src/main.js"
node --check "$TARGET/src/preload.js"
node --check "$TARGET/src/renderer.js"
node --check "$TARGET/src/self-updater.js"
node --check "$TARGET/src/weather-service.js"
echo "NetWatch Control Hub updated to v$TARGET_VERSION. Restart the app."
