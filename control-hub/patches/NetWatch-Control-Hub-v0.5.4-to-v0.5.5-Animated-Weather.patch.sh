#!/usr/bin/env bash
set -euo pipefail

NETWATCH_CONTROL_HUB_PATCH=1
SOURCE_VERSION=0.5.4
TARGET_VERSION=0.5.5
TARGET="${NETWATCH_INSTALLED_DIR:-${1:-$HOME/.local/share/netwatch-system-updater}}"

[[ -f "$TARGET/package.json" ]] || {
  echo "NetWatch Control Hub not found: $TARGET"
  exit 2
}

CURRENT="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["version"])' "$TARGET/package.json")"

if [[ "$CURRENT" == "$TARGET_VERSION" ]]; then
  echo "NetWatch Control Hub v$TARGET_VERSION is already installed."
  exit 0
fi

[[ "$CURRENT" == "$SOURCE_VERSION" ]] || {
  echo "Expected v$SOURCE_VERSION, found v$CURRENT"
  exit 3
}

STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="$TARGET/.netwatch_patch_backups/$STAMP-v$CURRENT-to-v$TARGET_VERSION"
mkdir -p "$BACKUP/src"

for f in package.json src/index.html src/renderer.js src/styles.css src/weather-service.js; do
  if [[ -f "$TARGET/$f" ]]; then
    mkdir -p "$(dirname "$BACKUP/$f")"
    cp -a "$TARGET/$f" "$BACKUP/$f"
  fi
done

python3 - "$TARGET" <<'PY'
from pathlib import Path
import json, sys

root = Path(sys.argv[1])

pkg = root / "package.json"
data = json.loads(pkg.read_text(encoding="utf-8"))
data["version"] = "0.5.5"
data["description"] = "NetWatch Crystal Glass Control Hub — animated local-weather command center with GitHub self-updater"
pkg.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")

weather = root / "src/weather-service.js"
s = weather.read_text(encoding="utf-8")
s = s.replace(
    "current:'temperature_2m,apparent_temperature,is_day,precipitation,weather_code,cloud_cover,wind_speed_10m'",
    "current:'temperature_2m,apparent_temperature,is_day,precipitation,rain,snowfall,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m'"
)
s = s.replace(
    "windMph:Number(c.wind_speed_10m),condition:text(code),",
    "windMph:Number(c.wind_speed_10m),windDirection:Number(c.wind_direction_10m),cloudCover:Number(c.cloud_cover),precipitation:Number(c.precipitation),rain:Number(c.rain),snowfall:Number(c.snowfall),isDay:Number(c.is_day)===1,condition:text(code),"
)
weather.write_text(s, encoding="utf-8")

renderer = root / "src/renderer.js"
s = renderer.read_text(encoding="utf-8")

begin = "// ===== NETWATCH 0.5.5 ANIMATED WEATHER HERO ====="
end = "// ===== END NETWATCH 0.5.5 ANIMATED WEATHER HERO ====="
if begin in s and end in s:
    before = s.split(begin)[0].rstrip()
    after = s.split(end, 1)[1].lstrip()
    s = before + "\n\n" + after

