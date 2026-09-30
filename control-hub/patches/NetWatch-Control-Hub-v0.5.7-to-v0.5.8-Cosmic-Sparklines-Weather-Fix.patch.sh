#!/usr/bin/env bash
set -euo pipefail
NETWATCH_CONTROL_HUB_PATCH=1
SOURCE_VERSION=0.5.7
TARGET_VERSION=0.5.8
TARGET="${NETWATCH_INSTALLED_DIR:-${1:-$HOME/.local/share/netwatch-system-updater}}"
[[ -f "$TARGET/package.json" ]] || { echo "NetWatch Control Hub not found: $TARGET"; exit 2; }
CURRENT="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["version"])' "$TARGET/package.json")"
[[ "$CURRENT" == "$TARGET_VERSION" ]] && { echo "NetWatch Control Hub v$TARGET_VERSION is already installed."; exit 0; }
[[ "$CURRENT" == "$SOURCE_VERSION" ]] || { echo "Expected v$SOURCE_VERSION, found v$CURRENT"; exit 3; }
STAMP="$(date +%Y%m%d-%H%M%S)"; BACKUP="$TARGET/.netwatch_patch_backups/$STAMP-v$CURRENT-to-v$TARGET_VERSION"; mkdir -p "$BACKUP/src"
for f in package.json src/renderer.js src/styles.css; do cp -a "$TARGET/$f" "$BACKUP/$f"; done
python3 - "$TARGET" <<'PY'
from pathlib import Path
import json,sys
r=Path(sys.argv[1]); pkg=r/'package.json'; js=r/'src/renderer.js'; css=r/'src/styles.css'
d=json.loads(pkg.read_text()); d['version']='0.5.8'; d['description']='NetWatch Control Hub — fixed weather hero and cosmic telemetry trails'; pkg.write_text(json.dumps(d,indent=2)+'\n')
def strip(s,a,b):
    if a in s and b in s: return s.split(a,1)[0].rstrip()+'\n\n'+s.split(b,1)[1].lstrip()
    return s
