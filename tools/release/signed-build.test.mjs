import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, delimiter, dirname, join, resolve } from 'node:path';
import { test } from 'node:test';

const helper = readFileSync(new URL('./build-signed-apk.sh', import.meta.url));
const bash = process.platform === 'win32'
  ? join(process.env.ProgramFiles || 'C:/Program Files', 'Git/bin/bash.exe')
  : 'bash';

function runSignedBuild(failGate) {
  const root = mkdtempSync(join(tmpdir(), 'signed-build-test-'));
  try {
    const bin = join(root, 'bin');
    mkdirSync(bin);
    writeFileSync(join(root, 'signed-build.sh'), helper);
    writeFileSync(join(root, 'gradlew'), [
      '#!/usr/bin/env bash',
      'printf "gradle %s\\n" "$*" >> events.txt',
      'if [[ "$*" == check && "$FAIL_GATE" == 1 ]]; then exit 1; fi',
      '',
    ].join('\n'));
    writeFileSync(join(bin, 'node'), [
      '#!/usr/bin/env bash',
      'if [[ "$1" == --test ]]; then echo release-tests >> events.txt;',
      'else echo artifact-verification >> events.txt; fi',
      '',
    ].join('\n'), { mode: 0o755 });
    const result = spawnSync(bash, ['signed-build.sh'], {
      cwd: root,
      encoding: 'utf8',
      env: {
        ...process.env,
        PATH: `${bin}${delimiter}${process.env.PATH}`,
        FAIL_GATE: failGate ? '1' : '0',
        ANDROID_RELEASE_KEYSTORE_B64: Buffer.from('test-fixture-not-a-keystore').toString('base64'),
      },
    });
    if (result.error) throw result.error;
    return {
      status: result.status,
      output: result.stdout,
      events: readFileSync(join(root, 'events.txt'), 'utf8').trim().split('\n'),
    };
  } finally {
    assert.equal(dirname(resolve(root)), resolve(tmpdir()));
    assert.ok(basename(root).startsWith('signed-build-test-'));
    rmSync(root, { recursive: true, force: true });
  }
}

test('signed helper completes the gate before assembly and artifact verification', () => {
  const result = runSignedBuild(false);
  assert.equal(result.status, 0);
  assert.deepEqual(result.events, ['release-tests', 'gradle check', 'gradle assembleRelease', 'artifact-verification']);
  assert.ok(result.output.indexOf('Phase A: repository gate passed') < result.output.indexOf('Phase B: release assembly/signing starts'));
});

test('a failing repository gate prevents assembly and artifact verification', () => {
  const result = runSignedBuild(true);
  assert.notEqual(result.status, 0);
  assert.deepEqual(result.events, ['release-tests', 'gradle check']);
  assert.ok(!result.output.includes('Phase B:'));
});
