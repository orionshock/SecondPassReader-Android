import { createHash } from 'node:crypto';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { basename, join, resolve } from 'node:path';
import { releasePolicy } from './release-policy.mjs';

function fail(message) {
  throw new Error(message);
}

const event = process.env.GITHUB_EVENT_NAME;
const ref = process.env.GITHUB_REF || '';
const tag = ref.startsWith('refs/tags/') ? ref.slice('refs/tags/'.length) : '';
const versionName = process.env.RELEASE_VERSION_NAME || '';
const versionCode = process.env.RELEASE_VERSION_CODE || '';
const minSdk = process.env.RELEASE_MIN_SDK || '';
if (event !== 'push') fail('Release publishing requires a pushed release tag.');
const { prerelease, makeLatest } = releasePolicy(tag, versionName);
if (!/^\d+$/.test(versionCode) || !/^\d+$/.test(minSdk)) fail('APK versionCode or minSdk is missing.');

const output = resolve(import.meta.dirname, '../../build/release-artifacts');
const apkName = `SecondPassReader-${versionName}.apk`;
const checksumName = `${apkName}.sha256`;
const files = readdirSync(output).sort();
if (files.length !== 2 || !files.includes(apkName) || !files.includes(checksumName)) {
  fail('Release artifact staging must contain exactly the versioned APK and its checksum.');
}
const apk = join(output, apkName);
const checksumFile = join(output, checksumName);
const checksum = createHash('sha256').update(readFileSync(apk)).digest('hex');
if (readFileSync(checksumFile, 'utf8') !== `${checksum}  ${apkName}\n`) {
  fail('APK SHA-256 does not match the staged checksum file.');
}
if (process.env.RELEASE_APK_SHA256 && process.env.RELEASE_APK_SHA256 !== checksum) {
  fail('APK SHA-256 changed after release packaging.');
}

const title = `Second Pass Reader ${versionName}`;
const notes = [
  `${title} (versionCode ${versionCode})`,
  '',
  prerelease ? 'Prerelease software. An SPL server is required.' : 'An SPL server is required.',
  `Minimum Android API level: ${minSdk}.`,
  `Install: download ${apkName} and install it manually.`,
  `SHA-256 checksum: ${checksumName}.`,
].join('\n');

console.log(`Tag ${tag} matches APK versionName ${versionName}; versionCode ${versionCode}; minSdk ${minSdk}.`);
console.log(`APK SHA-256: ${checksum}`);
if (process.argv.includes('--check')) process.exit(0);

const token = process.env.GITHUB_TOKEN;
const apiUrl = process.env.GITHUB_API_URL;
const repository = process.env.GITHUB_REPOSITORY;
if (!token || !apiUrl || !repository || !/^[^/]+\/[^/]+$/.test(repository)) {
  fail('GitHub job token or repository API context is missing.');
}
const [owner, repo] = repository.split('/');
const apiBase = `${apiUrl.replace(/\/$/, '')}/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}`;

async function request(method, path, body, acceptedStatus) {
  const headers = {
    Authorization: `Bearer ${token}`,
    Accept: 'application/vnd.github+json',
    'X-GitHub-Api-Version': '2022-11-28',
    'User-Agent': 'SecondPassReader-Android-release',
  };
  if (body) headers['Content-Type'] = 'application/json';
  const response = await fetch(`${apiBase}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (acceptedStatus.includes(response.status)) {
    return response.status === 404 ? null : response.json();
  }
  fail(`GitHub ${method} ${path} returned HTTP ${response.status}.`);
}

const existing = await request('GET', `/releases/tags/${encodeURIComponent(tag)}`, undefined, [200, 404]);
if (existing) fail(`A release already exists for ${tag}; refusing to replace it.`);

const release = await request('POST', '/releases', {
  tag_name: tag,
  name: title,
  body: notes,
  draft: true,
  prerelease,
  make_latest: makeLatest,
}, [201]);
if (!Number.isInteger(release.id) || !release.draft || release.prerelease !== prerelease) {
  fail('GitHub did not confirm creation of the intended draft release.');
}

// GitHub supplies a separate upload origin. Never send the job token elsewhere.
const uploadUrl = new URL(release.upload_url?.replace(/\{.*$/, '') || '');
if (uploadUrl.origin !== 'https://uploads.github.com' ||
    uploadUrl.pathname !== `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/releases/${release.id}/assets`) {
  fail('GitHub returned an unexpected release upload URL.');
}

for (const path of [apk, checksumFile]) {
  const name = basename(path);
  uploadUrl.searchParams.set('name', name);
  const response = await fetch(uploadUrl, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28',
      'Content-Type': name.endsWith('.apk') ? 'application/vnd.android.package-archive' : 'text/plain',
      'User-Agent': 'SecondPassReader-Android-release',
    },
    body: readFileSync(path),
  });
  if (response.status !== 201) fail(`GitHub upload of ${name} returned HTTP ${response.status}.`);
  const uploaded = await response.json();
  if (uploaded.name !== name || uploaded.size !== statSync(path).size) {
    fail(`GitHub did not confirm release asset ${name}.`);
  }
  console.log(`Attached ${name} (${uploaded.size} bytes).`);
}

const staged = await request('GET', `/releases/${release.id}`, undefined, [200]);
const attachmentNames = (staged.assets || []).map((asset) => asset.name).sort();
if (attachmentNames.join(',') !== [apkName, checksumName].sort().join(',')) {
  fail('Draft release does not contain exactly the APK and checksum attachments.');
}

const published = await request('PATCH', `/releases/${release.id}`, {
  draft: false,
  prerelease,
  make_latest: makeLatest,
}, [200]);
if (published.draft || published.prerelease !== prerelease || published.tag_name !== tag) {
  fail('GitHub did not confirm the expected published release.');
}
console.log(`Published ${prerelease ? 'prerelease' : 'stable release'}: ${published.html_url}`);
if (process.env.GITHUB_STEP_SUMMARY) {
  const { appendFileSync } = await import('node:fs');
  appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${title}\n\nAPK SHA-256: \`${checksum}\`\n\n${published.html_url}\n`);
}
