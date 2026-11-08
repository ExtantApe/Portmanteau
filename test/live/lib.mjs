// Shared harness for the live tests: one throwaway Paper server on 127.0.0.1, driven from its console, with
// mineflayer bots as players. Nothing here touches anything outside the server folder it makes in the temp directory.
import { spawn, execSync } from 'node:child_process'
import { existsSync, mkdirSync, readdirSync, rmSync, writeFileSync, appendFileSync, copyFileSync, cpSync, statfsSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import mineflayer from 'mineflayer'
import { paper, modrinth, CACHE } from './fetch.mjs'

const HERE = path.dirname(new URL(import.meta.url).pathname)
const ROOT = path.resolve(HERE, '../..')
export const WORK = process.env.PMT_WORK ?? path.join(HERE, 'work')
export const PORT = Number(process.env.PMT_PORT ?? 25620)
const JAVA = process.env.PMT_JAVA ?? '/opt/homebrew/opt/openjdk@25/bin/java'
export const sleep = ms => new Promise(r => setTimeout(r, ms))

function builtJar(dir, re) {
  const libs = path.join(ROOT, dir)
  const found = existsSync(libs) ? readdirSync(libs).filter(f => re.test(f)) : []
  if (found.length !== 1) throw new Error(`Run ./gradlew build first: expected one jar matching ${re} in ${dir}, found ${found.length}`)
  return path.join(libs, found[0])
}

export const PLUGIN = () => builtJar('build/libs', /^Portmanteau-[\d.]+\.jar$/)
export const HELPER = () => builtJar('test/live/helper/build/libs', /^PortmanteauLiveTestHelper\.jar$/)

/** Results for one run: each check passes, fails or is skipped, with a note. */
export class Results {
  constructor(name) { this.name = name; this.items = []; this.started = Date.now() }
  pass(id, note = '') { this.items.push({ id, status: 'pass', note }); console.log(`  PASS ${id}${note ? ' — ' + note : ''}`) }
  fail(id, note = '') { this.items.push({ id, status: 'fail', note }); console.log(`  FAIL ${id} — ${note}`) }
  skip(id, note = '') { this.items.push({ id, status: 'skip', note }); console.log(`  SKIP ${id} — ${note}`) }
  check(id, ok, note = '') { ok ? this.pass(id, note) : this.fail(id, note) }
  async run(id, fn) {
    try { await fn() } catch (e) { this.fail(id, 'threw: ' + (e?.stack ?? e).toString().split('\n').slice(0, 3).join(' | ')) }
  }
  summary() {
    const count = s => this.items.filter(i => i.status === s).length
    return { name: this.name, pass: count('pass'), fail: count('fail'), skip: count('skip'), seconds: Math.round((Date.now() - this.started) / 1000), items: this.items }
  }
}

/**
 * One server. Paper's first-start downloads are kept in work/cache/runtime-<version> and cloned into each new folder,
 * so a fresh server starts in seconds. The folder itself is new each time and deleted afterwards.
 */
const LIVE = new Set()
function emergency(reason) {
  for (const s of LIVE) {
    try { s.proc?.kill('SIGKILL') } catch {}
    try { s.remove() } catch {}
  }
  if (reason) console.log('Stopped and deleted the test server: ' + reason)
}
process.on('uncaughtException', e => { console.log(e); emergency('uncaught error'); process.exit(1) })
process.on('unhandledRejection', e => { console.log(e); emergency('unhandled rejection'); process.exit(1) })
process.on('SIGINT', () => { emergency('interrupted'); process.exit(130) })
process.on('SIGTERM', () => { emergency('terminated'); process.exit(143) })

export class Server {
  constructor(version, { plugins = [], config = null, mvGroups = null, jvm = [], tag = 'run' } = {}) {
    this.version = version
    this.plugins = [...viaPlugins(version), ...plugins]
    this.config = config
    this.mvGroups = mvGroups
    // ViaBackwards can't load its mappings next to Multiverse under Paper's newer plugin loader (a class loader
    // conflict between the two Via jars), so test servers that need Via use the legacy loader.
    this.jvm = viaPlugins(version).length && plugins.length ? ['-Dpaper.useLegacyPluginLoading=true', ...jvm] : jvm
    this.dir = path.join(os.tmpdir(), `portmanteau-live-${version}-${tag}-${process.pid}`)
    this.lines = []
    this.waiters = []
    this.id = 0
    mkdirSync(path.join(WORK, 'logs'), { recursive: true })
    this.log = path.join(WORK, 'logs', `server-${version}-${tag}.log`)
  }

  /** Builds the folder: Paper, the plugins, a flat world, 127.0.0.1 only, offline mode for the bots. */
  async prepare() {
    rmSync(this.dir, { recursive: true, force: true })
    mkdirSync(path.join(this.dir, 'plugins'), { recursive: true })
    const p = await paper(this.version)
    copyFileSync(p.file, path.join(this.dir, 'paper.jar'))
    this.paperBuild = p.build
    const runtime = path.join(CACHE, `runtime-${this.version}`)
    for (const f of ['cache', 'libraries', 'versions']) {
      if (existsSync(path.join(runtime, f))) execSync(`cp -cR "${path.join(runtime, f)}" "${path.join(this.dir, f)}"`)
    }
    copyFileSync(PLUGIN(), path.join(this.dir, 'plugins', 'Portmanteau.jar'))
    copyFileSync(HELPER(), path.join(this.dir, 'plugins', 'PortmanteauLiveTestHelper.jar'))
    this.versions = {}
    for (const slug of this.plugins) {
      const m = await modrinth(slug, this.version)
      if (!m) throw new Error(`No ${slug} build for ${this.version} on Modrinth`)
      copyFileSync(m.file, path.join(this.dir, 'plugins', path.basename(m.file)))
      this.versions[slug] = m.version
    }
    // A throwaway server on this machine only; accepting the EULA here is covered by the owner's standing rule.
    writeFileSync(path.join(this.dir, 'eula.txt'), 'eula=true\n')
    writeFileSync(path.join(this.dir, 'server.properties'), [
      'server-ip=127.0.0.1', `server-port=${PORT}`, 'online-mode=false', 'level-type=minecraft\\:flat',
      'generate-structures=false', 'spawn-protection=0', 'view-distance=4', 'simulation-distance=4', 'max-players=40',
      'difficulty=peaceful', 'enable-rcon=false', 'enable-query=false', 'gamemode=survival', 'motd=Portmanteau live test',
      'network-compression-threshold=-1', 'allow-flight=true', 'white-list=false', 'enforce-whitelist=false'].join('\n') + '\n')
    // Bots join faster without Paper's connection throttle.
    writeFileSync(path.join(this.dir, 'bukkit.yml'), 'settings:\n  connection-throttle: -1\n')
    if (this.config) {
      mkdirSync(path.join(this.dir, 'plugins', 'Portmanteau'), { recursive: true })
      writeFileSync(path.join(this.dir, 'plugins', 'Portmanteau', 'config.yml'), this.config)
    }
    if (this.mvGroups) this.writeGroups(this.mvGroups)
  }

  writeGroups(groups) {
    const dir = path.join(this.dir, 'plugins', 'Multiverse-Inventories')
    mkdirSync(dir, { recursive: true })
    let y = 'groups:\n'
    for (const [name, worlds] of Object.entries(groups)) y += `  ${name}:\n    worlds:\n${worlds.map(w => `    - ${w}`).join('\n')}\n    shares:\n    - all\n`
    writeFileSync(path.join(dir, 'groups.yml'), y)
  }

  writeConfig(text) {
    writeFileSync(path.join(this.dir, 'plugins', 'Portmanteau', 'config.yml'), text)
  }

  async start() {
    LIVE.add(this)
    this.lines = []
    this.proc = spawn(JAVA, ['-Xms1G', '-Xmx3G', ...this.jvm, '-jar', 'paper.jar', '--nogui'], { cwd: this.dir, stdio: ['pipe', 'pipe', 'pipe'] })
    let buf = ''
    const onData = d => {
      buf += d.toString()
      let i
      while ((i = buf.indexOf('\n')) >= 0) {
        const line = buf.slice(0, i); buf = buf.slice(i + 1)
        this.lines.push(line)
        appendFileSync(this.log, line + '\n')
        for (const w of [...this.waiters]) if (w.re.test(line)) { this.waiters.splice(this.waiters.indexOf(w), 1); w.resolve(line) }
      }
    }
    this.proc.stdout.on('data', onData)
    this.proc.stderr.on('data', onData)
    this.exited = new Promise(r => this.proc.on('exit', code => r(code)))
    await this.waitFor(/Done \(\d/, 300_000)
    await this.waitFor(/PMT-READY/, 1, true)
    // Keep Paper's downloads for the next fresh server.
    const runtime = path.join(CACHE, `runtime-${this.version}`)
    if (!existsSync(runtime)) {
      mkdirSync(runtime, { recursive: true })
      for (const f of ['cache', 'libraries', 'versions']) if (existsSync(path.join(this.dir, f))) execSync(`cp -cR "${path.join(this.dir, f)}" "${path.join(runtime, f)}"`)
    }
  }

  /** Resolves with the first new line matching, or with an earlier one if {@code past} is set. */
  waitFor(re, timeout = 30_000, past = false) {
    if (past) { const hit = this.lines.find(l => re.test(l)); if (hit) return Promise.resolve(hit) }
    return new Promise((resolve, reject) => {
      const w = { re, resolve }
      this.waiters.push(w)
      setTimeout(() => {
        const i = this.waiters.indexOf(w)
        if (i >= 0) { this.waiters.splice(i, 1); reject(new Error(`Timed out waiting for ${re}`)) }
      }, timeout)
    })
  }

  mark() { return this.lines.length }
  since(mark) { return this.lines.slice(mark) }
  grep(re, mark = 0) { return this.lines.slice(mark).filter(l => re.test(l)) }

  console(command) { this.proc.stdin.write(command + '\n') }

  /** Runs a helper command and returns its JSON answer. */
  async pmt(...args) {
    const id = `c${++this.id}`
    const done = this.waitFor(new RegExp(`PMT-RESULT ${id} `), 120_000)
    this.console(`pmt ${id} ${args.join(' ')}`)
    const line = await done
    const out = JSON.parse(line.slice(line.indexOf(`PMT-RESULT ${id} `) + `PMT-RESULT ${id} `.length))
    if (out.error) throw new Error(`pmt ${args.join(' ')}: ${out.error}`)
    return out
  }

  /** Runs a console command and waits for its output to settle. */
  async run(command, wait = 400) {
    const m = this.mark()
    this.console(command)
    await sleep(wait)
    return this.since(m)
  }

  async stop() {
    if (!this.proc || this.proc.exitCode !== null) return
    this.console('stop')
    const timer = setTimeout(() => this.proc.kill('SIGKILL'), 90_000)
    await this.exited
    clearTimeout(timer)
  }

  /** Kills the server at once, as a crash or power cut would. Nothing gets to save. */
  async kill9() {
    this.proc.kill('SIGKILL')
    await this.exited
  }

  remove() {
    LIVE.delete(this)
    rmSync(this.dir, { recursive: true, force: true, maxRetries: 5, retryDelay: 500 })
  }
}

/** A bot player. Collects chat and action bar text so tests can read what players were told. */
export const CLIENT = process.env.PMT_CLIENT ?? '26.1'

/** Bots speak 26.1; newer servers reach them through ViaVersion and ViaBackwards. */
export function viaPlugins(version) {
  return version.startsWith('26.1') ? [] : ['viaversion', 'viabackwards']
}

export async function bot(server, name, { version = CLIENT } = {}) {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: PORT, username: name, auth: 'offline', version, hideErrors: true })
  if (viaPlugins(server.version).length) {
    // Through ViaBackwards, the movement packets mineflayer sends after a teleport come out as moves the server rejects
    // and kicks for. The bots never walk, and the server knows where it put them, so they send no movement at all.
    const write = b._client.write.bind(b._client)
    b._client.write = (packet, params) => /^(position|position_look|look|flying)$/.test(packet) ? undefined : write(packet, params)
  }
  b.chat_ = []
  b.bar_ = []
  b.on('messagestr', (m, position) => (position === 'game_info' ? b.bar_ : b.chat_).push(m))
  b.on('actionBar', m => b.bar_.push(m.toString()))
  b._client.on('packet', (d, meta) => { if (meta.name === 'action_bar' || meta.name === 'set_action_bar_text') b.bar_.push(JSON.stringify(d)) })
  b.on('error', e => console.log(`  [bot ${name}] ${e.message}`))
  await new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error(`${name} didn't spawn`)), 60_000)
    b.once('spawn', () => { clearTimeout(t); resolve() })
    b.once('kicked', r => { clearTimeout(t); reject(new Error(`${name} was kicked: ${JSON.stringify(r)}`)) })
    b.once('end', r => { clearTimeout(t); reject(new Error(`${name} disconnected: ${r}`)) })
  })
  // The tests place and teleport bots; they never walk. Physics off keeps them from sending moves that ViaBackwards
  // translates badly, which the server kicks as invalid.
  b.physicsEnabled = false
  await sleep(500)
  return b
}

