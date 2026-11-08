// The multiversal trunk on a live server with Multiverse-Inventories: recipes, linking, kids and adults, per-player
// storage, breaking, automation next to it, group swaps, damaged data, the admin copy, read-only views and the fuzz.
import { readFileSync, readdirSync, existsSync, chmodSync } from 'node:fs'
import path from 'node:path'
import { Server, Results, bot, quit, sleep, useBlock, nextWindow, click, close, realClicks } from './lib.mjs'

const Y = -60
const BASE = readFileSync(path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../src/main/resources/config.yml'), 'utf8')
export const GROUPS = {
  adults_a: ['world', 'adults_a2'], adults_b: ['adults_b1'], kids_a: ['kids_a1'], kids_b: ['kids_b1'],
  mixed: ['mixed_k', 'mixed_a'], plain: ['plain1'],
}
export const WORLDS = Object.values(GROUPS).flat().filter(w => w !== 'world')

/** The default config with test groups' audiences, names, links and recipe overrides. */
export function trunkConfig({ links = [], sorting = true, extra = '' } = {}) {
  const head = BASE.slice(0, BASE.indexOf('\ntrunk:\n')).replace(/^  sorting: true$/m, `  sorting: ${sorting}`)
  return head + `
trunk:
  groups-from: auto
  audiences:
    groups: {adults_a: adults, adults_b: adults, kids_a: kids, kids_b: kids}
    worlds: {mixed_k: kids, mixed_a: adults}
  links: ${JSON.stringify(links)}
  names: {adults_a: "Survival", adults_b: "Frontier"}
  game-modes: [survival, adventure]
  recipes:
    key: standard
    trunk: standard
    groups:
      kids_a: {key: easy}
  look:
    trunk-item-model: ""
    key-item-model: ""
    block-model: "portmanteau:test_trunk"
    sound: "minecraft:block.ender_chest.open"
    particle: "minecraft:portal"
  admin-copies: true
${extra}`
}

export async function setupWorlds(s) {
  for (const w of WORLDS) await s.pmt('world', w)
}

/** Moves stacks from the bot's main inventory into the open trunk by shift-clicking them. */
export async function moveIn(b, win, count) {
  for (let i = 0; i < count; i++) {
    const slot = win.slots.findIndex((x, j) => j >= win.inventoryStart && x)
    if (slot < 0) break
    await click(b, slot, 0, 1, 200)
  }
}

/** Gives the player a real trunk item, then places it the way a player does. */
export async function place(s, who, world, x, y, z) {
  await s.run(`portmanteau trunk give ${who} trunk`, 250)
  return s.pmt('placetrunk', who, world, x, y, z)
}

export async function trunkSuite(version, { sorting = true } = {}) {
  const r = new Results(`trunk-${version}${sorting ? '' : '-sorting-off'}`)
  const config = trunkConfig({ sorting })
  const s = new Server(version, { config, plugins: ['multiverse-core', 'multiverse-inventories'], mvGroups: GROUPS, tag: r.name })
  const scope = g => `group:${g}`
  try {
    await s.prepare()
    await s.start()
    await setupWorlds(s)
    const how = realClicks(version) ? 'bot right-clicks' : 'interact event fired by the helper (bot clicks don\'t arrive through ViaBackwards)'
    const a = await bot(s, 'Alex')
    await s.pmt('tp', 'Alex', 'world', 0, Y, 0); await sleep(800)
    const alexUuid = (await s.pmt('uuid', 'Alex')).uuid

    // --- recipes ---
    await r.run('recipes', async () => {
      const key = await s.pmt('craft', 'Alex', '-,ender_eye,-,-,recovery_compass,-,-,-,-')
      await s.run('portmanteau trunk give Alex key', 250)
      const trunk = await s.pmt('craft', 'Alex', '-,key,-,-,ender_chest,-,-,chest,-')
      const fake = await s.pmt('craft', 'Alex', '-,fakekey,-,-,ender_chest,-,-,chest,-')
      const easyHere = await s.pmt('craft', 'Alex', '-,ender_pearl,-,-,compass,-,-,-,-')
      await s.pmt('tp', 'Alex', 'kids_a1', 0, Y, 0); await sleep(600)
      const easyKids = await s.pmt('craft', 'Alex', '-,ender_pearl,-,-,compass,-,-,-,-')
      const standardKids = await s.pmt('craft', 'Alex', '-,ender_eye,-,-,recovery_compass,-,-,-,-')
      await s.pmt('tp', 'Alex', 'world', 0, Y, 0); await sleep(600)
      r.check('recipe: key from an eye of ender and a recovery compass', key.result === 'multiversal_key')
      r.check('recipe: trunk from the key, an ender chest and a chest', trunk.result === 'multiversal_trunk')
      r.check('recipe: a renamed look-alike can\'t stand in for the key', fake.result === 'nothing', `result: ${fake.result}`)
      r.check('recipe: the per-group easy key works only in its group', easyKids.result === 'multiversal_key' && easyHere.result === 'nothing'
        && standardKids.result === 'nothing', `kids' group: easy ${easyKids.result}, standard ${standardKids.result}; elsewhere easy ${easyHere.result}`)
    })

    // --- placing, opening and storing ---
    await r.run('store and reopen', async () => {
      await place(s, 'Alex', 'world', 2, Y, 0)
      r.check('placed trunk has its display', (await s.pmt('displays', 'world', 2, Y, 0)).displays === 1)
      await s.pmt('clear', 'Alex')
      for (let i = 0; i < 6; i++) await s.pmt('setslot', 'Alex', 9 + i, ['STONE', 'OAK_LOG', 'BREAD', 'DIAMOND', 'ARROW', 'GLASS'][i], 10 + i)
      const before = await s.pmt('ledger', 'player', 'Alex')
      let win = await useBlock(s, a, 'world', 2, Y, 0)
      await moveIn(a, win, 6)
      await close(a, 600)
      const stored = await s.pmt('ledger', 'trunk', 'Alex', scope('adults_a'))
      const left = await s.pmt('ledger', 'player', 'Alex')
      r.check('trunk: items moved in are saved to the player\'s data', stored.items === before.items && left.items === 0,
        `${how}; ${stored.items} items stored`)
      win = await useBlock(s, a, 'world', 2, Y, 0)
      const shown = win.slots.slice(0, 54).filter(Boolean).reduce((n, x) => n + x.count, 0)
      await close(a, 400)
      r.check('trunk: reopening shows the same items', shown === stored.items, `${shown} shown`)
    })
    await r.run('same group, other world', async () => {
      await s.pmt('tp', 'Alex', 'adults_a2', 0, Y, 0); await sleep(600)
      await place(s, 'Alex', 'adults_a2', 2, Y, 0)
      const win = await useBlock(s, a, 'adults_a2', 2, Y, 0)
      const shown = win.slots.slice(0, 54).filter(Boolean).reduce((n, x) => n + x.count, 0)
      await close(a, 400)
      r.check('linking: two worlds in one group share storage', shown === (await s.pmt('ledger', 'trunk', 'Alex', scope('adults_a'))).items, `${shown} items`)
    })
    await r.run('other group', async () => {
      await s.pmt('tp', 'Alex', 'adults_b1', 0, Y, 0); await sleep(600)
      await place(s, 'Alex', 'adults_b1', 2, Y, 0)
      const win = await useBlock(s, a, 'adults_b1', 2, Y, 0)
      const shown = win.slots.slice(0, 54).filter(Boolean).length
      await close(a, 400)
      r.check('linking: a world in another group doesn\'t share', shown === 0, `${shown} stacks shown`)
    })
    await r.run('per player', async () => {
      const b2 = await bot(s, 'Blake')
      await s.pmt('tp', 'Blake', 'world', 1, Y, 1); await sleep(600)
      const win = await useBlock(s, b2, 'world', 2, Y, 0)
      const shown = win.slots.slice(0, 54).filter(Boolean).length
      await close(b2, 400)
      await quit(b2)
      r.check('per player: another player sees only their own storage', shown === 0, `${shown} stacks shown to the other player`)
    })
    await r.run('game modes', async () => {
      await s.pmt('tp', 'Alex', 'world', 0, Y, 0); await sleep(600)
      await s.pmt('gamemode', 'Alex', 'creative')
      a.bar_.length = 0
      let opened = true
      try { await useBlock(s, a, 'world', 2, Y, 0, 1500) } catch { opened = false }
      if (opened) await close(a, 300)
      await s.pmt('gamemode', 'Alex', 'survival')
      r.check('game modes: a trunk doesn\'t open in creative', !opened)
    })
    await r.run('open spam and two trunks of one scope', async () => {
      const before = await s.pmt('ledger', 'trunk', 'Alex', scope('adults_a'))
      await place(s, 'Alex', 'world', -2, Y, 0)
      for (let i = 0; i < 10; i++) { try { await useBlock(s, a, i % 2 ? 'world' : 'world', i % 2 ? 2 : -2, Y, 0, 800) } catch {} }
      await close(a, 500)
      const top = await s.pmt('top', 'Alex')
      const after = await s.pmt('ledger', 'trunk', 'Alex', scope('adults_a'))
      r.check('two trunks of one scope show one storage, under open spam', after.ledger === before.ledger && top.type === 'CRAFTING',
        'ten quick opens across two trunks; nothing duplicated or lost')
    })
    if (sorting) {
      await r.run('sorting the trunk', async () => {
        const win = await useBlock(s, a, 'world', 2, Y, 0)
        const before = await s.pmt('ledger', 'open', 'Alex')
        const empty = win.slots.slice(0, 54).findIndex(x => !x)
        a.bar_.length = 0
        await click(a, empty, 0, 1, 700)
        const after = await s.pmt('ledger', 'open', 'Alex')
        await close(a, 500)
        const stored = await s.pmt('ledger', 'trunk', 'Alex', scope('adults_a'))
        const said = a.bar_.join(' ')
        r.check('sorting the trunk goes through the same guarantee', after.ledger === before.ledger && stored.ledger === before.ledger
          && /Sorted \d+ items|Already sorted/.test(said), 'hotkey inside the trunk: ' + (said.match(/Sorted \d+ items|Already sorted/)?.[0] ?? 'no answer'))
      })
    }

    // --- automation next to a trunk ---
    await r.run('hoppers and comparators', async () => {
      await s.pmt('place', 'world', 6, Y, 0, 'STONE')
      await place(s, 'Alex', 'world', 6, Y + 1, 0)
      await s.pmt('place', 'world', 6, Y + 2, 0, 'HOPPER')
      const hopper = await s.pmt('fill', 'world', 6, Y + 2, 0, 5, 3)
      await s.pmt('place', 'world', 7, Y + 1, 0, 'COMPARATOR')
      const before = await s.pmt('ledger', 'trunk', 'Alex', scope('adults_a'))
      await sleep(4000)
      const hopperAfter = await s.pmt('ledger', 'block', 'world', 6, Y + 2, 0)
      const power = await s.pmt('power', 'world', 8, Y + 1, 0)
      const after = await s.pmt('ledger', 'trunk', 'Alex', scope('adults_a'))
      r.check('a hopper above can\'t put items in', hopperAfter.ledger === hopper.ledger && after.ledger === before.ledger)
      r.check('a comparator reads nothing', power.power === 0, `power ${power.power}`)
    })

    // --- breaking ---
    await r.run('breaking', async () => {
      const before = await s.pmt('ledger', 'trunk', 'Alex', scope('adults_a'))
      await place(s, 'Alex', 'world', 4, Y, -4)
      await s.pmt('drops', 'world', 4, Y, -4, 'clear')
      await s.pmt('breakas', 'Alex', 'world', 4, Y, -4)
      await sleep(300)
      const hand = await s.pmt('drops', 'world', 4, Y, -4, 'clear')
      const displays = await s.pmt('displays', 'world', 4, Y, -4)
      r.check('breaking by hand drops only the trunk', JSON.stringify(hand) === '{"multiversal_trunk":1}' && displays.displays === 0, JSON.stringify(hand))

      await place(s, 'Alex', 'world', 4, Y, -8)
      await s.pmt('drops', 'world', 4, Y, -8, 'clear')
      await s.pmt('explode', 'world', 4, Y, -8, 3)
      await sleep(500)
      const boom = await s.pmt('drops', 'world', 4, Y, -8, 'clear')
      r.check('an explosion drops only the trunk', (boom.multiversal_trunk ?? 0) === 1 && !boom.lodestone, JSON.stringify(boom))

      await s.pmt('gamemode', 'Alex', 'creative')
      await place(s, 'Alex', 'world', 4, Y, -12)
      await s.pmt('drops', 'world', 4, Y, -12, 'clear')
      await s.pmt('breakas', 'Alex', 'world', 4, Y, -12)
      await sleep(300)
      const admin = await s.pmt('drops', 'world', 4, Y, -12, 'clear')
      await s.pmt('gamemode', 'Alex', 'survival')
      r.check('an admin in creative breaking it drops nothing', Object.keys(admin).length === 0, JSON.stringify(admin))
      const after = await s.pmt('ledger', 'trunk', 'Alex', scope('adults_a'))
      r.check('the storage is untouched by any of it', after.ledger === before.ledger, `${after.items} items still stored`)
    })

    // --- group swaps ---
    await r.run('teleport with the trunk open', async () => {
      await s.pmt('tp', 'Alex', 'world', 0, Y, 0); await sleep(500)
      await s.pmt('clear', 'Alex')
      await s.pmt('setslot', 'Alex', 9, 'EMERALD', 7)
      const total = (await s.pmt('ledger', 'trunk', 'Alex', scope('adults_a'))).items + 7
      const win = await useBlock(s, a, 'world', 2, Y, 0)
      await moveIn(a, win, 1)
      await s.pmt('tp', 'Alex', 'adults_b1', 0, Y, 0)
      await sleep(800)
      const top = await s.pmt('top', 'Alex')
      const stored = await s.pmt('ledger', 'trunk', 'Alex', scope('adults_a'))
      const otherGroup = await s.pmt('ledger', 'player', 'Alex')
      await s.pmt('tp', 'Alex', 'world', 0, Y, 0); await sleep(800)
      const home = await s.pmt('ledger', 'player', 'Alex')
      r.check('group swap: a teleport closes and saves the trunk first', top.type === 'CRAFTING' && stored.items + home.items === total
        && otherGroup.items === 0, `${stored.items} stored + ${home.items} carried = ${total}; the other group's inventory held ${otherGroup.items}`)
    })
    await r.run('death with the trunk open', async () => {
      await s.pmt('setslot', 'Alex', 10, 'GOLD_INGOT', 5)
      const win = await useBlock(s, a, 'world', 2, Y, 0)
      await moveIn(a, win, 1)
      const stored0 = await s.pmt('ledger', 'open', 'Alex')
      await s.pmt('kill', 'Alex')
      await sleep(800)
      a.respawn?.()
      await sleep(800)
      const stored = await s.pmt('ledger', 'trunk', 'Alex', scope('adults_a'))
      r.check('group swap: death closes and saves the trunk first', stored.ledger === stored0.ledger, `${stored.items} stored`)
    })
    await r.run('disconnect with the trunk open', async () => {
      await s.pmt('tp', 'Alex', 'world', 0, Y, 0); await sleep(500)
      await s.pmt('setslot', 'Alex', 11, 'IRON_INGOT', 9)
      const win = await useBlock(s, a, 'world', 2, Y, 0)
      await moveIn(a, win, 1)
      const open = await s.pmt('ledger', 'open', 'Alex')
      await quit(a)
      await sleep(800)
      const back = await bot(s, 'Alex')
      await sleep(500)
      const stored = await s.pmt('ledger', 'trunk', 'Alex', scope('adults_a'))
      r.check('group swap: disconnecting saves the trunk first', stored.ledger === open.ledger, `${stored.items} stored after rejoining`)
      Object.assign(a, back)
    })
    const alex = () => a

    // --- links, kids and adults ---
    await r.run('admin link', async () => {
      s.writeConfig(trunkConfig({ sorting, links: [['adults_a', 'adults_b']] }))
      const m = s.mark()
      await s.run('portmanteau reload', 1500)
      const errors = s.grep(/links refused/i, m)
      const scopes = await s.run('portmanteau trunk scopes', 600)
      const linked = scopes.some(l => /adults_b1: .*linked:adults_a\+adults_b/.test(l)) && scopes.some(l => /world: .*linked:adults_a\+adults_b/.test(l))
      r.check('linking: an admin-linked pair of adults\' groups shares one storage', errors.length === 0 && linked,
        'both groups resolve to linked:adults_a+adults_b')
    })
    for (const [label, links] of [
      ['kids\' and adults\' groups, directly', [['kids_a', 'adults_a']]],
      ['kids\' and adults\' groups, through a chain', [['kids_a', 'kids_b'], ['kids_b', 'adults_b']]],
      ['a mixed group', [['mixed', 'adults_a']]],
      ['an unlabelled group', [['plain', 'adults_a']]],
    ]) {
      await r.run(`never linked: ${label}`, async () => {
        s.writeConfig(trunkConfig({ sorting, links }))
        const m = s.mark()
        await s.run('portmanteau reload', 1500)
        const refused = s.grep(/links refused/i, m).length > 0
        const scopes = (await s.run('portmanteau trunk scopes', 600)).join('\n')
        r.check(`never linked: ${label}`, refused && !/\[linked:/.test(scopes), 'refused at load with a clear error; no group shares')
      })
    }
    s.writeConfig(config)
    await s.run('portmanteau reload', 1500)

    // --- damaged data, the admin copy, a read-only view, the fuzz ---
    await r.run('damaged data keeps the trunk shut', async () => {
      await s.pmt('tp', 'Alex', 'world', 0, Y, 0); await sleep(500)
      const b2 = await bot(s, 'Casey')
      await s.pmt('tp', 'Casey', 'world', 1, Y, 1); await sleep(500)
      await s.pmt('setslot', 'Casey', 9, 'DIAMOND', 3)
      let win = await useBlock(s, b2, 'world', 2, Y, 0)
      await moveIn(b2, win, 1)
      await close(b2, 500)
      await s.pmt('corrupt', 'Casey', scope('adults_a'))
      const bytes = await s.pmt('trunkbytes', 'Casey', scope('adults_a'))
      const m = s.mark()
      b2.chat_.length = 0
      let opened = true
      try { await useBlock(s, b2, 'world', 2, Y, 0, 1500) } catch { opened = false }
      const after = await s.pmt('trunkbytes', 'Casey', scope('adults_a'))
      const logged = s.grep(/can't be read: .*stays shut/, m).length > 0
      await quit(b2)
      r.check('damaged data keeps the trunk shut and untouched', !opened && after.sha === bytes.sha && logged
        && b2.chat_.some(c => /can't be opened right now/.test(c)), 'player told kindly, admins told, stored bytes unchanged')
    })
    await r.run('admin copy', async () => {
      const dir = path.join(s.dir, 'plugins', 'Portmanteau', 'trunk-copies', alexUuid)
      const files = existsSync(dir) ? readdirSync(dir) : []
      const file = files.find(f => f.startsWith('group_adults_a'))
      const text = file ? readFileSync(path.join(dir, file), 'utf8') : ''
      const counts = [...text.matchAll(/^    count: (\d+)$/gm)].reduce((n, m) => n + Number(m[1]), 0)
      const stored = await s.pmt('ledger', 'trunk', 'Alex', scope('adults_a'))
      r.check('the admin copy is readable and matches the stored contents', /^format: 1$/m.test(text) && text.includes(`owner: "${alexUuid}"`)
        && counts === stored.items && !text.includes('Alex'), `${file}: ${counts} items, named by UUID only`)
    })
    await r.run('a failing admin copy never blocks the real save', async () => {
      const dir = path.join(s.dir, 'plugins', 'Portmanteau', 'trunk-copies', alexUuid)
      chmodSync(dir, 0o555)
      const m = s.mark()
      await s.pmt('setslot', 'Alex', 12, 'LAPIS_LAZULI', 4)
      const win = await useBlock(s, alex(), 'world', 2, Y, 0)
      await moveIn(alex(), win, 1)
      await close(alex(), 800)
      chmodSync(dir, 0o755)
      const stored = await s.pmt('ledger', 'trunk', 'Alex', scope('adults_a'))
      const warned = s.grep(/Couldn't write the admin copy/, m).length > 0
      const hasLapis = (await s.run('portmanteau status', 300)) && stored.items > 0
      r.check('a failing admin copy never blocks the real save', warned && hasLapis, 'read-only folder: warned, trunk saved')
    })
    await r.run('admin view is read-only', async () => {
      const admin = await bot(s, 'Morgan')
      await s.pmt('op', 'Morgan', true)
      await s.pmt('tp', 'Morgan', 'world', 0, Y, -1); await sleep(500)
      const before = await s.pmt('ledger', 'trunk', 'Alex', scope('adults_a'))
      const w = nextWindow(admin)
      admin.chat('/portmanteau trunk view Alex')
      const win = await w
      const filled = win.slots.slice(0, 54).findIndex(Boolean)
      await click(admin, filled, 0, 1, 500)
      await click(admin, filled, 0, 0, 300)
      await close(admin, 500)
      const after = await s.pmt('ledger', 'trunk', 'Alex', scope('adults_a'))
      const own = await s.pmt('ledger', 'player', 'Morgan')
      await quit(admin)
      r.check('an admin view is read-only and keyed to the owner', after.ledger === before.ledger && own.items === 0, 'clicks in it moved nothing')
    })
    await r.run('trunk fuzz', async () => {
      const out = await s.pmt('trunkfuzz', 'Alex', 2000, 7)
      r.check('fuzz: random contents saved and loaded 2,000 times', out.bad === 0, out.problems.join('; '))
    })
    await r.run('a spoiled trunk save keeps the previous contents', async () => {
      await s.pmt('setslot', 'Alex', 13, 'COAL', 6)
      const before = await s.pmt('ledger', 'trunk', 'Alex', scope('adults_a'))
      await s.pmt('fault', 'portmanteau.test.break-trunk-saves', 1)
      const m = s.mark()
      const win = await useBlock(s, alex(), 'world', 2, Y, 0)
      await moveIn(alex(), win, 1)
      await sleep(400)
      const mid = await s.pmt('ledger', 'trunk', 'Alex', scope('adults_a'))
      await close(alex(), 800)
      await s.pmt('fault', 'portmanteau.test.break-trunk-saves', 0)
      const after = await s.pmt('ledger', 'trunk', 'Alex', scope('adults_a'))
      const logged = s.grep(/the previous value was put back|previous saved contents were kept/, m).length > 0
      r.check('a spoiled trunk save keeps the previous contents, then saves on the next change', logged && after.items === before.items + 6,
        `spoiled save put back (console says so); the next save stored ${before.items} → ${after.items}`)
    })
    const errors = s.grep(/Exception|\bERROR\b|SEVERE/).filter(l => !/No key layers|can't be read: |didn't read back|previous saved contents were kept|refused|Couldn't write the admin copy/.test(l))
    r.check('no unexpected errors in the server log', errors.length === 0, errors.slice(0, 3).join(' | '))
    await quit(alex())
  } finally {
    await s.stop()
    s.remove()
  }
  return r
}
