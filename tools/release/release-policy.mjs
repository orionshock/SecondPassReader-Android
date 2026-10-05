export function releasePolicy(tag, versionName) {
  const match = /^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$/.exec(tag);
  if (!match || match[4]?.split('.').some((part) => /^\d+$/.test(part) && part.length > 1 && part.startsWith('0'))) {
    throw new Error('Release tag must be vX.Y.Z or vX.Y.Z-<prerelease identifiers>.');
  }
  if (tag.slice(1) !== versionName) {
    throw new Error(`Tag ${tag} does not match APK versionName ${versionName}.`);
  }
  const prerelease = Boolean(match[4]);
  return { prerelease, makeLatest: prerelease ? 'false' : 'true' };
}
