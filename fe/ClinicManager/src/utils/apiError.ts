export const MFA_SESSION_EXPIRED = "MFA_SESSION_EXPIRED";

export const apiErrorCode = (error: any): string | null => {
  const code = error?.response?.data?.code;
  return typeof code === "string" ? code : null;
};

export const apiErrorMessage = (error: any): string | null => {
  if (error?.response?.status === 429) {
    const message = error.response.data;
    return typeof message === "string" && message.trim() && !message.trim().startsWith("<")
      ? message
      : "Zbyt wiele prób. Spróbuj ponownie za chwilę.";
  }
  const data = error?.response?.data;
  if (typeof data === "string" && data.trim()) return data;
  if (data && typeof data.message === "string" && data.message.trim()) return data.message;
  return null;
};
