import { useEffect, useRef } from "react";
import PinInput from "../PinInput";
import TextInput from "../TextInput";
import { MFA_CODE_LENGTH } from "../../models/mfa";
import { StepUp } from "../../models/access-control";

export interface StepUpFieldProps {
  mfaEnabled: boolean;
  value: string;
  onChange: (value: string) => void;
  onSubmit?: () => void;
  stacked?: boolean;
  autoFocus?: boolean;
  className?: string;
}

export const toStepUp = (mfaEnabled: boolean, value: string): StepUp =>
  mfaEnabled ? { code: value } : { password: value };

export const stepUpError = (mfaEnabled: boolean, value: string): string | null => {
  if (mfaEnabled) return value.length === MFA_CODE_LENGTH ? null : "Wpisz 6-cyfrowy kod z aplikacji.";
  return value ? null : "Podaj hasło.";
};

export function StepUpField({
  mfaEnabled,
  value,
  onChange,
  onSubmit,
  stacked = false,
  autoFocus = false,
  className = "",
}: StepUpFieldProps) {
  const ref = useRef<HTMLDivElement>(null);
  const label = mfaEnabled ? "Kod 2FA:" : "Hasło";

  useEffect(() => {
    if (autoFocus) ref.current?.querySelector("input")?.focus();
  }, [autoFocus, mfaEnabled]);

  return (
    <div
      ref={ref}
      className={`ag-stepup flex g-1 ${stacked ? "flex-column align-items-center" : "align-items-center flex-wrap"} ${className}`}
    >
      <span className="pf-label ag-stepup-label">{stacked ? label : `${label}:`}</span>
      {mfaEnabled ? (
        <PinInput value={value} onChange={onChange} length={MFA_CODE_LENGTH} masked={false} revealable={false} onSubmit={onSubmit} />
      ) : (
        <TextInput
          className="in-md"
          password={true}
          value={value}
          onSelect={(next) => {
            if (typeof next === "string") onChange(next);
          }}
        />
      )}
    </div>
  );
}

export default StepUpField;
