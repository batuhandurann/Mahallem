import assert from "node:assert/strict";
import { parseTryAmountMinor } from "../lib/money.js";

const cases = [
  ["12.500 ₺", 1250000],
  ["1.250,50 TL", 125050],
  ["1250,5 TL", 125050],
  [" 2 800 ₺ ", 280000],
  ["₺ 125,5", 12550],
  ["1TL2", null],
  ["1₺2", null],
  ["₺1TL", null],
  ["1TLTL", null],
  ["TL100", null],
  ["0 TL", null],
  ["-100 TL", null],
  ["3.000 - 6.000 TL", null],
  ["1.234.567,89 ₺", 123456789],
  ["9.999,999 TL", null],
  ["1e9 TL", null],
  ["90071992547409,91", Number.MAX_SAFE_INTEGER],
  ["90071992547409,92", null],
  ["999999999999999999999999 TL", null],
];

for (const [input, expected] of cases) {
  assert.equal(parseTryAmountMinor(input), expected, input);
}

console.log("Money parser tests passed.");
