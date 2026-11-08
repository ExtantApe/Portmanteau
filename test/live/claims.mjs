// GriefPrevention container trust: only players who may take items from a claimed chest have it sorted.
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { Server, Results, bot, quit, sleep, nextWindow, close } from './lib.mjs'
import { modrinth } from './fetch.mjs'

const W = 'world', Y = -60
const CONFIG = readFileSync(path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../src/main/resources/config.yml'), 'utf8')

export async function claimsSuite(version) {
  const r = new Results(`claims-${version}`)
  if (!await modrinth('griefprevention', version)) {
    r.skip('GriefPrevention claims', `no GriefPrevention build for ${version} on Modrinth`)
    return r
  }
  const s = new Server(version, { config: CONFIG, plugins: ['griefprevention'], tag: r.name })
  try {
    await s.prepare()
    await s.start()
    const owner = await bot(s, 'Alex'), guest = await bot(s, 'Blake'), trusted = await bot(s, 'Casey'), admin = await bot(s, 'Morgan')
    for (const [n, x] of [['Alex', 0], ['Blake', 1], ['Casey', -1], ['Morgan', 2]]) await s.pmt('tp', n, W, x, Y, 0)
    await sleep(800)
    const made = await s.pmt('claim', 'Alex', W, -10, -10, 10, 10)
    await s.pmt('trust', W, 0, 0, 'Casey')
    await s.pmt('op', 'Morgan', true)
    await s.pmt('ignoreclaims', 'Morgan', true)
    await s.pmt('place', W, 3, Y, 3, 'CHEST')
    const sortedBy = async (b, seed) => {
      await s.pmt('fill', W, 3, Y, 3, seed, 25)
      const w = nextWindow(b); await s.pmt('openblock', b.username, W, 3, Y, 3); await w
      await close(b, 600)
      return (await s.pmt('sorted', 'block', W, 3, Y, 3)).result === 'ALREADY_SORTED'
    }
    r.check('claims: the claim was made', made.made)
    r.check('claims: the owner\'s close sorts it', await sortedBy(owner, 1))
    r.check('claims: a player with container trust sorts it', await sortedBy(trusted, 2))
    r.check('claims: a player without trust never sorts it', !await sortedBy(guest, 3), 'the server opened it for them, as an inventory-viewing plugin might')
    r.check('claims: an admin in /ignoreclaims never sorts it', !await sortedBy(admin, 4))
    for (const b of [owner, guest, trusted, admin]) await quit(b)
  } finally {
    await s.stop()
    s.remove()
  }
  return r
}
