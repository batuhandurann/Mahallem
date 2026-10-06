import { createHmac, timingSafeEqual } from "node:crypto";

export function createPaytrIframeToken(
  merchantId: string,
  userIp: string,
  merchantOid: string,
  email: string,
  paymentAmountMinor: string,
  userBasketBase64: string,
  noInstallment: string,
  maxInstallment: string,
  currency: string,
  testMode: string,
  merchantSalt: string,
  merchantKey: string
): string {
  const hashString =
    merchantId
    + userIp
    + merchantOid
    + email
    + paymentAmountMinor
    + userBasketBase64
    + noInstallment
    + maxInstallment
    + currency
    + testMode
    + merchantSalt;

  return createHmac("sha256", merchantKey)
    .update(hashString)
    .digest("base64");
}

export function createPaytrCallbackHash(
  merchantKey: string,
  merchantOid: string,
  merchantSalt: string,
  status: string,
  totalAmountMinor: string
): string {
  return createHmac("sha256", merchantKey)
    .update(merchantOid + merchantSalt + status + totalAmountMinor)
    .digest("base64");
}

export function verifyPaytrCallback(
  merchantKey: string,
  merchantOid: string,
  merchantSalt: string,
  status: string,
  totalAmountMinor: string,
  receivedHash: string
): boolean {
  const expected = createPaytrCallbackHash(
    merchantKey,
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
