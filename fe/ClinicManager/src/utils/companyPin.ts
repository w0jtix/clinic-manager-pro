export const COMPANY_PIN_LENGTH = 6;

const isSequence = (pin: string, step: number): boolean => {
  for (let i = 1; i < pin.length; i++) {
    if (Number(pin[i]) - Number(pin[i - 1]) !== step) return false;
  }
  return true;
};

export const validateCompanyPin = (pin: string): string | null => {
  if (!new RegExp(`^\\d{${COMPANY_PIN_LENGTH}}$`).test(pin)) {
    return "PIN musi składać się dokładnie z 6 cyfr.";
  }
  if (/^(\d)\1+$/.test(pin)) {
    return "PIN nie może składać się z samych jednakowych cyfr.";
  }
  if (isSequence(pin, 1) || isSequence(pin, -1)) {
    return "PIN nie może być ciągiem kolejnych cyfr (np. 123456 lub 654321).";
  }
  return null;
};

export const validateNewCompanyPin = (pin: string, confirmPin: string): string | null => {
  const error = validateCompanyPin(pin);
  if (error) return error;
  if (pin !== confirmPin) return "PIN-y nie są identyczne.";
  return null;
};