addon = r'''
// ===== NETWATCH 0.5.5 ANIMATED WEATHER HERO =====
let netwatchWeatherHero = null;

function netwatchWeatherKind(w) {
  const code = Number(w?.code);
  if ([95,96,99].includes(code)) return "storm";
  if ([71,73,75,77,85,86].includes(code)) return "snow";
  if ([61,63,65,66,67,80,81,82,51,53,55,56,57].includes(code)) return "rain";
  if ([45,48].includes(code)) return "fog";
  if (code === 3) return "cloudy";
  if ([1,2].includes(code)) return "partly";
  if (code === 0) return w?.isDay === false ? "clear-night" : "clear";
  return "partly";
}

function findNetwatchWeatherHero() {
  const headings = [...document.querySelectorAll("h1,h2,.hero-title,.page-title")];
  const heading = headings.find(el =>
    /NetWatch\s+(System\s+)?Control\s+Hub/i.test((el.textContent || "").trim())
  );
  if (!heading) return null;

  let host = heading.closest(".hero,.hero-card,.overview-hero,.pulse-hero,.page-hero,.command-hero");
  if (!host) host = heading.parentElement?.parentElement || heading.parentElement;
  if (!host) return null;

  host.classList.add("nw-weather-hero");
  heading.classList.add("nw-weather-original-title");

  let shell = host.querySelector(".nw-weather-hero-shell");
  if (!shell) {
    shell = document.createElement("div");
    shell.className = "nw-weather-hero-shell";
    shell.innerHTML = `
      <div class="nw-weather-sky" aria-hidden="true">
        <div class="nw-weather-stars"></div>
        <div class="nw-weather-sun"></div>
        <div class="nw-weather-moon"></div>
        <div class="nw-weather-cloud nw-cloud-1"></div>
        <div class="nw-weather-cloud nw-cloud-2"></div>
        <div class="nw-weather-cloud nw-cloud-3"></div>
        <div class="nw-weather-fog nw-fog-1"></div>
        <div class="nw-weather-fog nw-fog-2"></div>
        <div class="nw-weather-rain"></div>
        <div class="nw-weather-snow"></div>
        <div class="nw-weather-lightning"></div>
        <div class="nw-weather-aurora"></div>
      </div>
      <div class="nw-weather-hero-content">
        <div class="nw-weather-kicker">NETWATCH SYSTEM CONTROL HUB</div>
        <div class="nw-weather-location-row">
          <div class="nw-weather-location" id="nwWeatherHeroLocation">Local Weather</div>
          <div class="nw-weather-live-dot"><i></i> LIVE</div>
        </div>
        <div class="nw-weather-main">
          <div class="nw-weather-temp" id="nwWeatherHeroTemp">--°</div>
          <div class="nw-weather-condition-wrap">
            <div class="nw-weather-condition" id="nwWeatherHeroCondition">Loading current conditions…</div>
            <div class="nw-weather-detail" id="nwWeatherHeroDetail">Connecting to local weather</div>
          </div>
        </div>
        <div class="nw-weather-meta">
          <span id="nwWeatherFeels">Feels like --°</span>
          <span id="nwWeatherWind">Wind -- mph</span>
          <span id="nwWeatherClouds">Clouds --%</span>
          <span id="nwWeatherUpdated">Updating…</span>
        </div>
      </div>
    `;
    host.prepend(shell);
  }

  for (const child of [...host.children]) {
    if (child !== shell) child.classList.add("nw-weather-existing-content");
  }

  return { host, shell, heading };
}

function renderNetwatchWeatherHero(w) {
  if (!w) return;
  if (!netwatchWeatherHero) netwatchWeatherHero = findNetwatchWeatherHero();
  if (!netwatchWeatherHero) return;

  const { host } = netwatchWeatherHero;
  const kind = netwatchWeatherKind(w);
  host.dataset.weather = kind;
  host.dataset.day = w.isDay === false ? "night" : "day";

  const temp = Number.isFinite(Number(w.temperature)) ? `${Math.round(Number(w.temperature))}°` : "--°";
  const feels = Number.isFinite(Number(w.feelsLike)) ? `${Math.round(Number(w.feelsLike))}°` : "--°";
  const wind = Number.isFinite(Number(w.windMph)) ? `${Math.round(Number(w.windMph))} mph` : "-- mph";
  const clouds = Number.isFinite(Number(w.cloudCover)) ? `${Math.round(Number(w.cloudCover))}%` : "--%";

  const byId = id => document.getElementById(id);
  byId("nwWeatherHeroLocation") && (byId("nwWeatherHeroLocation").textContent = w.location || "Local Weather");
  byId("nwWeatherHeroTemp") && (byId("nwWeatherHeroTemp").textContent = temp);
  byId("nwWeatherHeroCondition") && (byId("nwWeatherHeroCondition").textContent = w.condition || "Current conditions");

  if (byId("nwWeatherHeroDetail")) {
    const detail = [];
    const precip = Number(w.precipitation);
    if (Number.isFinite(precip) && precip > 0) detail.push(`${precip.toFixed(2)} in precipitation`);
    if (kind === "storm") detail.push("Thunderstorm activity");
    else if (kind === "rain") detail.push("Rain in the area");
    else if (kind === "snow") detail.push("Snow in the area");
    else if (kind === "fog") detail.push("Reduced visibility");
    else if (kind === "clear" || kind === "clear-night") detail.push("Clear conditions");
    else detail.push("Current local conditions");
    byId("nwWeatherHeroDetail").textContent = detail.join(" • ");
  }

  byId("nwWeatherFeels") && (byId("nwWeatherFeels").textContent = `Feels like ${feels}`);
  byId("nwWeatherWind") && (byId("nwWeatherWind").textContent = `Wind ${wind}`);
  byId("nwWeatherClouds") && (byId("nwWeatherClouds").textContent = `Clouds ${clouds}`);

  if (byId("nwWeatherUpdated")) {
    const t = w.fetchedAt ? new Date(w.fetchedAt) : new Date();
    byId("nwWeatherUpdated").textContent =
      `Updated ${t.toLocaleTimeString([], {hour:"numeric", minute:"2-digit"})}`;
  }
}

document.addEventListener("DOMContentLoaded", () => {
  netwatchWeatherHero = findNetwatchWeatherHero();
  if (api?.getWeather) {
    api.getWeather(false).then(w => renderNetwatchWeatherHero(w)).catch(() => {});
  }

  const weatherButton = document.querySelector("#topWeather");
  weatherButton?.addEventListener("click", () => {
    if (!api?.getWeather) return;
    api.getWeather(true)
      .then(w => {
        if (typeof renderTopWeather === "function") renderTopWeather(w);
        renderNetwatchWeatherHero(w);
      })
      .catch(() => {});
  });

  setInterval(() => {
    if (!api?.getWeather) return;
    api.getWeather(false)
      .then(w => renderNetwatchWeatherHero(w))
      .catch(() => {});
  }, 600000);
});
// ===== END NETWATCH 0.5.5 ANIMATED WEATHER HERO =====
'''

