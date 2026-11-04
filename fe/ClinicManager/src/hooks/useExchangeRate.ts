import { useCallback, useEffect, useRef, useState } from "react";
import { ExchangeRate, ExchangeRateWindow, ExpenseCurrency } from "../models/expense";
import ExchangeRateService from "../services/ExchangeRateService";
import { apiErrorMessage } from "../utils/apiError";
import { parseIsoDate, toIsoDate } from "../utils/dateUtils";

export type ExchangeRateStatus = "idle" | "loading" | "ready" | "error";

const MIN_LOADING_MS = 300;

const shiftIsoDate = (value: string, days: number): string => {
  const date = parseIsoDate(value);
  date.setDate(date.getDate() + days);
  return toIsoDate(date);
};

const pickFromWindow = (rateWindow: ExchangeRateWindow, date: string): ExchangeRate | null => {
  if (rateWindow.windowTo < shiftIsoDate(date, -1)) return null;
  const candidate = [...rateWindow.rates].reverse().find((rate) => rate.rateDate < date) ?? null;
  if (!candidate || candidate.rateDate < shiftIsoDate(date, -7)) return null;
  return candidate;
};

export function useExchangeRate(currency: ExpenseCurrency, date: string | null, enabled: boolean) {
  const [status, setStatus] = useState<ExchangeRateStatus>("idle");
  const [rate, setRate] = useState<ExchangeRate | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState<number>(0);
  const windowsRef = useRef<Partial<Record<ExpenseCurrency, ExchangeRateWindow>>>({});
  const requestRef = useRef<number>(0);

  useEffect(() => {
    const requestId = ++requestRef.current;
    if (!enabled || currency === "PLN" || !date) {
      setStatus("idle");
      setRate(null);
      setError(null);
      return;
    }

    setStatus("loading");
    setError(null);
    const startedAt = Date.now();
    const finish = (next: ExchangeRate | null, message: string | null) => {
      const delay = Math.max(0, MIN_LOADING_MS - (Date.now() - startedAt));
      window.setTimeout(() => {
        if (requestId !== requestRef.current) return;
        setRate(next);
        setError(next ? null : message ?? "Brak kursu NBP.");
        setStatus(next ? "ready" : "error");
      }, delay);
    };

    const cached = windowsRef.current[currency];
    const fromCache = cached ? pickFromWindow(cached, date) : null;
    if (fromCache) {
      finish(fromCache, null);
      return;
    }

    ExchangeRateService.getRates(currency, date)
      .then((data) => {
        windowsRef.current[currency] = data;
        finish(data.selected ?? pickFromWindow(data, date), null);
      })
      .catch((err) => finish(null, apiErrorMessage(err) ?? "Nie udało się pobrać kursu z NBP."));
  }, [currency, date, enabled, attempt]);

  const retry = useCallback(() => setAttempt((prev) => prev + 1), []);

  return { status, rate, error, retry };
}

export default useExchangeRate;
