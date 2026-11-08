// CoreProtect: rolling back and restoring a player's container changes after a sort neither duplicates nor loses.
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { Server, Results, bot, quit, sleep, nextWindow, click, close } from './lib.mjs'
import { modrinth } from './fetch.mjs'

const W = 'world', Y = -60
const CONFIG = readFileSync(path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../src/main/resources/config.yml'), 'utf8')

export async function coreprotectSuite(version) {
  const r = new Results(`coreprotect-${version}`)
  if (!await modrinth('coreprotect', version)) {
    r.skip('CoreProtect rollbacks', `no CoreProtect build for ${version} on Modrinth`)
    return r
  }
  const s = new Server(version, { config: CONFIG, plugins: ['coreprotect'], tag: r.name })
  const chest = () => s.pmt('ledger', 'block', W, 3, Y, 3)
  const co = async cmd => {
    const m = s.mark()
    s.console(cmd)
    await s.waitFor(/[Rr]ollback|[Rr]estore|completed|Time taken/, 30_000).catch(() => {})
    await sleep(1500)
    return s.since(m)
  }
  try {
    await s.prepare()
    await s.start()
    const a = await bot(s, 'Alex')
    await s.pmt('tp', 'Alex', W, 0, Y, 0); await sleep(800)
    await s.pmt('place', W, 3, Y, 3, 'CHEST')
    const c0 = await s.pmt('fill', W, 3, Y, 3, 7, 20)

    // A sort alone: CoreProtect must see no change in what's in the chest.
    let w = nextWindow(a); await s.pmt('openblock', 'Alex', W, 3, Y, 3); await w
    await close(a, 600)
    const sorted = await chest()
    await sleep(3000)
    await co('co rollback u:Alex t:1h r:#global #silent')
    const rolled = await chest()
    await co('co restore u:Alex t:1h r:#global #silent')
    const restored = await chest()
    r.check('a sort alone: rollback and restore change no counts', sorted.ledger === c0.ledger && rolled.ledger === c0.ledger && restored.ledger === c0.ledger,
      `${c0.items} items throughout`)

    // A real take, then a sort: rollback puts back exactly what was taken, and restore takes it out again.
    w = nextWindow(a); await s.pmt('openblock', 'Alex', W, 3, Y, 3); const win = await w
    const filled = win.slots.slice(0, win.inventoryStart).findIndex(Boolean)
    await click(a, filled, 0, 1, 500)
    await close(a, 600)
    const took = (await s.pmt('ledger', 'player', 'Alex')).items
    const after = await chest()
    await sleep(3000)
    await co('co rollback u:Alex t:1h r:#global #silent')
    const rolled2 = await chest()
    await co('co restore u:Alex t:1h r:#global #silent')
    const restored2 = await chest()
    r.check('a take, then a sort: rollback and restore move exactly what was taken', after.items === c0.items - took
      && rolled2.items === c0.items && restored2.items === after.items,
      `took ${took}; chest ${c0.items} → ${after.items}, rolled back to ${rolled2.items}, restored to ${restored2.items}. CoreProtect never touches the player's own inventory, so a rollback leaves the taken items with them too: that's CoreProtect's own behaviour`)
    await quit(a)
  } finally {
    await s.stop()
    s.remove()
  }
  return r
}
