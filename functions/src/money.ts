export function parseTryAmountMinor(input: string): number | null {
  const raw = input
    .replace(/₺/g, "")
    .replace(/TL/gi, "")
    .trim()
    .replace(/\s+/g, "");

  if (!/^(?:\d+|\d{1,3}(?:\.\d{3})+)(?:,\d{1,2})?$/.test(raw)) return null;

  const normalized = raw.replace(/\./g, "").replace(",", ".");
  const [whole, fraction = ""] = normalized.split(".");
  if (!/^\d+$/.test(whole) || !/^\d{0,2}$/.test(fraction)) return null;

  const minorBig = BigInt(whole) * 100n + BigInt((fraction + "00").slice(0, 2));
  if (minorBig <= 0n || minorBig > BigInt(Number.MAX_SAFE_INTEGER)) return null;
  return Number(minorBig);
}
