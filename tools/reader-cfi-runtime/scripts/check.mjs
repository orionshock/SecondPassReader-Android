import { readFile } from "node:fs/promises";
import { spawnSync } from "node:child_process";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import {
  bundledRuntime,
  developmentOutputPath,
  releaseOutputPath
} from "./build-support.mjs";

const workspace = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const typecheck = spawnSync(
  process.execPath,
  [resolve(workspace, "node_modules/typescript/bin/tsc"), "--noEmit"],
  {
  cwd: workspace,
  stdio: "inherit",
  shell: false
  }
);
if (typecheck.status !== 0) {
  process.exit(typecheck.status ?? 1);
}

for (const [outputPath, minify] of [
  [developmentOutputPath, false],
  [releaseOutputPath, true]
]) {
  const expected = await bundledRuntime({ minify });
  const committed = await readFile(outputPath);
  if (!committed.equals(expected)) {
    console.error(`Committed Reader CFI runtime is stale: ${outputPath}. Run npm run build.`);
    process.exit(1);
  }
}
console.log("Reader CFI runtime typecheck and generated asset comparison passed.");
