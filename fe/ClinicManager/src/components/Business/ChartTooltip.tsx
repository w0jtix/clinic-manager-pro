import { ChartMode } from "../../models/statistics";
import { MONTHS, SHORT_TO_INDEX } from "../../utils/dateUtils";

interface TooltipPayloadItem {
  name: string;
  value: number;
  color: string;
}

interface ChartTooltipProps {
  active?: boolean;
  payload?: TooltipPayloadItem[];
  label?: string;
  mode: ChartMode;
  month: number | null;
  suffix?: string;
}

export function ChartTooltip({
  active,
  payload,
  label,
  mode,
  month,
  suffix = "zł",
}: ChartTooltipProps) {
  if (!active || !payload || !payload.length) return null;

  const formattedLabel =
    mode === ChartMode.MONTHLY
      ? MONTHS[SHORT_TO_INDEX[label || ""] ?? 0]?.name
      : `${label?.padStart(2, "0")} ${month ? MONTHS[month - 1]?.name : ""}`;

  return (
    <div className="chart-tooltip flex-column g-025">
      <p className="chart-tooltip-label">{formattedLabel}</p>
      {payload.map((entry, index) => (
        <p
          key={index}
          className="chart-tooltip-item flex g-1 space-between align-items-center"
        >
          <span className="chart-tooltip-name flex align-items-center g-05">
            <span
              className="chart-tooltip-swatch"
              style={{ backgroundColor: entry.color }}
            />
            {entry.name}
          </span>
          <span className="chart-tooltip-value tnum">
            {entry.value.toLocaleString("pl-PL")} {suffix}
          </span>
        </p>
      ))}
    </div>
  );
}

export default ChartTooltip;
