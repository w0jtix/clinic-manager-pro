import { useNavigate } from 'react-router-dom'
import { ChevronDown, LucideIcon, Dot } from 'lucide-react'

export interface SubItem {
  name: string;
  module: string;
}

export interface MenuItemProps {
  name: string;
  href: string;
  src: LucideIcon;
  alt: string;
  subItems?: SubItem[];
  isOpen?: boolean;
  active?: boolean;
  activeModule?: string | null;
  onToggle?: () => void;
  onNavigate?: () => void;
}

export function MenuItem ({ name, href, src, alt, subItems, isOpen = false, active = false, activeModule = null, onToggle, onNavigate }: MenuItemProps) {
  const navigate = useNavigate();
  const Icon = src;

  if (!subItems || subItems.length === 0) {
    return (
      <button
        className={`menu-button width-max flex g-05 border-none justify-start align-items-center pointer ${active ? "active" : ""}`}
        onClick={() => {
          navigate(href);
          onNavigate?.();
        }}
      >
        <span className="menu-icon-slot flex align-items-center justify-center">
          <Icon className="menuItem-icon" size={21} aria-label={alt} />
        </span>
        <span className="menu-a">
          {name}
        </span>
      </button>
    );
  }

  const handleSubItemClick = (sub: SubItem) => {
    navigate(href, { state: { module: sub.module } });
    onToggle?.();
    onNavigate?.();
  };

  return (
    <div className="menu-item-with-sub width-max">
      <button
        className={`menu-button width-max flex justify-start border-none align-items-center pointer ${active ? "active" : ""}`}
        onClick={() => onToggle?.()}
      >
        <span className="menu-button-left flex f-1 g-05 align-items-center">
          <span className="menu-icon-slot flex align-items-center justify-center">
            <Icon className="menuItem-icon" size={21} aria-label={alt} />
          </span>
          <span className="menu-a">{name}</span>
        </span>
        <ChevronDown className={`menu-sub-arrow ${isOpen ? "rotated" : ""}`} size={18} />
      </button>
      {isOpen && (
        <div className="menu-sub-dropdown flex-column">
          {subItems.map((sub) => (
            <div key={sub.module} className={`menu-sub-div flex g-5px align-items-center pointer ${active && activeModule === sub.module ? "active" : ""}`} onClick={() => handleSubItemClick(sub)}>
              <Dot className="menu-subitem-icon" size={16} />
              <span className="menu-sub-item flex align-items-center">
                {sub.name}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default MenuItem
