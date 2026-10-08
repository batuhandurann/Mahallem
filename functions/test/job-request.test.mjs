import assert from "node:assert/strict";
import test from "node:test";

process.env.GCLOUD_PROJECT = "mahallem-unit-test";
const { saveJobRequest } = await import("../lib/index.js");
const valid = {
  requestId: "123", title: "Su kaçağı", sector: "HOME_REPAIR",
  categoryId: "tesisatci", district: "Karşıyaka", urgencyMode: "EMERGENCY",
  eventOrJobDate: "2026-10-08", eventTime: "15:30", budgetEstimate: "",
};

for (const [field, values] of Object.entries({
  title: ["", "   ", "\t\n", "x".repeat(121)],
  eventOrJobDate: ["", "Hemen / Bugün", "2026-02-29", "2026-02-31", "2026-13-01", "2026-00-10", "2026-10-00", "2026-1-01"],
  eventTime: ["", "En geç 1 saat içinde", "24:00", "25:70", "12:60", "-1:00", "9:00", "12:00:00"],
})) {
  for (const value of values) {
    test(`saveJobRequest rejects ${field}=${JSON.stringify(value)} before any database access`, async () => {
      await assert.rejects(
        saveJobRequest.run({ auth: { uid: "account-a", token: {} }, data: { ...valid, [field]: value } }),
        { code: "invalid-argument" },
      );
    });
  }
}

test("saveJobRequest requires authentication", async () => {
  await assert.rejects(saveJobRequest.run({ data: valid }), { code: "unauthenticated" });
});
