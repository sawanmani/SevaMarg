#!/usr/bin/env node
// evals/build.mjs
// Compiles the question sets into evals/dist/eval-set.json, the single input the JVM bench runner
// (:bench) reads. Keeping the eval set as data means the accuracy numbers in the deck come from the
// same file every time, and adding a question cannot accidentally change its schema.
import path from 'node:path'
import { mkdirSync, writeFileSync, existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import Ajv2020 from 'ajv/dist/2020.js'
import yaml from 'js-yaml'
import {
  DIST_DIR as CAT_DIST, GLYPH, rel,
} from '../packages/catalogue/lib.mjs'

const HERE = path.dirname(fileURLToPath(import.meta.url))
// Same trick as the catalogue validator: the test suite points this at a sandbox copy so it can
// break a question file without ever touching the repo.
const SRC_DIR = process.env.SEVA_EVALS_SRC_DIR ?? HERE
const OUT_DIR = process.env.SEVA_EVALS_OUT_DIR ?? path.join(HERE, 'dist')
const STRICT = process.argv.includes('--strict') || !!process.env.SEVA_EVALS_STRICT
// Targets from docs/PHASES.md: 50 HI + 50 EN answerable questions, 15 refusal cases.
const TARGETS = { hi: 50, en: 50, oos: 15 }

const errors = []
const warnings = []
const err = (m) => errors.push(m)
const warn = (m) => warnings.push(m)

// ---------------------------------------------------------------- source resolution
// A real file wins; the .sample.yaml is the seed so the pipeline runs before content exists.
function loadSet(name) {
  const real = path.join(SRC_DIR, `${name}.yaml`)
  const sample = path.join(SRC_DIR, `${name}.sample.yaml`)
  const file = existsSync(real) ? real : existsSync(sample) ? sample : null
  if (!file) return { file: null, items: [], provisional: true }
  // CORE_SCHEMA: YAML here is data. A !!js/function in a question file must throw, not execute.
  // The decoded list is then checked against schema/eval.schema.json before anything is used.
  const list = yaml.load(readFileSync(file, 'utf8'), { filename: file, schema: yaml.CORE_SCHEMA })
  if (!Array.isArray(list)) {
    err(`${rel(file)}: expected a top-level list of questions`)
    return { file, items: [], provisional: file.includes('.sample.') }
  }
  return { file, items: list, provisional: file.includes('.sample.') }
}

// ---------------------------------------------------------------- catalogue cross-check
const catFile = path.join(CAT_DIST, 'catalogue.json')
let catalogueServices = null
if (existsSync(catFile)) {
  const bundle = JSON.parse(readFileSync(catFile, 'utf8'))
  catalogueServices = new Map(bundle.services.map((s) => [s.service_id, s]))
  if (bundle.provisional) warn('catalogue.json is PROVISIONAL (VERIFY_OFFICIAL fields present) - scores are structural, not factual')
} else {
  err('packages/catalogue/dist/catalogue.json missing - run `npm run catalogue:build:dev` first')
}

// ---------------------------------------------------------------- schema
const ajv = new Ajv2020({ allErrors: true, strict: false })
const check = ajv.compile(JSON.parse(readFileSync(path.join(SRC_DIR, 'schema', 'eval.schema.json'), 'utf8')))

const seenIds = new Set()
const items = []

function ingest(name, langDefault) {
  const { file, items: list, provisional } = loadSet(name)
  if (!file) {
    err(`no question file for ${name} (looked for ${name}.yaml and ${name}.sample.yaml)`)
    return { count: 0, provisional: true }
  }
  if (!check(list)) {
    for (const e of check.errors) err(`${rel(file)} ${e.instancePath}: ${e.message}`)
    return { count: 0, provisional }
  }
  for (const q of list) {
    if (seenIds.has(q.id)) err(`duplicate eval id '${q.id}' in ${rel(file)}`)
    seenIds.add(q.id)
    const lang = q.lang ?? langDefault
    if (q.id.startsWith('oos-')) {
      if (q.service) err(`${q.id}: an out-of-scope question may not name a service (it must refuse)`)
      if (q.expect_fields) err(`${q.id}: an out-of-scope question must not expect answer fields`)
    } else {
      if (!q.expect_fields) err(`${q.id}: an answerable question needs expect_fields (that is the answer key)`)
      if (catalogueServices && q.service && !catalogueServices.has(q.service)) {
        const msg = `${q.id}: references service '${q.service}' which is not in the catalogue yet`
        if (STRICT) err(msg)
        else warn(msg)
      }
      // intent, when given, must be one of the expected fields (or a known alias)
      for (const f of q.expect_fields ?? []) {
        if (!(f in (catalogueServices?.get(q.service) ?? {}))) {
          warn(`${q.id}: expect_fields '${f}' is not present on '${q.service}'`)
        }
      }
    }
    items.push({ ...q, lang, source: rel(file) })
  }
  const key = name.includes('_oos') ? 'oos' : langDefault
  const need = TARGETS[key]
  if (list.length < need) warn(`${rel(file)}: ${list.length} of ${need} target questions (${key})`)
  return { count: list.length, provisional }
}

const sets = {
  hi: ingest('questions_hi', 'hi'),
  en: ingest('questions_en', 'en'),
  oos: ingest('questions_oos', 'hi'),
}

// ---------------------------------------------------------------- HI/EN twin parity
// The synopsis promises "the same facts in both languages". That is only a claim if the answer key
// says so: an English question must expect exactly what its Hindi twin expects. A mismatch here is
// a real bug (a language switch that changes the answer), so it blocks the bench instead of
// warning. Questions without a twin are counted, because the parity delta we quote needs a
// denominator that a judge can see.
const byId = new Map(items.map((q) => [q.id, q]))
let twinsChecked = 0
for (const q of items.filter((i) => i.id.startsWith('en-'))) {
  if (!q.twin_of) {
    warn(`${q.id}: no twin_of - the HI/EN parity number cannot count this question`)
    continue
  }
  const twin = byId.get(q.twin_of)
  if (!twin) {
    err(`${q.id}: twin_of '${q.twin_of}' does not exist in the Hindi set`)
    continue
  }
  twinsChecked++
  if (twin.service !== q.service) {
    err(`${q.id}/${q.twin_of}: different services (${q.service} vs ${twin.service}) - a language switch may not change which service answers`)
  }
  const a = JSON.stringify(q.expect_fields ?? null)
  const b = JSON.stringify(twin.expect_fields ?? null)
  if (a !== b) {
    err(`${q.id}/${q.twin_of}: different answer keys ${a} vs ${b} - the same question must return the same facts in both languages`)
  }
}

// ---------------------------------------------------------------- dead-alias report
// The intent router matches on catalogue names/aliases. If a benchmark question shares not one
// normalised token with the service it expects, the router has nothing to work with - we want to
// know that now, in Node, instead of discovering it on stage.
const normalise = (s) => s.toLowerCase().replace(/[^a-z0-9\u0900-\u097F\s]/g, ' ').split(/\s+/).filter(Boolean)
const stop = new Set(['kya', 'hai', 'ke', 'ki', 'ka', 'kaun', 'kahan', 'kitna', 'kitne', 'kaise', 'karna', 'karo', 'mujhe', 'mere', 'mari', 'to', 'the', 'a', 'an', 'for', 'of', 'in', 'is', 'to', 'what', 'how', 'can', 'do', 'i', 'my', 'need', 'will'])
const unreachable = []
if (catalogueServices) {
  for (const q of items) {
    if (!q.service || !catalogueServices.has(q.service)) continue
    const svc = catalogueServices.get(q.service)
    const vocab = new Set([
      ...normalise(svc.lang.hi.name), ...normalise(svc.lang.en.name),
      ...normalise((svc.search_terms ?? []).join(' ')),
    ])
    const overlap = normalise(q.ask).filter((t) => vocab.has(t) && !stop.has(t))
    if (overlap.length === 0) unreachable.push({ id: q.id, ask: q.ask, service: q.service })
  }
}

// ---------------------------------------------------------------- emit
const bundle = {
  generator: 'evals/build.mjs',
  generated_on: new Date().toISOString().slice(0, 10),
  provisional: Object.values(sets).some((s) => s.provisional),
  strict: STRICT,
  targets: TARGETS,
  counts: Object.fromEntries(Object.entries(sets).map(([k, v]) => [k, v.count])),
  total: items.length,
  refusal_cases: items.filter((q) => q.id.startsWith('oos-')).length,
  twins_paired: twinsChecked,
  unreachable_by_aliases: unreachable,
  items,
}

if (errors.length) {
  console.log(`\n${GLYPH.no} eval set has ${errors.length} problem(s) - :bench will not run on this`)
  for (const e of errors) console.log(`   ${GLYPH.dot} ${e}`)
  console.log('')
  process.exit(1)
}

mkdirSync(OUT_DIR, { recursive: true })
writeFileSync(path.join(OUT_DIR, 'eval-set.json'), JSON.stringify(bundle, null, 1), 'utf8')

console.log(`\n${GLYPH.rule}\nSeva-Marg eval builder\n${GLYPH.rule}`)
console.log(`${GLYPH.ok} ${items.length} items -> ${rel(path.join(OUT_DIR, 'eval-set.json'))}`)
console.log(`   hi ${bundle.counts.hi}/${TARGETS.hi}   en ${bundle.counts.en}/${TARGETS.en}   refusal ${bundle.counts.oos}/${TARGETS.oos}   HI/EN twins paired ${bundle.twins_paired}   provisional: ${bundle.provisional}`)
if (warnings.length) {
  console.log(`\n${GLYPH.warn}  warnings (${warnings.length})`)
  for (const w of warnings.slice(0, 14)) console.log(`   ${GLYPH.dot} ${w}`)
  if (warnings.length > 14) console.log(`   ${GLYPH.dot} ${GLYPH.md} ${warnings.length - 14} more`)
}
if (unreachable.length) {
  console.log(`\n${GLYPH.dot} questions no current alias could route (${unreachable.length}) - add aliases or expect a refusal:`)
  for (const u of unreachable.slice(0, 10)) console.log(`     ${u.id} "${u.ask}" -> ${u.service}`)
}
console.log(`${GLYPH.rule}\n`)
