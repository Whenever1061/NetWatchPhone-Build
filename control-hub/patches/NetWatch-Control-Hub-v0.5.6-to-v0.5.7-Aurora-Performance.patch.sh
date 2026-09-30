#!/usr/bin/env bash
set -euo pipefail

NETWATCH_CONTROL_HUB_PATCH=1
SOURCE_VERSION=0.5.6
TARGET_VERSION=0.5.7
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

for f in package.json src/styles.css src/renderer.js; do
  [[ -f "$TARGET/$f" ]] || { echo "Missing required file: $TARGET/$f"; exit 4; }
done

STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="$TARGET/.netwatch_patch_backups/$STAMP-v$CURRENT-to-v$TARGET_VERSION"
mkdir -p "$BACKUP/src"
cp -a "$TARGET/package.json" "$BACKUP/package.json"
cp -a "$TARGET/src/styles.css" "$BACKUP/src/styles.css"
cp -a "$TARGET/src/renderer.js" "$BACKUP/src/renderer.js"

python3 - "$TARGET" <<'PY'
from pathlib import Path
import json, sys

root = Path(sys.argv[1])
pkg = root / "package.json"
css_file = root / "src/styles.css"
renderer_file = root / "src/renderer.js"

# Version
data = json.loads(pkg.read_text(encoding="utf-8"))
data["version"] = "0.5.7"
data["description"] = "NetWatch Crystal Glass Control Hub — live weather and aurora performance visualization"
pkg.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")

# CSS: remove any earlier 0.5.7 block before appending, so the patch is deterministic.
css = css_file.read_text(encoding="utf-8")
css_begin = "/* ===== NETWATCH 0.5.7 AURORA PERFORMANCE BEGIN ===== */"
css_end = "/* ===== NETWATCH 0.5.7 AURORA PERFORMANCE END ===== */"
if css_begin in css and css_end in css:
    before = css.split(css_begin, 1)[0].rstrip()
    after = css.split(css_end, 1)[1].lstrip()
    css = before + "\n\n" + after

