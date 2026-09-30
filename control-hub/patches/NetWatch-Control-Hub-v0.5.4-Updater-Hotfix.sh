#!/usr/bin/env bash
set -euo pipefail

TARGET="${NETWATCH_INSTALLED_DIR:-${1:-$HOME/.local/share/netwatch-system-updater}}"
FILE="$TARGET/src/self-updater.js"

[[ -f "$TARGET/package.json" ]] || {
  echo "NetWatch Control Hub not found: $TARGET"
  exit 2
}

CURRENT="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["version"])' "$TARGET/package.json")"
if [[ "$CURRENT" != "0.5.4" && "$CURRENT" != "0.5.5" ]]; then
  echo "This hotfix expects NetWatch v0.5.4 or v0.5.5. Found v$CURRENT"
  exit 3
fi

STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="$TARGET/.netwatch_patch_backups/$STAMP-updater-hotfix"
mkdir -p "$BACKUP/src"
cp -a "$FILE" "$BACKUP/src/self-updater.js"

cat > "$FILE" <<'JS'
const fs = require('fs');
const path = require('path');
const os = require('os');
const crypto = require('crypto');
const http = require('http');
const https = require('https');
const { spawn, execFile } = require('child_process');
const { promisify } = require('util');

const execFileAsync = promisify(execFile);

const DEFAULT_MANIFEST_URL = 'https://raw.githubusercontent.com/Whenever1061/NetWatchPhone-Build/main/control-hub/update.json';

function compareVersions(a, b) {
  const aa = String(a || '0').replace(/^v/i, '').split('.').map((x) => parseInt(x, 10) || 0);
  const bb = String(b || '0').replace(/^v/i, '').split('.').map((x) => parseInt(x, 10) || 0);
  const n = Math.max(aa.length, bb.length);
  for (let i = 0; i < n; i += 1) {
    const av = aa[i] || 0;
    const bv = bb[i] || 0;
    if (av > bv) return 1;
    if (av < bv) return -1;
  }
  return 0;
}

function sha256File(filePath) {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash('sha256');
    const stream = fs.createReadStream(filePath);
    stream.on('error', reject);
    stream.on('data', (chunk) => hash.update(chunk));
    stream.on('end', () => resolve(hash.digest('hex')));
  });
}

function requestJson(url, redirects = 0) {
  return new Promise((resolve, reject) => {
    if (redirects > 5) return reject(new Error('Too many redirects'));
    let parsed;
    try { parsed = new URL(url); } catch { return reject(new Error('Invalid update URL')); }
    if (!['https:', 'http:'].includes(parsed.protocol)) return reject(new Error('Only HTTP(S) update URLs are supported'));
    const client = parsed.protocol === 'https:' ? https : http;
    const req = client.get(parsed, { headers: { 'User-Agent': 'NetWatch-Control-Hub-Updater' } }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        const next = new URL(res.headers.location, parsed).toString();
        res.resume();
        return resolve(requestJson(next, redirects + 1));
      }
      if (res.statusCode !== 200) {
        res.resume();
        return reject(new Error(`Update server returned HTTP ${res.statusCode}`));
      }
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => {
        try { resolve(JSON.parse(Buffer.concat(chunks).toString('utf8'))); }
        catch { reject(new Error('Update manifest is not valid JSON')); }
      });
    });
    req.on('error', reject);
    req.setTimeout(15000, () => req.destroy(new Error('Update request timed out')));
  });
}

function downloadFile(url, destination, onProgress, redirects = 0) {
  return new Promise((resolve, reject) => {
    if (redirects > 5) return reject(new Error('Too many redirects'));
    const parsed = new URL(url);
    const client = parsed.protocol === 'https:' ? https : http;
    const req = client.get(parsed, { headers: { 'User-Agent': 'NetWatch-Control-Hub-Updater' } }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        const next = new URL(res.headers.location, parsed).toString();
        res.resume();
        return resolve(downloadFile(next, destination, onProgress, redirects + 1));
      }
      if (res.statusCode !== 200) {
        res.resume();
        return reject(new Error(`Download failed with HTTP ${res.statusCode}`));
      }
      const total = Number(res.headers['content-length'] || 0);
      let received = 0;
      const out = fs.createWriteStream(destination, { mode: 0o600 });
      res.on('data', (chunk) => {
        received += chunk.length;
        if (onProgress) onProgress({ received, total, percent: total ? Math.round((received / total) * 100) : null });
      });
      res.pipe(out);
      out.on('finish', () => out.close(() => resolve(destination)));
      out.on('error', reject);
    });
    req.on('error', reject);
    req.setTimeout(30000, () => req.destroy(new Error('Update download timed out')));
  });
}

