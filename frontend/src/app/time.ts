/** Hours and minutes are integers. Always use integer minutes at the API boundary. */
export function toMinutes(hours: number | null, minutes: number | null): number {
  if (hours === null || minutes === null || !Number.isInteger(hours) || !Number.isInteger(minutes) || hours < 0 || minutes < 0 || minutes > 59) throw new Error('Escribe horas enteras positivas o cero y minutos entre 0 y 59.');
  const total = hours * 60 + minutes;
  if (!Number.isSafeInteger(total) || total > 1000000000) throw new Error('La cantidad de tiempo excede el límite permitido.');
  return total;
}
export function formatTime(minutes: number): string {
  return `${Math.floor(minutes / 60)} h ${minutes % 60} min`;
}
export function passwordError(value: string): string | null {
  if (Array.from(value).length < 12) return 'La contraseña debe tener al menos 12 caracteres.';
  if (new TextEncoder().encode(value).length > 72) return 'La contraseña excede 72 bytes; usa menos caracteres, especialmente si incluyes acentos o emojis.';
  return null;
}
