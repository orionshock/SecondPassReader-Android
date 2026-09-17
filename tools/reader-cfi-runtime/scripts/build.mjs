import { mkdir, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import { generateProtocol, checkProtocol, typecheck } from "./protocol.mjs";
import {
  bundledRuntime,
  developmentOutputPath,
  releaseOutputPath
} from "./build-support.mjs";

await generateProtocol();
await checkProtocol();
typecheck();

for (const [outputPath, minify] of [
  [developmentOutputPath, false],
  [releaseOutputPath, true]
]) {
  await mkdir(dirname(outputPath), { recursive: true });
  await writeFile(outputPath, await bundledRuntime({ minify }));
  console.log(`Generated ${outputPath}`);
}
