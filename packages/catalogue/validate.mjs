#!/usr/bin/env node
// packages/catalogue/validate.mjs
// The no-fabrication gate. Exits non-zero if any user-visible fact lacks an official citation,
// or still carries the VERIFY_OFFICIAL sentinel. Nothing downstream (build, APK, deck) is allowed
// to run on top of a red result here.
import path from 'node:path'
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs'
import Ajv2020 from 'ajv/dist/2020.js'
import {
  DATA_DIR, OVERRIDES_DIR, SCHEMA_DIR, DIST_DIR,
  listYaml, readYaml, findUnverifiedStrings, findUncitedFactObjects,
  officialHostOk, findIdenticalTwins, isFixture, rel, GLYPH, UNVERIFIED_SENTINEL,
} from './lib.mjs'

const argv = process.argv.slice(2)
const jsonOut = argv.includes('--json') ? path.join(DIST_DIR, 'validation.json') : null
const WITH_FIXTURES = argv.includes('--include-fixtures')

const ajv = new Ajv2020({ allErrors: true, strict: false })
const serviceSchema = JSON.parse(readFileSync(path.join(SCHEMA_DIR, 'service.schema.json'), 'utf8'))
const overrideSchema = JSON.parse(readFileSync(path.join(SCHEMA_DIR, 'override.schema.json'), 'utf8'))
const checkService = ajv.compile(serviceSchema)
const checkOverride = ajv.compile(overrideSchema)

const errors = []
const warnings = []
const seenIds = new Map()
const aliasOwner = new Map()
let fieldCount = 0

function err(file, msg, detail) {
  errors.push({ file: rel(file), msg, ...(detail ? { detail } : {}) })
}
function warn(file, msg) {
  warnings.push({ file: rel(file), msg })
}

function todayIso() {
  return new Date().toISOString().slice(0, 10)
}

function validateServiceDoc(doc, file) {
  if (!checkService(doc)) {
    for (const e of checkService.errors) {
      err(file, `schema: ${e.instancePath || '/'} ${e.message}`, e.params?.allowedValues ? `allowed: ${e.params.allowedValues.join('|')}` : undefined)
    }
    return
  }

  // 1. sentinel values: a field nobody has verified yet
  const sentinel = findUnverifiedStrings(doc)
  for (const s of sentinel) err(file, `unverified field '${s.path}' still holds ${UNVERIFIED_SENTINEL} / 0000-00-00`)

  // 2. every fact-carrying object must carry source_url + verified_on
  for (const u of findUncitedFactObjects(doc)) {
    err(file, `fact node '${u.path}' is missing citation field(s): ${u.missing.join(', ')}`)
  }

  // 3. citations must point at official government pages
  const fixture = isFixture(file)
  for (const s of collectSources(doc)) {
    fieldCount++
    if (fixture) continue // fixtures point at example.invalid on purpose
    if (!officialHostOk(s.url)) {
      err(file, `source_url is not an official government domain: ${s.url} (${s.path})`)
    }
    if (s.date > todayIso()) {
      err(file, `verified_on is in the future: ${s.date} at ${s.path}`)
    }
  }

  // 4. Hindi must actually be Hindi: identical hi/en means an untranslated string shipped to a
  //    user who asked for their own language.
  for (const t of findIdenticalTwins(doc)) {
    warn(file, `hi/en twin identical at '${t.path}' ("${truncate(t.value)}") — is this untranslated?`)
  }

  // 5. step baseline integrity (this is the denominator of our headline number)
  for (let i = 0; i < (doc.channel ?? []).length; i++) {
    const ch = doc.channel[i]
    const ns = ch.steps.map((s) => s.n)
    const expected = ch.steps.map((_, k) => k + 1)
    if (ns.join(',') !== expected.join(',')) {
      err(file, `channel[${i}] (${ch.kind}) steps must be numbered 1..${ch.steps.length}, got ${ns.join(',')}`)
    }
  }

  // 6. the refusal path needs somewhere to send people
  if (!(doc.escalation ?? []).some((e) => typeof e.contact === 'string' && e.contact.trim().length > 0)) {
    err(file, 'no escalation entry carries a verified contact — the refusal path has nothing to offer')
  }

  // 7. routing sanity: aliases are how the intent router decides
  const aliases = [...(doc.lang.hi.aliases ?? []), ...(doc.lang.en.aliases ?? [])].map(normalise)
  for (const a of aliases) {
    const owner = aliasOwner.get(a)
    if (owner && owner !== doc.service_id) {
      err(file, `alias "${a}" is claimed by both '${owner}' and '${doc.service_id}' — routing would be ambiguous`)
    }
    aliasOwner.set(a, doc.service_id)
  }
  if (aliases.length < 4) warn(file, `only ${aliases.length} aliases — colloquial phrasings are what real speech input produces`)

  if ((doc.confidence_floor ?? 1) < 0.8) warn(file, `confidence_floor ${doc.confidence_floor} is permissive for a governance answer`)
  if (doc.stale_after_days > 180) warn(file, `stale_after_days ${doc.stale_after_days} is longer than one charter cycle`)

  const dup = seenIds.get(doc.service_id)
  if (dup) err(file, `duplicate service_id '${doc.service_id}' (also in ${dup})`)
  seenIds.set(doc.service_id, rel(file))
}