addon_css = r'''
/* ===== NETWATCH 0.5.7 AURORA PERFORMANCE BEGIN ===== */

/* The performance cards become living glass aurora chambers. */
.nw-aurora-metric {
  --nw-activity: .34;
  --nw-speed: 16s;
  position: relative !important;
  overflow: hidden !important;
  isolation: isolate !important;
  background:
    linear-gradient(180deg, rgba(255,255,255,.095), rgba(255,255,255,.025)),
    rgba(8,18,34,.18) !important;
  border: 1px solid rgba(198,235,255,.20) !important;
  box-shadow:
    inset 0 1px 0 rgba(255,255,255,.20),
    inset 0 -18px 44px rgba(0,0,0,.10),
    0 16px 42px rgba(0,0,0,.12),
    0 0 calc(16px + 22px * var(--nw-activity)) rgba(91,213,255,.10) !important;
  transform: translateZ(0);
}

.nw-aurora-metric > :not(.nw-aurora-atmosphere):not(.nw-aurora-spark) {
  position: relative;
  z-index: 3;
}

.nw-aurora-atmosphere {
  position: absolute;
  inset: -18%;
  z-index: 0;
  pointer-events: none;
  opacity: calc(.58 + var(--nw-activity) * .24);
  filter: saturate(1.18) contrast(1.04);
  mix-blend-mode: screen;
}

.nw-aurora-atmosphere .nw-abubble,
.nw-aurora-atmosphere .nw-aribbon {
  position: absolute;
  display: block;
  will-change: transform, filter, opacity;
  pointer-events: none;
}

.nw-aurora-atmosphere .nw-abubble {
  width: 46%;
  aspect-ratio: 1;
  border-radius: 999px;
  filter: blur(18px);
  opacity: .52;
}

.nw-aurora-atmosphere .nw-abubble.b1 {
  left: -7%; top: 8%;
  background: radial-gradient(circle at 38% 32%, rgba(126,255,239,.98), rgba(39,211,238,.48) 38%, rgba(48,85,255,.12) 72%, transparent 76%);
  animation: nwAuroraBubbleOne var(--nw-speed) ease-in-out infinite alternate;
}
.nw-aurora-atmosphere .nw-abubble.b2 {
  right: -10%; top: -4%;
  background: radial-gradient(circle at 46% 48%, rgba(191,112,255,.92), rgba(117,72,255,.42) 42%, rgba(33,192,255,.10) 74%, transparent 78%);
  animation: nwAuroraBubbleTwo calc(var(--nw-speed) * 1.17) ease-in-out infinite alternate;
}
.nw-aurora-atmosphere .nw-abubble.b3 {
  left: 26%; bottom: -23%;
  width: 55%;
  background: radial-gradient(circle at 48% 38%, rgba(65,255,176,.88), rgba(23,222,188,.35) 46%, rgba(127,66,255,.10) 73%, transparent 79%);
  animation: nwAuroraBubbleThree calc(var(--nw-speed) * .91) ease-in-out infinite alternate;
}
.nw-aurora-atmosphere .nw-abubble.b4 {
  right: 18%; bottom: 12%;
  width: 25%;
  background: radial-gradient(circle, rgba(255,100,221,.72), rgba(123,65,255,.20) 55%, transparent 73%);
  filter: blur(13px);
  opacity: calc(.20 + var(--nw-activity) * .28);
  animation: nwAuroraBubbleFour calc(var(--nw-speed) * .73) ease-in-out infinite alternate;
}

.nw-aurora-atmosphere .nw-aribbon {
  left: -18%;
  width: 138%;
  height: 34%;
  border-radius: 50%;
  background:
    linear-gradient(100deg,
      transparent 0%,
      rgba(67,239,255,.00) 9%,
      rgba(67,239,255,.56) 24%,
      rgba(102,255,197,.60) 39%,
      rgba(126,114,255,.52) 58%,
      rgba(241,92,255,.48) 72%,
      rgba(65,219,255,.24) 86%,
      transparent 100%);
  filter: blur(8px) saturate(1.35);
  opacity: calc(.30 + var(--nw-activity) * .34);
  transform-origin: 50% 50%;
}
.nw-aurora-atmosphere .nw-aribbon.r1 {
  top: 29%;
  animation: nwAuroraRibbonOne calc(var(--nw-speed) * .78) ease-in-out infinite alternate;
}
.nw-aurora-atmosphere .nw-aribbon.r2 {
  top: 53%;
  height: 22%;
  opacity: calc(.20 + var(--nw-activity) * .27);
  animation: nwAuroraRibbonTwo calc(var(--nw-speed) * .94) ease-in-out infinite alternate;
}

/* A translucent energy sphere overlays existing circular gauges without replacing their data. */
.nw-aurora-metric :is(.gauge,.metric-ring,.stat-ring,.progress-ring,.donut,.orb,.health-orb,.status-orb,[class*="gauge"],[class*="ring"]) {
  position: relative;
  isolation: isolate;
  box-shadow:
    0 0 18px rgba(81,232,255,.18),
    0 0 calc(24px + 28px * var(--nw-activity)) rgba(155,87,255,.14),
    inset 0 0 20px rgba(72,239,226,.08) !important;
}
.nw-aurora-metric :is(.gauge,.metric-ring,.stat-ring,.progress-ring,.donut,.orb,.health-orb,.status-orb,[class*="gauge"],[class*="ring"])::after {
  content: "";
  position: absolute;
  inset: -5px;
  border-radius: inherit;
  pointer-events: none;
  background:
    conic-gradient(from 0deg,
      rgba(62,236,255,.70),
      rgba(77,255,170,.48),
      rgba(125,91,255,.66),
      rgba(246,91,255,.54),
      rgba(62,236,255,.70));
  -webkit-mask: radial-gradient(farthest-side, transparent calc(100% - 3px), #000 calc(100% - 2px));
  mask: radial-gradient(farthest-side, transparent calc(100% - 3px), #000 calc(100% - 2px));
  opacity: calc(.42 + var(--nw-activity) * .30);
  filter: blur(.2px) drop-shadow(0 0 7px rgba(95,226,255,.40));
  animation: nwAuroraRingSpin calc(var(--nw-speed) * .74) linear infinite;
}

/* Aurora data ribbon: replaces the visual role of a generic static little line. */
.nw-aurora-spark {
  position: absolute;
  z-index: 2;
  left: 5%;
  right: 5%;
  bottom: 7%;
  width: 90%;
  height: 32%;
  overflow: visible;
  pointer-events: none;
  opacity: calc(.46 + var(--nw-activity) * .42);
  filter:
    drop-shadow(0 0 4px rgba(56,238,255,.82))
    drop-shadow(0 0 9px rgba(129,84,255,.46));
  animation: nwAuroraSparkFloat calc(var(--nw-speed) * .62) ease-in-out infinite alternate;
}
.nw-aurora-spark path {
  fill: none;
  stroke-width: 2.2;
  vector-effect: non-scaling-stroke;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-dasharray: 9 5 24 5;
  animation: nwAuroraSparkTravel calc(var(--nw-speed) * .24) linear infinite;
}

/* If the original UI already has a sparkline, make it glow like aurora too. */
.nw-aurora-metric :is(.sparkline,.trend-line,.heartbeat-line,.pulse-line,.mini-chart,.metric-sparkline) {
  filter: drop-shadow(0 0 5px rgba(70,236,255,.75)) drop-shadow(0 0 11px rgba(162,89,255,.34));
}
.nw-aurora-metric svg :is(path,polyline)[stroke]:not([stroke="none"]) {
  filter: drop-shadow(0 0 4px rgba(88,235,255,.64));
  animation: nwAuroraExistingLine calc(var(--nw-speed) * .52) linear infinite;
}

/* Light aurora wash over the whole Live Performance zone. */
.nw-aurora-zone {
  position: relative !important;
  isolation: isolate !important;
  overflow: hidden !important;
}
.nw-aurora-zone::before {
  content: "";
  position: absolute;
  z-index: 0;
  inset: -35%;
  pointer-events: none;
  background:
    radial-gradient(ellipse at 16% 44%, rgba(43,226,255,.13), transparent 34%),
    radial-gradient(ellipse at 60% 28%, rgba(99,255,189,.10), transparent 31%),
    radial-gradient(ellipse at 84% 59%, rgba(187,80,255,.11), transparent 35%);
  filter: blur(18px) saturate(1.3);
  animation: nwAuroraZoneDrift 22s ease-in-out infinite alternate;
}
.nw-aurora-zone > * { position: relative; z-index: 1; }

@keyframes nwAuroraBubbleOne {
  0% { transform: translate3d(-5%,-4%,0) scale(.88); filter: blur(18px) hue-rotate(0deg); }
  45% { transform: translate3d(38%,18%,0) scale(1.13); }
  100% { transform: translate3d(71%,5%,0) scale(.96); filter: blur(22px) hue-rotate(26deg); }
}
@keyframes nwAuroraBubbleTwo {
  0% { transform: translate3d(4%,-4%,0) scale(1.04); filter: blur(19px) hue-rotate(-15deg); }
  55% { transform: translate3d(-48%,31%,0) scale(.92); }
  100% { transform: translate3d(-72%,9%,0) scale(1.11); filter: blur(25px) hue-rotate(22deg); }
}
@keyframes nwAuroraBubbleThree {
  0% { transform: translate3d(-16%,4%,0) scale(.92); }
  50% { transform: translate3d(22%,-27%,0) scale(1.12); }
  100% { transform: translate3d(46%,-6%,0) scale(.99); }
}
@keyframes nwAuroraBubbleFour {
  0% { transform: translate3d(-10%,12%,0) scale(.82); }
  100% { transform: translate3d(54%,-42%,0) scale(1.22); }
}
@keyframes nwAuroraRibbonOne {
  0% { transform: translate3d(-3%,0,0) rotate(-5deg) skewX(-13deg) scaleY(.72); }
  48% { transform: translate3d(3%,-13%,0) rotate(2deg) skewX(9deg) scaleY(1.10); }
  100% { transform: translate3d(7%,8%,0) rotate(-2deg) skewX(-4deg) scaleY(.84); }
}
@keyframes nwAuroraRibbonTwo {
  0% { transform: translate3d(5%,0,0) rotate(4deg) skewX(13deg) scaleY(.74); }
  100% { transform: translate3d(-7%,-20%,0) rotate(-5deg) skewX(-11deg) scaleY(1.12); }
}
@keyframes nwAuroraRingSpin { to { transform: rotate(360deg); } }
@keyframes nwAuroraSparkTravel { to { stroke-dashoffset: -86; } }
@keyframes nwAuroraSparkFloat {
  from { transform: translate3d(-1%,1px,0) scaleY(calc(.72 + var(--nw-activity) * .22)); }
  to { transform: translate3d(1%,-3px,0) scaleY(calc(.88 + var(--nw-activity) * .28)); }
}
@keyframes nwAuroraExistingLine {
  0%,100% { filter: drop-shadow(0 0 3px rgba(64,231,255,.64)) hue-rotate(0deg); }
  50% { filter: drop-shadow(0 0 7px rgba(177,87,255,.70)) hue-rotate(48deg); }
}
@keyframes nwAuroraZoneDrift {
  0% { transform: translate3d(-3%,1%,0) rotate(-1deg) scale(1); }
  100% { transform: translate3d(4%,-3%,0) rotate(2deg) scale(1.06); }
}

@media (max-width: 900px) {
  .nw-aurora-atmosphere .nw-abubble { filter: blur(14px); }
  .nw-aurora-spark { height: 26%; opacity: .64; }
}

@media (prefers-reduced-motion: reduce) {
  .nw-aurora-atmosphere .nw-abubble,
  .nw-aurora-atmosphere .nw-aribbon,
  .nw-aurora-spark,
  .nw-aurora-spark path,
  .nw-aurora-zone::before,
  .nw-aurora-metric svg :is(path,polyline)[stroke]:not([stroke="none"]),
  .nw-aurora-metric :is(.gauge,.metric-ring,.stat-ring,.progress-ring,.donut,.orb,.health-orb,.status-orb,[class*="gauge"],[class*="ring"])::after {
    animation: none !important;
  }
}

/* ===== NETWATCH 0.5.7 AURORA PERFORMANCE END ===== */
'''
css_file.write_text(css.rstrip() + "\n\n" + addon_css.strip() + "\n", encoding="utf-8")

