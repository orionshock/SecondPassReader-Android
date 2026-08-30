import { readFile } from "node:fs/promises";
import { spawnSync } from "node:child_process";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { bundledRuntime, outputPath } from "./build-support.mjs";

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

const expected = await bundledRuntime();
const committed = await readFile(outputPath);
if (!committed.equals(expected)) {
  console.error("Committed Reader CFI runtime is stale. Run npm run build.");
  process.exit(1);
}
console.log("Reader CFI runtime typecheck and generated asset comparison passed.");
