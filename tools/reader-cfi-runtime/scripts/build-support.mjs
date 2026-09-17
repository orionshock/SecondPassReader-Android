import { createHash } from "node:crypto";
import { readFile, readdir } from "node:fs/promises";
import { dirname, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { build } from "esbuild";

const workspace = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const repository = resolve(workspace, "../..");
export const developmentOutputPath = resolve(
  repository,
  "app/src/main/assets/reader/cfi/secondpass-epub-cfi-runtime.js"
);
export const releaseOutputPath = resolve(
  repository,
  "app/src/release/assets/reader/cfi/secondpass-epub-cfi-runtime.js"
);

async function sourceFiles() {
  const sourceRoot = resolve(workspace, "src");
  const entries = await readdir(sourceRoot, { recursive: true, withFileTypes: true });
  const files = entries
    .filter((entry) => entry.isFile() && entry.name.endsWith(".ts"))
    .map((entry) => resolve(entry.parentPath, entry.name));
  return [
    ...files,
    resolve(workspace, "package.json"),
    resolve(workspace, "package-lock.json"),
    resolve(workspace, "tsconfig.json"),
    fileURLToPath(import.meta.url)
  ].sort((left, right) => relative(repository, left).localeCompare(relative(repository, right)));
}

export async function sourceDigest() {
  const hash = createHash("sha256");
  for (const path of await sourceFiles()) {
    hash.update(relative(repository, path).replaceAll("\\", "/"));
    hash.update("\0");
    hash.update((await readFile(path, "utf8")).replaceAll("\r\n", "\n"));
    hash.update("\0");
  }
  return hash.digest("hex").toUpperCase();
}

export async function bundledRuntime({ minify = false } = {}) {
  const digest = await sourceDigest();
  const result = await build({
    entryPoints: [resolve(workspace, "src/runtime.ts")],
    bundle: true,
    format: "iife",
    platform: "browser",
    target: ["chrome90"],
    write: false,
    legalComments: "none",
    charset: "utf8",
    minify,
    banner: {
      js:
        "// GENERATED from tools/reader-cfi-runtime; do not edit.\n" +
        `// Source-SHA256: ${digest}\n` +
        "// Rebuild: cd tools/reader-cfi-runtime && npm run build"
    }
  });
  const output = result.outputFiles[0];
  if (!output) {
    throw new Error("esbuild produced no Reader CFI runtime output.");
  }
  return output.contents;
}
