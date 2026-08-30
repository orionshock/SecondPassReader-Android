import { mkdir, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import { bundledRuntime, outputPath } from "./build-support.mjs";

await mkdir(dirname(outputPath), { recursive: true });
await writeFile(outputPath, await bundledRuntime());
console.log(`Generated ${outputPath}`);
