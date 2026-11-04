import { useMemo, useState } from "react";
import { Crown } from "lucide-react";
import { getDaysInMonth } from "../../utils/dateUtils";
import { BonusVisit } from "../../models/employee";
import BonusDayModal from "./BonusDayModal";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { formatWholeAmount } from "../../utils/formatUtils";

export interface BonusCalendarProps {
  visits: BonusVisit[];
  month: number;
  year: number;
}

const WEEKDAYS = ["Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd"];
const RED_ALPHA = 0.32;

export function BonusCalendar({ visits, month, year }: BonusCalendarProps) {
  const [selectedDay, setSelectedDay] = useState<number | null>(null);
  const { isNarrow } = useBreakpoint();

  const { dayValues, dayVisits, offset, maxPos, best, bestIndex, avg } =
    useMemo(() => {
      const daysInMonth = getDaysInMonth(year, month);
      const values = new Array<number>(daysInMonth).fill(0);
      const grouped: BonusVisit[][] = Array.from(
        { length: daysInMonth },
        () => []
      );
      for (const visit of visits) {
        const d = new Date(visit.date);
        if (d.getFullYear() === year && d.getMonth() === month - 1) {
          values[d.getDate() - 1] += visit.adjustedRevenue;
          grouped[d.getDate() - 1].push(visit);
        }
      }
      // Monday-first offset for the 1st of the month
      const firstWeekday = (new Date(year, month - 1, 1).getDay() + 6) % 7;
      const positives = values.filter((v) => v > 0);
      const maxPositive = positives.length > 0 ? Math.max(...positives) : 1;
      const workDays = values.filter((v) => v !== 0).length;
      const total = values.reduce((s, v) => s + v, 0);
      const bestValue = values.length > 0 ? Math.max(...values, 0) : 0;
      return {
        dayValues: values,
        dayVisits: grouped,
        offset: firstWeekday,
        maxPos: maxPositive,
        best: bestValue,
        bestIndex: bestValue > 0 ? values.indexOf(bestValue) : -1,
        avg: workDays > 0 ? total / workDays : 0,
      };
    }, [visits, month, year]);

  const greenAlpha = (v: number): number =>
    0.06 + 0.6 * Math.pow(v / maxPos, 1.1);

  return (
    <div className="pv-cal">
      <div className="pv-cal-wd g-05">
        {WEEKDAYS.map((wd, i) => (
          <span key={wd} className={i >= 5 ? "we" : ""}>
            {wd}
          </span>
        ))}
      </div>
      <div className="pv-cal-grid g-05">
        {Array.from({ length: offset }).map((_, i) => (
          <div className="pv-cal-cell relative flex-column space-between empty" key={`empty-${i}`} />
        ))}
        {dayValues.map((v, idx) => {
          let background: string | undefined;
          let valClass = "zeroval";
          let valText = "0";
          if (v > 0) {
            background = `rgba(23,196,127,${greenAlpha(v).toFixed(3)})`;
            valClass = "pos";
            valText = `+${formatWholeAmount(v)}`;
          } else if (v < 0) {
            background = `rgba(250,58,63,${RED_ALPHA})`;
            valClass = "neg";
            valText = `-${formatWholeAmount(-v)}`;
          }
          const hasVisits = dayVisits[idx].length > 0;
          return (
            <div
              className={`pv-cal-cell relative flex-column space-between ${v === 0 ? "zero" : ""} ${
                hasVisits ? "pointer" : ""
              }`}
              key={idx}
              style={background ? { background } : undefined}
              onClick={hasVisits ? () => setSelectedDay(idx) : undefined}
            >
              <span className="pv-cal-top flex align-items-center space-between">
                <span className="pv-cal-num">{idx + 1}</span>
                {idx === bestIndex && (
                  <Crown className="pv-cal-crown pc-y" />
                )}
              </span>
              <span
                className={`pv-cal-val ${
                  idx === bestIndex ? "pc-y" : valClass
                } tnum`}
              >
                {valText}
                {v !== 0 && !isNarrow ? " zł" : ""}
              </span>
            </div>
          );
        })}
      </div>
      <div className="pv-cal-foot flex align-items-center space-between">
        <div className="pv-cal-stats flex">
          <span>
            Najlepszy dzień: <b>+{formatWholeAmount(best)} zł</b>
          </span>
          <span>
            Średnia/dzień: <b>{formatWholeAmount(avg)} zł</b>
          </span>
        </div>
        <div className="pv-cal-scale flex align-items-center">
          <span>mniej</span>
          {[0.1, 0.28, 0.46, 0.66].map((a) => (
            <span
              key={a}
              className="pv-cal-swatch"
              style={{ background: `rgba(23,196,127,${a})` }}
            />
          ))}
          <span>więcej</span>
        </div>
      </div>
      {selectedDay != null && dayVisits[selectedDay].length > 0 && (
        <BonusDayModal
          date={dayVisits[selectedDay][0].date}
          visits={dayVisits[selectedDay]}
          onClose={() => setSelectedDay(null)}
        />
      )}
    </div>
  );
}

export default BonusCalendar;
