import { useEffect, useState } from "react";
import { useLocation, useNavigationType } from "react-router-dom";

let navigatedThroughHistory = false;
window.addEventListener("popstate", () => {
  navigatedThroughHistory = true;
});

export interface RestoreOptions {
  force?: boolean;
  skipOnReload?: boolean;
}

export function useRestorableState<T>(
  key: string,
  initial: T | (() => T),
  { force = false, skipOnReload = false }: RestoreOptions = {}
) {
  const location = useLocation();
  const navigationType = useNavigationType();
  const storageKey = `restore:${location.pathname}:${key}`;

  const [state, setState] = useState<T>(() => {
    const fallback =
      typeof initial === "function" ? (initial as () => T)() : initial;
    const isReturning =
      navigationType === "POP" && (!skipOnReload || navigatedThroughHistory);
    if (!force && !isReturning) return fallback;
    try {
      const raw = sessionStorage.getItem(storageKey);
      return raw != null ? (JSON.parse(raw) as T) : fallback;
    } catch {
      return fallback;
    }
  });

  useEffect(() => {
    try {
      sessionStorage.setItem(storageKey, JSON.stringify(state));
    } catch {
      return;
    }
  }, [storageKey, state]);

  return [state, setState] as const;
}

export default useRestorableState;
