import assert from 'node:assert/strict';
import { test } from 'node:test';
import { releasePolicy } from './release-policy.mjs';

test('stable tags publish a normal current release', () => {
  assert.deepEqual(releasePolicy('v1.2.3', '1.2.3'), { prerelease: false, makeLatest: 'true' });
});

test('prerelease tags do not replace the stable current release', () => {
  for (const version of ['0.1.0-alpha.1', '0.1.0-alpha.1.2', '1.2.3-rc.2', '1.2.3-beta.1', '1.2.3-preview.4']) {
    assert.deepEqual(releasePolicy(`v${version}`, version), { prerelease: true, makeLatest: 'false' });
  }
});

test('invalid tags and a tag/APK version mismatch cannot publish', () => {
  for (const tag of ['1.2.3', 'v1.2', 'v01.2.3', 'v1.2.3-rc.01', 'v1.2.3-', 'v1.2.3/other']) {
    assert.throws(() => releasePolicy(tag, tag.slice(1)), /Release tag must/);
  }
  assert.throws(() => releasePolicy('v1.2.3', '1.2.4'), /does not match APK/);
});
