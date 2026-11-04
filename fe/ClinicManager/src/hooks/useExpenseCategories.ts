import { useEffect, useMemo, useSyncExternalStore } from "react";
import {
  EDIT_LOCKED_CATEGORIES,
  ExpenseCategoryCode,
  ExpenseCategoryDTO,
  ExpenseCategoryItem,
  ITEM_FORBIDDEN_CATEGORIES,
} from "../models/expense";
import ExpenseCategoryService from "../services/ExpenseCategoryService";

const STALE_AFTER_MS = 30_000;

let snapshot: ExpenseCategoryDTO[] = [];
let fetchedAt = 0;
let pending: Promise<void> | null = null;
const listeners = new Set<() => void>();

const isStale = () => Date.now() - fetchedAt > STALE_AFTER_MS;

const handleVisibilityChange = () => {
  if (document.visibilityState === "visible" && isStale()) reloadExpenseCategories();
};

const subscribe = (listener: () => void) => {
  if (listeners.size === 0) document.addEventListener("visibilitychange", handleVisibilityChange);
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0) document.removeEventListener("visibilitychange", handleVisibilityChange);
  };
};

const getSnapshot = () => snapshot;

export const reloadExpenseCategories = (): Promise<void> => {
  if (pending) return pending;
  pending = ExpenseCategoryService.getCategories()
    .then((list) => {
      if (list) {
        snapshot = list;
        fetchedAt = Date.now();
        listeners.forEach((listener) => listener());
      }
    })
    .catch((error) => {
      console.error("Error fetching expense categories:", error);
    })
    .finally(() => {
      pending = null;
    });
  return pending;
};

const toItem = (category: ExpenseCategoryDTO): ExpenseCategoryItem => ({
  id: category.code,
  name: category.name,
  color: category.color,
});

export function useExpenseCategories() {
  const categories = useSyncExternalStore(subscribe, getSnapshot);

  useEffect(() => {
    if (isStale()) reloadExpenseCategories();
  }, []);

  return useMemo(() => {
    const byCode = new Map(categories.map((category) => [category.code, category]));
    const items = categories.filter((category) => !category.internal).map(toItem);
    return {
      allItems: categories.map(toItem),
      items,
      itemCategoryItems: items.filter((item) => !ITEM_FORBIDDEN_CATEGORIES.includes(item.id)),
      editableItems: items.filter((item) => !EDIT_LOCKED_CATEGORIES.includes(item.id)),
      nameOf: (code?: ExpenseCategoryCode | null) => (code ? byCode.get(code)?.name ?? "" : ""),
    };
  }, [categories]);
}

export default useExpenseCategories;
