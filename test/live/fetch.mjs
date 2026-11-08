// Downloads the jars a test server needs into work/cache, checking each against the checksum its publisher lists.
// Paper comes from PaperMC's download API (SHA-256). Other plugins come from Modrinth (SHA-512). Nothing is run here.
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'

const HERE = path.dirname(new URL(import.meta.url).pathname)
export const CACHE = path.join(process.env.PMT_WORK ?? path.join(HERE, 'work'), 'cache')
const UA = { 'User-Agent': 'portmanteau-live-tests (github.com/ExtantApe/Portmanteau)' }

async function json(url) {
  const r = await fetch(url, { headers: UA })
  if (!r.ok) throw new Error(`${url}: HTTP ${r.status}`)
  return r.json()
}

async function download(url, file, algorithm, expected) {
  if (existsSync(file) && hash(readFileSync(file), algorithm) === expected) return file
  const r = await fetch(url, { headers: UA })
  if (!r.ok) throw new Error(`${url}: HTTP ${r.status}`)
  const bytes = Buffer.from(await r.arrayBuffer())
  const got = hash(bytes, algorithm)
  if (got !== expected) throw new Error(`${path.basename(file)}: ${algorithm} is ${got}, but the publisher lists ${expected}. Not using it.`)
  writeFileSync(file, bytes)
  return file
}

const hash = (bytes, algorithm) => createHash(algorithm).update(bytes).digest('hex')

/** The latest stable Paper build for a Minecraft version. Returns { file, build }. */
export async function paper(version) {
  mkdirSync(CACHE, { recursive: true })
  const b = await json(`https://fill.papermc.io/v3/projects/paper/versions/${version}/builds/latest`)
  const d = b.downloads['server:default']
  const file = path.join(CACHE, d.name)
  await download(d.url, file, 'sha256', d.checksums.sha256)
  return { file, build: b.id }
}

/** The newest Modrinth release of a project for Paper on this Minecraft version. Returns { file, version } or null. */
export async function modrinth(slug, mcVersion, loaders = ['paper', 'bukkit', 'spigot']) {
  mkdirSync(CACHE, { recursive: true })
  const q = `loaders=${encodeURIComponent(JSON.stringify(loaders))}&game_versions=${encodeURIComponent(JSON.stringify([mcVersion]))}`
  let versions
  try {
    versions = await json(`https://api.modrinth.com/v2/project/${slug}/version?${q}`)
  } catch {
    return null
  }
  const v = versions.find(x => x.version_type === 'release') ?? versions[0]
  if (!v) return null
  const f = v.files.find(x => x.primary) ?? v.files[0]
  const file = path.join(CACHE, f.filename)
  await download(f.url, file, 'sha512', f.hashes.sha512)
  return { file, version: v.version_number }
}

if (process.argv[1] === new URL(import.meta.url).pathname) {
  const mc = process.argv[2] ?? '26.3'
  console.log(await paper(mc))
  for (const slug of ['multiverse-core', 'multiverse-inventories', 'griefprevention', 'coreprotect', 'viaversion', 'viabackwards']) {
    console.log(slug, await modrinth(slug, mc))
  }
}
