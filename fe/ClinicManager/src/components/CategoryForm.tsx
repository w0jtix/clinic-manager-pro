import React, { useCallback, useMemo } from "react";
import TextInput from "./TextInput";
import { NewProductCategory, ProductCategory, BaseServiceCategory, NewBaseServiceCategory } from "../models/categories";

export const CATEGORY_COLORS = [
  "239,68,68",
  "249,115,22",
  "234,179,8",
  "34,197,94",
  "20,184,166",
  "59,130,246",
  "99,102,241",
  "168,85,247",
  "236,72,153",
  "120,113,108",
];

export const CATEGORY_NAME_MAX_LENGTH = 50;

export const normalizeColor = (color: string): string => color.replace(/\s/g, "");

const hslToRgb = (hue: number, saturation: number, lightness: number): string => {
  const a = saturation * Math.min(lightness, 1 - lightness);
  const channel = (n: number) => {
    const k = (n + hue / 30) % 12;
    return Math.round(255 * (lightness - a * Math.max(-1, Math.min(k - 3, 9 - k, 1))));
  };
  return `${channel(0)},${channel(8)},${channel(4)}`;
};

type Lab = [number, number, number];

const toOklab = (rgb: string): Lab => {
  const [r, g, b] = normalizeColor(rgb)
    .split(",")
    .map((x) => {
      const c = (Number(x) || 0) / 255;
      return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    });
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  return [
    0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
    1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
    0.0259040371 * l + 0.7827717662 * m - 0.808675778 * s,
  ];
};

const labDistance = (a: Lab, b: Lab): number => Math.hypot(a[0] - b[0], a[1] - b[1], a[2] - b[2]);

const labHue = (lab: Lab): number => {
  if (Math.hypot(lab[1], lab[2]) < 0.04) return 1000;
  return (Math.atan2(lab[2], lab[1]) * 180) / Math.PI + 180;
};

const CANDIDATES: string[] = (() => {
  const generated: string[] = [];
  for (let hue = 0; hue < 360; hue += 10) {
    generated.push(hslToRgb(hue, 0.75, 0.55), hslToRgb(hue, 0.7, 0.7), hslToRgb(hue, 0.7, 0.48));
  }
  return [...CATEGORY_COLORS, ...generated.filter((value) => !CATEGORY_COLORS.includes(value))];
})();

const pickDistinctColors = (takenColors: string[]): string[] => {
  const taken = new Set(takenColors.map(normalizeColor));
  const references: Lab[] = [...taken].map(toOklab);
  const pool = CANDIDATES.filter((value) => !taken.has(value)).map((value) => ({
    value,
    lab: toOklab(value),
    bonus: CATEGORY_COLORS.includes(value) ? 1.15 : 1,
  }));
  const picked: string[] = [];
  while (picked.length < CATEGORY_COLORS.length && pool.length > 0) {
    let bestIndex = 0;
    let bestScore = -1;
    pool.forEach((candidate, index) => {
      const nearest = references.length
        ? Math.min(...references.map((ref) => labDistance(candidate.lab, ref)))
        : 1;
      const score = nearest * candidate.bonus;
      if (score > bestScore) {
        bestScore = score;
        bestIndex = index;
      }
    });
    const [chosen] = pool.splice(bestIndex, 1);
    picked.push(chosen.value);
    references.push(chosen.lab);
  }
  return picked;
};

export const suggestColors = (takenColors: string[]): string[] =>
  [...pickDistinctColors(takenColors)].sort((a, b) => labHue(toOklab(a)) - labHue(toOklab(b)));

export const firstFreeColor = (takenColors: string[]): string => pickDistinctColors(takenColors)[0] ?? CATEGORY_COLORS[0];

const rgbToHex = (rgb: string): string => {
  const [r, g, b] = normalizeColor(rgb).split(",").map(Number);
  return "#" + [r, g, b].map((x) => (Number.isFinite(x) ? x : 0).toString(16).padStart(2, "0")).join("");
};

const hexToRgb = (hex: string): string => {
  const value = parseInt(hex.replace("#", ""), 16);
  return `${(value >> 16) & 255},${(value >> 8) & 255},${value & 255}`;
};

export interface CategoryFormProps {
  categoryDTO: ProductCategory | NewProductCategory | BaseServiceCategory | NewBaseServiceCategory;
  setCategoryDTO: React.Dispatch<React.SetStateAction<ProductCategory | NewProductCategory | BaseServiceCategory | NewBaseServiceCategory>>;
  takenColors?: string[];
}

export function CategoryForm({ categoryDTO, setCategoryDTO, takenColors = [] }: CategoryFormProps) {
  const current = normalizeColor(categoryDTO.color);
  const takenKey = takenColors.map(normalizeColor).join("|");
  const colors = useMemo(() => suggestColors(takenColors), [takenKey]);
  const isCustom = !colors.includes(current);

  const handleName = useCallback((name: string): void => {
    setCategoryDTO((prev) => ({ ...prev, name }));
  }, [setCategoryDTO]);

  const handleColor = useCallback((color: string): void => {
    setCategoryDTO((prev) => ({ ...prev, color }));
  }, [setCategoryDTO]);

  return (
    <div className="st-mk-form flex-column g-15">
      <div className="st-mk-preview flex align-items-center g-075">
        <span className="dl-dot cf-preview-dot" style={{ backgroundColor: `rgb(${current})` }} />
        <span className={`cf-preview-name ${categoryDTO.name ? "" : "dl-dim"}`}>
          {categoryDTO.name || "Nazwa kategorii"}
        </span>
      </div>
      <div className="pf-row flex align-items-center g-1">
        <span className="pf-label">Nazwa:</span>
        <TextInput
          className="in-full"
          dropdown={false}
          value={categoryDTO.name}
          maxLength={CATEGORY_NAME_MAX_LENGTH}
          placeholder="np. Jednorazowe"
          onSelect={(value) => {
            if (typeof value === "string") handleName(value);
          }}
        />
      </div>
      <div className="flex-column g-1">
        <span className="pf-label st-tpl-label">Kolor:</span>
        <div className="st-mk-colors flex align-items-center flex-wrap g-05" role="radiogroup" aria-label="Kolor">
          {colors.map((value) => (
            <button
              key={value}
              type="button"
              role="radio"
              aria-checked={current === value}
              aria-label={`rgb(${value})`}
              className={`st-mk-color pointer ${current === value ? "active" : ""}`}
              style={{ backgroundColor: `rgb(${value})` }}
              onClick={() => handleColor(value)}
            />
          ))}
          <div
            className={`st-mk-custom relative flex align-items-center g-05 ${isCustom ? "active" : ""}`}
            title="Własny kolor"
          >
            <span className="st-mk-custom-swatch" style={{ backgroundColor: `rgb(${current})` }} />
            <span className="st-mk-custom-label">Własny</span>
            <input
              type="color"
              aria-label="Własny kolor"
              value={rgbToHex(current)}
              onChange={(e) => handleColor(hexToRgb(e.target.value))}
            />
          </div>
        </div>
      </div>
    </div>
  );
}

export default CategoryForm;
