#!/usr/bin/env node
// packages/i18n/i18n.test.mjs
// The bilingual promise in the synopsis ("same facts in both languages") is only real if a missing
// English string fails a build instead of showing up as a blank label in front of a judge.
import { test, after } from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdtempSync, writeFileSync, readFileSync, rmSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const PARITY = path.join(HERE, 'parity.mjs')
const REAL = path.join(HERE, 'strings')
const TMP_ROOTS = []
after(() => {
  for (const dir of TMP_ROOTS) rmSync(dir, { recursive: true, force: true })
})

function runParity(mutate) {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'sevamarg-i18n-'))
  const out = mkdtempSync(path.join(os.tmpdir(), 'sevamarg-i18n-out-'))
  TMP_ROOTS.push(dir, out)
  const tables = { hi: readJson(path.join(REAL, 'hi.json')), en: readJson(path.join(REAL, 'en.json')) }
  if (mutate) mutate(tables)
  for (const [locale, table] of Object.entries(tables)) {
    writeFileSync(path.join(dir, `${locale}.json`), JSON.stringify(table, null, 2), 'utf8')
  }
  const res = spawnSync(process.execPath, [PARITY], {
    encoding: 'utf8',
    env: { ...process.env, SEVA_MARG_ASCII: '1', SEVA_I18N_DIR: dir, SEVA_I18N_OUT_DIR: out },
  })
  return { code: res.status, out: `${res.stdout ?? ''}${res.stderr ?? ''}`, assetDir: out }
}

function readJson(p) {
  return JSON.parse(readFileSync(p, 'utf8'))
}

test('the shipped string files are in parity and emit two assets', () => {
  const { code, out, assetDir } = runParity(null)
  assert.equal(code, 0, out)
  const hi = readJson(path.join(assetDir, 'hi.json'))
  const en = readJson(path.join(assetDir, 'en.json'))
  assert.equal(hi.locale, 'hi')
  assert.equal(hi.key_count, en.key_count)
  assert.ok(hi.key_count >= 60, 'UI has enough strings for the five routes')
  assert.match(hi.strings['app.tagline'], /[\u0900-\u097F]/)
})

test('a key present only in Hindi fails the build', () => {
  const { code, out } = runParity((t) => {
    t.hi['nav.newpage'] = 'नया पृष्ठ'
  })
  assert.equal(code, 1)
  assert.match(out, /'nav\.newpage' is missing in en/)
})

test('a placeholder dropped in translation fails the build', () => {
  const { code, out } = runParity((t) => {
    t.en['answer.verified_upto'] = 'This information was verified recently'
  })
  assert.equal(code, 1)
  assert.match(out, /placeholders differ/)
})

test('an empty string fails the build (TalkBack would read silence)', () => {
  const { code, out } = runParity((t) => {
    t.hi['ask.send'] = '  '
  })
  assert.equal(code, 1)
  assert.match(out, /'ask\.send' is empty in hi/)
})

test('an untranslated Hindi label is warned about, loudly', () => {
  const { code, out } = runParity((t) => {
    t.hi['nav.checklist'] = t.en['nav.checklist']
  })
  assert.equal(code, 0)
  assert.match(out, /\[WARN\][\s\S]*'nav\.checklist' is identical in both languages/)
})

test('a VERIFY_OFFICIAL that leaked into UI strings is an error', () => {
  const { code, out } = runParity((t) => {
    t.en['about.version'] = 'VERIFY_OFFICIAL'
  })
  assert.equal(code, 1)
  assert.match(out, /still holds VERIFY_OFFICIAL/)
})

// --------------------------------------------------------------------------- schema <-> strings
// service.schema.json closes a handful of fields to a fixed vocabulary (who, mode, channel kind,
// next-step status, office type). The catalogue can therefore only ever contain those values, and
// :core turns each one into words through a string key. A value without a label would render as
// "dm_office" in front of a Hindi-speaking citizen - so this test is the drift gate between the
// schema and the two string files, and it is the reason the labels live in i18n and not in Kotlin.
const SCHEMA = path.join(HERE, '..', 'catalogue', 'schema', 'service.schema.json')
const ENUM_KEY_PREFIX = {
  who: 'answer.enum.who.',
  mode: 'answer.enum.mode.',
  kind: 'answer.enum.channel.',
  status: 'answer.enum.status.',
  type: 'office.type.',
}

/** Collect [propertyName, allowedValues] for every closed vocabulary the schema declares. */
function collectEnums(node, name, found = []) {
  if (!node || typeof node !== 'object' || Array.isArray(node)) return found
  if (Array.isArray(node.enum)) found.push([name, node.enum])
  if (node.items) {
    // an array of closed values (office.type) is one vocabulary; an array of objects is a subtree
    if (Array.isArray(node.items.enum)) found.push([name, node.items.enum])
    else collectEnums(node.items, name, found)
  }
  for (const [key, value] of Object.entries(node.properties ?? {})) collectEnums(value, key, found)
  for (const [key, value] of Object.entries(node.definitions ?? {})) collectEnums(value, key, found)
  return found
}

const VOCABULARIES = collectEnums(readJson(SCHEMA), null).filter(([name]) => name in ENUM_KEY_PREFIX)

function missingEnumLabels(tables) {
  const missing = []
  for (const [name, values] of VOCABULARIES) {
    for (const value of values) {
      const key = ENUM_KEY_PREFIX[name] + value
      for (const locale of ['hi', 'en']) {
        if (!(key in tables[locale])) missing.push(`${locale}: ${key}`)
      }
    }
  }
  return missing
}

test('the schema declares the five closed vocabularies this app labels', () => {
  assert.deepEqual(VOCABULARIES.map(([name]) => name).sort(), ['kind', 'mode', 'status', 'type', 'who'])
})

test('every schema enum value has a Hindi and an English label', () => {
  const tables = { hi: readJson(path.join(REAL, 'hi.json')), en: readJson(path.join(REAL, 'en.json')) }
  assert.deepEqual(missingEnumLabels(tables), [])
})

test('a new enum value with no label is caught before it ships as "dm_office"', () => {
  const tables = { hi: readJson(path.join(REAL, 'hi.json')), en: readJson(path.join(REAL, 'en.json')) }
  delete tables.en['answer.enum.status.rejected']
  assert.deepEqual(missingEnumLabels(tables), ['en: answer.enum.status.rejected'])
})
