// Each feature switched off leaves nothing of it running: no listeners, recipes, commands or files. Switching the trunk
// off and on again keeps every player's storage.
import { Server, Results, bot, quit, sleep, useBlock } from './lib.mjs'
import { trunkConfig, setupWorlds, GROUPS, moveIn, place } from './trunk.mjs'

const Y = -60

export async function switchesSuite(version) {
  const r = new Results(`switches-${version}`)
  const plugins = ['multiverse-core', 'multiverse-inventories']

  // Fresh servers, so "no files" means none were ever made.
  for (const [label, sorting, trunk] of [['sorting off', false, true], ['trunk off', true, false]]) {
    const config = trunkConfig({ sorting }).replace(/^  trunk: true$/m, `  trunk: ${trunk}`)
    const s = new Server(version, { config, plugins, mvGroups: GROUPS, tag: `${r.name}-${label.replace(' ', '-')}` })
    try {
      await s.prepare()
      await s.start()
      const f = await s.pmt('features')
      const sortingParts = ['SortListeners' in f.listeners, f.commands.includes('sort'), f.commands.includes('isort'), f.files.includes('categories'), f.api]
      const trunkParts = ['TrunkListeners' in f.listeners, 'TrunkBlocks' in f.listeners, 'TrunkRecipes' in f.listeners, f.recipes.length > 0,
        f.files.includes('server-id.txt'), f.files.includes('trunk-copies')]
      const adminTrunk = (await s.run('portmanteau trunk scopes', 500)).some(l => /Multiversal trunk scopes/.test(l))
      if (!sorting) {
        r.check(`${label}: nothing of sorting runs`, sortingParts.every(x => !x), JSON.stringify(f))
        r.check(`${label}: the trunk still runs`, trunkParts.slice(0, 4).every(Boolean) && adminTrunk)
      } else {
        r.check(`${label}: nothing of the trunk runs`, trunkParts.every(x => !x) && !adminTrunk, JSON.stringify(f))
        r.check(`${label}: sorting still runs`, sortingParts.every(Boolean))
      }
    } finally {
      await s.stop()
      s.remove()
    }
  }

  // Off and on again with storage in place.
  const s = new Server(version, { config: trunkConfig(), plugins, mvGroups: GROUPS, tag: `${r.name}-toggle` })
  try {
    await s.prepare()
    await s.start()
    await setupWorlds(s)
    let a = await bot(s, 'Alex')
    await s.pmt('tp', 'Alex', 'world', 0, Y, 0); await sleep(800)
    await place(s, 'Alex', 'world', 2, Y, 0)
    for (let i = 0; i < 3; i++) await s.pmt('setslot', 'Alex', 9 + i, ['EMERALD', 'BREAD', 'ARROW'][i], 9 + i)
    const win = await useBlock(s, a, 'world', 2, Y, 0)
    await moveIn(a, win, 3)
    a.closeWindow(win); await sleep(600)
    const stored = await s.pmt('ledger', 'trunk', 'Alex', 'group:adults_a')
    await quit(a)
    await s.stop()

    s.writeConfig(trunkConfig().replace(/^  trunk: true$/m, '  trunk: false'))
    await s.start()
    a = await bot(s, 'Alex')
    const pdc = await s.pmt('pdc', 'Alex')
    await quit(a)
    await s.stop()
    r.check('trunk off: stored contents stay in the player\'s data', pdc.scopes.includes('group:adults_a'), `scopes kept: ${pdc.scopes.join(', ')}`)

    s.writeConfig(trunkConfig())
    await s.start()
    await setupWorlds(s)
    a = await bot(s, 'Alex')
    const again = await s.pmt('ledger', 'trunk', 'Alex', 'group:adults_a')
    await s.pmt('tp', 'Alex', 'world', 0, Y, 0); await sleep(800)
    const w2 = await useBlock(s, a, 'world', 2, Y, 0)
    const shown = w2.slots.slice(0, 54).filter(Boolean).reduce((n, x) => n + x.count, 0)
    a.closeWindow(w2); await sleep(300)
    await quit(a)
    r.check('trunk off and on again keeps every item', again.ledger === stored.ledger && shown === stored.items,
      `${stored.items} items before, ${again.items} after; the placed trunk still opens them`)
  } finally {
    await s.stop()
    s.remove()
  }
  return r
}
