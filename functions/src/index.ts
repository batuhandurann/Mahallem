
export const sendMessage = onCall(
  { region: "europe-west1", enforceAppCheck: true },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    }
    await assertAccountActive(request.auth.uid);

    const data = request.data as Record<string, unknown>;
    const conversationId = String(data.conversationId ?? "");
    const text = String(data.text ?? "");
    const messageType = String(data.messageType ?? "TEXT");
    const attachmentUrl = data.attachmentUrl == null ? null : String(data.attachmentUrl);

    if (!conversationId) {
      throw new HttpsError("invalid-argument", "conversationId gerekli.");
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
      if (attachmentUrl.includes("..") || attachmentUrl.includes("?") || attachmentUrl.includes("#")) {
        throw new HttpsError("invalid-argument", "Geçersiz medya yolu.");
      }

      const allowedPrefixes = [
        "chatAttachments/" + conversationId + "/" + request.auth.uid + "/",
      ];
      if (!allowedPrefixes.some((prefix) => attachmentUrl.startsWith(prefix))) {
        throw new HttpsError("permission-denied", "Bu medya dosyasına mesajda erişim yetkiniz yok.");
      }