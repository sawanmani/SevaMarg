#!/usr/bin/env node
// packages/i18n/parity.mjs
// Bilingual is a promise we are making in the synopsis, so it has to be a build gate, not a hope.
// Checks: identical key sets, identical {placeholder} sets, no empty/sentinel values, Hindi actually
// in Devanagari, English not left as a copy of Hindi, and short enough labels for TalkBack + small
// screens. Then emits the two JSON assets the APK ships.
import path from 'node:path'
import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { GLYPH, UNVERIFIED_SENTINEL } from '../catalogue/lib.mjs'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const STRINGS_DIR = process.env.SEVA_I18N_DIR ?? path.join(HERE, 'strings')
const ASSET_DIR = path.resolve(HERE, '..', '..', 'android', 'core', 'src', 'main', 'assets', 'i18n')
const LOCALES = ['hi', 'en']

// Language names are shown in their own script on purpose (a Hindi speaker reads "हिन्दी").
const IDENTICAL_OK = new Set(['lang.hi', 'lang.en'])
// Latin-script proper nouns that stay as-is inside Hindi UI text.
const LATIN_OK = /\b(CSC|DM|PDF|SIM|OTP|UIDAI|Aadhaar|e-District|UP|iOS|Android|OSM)\b/g
// Keys rendered as buttons / chip labels: long strings truncate on a small phone. Nav is a prefix
// because every route label is a button; the rest are exact keys, so a dialog *message* that starts
// with the same words as its button is not mistaken for a button.
const LABEL_PREFIXES = ['nav.']
const LABEL_KEYS = new Set([
  'lang.toggle', 'ask.send', 'ask.mic.start', 'ask.mic.stop',
  'answer.read_aloud', 'answer.stop_reading', 'answer.copy', 'about.clear_data',
])
const LABEL_MAX = { hi: 26, en: 30 }

const errors = []
const warnings = []
const err = (msg) => errors.push(msg)
const warn = (msg) => warnings.push(msg)

function load(locale) {
  const file = path.join(STRINGS_DIR, `${locale}.json`)
  if (!existsSync(file)) {
    err(`missing string file ${path.basename(file)}`)
    return {}
  }
  const raw = JSON.parse(readFileSync(file, 'utf8'))
  const { _meta, ...strings } = raw
  return strings
}

const placeholders = (s) => [...s.matchAll(/\{([a-zA-Z_][\w]*)\}/g)].map((m) => m[1]).sort()
const hasDevanagari = (s) => /[\u0900-\u097F]/.test(s)

const tables = Object.fromEntries(LOCALES.map((l) => [l, load(l)]))
const allKeys = [...new Set(LOCALES.flatMap((l) => Object.keys(tables[l])))].sort()

for (const key of allKeys) {
  const missing = LOCALES.filter((l) => !(key in tables[l]))
  if (missing.length) {
    err(`'${key}' is missing in ${missing.join(', ')}`)
    continue
  }
  const values = LOCALES.map((l) => tables[l][key])
  values.forEach((v, i) => {
    if (typeof v !== 'string') return err(`'${key}' is not a string in ${LOCALES[i]}`)
    if (!v.trim()) return err(`'${key}' is empty in ${LOCALES[i]}`)
    if (v.toUpperCase().includes(UNVERIFIED_SENTINEL)) err(`'${key}' still holds ${UNVERIFIED_SENTINEL} in ${LOCALES[i]}`)
    if (v !== v.trim()) err(`'${key}' has leading/trailing whitespace in ${LOCALES[i]}`)
  })

  // placeholders are a crash risk, not a style issue: a missing {date} renders literally
  const phHi = JSON.stringify(placeholders(values[0]))
  const phEn = JSON.stringify(placeholders(values[1]))
  if (phHi !== phEn) err(`'${key}' placeholders differ: hi ${phHi} vs en ${phEn}`)

  // the two honest failure modes of a bilingual app
  if (values[0] === values[1] && !IDENTICAL_OK.has(key)) {
    warn(`'${key}' is identical in both languages - untranslated? ("${values[0].slice(0, 40)}")`)
  }
  if (!hasDevanagari(values[0]) && !LATIN_OK.test(values[0]) && !IDENTICAL_OK.has(key)) {
    warn(`'${key}' has no Devanagari in hi.json: "${values[0].slice(0, 40)}"`)
  }
  LATIN_OK.lastIndex = 0

  for (let i = 0; i < LOCALES.length; i++) {
    const max = LABEL_MAX[LOCALES[i]]
    const isLabel = LABEL_KEYS.has(key) || LABEL_PREFIXES.some((p) => key.startsWith(p))
    if (isLabel && values[i].length > max) {
      warn(`'${key}' is ${values[i].length} chars in ${LOCALES[i]} (> ${max}) - a button will truncate it`)
    }
  }
}

// --------------------------------------------------------------------------- emit assets
if (!errors.length) {
  const outDir = process.env.SEVA_I18N_OUT_DIR ?? ASSET_DIR
  mkdirSync(outDir, { recursive: true })
  for (const locale of LOCALES) {
    const payload = {
      locale,
      generator: 'packages/i18n/parity.mjs',
      generated_on: new Date().toISOString().slice(0, 10),
      key_count: allKeys.length,
      strings: tables[locale],
    }
    writeFileSync(path.join(outDir, `${locale}.json`), JSON.stringify(payload, null, 1), 'utf8')
  }
}

// --------------------------------------------------------------------------- report
console.log(`\n${GLYPH.rule}\nSeva-Marg i18n parity - ${allKeys.length} keys x ${LOCALES.length} languages\n${GLYPH.rule}`)
if (warnings.length) {
  console.log(`${GLYPH.warn}  warnings (${warnings.length})`)
  for (const w of warnings) console.log(`   ${GLYPH.dot} ${w}`)
}
if (errors.length) {
  console.log(`\n${GLYPH.no} errors (${errors.length}) - both languages must ship the same screen`)
  for (const e of errors) console.log(`   ${GLYPH.dot} ${e}`)
  console.log(GLYPH.rule)
} else {
  console.log(`${GLYPH.ok} key sets, placeholders and scripts are in parity`)
  console.log(`${GLYPH.arrow} ${LOCALES.map((l) => `android/core/src/main/assets/i18n/${l}.json`).join('  ')}`)
  console.log(GLYPH.rule)
}
process.exit(errors.length ? 1 : 0)
