import React from "react";
import ActionButton from "../ActionButton";
import { ExpenseCategoryCode } from "../../models/expense";
import useExpenseCategories from "../../hooks/useExpenseCategories";

export interface CategoryShardProps {
  color?: string | null;
  title?: string;
  onClick?: (e: React.MouseEvent) => void;
  className?: string;
}

export function CategoryShard({ color, title, onClick, className = "" }: CategoryShardProps) {
  const shape = (
    <svg className="cat-shard-svg" viewBox="0 0 12 12" aria-hidden="true">
      <polygon
        points="6,0.75 11.25,6 6,11.25 0.75,6"
        fill={color ? `rgb(${color})` : "none"}
        stroke={color ? "none" : "rgba(255, 255, 255, 0.7)"}
        strokeWidth="1"
      />
    </svg>
  );

  if (!onClick) {
    return (
      <span className={`cat-shard flex align-items-center justify-center ${className}`} title={title}>
        {shape}
      </span>
    );
  }

  return (
    <ActionButton
      iconTitle={title}
      disableText={true}
      disableBorder={true}
      className={`cat-shard ${className}`}
      onClick={onClick}
    >
      {shape}
    </ActionButton>
  );
}

export interface CategoryDotsProps {
  categories: ExpenseCategoryCode[];
  className?: string;
}

export function CategoryDots({ categories, className = "" }: CategoryDotsProps) {
  const { items: categoryItems } = useExpenseCategories();
  const items = categoryItems.filter((item) => categories.includes(item.id));
  if (items.length === 0) return null;
  return (
    <span
      className={`cat-dots flex align-items-center ${className}`}
      title={items.map((item) => item.name).join(", ")}
    >
      {items.map((item) => (
        <span
          key={item.id}
          className="dl-dot"
          style={{ backgroundColor: `rgb(${item.color})` }}
        />
      ))}
    </span>
  );
}

export default CategoryShard;
