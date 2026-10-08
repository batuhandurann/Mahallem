export function parseTryAmountMinor(input: string): number | null {
  // Accept one currency marker at either edge, never embedded in an amount.
  // Global replacement would turn "1TL2" into a valid 12 TL payment.
  const compact = input.trim().replace(/\s+/g, "");
  const raw = compact.startsWith("₺")
    ? compact.slice(1)
    : compact.replace(/(?:TL|₺)$/i, "");

  if (!/^(?:\d+|\d{1,3}(?:\.\d{3})+)(?:,\d{1,2})?$/.test(raw)) return null;

  const normalized = raw.replace(/\./g, "").replace(",", ".");
  const [whole, fraction = ""] = normalized.split(".");
  if (!/^\d+$/.test(whole) || !/^\d{0,2}$/.test(fraction)) return null;

  const minorBig = BigInt(whole) * 100n + BigInt((fraction + "00").slice(0, 2));
  if (minorBig <= 0n || minorBig > BigInt(Number.MAX_SAFE_INTEGER)) return null;
  return Number(minorBig);
}
