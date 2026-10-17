#!/usr/bin/env node
// packages/catalogue/catalogue.test.mjs
// Runs on the Node built-in test runner (node --test), zero extra dependencies.
//
// Why this file exists: validate.mjs is the claim our whole "we did not invent the fee" story
// rests on. A gate nobody tests is a gate that silently stops working. So every rule below is
// tested in BOTH directions - a fully cited fixture must pass, and each specific violation must
// produce that specific error. We drive the real CLI (not just the functions) so argument
// parsing and the exit code are covered too.
import { test, after } from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import yaml from 'js-yaml'
import { readYaml } from './lib.mjs'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const GOOD = path.join(HERE, '__fixtures__', 'pass', 'fixture-good-service.yaml')
const VALIDATE = path.join(HERE, 'validate.mjs')
const TMP_ROOTS = []

/** Run the real validator against a throwaway data dir built from the good fixture. */
function runValidator(mutate) {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'sevamarg-cat-'))
  const overrides = path.join(dir, 'overrides') // deliberately nonexistent: no overrides under test
  TMP_ROOTS.push(dir)
  const doc = readYaml(GOOD) // CORE_SCHEMA via lib.mjs: catalogue is data, never code
  if (mutate) mutate(doc)
  writeFileSync(path.join(dir, 'case.yaml'), yaml.dump(doc, { lineWidth: 200, noRefs: true }), 'utf8')
  const res = spawnSync(process.execPath, [VALIDATE], {
    encoding: 'utf8',
    env: { ...process.env, SEVA_MARG_ASCII: '1', SEVA_CATALOGUE_DATA_DIR: dir, SEVA_CATALOGUE_OVERRIDES_DIR: overrides },
  })
  return { code: res.status, out: `${res.stdout ?? ''}${res.stderr ?? ''}` }
}

after(() => {
  for (const dir of TMP_ROOTS) rmSync(dir, { recursive: true, force: true })
})

test('a fully cited service passes the gate (exit 0)', () => {
  const { code, out } = runValidator(null)
  assert.equal(code, 0, out)
  assert.match(out, /\[OK\] every fact field is cited/)
})

test('VERIFY_OFFICIAL sentinel blocks the build', () => {
  const { code, out } = runValidator((d) => {
    d.eligibility[0].text.hi = 'VERIFY_OFFICIAL'
  })
  assert.equal(code, 1)
  assert.match(out, /unverified field 'eligibility\[0\]\.text\.hi'/)
})

test('an unverified date 0000-00-00 blocks the build', () => {
  const { code, out } = runValidator((d) => {
    d.sla.verified_on = '0000-00-00'
  })
  assert.equal(code, 1)
  assert.match(out, /unverified field 'sla\.verified_on'/)
})

test('a fact object with no citation anywhere above it blocks the build', () => {
  const { code, out } = runValidator((d) => {
    delete d.fees[0].source_url
    delete d.fees[0].verified_on
  })
  assert.equal(code, 1)
  // schema catches it first and says so - that is the belt to the walker's braces
  assert.match(out, /schema: \/fees\/0/)
})

test('channel steps inherit the channel citation (no per-step URLs required)', () => {
  const { code, out } = runValidator(null)
  assert.equal(code, 0, out)
  assert.doesNotMatch(out, /channel\[0\]\.steps\[0\]/)
})

test('step numbering must be 1..N - the step-reduction denominator is auditable', () => {
  const { code, out } = runValidator((d) => {
    d.channel[0].steps[1].n = 3
  })
  assert.equal(code, 1)
  assert.match(out, /steps must be numbered 1\.\.2, got 1,3/)
})

test('the refusal path must have a verified contact to send people to', () => {
  const { code, out } = runValidator((d) => {
    d.escalation[0].contact = '   '
  })
  assert.equal(code, 1)
  assert.match(out, /no escalation entry carries a verified contact/)
})

test('an untranslatable hi/en twin is warned about, not silently shipped', () => {
  const { code, out } = runValidator((d) => {
    d.sla.scope.en = d.sla.scope.hi
  })
  assert.equal(code, 0)
  assert.match(out, /hi\/en twin identical at 'sla\.scope'/)
})

test('a verification date in the future is an error, not a hope', () => {
  const { code, out } = runValidator((d) => {
    d.sla.verified_on = '2099-01-01'
  })
  assert.equal(code, 1)
  assert.match(out, /verified_on is in the future/)
})

test('one alias cannot belong to two services - routing would be ambiguous', () => {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'sevamarg-clash-'))
  TMP_ROOTS.push(dir)
  const doc = readYaml(GOOD)
  const twin = structuredClone(doc)
  twin.service_id = 'fixture-second-service'
  twin.lang.hi.aliases = ['income certificate']
  twin.lang.en.aliases = ['ay praman patra']
  writeFileSync(path.join(dir, 'a.yaml'), yaml.dump(doc), 'utf8')
  writeFileSync(path.join(dir, 'b.yaml'), yaml.dump(twin), 'utf8')
  const res = spawnSync(process.execPath, [VALIDATE], {
    encoding: 'utf8',
    env: { ...process.env, SEVA_MARG_ASCII: '1', SEVA_CATALOGUE_DATA_DIR: dir, SEVA_CATALOGUE_OVERRIDES_DIR: path.join(dir, 'none') },
  })
  assert.equal(res.status, 1)
  assert.match(`${res.stdout}${res.stderr}`, /alias "income certificate" is claimed by both/)
})

// ---------------------------------------------------------------- unit level
test('only official government hosts count as citations', async () => {
  const { officialHostOk } = await import('./lib.mjs')
  assert.ok(officialHostOk('https://upcsc.in/services'))
  assert.ok(officialHostOk('https://sdsup.gov.in/x'))
  assert.ok(officialHostOk('https://lucknow.up.gov.in/y'))
  assert.ok(officialHostOk('https://e-district.gov.in/z'))
  assert.ok(!officialHostOk('https://example.invalid/services'))
  assert.ok(!officialHostOk('https://newsblog.co.in/income-certificate'))
  assert.ok(!officialHostOk('https://gov.in.evil.com/phishing'))
  assert.ok(!officialHostOk('VERIFY_OFFICIAL'))
  assert.ok(!officialHostOk(''))
})

test('the walk finds the sentinel wherever it hides, including inside arrays', async () => {
  const { findUnverifiedStrings } = await import('./lib.mjs')
  const hits = findUnverifiedStrings({
    fees: [{ item: { hi: 'VERIFY_OFFICIAL', en: 'ok' }, source_url: 'https://upcsc.in', verified_on: '2026-10-01' }],
  })
  assert.deepEqual(hits.map((h) => h.path), ['fees[0].item.hi'])
})

test('step baseline counts steps and physical trips separately', async () => {
  const { stepBaseline } = await import('./lib.mjs')
  const doc = readYaml(GOOD)
  const base = stepBaseline(doc)
  assert.deepEqual(base.map((b) => `${b.kind}:${b.steps}/${b.visits}`), ['online:2/0', 'csc:1/1'])
  assert.ok(base.every((b) => b.verified))
})
