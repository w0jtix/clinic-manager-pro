import { Fragment, useEffect, useRef, useState } from "react";
import { Eye, EyeOff } from "lucide-react";
import ActionButton from "./ActionButton";

export interface PinInputProps {
  value: string;
  onChange: (value: string) => void;
  length?: number;
  masked?: boolean;
  revealable?: boolean;
  autoFocus?: boolean;
  disabled?: boolean;
  onSubmit?: () => void;
  alphanumeric?: boolean;
  groupSize?: number;
  className?: string;
}

export function PinInput({
  value,
  onChange,
  length = 6,
  masked = true,
  revealable = true,
  autoFocus = false,
  disabled = false,
  onSubmit,
  alphanumeric = false,
  groupSize,
  className = "",
}: PinInputProps) {
  const sanitize = (raw: string) =>
    alphanumeric ? raw.replace(/[^a-z0-9]/gi, "").toUpperCase() : raw.replace(/\D/g, "");
  const refs = useRef<(HTMLInputElement | null)[]>([]);
  const [revealed, setRevealed] = useState<boolean>(false);
  const [digits, setDigits] = useState<string[]>(() =>
    Array.from({ length }, (_, i) => value[i] ?? "")
  );

  useEffect(() => {
    if (value !== digits.join("")) {
      setDigits(Array.from({ length }, (_, i) => value[i] ?? ""));
      const focusedInside = refs.current.some((el) => el !== null && el === document.activeElement);
      if (value === "" && (autoFocus || focusedInside)) {
        refs.current[0]?.focus();
      }
    }
  }, [value, length]);

  const commit = (next: string[]) => {
    setDigits(next);
    onChange(next.join(""));
  };

  const focusAt = (index: number) => {
    const target = refs.current[Math.max(0, Math.min(length - 1, index))];
    target?.focus();
    target?.select();
  };

  const setDigit = (index: number, digit: string) => {
    const next = digits.slice();
    next[index] = digit;
    commit(next);
  };

  const handleChange = (index: number, raw: string) => {
    const clean = sanitize(raw);
    if (!clean) {
      setDigit(index, "");
      return;
    }
    if (clean.length === 2 && digits[index]) {
      setDigit(index, clean[clean.length - 1]);
      focusAt(index + 1);
      return;
    }
    if (clean.length > 1) {
      const next = digits.slice();
      clean.split("").forEach((d, offset) => {
        if (index + offset < length) next[index + offset] = d;
      });
      commit(next);
      focusAt(index + clean.length);
      return;
    }
    setDigit(index, clean);
    focusAt(index + 1);
  };

  const handleKeyDown = (index: number, event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter" && onSubmit) {
      event.preventDefault();
      onSubmit();
    } else if (event.key === "Backspace" && !digits[index] && index > 0) {
      event.preventDefault();
      setDigit(index - 1, "");
      focusAt(index - 1);
    } else if (event.key === "ArrowLeft") {
      event.preventDefault();
      focusAt(index - 1);
    } else if (event.key === "ArrowRight") {
      event.preventDefault();
      focusAt(index + 1);
    }
  };

  const handlePaste = (index: number, event: React.ClipboardEvent<HTMLInputElement>) => {
    const pasted = sanitize(event.clipboardData.getData("text"));
    if (!pasted) return;
    event.preventDefault();
    handleChange(index, pasted);
  };

  return (
    <div className={`pin-boxes flex align-items-center g-05 ${className}`}>
      {digits.map((digit, index) => (
        <Fragment key={index}>
          <input
            ref={(el) => {
              refs.current[index] = el;
            }}
            className="pin-box"
            type="text"
            inputMode={alphanumeric ? "text" : "numeric"}
            autoComplete="off"
            autoCorrect="off"
            autoCapitalize={alphanumeric ? "characters" : "off"}
            spellCheck={false}
            maxLength={length}
            value={masked && !revealed && digit ? "•" : digit}
            disabled={disabled}
            autoFocus={autoFocus && index === 0}
            aria-label={`${alphanumeric ? "Znak" : "Cyfra"} ${index + 1} z ${length}`}
            onChange={(e) => handleChange(index, e.target.value)}
            onKeyDown={(e) => handleKeyDown(index, e)}
            onPaste={(e) => handlePaste(index, e)}
            onFocus={(e) => e.target.select()}
          />
          {groupSize && index === groupSize - 1 && index < length - 1 && (
            <span className="pin-separator" aria-hidden="true">-</span>
          )}
        </Fragment>
      ))}
      {masked && revealable && (
        <ActionButton
          disableText={true}
          disableBorder={true}
          iconTitle={revealed ? "Ukryj PIN" : "Pokaż PIN"}
          onClick={() => setRevealed((prev) => !prev)}
          className="pin-toggle-btn"
          disabled={disabled}
        >
          {revealed ? <EyeOff className="dl-icon icon-control" /> : <Eye className="dl-icon icon-control" />}
        </ActionButton>
      )}
    </div>
  );
}

export default PinInput;