renderer.write_text(s.rstrip() + "\n\n" + addon.strip() + "\n", encoding="utf-8")

styles = root / "src/styles.css"
s = styles.read_text(encoding="utf-8")
css_begin = "/* ===== NETWATCH 0.5.5 ANIMATED WEATHER HERO ===== */"
if css_begin in s:
    s = s.split(css_begin)[0].rstrip() + "\n"

css = r'''
/* ===== NETWATCH 0.5.5 ANIMATED WEATHER HERO ===== */
.nw-weather-hero{position:relative!important;overflow:hidden!important;isolation:isolate;min-height:310px!important;border:1px solid rgba(255,255,255,.20)!important;background:rgba(13,30,52,.16)!important}
.nw-weather-hero::after{content:"";position:absolute;inset:0;z-index:-1;pointer-events:none;background:linear-gradient(180deg,rgba(5,18,35,.02),rgba(5,18,35,.22)),linear-gradient(90deg,rgba(5,18,35,.30),transparent 58%)}
.nw-weather-hero-shell{position:absolute;inset:0;z-index:0;overflow:hidden;border-radius:inherit}
.nw-weather-sky{position:absolute;inset:0;overflow:hidden;background:radial-gradient(circle at 82% 20%,rgba(255,255,255,.16),transparent 22%),linear-gradient(135deg,rgba(42,126,191,.38),rgba(71,75,151,.30) 48%,rgba(19,31,61,.38));transition:background 1.2s ease,filter 1.2s ease}
.nw-weather-hero[data-weather="clear"] .nw-weather-sky{background:radial-gradient(circle at 82% 18%,rgba(255,236,143,.40),transparent 20%),linear-gradient(135deg,rgba(60,171,235,.52),rgba(102,201,240,.28) 48%,rgba(96,131,215,.26))}
.nw-weather-hero[data-weather="clear-night"] .nw-weather-sky{background:radial-gradient(circle at 80% 19%,rgba(185,205,255,.16),transparent 16%),linear-gradient(145deg,rgba(10,28,67,.70),rgba(32,36,88,.54) 50%,rgba(9,15,35,.74))}
.nw-weather-hero[data-weather="partly"] .nw-weather-sky{background:radial-gradient(circle at 82% 18%,rgba(255,225,146,.28),transparent 20%),linear-gradient(140deg,rgba(55,147,214,.44),rgba(91,111,170,.38) 56%,rgba(35,48,82,.45))}
.nw-weather-hero[data-weather="cloudy"] .nw-weather-sky,.nw-weather-hero[data-weather="fog"] .nw-weather-sky{background:linear-gradient(145deg,rgba(87,115,139,.52),rgba(68,83,107,.48) 48%,rgba(33,47,68,.58))}
.nw-weather-hero[data-weather="rain"] .nw-weather-sky{background:linear-gradient(145deg,rgba(34,82,112,.60),rgba(42,54,82,.62) 52%,rgba(18,31,49,.68))}
.nw-weather-hero[data-weather="storm"] .nw-weather-sky{background:linear-gradient(145deg,rgba(30,46,73,.75),rgba(30,27,60,.72) 52%,rgba(11,18,31,.80))}
.nw-weather-hero[data-weather="snow"] .nw-weather-sky{background:linear-gradient(145deg,rgba(111,155,184,.48),rgba(114,132,166,.46) 52%,rgba(64,83,110,.54))}
.nw-weather-hero-content{position:relative;z-index:4;height:100%;min-height:310px;display:flex;flex-direction:column;justify-content:center;padding:34px 42px 90px;color:white;text-shadow:0 2px 18px rgba(0,0,0,.28)}
.nw-weather-kicker{font-size:11px;font-weight:800;letter-spacing:.15em;opacity:.78;margin-bottom:8px}
.nw-weather-location-row{display:flex;align-items:center;gap:12px}.nw-weather-location{font-size:clamp(30px,4vw,58px);font-weight:750;letter-spacing:-.045em;line-height:.98}
.nw-weather-live-dot{display:flex;gap:6px;align-items:center;padding:5px 9px;border-radius:999px;font-size:9px;font-weight:800;letter-spacing:.08em;border:1px solid rgba(255,255,255,.20);background:rgba(255,255,255,.08);backdrop-filter:blur(16px)}
.nw-weather-live-dot i{width:7px;height:7px;border-radius:50%;background:#76f5c9;box-shadow:0 0 14px #76f5c9;animation:nwLivePulse 1.8s ease-in-out infinite}
.nw-weather-main{display:flex;align-items:end;gap:20px;margin-top:10px}.nw-weather-temp{font-size:clamp(66px,8.8vw,116px);font-weight:280;line-height:.82;letter-spacing:-.075em}.nw-weather-condition-wrap{padding-bottom:7px}
.nw-weather-condition{font-size:clamp(18px,2vw,28px);font-weight:680}.nw-weather-detail{margin-top:4px;font-size:12px;opacity:.82}
.nw-weather-meta{display:flex;gap:8px;flex-wrap:wrap;margin-top:16px}.nw-weather-meta span{padding:6px 10px;border:1px solid rgba(255,255,255,.14);border-radius:999px;background:rgba(8,21,39,.14);backdrop-filter:blur(16px) saturate(150%);font-size:10px;font-weight:650}
.nw-weather-existing-content{position:relative;z-index:5}.nw-weather-original-title{position:absolute!important;width:1px!important;height:1px!important;overflow:hidden!important;clip:rect(0 0 0 0)!important;clip-path:inset(50%)!important;white-space:nowrap!important}
.nw-weather-sun,.nw-weather-moon{position:absolute;right:7%;top:10%;width:118px;height:118px;border-radius:50%;opacity:0;transition:opacity 1.2s ease}
.nw-weather-sun{background:radial-gradient(circle,rgba(255,249,194,.96) 0 20%,rgba(255,215,101,.82) 38%,rgba(255,184,78,.18) 68%,transparent 72%);box-shadow:0 0 70px rgba(255,211,104,.34);animation:nwSunBreathe 7s ease-in-out infinite}
.nw-weather-moon{width:92px;height:92px;background:radial-gradient(circle at 36% 32%,#fff 0 8%,#dfeaff 26%,#9db7dd 68%,#7588aa 100%);box-shadow:0 0 54px rgba(168,197,255,.22)}
.nw-weather-hero[data-weather="clear"] .nw-weather-sun,.nw-weather-hero[data-weather="partly"] .nw-weather-sun{opacity:.95}.nw-weather-hero[data-weather="clear-night"] .nw-weather-moon{opacity:.90}
.nw-weather-stars{position:absolute;inset:0;opacity:0;background-image:radial-gradient(circle,rgba(255,255,255,.75) 0 1px,transparent 1.6px),radial-gradient(circle,rgba(170,215,255,.75) 0 1px,transparent 1.7px);background-size:82px 82px,123px 123px;background-position:7px 13px,41px 27px;transition:opacity 1s ease;animation:nwStars 5s ease-in-out infinite}.nw-weather-hero[data-day="night"] .nw-weather-stars{opacity:.46}
.nw-weather-cloud{position:absolute;width:220px;height:68px;border-radius:50px;opacity:0;filter:blur(.2px);background:radial-gradient(circle at 24% 52%,rgba(255,255,255,.68) 0 24%,transparent 25%),radial-gradient(circle at 46% 32%,rgba(255,255,255,.73) 0 31%,transparent 32%),radial-gradient(circle at 72% 51%,rgba(255,255,255,.62) 0 25%,transparent 26%),linear-gradient(rgba(255,255,255,.58),rgba(217,233,245,.40));box-shadow:0 18px 38px rgba(0,0,0,.10)}
.nw-cloud-1{top:15%;left:-250px;animation:nwCloudA 28s linear infinite}.nw-cloud-2{top:31%;left:-330px;transform:scale(.72);animation:nwCloudB 39s linear infinite 4s}.nw-cloud-3{top:5%;left:-280px;transform:scale(.50);animation:nwCloudA 48s linear infinite 12s}
.nw-weather-hero[data-weather="partly"] .nw-weather-cloud{opacity:.48}.nw-weather-hero[data-weather="cloudy"] .nw-weather-cloud,.nw-weather-hero[data-weather="rain"] .nw-weather-cloud,.nw-weather-hero[data-weather="storm"] .nw-weather-cloud,.nw-weather-hero[data-weather="snow"] .nw-weather-cloud,.nw-weather-hero[data-weather="fog"] .nw-weather-cloud{opacity:.68}
.nw-weather-hero[data-weather="rain"] .nw-weather-cloud,.nw-weather-hero[data-weather="storm"] .nw-weather-cloud{filter:brightness(.68) saturate(.75)}
.nw-weather-rain{position:absolute;inset:-30px;opacity:0;transform:skewX(-10deg);background-image:repeating-linear-gradient(105deg,transparent 0 18px,rgba(193,230,255,.36) 18px 20px,transparent 20px 40px);background-size:38px 64px;animation:nwRain .62s linear infinite;transition:opacity .7s ease}.nw-weather-hero[data-weather="rain"] .nw-weather-rain{opacity:.42}.nw-weather-hero[data-weather="storm"] .nw-weather-rain{opacity:.56}
.nw-weather-snow{position:absolute;inset:0;opacity:0;background-image:radial-gradient(circle,rgba(255,255,255,.88) 0 2px,transparent 2.8px),radial-gradient(circle,rgba(230,244,255,.76) 0 1.6px,transparent 2.3px);background-size:43px 43px,71px 71px;animation:nwSnow 8s linear infinite}.nw-weather-hero[data-weather="snow"] .nw-weather-snow{opacity:.62}
.nw-weather-fog{position:absolute;left:-20%;width:140%;height:70px;border-radius:50%;opacity:0;background:rgba(235,244,249,.20);filter:blur(24px)}.nw-fog-1{top:30%;animation:nwFog 15s ease-in-out infinite}.nw-fog-2{top:52%;animation:nwFog 21s ease-in-out infinite reverse}.nw-weather-hero[data-weather="fog"] .nw-weather-fog{opacity:.72}
.nw-weather-lightning{position:absolute;inset:0;opacity:0;background:white;pointer-events:none}.nw-weather-hero[data-weather="storm"] .nw-weather-lightning{animation:nwLightning 7s steps(1,end) infinite}
.nw-weather-aurora{position:absolute;inset:auto -10% -28% -10%;height:72%;opacity:.34;filter:blur(18px);background:radial-gradient(ellipse at 16% 54%,rgba(80,255,194,.36),transparent 34%),radial-gradient(ellipse at 44% 64%,rgba(49,207,255,.34),transparent 34%),radial-gradient(ellipse at 72% 52%,rgba(149,99,255,.28),transparent 36%),radial-gradient(ellipse at 92% 64%,rgba(255,95,202,.20),transparent 28%);animation:nwAuroraWeather 11s ease-in-out infinite alternate}
@keyframes nwCloudA{from{transform:translateX(0) scale(1)}to{transform:translateX(calc(100vw + 520px)) scale(1)}}@keyframes nwCloudB{from{transform:translateX(0) scale(.72)}to{transform:translateX(calc(100vw + 600px)) scale(.72)}}@keyframes nwRain{from{background-position:0 -80px}to{background-position:-18px 80px}}@keyframes nwSnow{from{background-position:0 -120px,20px -80px}to{background-position:65px 220px,-30px 250px}}@keyframes nwFog{0%,100%{transform:translateX(-3%) scaleX(.95)}50%{transform:translateX(4%) scaleX(1.05)}}@keyframes nwLightning{0%,86%,90%,100%{opacity:0}87%{opacity:.33}88%{opacity:0}89%{opacity:.18}}@keyframes nwSunBreathe{0%,100%{transform:scale(.96);filter:brightness(.96)}50%{transform:scale(1.05);filter:brightness(1.09)}}@keyframes nwStars{0%,100%{filter:brightness(.90)}50%{filter:brightness(1.18)}}@keyframes nwAuroraWeather{from{transform:translate3d(-2%,0,0) scaleX(.98);filter:blur(20px) saturate(1)}to{transform:translate3d(3%,-3%,0) scaleX(1.04);filter:blur(15px) saturate(1.35)}}@keyframes nwLivePulse{0%,100%{opacity:.65;transform:scale(.85)}50%{opacity:1;transform:scale(1.18)}}
@media(max-width:900px){.nw-weather-hero{min-height:285px!important}.nw-weather-hero-content{min-height:285px;padding:28px 24px 76px}.nw-weather-main{gap:12px}.nw-weather-meta span:nth-child(3){display:none}}
@media(prefers-reduced-motion:reduce){.nw-weather-cloud,.nw-weather-rain,.nw-weather-snow,.nw-weather-fog,.nw-weather-lightning,.nw-weather-aurora,.nw-weather-sun,.nw-weather-stars,.nw-weather-live-dot i{animation:none!important}}
/* ===== END NETWATCH 0.5.5 ANIMATED WEATHER HERO ===== */
'''

styles.write_text(s.rstrip() + "\n\n" + css.strip() + "\n", encoding="utf-8")
PY

echo "Validating NetWatch v$TARGET_VERSION..."
node --check "$TARGET/src/renderer.js"
node --check "$TARGET/src/weather-service.js"

echo
echo "NetWatch Control Hub updated to v$TARGET_VERSION."
echo "Backup: $BACKUP"
echo
echo "Restart with:"
echo "  $HOME/.local/share/netwatch-system-updater/run.sh"
