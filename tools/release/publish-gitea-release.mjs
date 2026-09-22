import { createHash } from 'node:crypto';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { basename, join, resolve } from 'node:path';

function fail(message) {
  throw new Error(message);
}

const event = process.env.GITHUB_EVENT_NAME;
const ref = process.env.GITHUB_REF || '';
const tag = ref.startsWith('refs/tags/') ? ref.slice('refs/tags/'.length) : '';
const versionName = process.env.RELEASE_VERSION_NAME || '';
const versionCode = process.env.RELEASE_VERSION_CODE || '';
const minSdk = process.env.RELEASE_MIN_SDK || '';
if (event !== 'push' || !/^v[0-9A-Za-z][0-9A-Za-z.+-]*$/.test(tag)) {
  fail('Release publishing requires a pushed v* tag.');
}
if (tag.slice(1) !== versionName) fail(`Tag ${tag} does not match APK versionName ${versionName}.`);
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
  'Alpha software. An SPL server is required.',
  `Minimum Android API level: ${minSdk}.`,
  `Install: download ${apkName} and install it manually.`,
  'Updates: future APKs signed with the same release certificate can install over earlier release APKs.',
  `SHA-256 checksum: ${checksumName}.`,
].join('\n');

console.log(`Tag ${tag} matches APK versionName ${versionName}; versionCode ${versionCode}; minSdk ${minSdk}.`);
console.log(`APK SHA-256: ${checksum}`);
if (process.argv.includes('--check')) process.exit(0);

const token = process.env.GITEA_TOKEN;
const apiUrl = process.env.GITHUB_API_URL;
const repository = process.env.GITHUB_REPOSITORY;
if (!token || !apiUrl || !repository || !/^[^/]+\/[^/]+$/.test(repository)) {
  fail('Gitea job token or repository API context is missing.');
}
const [owner, repo] = repository.split('/');
const apiBase = `${apiUrl.replace(/\/$/, '')}/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}`;

async function request(method, path, body, acceptedStatus) {
  const headers = { Authorization: `token ${token}` };
  if (body && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
  const response = await fetch(`${apiBase}${path}`, {
    method,
    headers,
    body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
  });
  if (acceptedStatus.includes(response.status)) {
    return response.status === 404 ? null : response.json();
  }
  fail(`Gitea ${method} ${path} returned HTTP ${response.status}.`);
}

const existing = await request('GET', `/releases/tags/${encodeURIComponent(tag)}`, undefined, [200, 404]);
if (existing) fail(`A release already exists for ${tag}; refusing to replace it.`);

const release = await request('POST', '/releases', {
  tag_name: tag,
  name: title,
  body: notes,
  draft: true,
  prerelease: true,
}, [201]);
if (!Number.isInteger(release.id) || !release.draft || !release.prerelease) {
  fail('Gitea did not confirm creation of a draft prerelease.');
}

for (const path of [apk, checksumFile]) {
  const name = basename(path);
  const form = new FormData();
  form.append('attachment', new Blob([readFileSync(path)]), name);
  const uploaded = await request(
    'POST',
    `/releases/${release.id}/assets?name=${encodeURIComponent(name)}`,
    form,
    [201],
  );
  if (uploaded.name !== name || uploaded.size !== statSync(path).size) {
    fail(`Gitea did not confirm release attachment ${name}.`);
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
  prerelease: true,
}, [200]);
if (published.draft || !published.prerelease || published.tag_name !== tag) {
  fail('Gitea did not confirm a published prerelease for the expected tag.');
}
console.log(`Published prerelease: ${published.html_url}`);
if (process.env.GITHUB_STEP_SUMMARY) {
  const { appendFileSync } = await import('node:fs');
  appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${title}\n\nAPK SHA-256: \`${checksum}\`\n\n${published.html_url}\n`);
}
