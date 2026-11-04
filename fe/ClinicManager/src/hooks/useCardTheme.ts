import { useCallback, useEffect, useMemo, useState } from "react";
import { useUser } from "../components/User/UserProvider";
import UserService from "../services/UserService";
import { CardTheme, DEFAULT_CARD_THEME } from "../models/preferences";

interface CardThemeEntry {
  userId: number;
  theme: CardTheme;
  verified: boolean;
}

interface CardThemeSnapshot {
  entry: CardThemeEntry | null;
  failedUserId: number | null;
}

const STORAGE_PREFIX = "card-theme:";

let snapshot: CardThemeSnapshot = { entry: null, failedUserId: null };
let pending: { userId: number; request: Promise<void> } | null = null;
const listeners = new Set<(next: CardThemeSnapshot) => void>();

const isCardTheme = (value: unknown): value is CardTheme => value === "LIGHT" || value === "DARK";

const readStored = (userId: number): CardTheme | null => {
  try {
    const value = localStorage.getItem(`${STORAGE_PREFIX}${userId}`);
    return isCardTheme(value) ? value : null;
  } catch {
    return null;
  }
};

const writeStored = (userId: number, theme: CardTheme) => {
  try {
    localStorage.setItem(`${STORAGE_PREFIX}${userId}`, theme);
  } catch {
    return;
  }
};

const publish = (next: CardThemeSnapshot) => {
  snapshot = next;
  listeners.forEach((listener) => listener(next));
};

const publishTheme = (userId: number, theme: CardTheme) => {
  writeStored(userId, theme);
  publish({ entry: { userId, theme, verified: true }, failedUserId: null });
};

const loadTheme = (userId: number): Promise<void> => {
  if (pending?.userId === userId) return pending.request;
  const request = UserService.getMyPreferences()
    .then((preferences) => publishTheme(userId, preferences.cardTheme))
    .catch((error) => {
      console.error("Error fetching User preferences:", error);
      publish({ ...snapshot, failedUserId: userId });
    })
    .finally(() => {
      if (pending?.request === request) pending = null;
    });
  pending = { userId, request };
  return request;
};

export function useCardTheme() {
  const { user } = useUser();
  const userId = user?.id ?? null;
  const [state, setState] = useState<CardThemeSnapshot>(snapshot);

  useEffect(() => {
    listeners.add(setState);
    return () => {
      listeners.delete(setState);
    };
  }, []);

  useEffect(() => {
    if (userId === null) return;
    if (snapshot.entry?.userId === userId && snapshot.entry.verified) return;
    loadTheme(userId);
  }, [userId]);

  const entry = useMemo<CardThemeEntry | null>(() => {
    if (userId === null) return null;
    if (state.entry?.userId === userId) return state.entry;
    const stored = readStored(userId);
    return stored ? { userId, theme: stored, verified: false } : null;
  }, [userId, state.entry]);

  const setTheme = useCallback(
    async (theme: CardTheme) => {
      if (userId === null) return;
      const saved = await UserService.updateMyPreferences({ cardTheme: theme });
      publishTheme(userId, saved.cardTheme);
    },
    [userId]
  );

  const retry = useCallback(() => {
    if (userId !== null) loadTheme(userId);
  }, [userId]);

  const loaded = entry?.verified ?? false;
  return {
    theme: entry?.theme ?? DEFAULT_CARD_THEME,
    loaded,
    failed: !loaded && userId !== null && state.failedUserId === userId,
    retry,
    setTheme,
  };
}
