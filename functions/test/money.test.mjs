import assert from "node:assert/strict";
import { parseTryAmountMinor } from "../lib/money.js";

const cases = [
  ["12.500 ₺", 1250000],
  ["1.250,50 TL", 125050],
  ["1250,5 TL", 125050],
  ["0 TL", null],
  ["-100 TL", null],
  ["3.000 - 6.000 TL", null],
  ["1.234.567,89 ₺", 123456789],
  ["9.999,999 TL", null],
  ["1e9 TL", null],
  ["999999999999999999999999 TL", null],
];

for (const [input, expected] of cases) {
  assert.equal(parseTryAmountMinor(input), expected, input);
}

console.log("Money parser tests passed.");