class SelfUpdater {
  constructor({ app, dialog, getWindow }) {
    this.app = app;
    this.dialog = dialog;
    this.getWindow = getWindow;
    this.appRoot = path.resolve(__dirname, '..');
    this.packagePath = path.join(this.appRoot, 'package.json');
    this.userData = app.getPath('userData');
    this.configPath = path.join(this.userData, 'self-updater-config.json');
    this.statePath = path.join(this.userData, 'self-updater-state.json');
    this.resultPath = path.join(this.userData, 'self-updater-last-result.json');
    this.stagingRoot = path.join(this.userData, 'self-updater-staging');
    this.backupRoot = path.join(this.userData, 'self-updater-backups');
    this.staged = null;
    fs.mkdirSync(this.stagingRoot, { recursive: true });
    fs.mkdirSync(this.backupRoot, { recursive: true });
  }

  currentVersion() {
    try { return JSON.parse(fs.readFileSync(this.packagePath, 'utf8')).version || '0.0.0'; }
    catch { return '0.0.0'; }
  }

  readConfig() {
    let saved = {};
    try { saved = JSON.parse(fs.readFileSync(this.configPath, 'utf8')); } catch {}
    return {
      manifestUrl: process.env.NETWATCH_CONTROL_HUB_UPDATE_URL || saved.manifestUrl || DEFAULT_MANIFEST_URL,
      channel: saved.channel || 'stable',
    };
  }

