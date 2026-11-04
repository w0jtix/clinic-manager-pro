import { useEffect, useRef, useState } from "react";
import { Plus, X } from "lucide-react";
import ActionButton from "../ActionButton";
import TextInput from "../TextInput";

export const REMARKS_SEPARATOR = "\n";

export const splitRemarks = (value: string | null | undefined): string[] =>
  value === null || value === undefined ? [] : value.split(REMARKS_SEPARATOR);

export const normalizeRemarks = (value: string | null | undefined): string | null =>
  splitRemarks(value)
    .map((item) => item.trim())
    .filter(Boolean)
    .join(REMARKS_SEPARATOR) || null;

export interface RemarksListProps {
  label: string;
  value: string | null;
  onChange: (value: string | null) => void;
  maxLength: number;
  disabled?: boolean;
}

export function RemarksList({ label, value, onChange, maxLength, disabled = false }: RemarksListProps) {
  const items = splitRemarks(value);
  const listRef = useRef<HTMLDivElement>(null);
  const [focusIndex, setFocusIndex] = useState<number | null>(null);
  const totalLength = value?.length ?? 0;
  const canAdd = !disabled && totalLength + REMARKS_SEPARATOR.length < maxLength;

  useEffect(() => {
    if (focusIndex === null) return;
    const inputs = listRef.current?.querySelectorAll<HTMLInputElement>(".cc-remark-row input");
    inputs?.[focusIndex]?.focus();
    setFocusIndex(null);
  }, [focusIndex]);

  const commit = (next: string[]) => onChange(next.length ? next.join(REMARKS_SEPARATOR) : null);

  const updateItem = (index: number, text: string) =>
    commit(items.map((item, i) => (i === index ? text.split(REMARKS_SEPARATOR).join(" ") : item)));

  const removeItem = (index: number) => commit(items.filter((_, i) => i !== index));

  const addItem = () => {
    commit([...items, ""]);
    setFocusIndex(items.length);
  };

  const header = (
    <div className="cc-remark-header flex align-items-center space-between g-05">
      <span className="pf-label">{label}</span>
      {!disabled && (
        <ActionButton
          text="Dodaj informację"
          iconTitle="Dodaj informację"
          disabled={!canAdd}
          className="cc-remark-add"
          onClick={addItem}
        >
          <Plus className="dl-icon-1" />
        </ActionButton>
      )}
    </div>
  );

  if (disabled) {
    const filled = items.map((item) => item.trim()).filter(Boolean);
    return (
      <>
        {header}
        {filled.length ? (
          <ul className="cc-remark-list cc-remark-static">
            {filled.map((item, index) => (
              <li key={index}>{item}</li>
            ))}
          </ul>
        ) : (
          <span className="cq-empty italic">Brak dodatkowych informacji.</span>
        )}
      </>
    );
  }

  return (
    <>
      {header}
      <div ref={listRef} className="cc-remark-list flex-column g-05">
        {items.map((item, index) => (
          <div key={index} className="cc-remark-row flex align-items-center g-05">
            <span className="cc-remark-dash">•</span>
            <TextInput
              className="in-full"
              value={item}
              placeholder="Wpisz informację"
              maxLength={maxLength - (totalLength - item.length)}
              onSelect={(text) => {
                if (typeof text === "string") updateItem(index, text);
              }}
            />
            <ActionButton
              iconTitle="Usuń informację"
              disableText
              disableBorder
              onClick={() => removeItem(index)}
            >
              <X className="dl-icon" />
            </ActionButton>
          </div>
        ))}
      </div>
    </>
  );
}

export default RemarksList;
