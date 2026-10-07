import { createHash, randomUUID } from "node:crypto";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue, Timestamp as FirestoreTimestamp } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { getStorage } from "firebase-admin/storage";
import { onDocumentCreated, onDocumentWritten } from "firebase-functions/v2/firestore";
import { onCall, onRequest, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { logger } from "firebase-functions";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { setGlobalOptions } from "firebase-functions/options";
import { onObjectFinalized } from "firebase-functions/storage";
import {
  createPaytrIframeToken,
  verifyPaytrCallback,
} from "./payments/paytr";
import { parseTryAmountMinor } from "./money";

initializeApp();
setGlobalOptions({ region: "europe-west1", maxInstances: 20, concurrency: 40 });

const db = getFirestore();
const adminAuth = getAuth();
const paytrMerchantId = defineSecret("PAYTR_MERCHANT_ID");
const paytrKey = defineSecret("PAYTR_MERCHANT_KEY");
const paytrSalt = defineSecret("PAYTR_MERCHANT_SALT");
const paytrOkUrl = defineSecret("PAYTR_OK_URL");
const paytrFailUrl = defineSecret("PAYTR_FAIL_URL");
const paytrTestMode = defineSecret("PAYTR_TEST_MODE");
const MAX_QUOTE_AMOUNT_MINOR = 100_000_000;
const ID_PATTERN = /^[A-Za-z0-9_-]{1,80}$/;

function callableData(value: unknown): Record<string, unknown> {
  if (value == null || typeof value !== "object" || Array.isArray(value)) {
    throw new HttpsError("invalid-argument", "İstek gövdesi geçersiz.");
  }
  return value as Record<string, unknown>;
}

function requireString(data: Record<string, unknown>, key: string, maxLength: number, minLength = 1): string {
  const value = data[key];
  if (typeof value !== "string" || value.length < minLength || value.length > maxLength) {
    throw new HttpsError("invalid-argument", `Geçersiz ${key}.`);
  }
  return value;
}

function hashDeviceToken(token: string): string {
  return createHash("sha256").update(token).digest("hex");
}

function isValidIsoDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return Number.isFinite(date.getTime())
    && date.getUTCFullYear() === year
    && date.getUTCMonth() === month - 1
    && date.getUTCDate() === day;
}

function isValidTime(value: string): boolean {
  if (!/^\d{2}:\d{2}$/.test(value)) return false;
  const [hour, minute] = value.split(":").map(Number);
  return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59;
}

function optionalBoolean(data: Record<string, unknown>, key: string): boolean | null {
  const value = data[key];
  if (value == null) return null;
  if (typeof value !== "boolean") {
    throw new HttpsError("invalid-argument", `Geçersiz ${key}.`);
  }
  return value;
}

async function getUsableAuthUser(uid: string) {
  const authUser = await adminAuth.getUser(uid).catch((error: { code?: string }) => {
    if (error.code === "auth/user-not-found") {
      throw new HttpsError("failed-precondition", "Kullanıcı hesabı artık etkin değil.");
    }
    throw error;
  });

  if (authUser.disabled) {
    throw new HttpsError("failed-precondition", "Kullanıcı hesabı devre dışı.");
  }

  if (!authUser.emailVerified && !authUser.phoneNumber) {
    throw new HttpsError("failed-precondition", "Bu işlem için doğrulanmış e-posta veya telefon gerekir.");
  }

  return authUser;
}

async function assertAccountActive(uid: string) {
  const [, userSnap] = await Promise.all([
    getUsableAuthUser(uid),
    db.collection("users").doc(uid).get(),
  ]);

  if (!userSnap.exists) {
    throw new HttpsError("failed-precondition", "Kullanıcı profili henüz hazır değil.");
  }

  if (["REQUESTED", "PURGING"].includes(String(userSnap.data()?.deletionStatus ?? ""))) {
    throw new HttpsError("failed-precondition", "Hesap silme sürecinde olduğu için bu işlem kullanılamaz.");
  }
}

