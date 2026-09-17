import { readFile } from "node:fs/promises";
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
  runInNewContext(committed.toString("utf8"), { window });
  const runtime = window[manifest.global];
  assert.deepEqual(Object.keys(runtime).sort(), Object.keys(manifest.methods).sort());
  assert.equal(runtime.runtimeVersion(), manifest.runtimeVersion);
  const failure = runtime.parse("invalid");
  assert.equal(failure[manifest.envelope.ok], false);
  assert.equal(failure[manifest.envelope.error][manifest.envelope.code], "INVALID_CFI");
  if (!committed.equals(expected)) {
    console.error(`Committed Reader CFI runtime is stale: ${outputPath}. Run npm run build.`);
    process.exit(1);
  }
}
console.log("Reader CFI runtime typecheck and generated asset comparison passed.");
