// Quick check that the server, both plugins and a bot all work together.
import { Server, bot, quit, sleep, openBlock, close } from './lib.mjs'
const v = process.argv[2] ?? '26.3'
const s = new Server(v, { tag: 'smoke' })
try {
  await s.prepare()
  await s.start()
  console.log('started; Portmanteau lines:')
  for (const l of s.grep(/Portmanteau\]/)) console.log('  ', l.slice(0, 200))
  const b = await bot(s, 'Alex')
  console.log('bot joined', await s.pmt('where', 'Alex'))
  await s.pmt('place', 'world', 2, -60, 2, 'CHEST')
  console.log(await s.pmt('fill', 'world', 2, -60, 2, 1, 20))
  await s.pmt('tp', 'Alex', 'world', 0, -60, 2)
  await sleep(1000)
  const w = await openBlock(b, 2, -60, 2)
  console.log('opened window', w.type, w.title)
  await close(b, 800)
  console.log('after close', await s.pmt('sorted', 'block', 'world', 2, -60, 2), await s.pmt('events'))
  console.log(await s.pmt('fuzz', 200, 1))
  console.log(await s.pmt('features'))
  await quit(b)
} catch (e) { console.log('ERROR', e) } finally {
  await s.stop(); s.remove()
  for (const l of s.grep(/WARN|ERROR|Exception/)) console.log('  log:', l.slice(0, 220))
  process.exit(0)
}
