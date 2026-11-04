import { useEffect } from "react";

const LIGHT_PORTALS_CLASS = "cc-light-portals";
let activeCount = 0;

export function useLightPortals(active: boolean) {
  useEffect(() => {
    if (!active) return;
    activeCount += 1;
    document.body.classList.add(LIGHT_PORTALS_CLASS);
    return () => {
      activeCount = Math.max(0, activeCount - 1);
      if (activeCount === 0) document.body.classList.remove(LIGHT_PORTALS_CLASS);
    };
  }, [active]);
}
