// Prism: a quick transfer shows up as item-insert and item-remove by the player at the chest, with the right counts.
// A sort alone moves nothing in or out, so it adds nothing.
import { readFileSync, readdirSync, existsSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import path from 'node:path'
import { Server, Results, bot, quit, sleep, nextWindow, click, close } from './lib.mjs'
import { modrinth } from './fetch.mjs'

const W = 'world', Y = -60, X = 5, Z = 5
const CONFIG = readFileSync(path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../src/main/resources/config.yml'), 'utf8')

function prismRows(dir) {
  // Read-only, from a copy, after the server has stopped and Prism has written its queue.
  const folder = readdirSync(path.join(dir, 'plugins')).find(f => f.toLowerCase() === 'prism')
  const db = folder && path.join(dir, 'plugins', folder, 'prism.db')
  if (!db || !existsSync(db)) throw new Error('no prism.db; plugins has: ' + readdirSync(path.join(dir, 'plugins')).join(', '))
  const sql = `select a.action, i.material, sum(v.affected_item_quantity), p.player from prism_activities v
    join prism_actions a on a.action_id = v.action_id left join prism_items i on i.item_id = v.affected_item_id
    left join prism_players p on p.player_id = v.cause_player_id
    where v.x = ${X} and v.y = ${Y} and v.z = ${Z} and a.action like 'item-%' group by a.action, i.material, p.player order by a.action;`
  return execFileSync('sqlite3', [db, sql], { encoding: 'utf8' }).trim().split('\n').filter(Boolean)
}

export async function prismSuite(version) {
  const r = new Results(`prism-${version}`)
  if (!await modrinth('prism', version) || !await modrinth('nbtapi', version)) {
    r.skip('Prism records quick transfers', `no Prism or NBT-API build for ${version} on Modrinth`)
    return r
  }
  const config = CONFIG.replace('quick-transfer: false', 'quick-transfer: true')
  const s = new Server(version, { config, plugins: ['nbtapi', 'prism'], tag: r.name })
  try {
    await s.prepare()
    await s.start()
    r.check('Portmanteau finds Prism', s.grep(/Quick transfers are recorded in Prism too/).length > 0, 'its startup line')
    const a = await bot(s, 'Alex')
    await s.pmt('tp', 'Alex', W, 0, Y, 0); await sleep(800)
    await s.pmt('clear', 'Alex')
    await s.pmt('place', W, X, Y, Z, 'CHEST')
    await s.run(`item replace block ${X} ${Y} ${Z} container.0 with minecraft:cobblestone 1`, 300)
    await s.run(`item replace block ${X} ${Y} ${Z} container.1 with minecraft:oak_log 5`, 300)
    await s.pmt('setslot', 'Alex', 12, 'COBBLESTONE', 30)
    await s.pmt('setslot', 'Alex', 13, 'DIRT', 7) // the chest holds no dirt, so it stays with Alex

    let w = nextWindow(a); await s.pmt('openblock', 'Alex', W, X, Y, Z); await w
    await click(a, -999, 0, 0, 800) // left-click outside: in
    const inChest = await s.pmt('ledger', 'block', W, X, Y, Z)
    await s.pmt('setslot', 'Alex', 14, 'COBBLESTONE', 1) // out only takes kinds Alex already holds
    await sleep(1500) // past the hotkey cooldown
    // right-click outside: out. mineflayer's own model drops an empty right-click outside before sending it, so send it as is.
    { const win = a.currentWindow, real = win.acceptClick; win.acceptClick = () => []
      try { a.clickWindow(-999, 1, 0).catch(() => {}) } finally { win.acceptClick = real } }
    await sleep(800)
    const outChest = await s.pmt('ledger', 'block', W, X, Y, Z)
    await close(a, 600) // closing sorts the chest, which moves nothing in or out
    await quit(a)
    r.check('the transfers happened', inChest.items === 36 && outChest.items === 5, `chest held 6, then ${inChest.items}, then ${outChest.items} (the logs stay: Alex holds none)`)
    await sleep(8000) // Prism writes its queue every few seconds
    await s.stop()
    const rows = prismRows(s.dir)
    const want = ['item-insert|cobblestone|30|Alex', 'item-remove|cobblestone|31|Alex']
    r.check('Prism has each transfer as the player, with the counts', want.every(x => rows.includes(x)), rows.join('; ') || 'no rows')
    r.check('nothing else was recorded at the chest (the sort added nothing; the dirt and logs never moved)', rows.length === want.length, rows.join('; '))
  } finally {
    await s.stop()
    s.remove()
  }
  return r
}
