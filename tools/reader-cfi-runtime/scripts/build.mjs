import { mkdir, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import {
  bundledRuntime,
  developmentOutputPath,
  releaseOutputPath
} from "./build-support.mjs";

for (const [outputPath, minify] of [
  [developmentOutputPath, false],
  [releaseOutputPath, true]
]) {
  await mkdir(dirname(outputPath), { recursive: true });
  await writeFile(outputPath, await bundledRuntime({ minify }));
  console.log(`Generated ${outputPath}`);
}