# JS decoration: locate live performance cards without depending on one exact historical DOM layout.
renderer = renderer_file.read_text(encoding="utf-8")
js_begin = "// ===== NETWATCH 0.5.7 AURORA PERFORMANCE BEGIN ====="
js_end = "// ===== NETWATCH 0.5.7 AURORA PERFORMANCE END ====="
if js_begin in renderer and js_end in renderer:
    before = renderer.split(js_begin, 1)[0].rstrip()
    after = renderer.split(js_end, 1)[1].lstrip()
    renderer = before + "\n\n" + after

addon_js = r'''
// ===== NETWATCH 0.5.7 AURORA PERFORMANCE BEGIN =====
(() => {
  const metricWords = /\b(cpu|processor|memory|ram|storage|disk|network|temperature|temp)\b/i;
  const cardSelectors = [
    '.metric-card', '.stat-card', '.monitor-card', '.performance-card',
    '.health-card', '.summary-card', '.gauge-card', '.system-card',
    '[class*="metric-card"]', '[class*="stat-card"]', '[class*="performance-card"]',
    '[class*="monitor-card"]', '[class*="gauge-card"]'
  ].join(',');

  let auroraSerial = 0;
  let decorateTimer = null;

  function visibleEnough(el) {
    if (!(el instanceof HTMLElement)) return false;
    const r = el.getBoundingClientRect();
    return r.width >= 115 && r.height >= 60;
  }

  function activityFromCard(card) {
    const text = (card.textContent || '').replace(/\s+/g, ' ');
    let value = null;
    const pct = text.match(/(-?\d+(?:\.\d+)?)\s*%/);
    if (pct) value = Number(pct[1]) / 100;

    if (value == null && /temperature|\btemp\b/i.test(text)) {
      const temp = text.match(/(-?\d+(?:\.\d+)?)\s*°/);
      if (temp) value = Math.max(0, Math.min(1, (Number(temp[1]) - 20) / 80));
    }

    if (value == null && /memory|ram/i.test(text)) {
      const ratio = text.match(/(\d+(?:\.\d+)?)\s*(?:GB|GiB)\s*\/\s*(\d+(?:\.\d+)?)\s*(?:GB|GiB)/i);
      if (ratio && Number(ratio[2]) > 0) value = Number(ratio[1]) / Number(ratio[2]);
    }

    if (!Number.isFinite(value)) value = .34;
    return Math.max(.10, Math.min(1, value));
  }

  function setActivity(card) {
    const a = activityFromCard(card);
    card.style.setProperty('--nw-activity', a.toFixed(3));
    const speed = 18 - (a * 7.5);
    card.style.setProperty('--nw-speed', `${speed.toFixed(2)}s`);
  }

  function buildAtmosphere(card) {
    if (card.querySelector(':scope > .nw-aurora-atmosphere')) return;
    const layer = document.createElement('div');
    layer.className = 'nw-aurora-atmosphere';
    layer.setAttribute('aria-hidden', 'true');
    layer.innerHTML = `
      <i class="nw-abubble b1"></i>
      <i class="nw-abubble b2"></i>
      <i class="nw-abubble b3"></i>
      <i class="nw-abubble b4"></i>
      <i class="nw-aribbon r1"></i>
      <i class="nw-aribbon r2"></i>`;
    card.prepend(layer);
  }

  function buildSpark(card) {
    if (card.querySelector(':scope > .nw-aurora-spark')) return;
    auroraSerial += 1;
    const gradientId = `nwAuroraMetricGradient${auroraSerial}`;
    const svgNS = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(svgNS, 'svg');
    svg.setAttribute('class', 'nw-aurora-spark');
    svg.setAttribute('viewBox', '0 0 300 60');
    svg.setAttribute('preserveAspectRatio', 'none');
    svg.setAttribute('aria-hidden', 'true');
    svg.innerHTML = `
      <defs>
        <linearGradient id="${gradientId}" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stop-color="#4feaff" stop-opacity="0"/>
          <stop offset="13%" stop-color="#4feaff" stop-opacity=".94"/>
          <stop offset="37%" stop-color="#61ffc0" stop-opacity=".96"/>
          <stop offset="63%" stop-color="#8c72ff" stop-opacity=".96"/>
          <stop offset="82%" stop-color="#f269ff" stop-opacity=".92"/>
          <stop offset="100%" stop-color="#4feaff" stop-opacity="0"/>
        </linearGradient>
      </defs>
      <path d="M0 42 C18 40 25 22 44 30 S73 50 92 33 S122 13 141 28 S171 53 190 32 S221 16 238 29 S268 47 300 24" stroke="url(#${gradientId})"/>`;
    card.append(svg);
  }

  function decorateCard(card) {
    if (!(card instanceof HTMLElement)) return;
    const text = (card.textContent || '').replace(/\s+/g, ' ').trim();
    if (!metricWords.test(text) || !visibleEnough(card)) return;
    card.classList.add('nw-aurora-metric');
    buildAtmosphere(card);
    buildSpark(card);
    setActivity(card);
  }

  function findPerformanceZone() {
    const headings = [...document.querySelectorAll('h1,h2,h3,h4,.title,.section-title,.card-title,.panel-title')];
    const heading = headings.find(el => /live\s+performance|system\s+performance|performance/i.test((el.textContent || '').trim()));
    if (!heading) return;
    let host = heading.closest('.panel,.card,.section,.performance,.performance-panel,.monitor-panel,.glass-panel');
    if (!host) host = heading.parentElement;
    if (host && visibleEnough(host)) host.classList.add('nw-aurora-zone');
  }

  function decorate() {
    findPerformanceZone();
    const candidates = [...document.querySelectorAll(cardSelectors)];
    for (const card of candidates) decorateCard(card);

    // Fallback for builds where the little live-performance bubbles have generic classes.
    // Start from exact metric labels and climb only to a compact single-metric container.
    const labels = [...document.querySelectorAll('div,span,p,strong,b,h3,h4,h5')]
      .filter(el => /^(cpu|processor|memory|ram|storage|disk|network|temperature|temp)$/i.test((el.textContent || '').trim()));
    for (const label of labels) {
      let el = label.parentElement;
      for (let depth = 0; el && depth < 5; depth += 1, el = el.parentElement) {
        const text = (el.textContent || '').replace(/\s+/g, ' ').trim();
        const metricCount = (text.match(/\b(cpu|processor|memory|ram|storage|disk|network|temperature|temp)\b/gi) || []).length;
        const r = el.getBoundingClientRect();
        if (metricCount <= 2 && r.width >= 115 && r.width <= 520 && r.height >= 60 && r.height <= 360) {
          decorateCard(el);
          break;
        }
      }
    }
  }

  function scheduleDecorate() {
    clearTimeout(decorateTimer);
    decorateTimer = setTimeout(decorate, 140);
  }

  function refreshActivity() {
    document.querySelectorAll('.nw-aurora-metric').forEach(setActivity);
  }

  function startAuroraPerformance() {
    decorate();
    setInterval(refreshActivity, 2000);
    const observer = new MutationObserver(scheduleDecorate);
    observer.observe(document.body, { childList: true, subtree: true });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', startAuroraPerformance, { once: true });
  } else {
    startAuroraPerformance();
  }
})();
// ===== NETWATCH 0.5.7 AURORA PERFORMANCE END =====
'''
renderer_file.write_text(renderer.rstrip() + "\n\n" + addon_js.strip() + "\n", encoding="utf-8")
PY

echo "Validating NetWatch v$TARGET_VERSION..."
python3 -c 'import json,sys; json.load(open(sys.argv[1])); print("package.json: PASS")' "$TARGET/package.json"
node --check "$TARGET/src/renderer.js"
echo "renderer.js: PASS"

if [[ -f "$TARGET/src/weather-service.js" ]]; then
  node --check "$TARGET/src/weather-service.js"
  echo "weather-service.js: PASS"
fi
if [[ -f "$TARGET/src/self-updater.js" ]]; then
  node --check "$TARGET/src/self-updater.js"
  echo "self-updater.js: PASS"
fi

echo
echo "PASS: NetWatch Control Hub updated from v$SOURCE_VERSION to v$TARGET_VERSION"
echo "Aurora performance bubbles and aurora data ribbons are installed."
echo "Backup: $BACKUP"
echo
echo "Restart with:"
echo "  $HOME/.local/share/netwatch-system-updater/run.sh"
