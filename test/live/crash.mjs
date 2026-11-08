// Crashes and stops in the middle of moving items, for the trunk and for sorting. After each restart every item is
// counted: nothing may be lost or duplicated.
import { writeFileSync } from 'node:fs'
import path from 'node:path'
import { Server, Results, bot, quit, sleep, useBlock, openContainer, click, close } from './lib.mjs'
import { trunkConfig, setupWorlds, GROUPS, moveIn, place } from './trunk.mjs'

const Y = -60
const SCOPE = 'group:adults_a'

export async function crashSuite(version) {
  const r = new Results(`crash-${version}`)
  const s = new Server(version, { config: trunkConfig(), plugins: ['multiverse-core', 'multiverse-inventories'], mvGroups: GROUPS, tag: r.name })
  let a
  const join = async () => {
    a = await bot(s, 'Alex')
    await s.pmt('tp', 'Alex', 'world', 0, Y, 0)
    await sleep(800)
  }
  const restart = async how => {
    if (a) { try { a.end() } catch {} a = null }
    if (how === 'kill') await s.kill9(); else await s.stop()
    await s.start()
    await setupWorlds(s)
    await join()
  }
  /** Every item the player owns in this group: carried, on the cursor, and in their trunk. */
  const total = async () => (await s.pmt('ledger', 'player', 'Alex')).items + (await s.pmt('ledger', 'trunk', 'Alex', SCOPE)).items
  try {
    await s.prepare()
    // Save players every tick, so a save is as likely as possible to land between a move and the trunk's own save.
    writeFileSync(path.join(s.dir, 'bukkit.yml'), 'settings:\n  connection-throttle: -1\nticks-per:\n  autosave: 1\n')
    await s.start()
    await setupWorlds(s)
    await join()
    await place(s, 'Alex', 'world', 2, Y, 0)

    const cases = [
      ['kill -9 at once after a move', 'kill', 0, false],
      ['kill -9 one tick after a move', 'kill', 50, false],
      ['kill -9 a quarter second after a move', 'kill', 250, false],
      ['kill -9 two seconds after a move', 'kill', 2000, false],
      ['kill -9 just after a forced player save, right after a move', 'kill', 0, true],
      ['kill -9 after a forced save, a tick after a move', 'kill', 50, true],
      ['stop with the trunk open, mid-move', 'stop', 0, false],
    ]
    let round = 0
    for (const [label, how, delay, forceSave] of cases) {
      for (let rep = 0; rep < 3; rep++) {
        const id = `${label} (${rep + 1} of 3)`
        await r.run(id, async () => {
          round++
          await s.pmt('clear', 'Alex')
          for (let i = 0; i < 4; i++) await s.pmt('setslot', 'Alex', 9 + i, ['STONE', 'BREAD', 'DIAMOND', 'ARROW'][i], 5 + i + round)
          await s.pmt('saveplayer', 'Alex')
          await sleep(200)
          const before = await total()
          const win = await useBlock(s, a, 'world', 2, Y, 0)
          const slot = win.slots.findIndex((x, j) => j >= win.inventoryStart && x)
          a.clickWindow(slot, 0, 1).catch(() => {})
          if (forceSave) s.console('pmt x saveplayer Alex')
          if (delay) await sleep(delay)
          await restart(how)
          const after = await total()
          r.check(id, after === before, `${before} items before, ${after} after the restart`)
        })
      }
    }

    // Sorting can't move items between inventories, so a crash can only roll a chest back to its last save.
    await r.run('kill -9 straight after sorting a chest', async () => {
      await s.pmt('place', 'world', -2, Y, 2, 'CHEST')
      await s.pmt('fill', 'world', -2, Y, 2, 42, 25)
      await s.run('save-all flush', 1500)
      const before = await s.pmt('ledger', 'block', 'world', -2, Y, 2)
      const win = await openContainer(s, a, 'world', -2, Y, 2)
      const empty = win.slots.slice(0, win.inventoryStart).findIndex(x => !x)
      a.clickWindow(empty, 0, 1).catch(() => {})
      await sleep(100)
      await restart('kill')
      const after = await s.pmt('ledger', 'block', 'world', -2, Y, 2)
      r.check('kill -9 straight after sorting a chest', after.ledger === before.ledger, `${before.items} items, same identities after the restart`)
    })

    // A world change with a chest open never sorts into the wrong group's inventory.
    await r.run('Multiverse-Inventories: teleport between groups with a chest open', async () => {
      await s.pmt('place', 'world', -4, Y, 2, 'CHEST')
      await s.pmt('fill', 'world', -4, Y, 2, 43, 10)
      await s.pmt('fillplayer', 'Alex', 44, 30)
      const home = await s.pmt('ledger', 'storage', 'Alex')
      await s.pmt('tp', 'Alex', 'adults_b1', 0, Y, 0); await sleep(800)
      await s.pmt('fillplayer', 'Alex', 45, 20)
      const away = await s.pmt('ledger', 'storage', 'Alex')
      await s.pmt('tp', 'Alex', 'world', 0, Y, 0); await sleep(800)
      await openContainer(s, a, 'world', -4, Y, 2)
      await s.pmt('tp', 'Alex', 'adults_b1', 0, Y, 0); await sleep(1200)
      const awayAfter = await s.pmt('ledger', 'storage', 'Alex')
      await s.pmt('tp', 'Alex', 'world', 0, Y, 0); await sleep(1200)
      const homeAfter = await s.pmt('ledger', 'storage', 'Alex')
      r.check('Multiverse-Inventories: teleport between groups with a chest open', awayAfter.layout === away.layout && homeAfter.layout === home.layout,
        'each group\'s inventory exactly as it was, slot for slot')
    })
    if (a) await quit(a)
  } finally {
    await s.stop()
    s.remove()
  }
  return r
}
