import { describe, expect, it } from "vitest";
import { parseVolumeFromName } from "../../utils/productNameUtils";
import { Unit } from "../../models/product";

describe("parseVolumeFromName", () => {
  it("reads millilitres and grams as they are", () => {
    expect(parseVolumeFromName("Krem do stóp 12 ml")).toEqual({ volume: 12, unit: Unit.ML });
    expect(parseVolumeFromName("Pasta 250g")).toEqual({ volume: 250, unit: Unit.G });
  });

  it("reads the Polish gram abbreviation gr", () => {
    expect(parseVolumeFromName("Pasta ochronna 250 gr")).toEqual({ volume: 250, unit: Unit.G });
    expect(parseVolumeFromName("Krem 50gr.")).toEqual({ volume: 50, unit: Unit.G });
  });

  it("converts litres and kilograms", () => {
    expect(parseVolumeFromName("Płyn dezynfekujący 1L")).toEqual({ volume: 1000, unit: Unit.ML });
    expect(parseVolumeFromName("Sól 1,5 kg")).toEqual({ volume: 1500, unit: Unit.G });
    expect(parseVolumeFromName("Żel 0.5l")).toEqual({ volume: 500, unit: Unit.ML });
  });

  it("uses the last volume in the name", () => {
    expect(parseVolumeFromName("Zestaw 2 x 50 ml")).toEqual({ volume: 50, unit: Unit.ML });
  });

  it("ignores units that are part of a word", () => {
    expect(parseVolumeFromName("Glamour 5 lakier")).toBeNull();
    expect(parseVolumeFromName("Rękawice 100 gumowe")).toBeNull();
    expect(parseVolumeFromName("Waga 100 gram")).toBeNull();
  });

  it("returns null when there is no volume or it is too large", () => {
    expect(parseVolumeFromName("Pilnik")).toBeNull();
    expect(parseVolumeFromName("")).toBeNull();
    expect(parseVolumeFromName("Beczka 20 kg")).toBeNull();
  });
});
