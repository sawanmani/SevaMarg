// packages/catalogue/lib.mjs
// Shared loading / walking rules for the catalogue. Keep the rules HERE so validate.mjs,
// build.mjs and tools/bench/* can never disagree about what "cited" means.
import { readFileSync, readdirSync, existsSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import yaml from 'js-yaml'

const HERE = path.dirname(fileURLToPath(import.meta.url))

export const PKG_ROOT = HERE
// Env override exists for one reason: the test suite must be able to run the REAL validator CLI
// against __fixtures__/ without touching data/. Production builds never set it.
export const DATA_DIR = process.env.SEVA_CATALOGUE_DATA_DIR ?? path.join(HERE, 'data')
export const OVERRIDES_DIR = process.env.SEVA_CATALOGUE_OVERRIDES_DIR ?? path.join(HERE, 'overrides')
export const SCHEMA_DIR = path.join(HERE, 'schema')
export const DIST_DIR = path.join(HERE, 'dist')
export const FIXTURE_MARKER = '__fixtures__'

/**
 * Windows PowerShell 5.1 renders Node's UTF-8 stdout through the legacy OEM codepage, so the
 * box-drawing glyphs in our reports arrive as mojibake on a lot of machines. A judge running a
 * script should see clean output, so pick ASCII when the console clearly cannot do UTF-8.
 * Force either way with SEVA_MARG_ASCII=1 / SEVA_MARG_UNICODE=1.
 */
const unicodeOk =
  !process.env.SEVA_MARG_ASCII &&
  (process.env.SEVA_MARG_UNICODE ||
    process.platform !== 'win32' ||
    !!process.env.WT_SESSION ||
    /utf-?8/i.test(process.env.PYTHONIOENCODING ?? '') ||
    /utf-?8/i.test(process.env.LANG ?? ''))

export const GLYPH = unicodeOk
  ? { rule: '─'.repeat(74), ok: '✅', no: '❌', warn: '⚠️', dot: '•', arrow: '→', md: '…' }
  : { rule: '-'.repeat(74), ok: '[OK]', no: '[FAIL]', warn: '[WARN]', dot: '*', arrow: '->', md: '...' }

/** The sentinel that makes fabrication impossible: an unverified value cannot be typed by accident. */
export const UNVERIFIED_SENTINEL = 'VERIFY_OFFICIAL'
export const UNVERIFIED_DATE = '0000-00-00'

/**
 * Official-source allowlist. A citation only counts if it comes from a government service page.
 * News articles, blogs, PDFs of unknown provenance and agent websites are NOT citations.
 * Extend deliberately — every addition is a claim a judge can check.
 */
export const OFFICIAL_HOST_SUFFIXES = [
  '.nic.in',
  '.gov.in',
  '.up.gov.in',
  'upcdn.up.gov.in',
  'e-district.gov.in',
  'upcsc.in',
  'csc.gov.in',
  'sdsup.gov.in',
  'updsdm.up.gov.in',
  'data.up.gov.in',
  'india.gov.in',
]

export function isFixture(filePath) {
  return filePath.split(path.sep).includes(FIXTURE_MARKER)
}

export function listYaml(dir, { includeFixtures = false } = {}) {
  if (!existsSync(dir)) return []
  return readdirSync(dir)
    .filter((f) => f.endsWith('.yaml') || f.endsWith('.yml'))
    .map((f) => path.join(dir, f))
    .filter((p) => {
      if (!statSync(p).isFile()) return false
      return includeFixtures || !isFixture(p)
    })
    .sort()
}

export function readYaml(file) {
  // CORE_SCHEMA on purpose: js-yaml's DEFAULT_SCHEMA can construct !!js/function, !!js/regexp and
  // !!js/undefined objects. Our catalogue is data, never code — a hand-edited YAML file must not
  // be able to execute anything at build time. Unknown tags then throw instead of materialising.
  const doc = yaml.load(readFileSync(file, 'utf8'), {
    filename: file,
    schema: yaml.CORE_SCHEMA,
  })
  if (doc === null || typeof doc !== 'object' || Array.isArray(doc)) {
    throw new Error(`${file}: expected a top-level YAML mapping`)
  }
  return doc
}

/** Walk every string in the tree with its JSON-ish path. Arrays become [i]. */
export function* walkStrings(node, prefix = '') {
  if (typeof node === 'string') {
    yield { path: prefix, value: node }
    return
  }
  if (Array.isArray(node)) {
    for (let i = 0; i < node.length; i++) yield* walkStrings(node[i], `${prefix}[${i}]`)
    return
  }
  if (node && typeof node === 'object') {
    for (const [k, v] of Object.entries(node)) yield* walkStrings(v, prefix ? `${prefix}.${k}` : k)
  }
}

/** Walk every *object* node so we can check that fact-carrying objects carry citations. */
export function* walkObjects(node, prefix = '') {
  if (node && typeof node === 'object') {
    yield { path: prefix, node }
    if (Array.isArray(node)) {
      for (let i = 0; i < node.length; i++) yield* walkObjects(node[i], `${prefix}[${i}]`)
    } else {
      for (const [k, v] of Object.entries(node)) yield* walkObjects(v, prefix ? `${prefix}.${k}` : k)
    }
  }
}

export function findUnverifiedStrings(doc) {
  const hits = []
  for (const { path: p, value } of walkStrings(doc)) {
    if (value.toUpperCase().includes(UNVERIFIED_SENTINEL) || value === UNVERIFIED_DATE) {
      hits.push({ path: p, value })
    }
  }
  return hits
}

/**
 * A "fact-carrying object" is any object that holds user-visible content or a number a citizen
 * would act on. Each one MUST be traceable to source_url + verified_on. This is the rule that
 * makes the "how do we know you did not invent the fee?" question unaskable.
 *
 * Citation inheritance: a node may rely on the citation of its nearest cited ancestor, because
 * one procedure is read from one page. So channel[].steps[] inherits the channel's citation
 * instead of forcing us to paste the same URL nine times — but the channel itself must be cited,
 * and if nothing above a fact node is cited, it is reported.
 */
const FACT_KEYS = new Set([
  'text', 'item', 'means', 'q', 'a', 'scope', 'how_to_find', 'do',
  'amount_inr', 'days', 'eta_days', 'steps', 'contact',
])

export function findUncitedFactObjects(doc) {
  const bad = []
  const visit = (node, pathPrefix, citedByAncestor) => {
    if (node === null || typeof node !== 'object') return
    if (Array.isArray(node)) {
      node.forEach((v, i) => visit(v, `${pathPrefix}[${i}]`, citedByAncestor))
      return
    }
    const keys = Object.keys(node)
    const selfCited = keys.includes('source_url') && keys.includes('verified_on')
    const carriesFact = keys.some((k) => FACT_KEYS.has(k))
    if (carriesFact && !selfCited && !citedByAncestor) {
      bad.push({ path: pathPrefix || '<root>', missing: ['source_url', 'verified_on'].filter((k) => !keys.includes(k)) })
    }
    if (selfCited && keys.includes('source_url') && keys.includes('verified_on')) {
      // cited: keep checking, but children may legitimately inherit this citation
      for (const [k, v] of Object.entries(node)) visit(v, pathPrefix ? `${pathPrefix}.${k}` : k, true)
      return
    }
    for (const [k, v] of Object.entries(node)) visit(v, pathPrefix ? `${pathPrefix}.${k}` : k, citedByAncestor)
  }
  visit(doc, '', false)
  return bad
}

export function officialHostOk(url) {
  try {
    const u = new URL(url)
    const host = u.hostname.toLowerCase()
    return OFFICIAL_HOST_SUFFIXES.some(
      (suffix) => host === suffix.replace(/^\./, '') || host.endsWith(suffix.startsWith('.') ? suffix : `.${suffix}`),
    )
  } catch {
    return false
  }
}

/** Bilingual objects must not be the same string in both languages (that means "not translated"). */
export function findIdenticalTwins(doc) {
  const out = []
  for (const { path: p, node } of walkObjects(doc)) {
    if (Array.isArray(node) || node === null) continue
    if (typeof node.hi === 'string' && typeof node.en === 'string' && node.hi.trim() === node.en.trim()) {
      // A sentinel twin is not an untranslated string, it is an unverified one; the error report
      // already says that loudly. Warning about it too would bury the real translation gaps.
      if (node.hi.toUpperCase().includes(UNVERIFIED_SENTINEL) || node.hi === UNVERIFIED_DATE) continue
      out.push({ path: p, value: node.hi })
    }
  }
  return out
}

/** Step-reduction denominator, computed from the catalogue so it is auditable, not typed on a slide. */
export function stepBaseline(doc) {
  return (doc.channel ?? []).map((ch) => ({
    kind: ch.kind,
    steps: ch.steps.length,
    visits: ch.steps.filter((s) => s.needs_visit).length,
    verified: !(ch.source_url ?? '').toUpperCase().includes(UNVERIFIED_SENTINEL),
  }))
}

export function rel(p) {
  return path.relative(path.join(HERE, '..', '..'), p).split(path.sep).join('/')
}