  writeConfig(next) {
    const current = this.readConfig();
    const merged = { ...current, ...next };
    if (merged.manifestUrl && !/^https?:\/\//i.test(merged.manifestUrl)) throw new Error('Update manifest URL must start with http:// or https://');
    fs.writeFileSync(this.configPath, JSON.stringify(merged, null, 2));
    return merged;
  }

  readState() {
    try { return JSON.parse(fs.readFileSync(this.statePath, 'utf8')); } catch { return {}; }
  }

  writeState(next) {
    const merged = { ...this.readState(), ...next, updatedAt: new Date().toISOString() };
    fs.writeFileSync(this.statePath, JSON.stringify(merged, null, 2));
    return merged;
  }

  status() {
    const config = this.readConfig();
    const state = this.readState();
    let installResult = null;
    try { installResult = JSON.parse(fs.readFileSync(this.resultPath, 'utf8')); } catch {}
    return {
      currentVersion: this.currentVersion(),
      manifestUrl: config.manifestUrl,
      channel: config.channel,
      staged: this.staged ? {
        version: this.staged.version,
        file: this.staged.file,
        sha256: this.staged.sha256,
        source: this.staged.source,
        notes: this.staged.notes || '',
        type: this.staged.type || 'zip',
      } : null,
      lastCheck: state.lastCheck || null,
      lastResult: state.lastResult || null,
      lastInstalledVersion: installResult?.ok ? installResult.version : (state.lastInstalledVersion || null),
      installResult,
    };
  }

  async checkOnline() {
    const config = this.readConfig();
    if (!config.manifestUrl) throw new Error('No update manifest URL is configured. Add one in the updater page, or choose a local update package.');
    const manifest = await requestJson(config.manifestUrl);
    for (const key of ['version', 'url', 'sha256']) {
      if (!manifest[key]) throw new Error(`Update manifest is missing ${key}`);
    }
    if (!/^[a-f0-9]{64}$/i.test(String(manifest.sha256))) throw new Error('Update manifest SHA-256 is invalid');
    const current = this.currentVersion();
    const available = compareVersions(manifest.version, current) > 0;
    this.writeState({ lastCheck: new Date().toISOString(), lastResult: available ? `Version ${manifest.version} available` : `Current version ${current} is up to date` });
    return {
      currentVersion: current,
      available,
      version: manifest.version,
      url: manifest.url,
      sha256: String(manifest.sha256).toLowerCase(),
      notes: manifest.notes || '',
      publishedAt: manifest.publishedAt || null,
      type: String(manifest.type || 'zip').toLowerCase(),
    };
  }

  async downloadOnline(manifest, onProgress) {
    if (!manifest || !manifest.url || !manifest.sha256 || !manifest.version) throw new Error('No valid online update is selected');
    const type = String(manifest.type || 'zip').toLowerCase();
    if (!['zip', 'patch'].includes(type)) throw new Error(`Unsupported update type: ${type}`);
    const dir = fs.mkdtempSync(path.join(this.stagingRoot, 'download-'));
    const file = path.join(dir, type === 'patch' ? `NetWatch-Control-Hub-${manifest.version}.sh` : `NetWatch-Control-Hub-${manifest.version}.zip`);
    await downloadFile(manifest.url, file, onProgress);
    const digest = await sha256File(file);
    if (digest.toLowerCase() !== String(manifest.sha256).toLowerCase()) {
      fs.rmSync(dir, { recursive: true, force: true });
      throw new Error('Downloaded update failed SHA-256 verification');
    }
    if (type === 'patch') return this.stagePatch(file, { source: 'online', expectedVersion: manifest.version, notes: manifest.notes || '', knownHash: digest });
    return this.stageArchive(file, { source: 'online', expectedVersion: manifest.version, notes: manifest.notes || '', knownHash: digest });
  }

  async stagePatch(file, options = {}) {
    if (!fs.existsSync(file)) throw new Error('Update patch does not exist');
    const stat = fs.statSync(file);
    if (stat.size > 2 * 1024 * 1024) throw new Error('Update patch is unexpectedly large');
    const text = fs.readFileSync(file, 'utf8');
    if (!text.includes('NETWATCH_CONTROL_HUB_PATCH=1')) throw new Error('Patch identity marker is missing');
    const match = text.match(/TARGET_VERSION=["']?([0-9]+(?:\.[0-9]+){2,3})/);
    if (!match) throw new Error('Patch target version is missing');
    const version = match[1];
    if (options.expectedVersion && String(version) !== String(options.expectedVersion)) throw new Error(`Patch version ${version} does not match manifest version ${options.expectedVersion}`);
    await execFileAsync('/bin/bash', ['-n', file], { timeout: 15000 });
    const sha = options.knownHash || await sha256File(file);
    const dir = fs.mkdtempSync(path.join(this.stagingRoot, 'patch-'));
    const stagedPatch = path.join(dir, 'update.sh');
    fs.copyFileSync(file, stagedPatch);
    fs.chmodSync(stagedPatch, 0o700);
    this.staged = {
      version, file, sha256: sha, source: options.source || 'local', notes: options.notes || '',
      type: 'patch', patchFile: stagedPatch, stagingDir: dir,
    };
    return { canceled: false, ...this.status().staged, newer: compareVersions(version, this.currentVersion()) > 0 };
  }

  async chooseLocal() {
    const options = {
      title: 'Choose NetWatch Control Hub update',
      properties: ['openFile'],
      filters: [{ name: 'NetWatch update packages', extensions: ['zip'] }],
    };
    const win = this.getWindow();
    const result = win ? await this.dialog.showOpenDialog(win, options) : await this.dialog.showOpenDialog(options);
    if (result.canceled || !result.filePaths[0]) return { canceled: true };
    return this.stageArchive(result.filePaths[0], { source: 'local' });
  }

  async stageArchive(archive, options = {}) {
    if (!fs.existsSync(archive)) throw new Error('Update archive does not exist');
    const sha = options.knownHash || await sha256File(archive);
    const dir = fs.mkdtempSync(path.join(this.stagingRoot, 'stage-'));
    const extract = path.join(dir, 'payload');
    fs.mkdirSync(extract, { recursive: true });
    await execFileAsync('unzip', ['-q', archive, '-d', extract], { timeout: 120000 });

    const roots = fs.readdirSync(extract, { withFileTypes: true }).filter((e) => e.isDirectory());
    let payloadRoot = extract;
    if (!fs.existsSync(path.join(payloadRoot, 'package.json')) && roots.length === 1 && fs.existsSync(path.join(extract, roots[0].name, 'package.json'))) {
      payloadRoot = path.join(extract, roots[0].name);
    }
    const payloadPackage = path.join(payloadRoot, 'package.json');
    const payloadInstaller = path.join(payloadRoot, 'install.sh');
    const payloadRun = path.join(payloadRoot, 'run.sh');
    if (!fs.existsSync(payloadPackage) || !fs.existsSync(payloadInstaller) || !fs.existsSync(payloadRun)) {
      fs.rmSync(dir, { recursive: true, force: true });
      throw new Error('This ZIP is not a valid NetWatch Control Hub update package');
    }
    const info = JSON.parse(fs.readFileSync(payloadPackage, 'utf8'));
    if (info.name !== 'netwatch-system-updater') throw new Error('Package identity does not match NetWatch Control Hub');
    if (!info.version) throw new Error('Update package has no version');
    if (options.expectedVersion && String(info.version) !== String(options.expectedVersion)) throw new Error(`Package version ${info.version} does not match manifest version ${options.expectedVersion}`);

    this.staged = {
      version: info.version,
      file: archive,
      sha256: sha,
      source: options.source || 'local',
      notes: options.notes || '',
      payloadRoot,
      stagingDir: dir,
      type: 'zip',
    };
    return { canceled: false, ...this.status().staged, newer: compareVersions(info.version, this.currentVersion()) > 0 };
  }

  discard() {
    if (this.staged?.stagingDir) fs.rmSync(this.staged.stagingDir, { recursive: true, force: true });
    this.staged = null;
    return this.status();
  }

  async installStaged() {
    if (!this.staged) throw new Error('No update package has been staged');
    const staged = this.staged;
    const installedDir = this.appRoot;
    const backupDir = path.join(this.backupRoot, `${new Date().toISOString().replace(/[:.]/g, '-')}-v${this.currentVersion()}`);
    const helper = path.join(this.userData, `apply-self-update-${Date.now()}.sh`);
    const resultFile = this.resultPath;

    const q = (s) => `'${String(s).replace(/'/g, `'"'"'`)}'`;
    const script = staged.type === 'patch'
      ? `#!/usr/bin/env bash\nset -u\nPARENT=${process.pid}\nPATCH=${q(staged.patchFile)}\nINSTALLED=${q(installedDir)}\nBACKUP=${q(backupDir)}\nRESULT=${q(resultFile)}\nVERSION=${q(staged.version)}\nfor i in $(seq 1 120); do\n  if ! kill -0 "$PARENT" 2>/dev/null; then break; fi\n  sleep 0.5\ndone\nmkdir -p "$BACKUP"\nif command -v rsync >/dev/null 2>&1; then rsync -a --exclude=node_modules/ "$INSTALLED/" "$BACKUP/" || true; else cp -a "$INSTALLED/." "$BACKUP/" || true; fi\nif NETWATCH_INSTALLED_DIR="$INSTALLED" /bin/bash "$PATCH" >"$RESULT.log" 2>&1; then\n  printf '{"ok":true,"version":"%s","time":"%s"}\\n' "$VERSION" "$(date -Iseconds)" > "$RESULT"\n  nohup "$HOME/.local/share/netwatch-system-updater/run.sh" >/dev/null 2>&1 &\n  exit 0\nelse\n  code=$?\n  printf '{"ok":false,"version":"%s","code":%s,"time":"%s"}\\n' "$VERSION" "$code" "$(date -Iseconds)" > "$RESULT"\n  exit "$code"\nfi\n`
      : `#!/usr/bin/env bash\nset -u\nPARENT=${process.pid}\nPAYLOAD=${q(staged.payloadRoot)}\nINSTALLED=${q(installedDir)}\nBACKUP=${q(backupDir)}\nRESULT=${q(resultFile)}\nVERSION=${q(staged.version)}\nfor i in $(seq 1 120); do\n  if ! kill -0 "$PARENT" 2>/dev/null; then break; fi\n  sleep 0.5\ndone\nmkdir -p "$BACKUP"\nif command -v rsync >/dev/null 2>&1; then\n  rsync -a --exclude=node_modules/ "$INSTALLED/" "$BACKUP/" || true\nelse\n  cp -a "$INSTALLED/." "$BACKUP/" || true\nfi\ncd "$PAYLOAD" || exit 20\nchmod +x install.sh run.sh\nif ./install.sh >"$RESULT.log" 2>&1; then\n  printf '{"ok":true,"version":"%s","time":"%s"}\\n' "$VERSION" "$(date -Iseconds)" > "$RESULT"\n  nohup "$HOME/.local/share/netwatch-system-updater/run.sh" >/dev/null 2>&1 &\n  exit 0\nelse\n  code=$?\n  printf '{"ok":false,"version":"%s","code":%s,"time":"%s"}\\n' "$VERSION" "$code" "$(date -Iseconds)" > "$RESULT"\n  exit "$code"\nfi\n`;
    fs.writeFileSync(helper, script, { mode: 0o700 });
    this.writeState({ lastResult: `Installing version ${staged.version}` });
    const child = spawn('/bin/bash', [helper], { detached: true, stdio: 'ignore' });
    child.unref();
    return { ok: true, quitting: true, version: staged.version };
  }
}

module.exports = { SelfUpdater, compareVersions };
JS

node --check "$FILE"

echo
echo "NetWatch self-updater hotfix installed."
echo "Patch releases are now handled as .sh patches instead of ZIP archives."
echo "Backup: $BACKUP"
echo
echo "Restart NetWatch, then use Hub Updater -> Check for updates -> Download & Verify."
