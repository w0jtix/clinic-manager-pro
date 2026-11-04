export const CODE_LIFETIME_SECONDS = 15 * 60;
export const CODE_LOW_SECONDS = 2 * 60;
export const CODE_MID_SECONDS = 5 * 60;
export const CODE_POLL_MS = 3000;

export const progressLevel = (seconds: number): string =>
  seconds <= CODE_LOW_SECONDS ? "low" : seconds <= CODE_MID_SECONDS ? "mid" : "";

export const secondsLeft = (expiresAt: string, now: number): number =>
  Math.max(0, Math.floor((new Date(expiresAt).getTime() - now) / 1000));

export const formatCountdown = (seconds: number): string => {
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return `${minutes}:${String(rest).padStart(2, "0")}`;
};
