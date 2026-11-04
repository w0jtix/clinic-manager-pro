import { useState } from "react";
import { Lock } from "lucide-react";
import Modal from "../Modal";
import ActionButton from "../ActionButton";
import CategoryForm, { firstFreeColor } from "../CategoryForm";
import {
  BaseServiceCategory,
  NewBaseServiceCategory,
  NewProductCategory,
  ProductCategory,
} from "../../models/categories";
import { ExpenseCategoryDTO } from "../../models/expense";

type CategoryFormValue =
  | ProductCategory
  | NewProductCategory
  | BaseServiceCategory
  | NewBaseServiceCategory;

export interface CategoryModalProps {
  onClose: () => void;
  onConfirm: (category: CategoryFormValue) => void;
  category?: ProductCategory | BaseServiceCategory | ExpenseCategoryDTO | null;
  takenColors?: string[];
  systemNote?: string;
  className?: string;
}

export function CategoryModal({
  onClose,
  onConfirm,
  category = null,
  takenColors = [],
  systemNote = "",
  className = "",
}: CategoryModalProps) {
  const [categoryDTO, setCategoryDTO] = useState<CategoryFormValue>(() =>
    category
      ? { id: category.id, name: category.name, color: category.color }
      : { name: "", color: firstFreeColor(takenColors) }
  );

  const system = !!category && "system" in category && !!category.system;

  return (
    <Modal
      title={category ? "Edytuj kategorię" : "Nowa kategoria"}
      onClose={onClose}
      size="md"
      bodyClassName="cm-modal-body"
      onConfirm={() => onConfirm(categoryDTO)}
      footer={
        system && systemNote ? (
          <>
            <div className="cm-system-note flex align-items-center g-05">
              <Lock className="dl-icon" />
              <span>{systemNote}</span>
            </div>
            <ActionButton text="Zapisz" className="modal-button" onClick={() => onConfirm(categoryDTO)} />
          </>
        ) : undefined
      }
      dirtyValue={`${categoryDTO.name}|${categoryDTO.color}`}
      className={className}
    >
      <CategoryForm categoryDTO={categoryDTO} setCategoryDTO={setCategoryDTO} takenColors={takenColors} />
    </Modal>
  );
}

export default CategoryModal;
