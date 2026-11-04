import { Unit } from "../models/product";

const VOLUME_PATTERN = /(\d+(?:[.,]\d+)?)\s*(ml|l|kg|gr|g)(?![\p{L}\d])/giu;
const MAX_VOLUME = 9999;

export const parseVolumeFromName = (
  name: string | null | undefined
): { volume: number; unit: Unit } | null => {
  if (!name) return null;
  const matches = [...name.matchAll(VOLUME_PATTERN)];
  if (matches.length === 0) return null;
  const [, rawValue, rawUnit] = matches[matches.length - 1];
  const value = parseFloat(rawValue.replace(",", "."));
  if (!Number.isFinite(value) || value <= 0) return null;

  const unitKey = rawUnit.toLowerCase();
  const multiplier = unitKey === "l" || unitKey === "kg" ? 1000 : 1;
  const unit = unitKey === "ml" || unitKey === "l" ? Unit.ML : Unit.G;
  const volume = Math.round(value * multiplier);

  if (volume <= 0 || volume > MAX_VOLUME) return null;
  return { volume, unit };
};