s=js.read_text()
for a,b in [
('// ===== NETWATCH 0.5.5 ANIMATED WEATHER HERO =====','// ===== END NETWATCH 0.5.5 ANIMATED WEATHER HERO ====='),
('// ===== NETWATCH 0.5.6 WEATHER HERO LAYOUT FIX =====','// ===== END NETWATCH 0.5.6 WEATHER HERO LAYOUT FIX ====='),
('// ===== NETWATCH 0.5.7 AURORA PERFORMANCE BEGIN =====','// ===== NETWATCH 0.5.7 AURORA PERFORMANCE END ====='),
('// ===== NETWATCH 0.5.8 FIX BEGIN =====','// ===== NETWATCH 0.5.8 FIX END =====')]: s=strip(s,a,b)
s+=r'''
// ===== NETWATCH 0.5.8 FIX BEGIN =====
(()=>{
 const $=q=>document.querySelector(q), ns='http://www.w3.org/2000/svg';
 let wh=null;
 const kind=w=>{let c=+w?.code;if([95,96,99].includes(c))return'storm';if([71,73,75,77,85,86].includes(c))return'snow';if([61,63,65,66,67,80,81,82,51,53,55,56,57].includes(c))return'rain';if([45,48].includes(c))return'fog';if(c===3)return'cloudy';if([1,2].includes(c))return'partly';return c===0?(w?.isDay===false?'night':'clear'):'partly'};
 function buildWeather(){
  const h=$('#page-overview .hero'); if(!h)return null;
  h.querySelectorAll(':scope>.nw-weather-hero-shell,:scope>.nw-weather-hero-v056,:scope>.nw58-weather').forEach(x=>x.remove());
  h.classList.remove('nw-weather-hero','nw-weather-host'); h.classList.add('nw58-owner');
  const copy=h.querySelector(':scope>.hero-copy'); if(copy)copy.classList.add('nw58-hide-copy');
  const x=document.createElement('section'); x.className='nw58-weather'; x.innerHTML=`<div class="nw58-sky"><i class="nw58-sun"></i><i class="nw58-moon"></i><i class="nw58-cloud c1"></i><i class="nw58-cloud c2"></i><i class="nw58-rain"></i><i class="nw58-drops"></i><i class="nw58-fog"></i><i class="nw58-flash"></i></div><div class="nw58-wtext"><small>NETWATCH LOCAL WEATHER</small><div class="nw58-lrow"><b id="nw58loc">Local Weather</b><em>● LIVE</em></div><div class="nw58-main"><strong id="nw58temp">--°</strong><div><h2 id="nw58cond">Loading…</h2><p id="nw58detail">Connecting to weather</p></div></div><div class="nw58-meta"><span id="nw58feel">Feels like --°</span><span id="nw58wind">Wind -- mph</span><span id="nw58cloud">Clouds --%</span><span id="nw58time">Updating…</span></div></div>`;
  h.insertBefore(x,h.querySelector(':scope>.health-wrap')||h.firstChild); return x;
 }
 function renderW(w){if(!w)return;wh=wh?.isConnected?wh:buildWeather();if(!wh)return;wh.dataset.wx=kind(w);wh.dataset.day=w.isDay===false?'n':'d';let q=id=>wh.querySelector('#'+id),n=v=>Number.isFinite(+v);q('nw58loc').textContent=w.location||'Local Weather';q('nw58temp').textContent=n(w.temperature)?Math.round(+w.temperature)+'°':'--°';q('nw58cond').textContent=w.condition||'Current conditions';let k=kind(w),p=n(w.precipitation)&&+w.precipitation>0?(+w.precipitation).toFixed(2)+' in precipitation • ':'';q('nw58detail').textContent=p+({storm:'Thunderstorm activity nearby',rain:'Rain falling nearby',snow:'Snow in the area',fog:'Reduced visibility',clear:'Clear conditions',night:'Clear night',cloudy:'Cloudy',partly:'Partly cloudy'}[k]||'Current conditions');q('nw58feel').textContent='Feels like '+(n(w.feelsLike)?Math.round(+w.feelsLike)+'°':'--°');q('nw58wind').textContent='Wind '+(n(w.windMph)?Math.round(+w.windMph)+' mph':'-- mph');q('nw58cloud').textContent='Clouds '+(n(w.cloudCover)?Math.round(+w.cloudCover)+'%':'--%');let t=w.fetchedAt?new Date(w.fetchedAt):new Date();q('nw58time').textContent='Updated '+t.toLocaleTimeString([],{hour:'numeric',minute:'2-digit'});}
 function weather(force=false){if(api?.getWeather)api.getWeather(force).then(w=>{if(typeof renderTopWeather==='function')renderTopWeather(w);renderW(w)}).catch(()=>{})}
 const ids=['cpuSpark','memorySpark','storageSpark','networkSpark','tempSpark','uptimeSpark','cpuLargeSpark','memoryLargeSpark'];
 function head(poly,c){let a=(poly.getAttribute('points')||'').trim().split(/\s+/).pop();if(!a)return;let [x,y]=a.split(',').map(Number);if(Number.isFinite(x)&&Number.isFinite(y)){c.setAttribute('cx',x);c.setAttribute('cy',y)}}
 function cosmic(poly){if(!poly||poly.dataset.nw58)return;poly.dataset.nw58='1';let svg=poly.ownerSVGElement;svg.classList.add('nw58-cosmic');let id='nw58g'+poly.id;
  let defs=document.createElementNS(ns,'defs');defs.innerHTML=`<linearGradient id="${id}" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#45eaff" stop-opacity=".08"/><stop offset="38%" stop-color="#5dffd0"/><stop offset="70%" stop-color="#8b70ff"/><stop offset="100%" stop-color="#f36bff"/></linearGradient>`;svg.prepend(defs);
  let glow=poly.cloneNode();glow.removeAttribute('id');glow.classList.add('nw58-glow');let tail=poly.cloneNode();tail.removeAttribute('id');tail.classList.add('nw58-tail');poly.classList.add('nw58-core');[glow,tail,poly].forEach(x=>x.setAttribute('stroke',`url(#${id})`));poly.before(glow,tail);
  let c=document.createElementNS(ns,'circle');c.classList.add('nw58-head');c.setAttribute('r','2.8');c.setAttribute('fill','#fff');c.setAttribute('stroke','#d883ff');c.setAttribute('stroke-width','1');poly.after(c);head(poly,c);
  new MutationObserver(()=>{glow.setAttribute('points',poly.getAttribute('points')||'');tail.setAttribute('points',poly.getAttribute('points')||'');head(poly,c);svg.classList.remove('nw58-ping');void svg.getBoundingClientRect();svg.classList.add('nw58-ping')}).observe(poly,{attributes:true,attributeFilter:['points']});
 }
 function start(){document.querySelectorAll('.nw-aurora-atmosphere,.nw-aurora-spark').forEach(x=>x.remove());document.querySelectorAll('.nw-aurora-metric,.nw-aurora-zone').forEach(x=>x.classList.remove('nw-aurora-metric','nw-aurora-zone'));wh=buildWeather();ids.forEach(id=>cosmic(document.getElementById(id)));weather(false);document.querySelector('#topWeather')?.addEventListener('click',()=>weather(true));setInterval(()=>weather(false),600000)}
 document.readyState==='loading'?document.addEventListener('DOMContentLoaded',start,{once:true}):start();
})();
// ===== NETWATCH 0.5.8 FIX END =====
'''
js.write_text(s)
c=css.read_text()
for a,b in [
('/* ===== NETWATCH 0.5.5 ANIMATED WEATHER HERO ===== */','/* ===== END NETWATCH 0.5.5 ANIMATED WEATHER HERO ===== */'),
('/* ===== NETWATCH 0.5.6 WEATHER HERO LAYOUT FIX ===== */','/* ===== END NETWATCH 0.5.6 WEATHER HERO LAYOUT FIX ===== */'),
('/* ===== NETWATCH 0.5.7 AURORA PERFORMANCE BEGIN ===== */','/* ===== NETWATCH 0.5.7 AURORA PERFORMANCE END ===== */'),
('/* ===== NETWATCH 0.5.8 FIX BEGIN ===== */','/* ===== NETWATCH 0.5.8 FIX END ===== */')]: c=strip(c,a,b)
c+=r'''
/* ===== NETWATCH 0.5.8 FIX BEGIN ===== */
#page-overview .hero.nw58-owner{display:grid!important;grid-template-columns:minmax(0,1.28fr) minmax(260px,.72fr)!important;gap:16px!important;align-items:stretch!important;padding:16px!important;min-height:0!important;overflow:hidden!important}
.nw58-hide-copy{display:none!important}.nw58-weather{grid-column:1;position:relative;min-height:260px;border-radius:26px;overflow:hidden;isolation:isolate;border:1px solid rgba(255,255,255,.16);background:rgba(8,22,40,.18);box-shadow:inset 0 1px rgba(255,255,255,.12)}
.nw58-sky{position:absolute;inset:0;overflow:hidden;background:linear-gradient(145deg,rgba(22,78,120,.26),rgba(13,29,50,.18) 53%,rgba(67,46,101,.18));pointer-events:none}.nw58-weather[data-day="n"] .nw58-sky{background:linear-gradient(145deg,rgba(7,18,42,.66),rgba(24,18,55,.48))}.nw58-weather[data-wx="rain"] .nw58-sky,.nw58-weather[data-wx="storm"] .nw58-sky{background:linear-gradient(145deg,rgba(20,37,52,.70),rgba(28,31,55,.58))}
.nw58-wtext{position:relative;z-index:4;min-height:260px;padding:24px 28px;display:flex;flex-direction:column;justify-content:center;text-shadow:0 2px 12px rgba(0,0,0,.28)}.nw58-wtext small{font-weight:800;letter-spacing:.18em;color:#90ecff}.nw58-lrow{display:flex;align-items:center;gap:12px}.nw58-lrow b{font-size:clamp(30px,3.1vw,52px);line-height:1}.nw58-lrow em{font-style:normal;font-size:10px;letter-spacing:.13em;padding:5px 9px;border:1px solid rgba(255,255,255,.18);border-radius:999px}.nw58-main{display:flex;align-items:center;gap:22px;margin-top:5px}.nw58-main>strong{font-size:clamp(64px,6vw,96px);font-weight:300;line-height:1}.nw58-main h2{font-size:22px;margin:0 0 5px}.nw58-main p{margin:0;opacity:.8}.nw58-meta{display:flex;flex-wrap:wrap;gap:8px;margin-top:15px}.nw58-meta span{font-size:11px;padding:6px 9px;border-radius:999px;background:rgba(5,16,30,.25);border:1px solid rgba(255,255,255,.12)}
.nw58-sun,.nw58-moon{position:absolute;right:9%;top:10%;width:94px;height:94px;border-radius:50%;opacity:0}.nw58-sun{background:radial-gradient(circle,#fff7bd 0 14%,#ffd56e 34%,rgba(255,196,87,.17) 67%,transparent 70%);filter:drop-shadow(0 0 28px rgba(255,216,110,.4));animation:nw58sun 7s ease-in-out infinite}.nw58-moon{width:72px;height:72px;background:radial-gradient(circle at 35% 30%,#fff,#cbdcff 35%,#8298bd);box-shadow:0 0 40px rgba(185,210,255,.25)}.nw58-weather[data-wx="clear"] .nw58-sun,.nw58-weather[data-wx="partly"] .nw58-sun{opacity:.95}.nw58-weather[data-wx="night"] .nw58-moon{opacity:.9}
.nw58-cloud{position:absolute;left:-260px;width:240px;height:70px;border-radius:50px;opacity:0;background:radial-gradient(circle at 28% 58%,rgba(255,255,255,.7) 0 25%,transparent 26%),radial-gradient(circle at 54% 36%,rgba(255,255,255,.72) 0 30%,transparent 31%),radial-gradient(circle at 76% 60%,rgba(255,255,255,.58) 0 25%,transparent 26%),linear-gradient(rgba(255,255,255,.53),rgba(210,225,238,.34));filter:blur(.4px)}.nw58-cloud.c1{top:17%;animation:nw58cloud 31s linear infinite}.nw58-cloud.c2{top:49%;animation:nw58cloud 43s linear infinite 8s;transform:scale(.72)}.nw58-weather[data-wx="partly"] .nw58-cloud{opacity:.42}.nw58-weather:is([data-wx="cloudy"],[data-wx="rain"],[data-wx="storm"],[data-wx="snow"],[data-wx="fog"]) .nw58-cloud{opacity:.68}
.nw58-rain,.nw58-drops,.nw58-fog,.nw58-flash{position:absolute;inset:0;opacity:0}.nw58-rain{inset:-40px;background:repeating-linear-gradient(104deg,transparent 0 38px,rgba(220,241,255,.32) 39px 40px,transparent 41px 78px);background-size:82px 120px;animation:nw58rain .76s linear infinite}.nw58-drops{background-image:radial-gradient(ellipse,rgba(235,249,255,.3) 0 1.5px,transparent 4px);background-size:67px 91px;animation:nw58drops 8s linear infinite}.nw58-fog{left:-15%;width:130%;height:80px;top:42%;background:rgba(236,245,250,.23);filter:blur(24px);animation:nw58fog 15s ease-in-out infinite}.nw58-flash{background:radial-gradient(circle at 76% 18%,white,rgba(210,225,255,.25) 24%,transparent 50%)}.nw58-weather[data-wx="rain"] :is(.nw58-rain,.nw58-drops){opacity:.47}.nw58-weather[data-wx="storm"] :is(.nw58-rain,.nw58-drops){opacity:.62}.nw58-weather[data-wx="storm"] .nw58-flash{animation:nw58flash 8s steps(1) infinite}.nw58-weather[data-wx="fog"] .nw58-fog{opacity:.7}
/* Keep the glass cards untouched; cosmic styling is confined to their existing SVG telemetry. */
.nw58-cosmic{overflow:visible!important}.nw58-cosmic :is(.nw58-glow,.nw58-tail,.nw58-core){fill:none;stroke-linecap:round;stroke-linejoin:round;vector-effect:non-scaling-stroke}.nw58-glow{stroke-width:8!important;opacity:.18;filter:blur(3px) drop-shadow(0 0 9px #50ddff)}.nw58-tail{stroke-width:4.4!important;opacity:.43;stroke-dasharray:6 3 13 4;filter:drop-shadow(0 0 5px #63ffd1) drop-shadow(0 0 8px #9b68ff);animation:nw58travel 2.5s linear infinite}.nw58-core{stroke-width:2.15!important;opacity:1;filter:drop-shadow(0 0 3px #63efff) drop-shadow(0 0 6px #b264ff)}.nw58-head{filter:drop-shadow(0 0 4px white) drop-shadow(0 0 10px #d26cff)}.nw58-ping .nw58-head{animation:nw58head .6s ease-out}.nw58-ping .nw58-tail{animation:nw58tail .72s ease-out,nw58travel 2.5s linear infinite}
@keyframes nw58cloud{to{transform:translateX(calc(100vw + 570px))}}@keyframes nw58rain{to{background-position:-30px 240px}}@keyframes nw58drops{to{background-position:6px 240px}}@keyframes nw58fog{50%{transform:translateX(7%) scaleX(1.05)}}@keyframes nw58flash{0%,82%,84%,87%,100%{opacity:0}83%{opacity:.7}85%{opacity:.2}86%{opacity:.5}}@keyframes nw58sun{50%{transform:scale(1.05);filter:drop-shadow(0 0 38px rgba(255,216,110,.48))}}@keyframes nw58travel{to{stroke-dashoffset:-52}}@keyframes nw58head{0%{opacity:.3;transform:scale(.55)}35%{opacity:1;transform:scale(1.7)}100%{transform:scale(1)}}@keyframes nw58tail{35%{opacity:.8}}
@media(max-width:1100px){#page-overview .hero.nw58-owner{grid-template-columns:1fr!important}.nw58-weather,.health-wrap,.quick-panel{grid-column:1!important}}@media(prefers-reduced-motion:reduce){.nw58-cloud,.nw58-rain,.nw58-drops,.nw58-fog,.nw58-flash,.nw58-sun,.nw58-tail,.nw58-head{animation:none!important}}
/* ===== NETWATCH 0.5.8 FIX END ===== */
'''
css.write_text(c)
PY
python3 -c 'import json,sys;json.load(open(sys.argv[1]));print("package.json: PASS")' "$TARGET/package.json"
node --check "$TARGET/src/renderer.js"
[[ -f "$TARGET/src/weather-service.js" ]] && node --check "$TARGET/src/weather-service.js"
[[ -f "$TARGET/src/self-updater.js" ]] && node --check "$TARGET/src/self-updater.js"
echo "PASS: NetWatch Control Hub updated from v$SOURCE_VERSION to v$TARGET_VERSION"
echo "Weather owns the hero slot; glass tiles stay clean; real telemetry lines now carry cosmic aurora tails."
echo "Backup: $BACKUP"