export const createPaymentIntent = onCall(
  {
    region: "europe-west1",
    enforceAppCheck: true,
    secrets: [
      paytrMerchantId,
      paytrKey,
      paytrSalt,
      paytrOkUrl,
      paytrFailUrl,
      paytrTestMode,
    ],
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    }

    await assertAccountActive(request.auth.uid);

    requireRecentAuthentication(request.auth.token.auth_time);

    const data = callableData(request.data);
    const requestId = String(data.requestId ?? "");
    const quoteId = String(data.quoteId ?? "");
    const idempotencyKey = String(data.idempotencyKey ?? "");
    const customerEmail = String(data.customerEmail ?? request.auth.token.email ?? "").trim();
    const currency = String(data.currency ?? "TRY").toUpperCase();

    if (!ID_PATTERN.test(requestId)
      || !ID_PATTERN.test(quoteId)
      || currency !== "TRY"
      || customerEmail.length > 100
      || !/^[A-Za-z0-9._%+-]{1,100}@[A-Za-z0-9.-]{1,190}\.[A-Za-z]{2,63}$/.test(customerEmail)
      || !/^[A-Za-z0-9._:-]{16,128}$/.test(idempotencyKey)) {
      throw new HttpsError("invalid-argument", "Geçersiz ödeme parametreleri.");
    }

    const quoteSnap = await db.collection("quotes").doc(quoteId).get();
    if (!quoteSnap.exists) {
      throw new HttpsError("not-found", "Teklif bulunamadı.");
    }
    const quote = quoteSnap.data()!;
    if (String(quote.requestId ?? "") !== requestId) {
      throw new HttpsError("failed-precondition", "Teklif ve talep eşleşmiyor.");
    }
    if (String(quote.customerId ?? "") !== request.auth.uid) {
      throw new HttpsError("permission-denied", "Bu ödeme yalnızca talep sahibine aittir.");
    }
    if (String(quote.status ?? "") !== "ACCEPTED") {
      throw new HttpsError("failed-precondition", "Ödeme için teklif kabul edilmiş olmalı.");
    }

    const amountMinor = Number(quote.amountMinor ?? 0);
    if (!Number.isSafeInteger(amountMinor) || amountMinor <= 0 || amountMinor > 100_000_000) {
      throw new HttpsError("failed-precondition", "Teklifin güvenilir ödeme tutarı bulunmuyor.");
    }

    const requestRef = db.collection("jobRequests").doc(requestId);
    const privateRequestRef = db.collection("jobRequestPrivate").doc(requestId);
    const quoteRef = db.collection("quotes").doc(quoteId);
    const idemHash = createHash("sha256")
      .update(request.auth.uid + ":" + idempotencyKey)
      .digest("hex");
    const paymentRef = db.collection("payments").doc(idemHash);
    const rateLimitRef = db.collection("rateLimits").doc("payment-intent:" + request.auth.uid);
    const hourlyRateLimitRef = db.collection("rateLimits").doc("payment-intent-hour:" + request.auth.uid);

    const existing = await paymentRef.get();
    if (existing.exists) {
      const existingPayment = existing.data()!;
      if (
        String(existingPayment.requestId ?? "") !== requestId
        || String(existingPayment.quoteId ?? "") !== quoteId
        || Number(existingPayment.amountMinor ?? 0) !== amountMinor
        || String(existingPayment.customerId ?? "") !== request.auth.uid
      ) {
        throw new HttpsError("already-exists", "Idempotency anahtarı farklı bir ödeme işlemi için kullanılmış.");
      }
      if (typeof existingPayment.checkoutUrl === "string" && existingPayment.checkoutUrl.startsWith("https://www.paytr.com/")) {
        return existingPayment;
      }
      if (String(existingPayment.status ?? "") === "FAILED") {
        throw new HttpsError("failed-precondition", "Bu ödeme denemesi başarısız oldu. Yeni bir ödeme denemesi başlatın.");
      }
    }

    const requestDoc = await requestRef.get();
    if (!requestDoc.exists || requestDoc.data()?.ownerId !== request.auth.uid || requestDoc.data()?.status !== "ACCEPTED") {
      throw new HttpsError("failed-precondition", "Talep ödeme için uygun durumda değil.");
    }

    const privateRequestSnap = await privateRequestRef.get();
    const privateRequest = privateRequestSnap.data() ?? {};
    const userIp = String(request.rawRequest?.ip ?? "").trim();
    const merchantId = paytrMerchantId.value().trim();
    const merchantKey = paytrKey.value();
    const merchantSalt = paytrSalt.value();
    const okUrl = paytrOkUrl.value().trim();
    const failUrl = paytrFailUrl.value().trim();
    const testMode = paytrTestMode.value().trim();

    if (!merchantId || !merchantKey || !merchantSalt || !okUrl || !failUrl || !/^[01]$/.test(testMode)) {
      throw new HttpsError("failed-precondition", "PayTR ödeme yapılandırması eksik.");
    }

    try {
      const okUrlParsed = new URL(okUrl);
      const failUrlParsed = new URL(failUrl);
      if (
        testMode === "0"
        && (okUrlParsed.protocol !== "https:" || failUrlParsed.protocol !== "https:")
      ) {
        throw new HttpsError("failed-precondition", "Canlı PayTR dönüş adresleri HTTPS olmalı.");
      }
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      throw new HttpsError("failed-precondition", "PayTR dönüş adresleri geçersiz.");
    }

    if (!userIp || userIp.length > 39) {
      throw new HttpsError("failed-precondition", "Ödeme sağlayıcısı için müşteri IP bilgisi alınamadı.");
    }

    const merchantOid = "MHL" + idemHash.slice(0, 45);
    const noInstallment = "1";
    const maxInstallment = "0";
    const timeoutLimit = "30";
    const paymentAmount = String(amountMinor);
    const userBasketBase64 = Buffer.from(JSON.stringify([
      [String(requestDoc.data()?.title ?? "Mahallem hizmeti").slice(0, 200), (amountMinor / 100).toFixed(2), 1],
    ])).toString("base64");

    const paytrToken = createPaytrIframeToken(
      merchantId,
      userIp,
      merchantOid,
      customerEmail,
      paymentAmount,
      userBasketBase64,
      noInstallment,
      maxInstallment,
      "TL",
      testMode,
      merchantSalt,
      merchantKey
    );

    const tokenAction = await db.runTransaction(async (tx) => {
      const [rateSnap, hourlyRateSnap, paymentSnap] = await Promise.all([
        tx.get(rateLimitRef),
        tx.get(hourlyRateLimitRef),
        tx.get(paymentRef),
      ]);

      const rate = rateSnap.exists ? rateSnap.data()! : {};
      const hourlyRate = hourlyRateSnap.exists ? hourlyRateSnap.data()! : {};
      const windowStart = Number(rate.windowStartMs ?? 0);
      const count = Number(rate.count ?? 0);
      const hourWindowStart = Number(hourlyRate.windowStartMs ?? 0);
      const hourCount = Number(hourlyRate.count ?? 0);
      const now = Date.now();
      const activeWindow = Number.isSafeInteger(windowStart) && now - windowStart < 60_000;
      const activeHourWindow = Number.isSafeInteger(hourWindowStart) && now - hourWindowStart < 3_600_000;

      const consumePaymentAttempt = () => {
        if (activeWindow && count >= 5) {
          throw new HttpsError("resource-exhausted", "Çok fazla ödeme denemesi. Lütfen biraz sonra tekrar deneyin.");
        }
        if (activeHourWindow && hourCount >= 20) {
          throw new HttpsError("resource-exhausted", "Saatlik ödeme denemesi kotanıza ulaştınız.");
        }

        tx.set(rateLimitRef, {
          windowStartMs: activeWindow ? windowStart : now,
          count: activeWindow ? count + 1 : 1,
          updatedAt: FieldValue.serverTimestamp(),
        }, { merge: true });
        tx.set(hourlyRateLimitRef, {
          windowStartMs: activeHourWindow ? hourWindowStart : now,
          count: activeHourWindow ? hourCount + 1 : 1,
          updatedAt: FieldValue.serverTimestamp(),
        }, { merge: true });
      };

      if (paymentSnap.exists) {
        const payment = paymentSnap.data()!;
        const status = String(payment.status ?? "");
        if (status === "PENDING" && typeof payment.checkoutUrl === "string") {
          return "REUSE" as const;
        }
        if (status === "CREATED") {
          const startedAt = payment.tokenGenerationStartedAt;
          const startedMillis = startedAt && typeof startedAt.toMillis === "function"
            ? startedAt.toMillis()
            : 0;
          if (startedMillis > Date.now() - 2 * 60_000) {
            return "IN_PROGRESS" as const;
          }
          consumePaymentAttempt();
          tx.update(paymentRef, {
            tokenGenerationStartedAt: FieldValue.serverTimestamp(),
            updatedAt: FieldValue.serverTimestamp(),
          });
          return "START" as const;
        }
        if (status === "FAILED") {
          throw new HttpsError(
            "failed-precondition",
            "Bu ödeme denemesi başarısız oldu. Yeni bir ödeme denemesi başlatın."
          );
        }
        return "IN_PROGRESS" as const;
      }

      consumePaymentAttempt();

      tx.create(paymentRef, {
        id: idemHash,
        requestId,
        quoteId,
        customerId: request.auth!.uid,
        providerId: String(quote.providerOwnerId ?? ""),
        amountMinor,
        currency: "TRY",
        status: "CREATED",
        providerOrderId: merchantOid,
        idempotencyKeyHash: idemHash,
        tokenGenerationStartedAt: FieldValue.serverTimestamp(),
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
      return "START" as const;
    });

    if (tokenAction === "REUSE") {
      const current = await paymentRef.get();
      if (current.exists && typeof current.data()?.checkoutUrl === "string") {
        return current.data();
      }
      throw new HttpsError("aborted", "Ödeme işlemi durum değiştirirken tekrar deneyin.");
    }
    if (tokenAction === "IN_PROGRESS") {
      throw new HttpsError(
        "aborted",
        "Aynı ödeme işlemi zaten başlatılıyor. Lütfen mevcut ödeme ekranını kontrol edin."
      );
    }

    let response: Response;
    try {
      const body = new URLSearchParams({
        merchant_id: merchantId,
        user_ip: userIp,
        merchant_oid: merchantOid,
        email: customerEmail,
        payment_amount: paymentAmount,
        paytr_token: paytrToken,
        user_basket: userBasketBase64,
        debug_on: testMode === "1" ? "1" : "0",
        no_installment: noInstallment,
        max_installment: maxInstallment,
        user_name: String(privateRequest.customerName ?? request.auth.token.name ?? "Mahallem Kullanıcısı").slice(0, 120),
        user_address: String(privateRequest.address ?? "").slice(0, 500),
        user_phone: String(privateRequest.customerPhone ?? request.auth.token.phone_number ?? "").slice(0, 32),
        merchant_ok_url: okUrl,
        merchant_fail_url: failUrl,
        timeout_limit: timeoutLimit,
        currency: "TL",
        test_mode: testMode,
        lang: "tr",
        iframe_v2: "1",
      });

      response = await fetch("https://www.paytr.com/odeme/api/get-token", {
        method: "POST",
        headers: { "content-type": "application/x-www-form-urlencoded" },
        body,
        signal: AbortSignal.timeout(15_000),
      });
    } catch (error) {
      await paymentRef.set({
        status: "FAILED",
        failureReason: "PayTR token endpoint unreachable",
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
      logger.error("PayTR token request failed", { paymentId: idemHash, error });
      throw new HttpsError("unavailable", "Ödeme sağlayıcısına bağlanılamadı.");
    }

    let providerResponse: { status?: string; token?: string; reason?: string };
    try {
      providerResponse = await response.json() as { status?: string; token?: string; reason?: string };
    } catch (error) {
      await paymentRef.set({
        status: "FAILED",
        failureReason: "Invalid PayTR response",
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
      logger.error("PayTR returned invalid JSON", { paymentId: idemHash, status: response.status, error });
      throw new HttpsError("unavailable", "Ödeme sağlayıcısından geçersiz yanıt alındı.");
    }

    if (providerResponse.status !== "success" || !providerResponse.token) {
      await paymentRef.set({
        status: "FAILED",
        failureReason: String(providerResponse.reason ?? "PayTR token oluşturamadı").slice(0, 500),
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
      throw new HttpsError("failed-precondition", "Ödeme başlatılamadı.");
    }

    const checkoutUrl = "https://www.paytr.com/odeme/guvenli/" + providerResponse.token;
    await paymentRef.set({
      status: "PENDING",
      checkoutUrl,
      providerTokenCreatedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });

    return {
      id: idemHash,
      checkoutUrl,
      requestId,
      quoteId,
      amountMinor,
      currency: "TRY",
      status: "PENDING",
    };
  }
);

export const saveProviderListing = onCall(
  { enforceAppCheck: true },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    await assertAccountActive(request.auth.uid);

    const data = callableData(request.data);
    const providerId = requireString(data, "providerId", 120, 1);
    const displayName = requireString(data, "displayName", 120, 1);
    const title = requireString(data, "title", 200, 1);
    const bio = requireString(data, "bio", 2000, 0);
    const sector = requireString(data, "sector", 64, 1);
    const categoryId = requireString(data, "categoryId", 80, 1);
    const district = requireString(data, "district", 80, 1);
    const city = requireString(data, "city", 80, 1);
    const hourlyOrBasePrice = typeof data.hourlyOrBasePrice === "string"
      ? data.hourlyOrBasePrice.slice(0, 80)
      : "Anlaşmaya Bağlı";
    const isEmergencyAvailable = data.isEmergencyAvailable == null
      ? false
      : optionalBoolean(data, "isEmergencyAvailable") === true;
    const experienceYears = Number(data.experienceYears);
    const latitude = Number(data.latitude);
    const longitude = Number(data.longitude);
    const isOpenForOffers = data.isOpenForOffers == null
      ? false
      : optionalBoolean(data, "isOpenForOffers") === true;

    if (!ID_PATTERN.test(providerId)
      || !Number.isSafeInteger(experienceYears) || experienceYears < 0 || experienceYears > 80
      || !Number.isFinite(latitude) || latitude < -90 || latitude > 90
      || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
      throw new HttpsError("invalid-argument", "Geçersiz hizmet sağlayıcı verisi.");
    }

    const ref = db.collection("providers").doc(providerId);
    const rateRef = db.collection("rateLimits").doc("provider-write-day:" + request.auth.uid);
    const now = Date.now();

    await db.runTransaction(async (tx) => {
      const [existing, rateSnap] = await Promise.all([tx.get(ref), tx.get(rateRef)]);
      if (existing.exists && existing.data()?.ownerId !== request.auth!.uid) {
        throw new HttpsError("permission-denied", "Bu hizmet ilanına erişemezsiniz.");
      }

      const rate = rateSnap.exists ? rateSnap.data()! : {};
      const windowStart = Number(rate.windowStartMs ?? 0);
      const count = Number(rate.count ?? 0);
      const activeWindow = Number.isSafeInteger(windowStart) && now - windowStart < 86_400_000;
      if (activeWindow && count >= 10) {
        throw new HttpsError("resource-exhausted", "Günlük hizmet ilanı değişikliği kotanıza ulaştınız.");
      }

      tx.set(rateRef, {
        windowStartMs: activeWindow ? windowStart : now,
        count: activeWindow ? count + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      tx.set(ref, {
        ownerId: request.auth!.uid,
        displayName,
        title,
        bio,
        sector,
        categoryId,
        district,
        city,
        hourlyOrBasePrice,
        isEmergencyAvailable,
        experienceYears,
        serviceArea: { latitude, longitude },
        isOpenForOffers,
        ...(existing.exists ? {} : { createdAt: FieldValue.serverTimestamp() }),
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
    });

    return { saved: true, providerId };
  }
);

export const updateProviderAvailability = onCall(
  { enforceAppCheck: true },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    await assertAccountActive(request.auth.uid);

    const data = callableData(request.data);
    const providerId = requireString(data, "providerId", 120, 1);
    const dateIso = data.dateIso == null ? "" : requireString(data, "dateIso", 10, 0);
    const dateBooked = optionalBoolean(data, "dateBooked");
    const isOpenForOffers = optionalBoolean(data, "isOpenForOffers");

    if (!ID_PATTERN.test(providerId)) {
      throw new HttpsError("invalid-argument", "Geçersiz hizmet sağlayıcı kimliği.");
    }
    if ((dateBooked !== null) !== Boolean(dateIso)) {
      throw new HttpsError("invalid-argument", "Takvim güncellemesi için dateIso ve dateBooked birlikte gönderilmeli.");
    }
    if (dateIso && !isValidIsoDate(dateIso)) {
      throw new HttpsError("invalid-argument", "Geçersiz takvim tarihi.");
    }
    if (isOpenForOffers === null && dateBooked === null) {
      throw new HttpsError("invalid-argument", "Güncelleme alanı bulunamadı.");
    }

    const ref = db.collection("providers").doc(providerId);
    const rateRef = db.collection("rateLimits").doc("availability:" + request.auth.uid);
    const minuteRateRef = db.collection("rateLimits").doc("availability-minute:" + request.auth.uid);
    const hourRateRef = db.collection("rateLimits").doc("availability-hour:" + request.auth.uid);
    const now = Date.now();

    await db.runTransaction(async (tx) => {
      const [snap, rateSnap, minuteRateSnap, hourRateSnap] = await Promise.all([
        tx.get(ref),
        tx.get(rateRef),
        tx.get(minuteRateRef),
        tx.get(hourRateRef),
      ]);
      if (!snap.exists || snap.data()?.ownerId !== request.auth!.uid) {
        throw new HttpsError("permission-denied", "Bu hizmet sağlayıcıyı güncelleyemezsiniz.");
      }

      const minuteRate = minuteRateSnap.exists ? minuteRateSnap.data()! : {};
      const hourRate = hourRateSnap.exists ? hourRateSnap.data()! : {};
      const minuteWindowStart = Number(minuteRate.windowStartMs ?? 0);
      const minuteCount = Number(minuteRate.count ?? 0);
      const hourWindowStart = Number(hourRate.windowStartMs ?? 0);
      const hourCount = Number(hourRate.count ?? 0);
      const activeMinute = Number.isSafeInteger(minuteWindowStart) && now - minuteWindowStart < 60_000;
      const activeHour = Number.isSafeInteger(hourWindowStart) && now - hourWindowStart < 3_600_000;

      if (activeMinute && minuteCount >= 60) {
        throw new HttpsError("resource-exhausted", "Çok fazla takvim güncellemesi. Lütfen biraz sonra tekrar deneyin.");
      }
      if (activeHour && hourCount >= 600) {
        throw new HttpsError("resource-exhausted", "Saatlik takvim güncelleme kotanıza ulaştınız.");
      }

      tx.set(minuteRateRef, {
        windowStartMs: activeMinute ? minuteWindowStart : now,
        count: activeMinute ? minuteCount + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
      tx.set(hourRateRef, {
        windowStartMs: activeHour ? hourWindowStart : now,
        count: activeHour ? hourCount + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      const rate = rateSnap.exists ? rateSnap.data()! : {};
      const windowStart = Number(rate.windowStartMs ?? 0);
      const count = Number(rate.count ?? 0);
      const activeWindow = Number.isSafeInteger(windowStart) && now - windowStart < 86_400_000;
      if (activeWindow && count >= 50) {
        throw new HttpsError("resource-exhausted", "Günlük müsaitlik değişikliği kotanıza ulaştınız.");
      }

      const update: Record<string, unknown> = {
        updatedAt: FieldValue.serverTimestamp(),
      };
      if (isOpenForOffers !== null) update.isOpenForOffers = isOpenForOffers;

      if (dateBooked !== null) {
        const current = snap.data()?.bookedDates;
        const dates = Array.isArray(current)
          ? current.filter((value): value is string => typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value))
          : [];
        const next = new Set(dates);
        if (dateBooked) {
          next.add(dateIso);
          if (next.size > 366) {
            throw new HttpsError("resource-exhausted", "Takvimde en fazla 366 gün tutulabilir.");
          }
        } else {
          next.delete(dateIso);
        }
        update.bookedDates = [...next].sort();
      }

      tx.set(rateRef, {
        windowStartMs: activeWindow ? windowStart : now,
        count: activeWindow ? count + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
      tx.update(ref, update);
    });

    return { updated: true, providerId };
  }
);

export const saveJobRequest = onCall(
  { enforceAppCheck: true },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    await assertAccountActive(request.auth.uid);

    const data = callableData(request.data);
    const requestId = requireString(data, "requestId", 80, 1);
    const title = requireString(data, "title", 200, 1);
    const sector = requireString(data, "sector", 64, 1);
    const categoryId = requireString(data, "categoryId", 80, 1);
    const district = requireString(data, "district", 80, 1);
    const urgencyMode = requireString(data, "urgencyMode", 32, 1);
    const eventOrJobDate = requireString(data, "eventOrJobDate", 32, 0);
    const eventTime = requireString(data, "eventTime", 32, 0);
    const budgetEstimate = requireString(data, "budgetEstimate", 200, 0);

    if (eventOrJobDate && !isValidIsoDate(eventOrJobDate)) {
      throw new HttpsError("invalid-argument", "Geçersiz hizmet/talep tarihi.");
    }
    if (eventTime && !isValidTime(eventTime)) {
      throw new HttpsError("invalid-argument", "Geçersiz hizmet/talep saati.");
    }

    const customerPhone = typeof data.customerPhone === "string" ? data.customerPhone.slice(0, 32) : "";
    const phoneVerified = data.phoneVerified === true;
    const rawArea = Number(data.areaSquareMeters);
    const rawDuration = Number(data.durationHours);
    const rawLatitude = Number(data.latitude);
    const rawLongitude = Number(data.longitude);

    if (
      (Number.isFinite(rawArea) && (!Number.isSafeInteger(rawArea) || rawArea < 0 || rawArea > 100_000))
      || (Number.isFinite(rawDuration) && (!Number.isSafeInteger(rawDuration) || rawDuration < 0 || rawDuration > 168))
      || (Number.isFinite(rawLatitude) && (rawLatitude < -90 || rawLatitude > 90))
      || (Number.isFinite(rawLongitude) && (rawLongitude < -180 || rawLongitude > 180))
    ) {
      throw new HttpsError("invalid-argument", "Geçersiz özel talep ölçü/konum verisi.");
    }
    if (phoneVerified) {
      const authPhone = typeof request.auth.token.phone_number === "string"
        ? request.auth.token.phone_number
        : "";
      if (!authPhone || customerPhone !== authPhone) {
        throw new HttpsError("permission-denied", "Doğrulanmamış telefon bilgisi.");
      }
    }

    const ref = db.collection("jobRequests").doc(requestId);
    const privateRef = db.collection("jobRequestPrivate").doc(requestId);
    const rateRef = db.collection("rateLimits").doc("request-write-day:" + request.auth.uid);

    await db.runTransaction(async (tx) => {
      const [existing, rateSnap] = await Promise.all([tx.get(ref), tx.get(rateRef)]);
      if (existing.exists) {
        if (existing.data()?.ownerId !== request.auth!.uid) {
          throw new HttpsError("permission-denied", "Bu talebe erişemezsiniz.");
        }
        if (String(existing.data()?.status ?? "") !== "PENDING") {
          throw new HttpsError("failed-precondition", "Teklif alınmış veya tamamlanmış talep artık düzenlenemez.");
        }
      }

      const rate = rateSnap.exists ? rateSnap.data()! : {};
      const windowStart = Number(rate.windowStartMs ?? 0);
      const count = Number(rate.count ?? 0);
      const now = Date.now();
      const activeWindow = Number.isSafeInteger(windowStart) && now - windowStart < 86_400_000;
      if (activeWindow && count >= 20) {
        throw new HttpsError("resource-exhausted", "Günlük talep oluşturma/değiştirme kotanıza ulaştınız.");
      }

      tx.set(rateRef, {
        windowStartMs: activeWindow ? windowStart : now,
        count: activeWindow ? count + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      tx.set(ref, {
        ownerId: request.auth!.uid,
        title,
        sector,
        categoryId,
        district,
        urgencyMode,
        eventOrJobDate,
        eventTime,
        budgetEstimate,
        status: existing.exists ? existing.data()?.status : "PENDING",
        ...(existing.exists ? {} : { createdAt: FieldValue.serverTimestamp() }),
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      const privateData: Record<string, unknown> = {
        ownerId: request.auth!.uid,
        address: typeof data.address === "string" ? data.address.slice(0, 500) : "",
        customerName: typeof data.customerName === "string" ? data.customerName.slice(0, 120) : "",
        customerPhone,
        phoneVerified,
        areaSquareMeters: Number.isSafeInteger(rawArea) ? rawArea : 0,
        roomCount: typeof data.roomCount === "string" ? data.roomCount.slice(0, 32) : "",
        isFurnished: data.isFurnished === true,
        materialsIncluded: data.materialsIncluded === true,
        renovationNotes: typeof data.renovationNotes === "string" ? data.renovationNotes.slice(0, 2000) : "",
        eventType: typeof data.eventType === "string" ? data.eventType.slice(0, 80) : "",
        durationHours: Number.isSafeInteger(rawDuration) ? rawDuration : 0,
        targetAgeGroup: typeof data.targetAgeGroup === "string" ? data.targetAgeGroup.slice(0, 80) : "",
        selectedCostumeOrCharacter: typeof data.selectedCostumeOrCharacter === "string" ? data.selectedCostumeOrCharacter.slice(0, 120) : "",
        extraServicesRequested: typeof data.extraServicesRequested === "string" ? data.extraServicesRequested.slice(0, 1000) : "",
        latitude: Number.isFinite(rawLatitude) ? rawLatitude : 0,
        longitude: Number.isFinite(rawLongitude) ? rawLongitude : 0,
        updatedAt: FieldValue.serverTimestamp(),
      };
      tx.set(privateRef, privateData, { merge: true });
    });

    return { saved: true, requestId };
  }
);

export const issueImageUploadGrant = onCall(
  { enforceAppCheck: true },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    await assertAccountActive(request.auth.uid);

    const data = callableData(request.data);
    const kind = requireString(data, "kind", 20, 1);
    if (!["USER", "CHAT", "JOB_REQUEST"].includes(kind)) {
      throw new HttpsError("invalid-argument", "Geçersiz yükleme türü.");
    }

    const conversationId = data.conversationId == null ? "" : requireString(data, "conversationId", 64, 1);
    const requestId = data.requestId == null ? "" : requireString(data, "requestId", 80, 1);

    if (kind === "CHAT" && !/^[a-f0-9]{64}$/.test(conversationId)) {
      throw new HttpsError("invalid-argument", "Geçersiz sohbet kimliği.");
    }
    if (kind === "JOB_REQUEST" && !ID_PATTERN.test(requestId)) {
      throw new HttpsError("invalid-argument", "Geçersiz talep kimliği.");
    }
    if (kind === "USER" && (conversationId || requestId)) {
      throw new HttpsError("invalid-argument", "Kullanıcı görseli için hedef alanı gönderilmemeli.");
    }

    const uid = request.auth.uid;
    const rateRef = db.collection("rateLimits").doc("storage-grant-hour:" + uid);
    const grantId = randomUUID().replace(/-/g, "");
    const grantRef = db.collection("users").doc(uid).collection("uploadGrants").doc(grantId);
    const now = Date.now();
    let conversationSnapForGrant: FirebaseFirestore.DocumentSnapshot | null = null;

    await db.runTransaction(async (tx) => {
      const [rateSnap, grantSnap] = await Promise.all([
        tx.get(rateRef),
        tx.get(grantRef),
      ]);

      if (grantSnap.exists) {
        throw new HttpsError("aborted", "Yükleme kimliği çakıştı; tekrar deneyin.");
      }

      const rate = rateSnap.exists ? rateSnap.data()! : {};
      const windowStart = Number(rate.windowStartMs ?? 0);
      const count = Number(rate.count ?? 0);
      const activeWindow = Number.isSafeInteger(windowStart) && now - windowStart < 3_600_000;

      if (activeWindow && count >= 20) {
        throw new HttpsError("resource-exhausted", "Saatlik görsel yükleme kotanıza ulaştınız.");
      }

      if (kind === "CHAT") {
        conversationSnapForGrant = await tx.get(db.collection("conversations").doc(conversationId));
        const participants = conversationSnapForGrant.exists ? conversationSnapForGrant.data()?.participantIds : null;
        if (
          !conversationSnapForGrant.exists
          || !Array.isArray(participants)
          || participants.length !== 2
          || !participants.every((id) => typeof id === "string" && ID_PATTERN.test(id))
          || !participants.includes(uid)
        ) {
          throw new HttpsError("permission-denied", "Bu sohbete görsel yükleyemezsiniz.");
        }
      }

      if (kind === "JOB_REQUEST") {
        const requestSnap = await tx.get(db.collection("jobRequests").doc(requestId));
        const requestData = requestSnap.exists ? requestSnap.data() : null;
        const requestStatus = String(requestData?.status ?? "");
        if (
          !requestSnap.exists
          || requestData?.ownerId !== uid
          || !["PENDING", "QUOTED", "ACCEPTED"].includes(requestStatus)
        ) {
          throw new HttpsError("permission-denied", "Bu talep için görsel yükleyemezsiniz.");
        }
      }

      tx.set(rateRef, {
        windowStartMs: activeWindow ? windowStart : now,
        count: activeWindow ? count + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      tx.create(grantRef, {
        ownerUid: uid,
        kind,
        conversationId: kind === "CHAT" ? conversationId : FieldValue.delete(),
        participantIds: kind === "CHAT"
          ? (conversationSnapForGrant?.data()?.participantIds as string[])
          : FieldValue.delete(),
        requestId: kind === "JOB_REQUEST" ? requestId : FieldValue.delete(),
        expiresAt: FirestoreTimestamp.fromMillis(now + 10 * 60_000),
        createdAt: FieldValue.serverTimestamp(),
      });
    });

    return { grantId, expiresInSeconds: 600 };
  }
);

export const registerDeviceToken = onCall(
  { enforceAppCheck: true },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    await assertAccountActive(request.auth.uid);

    const data = callableData(request.data);
    const token = requireString(data, "token", 4096, 20);
    const platform = typeof data.platform === "string" ? data.platform : "android";

    if (platform !== "android" || !/^[A-Za-z0-9:_-]{20,4096}$/.test(token)) {
      throw new HttpsError("invalid-argument", "Geçersiz cihaz belirteci.");
    }

    const uid = request.auth.uid;
    const tokenId = hashDeviceToken(token);
    const tokenOwnerRef = db.collection("deviceTokenOwners").doc(tokenId);
    const deviceRef = db.collection("users").doc(uid).collection("devices").doc(tokenId);
    const devicesQuery = db.collection("users").doc(uid).collection("devices").limit(11);
    const rateLimitRef = db.collection("rateLimits").doc("device-register:" + uid);
    const now = Date.now();

    await db.runTransaction(async (tx) => {
      const [existing, devices, rateSnap, tokenOwnerSnap] = await Promise.all([
        tx.get(deviceRef),
        tx.get(devicesQuery),
        tx.get(rateLimitRef),
        tx.get(tokenOwnerRef),
      ]);

      if (tokenOwnerSnap.exists && tokenOwnerSnap.data()?.uid !== uid) {
        throw new HttpsError("permission-denied", "Bu cihaz belirteci başka bir hesaba bağlı.");
      }

      const rate = rateSnap.exists ? rateSnap.data()! : {};
      const windowStart = Number(rate.windowStartMs ?? 0);
      const count = Number(rate.count ?? 0);
      const activeWindow = Number.isSafeInteger(windowStart) && now - windowStart < 60 * 60 * 1000;
      if (activeWindow && count >= 20) {
        throw new HttpsError("resource-exhausted", "Çok fazla cihaz kaydı denemesi.");
      }

      if (!existing.exists && devices.size >= 10) {
        throw new HttpsError("resource-exhausted", "Bu hesap için en fazla 10 cihaz kaydı tutulabilir.");
      }

      tx.set(rateLimitRef, {
        windowStartMs: activeWindow ? windowStart : now,
        count: activeWindow ? count + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      tx.set(deviceRef, {
        token,
        platform,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
      tx.set(tokenOwnerRef, {
        uid,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
    });

    return { registered: true };
  }
);

export const unregisterDeviceToken = onCall(
  { enforceAppCheck: true },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    const data = callableData(request.data);
    const token = requireString(data, "token", 4096, 20);
    if (!/^[A-Za-z0-9:_-]{20,4096}$/.test(token)) {
      throw new HttpsError("invalid-argument", "Geçersiz cihaz belirteci.");
    }

    const tokenId = hashDeviceToken(token);
    const deviceRef = db.collection("users").doc(request.auth.uid).collection("devices").doc(tokenId);
    const tokenOwnerRef = db.collection("deviceTokenOwners").doc(tokenId);
    await db.runTransaction(async (tx) => {
      const ownerSnap = await tx.get(tokenOwnerRef);
      if (ownerSnap.exists && ownerSnap.data()?.uid !== request.auth!.uid) return;
      tx.delete(deviceRef);
      if (ownerSnap.exists) tx.delete(tokenOwnerRef);
    });
    return { unregistered: true };
  }
);

export const sendMessage = onCall(
  { region: "europe-west1", enforceAppCheck: true },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    }
    await assertAccountActive(request.auth.uid);

    const data = callableData(request.data);
    const conversationId = requireString(data, "conversationId", 100, 1);
    const rawText = data.text;
    const text = rawText == null ? "" : requireString(data, "text", 2000, 0);
    const messageType = data.messageType == null ? "TEXT" : requireString(data, "messageType", 10, 1);
    const attachmentUrl = data.attachmentUrl == null ? null : requireString(data, "attachmentUrl", 512, 1);

    if (!/^[a-f0-9]{64}$/.test(conversationId)) {
      throw new HttpsError("invalid-argument", "Geçersiz conversationId.");
    }
    if (!["TEXT", "OFFER", "VOICE", "IMAGE"].includes(messageType)) {
      throw new HttpsError("invalid-argument", "Geçersiz mesaj tipi.");
    }
    if (text.length > 2000) {
      throw new HttpsError("invalid-argument", "Mesaj en fazla 2000 karakter olabilir.");
    }
    if (!text.trim() && !attachmentUrl) {
      throw new HttpsError("invalid-argument", "Mesaj içeriği boş olamaz.");
    }
    if (attachmentUrl && attachmentUrl.length > 512) {
      throw new HttpsError("invalid-argument", "Ek dosya yolu çok uzun.");
    }
    if (messageType === "IMAGE" && !attachmentUrl) {
      throw new HttpsError("invalid-argument", "Fotoğraf mesajı için ek dosya gerekli.");
    }
    if (attachmentUrl) {
      const allowedPrefixes = [
        "chatAttachments/" + conversationId + "/" + request.auth.uid + "/",
      ];
      if (!allowedPrefixes.some((prefix) => attachmentUrl.startsWith(prefix))) {
        throw new HttpsError("permission-denied", "Bu medya dosyasına mesajda erişim yetkiniz yok.");
      }
      if (attachmentUrl.includes("..") || attachmentUrl.includes("?") || attachmentUrl.includes("#")) {
        throw new HttpsError("invalid-argument", "Geçersiz medya yolu.");
      }
    }

    const conversationRef = db.collection("conversations").doc(conversationId);
    const rateLimitRef = db.collection("rateLimits").doc(
      "message:" + request.auth.uid
    );
    const hourlyRateLimitRef = db.collection("rateLimits").doc(
      "message-hour:" + request.auth.uid
    );

    const messageRef = db.collection("messages").doc();
    const now = Date.now();

    await db.runTransaction(async (tx) => {
      const conversationSnap = await tx.get(conversationRef);
      if (attachmentUrl) {
        const attachmentParts = attachmentUrl.split("/");
        const attachmentName = attachmentParts.length === 4 ? attachmentParts[3] ?? "" : "";
        const grantId = attachmentName.endsWith(".jpg")
          ? attachmentName.slice(0, -4)
          : "";
        const grantRef = db.collection("users").doc(request.auth!.uid).collection("uploadGrants").doc(grantId || "invalid");
        const grantSnap = await tx.get(grantRef);
        const grant = grantSnap.data();
        if (!grantSnap.exists || grant?.ownerUid !== request.auth!.uid || grant?.kind !== "CHAT"
          || grant?.conversationId !== conversationId || grant?.validated !== true) {
          throw new HttpsError("failed-precondition", "Medya dosyası henüz doğrulanmadı.");
        }
      }
      const [rateLimitSnap, hourlyRateSnap] = await Promise.all([
        tx.get(rateLimitRef),
        tx.get(hourlyRateLimitRef),
      ]);

      if (!conversationSnap.exists) {
        throw new HttpsError("not-found", "Sohbet bulunamadı.");
      }

      const participants = conversationSnap.data()?.participantIds;
      if (!Array.isArray(participants) || !participants.includes(request.auth!.uid)) {
        throw new HttpsError("permission-denied", "Bu sohbete mesaj gönderemezsiniz.");
      }

      const rate = rateLimitSnap.exists ? rateLimitSnap.data()! : {};
      const hourlyRate = hourlyRateSnap.exists ? hourlyRateSnap.data()! : {};
      const windowStart = Number(rate.windowStartMs ?? 0);
      const count = Number(rate.count ?? 0);
      const hourWindowStart = Number(hourlyRate.windowStartMs ?? 0);
      const hourCount = Number(hourlyRate.count ?? 0);
      const activeWindow = Number.isSafeInteger(windowStart) && now - windowStart < 60_000;
      const activeHourWindow = Number.isSafeInteger(hourWindowStart) && now - hourWindowStart < 3_600_000;

      if (activeWindow && count >= 30) {
        throw new HttpsError("resource-exhausted", "Çok fazla mesaj gönderildi. Lütfen biraz sonra tekrar deneyin.");
      }
      if (activeHourWindow && hourCount >= 500) {
        throw new HttpsError("resource-exhausted", "Saatlik mesaj kotanıza ulaştınız.");
      }

      tx.set(rateLimitRef, {
        windowStartMs: activeWindow ? windowStart : now,
        count: activeWindow ? count + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
      tx.set(hourlyRateLimitRef, {
        windowStartMs: activeHourWindow ? hourWindowStart : now,
        count: activeHourWindow ? hourCount + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      tx.create(messageRef, {
        conversationId,
        senderId: request.auth!.uid,
        text,
        attachmentUrl,
        messageType,
        createdAt: FieldValue.serverTimestamp(),
      });

      tx.update(conversationRef, {
        lastMessageAt: FieldValue.serverTimestamp(),
        lastMessagePreview: messageType === "IMAGE" ? "📷 Fotoğraf" : text.slice(0, 200),
        updatedAt: FieldValue.serverTimestamp(),
      });

      const recipientIds = participants.filter((id: unknown) =>
        typeof id === "string" && id !== request.auth!.uid
      ) as string[];
      for (const recipientId of recipientIds) {
        tx.set(
          db.collection("users").doc(recipientId).collection("conversationState").doc(conversationId),
          {
            conversationId,
            unreadCount: FieldValue.increment(1),
            lastMessageAt: FieldValue.serverTimestamp(),
            updatedAt: FieldValue.serverTimestamp(),
          },
          { merge: true }
        );
      }
    });

    return { messageId: messageRef.id };
  }
);

export const markConversationRead = onCall(
  { region: "europe-west1", enforceAppCheck: true },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    await assertAccountActive(request.auth.uid);

    const data = callableData(request.data);
    const conversationId = requireString(data, "conversationId", 64, 1);
    if (!/^[a-f0-9]{64}$/.test(conversationId)) {
      throw new HttpsError("invalid-argument", "Geçersiz conversationId.");
    }

    const conversationRef = db.collection("conversations").doc(conversationId);
    const stateRef = db.collection("users").doc(request.auth.uid)
      .collection("conversationState").doc(conversationId);
    const minuteRateRef = db.collection("rateLimits").doc("conversation-read:" + request.auth.uid);
    const hourRateRef = db.collection("rateLimits").doc("conversation-read-hour:" + request.auth.uid);
    const now = Date.now();

    await db.runTransaction(async (tx) => {
      const [conversationSnap, minuteRateSnap, hourRateSnap] = await Promise.all([
        tx.get(conversationRef),
        tx.get(minuteRateRef),
        tx.get(hourRateRef),
      ]);
      if (!conversationSnap.exists) {
        throw new HttpsError("not-found", "Sohbet bulunamadı.");
      }

      const participants = conversationSnap.data()?.participantIds;
      if (!Array.isArray(participants) || !participants.includes(request.auth!.uid)) {
        throw new HttpsError("permission-denied", "Bu sohbeti okundu işaretleyemezsiniz.");
      }

      const minuteRate = minuteRateSnap.exists ? minuteRateSnap.data()! : {};
      const hourRate = hourRateSnap.exists ? hourRateSnap.data()! : {};
      const minuteWindowStart = Number(minuteRate.windowStartMs ?? 0);
      const minuteCount = Number(minuteRate.count ?? 0);
      const hourWindowStart = Number(hourRate.windowStartMs ?? 0);
      const hourCount = Number(hourRate.count ?? 0);
      const activeMinute = Number.isSafeInteger(minuteWindowStart) && now - minuteWindowStart < 60_000;
      const activeHour = Number.isSafeInteger(hourWindowStart) && now - hourWindowStart < 3_600_000;

      if (activeMinute && minuteCount >= 120) {
        throw new HttpsError("resource-exhausted", "Çok fazla okundu işareti gönderildi. Lütfen biraz sonra tekrar deneyin.");
      }
      if (activeHour && hourCount >= 1000) {
        throw new HttpsError("resource-exhausted", "Saatlik okundu işareti kotanıza ulaştınız.");
      }

      tx.set(minuteRateRef, {
        windowStartMs: activeMinute ? minuteWindowStart : now,
        count: activeMinute ? minuteCount + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
      tx.set(hourRateRef, {
        windowStartMs: activeHour ? hourWindowStart : now,
        count: activeHour ? hourCount + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      tx.set(stateRef, {
        conversationId,
        unreadCount: 0,
        lastReadAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
    });

    return { markedRead: true, conversationId };
  }
);

export const startConversation = onCall(
  { region: "europe-west1", enforceAppCheck: true },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    }
    await assertAccountActive(request.auth.uid);

    const data = callableData(request.data);
    const targetId = typeof data.targetId === "string" ? data.targetId : "";
    const relatedItemId = typeof data.relatedItemId === "string" ? data.relatedItemId : "";
    const relatedItemTitle = data.relatedItemTitle == null ? "" : requireString(data, "relatedItemTitle", 200, 0);
    const isRequestConversation = targetId === "request-owner";

    if (relatedItemId.length > 80 || (relatedItemId && !ID_PATTERN.test(relatedItemId))) {
      throw new HttpsError("invalid-argument", "Geçersiz relatedItemId.");
    }
    if (!isRequestConversation && targetId.length > 80) {
      throw new HttpsError("invalid-argument", "Geçersiz targetId.");
    }
    if (isRequestConversation && !relatedItemId) {
      throw new HttpsError("invalid-argument", "Talep sohbeti için requestId gerekli.");
    }
    if (!isRequestConversation && !targetId) {
      throw new HttpsError("invalid-argument", "Geçerli bir sohbet hedefi gerekli.");
    }

    let participantUid = "";

    if (isRequestConversation) {
      const requestSnap = await db.collection("jobRequests").doc(relatedItemId).get();
      if (!requestSnap.exists) {
        throw new HttpsError("not-found", "Talep bulunamadı.");
      }

      participantUid = String(requestSnap.data()?.ownerId ?? "");
      if (!participantUid || participantUid === request.auth.uid) {
        throw new HttpsError("permission-denied", "Talep sahibiyle geçerli bir sohbet oluşturulamadı.");
      }

      const status = String(requestSnap.data()?.status ?? "");
      if (!["PENDING", "QUOTED", "ACCEPTED"].includes(status)) {
        throw new HttpsError("failed-precondition", "Bu talep artık sohbet başlatılabilir durumda değil.");
      }

      const providerQuery = await db.collection("providers")
        .where("ownerId", "==", request.auth.uid)
        .limit(1)
        .get();

      if (providerQuery.empty) {
        throw new HttpsError("permission-denied", "Bu talep için sohbet başlatma yetkiniz yok.");
      }

      const provider = providerQuery.docs[0].data();
      if (provider.isOpenForOffers !== true) {
        throw new HttpsError("permission-denied", "Hizmet sağlayıcınız yeni taleplere kapalı.");
      }

      await assertAccountActive(participantUid);
    } else {
      const providerSnap = await db.collection("providers").doc(targetId).get();
      if (!providerSnap.exists) {
        throw new HttpsError("not-found", "Hizmet sağlayıcı bulunamadı.");
      }

      participantUid = String(providerSnap.data()?.ownerId ?? "");
      if (!participantUid || participantUid === request.auth.uid) {
        throw new HttpsError("failed-precondition", "Geçerli bir hizmet sağlayıcı bulunamadı.");
      }

      if (relatedItemId !== targetId) {
        throw new HttpsError("invalid-argument", "Hizmet sağlayıcı sohbet bağlantısı geçersiz.");
      }

      await assertAccountActive(participantUid);
    }

    const participantIds = [request.auth.uid, participantUid].sort();
    const conversationId = createHash("sha256")
      .update(participantIds.join(":") + ":" + relatedItemId)
      .digest("hex");

    const ref = db.collection("conversations").doc(conversationId);
    const rateLimitRef = db.collection("rateLimits").doc("conversation:" + request.auth.uid);
    const hourlyRateLimitRef = db.collection("rateLimits").doc("conversation-hour:" + request.auth.uid);
    const now = Date.now();

    await db.runTransaction(async (tx) => {
      const existing = await tx.get(ref);
      if (existing.exists) return;

      const [rateLimitSnap, hourlyRateSnap] = await Promise.all([
        tx.get(rateLimitRef),
        tx.get(hourlyRateLimitRef),
      ]);
      const rate = rateLimitSnap.exists ? rateLimitSnap.data()! : {};
      const hourlyRate = hourlyRateSnap.exists ? hourlyRateSnap.data()! : {};
      const windowStart = Number(rate.windowStartMs ?? 0);
      const count = Number(rate.count ?? 0);
      const hourWindowStart = Number(hourlyRate.windowStartMs ?? 0);
      const hourCount = Number(hourlyRate.count ?? 0);
      const activeWindow = Number.isSafeInteger(windowStart) && now - windowStart < 60_000;
      const activeHourWindow = Number.isSafeInteger(hourWindowStart) && now - hourWindowStart < 3_600_000;

      if (activeWindow && count >= 10) {
        throw new HttpsError("resource-exhausted", "Çok fazla yeni sohbet başlatıldı. Lütfen biraz sonra tekrar deneyin.");
      }
      if (activeHourWindow && hourCount >= 100) {
        throw new HttpsError("resource-exhausted", "Saatlik yeni sohbet kotanıza ulaştınız.");
      }

      tx.set(rateLimitRef, {
        windowStartMs: activeWindow ? windowStart : now,
        count: activeWindow ? count + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
      tx.set(hourlyRateLimitRef, {
        windowStartMs: activeHourWindow ? hourWindowStart : now,
        count: activeHourWindow ? hourCount + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      tx.create(ref, {
        participantIds,
        relatedItemId,
        relatedItemTitle,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    });

    return { conversationId };
  }
);

export const createQuote = onCall(
  { region: "europe-west1", enforceAppCheck: true },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    await assertAccountActive(request.auth.uid);

    const data = callableData(request.data);
    const quoteId = requireString(data, "quoteId", 80, 1);
    const requestId = requireString(data, "requestId", 80, 1);
    const providerId = requireString(data, "providerId", 120, 1);
    const price = requireString(data, "price", 200, 1);
    const durationOrArrival = requireString(data, "durationOrArrival", 200, 0);
    const notes = requireString(data, "notes", 1000, 0);
    const amountMinor = typeof data.amountMinor === "number" ? data.amountMinor : Number.NaN;

    if (!/^[A-Za-z0-9_-]{1,80}$/.test(quoteId) ||
        !/^[A-Za-z0-9_-]{1,80}$/.test(requestId) ||
        !providerId ||
        !Number.isSafeInteger(amountMinor) || amountMinor <= 0 || amountMinor > MAX_QUOTE_AMOUNT_MINOR ||
        parseTryAmountMinor(price) !== amountMinor || !ID_PATTERN.test(quoteId) || !ID_PATTERN.test(requestId)) {
      throw new HttpsError("invalid-argument", "Geçersiz teklif verisi.");
    }

    const uid = request.auth.uid;
    const providerRef = db.collection("providers").doc(providerId);
    const requestRef = db.collection("jobRequests").doc(requestId);
    const quoteRef = db.collection("quotes").doc(quoteId);
    const rateLimitRef = db.collection("rateLimits").doc("quote:" + uid);
    const hourlyRateLimitRef = db.collection("rateLimits").doc("quote-hour:" + uid);

    await db.runTransaction(async (tx) => {
      const [providerSnap, requestSnap, quoteSnap, rateSnap, hourlyRateSnap] = await Promise.all([
        tx.get(providerRef),
        tx.get(requestRef),
        tx.get(quoteRef),
        tx.get(rateLimitRef),
        tx.get(hourlyRateLimitRef),
      ]);

      if (!providerSnap.exists || providerSnap.data()?.ownerId !== uid) {
        throw new HttpsError("permission-denied", "Bu hizmet sağlayıcı adına teklif veremezsiniz.");
      }
      if (providerSnap.data()?.isOpenForOffers !== true) {
        throw new HttpsError("failed-precondition", "Hizmet sağlayıcı yeni tekliflere kapalı.");
      }
      if (!requestSnap.exists || requestSnap.data()?.status !== "PENDING") {
        throw new HttpsError("failed-precondition", "Talep artık yeni teklif kabul etmiyor.");
      }

      const customerId = String(requestSnap.data()?.ownerId ?? "");
      if (!customerId || customerId === uid) {
        throw new HttpsError("permission-denied", "Geçerli bir müşteri talebi bulunamadı.");
      }

      if (quoteSnap.exists) {
        throw new HttpsError("already-exists", "Bu teklif kimliği zaten kullanılmış.");
      }

      const rate = rateSnap.exists ? rateSnap.data()! : {};
      const hourlyRate = hourlyRateSnap.exists ? hourlyRateSnap.data()! : {};
      const windowStart = Number(rate.windowStartMs ?? 0);
      const count = Number(rate.count ?? 0);
      const hourWindowStart = Number(hourlyRate.windowStartMs ?? 0);
      const hourCount = Number(hourlyRate.count ?? 0);
      const now = Date.now();
      const activeWindow = Number.isSafeInteger(windowStart) && now - windowStart < 60_000;
      const activeHourWindow = Number.isSafeInteger(hourWindowStart) && now - hourWindowStart < 3_600_000;
      if (activeWindow && count >= 20) {
        throw new HttpsError("resource-exhausted", "Çok fazla teklif gönderildi. Lütfen biraz sonra tekrar deneyin.");
      }
      if (activeHourWindow && hourCount >= 200) {
        throw new HttpsError("resource-exhausted", "Saatlik teklif kotanıza ulaştınız.");
      }

      tx.set(rateLimitRef, {
        windowStartMs: activeWindow ? windowStart : now,
        count: activeWindow ? count + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
      tx.set(hourlyRateLimitRef, {
        windowStartMs: activeHourWindow ? hourWindowStart : now,
        count: activeHourWindow ? hourCount + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      tx.create(quoteRef, {
        providerId,
        providerOwnerId: request.auth!.uid,
        customerId,
        requestId,
        price,
        amountMinor,
        durationOrArrival,
        notes,
        status: "PENDING",
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    });

    return { created: true, quoteId };
  }
);

export const acceptQuote = onCall({ region: "europe-west1", enforceAppCheck: true }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    await assertAccountActive(request.auth.uid);
  const data = callableData(request.data);
  const quoteId = requireString(data, "quoteId", 80, 1);
  if (!ID_PATTERN.test(quoteId)) throw new HttpsError("invalid-argument", "Geçersiz teklif kimliği.");
  const quoteRef = db.collection("quotes").doc(quoteId);
  let acceptedRequestId = "";

  await db.runTransaction(async (tx) => {
    const quoteSnap = await tx.get(quoteRef);
    if (!quoteSnap.exists) throw new HttpsError("not-found", "Teklif bulunamadı.");
    const quote = quoteSnap.data()!;
    if (quote.customerId !== request.auth!.uid) {
      throw new HttpsError("permission-denied", "Bu teklifi yalnızca talep sahibi kabul edebilir.");
    }
    if (quote.status !== "PENDING") {
      throw new HttpsError("failed-precondition", "Teklif artık beklemede değil.");
    }

    acceptedRequestId = String(quote.requestId ?? "");
    if (!acceptedRequestId) {
      throw new HttpsError("failed-precondition", "Teklif talep bağlantısı eksik.");
    }

    const requestRef = db.collection("jobRequests").doc(acceptedRequestId);
    const requestSnap = await tx.get(requestRef);
    if (!requestSnap.exists || requestSnap.data()?.ownerId !== request.auth!.uid) {
      throw new HttpsError("permission-denied", "Talep doğrulanamadı.");
    }
    if (requestSnap.data()?.status !== "PENDING") {
      throw new HttpsError("failed-precondition", "Talep artık beklemede değil.");
    }

    tx.update(quoteRef, { status: "ACCEPTED", updatedAt: FieldValue.serverTimestamp() });
    tx.update(requestRef, { status: "ACCEPTED", updatedAt: FieldValue.serverTimestamp() });
  });

  const alternatives = await db.collection("quotes").where("requestId", "==", acceptedRequestId).get();
  const pendingAlternatives = alternatives.docs.filter(
    (doc) => doc.id !== quoteId && doc.data().status === "PENDING"
  );
  await commitInChunks(pendingAlternatives, (batch, doc) => {
    batch.update(doc.ref, {
      status: "REJECTED",
      updatedAt: FieldValue.serverTimestamp(),
    });
  });

  return { accepted: true, quoteId };
});

export const reportContent = onCall(
  { region: "europe-west1", enforceAppCheck: true },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    await assertAccountActive(request.auth.uid);

    const data = callableData(request.data);
    const targetType = requireString(data, "targetType", 20, 1);
    const targetId = requireString(data, "targetId", 120, 1);
    const reason = requireString(data, "reason", 500, 1).trim();

    if (!["PROVIDER", "JOB_REQUEST"].includes(targetType) || !ID_PATTERN.test(targetId) || !reason) {
      throw new HttpsError("invalid-argument", "Geçersiz şikayet bilgisi.");
    }

    const targetRef = targetType === "PROVIDER"
      ? db.collection("providers").doc(targetId)
      : db.collection("jobRequests").doc(targetId);
    const reportRef = db.collection("contentReports").doc(
      createHash("sha256")
        .update(request.auth.uid + ":" + targetType + ":" + targetId)
        .digest("hex")
    );
    const rateRef = db.collection("rateLimits").doc("report-day:" + request.auth.uid);

    await db.runTransaction(async (tx) => {
      const [targetSnap, existingReport, rateSnap] = await Promise.all([
        tx.get(targetRef),
        tx.get(reportRef),
        tx.get(rateRef),
      ]);

      if (!targetSnap.exists) {
        throw new HttpsError("not-found", "Şikayet edilecek içerik bulunamadı.");
      }

      const ownerId = String(targetSnap.data()?.ownerId ?? "");
      if (!ownerId || ownerId === request.auth!.uid) {
        throw new HttpsError("permission-denied", "Bu içeriği şikayet edemezsiniz.");
      }

      const rate = rateSnap.exists ? rateSnap.data()! : {};
      const windowStart = Number(rate.windowStartMs ?? 0);
      const count = Number(rate.count ?? 0);
      const now = Date.now();
      const activeWindow = Number.isSafeInteger(windowStart) && now - windowStart < 86_400_000;

      if (activeWindow && count >= 20) {
        throw new HttpsError("resource-exhausted", "Günlük şikayet kotanıza ulaştınız.");
      }

      tx.set(rateRef, {
        windowStartMs: activeWindow ? windowStart : now,
        count: activeWindow ? count + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      tx.set(reportRef, {
        reporterUid: request.auth!.uid,
        targetType,
        targetId,
        reason,
        status: existingReport.exists ? String(existingReport.data()?.status ?? "OPEN") : "OPEN",
        createdAt: existingReport.exists
          ? (existingReport.data()?.createdAt ?? FieldValue.serverTimestamp())
          : FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
    });

    return { reported: true };
  }
);

export const rejectQuote = onCall({ region: "europe-west1", enforceAppCheck: true }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    await assertAccountActive(request.auth.uid);
  const data = callableData(request.data);
  const quoteId = requireString(data, "quoteId", 80, 1);
  if (!ID_PATTERN.test(quoteId)) throw new HttpsError("invalid-argument", "Geçersiz teklif kimliği.");
  const quoteRef = db.collection("quotes").doc(quoteId);

  await db.runTransaction(async (tx) => {
    const quoteSnap = await tx.get(quoteRef);
    if (!quoteSnap.exists) throw new HttpsError("not-found", "Teklif bulunamadı.");
    const quote = quoteSnap.data()!;
    if (quote.customerId !== request.auth!.uid) {
      throw new HttpsError("permission-denied", "Bu işlemi yalnızca talep sahibi reddedebilir.");
    }
    if (quote.status !== "PENDING") {
      throw new HttpsError("failed-precondition", "Teklif artık beklemede değil.");
    }
    tx.update(quoteRef, { status: "REJECTED", updatedAt: FieldValue.serverTimestamp() });
  });

  return { rejected: true, quoteId };
});

export const releaseEscrowPayment = onCall({ region: "europe-west1", enforceAppCheck: true }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    await assertAccountActive(request.auth.uid);
    requireRecentAuthentication(request.auth.token.auth_time);
  const data = callableData(request.data);
  const paymentId = requireString(data, "paymentId", 64, 64);
  if (!/^[a-f0-9]{64}$/.test(paymentId)) {
    throw new HttpsError("invalid-argument", "Geçersiz ödeme kimliği.");
  }
  const paymentRef = db.collection("payments").doc(paymentId);

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(paymentRef);
    if (!snap.exists) throw new HttpsError("not-found", "Ödeme bulunamadı.");
    const payment = snap.data()!;
    if (payment.customerId !== request.auth!.uid) {
      throw new HttpsError("permission-denied", "Bu ödemeyi yalnızca müşteri serbest bırakabilir.");
    }
    if (!["PAID", "HELD"].includes(String(payment.status ?? ""))) {
      throw new HttpsError("failed-precondition", "Ödeme serbest bırakılabilir durumda değil.");
    }
    tx.update(paymentRef, { status: "RELEASE_REQUESTED", updatedAt: FieldValue.serverTimestamp() });
  });

  return { accepted: true, paymentId, status: "RELEASE_REQUESTED" };
});

export const getPaymentStatus = onCall(
  { region: "europe-west1", enforceAppCheck: true },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    await assertAccountActive(request.auth.uid);

    const data = callableData(request.data);
    const paymentId = requireString(data, "paymentId", 64, 64);
    if (!/^[a-f0-9]{64}$/.test(paymentId)) {
      throw new HttpsError("invalid-argument", "Geçersiz ödeme kimliği.");
    }

    const snap = await db.collection("payments").doc(paymentId).get();
    if (!snap.exists) throw new HttpsError("not-found", "Ödeme bulunamadı.");

    const payment = snap.data()!;
    const isCustomer = payment.customerId === request.auth.uid;
    // payments.providerId stores the provider owner's Firebase UID, not the provider document ID.
    const isProvider = payment.providerId === request.auth.uid;

    if (!isCustomer && !isProvider && request.auth.token.admin !== true) {
      throw new HttpsError("permission-denied", "Bu ödemenin durumunu görüntüleyemezsiniz.");
    }

    return {
      id: paymentId,
      status: String(payment.status ?? "UNKNOWN"),
      amountMinor: Number(payment.amountMinor ?? 0),
      currency: String(payment.currency ?? "TRY"),
      requestId: String(payment.requestId ?? ""),
      quoteId: String(payment.quoteId ?? ""),
      providerOrderId: String(payment.providerOrderId ?? ""),
      updatedAt: payment.updatedAt ?? null,
    };
  }
);

export const requestRefund = onCall(
  { region: "europe-west1", enforceAppCheck: true },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    await assertAccountActive(request.auth.uid);
    if (request.auth.token.admin !== true) {
      requireRecentAuthentication(request.auth.token.auth_time);
    }
    const data = callableData(request.data);
    const paymentId = requireString(data, "paymentId", 64, 64);
    if (!/^[a-f0-9]{64}$/.test(paymentId)) {
      throw new HttpsError("invalid-argument", "Geçersiz ödeme kimliği.");
    }
    const paymentRef = db.collection("payments").doc(paymentId);

    await db.runTransaction(async (tx) => {
      const snap = await tx.get(paymentRef);
      if (!snap.exists) throw new HttpsError("not-found", "Ödeme bulunamadı.");
      const payment = snap.data()!;
      const isAdmin = request.auth!.token.admin === true;
      if (!isAdmin && payment.customerId !== request.auth!.uid) {
        throw new HttpsError("permission-denied", "Bu ödeme için iade talebi açamazsınız.");
      }
      if (!["PAID", "HELD", "RELEASE_REQUESTED"].includes(String(payment.status ?? ""))) {
        throw new HttpsError("failed-precondition", "Ödeme iade için uygun durumda değil.");
      }
      tx.update(paymentRef, {
        status: "REFUND_REQUESTED",
        refundRequestedBy: request.auth!.uid,
        updatedAt: FieldValue.serverTimestamp(),
      });
    });

    return { accepted: true, paymentId, status: "REFUND_REQUESTED" };
  }
);


const ACCOUNT_DELETION_DELAY_MS = 30 * 24 * 60 * 60 * 1000;

function requireRecentAuthentication(authTimeSeconds: unknown) {
  const authTime = Number(authTimeSeconds ?? 0);
  const now = Date.now();
  const authTimeMs = authTime * 1000;
  const withinClockSkew = authTimeMs <= now + 60_000;
  const recentEnough = now - authTimeMs <= 15 * 60 * 1000;

  if (!Number.isSafeInteger(authTime) || authTime <= 0 || !withinClockSkew || !recentEnough) {
    throw new HttpsError("failed-precondition", "Bu güvenlik işlemi için yakın zamanda yeniden doğrulama gerekli.");
  }
}

function deletedAccountId(uid: string): string {
  return "deleted:" + createHash("sha256").update(uid).digest("hex").slice(0, 24);
}

async function commitInChunks<T>(
  items: T[],
  apply: (batch: FirebaseFirestore.WriteBatch, item: T) => void,
  chunkSize = 450
): Promise<void> {
  for (let i = 0; i < items.length; i += chunkSize) {
    const batch = db.batch();
    for (const item of items.slice(i, i + chunkSize)) {
      apply(batch, item);
    }
    await batch.commit();
  }
}

async function processQueryInPages(
  query: FirebaseFirestore.Query,
  processPage: (docs: FirebaseFirestore.QueryDocumentSnapshot[]) => Promise<void>
): Promise<void> {
  const pageSize = 450;
  let cursor: FirebaseFirestore.QueryDocumentSnapshot | undefined;

  while (true) {
    const page = cursor
      ? await query.startAfter(cursor).limit(pageSize).get()
      : await query.limit(pageSize).get();

    if (page.empty) return;
    await processPage(page.docs);
    if (page.size < pageSize) return;
    cursor = page.docs[page.docs.length - 1];
  }
}

async function anonymizeAccount(uid: string): Promise<void> {
  const anonymizedId = deletedAccountId(uid);
  const writer = db.bulkWriter();
  writer.onWriteError((error) => error.failedAttempts < 5);

  try {
    await processQueryInPages(
      db.collection("providers").where("ownerId", "==", uid),
      async (docs) => { for (const doc of docs) writer.delete(doc.ref); }
    );

    await processQueryInPages(
      db.collection("jobRequests").where("ownerId", "==", uid),
      async (docs) => {
        for (const doc of docs) {
          writer.set(doc.ref, {
            ownerId: anonymizedId,
            status: "CLOSED",
            accountDeletedAt: FieldValue.serverTimestamp(),
          }, { merge: true });
          writer.delete(db.collection("jobRequestPrivate").doc(doc.id));
        }
      }
    );

    await processQueryInPages(
      db.collection("quotes").where("customerId", "==", uid),
      async (docs) => {
        for (const doc of docs) {
          writer.set(doc.ref, {
            customerId: anonymizedId,
            accountDeletedAt: FieldValue.serverTimestamp(),
          }, { merge: true });
        }
      }
    );

    await processQueryInPages(
      db.collection("quotes").where("providerOwnerId", "==", uid),
      async (docs) => {
        for (const doc of docs) {
          writer.set(doc.ref, {
            providerOwnerId: anonymizedId,
            accountDeletedAt: FieldValue.serverTimestamp(),
          }, { merge: true });
        }
      }
    );

    await processQueryInPages(
      db.collection("payments").where("customerId", "==", uid),
      async (docs) => {
        for (const doc of docs) {
          writer.set(doc.ref, {
            customerId: anonymizedId,
            accountDeletedAt: FieldValue.serverTimestamp(),
          }, { merge: true });
        }
      }
    );

    await processQueryInPages(
      db.collection("payments").where("providerId", "==", uid),
      async (docs) => {
        for (const doc of docs) {
          writer.set(doc.ref, {
            providerId: anonymizedId,
            accountDeletedAt: FieldValue.serverTimestamp(),
          }, { merge: true });
        }
      }
    );

    await processQueryInPages(
      db.collection("contentReports").where("reporterUid", "==", uid),
      async (reports) => {
        for (const report of reports) {
          writer.set(report.ref, {
            reporterUid: anonymizedId,
            accountDeletedAt: FieldValue.serverTimestamp(),
          }, { merge: true });
        }
      }
    );

    await processQueryInPages(
      db.collection("conversations").where("participantIds", "array-contains", uid),
      async (conversations) => {
        for (const conversation of conversations) {
          const participantIds = (conversation.data().participantIds as unknown[])
            .map((id) => id === uid ? anonymizedId : id);

          writer.set(conversation.ref, {
            participantIds,
            accountDeletedAt: FieldValue.serverTimestamp(),
          }, { merge: true });

          await processQueryInPages(
            db.collection("messages").where("conversationId", "==", conversation.id),
            async (messages) => {
              for (const message of messages) {
                if (message.data().senderId === uid) {
                  writer.set(message.ref, {
                    senderId: anonymizedId,
                    accountDeletedAt: FieldValue.serverTimestamp(),
                  }, { merge: true });
                }
              }
            }
          );
        }
      }
    );

    await writer.close();

    await processQueryInPages(
      db.collection("deviceTokenOwners").where("uid", "==", uid),
      async (owners) => {
        const batch = db.batch();
        for (const owner of owners) batch.delete(owner.ref);
        await batch.commit();
      }
    );

  const devicesSnapshot = await db.collection("users").doc(uid).collection("devices").get();
  const cleanupBatch = db.batch();
  for (const device of devicesSnapshot.docs) {
    cleanupBatch.delete(db.collection("deviceTokenOwners").doc(device.id));
  }

  const rateLimitIds = [
    "payment-intent:" + uid,
    "payment-intent-hour:" + uid,
    "provider-write-day:" + uid,
    "request-write-day:" + uid,
    "storage-grant-hour:" + uid,
    "device-register:" + uid,
    "message:" + uid,
    "message-hour:" + uid,
    "conversation:" + uid,
    "conversation-hour:" + uid,
    "quote:" + uid,
    "quote-hour:" + uid,
    "report-day:" + uid,
    "availability:" + uid,
    "availability-minute:" + uid,
    "availability-hour:" + uid,
    "conversation-read:" + uid,
    "conversation-read-hour:" + uid,
  ];
  for (const rateLimitId of rateLimitIds) {
    cleanupBatch.delete(db.collection("rateLimits").doc(rateLimitId));
  }
  await cleanupBatch.commit();

  const bucket = getStorage().bucket();
  const grantsRef = db.collection("users").doc(uid).collection("uploadGrants");
  await processQueryInPages(grantsRef, async (grants) => {
    await Promise.all(grants.map(async (grant) => {
      const data = grant.data();
      const grantId = grant.id;
      const kind = String(data.kind ?? "");
      let objectPath = "";

      if (kind === "USER") {
        objectPath = "users/" + uid + "/images/" + grantId + ".jpg";
      } else if (
        kind === "JOB_REQUEST"
        && typeof data.requestId === "string"
        && ID_PATTERN.test(data.requestId)
      ) {
        objectPath = "jobRequests/" + uid + "/" + data.requestId + "/images/" + grantId + ".jpg";
      } else if (
        kind === "CHAT"
        && typeof data.conversationId === "string"
        && /^[a-f0-9]{64}$/.test(data.conversationId)
      ) {
        objectPath = "chatAttachments/" + data.conversationId + "/" + uid + "/" + grantId + ".jpg";
      }

      if (!objectPath) return;
      await bucket.file(objectPath).delete().catch((error: { code?: number }) => {
        if (error.code !== 404) throw error;
      });
    }));
  });

  // Defensive catch-all for any legacy user-scoped media not represented by a grant.
  await bucket.deleteFiles({ prefix: "users/" + uid + "/" });

  } catch (error) {
    await writer.close().catch(() => undefined);
    throw error;
  }

  await db.recursiveDelete(db.collection("users").doc(uid));
  await adminAuth.deleteUser(uid).catch((error: { code?: string }) => {
    if (error.code !== "auth/user-not-found") throw error;
  });
}
export const requestAccountDeletion = onCall(
  { region: "europe-west1", enforceAppCheck: true },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    await getUsableAuthUser(request.auth.uid);
    requireRecentAuthentication(request.auth.token.auth_time);

    const ref = db.collection("users").doc(request.auth.uid);
    let alreadyRequested = false;

    await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      const status = snap.exists ? String(snap.data()?.deletionStatus ?? "ACTIVE") : "ACTIVE";

      if (status === "REQUESTED") {
        alreadyRequested = true;
        return;
      }
      if (status === "PURGING") {
        throw new HttpsError("failed-precondition", "Hesap silme işlemi devam ediyor.");
      }
      if (status !== "ACTIVE") {
        throw new HttpsError("failed-precondition", "Hesap silme durumu geçersiz.");
      }

      tx.set(ref, {
        deletionStatus: "REQUESTED",
        deletionRequestedAt: FieldValue.serverTimestamp(),
        deletionDueAt: new Date(Date.now() + ACCOUNT_DELETION_DELAY_MS),
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
    });

    return { requested: true, alreadyRequested, dueInDays: 30 };
  }
);

export const cancelAccountDeletion = onCall(
  { region: "europe-west1", enforceAppCheck: true },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    await getUsableAuthUser(request.auth.uid);
    requireRecentAuthentication(request.auth.token.auth_time);

    const ref = db.collection("users").doc(request.auth.uid);
    let canceled = false;

    await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists) return;

      const status = String(snap.data()?.deletionStatus ?? "ACTIVE");
      if (status !== "REQUESTED") {
        if (status === "PURGING") {
          throw new HttpsError("failed-precondition", "Hesap silme işlemi başladı ve artık iptal edilemez.");
        }
        return;
      }

      const dueAt = snap.data()?.deletionDueAt as FirestoreTimestamp | Date | undefined;
      const dueMillis = dueAt instanceof Date
        ? dueAt.getTime()
        : dueAt && "toMillis" in dueAt
          ? dueAt.toMillis()
          : Number.NaN;

      if (!Number.isFinite(dueMillis)) {
        throw new HttpsError(
          "failed-precondition",
          "Hesap silme zamanlayıcısı geçersiz; işlem güvenlik nedeniyle durduruldu."
        );
      }

      if (dueMillis <= Date.now()) {
        throw new HttpsError(
          "failed-precondition",
          "Hesap silme süresi dolduğu için silme işlemi artık iptal edilemez."
        );
      }

      tx.set(ref, {
        deletionStatus: "ACTIVE",
        deletionCanceledAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
      canceled = true;
    });

    return { canceled, alreadyActive: !canceled };
  }
);
export const purgeDeletedAccounts = onSchedule(
  { schedule: "every day 03:15", region: "europe-west1", timeZone: "Europe/Istanbul" },
  async () => {
    const now = Date.now();
    const staleCutoff = new Date(now - 60 * 60 * 1000);

    const requestedSnapshot = await db.collection("users")
      .where("deletionStatus", "==", "REQUESTED")
      .where("deletionDueAt", "<=", new Date(now))
      .orderBy("deletionDueAt")
      .limit(20)
      .get();

    const stalePurgingSnapshot = await db.collection("users")
      .where("deletionStatus", "==", "PURGING")
      .where("purgeStartedAt", "<=", staleCutoff)
      .orderBy("purgeStartedAt")
      .limit(20)
      .get();

    const candidates = new Map<string, FirebaseFirestore.QueryDocumentSnapshot>();
    for (const doc of requestedSnapshot.docs) candidates.set(doc.id, doc);
    for (const doc of stalePurgingSnapshot.docs) candidates.set(doc.id, doc);

    for (const doc of candidates.values()) {
      const ref = doc.ref;
      let shouldPurge = false;

      await db.runTransaction(async (tx) => {
        const fresh = await tx.get(ref);
        if (!fresh.exists) return;

        const data = fresh.data() ?? {};
        const status = String(data.deletionStatus ?? "");

        if (status === "REQUESTED") {
          const dueAt = data.deletionDueAt as FirestoreTimestamp | Date | undefined;
          const dueMillis = dueAt instanceof Date
            ? dueAt.getTime()
            : dueAt && "toMillis" in dueAt
              ? dueAt.toMillis()
              : Number.POSITIVE_INFINITY;

          if (dueMillis <= now) {
            tx.update(ref, {
              deletionStatus: "PURGING",
              purgeStartedAt: FieldValue.serverTimestamp(),
              purgeLastErrorAt: FieldValue.delete(),
              updatedAt: FieldValue.serverTimestamp(),
            });
            shouldPurge = true;
          }
          return;
        }

        if (status !== "PURGING") return;

        const startedAt = data.purgeStartedAt as FirestoreTimestamp | Date | undefined;
        const startedMillis = startedAt instanceof Date
          ? startedAt.getTime()
          : startedAt && "toMillis" in startedAt
            ? startedAt.toMillis()
            : Number.POSITIVE_INFINITY;

        if (startedMillis <= now - 60 * 60 * 1000) {
          const retryCount = Number(data.purgeRetryCount ?? 0);
          tx.update(ref, {
            purgeStartedAt: FieldValue.serverTimestamp(),
            purgeRetryCount: Number.isSafeInteger(retryCount) ? retryCount + 1 : 1,
            updatedAt: FieldValue.serverTimestamp(),
          });
          shouldPurge = true;
        }
      });

      if (!shouldPurge) continue;

      try {
        await anonymizeAccount(doc.id);
      } catch (error) {
        logger.error("Account purge failed; account remains locked in PURGING for retry", {
          uid: doc.id,
          error,
        });
        await ref.set({
          deletionStatus: "PURGING",
          purgeLastErrorAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
        }, { merge: true });
      }
    }
  }
);
export const paytrWebhook = onRequest(
  { region: "europe-west1", secrets: [paytrKey, paytrSalt] },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).send("");
      return;
    }

    const merchantOid = String(req.body?.merchant_oid ?? "");
    const status = String(req.body?.status ?? "");
    const totalAmount = String(req.body?.total_amount ?? "");
    const receivedHash = String(req.body?.hash ?? "");

    if (!merchantOid || !["success", "failed"].includes(status) || !/^\d+$/.test(totalAmount)) {
      res.status(400).send("");
      return;
    }

    const valid = verifyPaytrCallback(
      paytrKey.value(),
      merchantOid,
      paytrSalt.value(),
      status,
      totalAmount,
      receivedHash
    );

    if (!valid) {
      logger.warn("Rejected PayTR webhook with invalid signature", { merchantOid });
      res.status(400).send("");
      return;
    }

    const receivedTotalMinor = Number(totalAmount);
    if (!Number.isSafeInteger(receivedTotalMinor) || receivedTotalMinor <= 0) {
      res.status(400).send("");
      return;
    }

    const paymentQuery = await db
      .collection("payments")
      .where("providerOrderId", "==", merchantOid)
      .limit(1)
      .get();

    if (!paymentQuery.empty) {
      const paymentRef = paymentQuery.docs[0].ref;

      await db.runTransaction(async (tx) => {
        const paymentSnap = await tx.get(paymentRef);
        if (!paymentSnap.exists) return;

        const payment = paymentSnap.data()!;
        if (payment.webhookProcessedAt) return;

        const expectedMinor = Number(payment.amountMinor ?? 0);
        if (!Number.isSafeInteger(expectedMinor) || expectedMinor <= 0) {
          logger.error("PayTR webhook ignored: invalid stored payment amount", { merchantOid });
          return;
        }

        if (receivedTotalMinor !== expectedMinor) {
          logger.error("PayTR webhook ignored: callback amount does not match order amount", {
            merchantOid,
            expectedMinor,
            receivedTotalMinor,
          });
          return;
        }

        const currentStatus = String(payment.status ?? "");
        const callbackEligibleStatuses = ["CREATED", "PENDING"];
        if (!callbackEligibleStatuses.includes(currentStatus)) {
          return;
        }

        tx.set(
          paymentRef,
          {
            status: status === "success" ? "PAID" : "FAILED",
            providerStatus: status,
            providerTotalMinor: receivedTotalMinor,
            webhookProcessedAt: FieldValue.serverTimestamp(),
            updatedAt: FieldValue.serverTimestamp(),
          },
          { merge: true }
        );
      });
    }

    res.status(200).send("OK");
  }
);

export const syncPublicProvider = onDocumentWritten(
  { document: "providers/{providerId}", region: "europe-west1" },
  async (event) => {
    const providerRef = db.collection("publicProviders").doc(event.params.providerId);
    const provider = event.data?.after.data();
    if (!event.data?.after.exists || !provider) {
      await providerRef.delete().catch(() => undefined);
      return;
    }

    await providerRef.set({
      displayName: provider.displayName ?? "",
      title: provider.title ?? "",
      bio: provider.bio ?? "",
      sector: provider.sector ?? "",
      categoryId: provider.categoryId ?? "",
      district: provider.district ?? "",
      city: provider.city ?? "",
      hourlyOrBasePrice: provider.hourlyOrBasePrice ?? "Anlaşmaya Bağlı",
      isEmergencyAvailable: provider.isEmergencyAvailable === true,
      experienceYears: provider.experienceYears ?? 0,
      paintBrandsJson: typeof provider.paintBrandsJson === "string"
        ? provider.paintBrandsJson.slice(0, 2000)
        : "",
      charactersOfferedJson: typeof provider.charactersOfferedJson === "string"
        ? provider.charactersOfferedJson.slice(0, 2000)
        : "",
      includedEquipmentsJson: typeof provider.includedEquipmentsJson === "string"
        ? provider.includedEquipmentsJson.slice(0, 2000)
        : "",
      serviceArea: (() => {
        const area = provider.serviceArea;
        if (!area || typeof area !== "object") return null;
        const latitude = Number((area as { latitude?: unknown }).latitude);
        const longitude = Number((area as { longitude?: unknown }).longitude);
        if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null;
        return {
          // Public discovery exposes only an approximate area (~1km grid), not exact coordinates.
          latitude: Math.round(latitude * 100) / 100,
          longitude: Math.round(longitude * 100) / 100,
        };
      })(),
      isOpenForOffers: provider.isOpenForOffers === true,
      bookedDates: Array.isArray(provider.bookedDates)
        ? provider.bookedDates.filter((value: unknown): value is string =>
            typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value)
          ).slice(0, 366)
        : [],
      createdAt: provider.createdAt ?? FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: false });
  }
);

export const syncPublicJobRequest = onDocumentWritten(
  { document: "jobRequests/{requestId}", region: "europe-west1" },
  async (event) => {
    const requestRef = db.collection("publicJobRequests").doc(event.params.requestId);
    const jobRequest = event.data?.after.data();
    if (
      !event.data?.after.exists
      || !jobRequest
      || String(jobRequest.status ?? "") === "CLOSED"
      || String(jobRequest.ownerId ?? "").startsWith("deleted:")
    ) {
      await requestRef.delete().catch(() => undefined);
      return;
    }

    await requestRef.set({
      title: jobRequest.title ?? "",
      sector: jobRequest.sector ?? "",
      categoryId: jobRequest.categoryId ?? "",
      district: jobRequest.district ?? "",
      urgencyMode: jobRequest.urgencyMode ?? "",
      eventOrJobDate: jobRequest.eventOrJobDate ?? "",
      eventTime: jobRequest.eventTime ?? "",
      budgetEstimate: jobRequest.budgetEstimate ?? "",
      status: jobRequest.status ?? "PENDING",
      createdAt: jobRequest.createdAt ?? FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: false });
  }
);

export const validateUploadedImage = onObjectFinalized(
  { region: "europe-west1" },
  async (event) => {
    const object = event.data;
    const name = String(object.name ?? "");
    const contentType = String(object.contentType ?? "");
    const size = Number(object.size ?? 0);

    const parts = name.split("/");
    let uid = "";
    let grantId = "";

    if (parts.length === 4 && parts[0] === "users" && parts[2] === "images") {
      uid = parts[1] ?? "";
      grantId = (parts[3] ?? "").endsWith(".jpg") ? (parts[3] ?? "").slice(0, -4) : "";
    } else if (
      parts.length === 6
      && parts[0] === "jobRequests"
      && parts[3] === "images"
    ) {
      uid = parts[1] ?? "";
      grantId = (parts[5] ?? "").endsWith(".jpg") ? (parts[5] ?? "").slice(0, -4) : "";
    } else if (parts.length === 4 && parts[0] === "chatAttachments") {
      uid = parts[2] ?? "";
      grantId = (parts[3] ?? "").endsWith(".jpg") ? (parts[3] ?? "").slice(0, -4) : "";
    }

    if (!uid || !/^[A-Za-z0-9_-]{1,120}$/.test(grantId)) return;

    const grantRef = db.collection("users").doc(uid).collection("uploadGrants").doc(grantId);
    const grantSnap = await grantRef.get();
    if (!grantSnap.exists) return;

    const grant = grantSnap.data() ?? {};
    const expiresAt = grant.expiresAt as FirestoreTimestamp | Date | undefined;
    const expiresMillis = expiresAt instanceof Date
      ? expiresAt.getTime()
      : expiresAt && "toMillis" in expiresAt
        ? expiresAt.toMillis()
        : Number.NaN;

    const file = getStorage().bucket(object.bucket).file(name);
    let valid = Number.isFinite(expiresMillis) && expiresMillis > Date.now()
      && size > 0 && size <= 5 * 1024 * 1024
      && ["image/jpeg", "image/png", "image/webp"].includes(contentType);

    if (valid) {
      try {
        const [bytes] = await file.download({ start: 0, end: 15 });
        const jpeg = bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
        const png = bytes.length >= 8 && bytes.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]));
        const webp = bytes.length >= 12
          && bytes.subarray(0, 4).toString("ascii") === "RIFF"
          && bytes.subarray(8, 12).toString("ascii") === "WEBP";
        valid = (contentType === "image/jpeg" && jpeg)
          || (contentType === "image/png" && png)
          || (contentType === "image/webp" && webp);
      } catch (error) {
        logger.error("Image validation failed", { name, error });
        valid = false;
      }
    }

    if (!valid) {
      await file.delete().catch(() => undefined);
      await grantRef.set({ validated: false, rejectedAt: FieldValue.serverTimestamp() }, { merge: true });
      return;
    }

    await grantRef.set({ validated: true, validatedAt: FieldValue.serverTimestamp() }, { merge: true });
  }
);
export const notifyNewMessage = onDocumentCreated(
  { document: "messages/{messageId}", region: "europe-west1" },
  async (event) => {
    const message = event.data?.data();
    if (!message) return;
    const conversationId = String(message.conversationId ?? "");
    const senderId = String(message.senderId ?? "");
    if (!conversationId || !senderId) return;

    const conversationSnap = await db.collection("conversations").doc(conversationId).get();
    if (!conversationSnap.exists) return;

    const participantIds = conversationSnap.data()?.participantIds;
    if (!Array.isArray(participantIds)) return;

    const recipientIds = participantIds.filter((id: unknown) =>
      typeof id === "string" && id !== senderId
    ) as string[];

    if (recipientIds.length === 0) return;

    const messageCreatedAt = event.data?.data()?.createdAt;
    const candidateRegistrations = await Promise.all(
      recipientIds.map(async (uid) => {
        const [profileSnap, tokenSnap, stateSnap] = await Promise.all([
          db.collection("users").doc(uid).get(),
          db.collection("users").doc(uid).collection("devices").get(),
          db.collection("users").doc(uid).collection("conversationState").doc(conversationId).get(),
        ]);

        if (
          !profileSnap.exists
          || ["REQUESTED", "PURGING"].includes(String(profileSnap.data()?.deletionStatus ?? ""))
        ) {
          return [];
        }

        const preferences = profileSnap.data()?.notificationPreferences;
        const messagesEnabled = preferences == null || preferences.messages !== false;
        if (!messagesEnabled) return [];

        const lastReadAt = stateSnap.data()?.lastReadAt;
        if (messageCreatedAt && lastReadAt) {
          const messageMillis = typeof messageCreatedAt?.toMillis === "function"
            ? messageCreatedAt.toMillis()
            : Number(messageCreatedAt);
          const readMillis = typeof lastReadAt?.toMillis === "function"
            ? lastReadAt.toMillis()
            : Number(lastReadAt);
          if (Number.isFinite(messageMillis) && Number.isFinite(readMillis) && readMillis >= messageMillis) {
            return [];
          }
        }

        return tokenSnap.docs
          .map((doc) => ({ ref: doc.ref, token: String(doc.data()?.token ?? "") }))
          .filter((entry) => entry.token.length > 0);
      })
    );

    const registrations = candidateRegistrations.flat();
    if (registrations.length === 0) return;

    const payload = {
      notification: {
        title: "Mahallem'den yeni mesaj",
        body: "Yeni bir mesajınız var.",
      },
      data: {
        conversationId,
      },
    };

    for (let i = 0; i < registrations.length; i += 500) {
      const batch = registrations.slice(i, i + 500);
      const response = await getMessaging().sendEachForMulticast({
        tokens: batch.map((entry) => entry.token),
        ...payload,
      });

      const invalidRefs = response.responses
        .map((result, index) => ({ result, ref: batch[index].ref }))
        .filter(({ result }) =>
          result.error?.code === "messaging/registration-token-not-registered"
          || result.error?.code === "messaging/invalid-registration-token"
        )
        .map(({ ref }) => ref);

      await Promise.all(invalidRefs.map((ref) => ref.delete()));
    }
  }
);