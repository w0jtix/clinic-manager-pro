import avatar5 from "../assets/avatars/avatar5.png";
import { Fragment, useState, useEffect, useRef, useCallback } from "react";
import {
  USER_MENU_ITEMS,
} from "../constants/modules";
import AuthService from "../services/AuthService";
import { useNavigate } from "react-router-dom";
import { AVAILABLE_AVATARS } from "../constants/avatars";
import { useUser } from "./User/UserProvider";
import { RoleType } from "../models/login";
import { ChevronDown, Zap, ZapOff } from "lucide-react";
import { useCompanyMode } from "../hooks/useCompanyMode";

export interface UserMenuProps {
  username?: string;
  avatar?: string;
  className?: string;
}

export function UserMenu({
  username,
  avatar,
  className = "",
}: UserMenuProps) {
  const [isMenuVisible, setMenuVisible] = useState<boolean>(false);
  const menuRef = useRef<HTMLDivElement>(null);

  const navigate = useNavigate();
  const { user } = useUser();
  const companyMode = useCompanyMode();

  const currentUser = AuthService.getCurrentUser();
  const displayUsername = username ?? currentUser?.username;
  const displayAvatar =
    avatar
      ? AVAILABLE_AVATARS[avatar]
      : currentUser?.avatar
      ? AVAILABLE_AVATARS[currentUser.avatar]
      : avatar5;

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

  const visibleUserMenuItems = USER_MENU_ITEMS.filter((item) =>
    hasPermission(item.permissions)
  );

   const handleAction = (label: string) => {
    switch (label) {
      case "Profil":
        navigate("/profile");
        break;
      case "Ustawienia":
        navigate("/settings");
        break;
      case "Wyloguj":
        AuthService.logout();
        break;
    }
    setMenuVisible(false);
  };

  const handleCompanyMode = () => {
    setMenuVisible(false);
    if (companyMode.active) {
      companyMode.dropCompanyMode();
    } else {
      companyMode.openPinModal();
    }
  };

  const toggleMenu = useCallback(() => {
    setMenuVisible((prev) => !prev);
  }, []);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setMenuVisible(false);
      }
    };
    document.addEventListener("pointerdown", handleClickOutside);

    return () => {
      document.removeEventListener("pointerdown", handleClickOutside);
    };
  }, []);

  return (
    <div className={`user-menu flex-column justify-center height-auto transparent border-none relative ${className}`} ref={menuRef}>
      <button
        className={`dropdown-button flex justify-center align-items-center transparent pointer g-10px border-none ${
          companyMode.active ? "company" : ""
        }`}
        onClick={toggleMenu}
        title={companyMode.active ? "Tryb firmowy aktywny" : undefined}
      >
        <img
          src={displayAvatar}
          alt={`${username} profile picture`}
          className="user-pfp"
        />
        {companyMode.active && <Zap className="user-company-zap" aria-label="Tryb firmowy aktywny" />}
        <h2 className="username">{displayUsername}</h2>
        <ChevronDown
                  className={`arrow-down ${isMenuVisible ? "rotated" : ""} ${className}`}
                />
      </button>
      {isMenuVisible && (
        <div className="dropdown-menu user absolute mt-05" onClick={(e) => e.stopPropagation()}>
          <ul className="user-menu-dropdown-list m-0 p-0">
            {visibleUserMenuItems.map((item, index) => {
              const Icon = item.icon;
              return (
                <Fragment key={item.label}>
                  {index === 0 && companyMode.available && (
                    <li
                      className={`dropdown-item-user-menu company-mode-item pointer flex align-items-center justify-start ${
                        companyMode.active ? "leave" : ""
                      }`}
                      onClick={handleCompanyMode}
                    >
                      {companyMode.active ? (
                        <ZapOff className="dl-icon-1" aria-hidden="true" />
                      ) : (
                        <Zap className="dl-icon-1" aria-hidden="true" />
                      )}
                      {companyMode.active ? "Tryb pracowniczy" : "Tryb firmowy"}
                    </li>
                  )}
                  <li
                    className="dropdown-item-user-menu pointer flex align-items-center justify-start"
                    onClick={() => handleAction(item.label)}
                  >
                    <Icon className={`dl-icon-1 ${item.className}`} aria-label={item.label} />
                    {item.label}
                  </li>
                </Fragment>
              );
            })}
          </ul>
        </div>
      )}
      {companyMode.pinModal}
    </div>
  );
}

export default UserMenu;
