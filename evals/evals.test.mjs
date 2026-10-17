#!/usr/bin/env node
// evals/evals.test.mjs
// The benchmark is the evidence. If eval-set.json can be built from a broken question file, every
// accuracy number derived from it is worthless - so this file checks the checks.
import { test, after } from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdtempSync, writeFileSync, readFileSync, rmSync, cpSync, mkdirSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const BUILD = path.join(HERE, 'build.mjs')
const TMP_ROOTS = []
after(() => {
  for (const dir of TMP_ROOTS) rmSync(dir, { recursive: true, force: true })
})

/**
 * Build the eval set in a sandbox that has copies of the real question files, so a mutation can
 * make one of them wrong without ever touching the repo.
 */
function runBuild(mutate) {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'sevamarg-eval-'))
  TMP_ROOTS.push(dir)
  for (const f of ['questions_hi.sample.yaml', 'questions_en.sample.yaml', 'questions_oos.sample.yaml']) {
    cpSync(path.join(HERE, f), path.join(dir, f))
  }
  mkdirSync(path.join(dir, 'schema'), { recursive: true })
  cpSync(path.join(HERE, 'schema', 'eval.schema.json'), path.join(dir, 'schema', 'eval.schema.json'))
  if (mutate) mutate(dir)
  const out = path.join(dir, 'dist')
  const res = spawnSync(process.execPath, [BUILD], {
    encoding: 'utf8',
    env: {
      ...process.env,
      SEVA_MARG_ASCII: '1',
      SEVA_EVALS_SRC_DIR: dir,
      SEVA_EVALS_OUT_DIR: out,
    },
  })
  return { code: res.status, out: `${res.stdout ?? ''}${res.stderr ?? ''}`, file: path.join(out, 'eval-set.json') }
}

function write(p, s) {
  writeFileSync(p, s, 'utf8')
}

test('the seeded sets compile into a bench-readable file', () => {
  const { code, out, file } = runBuild(null)
  assert.equal(code, 0, out)
  const bundle = JSON.parse(readFileSync(file, 'utf8'))
  assert.equal(bundle.total, bundle.items.length)
  assert.equal(bundle.counts.hi, 12)
  assert.equal(bundle.counts.en, 10)
  assert.equal(bundle.refusal_cases, 10)
  assert.equal(bundle.twins_paired, 10)
  assert.ok(bundle.provisional, 'sample sets are provisional until content days happen')
  // :bench reads `lang` and `expect_fields`; if either is missing the runner silently scores 0
  for (const q of bundle.items) {
    assert.ok(['hi', 'en'].includes(q.lang), `${q.id} has a language`)
    if (!q.id.startsWith('oos-')) assert.ok(Array.isArray(q.expect_fields) && q.expect_fields.length > 0)
  }
})

test('an out-of-scope question that names a service is rejected', () => {
  const { code, out } = runBuild((dir) => {
    write(path.join(dir, 'questions_oos.sample.yaml'), '- id: oos-001\n  ask: "kuch sawal"\n  service: income-certificate\n  why: test\n')
  })
  assert.equal(code, 1)
  assert.match(out, /may not name a service/)
})

test('an answerable question with no answer key is rejected', () => {
  const { code, out } = runBuild((dir) => {
    write(path.join(dir, 'questions_hi.sample.yaml'), '- id: hi-001\n  ask: "income certificate fee"\n  service: income-certificate\n')
  })
  assert.equal(code, 1)
  assert.match(out, /needs expect_fields/)
})

test('a bad field name in the answer key is rejected by the schema', () => {
  const { code, out } = runBuild((dir) => {
    write(path.join(dir, 'questions_hi.sample.yaml'), '- id: hi-001\n  ask: "income certificate fee"\n  service: income-certificate\n  expect_fields: [payment]\n')
  })
  assert.equal(code, 1)
  assert.match(out, /must be equal to one of the allowed values/)
})

test('an English twin that expects different facts is a hard error', () => {
  const { code, out } = runBuild((dir) => {
    write(path.join(dir, 'questions_en.sample.yaml'), '- id: en-001\n  ask: "what documents for income certificate"\n  service: income-certificate\n  expect_fields: [fees]\n  twin_of: hi-001\n')
  })
  assert.equal(code, 1)
  assert.match(out, /the same question must return the same facts in both languages/)
})

test('a twin pointing at a question that does not exist is caught', () => {
  const { code, out } = runBuild((dir) => {
    write(path.join(dir, 'questions_en.sample.yaml'), '- id: en-001\n  ask: "documents for income certificate"\n  service: income-certificate\n  expect_fields: [documents]\n  twin_of: hi-900\n')
  })
  assert.equal(code, 1)
  assert.match(out, /does not exist in the Hindi set/)
})

test('duplicate question ids across files are caught', () => {
  const { code, out } = runBuild((dir) => {
    write(path.join(dir, 'questions_en.sample.yaml'), '- id: hi-001\n  ask: "documents"\n  service: income-certificate\n  expect_fields: [documents]\n')
  })
  assert.equal(code, 1)
  assert.match(out, /duplicate eval id 'hi-001'/)
})
