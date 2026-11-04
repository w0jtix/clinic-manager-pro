import WheelDateField from "../WheelDateField";

export interface BirthDateFieldProps {
  value: string | null;
  onChange: (value: string | null) => void;
  disabled?: boolean;
  lightPortal?: boolean;
}

export function BirthDateField({ value, onChange, disabled = false, lightPortal = false }: BirthDateFieldProps) {
  return (
    <WheelDateField
      value={value}
      onChange={onChange}
      title="Data urodzenia"
      placeholder="Dodaj"
      disabled={disabled}
      clearable={true}
      maxDate={new Date()}
      defaultYear={new Date().getFullYear() - 40}
      className="cc-birth-btn"
      lightPortal={lightPortal}
    />
  );
}

export default BirthDateField;