function validateOverrideDoc(doc, file) {
  if (!checkOverride(doc)) {
    for (const e of checkOverride.errors) err(file, `schema: ${e.instancePath || '/'} ${e.message}`)
    return
  }
  for (const s of findUnverifiedStrings(doc)) err(file, `unverified field '${s.path}' still holds a sentinel`)
  for (const u of findUncitedFactObjects(doc)) err(file, `override node '${u.path}' missing: ${u.missing.join(', ')}`)
  for (const s of collectSources(doc)) {
    fieldCount++
    if (!officialHostOk(s.url)) err(file, `override source_url is not official: ${s.url}`)
  }
  if (!seenIds.has(doc.service_id)) err(file, `override targets unknown service_id '${doc.service_id}'`)
}

function collectSources(node, prefix = '', out = []) {
  if (Array.isArray(node)) {
    node.forEach((v, i) => collectSources(v, `${prefix}[${i}]`, out))
  } else if (node && typeof node === 'object') {
    if (typeof node.source_url === 'string') {
      out.push({ path: prefix, url: node.source_url, date: node.verified_on })
    }
    for (const [k, v] of Object.entries(node)) collectSources(v, prefix ? `${prefix}.${k}` : k, out)
  }
  return out
}

function normalise(s) {
  return s.toLowerCase().replace(/[^a-z0-9\u0900-\u097F\s]/g, '').replace(/\s+/g, ' ').trim()
}
function truncate(s) {
  return s.length > 40 ? `${s.slice(0, 40)}${GLYPH.md}` : s
}

// --------------------------------------------------------------------------- run
const serviceFiles = listYaml(DATA_DIR, { includeFixtures: WITH_FIXTURES })
const overrideFiles = listYaml(OVERRIDES_DIR, { includeFixtures: WITH_FIXTURES })

for (const file of serviceFiles) validateServiceDoc(readYaml(file), file)
for (const file of overrideFiles) validateOverrideDoc(readYaml(file), file)

const services = seenIds.size
const coverage = { services, cited_fact_objects: fieldCount, errors: errors.length, warnings: warnings.length }

// --------------------------------------------------------------------------- report
const bar = GLYPH.rule
console.log(`\n${bar}\nSeva-Marg catalogue validator - citation gate\n${bar}`)
console.log(`services: ${services}   files: ${serviceFiles.length + overrideFiles.length}   cited fact objects: ${fieldCount}`)
console.log(`data dir: ${process.env.SEVA_CATALOGUE_DATA_DIR ? rel(DATA_DIR) + ' (fixture override)' : rel(DATA_DIR)}`)

if (warnings.length) {
  console.log(`\n${GLYPH.warn}  warnings (${warnings.length})`)
  for (const w of warnings.slice(0, 20)) console.log(`   ${w.file}: ${w.msg}`)
  if (warnings.length > 20) console.log(`   ${GLYPH.md} ${warnings.length - 20} more`)
}

if (errors.length) {
  const byFile = new Map()
  for (const e of errors) byFile.set(e.file, [...(byFile.get(e.file) ?? []), e])
  console.log(`\n${GLYPH.no} errors (${errors.length}) - the build is blocked until every one has an official citation`)
  for (const [file, list] of byFile) {
    console.log(`\n  ${file}  (${list.length})`)
    for (const e of list.slice(0, 12)) console.log(`     ${GLYPH.dot} ${e.msg}${e.detail ? ` [${e.detail}]` : ''}`)
    if (list.length > 12) console.log(`     ${GLYPH.dot} ${GLYPH.md} ${list.length - 12} more`)
  }
  console.log(`\nFix: put the real value plus source_url + verified_on in the YAML, or leave the`)
  console.log(`sentinel and the field will not ship. Never invent a fee, document, office or SLA.\n${bar}`)
} else {
  console.log(`\n${GLYPH.ok} every fact field is cited from an official domain, no ${UNVERIFIED_SENTINEL} remains`)
  console.log(bar)
}

if (jsonOut) {
  mkdirSync(DIST_DIR, { recursive: true })
  writeFileSync(jsonOut, JSON.stringify({ coverage, errors, warnings }, null, 2), 'utf8')
  console.log(`json report → ${rel(jsonOut)}`)
}

process.exit(errors.length ? 1 : 0)
