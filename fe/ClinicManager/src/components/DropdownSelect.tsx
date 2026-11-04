import React, { useCallback } from "react";
import ReactDOM from "react-dom";
import { ChevronDown, Check, Plus, Search } from "lucide-react";
import { useState, useRef, useEffect, useLayoutEffect } from "react";
import ActionButton from "./ActionButton";

/* single select -> item
multi select -> array of items */
export interface DropdownItem {
  id: string | number;
  name?: string;
  [key: string]: any;
}

export interface DropdownSelectProps<T extends DropdownItem> {
  items: T[];
  placeholder?: string;
  value?: T | T[] | null;
  onChange: (value: T | T[] | null) => void;
  getItemLabel?: (item: T) => string;
  searchable?: boolean;
  allowNew?: boolean;
  showTick?: boolean;
  multiple?: boolean;
  reversed?: boolean;
  showNewPopup?: boolean;
  allowColors?: boolean;
  divided?: boolean;
  menuPortal?: boolean;
  newItemComponent?: React.ComponentType<any> /* React.ComponentType<NewItemComponentProps>; */;
  newItemProps?: Record<string, any>;
  newItemLabel?: string;

  disabled?: boolean;
  disabledItemIds?: (string | number)[];
  className?: string;
  searchPlaceholder?: string;
  emptyMessage?: string;
  maxHeight?: number;
}

