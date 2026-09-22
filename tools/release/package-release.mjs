import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const root = resolve(import.meta.dirname, '../..');
const apk = join(root, 'app/build/outputs/apk/release/app-release.apk');
const output = join(root, 'build/release-artifacts');
const expectedSigner = '56E8DEB6C1C9F23E0071F9917C21D0C88E28ECD0AACF6CFF8A97B5C37DB158A8';
const expectedPackage = 'com.secondpasslibrary.reader';
const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;

function fail(message) {
  throw new Error(message);
}

function sdkTool(name) {
  if (!sdk) fail('ANDROID_HOME or ANDROID_SDK_ROOT is required to verify the release APK.');
  const suffix = process.platform === 'win32' ? (name === 'apksigner' ? '.bat' : '.exe') : '';
  const path = join(sdk, 'build-tools', '37.0.0', name + suffix);
  if (!existsSync(path)) fail(`Android SDK build-tools 37.0.0 is required: ${path}`);
  return path;
}

function toolOutput(name, args) {
  let executable = sdkTool(name);
  if (process.platform === 'win32' && name === 'apksigner') {
    executable = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java.exe') : 'java.exe';
    args = ['-jar', join(sdk, 'build-tools', '37.0.0', 'lib', 'apksigner.jar'), ...args];
  }
  return execFileSync(executable, args, { encoding: 'utf8', maxBuffer: 8 * 1024 * 1024 });
}

if (!existsSync(apk)) fail(`Release APK is missing: ${apk}`);

const verification = toolOutput('apksigner', ['verify', '--verbose', '--print-certs', apk]);
const signerCount = verification.match(/^Number of signers:\s*(\d+)\s*$/m);
const signer = verification.match(/^(?:V\d+ Signer|Signer #1): certificate SHA-256 digest:\s*([0-9a-fA-F:]+)\s*$/m);
if (signerCount?.[1] !== '1' || !signer) fail('Expected exactly one APK signing certificate.');
const fingerprint = signer[1].replaceAll(':', '').toUpperCase();
if (fingerprint !== expectedSigner) fail(`Release certificate fingerprint mismatch: ${fingerprint}`);

const badging = toolOutput('aapt2', ['dump', 'badging', apk]);
const identity = badging.match(/^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'/m);
if (!identity) fail('Could not read package identity from APK.');
const [, packageId, versionCode, versionName] = identity;
const minSdk = badging.match(/^minSdkVersion:'(\d+)'\s*$/m)?.[1];
if (packageId !== expectedPackage) fail(`Unexpected application ID: ${packageId}`);
if (!/^\d+$/.test(versionCode) || !minSdk || !/^[0-9A-Za-z][0-9A-Za-z.+-]*$/.test(versionName)) {
  fail('APK version is invalid for release artifact naming.');
}

mkdirSync(output, { recursive: true });
for (const name of readdirSync(output)) {
  const path = join(output, name);
  if (!statSync(path).isFile()) fail(`Unexpected directory in release artifact staging: ${path}`);
  rmSync(path);
}
const artifactName = `SecondPassReader-${versionName}.apk`;
const artifact = join(output, artifactName);
copyFileSync(apk, artifact);
const checksum = createHash('sha256').update(readFileSync(artifact)).digest('hex');
writeFileSync(`${artifact}.sha256`, `${checksum}  ${artifactName}\n`);

console.log(`APK verified: ${packageId} ${versionName} (${versionCode})`);
console.log(`Release signer SHA-256: ${fingerprint}`);
console.log(`APK size: ${statSync(artifact).size} bytes`);
console.log(`APK SHA-256: ${checksum}`);
console.log(`Artifacts: ${artifactName}, ${artifactName}.sha256`);

if (process.env.GITHUB_OUTPUT) {
  writeFileSync(
    process.env.GITHUB_OUTPUT,
    `version_name=${versionName}\nversion_code=${versionCode}\nmin_sdk=${minSdk}\napk_sha256=${checksum}\n`,
    { flag: 'a' },
  );
}