export async function quit(b) {
  if (!b?._client || b._client.ended) return
  const ended = new Promise(r => b.once('end', r))
  b.quit()
  await Promise.race([ended, sleep(5000)])
}

/** Right-clicks a block and waits for the window to open. */
export async function openBlock(b, x, y, z, timeout = 5000) {
  const { Vec3 } = await import('vec3')
  const block = b.blockAt(new Vec3(x, y, z))
  if (!block) throw new Error(`${b.username} can't see the block at ${x} ${y} ${z}`)
  let done
  const opened = new Promise(resolve => {
    const t = setTimeout(() => resolve(null), timeout)
    done = w => { clearTimeout(t); resolve(w) }
    b.once('windowOpen', done)
  })
  try { await b.lookAt(block.position.offset(0.5, 0.5, 0.5), true) } catch {}
  b.activateBlock(block).catch(() => {})
  const w = await opened
  if (!w) { b.removeListener('windowOpen', done); throw new Error(`No window opened at ${x} ${y} ${z} (block ${block.name}, bot at ${b.entity.position})`) }
  return w
}

/** Whether bots can right-click blocks on this server. Through ViaBackwards on 26.2 and 26.3 they can't. */
export const realClicks = version => viaPlugins(version).length === 0

/** Opens a container for the bot: a real right-click where it works, otherwise the server opens it for them. */
export async function openContainer(server, b, world, x, y, z) {
  if (realClicks(server.version)) return openBlock(b, x, y, z)
  const w = nextWindow(b)
  await server.pmt('openblock', b.username, world, x, y, z)
  return w
}

