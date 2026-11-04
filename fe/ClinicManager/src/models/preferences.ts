export type CardTheme = "LIGHT" | "DARK";

export const DEFAULT_CARD_THEME: CardTheme = "LIGHT";

export interface UserPreferences {
  cardTheme: CardTheme;
}
