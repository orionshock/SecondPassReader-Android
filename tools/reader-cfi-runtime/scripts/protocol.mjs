import { readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const workspace = resolve(dirname(fileURLToPath(import.meta.url)), "..");
export const manifest = JSON.parse(await readFile(resolve(workspace, "protocol.json"), "utf8"));
export const kotlinPath = resolve(workspace,
  "../../app/src/main/kotlin/com/secondpasslibrary/reader/reader/readium/cfi/CfiProtocol.kt");
export const typescriptPath = resolve(workspace, "src/protocol.generated.ts");
const symbol = (name) => name.replace(/([a-z])([A-Z])/g, "$1_$2").toUpperCase();
const quote = JSON.stringify;

export function declarations(protocol) {
  const constants = {
    RUNTIME_VERSION: protocol.runtimeVersion,
    GLOBAL: protocol.global,
    ...protocol.limits
  };
  for (const [name, value] of Object.entries(protocol.envelope)) constants[`FIELD_${symbol(name)}`] = value;
  for (const fields of Object.values(protocol.types)) {
    for (const field of Object.keys(fields)) {
      const name = field.replace("?", "");
      constants[`FIELD_${symbol(name)}`] = name;
    }
  }
  for (const code of protocol.errors) constants[`ERROR_${code}`] = code;
  for (const kind of protocol.kinds) constants[`KIND_${symbol(kind)}`] = kind;
  for (const [name, method] of Object.entries(protocol.methods)) {
    constants[`METHOD_${symbol(name)}`] = name;
    for (const [arg] of method.arguments) constants[`ARG_${symbol(arg)}`] = arg;
  }
  return Object.fromEntries(Object.entries(constants).sort(([a], [b]) => a < b ? -1 : 1));
}

export function renderKotlin(protocol) {
  const constants = Object.entries(declarations(protocol))
    .map(([key, value]) => `    const val ${key} = ${quote(value)}`).join("\n");
  const methods = Object.entries(protocol.methods).sort().map(([name, method]) => {
    const values = [`CfiProtocol.METHOD_${symbol(name)}`,
      ...method.arguments.map(([arg]) => `CfiProtocol.ARG_${symbol(arg)}`)];
    return `    ${symbol(name)}(\n${values.map((value) => `        ${value}`).join(",\n")}\n    )`;
  }).join(",\n");
  return `// GENERATED from tools/reader-cfi-runtime/protocol.json; do not edit.
package com.secondpasslibrary.reader.reader.readium.cfi

internal object CfiProtocol {
${constants}
}

internal enum class CfiRuntimeMethod(val wireName: String, vararg val argumentNames: String) {
${methods}
}
`;
}

export function renderTypescript(protocol) {
  const constants = Object.entries(declarations(protocol))
    .map(([key, value]) => `  ${key}: ${quote(value)}`).join(",\n");
  const types = Object.entries(protocol.types).sort().map(([name, fields]) =>
    `export interface ${name} {\n${Object.entries(fields).sort().map(([field, type]) =>
      `  readonly ${field}: ${type};`).join("\n")}\n}`).join("\n");
  const methods = Object.entries(protocol.methods).sort().map(([name, method]) => {
    const args = method.arguments.map(([arg, type]) => `${arg}: ${type}`).join(", ");
    const result = name === "runtimeVersion" ? method.result : `RuntimeResult<${method.result}>`;
    return `  [Protocol.METHOD_${symbol(name)}](${args}): ${result};`;
  }).join("\n");
  const { ok, value, error, code } = protocol.envelope;
  return `// GENERATED from protocol.json; do not edit.
export const Protocol = {
${constants}
} as const;
export type TargetKind = ${protocol.kinds.map(quote).join(" | ")};
export type RuntimeErrorCode = ${protocol.errors.map(quote).join(" | ")};
export type RuntimeResult<T> =
  | { readonly ${ok}: true; readonly ${value}: T }
  | { readonly ${ok}: false; readonly ${error}: { readonly ${code}: RuntimeErrorCode } };
${types}
export interface RuntimeFacade {
${methods}
}
`;
}

export async function checkProtocol() {
  for (const [path, expected] of [[kotlinPath, renderKotlin(manifest)], [typescriptPath, renderTypescript(manifest)]]) {
    if ((await readFile(path, "utf8")).replaceAll("\r\n", "\n") !== expected) {
      throw new Error(`Stale CFI protocol declarations: ${path}. Run npm run build.`);
    }
  }
  const entry = await readFile(resolve(workspace, "src/runtime.ts"), "utf8");
  for (const [name, method] of Object.entries(manifest.methods)) {
    const pattern = new RegExp(`\\[P\\.METHOD_${symbol(name)}]:\\s*\\(([^)]*)\\)\\s*=>`);
    const actual = pattern.exec(entry)?.[1].split(",").map((arg) => arg.trim()).filter(Boolean);
    if (JSON.stringify(actual) !== JSON.stringify(method.arguments.map(([arg]) => arg))) {
      throw new Error(`CFI entrypoint ${name} does not match the protocol argument order.`);
    }
  }
}

export function typecheck() {
  const result = spawnSync(process.execPath,
    [resolve(workspace, "node_modules/typescript/bin/tsc"), "--noEmit"],
    { cwd: workspace, stdio: "inherit", shell: false });
  if (result.status !== 0) throw new Error("CFI runtime TypeScript contract check failed.");
}

export async function generateProtocol() {
  await writeFile(kotlinPath, renderKotlin(manifest));
  await writeFile(typescriptPath, renderTypescript(manifest));
}
