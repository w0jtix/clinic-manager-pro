import { useState, useCallback, useMemo, useEffect } from "react";
import {
  formatDate,
  getWeekday,
  getWeeksOfMonth,
  WeekRange,
} from "../../utils/dateUtils";
import BonusVisitTable from "./BonusVisitTable";
import { BonusVisit } from "../../models/employee";
import { ChevronDown } from "lucide-react";
import { formatNumber } from "../../utils/formatUtils";

export interface BonusHistoryVisitListProps {
  visits: BonusVisit[];
  month: number;
  year: number;
}

function isInWeek(visitDate: string, week: WeekRange): boolean {
  const d = new Date(visitDate);
  d.setHours(0, 0, 0, 0);
  const start = new Date(week.start);
  start.setHours(0, 0, 0, 0);
  const end = new Date(week.end);
  end.setHours(23, 59, 59, 999);
  return d >= start && d <= end;
}

function groupByDay(visits: BonusVisit[]): { date: string; visits: BonusVisit[] }[] {
  const groups: { date: string; visits: BonusVisit[] }[] = [];
  for (const visit of visits) {
    const last = groups[groups.length - 1];
    if (last && last.date === visit.date) last.visits.push(visit);
    else groups.push({ date: visit.date, visits: [visit] });
  }
  return groups;
}

export function BonusHistoryVisitList({
  visits,
  month,
  year,
}: BonusHistoryVisitListProps) {
  const [expandedWeeks, setExpandedWeeks] = useState<number[]>([]);
  const [expandedDays, setExpandedDays] = useState<Set<string>>(new Set());

  const weeks = useMemo(() => getWeeksOfMonth(year, month), [year, month]);

  const toggleWeek = useCallback((weekNumber: number) => {
    setExpandedWeeks((prev) =>
      prev.includes(weekNumber)
        ? prev.filter((w) => w !== weekNumber)
        : [...prev, weekNumber]
    );
  }, []);

  const toggleDay = useCallback((key: string) => {
    setExpandedDays((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }, []);

  useEffect(() => {
    setExpandedWeeks([]);
    setExpandedDays(new Set());
  }, [visits]);

  const weekVisitsMap = useMemo(() => {
    const map = new Map<number, BonusVisit[]>();
    for (const week of weeks) {
      map.set(
        week.weekNumber,
        visits.filter((v) => isInWeek(v.date, week))
      );
    }
    return map;
  }, [weeks, visits]);

  return (
    <div className="pv-weeks">
      {weeks.map((week) => {
        const weekVisits = weekVisitsMap.get(week.weekNumber) ?? [];
        const weekSum = weekVisits.reduce((s, v) => s + v.adjustedRevenue, 0);
        const open = expandedWeeks.includes(week.weekNumber);
        return (
          <div className="pv-week" key={`week-${week.weekNumber}`}>
            <div
              className="pv-week-head align-items-center"
              onClick={() => toggleWeek(week.weekNumber)}
            >
              <ChevronDown className={`pv-chev dl-icon ${open ? "open" : ""}`} />
              <span className="pv-week-name">{`Tydzień ${week.weekNumber}.`}</span>
              <span className="pv-week-range">
                {formatDate(week.start.toISOString())} -{" "}
                {formatDate(week.end.toISOString())}
              </span>
              <span className="pv-week-sum">+ {formatNumber(weekSum)} zł</span>
            </div>

            {open && (
              <div className="pv-week-body">
                {weekVisits.length === 0 && (
                  <div className="pv-empty-week text-align-center">
                    Brak wizyt w tym tygodniu.
                  </div>
                )}
                {groupByDay(weekVisits).map((day) => {
                  const dayKey = `${week.weekNumber}-${day.date}`;
                  const dayOpen = expandedDays.has(dayKey);
                  const daySum = day.visits.reduce(
                    (s, v) => s + v.adjustedRevenue,
                    0
                  );
                  return (
                    <div className="pv-day-group" key={dayKey}>
                      <div
                        className="pv-day flex space-between align-items-center pointer"
                        onClick={() => toggleDay(dayKey)}
                      >
                        <span className="flex align-items-center g-05">
                          <ChevronDown
                            className={`pv-chev dl-icon ${dayOpen ? "open" : ""}`}
                          />
                          <span className="pv-day-lbl">
                            {formatDate(day.date)} - {getWeekday(day.date)}
                          </span>
                        </span>
                        <span className="pv-day-sum">+ {formatNumber(daySum)} zł</span>
                      </div>

                      {dayOpen && <BonusVisitTable visits={day.visits} />}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

export default BonusHistoryVisitList;
