export interface RegulationsSegmentProps {
  value: boolean;
  onChange: (value: boolean) => void;
  disabled?: boolean;
}

export function RegulationsSegment({ value, onChange, disabled = false }: RegulationsSegmentProps) {
  return (
    <div className="cat-seg-group flex align-items-center width-fit-content g-025">
      <span className="cat-seg-label">Regulamin podpisany:</span>
      <button
        type="button"
        disabled={disabled}
        className={`cat-seg cl-no align-items-center pointer ${!value ? "active" : ""}`}
        onClick={() => onChange(false)}
      >
        NIE
      </button>
      <button
        type="button"
        disabled={disabled}
        className={`cat-seg cl-yes align-items-center pointer ${value ? "active" : ""}`}
        onClick={() => onChange(true)}
      >
        TAK
      </button>
    </div>
  );
}

export default RegulationsSegment;
