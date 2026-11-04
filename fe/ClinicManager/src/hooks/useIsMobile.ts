import { useEffect, useState } from "react";

// Single breakpoint for the whole app: below it we're in "mobile mode"
// (phone and portrait tablet alike - a portrait iPad/Galaxy Tab sits around
// 800-834px, which is much closer to a large phone than to the desktop
// layout). Kept in sync by hand with the `1024px` media queries in index.css.
export const MOBILE_BREAKPOINT = 1024;

export function useIsMobile(breakpoint: number = MOBILE_BREAKPOINT): boolean {
  const query = `(max-width: ${breakpoint - 1}px)`;
  const [isMobile, setIsMobile] = useState<boolean>(
    () => window.matchMedia(query).matches
  );

  useEffect(() => {
    const mql = window.matchMedia(query);
    const handler = (event: MediaQueryListEvent) => setIsMobile(event.matches);
    setIsMobile(mql.matches);
    mql.addEventListener("change", handler);
    return () => mql.removeEventListener("change", handler);
  }, [query]);

  return isMobile;
}

export default useIsMobile;
