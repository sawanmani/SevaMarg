#!/usr/bin/env node
// packages/catalogue/build.mjs
// Compiles the cited YAML into the single JSON asset the APK ships:
//   android/core/src/main/assets/catalogue.json
// Offline is a packaging fact: whatever is not in this file does not exist at runtime.
import path from 'node:path'
import { mkdirSync, writeFileSync, readFileSync } from 'node:fs'
import {
  DATA_DIR, OVERRIDES_DIR, DIST_DIR, PKG_ROOT,
  listYaml, readYaml, findUnverifiedStrings, rel, stepBaseline, GLYPH, UNVERIFIED_SENTINEL,
} from './lib.mjs'

const argv = process.argv.slice(2)
const ALLOW_UNVERIFIED = argv.includes('--allow-unverified')
// A fixture-driven build must never overwrite the real APK asset.
const FIXTURE_RUN = !!process.env.SEVA_CATALOGUE_DATA_DIR
const ASSET_OUT = path.resolve(PKG_ROOT, '..', '..', 'android', 'core', 'src', 'main', 'assets', 'catalogue.json')
const SCHEMA_VERSION = 1

function fail(msg) {
  console.error(`\n${GLYPH.no} catalogue build: ${msg}\n`)
  process.exit(1)
}

// ---------------------------------------------------------------- load services
const files = listYaml(DATA_DIR) // __fixtures__ excluded: test data must never reach a device
if (files.length === 0) fail(`no YAML found in ${rel(DATA_DIR)} — nothing to build`)

const services = []
const unverified = new Map()
for (const file of files) {
  const doc = readYaml(file)
  const hits = findUnverifiedStrings(doc)
  if (hits.length) unverified.set(rel(file), hits)
  services.push(doc)
}
services.sort((a, b) => a.service_id.localeCompare(b.service_id))

// ---------------------------------------------------------------- district overrides
const overrides = listYaml(OVERRIDES_DIR).map(readYaml)
const byId = new Map(services.map((s) => [s.service_id, s]))
for (const ov of overrides) {
  const target = byId.get(ov.service_id)
  if (!target) fail(`override ${rel(OVERRIDES_DIR)} targets unknown service '${ov.service_id}'`)
  // Semantics: an override REPLACES the whole field it defines (never merges array elements),
  // so a district fee table is always one coherent, fully cited list.
  for (const [field, value] of Object.entries(ov.overrides)) {
    target[field] = value
    ;(target.district_override_fields ??= []).push(field)
  }
  target.district_scope = ov.district
}

// ---------------------------------------------------------------- provisional guard
const provisional = unverified.size > 0
if (provisional) {
  const total = [...unverified.values()].reduce((n, l) => n + l.length, 0)
  console.error(`\n${GLYPH.warn}  ${total} field(s) still hold ${UNVERIFIED_SENTINEL}:`)
  for (const [file, hits] of unverified) console.error(`   ${file}: ${hits.map((h) => h.path).slice(0, 6).join(', ')}${hits.length > 6 ? ', …' : ''}`)
  if (!ALLOW_UNVERIFIED) {
    fail('refusing to build. Run `npm run validate:catalogue` for the full list.\n' +
      '   For UI development only, use: npm run catalogue:build:dev  (stamps provisional:true,\n' +
      '   which the app renders as a visible "data not verified" banner — never as a real answer.)')
  }
  console.error(`\n${GLYPH.warn} --allow-unverified: emitting a PROVISIONAL asset for UI development only.\n`)
}

// ---------------------------------------------------------------- compute + emit
const built = services.map((s) => ({
  ...s,
  search_terms: [...new Set([...(s.lang.hi.aliases ?? []), ...(s.lang.en.aliases ?? [])])],
  channels_baseline: stepBaseline(s),
}))

const hosts = new Set()
for (const s of built) {
  JSON.stringify(s, (k, v) => {
    if (k === 'source_url' && typeof v === 'string') {
      try { hosts.add(new URL(v).hostname) } catch { /* sentinel value */ }
    }
    return v
  })
}

const bundle = {
  schema_version: SCHEMA_VERSION,
  generator: 'packages/catalogue/build.mjs',
  generated_on: new Date().toISOString().slice(0, 10),
  provisional,
  langs: ['hi', 'en'],
  statement: 'UPNIC-004',
  citation_rule: 'every fact object carries source_url + verified_on; VERIFY_OFFICIAL cannot ship',
  source_hosts: [...hosts].sort(),
  services: built,
}

const json = JSON.stringify(bundle, null, 1)
const targets = FIXTURE_RUN ? [path.join(DIST_DIR, 'catalogue.fixture.json')] : [path.join(DIST_DIR, 'catalogue.json'), ASSET_OUT]
for (const out of targets) {
  mkdirSync(path.dirname(out), { recursive: true })
  writeFileSync(out, json, 'utf8')
}

// step baseline as its own artefact, because tools/bench/stepcount.py and the deck both quote it
const baseline = Object.fromEntries(built.map((s) => [s.service_id, s.channels_baseline]))
mkdirSync(DIST_DIR, { recursive: true })
writeFileSync(path.join(DIST_DIR, 'step-baseline.json'), JSON.stringify(baseline, null, 2), 'utf8')
if (provisional) {
  writeFileSync(path.join(DIST_DIR, 'PROVISIONAL.txt'),
    `This asset was built with unverified fields on ${bundle.generated_on}.\nIt is for UI development only and must not be shipped in a submission build.\n`, 'utf8')
}

const kb = (s) => `${(Buffer.byteLength(s, 'utf8') / 1024).toFixed(1)} KB`
console.log(`\n${GLYPH.ok} catalogue.json ${bundle.services.length} service(s), ${kb(json)} gz-less${FIXTURE_RUN ? ' [fixture run - APK asset not written]' : ''}`)
for (const out of targets) console.log(`   ${GLYPH.arrow} ${rel(out)}`)
console.log(`   provisional: ${provisional}   source hosts: ${bundle.source_hosts.length}`)
for (const s of built) {
  const steps = s.channels_baseline.map((c) => `${c.kind}:${c.steps}steps/${c.visits}visit`).join('  ')
  console.log(`   ${GLYPH.dot} ${s.service_id}  (hi: ${s.lang.hi.name})  baseline ${steps || '-'}`)
}
console.log('')
