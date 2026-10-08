export function validateRequestSchedule(title: string, date: string, time: string): void {
  if (!title.trim() || title.trim().length > 120) throw new Error("Geçersiz talep başlığı.");
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) throw new Error("Geçersiz hizmet/talep tarihi.");
  const [year, month, day] = date.split("-").map(Number);
  const parsed = new Date(Date.UTC(year, month - 1, day));
  if (parsed.getUTCFullYear() !== year || parsed.getUTCMonth() !== month - 1 || parsed.getUTCDate() !== day) {
    throw new Error("Geçersiz hizmet/talep tarihi.");
  }
  if (!/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(time)) throw new Error("Geçersiz hizmet/talep saati.");
}
