import React, { useCallback, useEffect, useState } from "react";

export interface RangeSliderProps {
  min?: number;
  max?: number;
  step?: number;
  valueFrom: number;
  valueTo: number;
  onChange: (from: number, to: number) => void;
  unit?: string;
  className?: string;
}

export function RangeSlider({
  min = 0,
  max = 1500,
  step = 10,
  valueFrom,
  valueTo,
  onChange,
  unit = "",
  className = "",
}: RangeSliderProps) {
  // Draft = live position while dragging. onChange (fetch) fires only on release.
  const [draftFrom, setDraftFrom] = useState<number>(valueFrom);
  const [draftTo, setDraftTo] = useState<number>(valueTo);

  useEffect(() => {
    setDraftFrom(valueFrom);
  }, [valueFrom]);

  useEffect(() => {
    setDraftTo(valueTo);
  }, [valueTo]);

  const from = Math.min(draftFrom, draftTo);
  const to = Math.max(draftFrom, draftTo);

  const handleFrom = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      setDraftFrom(Math.min(Number(e.target.value), draftTo));
    },
    [draftTo]
  );

  const handleTo = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      setDraftTo(Math.max(Number(e.target.value), draftFrom));
    },
    [draftFrom]
  );

  const commit = useCallback(() => {
    onChange(from, to);
  }, [onChange, from, to]);

  const fromPct = ((from - min) / (max - min)) * 100;
  const toPct = ((to - min) / (max - min)) * 100;

  return (
    <div className={`range-slider flex align-items-center g-05 ${className}`}>
      <span className="range-slider-value">
        {from}
        {unit}
      </span>
      <div className="range-slider-track relative f-1">
        <div className="range-slider-rail" />
        <div
          className="range-slider-fill"
          style={{ left: `${fromPct}%`, width: `${toPct - fromPct}%` }}
        />
        <input
          type="range"
          min={min}
          max={max}
          step={step}
          value={from}
          onChange={handleFrom}
          onMouseUp={commit}
          onTouchEnd={commit}
          onKeyUp={commit}
          className="range-slider-input"
          style={{ zIndex: fromPct > 50 ? 5 : 3 }}
        />
        <input
          type="range"
          min={min}
          max={max}
          step={step}
          value={to}
          onChange={handleTo}
          onMouseUp={commit}
          onTouchEnd={commit}
          onKeyUp={commit}
          className="range-slider-input"
          style={{ zIndex: 4 }}
        />
      </div>
      <span className="range-slider-value">
        {to}
        {unit}
      </span>
    </div>
  );
}

export default RangeSlider;
