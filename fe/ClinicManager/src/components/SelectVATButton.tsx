import React from "react";
import ReactDOM from "react-dom";
import { ChevronDown } from "lucide-react";
import { useState, useEffect, useLayoutEffect, useRef, useCallback } from "react";
import { VatRate, getVatRateDisplay } from "../models/vatrate";

export interface SelectVatButtonProps {
  selectedVat: VatRate | null;
  onSelect: (vatRate: VatRate) => void;
  placeholder?: string;
  disabled?: boolean;
  className?: string;
  portal?: boolean;
}

export function SelectVATButton({
  selectedVat,
  onSelect,
  placeholder = "0%",
  disabled = false,
  className="",
  portal = false,
}: SelectVatButtonProps) {
  const [isExpanded, setIsExpanded] = useState<boolean>(false);
  const [selectedItem, setSelectedItem] = useState<VatRate | null>(
    selectedVat || VatRate.VAT_8
  );
  const dropdownRef = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLUListElement>(null);
  const [menuPosition, setMenuPosition] = useState<{ top: number; left: number } | null>(null);

  const vatValues: VatRate[] = Object.values(VatRate);

  useEffect(() => {
    setSelectedItem(selectedVat);
  }, [selectedVat]);

  const handleSelect = useCallback(
    (item: VatRate) => {
      setSelectedItem(item);
      onSelect(item);
      setIsExpanded(false);
    },
    [onSelect]
  );

  const toggleDropdown = useCallback(
    (e: React.MouseEvent<HTMLButtonElement>) => {
      e.stopPropagation();
      if (!disabled) {
        setIsExpanded((prev) => !prev);
      }
    },
    [disabled]
  );

  const placeMenu = useCallback(() => {
    const anchor = dropdownRef.current;
    const menu = menuRef.current;
    if (!anchor || !menu) return;
    const rect = anchor.getBoundingClientRect();
    const width = menu.offsetWidth;
    const height = menu.offsetHeight;
    const left = Math.min(
      Math.max(8, rect.right + 10 - width),
      window.innerWidth - width - 8
    );
    const top = rect.top - 4 - height >= 8 ? rect.top - 4 - height : rect.bottom + 4;
    setMenuPosition({ top, left });
  }, []);

  useLayoutEffect(() => {
    if (!portal || !isExpanded) {
      setMenuPosition(null);
      return;
    }
    placeMenu();
    window.addEventListener("scroll", placeMenu, true);
    window.addEventListener("resize", placeMenu);
    return () => {
      window.removeEventListener("scroll", placeMenu, true);
      window.removeEventListener("resize", placeMenu);
    };
  }, [portal, isExpanded, placeMenu]);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as Node;
      if (menuRef.current?.contains(target)) return;
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(target)
      ) {
        setIsExpanded(false);
      }
    };
    document.addEventListener("pointerdown", handleClickOutside);

    return () => {
      document.removeEventListener("pointerdown", handleClickOutside);
    };
  }, []);

  const portalRoot = document.getElementById("portal-root");

  const options = vatValues.map((item) => (
    <li
      key={item}
      className={`vat-select-item flex justify-center align-items-center pointer ${
        selectedItem === item ? "selected" : ""
      }`}
      onClick={() => handleSelect(item)}
    >
      {getVatRateDisplay(item)}
    </li>
  ));

  return (
    <div className="vat-select-container relative inline-block" ref={dropdownRef}>
      <button 
        className={`vat-select-button flex align-items-center justify-center g-5px  transparent ${className} ${disabled ? "not-allowed" : "pointer"}`} 
        onClick={toggleDropdown}
        >
        <a className="vat-button-selection flex justify-center align-items-center">
          {selectedItem ? getVatRateDisplay(selectedItem) : placeholder}
        </a>
        <ChevronDown
          className={`dl-icon vat-select-arrow-icon ${isExpanded ? "rotated" : ""}`}
        />
      </button>
      {isExpanded && !disabled && !portal && (
        <ul className="vat-select flex absolute m-0 p-0 justify-center align-items-center">
          {options}
        </ul>
      )}
      {isExpanded &&
        !disabled &&
        portal &&
        portalRoot &&
        ReactDOM.createPortal(
          <ul
            ref={menuRef}
            className="vat-select vat-select-portal flex m-0 p-0 justify-center align-items-center"
            style={{
              position: "fixed",
              top: menuPosition?.top ?? 0,
              left: menuPosition?.left ?? 0,
              visibility: menuPosition ? "visible" : "hidden",
            }}
          >
            {options}
          </ul>,
          portalRoot
        )}
    </div>
  );
}

export default SelectVATButton;
