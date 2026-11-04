export const backupCodesLeftMessage = (left: number): string => {
  if (left <= 0) return "Nie masz już kodów zapasowych. Wygeneruj nowe w Profilu.";
  if (left === 1) return "Został 1 kod zapasowy. Wygeneruj nowe w Profilu.";
  return `Zostały ${left} kody zapasowe. Wygeneruj nowe w Profilu.`;
};

export const formatSecret = (secret: string): string =>
  secret.replace(/\s/g, "").replace(/(.{4})/g, "$1 ").trim();
