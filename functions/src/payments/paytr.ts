import { createHmac, timingSafeEqual } from "node:crypto";

export function createPaytrCallbackHash(
  merchantOid: string,
  merchantSalt: string,
  status: string,
  totalAmountMinor: string
): string {
  return createHmac("sha256", merchantSalt)
    .update(merchantOid + merchantSalt + status + totalAmountMinor)
    .digest("base64");
}

export function verifyPaytrCallback(
  merchantOid: string,
  merchantSalt: string,
  status: string,
  totalAmountMinor: string,
  receivedHash: string
): boolean {
  const expected = createPaytrCallbackHash(
    merchantOid,
    merchantSalt,
    status,
    totalAmountMinor
  );
  const expectedBuffer = Buffer.from(expected);
  const receivedBuffer = Buffer.from(receivedHash);
  return expectedBuffer.length === receivedBuffer.length
    && timingSafeEqual(expectedBuffer, receivedBuffer);
}
