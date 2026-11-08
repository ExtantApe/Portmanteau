// Runs live test suites, one server at a time. Usage: node run.mjs <minecraft-version> <suite> [suite ...]
// Suites: sorting, sorting-solo (trunk switched off), trunk, trunk-solo (sorting switched off), crash, switches
import { saveResults, freeGb } from './lib.mjs'
import { sortingSuite } from './sorting.mjs'

const [version, ...names] = process.argv.slice(2)
if (!version || names.length === 0) { console.log('Usage: node run.mjs <version> <suite> [suite ...]'); process.exit(2) }
const suites = {
  sorting: () => sortingSuite(version),
  'sorting-solo': () => sortingSuite(version, { trunk: false }),
}
try {
  const { trunkSuite } = await import('./trunk.mjs')
  suites.trunk = () => trunkSuite(version)
  suites['trunk-solo'] = () => trunkSuite(version, { sorting: false })
} catch {}
try {
  const { crashSuite } = await import('./crash.mjs')
  suites.crash = () => crashSuite(version)
} catch {}
try {
  const { switchesSuite } = await import('./switches.mjs')
  suites.switches = () => switchesSuite(version)
} catch {}

let failed = 0
for (const n of names) {
  if (!suites[n]) { console.log(`No suite ${n}`); failed++; continue }
  console.log(`\n== ${n} on ${version} ==`)
  const r = await suites[n]()
  const sum = r.summary()
  console.log(`== ${n} on ${version}: ${sum.pass} passed, ${sum.fail} failed, ${sum.skip} skipped, ${sum.seconds} s. ${saveResults(r)}`)
  failed += sum.fail
}
const gb = freeGb()
if (gb < 50) console.log(`Disk: ${gb} GB free, under 50 GB.`)
process.exit(failed ? 1 : 0)
