import { describe, expect, it } from 'vitest';
import { splitAddress } from '../../utils/addressUtils';

describe('splitAddress', () => {
  it('splits a street and a postal code with city', () => {
    expect(splitAddress('ul. Przemysłowa 12, 60-101 Lublin')).toEqual({
      street: 'ul. Przemysłowa 12',
      city: '60-101 Lublin',
      rest: null,
    });
  });

  it('keeps administrative details after the city as the rest', () => {
    expect(
      splitAddress(
        'WIEJSKA 37, 46-100 SMOGORZÓW, opolskie, gmina NAMYSŁÓW, powiat NAMYSŁOWSKI, poczta NAMYSŁÓW'
      )
    ).toEqual({
      street: 'WIEJSKA 37',
      city: '46-100 SMOGORZÓW',
      rest: 'opolskie, gmina NAMYSŁÓW, powiat NAMYSŁOWSKI, poczta NAMYSŁÓW',
    });
  });

  it('splits when there is no comma before the postal code', () => {
    expect(splitAddress('ul. Zdrowa 5 61-001 Lublin')).toEqual({
      street: 'ul. Zdrowa 5',
      city: '61-001 Lublin',
      rest: null,
    });
  });

  it('trims surrounding whitespace in every part', () => {
    expect(splitAddress('  ul. Leśna 1 ,  60-101 Lublin  ')).toEqual({
      street: 'ul. Leśna 1',
      city: '60-101 Lublin',
      rest: null,
    });
  });

  it('returns null when the postal code comes first', () => {
    expect(splitAddress('60-101 Lublin, ul. Przemysłowa 12')).toBeNull();
  });

  it('returns null when there is no postal code', () => {
    expect(splitAddress('Lublin, ul. Przemysłowa 12')).toBeNull();
  });
});
