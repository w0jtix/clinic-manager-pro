import { useState, useRef, useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import MenuItem from './MenuItem'
import { MENU_ITEMS } from '../constants/modules';
import { useUser } from './User/UserProvider';
import { RoleType } from '../models/login';

export interface NavbarMenuProps {
  expanded: boolean;
  onNavigate?: () => void;
}

export function NavbarMenu ({ expanded, onNavigate }: NavbarMenuProps) {
  const { user } = useUser();
  const location = useLocation();
  const [openMenu, setOpenMenu] = useState<string | null>(null);
  const navRef = useRef<HTMLElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (navRef.current && !navRef.current.contains(event.target as Node)) {
        setOpenMenu(null);
      }
    };
    document.addEventListener("pointerdown", handleClickOutside);
    return () => document.removeEventListener("pointerdown", handleClickOutside);
  }, []);

  const hasPermission = (requiredPermissions?: string[]): boolean => {
    if (!requiredPermissions || requiredPermissions.length === 0) {
      return true;
    }

    if (!user || !user.roles) {
      return false;
    }

    return requiredPermissions.some((permission) =>
      user.roles.includes(permission as RoleType)
    );
  };

  const visibleMenuItems = MENU_ITEMS.filter((item) => hasPermission(item.permissions));

  const activeModule = (location.state as { module?: string } | null)?.module ?? null;

  useEffect(() => {
    if (!expanded) {
      setOpenMenu(null);
    }
  }, [expanded]);

  const handleToggle = (name: string) => {
    setOpenMenu((prev) => (prev === name ? null : name));
  };

  return (
    <nav ref={navRef} className="navbar-menu flex-column width-max align-items-start f-1 min-height-0">
            {visibleMenuItems.map((item) => (
                <MenuItem
                    key={item.name}
                    name={item.name}
                    href={item.href}
                    src={item.icon}
                    alt={item.name}
                    subItems={item.subItems}
                    isOpen={openMenu === item.name}
                    active={item.href === location.pathname}
                    activeModule={activeModule}
                    onToggle={() => handleToggle(item.name)}
                    onNavigate={onNavigate}
                />
            ))}
    </nav>
  )
}

export default NavbarMenu
