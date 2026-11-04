import { ChartColumnBig, ChartLine } from "lucide-react";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export type ChartType = "line" | "bar";

export interface ChartTypeToggleProps {
  value: ChartType;
  onChange: (type: ChartType) => void;
}

export function ChartTypeToggle({ value, onChange }: ChartTypeToggleProps) {
  const { isCompact } = useBreakpoint();

  return (
    <div className="cs-seg flex">
      <button
        type="button"
        className={value === "line" ? "on" : ""}
        onClick={() => onChange("line")}
        title="Liniowy"
      >
        <ChartLine className="dl-icon-1" />
        {!isCompact && "Liniowy"}
      </button>
      <button
        type="button"
        className={value === "bar" ? "on" : ""}
        onClick={() => onChange("bar")}
        title="Słupkowy"
      >
        <ChartColumnBig className="dl-icon-1" />
        {!isCompact && "Słupkowy"}
      </button>
    </div>
  );
}

export default ChartTypeToggle;
