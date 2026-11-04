import { useState, useEffect } from "react";
import {
  ProductCategory,
  NewProductCategory,
  BaseServiceCategory,
  NewBaseServiceCategory,
} from "../models/categories";

type AnyCategory = ProductCategory | BaseServiceCategory;

const EMPTY_SELECTED: AnyCategory[] = [];

export interface CategorySegmentsProps {
  categories: ProductCategory[] | NewProductCategory[] | BaseServiceCategory[] | NewBaseServiceCategory[];
  selectedCategories?: ProductCategory[] | BaseServiceCategory[];
  onSelect: (selected: ProductCategory[] | BaseServiceCategory[] | null) => void;
  resetTriggered?: boolean;
  multiple?: boolean;
  showAllSegment?: boolean;
  allLabel?: string;
  className?: string;
}

export function CategorySegments({
  categories,
  selectedCategories = EMPTY_SELECTED,
  onSelect,
  resetTriggered,
  multiple = true,
  showAllSegment = true,
  allLabel = "Wszystko",
  className = "",
}: CategorySegmentsProps) {
  const [selected, setSelected] = useState<AnyCategory[]>(selectedCategories);

  useEffect(() => {
    if (resetTriggered) setSelected([]);
  }, [resetTriggered]);

  useEffect(() => {
    setSelected(selectedCategories);
  }, [selectedCategories]);

  const isSelected = (category: AnyCategory): boolean =>
    selected.some((item) => item.id === category.id);

  const toggleCategory = (category: AnyCategory) => {
    let next: AnyCategory[];
    if (isSelected(category)) {
      next = selected.filter((item) => item.id !== category.id);
    } else {
      next = multiple ? [...selected, category] : [category];
    }
    setSelected(next);
    onSelect(next as ProductCategory[] | BaseServiceCategory[]);
  };

  const selectAll = () => {
    setSelected([]);
    onSelect([]);
  };

  return (
    <div className={`cat-seg-group flex align-items-center width-fit-content g-025 ${className}`}>
      {showAllSegment && (
        <button
          type="button"
          className={`cat-seg align-items-center pointer ${selected.length === 0 ? "active" : ""}`}
          onClick={selectAll}
        >
          {allLabel}
        </button>
      )}

      {(categories as AnyCategory[]).map((category, index) => (
        <button
          type="button"
          key={("id" in category && category.id ? category.id : index) as string | number}
          className={`cat-seg align-items-center pointer ${isSelected(category) ? "active" : ""}`}
          onClick={() => toggleCategory(category)}
        >
          <span
            className="dl-dot"
            style={{
              backgroundColor: `rgb(${category.color})`,
              boxShadow: `0 0 6px rgba(${category.color}, 0.5)`,
            }}
          />
          {category.name}
        </button>
      ))}
    </div>
  );
}

export default CategorySegments;
