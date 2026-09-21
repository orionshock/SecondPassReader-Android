import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { runInNewContext } from "node:vm";
import assert from "node:assert/strict";
import { checkProtocol, manifest, typecheck } from "./protocol.mjs";
import {
  bundledRuntime,
  developmentOutputPath,
  releaseOutputPath
} from "./build-support.mjs";

await checkProtocol();
typecheck();

for (const [outputPath, minify] of [
  [developmentOutputPath, false],
  [releaseOutputPath, true]
]) {
  const expected = await bundledRuntime({ minify });
  const committed = await readFile(outputPath);
  const window = {};
  const context = { window };
  runInNewContext(
    await readFile(resolve(dirname(developmentOutputPath), "colibrio-epubcfi-1.1.0.min.js"), "utf8"),
    context
  );
  window.SecondPassColibrio = context.SecondPassColibrio;
  runInNewContext(committed.toString("utf8"), context);
  const runtime = window[manifest.global];
  assert.deepEqual(Object.keys(runtime).sort(), Object.keys(manifest.methods).sort());
  assert.equal(runtime.runtimeVersion(), manifest.runtimeVersion);
  const failure = runtime.parse("invalid");
  assert.equal(failure[manifest.envelope.ok], false);
  assert.equal(failure[manifest.envelope.error][manifest.envelope.code], "INVALID_CFI");
  for (const cfi of [
    "epubcfi(/6/34!/4[x9780451492128_EPUB-15]/2,/310/1:0,/314/1:17)",
    "epubcfi(/6/4[spine-chapter-two]!/4/2[chapter-two-root]/4[cross-spine-target]/1:4)"
  ]) {
    const parsed = runtime.parse(cfi);
    assert.equal(parsed[manifest.envelope.ok], true);
  }
  if (!committed.equals(expected)) {
    console.error(`Committed Reader CFI runtime is stale: ${outputPath}. Run npm run build.`);
    process.exit(1);
  }
}
console.log("Reader CFI runtime typecheck and generated asset comparison passed.");
