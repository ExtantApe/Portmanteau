// /portmanteau import chestsort on a server with a made-up ChestSort folder: only numbered category files are copied,
// the default presets are fitted to the new categories, and config.yml keeps its comments.
import { readFileSync, readdirSync, writeFileSync, mkdirSync, existsSync } from 'node:fs'
import path from 'node:path'
import { Server, Results, sleep } from './lib.mjs'

// Twelve made-up categories. The default presets name wood, weapons and food (kept here), and tools, armor,
// stone-and-building and colorful-blocks (not here).
const CATEGORIES = {
  '100-wood.txt': ['*_log', '*_planks'],
  '200-weapons.txt': ['*_sword', 'bow', 'crossbow'],
  '300-food.txt': ['bread', 'cooked_*', 'apple'],
  '400-ores.txt': ['*_ore', 'raw_*'],
  '500-gems.txt': ['diamond', 'emerald'],
  '600-redstone.txt': ['redstone', 'repeater', 'comparator'],
  '700-farming.txt': ['wheat*', '*_seeds'],
  '800-mob-drops.txt': ['bone', 'string', 'gunpowder'],
  '810-dyes.txt': ['*_dye'],
  '820-glass.txt': ['*glass*'],
  '830-wool.txt': ['*_wool'],
  '900-misc.txt': ['torch', 'ladder'],
}
const README = '_ReadMe - Category files.txt'
const comments = text => text.split('\n').map(l => l.trim()).filter(l => l.startsWith('#'))

export async function importSuite(version) {
  const r = new Results(`import-${version}`)
  const s = new Server(version, { tag: r.name })
  try {
    await s.prepare()
    const cs = path.join(s.dir, 'plugins', 'ChestSort')
    mkdirSync(path.join(cs, 'categories'), { recursive: true })
    for (const [name, lines] of Object.entries(CATEGORIES)) writeFileSync(path.join(cs, 'categories', name), lines.join('\n') + '\n')
    writeFileSync(path.join(cs, 'categories', README), 'These files decide the order.\nName them with a number first.\nOne item per line.\n')
    writeFileSync(path.join(cs, 'config.yml'), [
      'sorting-method: "{category},{name},{color}"',
      'disabled-worlds:', '- made_up_world',
      'sorting-enabled-by-default: true',
      'hotkeys:', '  middle-click: true',
    ].join('\n') + '\n')

    await s.start()
    const ours = path.join(s.dir, 'plugins', 'Portmanteau')
    const defaultComments = comments(readFileSync(path.join(ours, 'config.yml'), 'utf8'))
    r.check('the default config.yml has comments to keep', defaultComments.length > 20, `${defaultComments.length} comment lines`)

    const report = await s.run('portmanteau import chestsort', 3000)
    const text = report.join('\n')
    const copied = readdirSync(path.join(ours, 'categories')).sort()
    r.check('only the 12 numbered files are copied', copied.length === 12 && !copied.includes(README), copied.join(', '))
    r.check('the read-me is left out with one clear line',
      report.filter(l => l.includes(README)).length === 1 && /doesn't start with a number/.test(text), report.find(l => l.includes(README)) ?? '(no line)')
    r.check('no category file warnings', !/[Cc]ouldn't read/.test(text))
    r.check('no warnings about presets naming missing categories', !/no category with that name/.test(text), text)
    r.check('the presets were fitted, and the report says so',
      /tools-first no longer puts tools, armor first/.test(text) && /blocks-first no longer puts stone-and-building, colorful-blocks first/.test(text), text)

    const after = readFileSync(path.join(ours, 'config.yml'), 'utf8')
    const kept = comments(after)
    const lost = defaultComments.filter(c => !kept.includes(c))
    r.check('config.yml keeps every comment', lost.length === 0, lost.length ? `lost ${lost.length}: ${lost.slice(0, 3).join(' | ')}` : `${kept.length} comment lines`)
    r.check('the method and disabled worlds were carried over',
      /method: ['"]?\{category\},\{name\},\{color\}/.test(after) && /made_up_world/.test(after))
    r.check('presets now name only categories that exist', /first:\s*\n\s*- weapons/.test(after) && !/stone-and-building/.test(after))
    r.check('nothing is left half written', !existsSync(path.join(ours, 'config.yml.tmp')))

    const reload = (await s.run('portmanteau reload', 2000)).join('\n')
    r.check('a reload afterwards has nothing to warn about', !/no category with that name|[Cc]ouldn't read|isn't a preset/.test(reload), reload)
    const cats = (await s.run('portmanteau categories', 1500)).join('\n')
    r.check('/portmanteau categories lists the 12 in ChestSort\'s order', /Wood[\s\S]*Weapons[\s\S]*Food[\s\S]*Ores[\s\S]*Misc/.test(cats), cats)

    // A read-me copied in by hand is skipped by the loader too, with one line and no warnings.
    writeFileSync(path.join(ours, 'categories', README), 'Name them with a number first.\n')
    const hand = (await s.run('portmanteau reload', 2000)).join('\n')
    r.check('a read-me copied by hand is skipped without warnings', /Not category files, so not read/.test(hand) && !/[Cc]ouldn't read/.test(hand), hand)
  } finally {
    await s.stop()
    s.remove()
  }
  return r
}