export function DropdownSelect<T extends DropdownItem>({
  items,
  placeholder = "",
  value,
  onChange,
  getItemLabel,
  searchable = true,
  allowNew = true,
  showTick = true,
  multiple = false,
  reversed = false,
  showNewPopup = false,
  allowColors = false,
  divided = false,
  menuPortal = false,
  newItemComponent: NewItemComponent,
  newItemProps = {},
  newItemLabel = "Dodaj nowy",
  disabled = false,
  disabledItemIds = [],
  className = "",
  searchPlaceholder = "Szukaj...",
  emptyMessage = "Brak pozycji"
}: DropdownSelectProps<T>) {
  const [searchValue, setSearchValue] = useState<string>("");
  const [isDropdownVisible, setIsDropdownVisible] = useState<boolean>(false);
  const [isAddNewPopupOpen, setIsAddNewPopupOpen] = useState<boolean>(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const [portalStyle, setPortalStyle] = useState<React.CSSProperties | null>(null);
  const [portalUp, setPortalUp] = useState<boolean>(false);
  const [insideModal, setInsideModal] = useState<boolean>(false);

  useLayoutEffect(() => {
    setInsideModal(!!dropdownRef.current?.closest(".modal"));
  }, []);

  const effectivePortal = menuPortal || insideModal;

  const getSelectedValue = useCallback((): T[] => {
    if (!value) return [];
    return Array.isArray(value) ? value : [value];
  }, [value]);

  const selectedItems = getSelectedValue();

  const filteredItems = items.filter((item) => {
    const label = (getItemLabel ? getItemLabel(item) : item.name!).toLowerCase();
    const search = searchValue.toLowerCase();
    return label.split(" ").some((word) => word.startsWith(search));
  });

  const handleSelect = useCallback(
    (item: T) => {
      if (disabled) return;

      if (multiple) {
        const currentSelected = getSelectedValue();
        const isSelected = currentSelected.some((s) => s.id === item.id);

        const newSelected = isSelected
          ? currentSelected.filter((s) => s.id !== item.id)
          : [...currentSelected, item];

        onChange(newSelected);
      } else {
        const currentSelected = value as T | null;
        const newSelected = currentSelected?.id === item.id ? null : item;

        onChange(newSelected);
        setIsDropdownVisible(false);
      }
    },
    [multiple, value, onChange, disabled, getSelectedValue]
  );

  const handleOpenAddNewPopup = useCallback(() => {
    if (disabled) return;
    setIsAddNewPopupOpen(true);
  }, [disabled]);

  const handleCloseAddNewPopup = useCallback(() => {
    setIsAddNewPopupOpen(false);
  }, []);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as Node;
      const insideTrigger = dropdownRef.current?.contains(target);
      const insideMenu = menuRef.current?.contains(target);
      if (!insideTrigger && !insideMenu) {
        setIsDropdownVisible(false);
      }
    };
    document.addEventListener("pointerdown", handleClickOutside);
    return () => document.removeEventListener("pointerdown", handleClickOutside);
  }, []);

  const updatePortalPosition = useCallback(() => {
    const el = triggerRef.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const gap = 4;
    const spaceBelow = window.innerHeight - rect.bottom;
    const openUp = spaceBelow < 260 && rect.top > spaceBelow;
    setPortalUp(openUp);
    setPortalStyle({
      position: "fixed",
      left: rect.left,
      top: openUp ? rect.top - gap : rect.bottom + gap,
      width: rect.width,
      zIndex: 1100,
    });
  }, []);

  useLayoutEffect(() => {
    if (!effectivePortal || !isDropdownVisible || isAddNewPopupOpen) return;
    updatePortalPosition();
    const onReposition = () => updatePortalPosition();
    window.addEventListener("scroll", onReposition, true);
    window.addEventListener("resize", onReposition);
    return () => {
      window.removeEventListener("scroll", onReposition, true);
      window.removeEventListener("resize", onReposition);
    };
  }, [effectivePortal, isDropdownVisible, isAddNewPopupOpen, updatePortalPosition]);

  useLayoutEffect(() => {
    if (!effectivePortal || !portalStyle || !menuRef.current || !triggerRef.current) return;
    const margin = 8;
    const menuRect = menuRef.current.getBoundingClientRect();
    if (menuRect.right <= window.innerWidth - margin) return;
    const triggerRect = triggerRef.current.getBoundingClientRect();
    const left = Math.max(margin, triggerRect.right - menuRect.width);
    if (portalStyle.left === left) return;
    setPortalStyle((prev) => (prev ? { ...prev, left } : prev));
  }, [effectivePortal, portalStyle]);

  useEffect(() => {
    if (!isDropdownVisible) {
      setSearchValue("");
    }
  }, [isDropdownVisible]);

  const getItemText = (item: T): string =>
    getItemLabel ? getItemLabel(item) : (item.name || `${item.firstName ?? ""} ${item.lastName ?? ""}`.trim());

  const getDisplayText = (): string =>
    selectedItems.length === 0 ? placeholder : getItemText(selectedItems[0]);

  const moreCount = multiple ? selectedItems.length - 1 : 0;
  const selectedColor = allowColors || !multiple ? selectedItems[0]?.color : undefined;
  const canAddNew = allowNew && !!NewItemComponent;

  const isItemSelected = (item: T): boolean => {
    return selectedItems.some((s) => s.id === item.id);
  };

  const isItemDisabled = (item: T): boolean => {
    return disabledItemIds.includes(item.id);
  };

  const menuNode =
    isDropdownVisible && !isAddNewPopupOpen ? (
      <div
        ref={menuRef}
        className={`dropdown-menu ${reversed ? "reversed" : ""} ${divided ? "divided" : ""} ${!searchable && !allowNew ? "no-search" : ""} ${effectivePortal ? "portal" : "absolute mt-025"} ${className}`}
        style={
          effectivePortal
            ? {
                position: "static",
                margin: 0,
                transform: portalUp ? "translateY(-100%)" : undefined,
              }
            : undefined
        }
      >
        {(searchable || allowNew) && (
          <section className="dropdown-search-and-add-new flex align-items-center">
            <Search className="dropdown-search-icon" />
            <input
              type="text"
              className="dropdown-search transparent border-none"
              placeholder={searchPlaceholder}
              value={searchValue}
              onChange={(e) => setSearchValue(e.target.value)}
              onClick={(e) => e.stopPropagation()}
            />
            {canAddNew && (
              <ActionButton
                disableText
                disableBorder
                iconTitle={newItemLabel}
                className="dd-add"
                onClick={handleOpenAddNewPopup}
              >
                <Plus className="dl-icon icon-add-new" />
              </ActionButton>
            )}
          </section>
        )}
        <ul className={`dropdown-list m-0 p-0 ${divided ? "divided" : ""} ${className}`}>
          {filteredItems.length > 0 ? (
            filteredItems.map((item) => (
              <li
                key={item.id}
                className={`dropdown-item flex align-items-center space-between ${
                  isItemSelected(item) ? "selected" : ""
                } ${isItemDisabled(item) ? "disabled" : "pointer"} ${className}`}
                onClick={() => !isItemDisabled(item) && handleSelect(item)}
              >
                <div className={`dropdown-left flex align-items-center g-05  ${className}`}>
                  {item.color && (
                    <span
                      className="color-symbol"
                      style={{
                        backgroundColor: `rgb(${item.color})`,
                      }}
                    />
                  )}
                  <span className="dropdown-item-label" title={getItemLabel ? getItemLabel(item) : item.name}>
                    {getItemLabel ? getItemLabel(item) : item.name}
                  </span>
                </div>
                {showTick && isItemSelected(item) && (
                  <Check className={`dl-icon icon-check `} />
                )}
              </li>
            ))
          ) : (
            <li className="dropdown-empty flex-column align-items-center">
              <span className="dropdown-empty-text">
                {searchValue.trim() ? `Brak wyników dla „${searchValue.trim()}”` : emptyMessage}
              </span>
              {canAddNew && (
                <ActionButton
                  text={newItemLabel}
                  className="ag-btn ag-green dd-empty-add"
                  onClick={handleOpenAddNewPopup}
                >
                  <Plus className="dl-icon icon-add-new" />
                </ActionButton>
              )}
            </li>
          )}
        </ul>
      </div>
    ) : null;

  const portalRoot =
    typeof document !== "undefined"
      ? document.getElementById("portal-root")
      : null;

  return (
    <div className={`searchable-dropdown relative height-auto transparent border-none ${className}`} ref={dropdownRef}>
      <button
        ref={triggerRef}
        className={`dropdown-header flex space-between align-items-center transparent pointer ${className} ${disabled ? "disabled" : ""} ${
          allowColors && selectedItems.length > 0 ? "selected" : ""
        } `}
        onClick={() => !disabled && setIsDropdownVisible((prev) => !prev)}
        disabled={disabled}
      >
        <div className="dropdown-placeholder-wrapper width-max">
          <a
            className={`dropdown-header-a flex align-items-center justify-center ${className} ${((className === "categories" && multiple) ||
  (className !== "categories")) && (
              selectedItems.length > 0 ? "center" : "")
            }`}
          >
            {selectedColor && (
              <span
                className="dropdown-header-dot"
                style={{ backgroundColor: `rgb(${selectedColor})` }}
              />
            )}
            <span className="dropdown-header-text" title={selectedItems.length > 0 ? selectedItems.map(getItemText).join(", ") : undefined}>
              {getDisplayText()}
            </span>
            {moreCount > 0 && (
              <span className="dropdown-header-more">+{moreCount}</span>
            )}
          </a>
        </div>
        <ChevronDown
          className={`arrow-down ${isDropdownVisible ? "rotated" : ""} ${className}`}
        />
      </button>
      {effectivePortal
        ? menuNode && portalRoot
          ? ReactDOM.createPortal(
              <div
                className={`searchable-dropdown ${className}`}
                style={portalStyle ?? { position: "fixed", visibility: "hidden" }}
              >
                {menuNode}
              </div>,
              portalRoot
            )
          : null
        : menuNode}
      {showNewPopup && isAddNewPopupOpen && NewItemComponent && (
        <NewItemComponent
          onClose={handleCloseAddNewPopup}
          {...newItemProps}
          onReset={(created?: T) => {
            newItemProps.onReset?.(created);
            if (created) {
              setIsAddNewPopupOpen(false);
              setIsDropdownVisible(false);
            }
          }}
        />
      )}
    </div>
  );
}

export default DropdownSelect;
