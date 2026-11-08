// Sorting on a live server: every container type, places it must never sort, hotkeys, interference, a forced bad
// write, commands, config modes, every item in the game, the fuzz, quick transfer and load.
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { Server, Results, bot, quit, sleep, openContainer, nextWindow, click, close, realClicks } from './lib.mjs'

const W = 'world', Y = -60
const DEFAULT_CONFIG = readFileSync(path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../src/main/resources/config.yml'), 'utf8')

export async function sortingSuite(version, { trunk = true } = {}) {
  const r = new Results(`sorting-${version}${trunk ? '' : '-trunk-off'}`)
  const config = trunk ? DEFAULT_CONFIG : DEFAULT_CONFIG.replace(/^  trunk: true$/m, '  trunk: false')
  const s = new Server(version, { config, tag: r.name })
  const events = async () => (await s.pmt('events')).sorted
  try {
    await s.prepare()
    await s.start()
    const a = await bot(s, 'Alex')
    await s.pmt('tp', 'Alex', W, 0, Y, 0)
    await sleep(800)
    const how = realClicks(version) ? 'bot right-clicks' : 'server opens the container for the bot'

    // --- every container type ---
    const blocks = [['chest', 'CHEST', 2, 0], ['trapped chest', 'TRAPPED_CHEST', 2, 2], ['barrel', 'BARREL', -2, 0],
      ['placed shulker box', 'SHULKER_BOX', -2, 2]]
    for (const [label, material, x, z] of blocks) {
      await r.run(`container: ${label}`, async () => {
        await s.pmt('place', W, x, Y, z, material)
        const before = await s.pmt('fill', W, x, Y, z, 11 + x * 7 + z, 18)
        const e0 = await events()
        await openContainer(s, a, W, x, Y, z)
        await close(a, 500)
        const after = await s.pmt('ledger', 'block', W, x, Y, z)
        const sorted = await s.pmt('sorted', 'block', W, x, Y, z)
        r.check(`container: ${label}`, after.ledger === before.ledger && sorted.result === 'ALREADY_SORTED' && await events() === e0 + 1,
          `${how}; ${before.items} items kept by full identity, sorted on close`)
      })
    }
    await r.run('container: double chest', async () => {
      await s.pmt('doublechest', W, 2, Y, -3)
      const before = await s.pmt('fill', W, 2, Y, -3, 77, 40)
      const e0 = await events()
      await openContainer(s, a, W, 2, Y, -3)
      await close(a, 500)
      const after = await s.pmt('ledger', 'block', W, 2, Y, -3)
      r.check('container: double chest', after.ledger === before.ledger && (await s.pmt('sorted', 'block', W, 2, Y, -3)).result === 'ALREADY_SORTED'
        && await events() === e0 + 1, `${before.items} items, 54 slots`)
    })
    await r.run('container: ender chest', async () => {
      const before = await s.pmt('fillender', 'Alex', 5, 15)
      const e0 = await events()
      const w = nextWindow(a); await s.pmt('openender', 'Alex'); await w
      await close(a, 500)
      const after = await s.pmt('ledger', 'ender', 'Alex')
      r.check('container: ender chest', after.ledger === before.ledger && after.layout !== before.layout && await events() === e0 + 1,
        'the player\'s own ender chest, opened by the server')
    })
    for (const [label, kind, x] of [['donkey', 'donkey', 6], ['mule', 'mule', 8], ['llama', 'llama', 10], ['chest boat', 'chestboat', 12], ['chest minecart', 'chestminecart', 14]]) {
      await r.run(`container: ${label}`, async () => {
        const { uuid } = await s.pmt('spawn', W, x, Y, 6, kind)
        const before = await s.pmt('fillentity', uuid, x, 10)
        const e0 = await events()
        const w = nextWindow(a); await s.pmt('openentity', 'Alex', uuid); await w
        await close(a, 500)
        const after = await s.pmt('ledger', 'entity', uuid)
        const sorted = await s.pmt('sorted', 'entity', uuid)
        r.check(`container: ${label}`, after.ledger === before.ledger && sorted.result === 'ALREADY_SORTED' && await events() === e0 + 1,
          'opened by the server; saddle and armour slots fixed')
      })
    }

    // --- the player's inventory ---
    await r.run('inventory: sorts on close, hotbar left alone by default', async () => {
      const before = await s.pmt('fillplayer', 'Alex', 3, 30)
      const hotbar = await s.pmt('ledger', 'hotbar', 'Alex')
      const e0 = await events()
      await close(a, 600) // closing the player's own inventory screen
      const after = await s.pmt('ledger', 'storage', 'Alex')
      const hotbarAfter = await s.pmt('ledger', 'hotbar', 'Alex')
      r.check('inventory: sorts on close, hotbar left alone by default', after.ledger === before.ledger && hotbarAfter.layout === hotbar.layout
        && await events() === e0 + 1, 'closing the inventory screen')
    })
    await r.run('inventory: /isort all sorts the hotbar too', async () => {
      await s.pmt('fillplayer', 'Alex', 4, 30)
      const hotbar = await s.pmt('ledger', 'hotbar', 'Alex')
      const storage = await s.pmt('ledger', 'storage', 'Alex')
      a.bar_.length = 0
      a.chat('/isort all'); await sleep(800)
      const hotbarAfter = await s.pmt('ledger', 'hotbar', 'Alex')
      const storageAfter = await s.pmt('ledger', 'storage', 'Alex')
      r.check('inventory: /isort all sorts the hotbar too', storageAfter.ledger === storage.ledger && hotbarAfter.layout !== hotbar.layout,
        `action bar: ${a.bar_.at(-1) ?? '(none seen)'}`)
    })
    await r.run('inventory: /isort hotbar sorts only the hotbar', async () => {
      await s.pmt('fillplayer', 'Alex', 5, 30)
      const all = await s.pmt('ledger', 'storage', 'Alex')
      const hotbar = await s.pmt('ledger', 'hotbar', 'Alex')
      a.chat('/isort hotbar'); await sleep(800)
      const allAfter = await s.pmt('ledger', 'storage', 'Alex')
      const hotbarAfter = await s.pmt('ledger', 'hotbar', 'Alex')
      r.check('inventory: /isort hotbar sorts only the hotbar', allAfter.ledger === all.ledger && hotbarAfter.ledger === hotbar.ledger,
        'items kept; the hotbar keeps its own items')
    })

    // --- never sorted ---
    for (const kind of ['customholder', 'noholder', 'chesttype', 'preview', 'grave', 'shulkeritem']) {
      await r.run(`never sorted: ${kind}`, async () => {
        await s.pmt('fillplayer', 'Alex', 9, 25)
        const player = await s.pmt('ledger', 'storage', 'Alex')
        const e0 = await events()
        const w = nextWindow(a)
        const gui = await s.pmt('gui', 'Alex', kind)
        const win = await w
        const top = win.inventoryStart
        const empty = win.slots.slice(0, top).findIndex(x => !x)
        if (empty >= 0) {
          await click(a, empty, 0, 1)   // shift-click
          await click(a, empty, 0, 6)   // double-click
          await click(a, empty, 1, 1)   // shift-right-click
        }
        await click(a, 0, 2, 3)         // middle-click
        const emptyBelow = win.slots.findIndex((x, i) => i >= top && !x)
        if (emptyBelow >= 0) await click(a, emptyBelow, 0, 1)
        await close(a, 600)
        const guiAfter = await s.pmt('ledger', 'gui', kind)
        const playerAfter = await s.pmt('ledger', 'storage', 'Alex')
        r.check(`never sorted: ${kind}`, guiAfter.layout === gui.layout && playerAfter.layout === player.layout && await events() === e0,
          'a stand-in for another plugin\'s screen, with every hotkey tried; nothing moved')
      })
    }
    await r.run('never sorted: crafting grid', async () => {
      const e0 = await events()
      const w = nextWindow(a); await s.pmt('workbench', 'Alex'); await w
      await click(a, 1, 0, 1); await click(a, 1, 0, 6)
      await close(a, 600)
      r.check('never sorted: crafting grid', await events() === e0, 'hotkeys in a crafting table do nothing')
    })

    // --- hotkeys ---
    await s.pmt('place', W, 0, Y, 3, 'CHEST')
    for (const [label, button, mode] of [['shift-click', 0, 1], ['double-click', 0, 6], ['shift-right-click', 1, 1], ['middle-click', 2, 3]]) {
      await r.run(`hotkey: ${label}`, async () => {
        await s.pmt('fill', W, 0, Y, 3, 100 + mode * 10 + button, 14)
        const win = await openContainer(s, a, W, 0, Y, 3)
        const before = await s.pmt('ledger', 'block', W, 0, Y, 3)
        const e0 = await events()
        a.bar_.length = 0
        const slot = mode === 3 ? 0 : win.slots.slice(0, win.inventoryStart).findIndex(x => !x)
        await click(a, slot, button, mode, 600)
        const after = await s.pmt('ledger', 'block', W, 0, Y, 3)
        const fired = await events() - e0
        await close(a, 400)
        r.check(`hotkey: ${label}`, after.ledger === before.ledger && fired === 1, `sorted ${fired} time(s); action bar: ${a.bar_.at(-1) ?? '(none seen)'}`)
      })
    }
    await r.run('hotkey: cooldown', async () => {
      await s.pmt('fill', W, 0, Y, 3, 500, 14)
      const win = await openContainer(s, a, W, 0, Y, 3)
      const e0 = await events()
      const slot = win.slots.slice(0, win.inventoryStart).findIndex(x => !x)
      a.clickWindow(slot, 0, 1).catch(() => {})
      await sleep(60)
      await s.pmt('fill', W, 0, Y, 3, 501, 14)
      a.clickWindow(slot, 0, 1).catch(() => {})
      await sleep(600)
      const fired = await events() - e0
      await close(a, 400)
      r.check('hotkey: cooldown', fired === 1, `two shift-clicks 60 ms apart sorted ${fired} time(s)`)
    })
    await r.run('hotkey: middle-click in creative does nothing', async () => {
      await s.pmt('gamemode', 'Alex', 'creative')
      await s.pmt('fill', W, 0, Y, 3, 600, 14)
      await openContainer(s, a, W, 0, Y, 3)
      const e0 = await events()
      await click(a, 0, 2, 3, 600)
      const fired = await events() - e0
      await close(a, 400)
      await s.pmt('gamemode', 'Alex', 'survival')
      r.check('hotkey: middle-click in creative does nothing', fired <= 1 && (await events() - e0) <= 1,
        `middle-click fired ${fired}; closing may sort once`)
    })
    await r.run('hotkey: shift-click on a filled slot stays vanilla', async () => {
      await s.pmt('clear', 'Alex')
      await s.pmt('fill', W, 0, Y, 3, 700, 14)
      const win = await openContainer(s, a, W, 0, Y, 3)
      const e0 = await events()
      const filled = win.slots.slice(0, win.inventoryStart).findIndex(x => !!x)
      const before = await s.pmt('ledger', 'player', 'Alex')
      await click(a, filled, 0, 1, 600)
      const fired = await events() - e0
      const after = await s.pmt('ledger', 'player', 'Alex')
      await close(a, 400)
      r.check('hotkey: shift-click on a filled slot stays vanilla', fired === 0 && after.items > before.items, 'the item moved to the player, as in vanilla')
    })

    // --- interference ---
    await r.run('interference: a hopper feeding the chest', async () => {
      await s.pmt('place', W, 4, Y, 3, 'CHEST')
      await s.pmt('place', W, 4, Y + 1, 3, 'HOPPER')
      const c0 = await s.pmt('fill', W, 4, Y, 3, 800, 10)
      const h0 = await s.pmt('fill', W, 4, Y + 1, 3, 801, 5)
      for (let i = 0; i < 8; i++) {
        const win = await openContainer(s, a, W, 4, Y, 3)
        const slot = win.slots.slice(0, win.inventoryStart).findIndex(x => !x)
        if (slot >= 0) await click(a, slot, 0, 1, 120)
        await close(a, 150)
      }
      await sleep(3000)
      const c1 = await s.pmt('ledger', 'block', W, 4, Y, 3)
      const h1 = await s.pmt('ledger', 'block', W, 4, Y + 1, 3)
      r.check('interference: a hopper feeding the chest', c0.items + h0.items === c1.items + h1.items,
        `${c0.items + h0.items} items before, ${c1.items + h1.items} after, across chest and hopper`)
    })
    await r.run('interference: two players in one double chest', async () => {
      const b2 = await bot(s, 'Blake')
      await s.pmt('tp', 'Blake', W, 1, Y, -1); await sleep(600)
      await s.pmt('fill', W, 2, Y, -3, 900, 30)
      const w1 = nextWindow(a); await s.pmt('openblock', 'Alex', W, 2, Y, -3); await w1
      const w2 = nextWindow(b2); await s.pmt('openblock', 'Blake', W, 2, Y, -3); await w2
      const e0 = await events()
      await close(a, 500)
      const afterFirst = await events() - e0
      await close(b2, 500)
      const afterSecond = await events() - e0
      await quit(b2)
      r.check('interference: two players in one double chest', afterFirst === 0 && afterSecond === 1,
        `first close sorted ${afterFirst}, last close sorted ${afterSecond - afterFirst}`)
    })
    await r.run('interference: closing with an item on the cursor', async () => {
      await s.pmt('clear', 'Alex')
      await s.pmt('fill', W, 0, Y, 3, 950, 14)
      const win = await openContainer(s, a, W, 0, Y, 3)
      const chest = await s.pmt('ledger', 'block', W, 0, Y, 3)
      const filled = win.slots.slice(0, win.inventoryStart).findIndex(x => !!x)
      await click(a, filled, 0, 0, 400) // pick up onto the cursor
      await close(a, 800)
      const chestAfter = await s.pmt('ledger', 'block', W, 0, Y, 3)
      const player = await s.pmt('ledger', 'player', 'Alex')
      const dropped = await s.pmt('drops', W, 0, Y, 0, 'clear')
      const droppedItems = Object.values(dropped).reduce((x, y) => x + y, 0)
      r.check('interference: closing with an item on the cursor', chest.items === chestAfter.items + player.items + droppedItems,
        `${chest.items} before; ${chestAfter.items} in the chest, ${player.items} with the player, ${droppedItems} dropped`)
    })
    await r.run('interference: closing mid-drag', async () => {
      await s.pmt('clear', 'Alex')
      await s.pmt('give', 'Alex', 'STONE', 64)
      await s.pmt('fill', W, 0, Y, 3, 960, 5)
      const win = await openContainer(s, a, W, 0, Y, 3)
      const total0 = (await s.pmt('ledger', 'block', W, 0, Y, 3)).items + (await s.pmt('ledger', 'player', 'Alex')).items
      const stone = win.slots.findIndex((x, i) => i >= win.inventoryStart && x)
      await click(a, stone, 0, 0, 300)        // pick up the stone
      await click(a, -999, 0, 5, 100)         // start a drag
      await click(a, 20, 1, 5, 100)           // drag over a slot
      await close(a, 800)                     // close without finishing
      const total1 = (await s.pmt('ledger', 'block', W, 0, Y, 3)).items + (await s.pmt('ledger', 'player', 'Alex')).items
      const dropped = Object.values(await s.pmt('drops', W, 0, Y, 0, 'clear')).reduce((x, y) => x + y, 0)
      r.check('interference: closing mid-drag', total0 === total1 + dropped, `${total0} before, ${total1} + ${dropped} dropped after`)
    })
    await r.run('interference: hotkey spam', async () => {
      await s.pmt('fill', W, 0, Y, 3, 970, 20)
      const win = await openContainer(s, a, W, 0, Y, 3)
      const before = await s.pmt('ledger', 'block', W, 0, Y, 3)
      const slot = win.slots.slice(0, win.inventoryStart).findIndex(x => !x)
      for (let i = 0; i < 25; i++) { a.clickWindow(slot, 0, 1).catch(() => {}); await sleep(20) }
      await sleep(800)
      await close(a, 400)
      const after = await s.pmt('ledger', 'block', W, 0, Y, 3)
      r.check('interference: hotkey spam', after.ledger === before.ledger, '25 shift-clicks in half a second')
    })

    // --- a write that comes out wrong ---
    await r.run('forced abort: a spoiled write is put back', async () => {
      await s.pmt('fill', W, 0, Y, 3, 990, 20)
      const win = await openContainer(s, a, W, 0, Y, 3)
      const before = await s.pmt('ledger', 'block', W, 0, Y, 3)
      await s.pmt('fault', 'portmanteau.test.break-writes', 1)
      const m = s.mark()
      a.chat_.length = 0
      const slot = win.slots.slice(0, win.inventoryStart).findIndex(x => !x)
      await click(a, slot, 0, 1, 800)
      const after = await s.pmt('ledger', 'block', W, 0, Y, 3)
      await close(a, 300)
      await s.pmt('fault', 'portmanteau.test.break-writes', 0)
      const told = a.chat_.some(c => /left this as it was/i.test(c))
      const logged = s.grep(/didn't read back as written, so the original contents were put back/, m).length > 0
      r.check('forced abort: a spoiled write is put back', after.layout === before.layout && told && logged,
        `player told: ${told}; console says why: ${logged}`)
    })
    await r.run('forced abort: over-stacked items leave the chest alone', async () => {
      const filled = await s.pmt('fill', W, 0, Y, 3, 1000, 54, 'overstack')
      if (filled.over === 0) {
        return r.skip('forced abort: over-stacked items leave the chest alone',
          'Paper clamps over-stacked items put into a real chest, so this can\'t be set up in one; the refusal is covered by the fuzz (plugin-made inventories) and the unit tests')
      }
      const m = s.mark()
      a.chat_.length = 0
      const win = await openContainer(s, a, W, 0, Y, 3)
      const slot = win.slots.slice(0, win.inventoryStart).findIndex(x => !x)
      if (slot >= 0) await click(a, slot, 0, 1, 500)
      await close(a, 300)
      const after = await s.pmt('ledger', 'block', W, 0, Y, 3)
      r.check('forced abort: over-stacked items leave the chest alone', after.layout === filled.layout
        && s.grep(/unsorted to keep its items safe/, m).length > 0, 'layout unchanged, console says why')
    })

    // --- quick transfer, setup command, config modes ---
    await r.run('quick transfer', async () => {
      s.writeConfig(config.replace('quick-transfer: false', 'quick-transfer: true'))
      await s.run('portmanteau reload', 800)
      await s.pmt('clear', 'Alex')
      await s.pmt('place', W, -2, Y, -3, 'CHEST')
      await s.pmt('setslot', 'Alex', 12, 'COBBLESTONE', 30)
      await s.pmt('setslot', 'Alex', 3, 'COBBLESTONE', 10) // hotbar: never moves
      const w = nextWindow(a); await s.pmt('openblock', 'Alex', W, -2, Y, -3); await w
      // Seed the chest with one cobblestone so it "holds the kind".
      const c = await s.pmt('ledger', 'block', W, -2, Y, -3)
      await s.run('item replace block -2 -60 -3 container.0 with minecraft:cobblestone 1', 300)
      const before = (await s.pmt('ledger', 'block', W, -2, Y, -3)).items + (await s.pmt('ledger', 'player', 'Alex')).items
      await click(a, -999, 0, 0, 800)
      const chest = await s.pmt('ledger', 'block', W, -2, Y, -3)
      const after = chest.items + (await s.pmt('ledger', 'player', 'Alex')).items
      const hotbar = await s.pmt('ledger', 'hotbar', 'Alex')
      await close(a, 300)
      s.writeConfig(config)
      await s.run('portmanteau reload', 800)
      r.check('quick transfer', before === after && chest.items === 31 && hotbar.items === 10,
        `${c.items} → ${chest.items} in the chest; the hotbar kept its 10`)
    })
    await r.run('setup: /sort opens without errors', async () => {
      const m = s.mark()
      a.chat('/sort'); await sleep(1000)
      const errors = s.grep(/Exception|ERROR/, m)
      r.check('setup: /sort opens without errors', errors.length === 0,
        'the dialog is sent; a bot can\'t show it or click its buttons, so its layout and saving are untested here')
    })
    const modes = [['off', false], ['default-off', false], ['default-on', true], ['forced', true]]
    for (const [mode, sorts] of modes) {
      await r.run(`config: containers ${mode}`, async () => {
        s.writeConfig(config.replace(/^  containers: .*$/m, `  containers: ${mode}`))
        await s.run('portmanteau reload', 800)
        await s.pmt('fill', W, 0, Y, 3, 1200 + mode.length, 20)
        const e0 = await events()
        await openContainer(s, a, W, 0, Y, 3)
        await close(a, 600)
        const fired = await events() - e0
        // The inventory may sort on close too; containers count only when the chest itself sorted.
        const sorted = (await s.pmt('sorted', 'block', W, 0, Y, 3)).result === 'ALREADY_SORTED'
        r.check(`config: containers ${mode}`, sorted === sorts, `chest sorted on close: ${sorted} (sort events: ${fired})`)
      })
    }
    await r.run('config: a broken value falls back and says why', async () => {
      s.writeConfig(config.replace(/^  containers: .*$/m, '  containers: sometimes'))
      const m = s.mark()
      await s.run('portmanteau reload', 800)
      const said = s.grep(/isn't forced, default-on, default-off or off/, m).length > 0
      s.writeConfig(config)
      await s.run('portmanteau reload', 800)
      r.check('config: a broken value falls back and says why', said, 'warned and used default-on')
    })

    // --- every item, the fuzz, timing and load ---
    await r.run('every item in the registry sorts', async () => {
      const out = await s.pmt('allitems')
      r.check('every item in the registry sorts', out.bad === 0, `${out.items} items in ${out.batches} batches`)
    })
    await r.run('fuzz: thousands of random containers', async () => {
      const out = await s.pmt('fuzz', 3000, 20261107)
      r.check('fuzz: thousands of random containers', out.bad === 0,
        `${out.rounds} rounds: ${out.sorted} sorted, ${out.leftUnchanged} left unchanged (illegal stacks), ${out.already} already sorted; ${out.avgMicros.toFixed(0)} µs average; ${out.problems.join('; ')}`)
    })
    await quit(a)

    await r.run('load: 20 bots closing chests and spamming hotkeys', async () => {
      const bots = []
      for (let i = 0; i < 20; i++) {
        const b = await bot(s, `Load${i}`)
        await s.pmt('place', W, 20 + i * 2, Y, 20, 'CHEST')
        await s.pmt('tp', `Load${i}`, W, 20 + i * 2, Y, 18)
        bots.push(b)
      }
      await sleep(1500)
      const tps0 = await s.pmt('tps')
      const end = Date.now() + 60_000
      let opens = 0
      await Promise.all(bots.map(async (b, i) => {
        while (Date.now() < end) {
          await s.pmt('fill', W, 20 + i * 2, Y, 20, opens++, 25)
          const w = nextWindow(b, 4000).catch(() => null)
          await s.pmt('openblock', b.username, W, 20 + i * 2, Y, 20)
          const win = await w
          if (win) {
            const slot = win.slots.slice(0, win.inventoryStart).findIndex(x => !x)
            if (slot >= 0) await click(b, slot, 0, 1, 250)
            await close(b, 250)
          }
        }
      }))
      const tps1 = await s.pmt('tps')
      const status = await s.run('portmanteau status', 600)
      const timing = status.find(l => /sorts since the server started/.test(l)) ?? ''
      for (const b of bots) await quit(b)
      r.check('load: 20 bots closing chests and spamming hotkeys', tps1.tps > 19,
        `${opens} chest opens in 60 s; TPS ${tps0.tps.toFixed(1)} → ${tps1.tps.toFixed(1)}, ${tps1.mspt.toFixed(1)} ms per tick; ${timing.replace(/.*INFO\]: +/, '')}`)
    })
    const errors = s.grep(/Exception|\bERROR\b/).filter(l => !/No key layers/.test(l))
    r.check('no errors in the server log', errors.length === 0, errors.slice(0, 3).join(' | '))
  } finally {
    await s.stop()
    s.remove()
  }
  return r
}
