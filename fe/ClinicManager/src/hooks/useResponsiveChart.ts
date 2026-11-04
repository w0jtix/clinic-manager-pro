import { useBreakpoint } from "./useBreakpoint";

export interface ChartMargin {
  top: number;
  right: number;
  bottom: number;
  left: number;
}

export interface ResponsiveChartConfig {
  margin: ChartMargin;
  mobileMargin?: Partial<ChartMargin>;
  narrowMargin?: Partial<ChartMargin>;
  yAxisWidth?: number;
  mobileYAxisWidth?: number;
}

export function useResponsiveChart({
  margin,
  mobileMargin,
  narrowMargin,
  yAxisWidth,
  mobileYAxisWidth,
}: ResponsiveChartConfig) {
  const { isMobile, isNarrow } = useBreakpoint();

  return {
    margin: isMobile
      ? { ...margin, ...mobileMargin, ...(isNarrow ? narrowMargin : undefined) }
      : margin,
    yAxisWidth: isMobile ? (mobileYAxisWidth ?? yAxisWidth) : yAxisWidth,
    monthTickInterval: isMobile ? 2 : ("preserveEnd" as const),
  };
}

export default useResponsiveChart;
