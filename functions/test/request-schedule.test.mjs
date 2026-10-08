import assert from "node:assert/strict";
import { test } from "node:test";
import { validateRequestSchedule } from "../lib/requestSchedule.js";

test("canonical emergency payload and leap day are accepted", () => {
  validateRequestSchedule("Acil tesisatçı", "2026-10-08", "23:59");
  validateRequestSchedule("Planlı temizlik", "2028-02-29", "00:00");
});

test("blank or oversized request titles are rejected", () => {
  for (const title of ["", "   ", "\t\n", "x".repeat(121)]) {
    assert.throws(() => validateRequestSchedule(title, "2026-10-08", "09:00"), /başlığı/);
  }
});

test("invalid and missing calendar dates are rejected", () => {
  for (const date of ["", "2026-02-29", "2026-02-30", "2026-13-01", "2026-04-31", "2026-2-01", "Hemen / Bugün"]) {
    assert.throws(() => validateRequestSchedule("İlan", date, "09:00"), /tarihi/);
  }
});

test("invalid and missing clock times are rejected", () => {
  for (const time of ["", "24:00", "12:60", "9:00", "En geç 1 saat içinde"]) {
    assert.throws(() => validateRequestSchedule("İlan", "2026-10-08", time), /saati/);
  }
});