/**
 * Right-clicks a trunk: a real click where it works, otherwise the helper fires the same interact event Paper fires for
 * a click, so the trunk's own listener runs.
 */
export async function useBlock(server, b, world, x, y, z, timeout = 5000) {
  if (realClicks(server.version)) return openBlock(b, x, y, z, timeout)
  const w = nextWindow(b, timeout).catch(() => null)
  await server.pmt('interact', b.username, world, x, y, z)
  const got = await w
  if (!got) throw new Error(`No window opened at ${x} ${y} ${z}`)
  return got
}

/** Waits for the server to open a window for the bot, after a helper command opened it. */
export function nextWindow(b, timeout = 5000) {
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('No window opened')), timeout)
    b.once('windowOpen', w => { clearTimeout(t); resolve(w) })
  })
}

/** Sends a window click without waiting for the server to answer, since a cancelled click changes nothing. */
export async function click(b, slot, button, mode, wait = 250) {
  const window = b.currentWindow || b.inventory
  if (mode === 5 || mode === 6) {
    // mineflayer's own model of these clicks throws before the packet is sent. Skip the model and send the click;
    // the server works out what it does and sends the slots back.
    const real = window.acceptClick
    window.acceptClick = () => []
    try { b.clickWindow(slot, button, mode).catch(() => {}) } finally { window.acceptClick = real }
  } else {
    b.clickWindow(slot, button, mode).catch(() => {})
  }
  await sleep(wait)
}

/** Closes the bot's window the way a player pressing Escape does. */
export async function close(b, wait = 300) {
  const w = b.currentWindow
  if (w) b.closeWindow(w)
  else b._client.write('close_window', { windowId: 0 })
  await sleep(wait)
}

export function freeGb() {
  const s = statfsSync(os.tmpdir())
  return Math.round(s.bavail * s.bsize / 1e9)
}

export function saveResults(results) {
  mkdirSync(WORK, { recursive: true })
  const file = path.join(WORK, `results-${results.name}.json`)
  writeFileSync(file, JSON.stringify(results.summary(), null, 2))
  return file
}

export { cpSync }
