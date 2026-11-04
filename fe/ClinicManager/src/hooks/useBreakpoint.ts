import { MOBILE_BREAKPOINT, useIsMobile } from "./useIsMobile";

export const BREAKPOINTS = {
  mobile: MOBILE_BREAKPOINT,
  compact: 769,
  narrow: 481,
} as const;

export function useBreakpoint() {
  const isMobile = useIsMobile(BREAKPOINTS.mobile);
  const isCompact = useIsMobile(BREAKPOINTS.compact);
  const isNarrow = useIsMobile(BREAKPOINTS.narrow);

  return {
    isMobile,
    isCompact,
    isNarrow,
    isTablet: isMobile && !isCompact,
  };
}

export default useBreakpoint;
