import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const source = readFileSync(new URL("../src/index.ts", import.meta.url), "utf8");
const exportedNames = [...source.matchAll(/export const ([A-Za-z_$][\w$]*)\s*=/g)].map((match) => match[1]);
const duplicates = exportedNames.filter((name, index) => exportedNames.indexOf(name) !== index);

assert.deepEqual(duplicates, [], "Duplicate exported Cloud Function names are not allowed.");
assert.ok(exportedNames.length > 0, "No Cloud Functions were found.");

console.log(`Checked ${exportedNames.length} exported Cloud Functions; names are unique.`);
