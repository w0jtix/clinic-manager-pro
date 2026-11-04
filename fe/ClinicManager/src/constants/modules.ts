import { Package, Banknote, Tag, Stethoscope, Users, ClipboardList, Calendar, Building2, Settings, CircleUserRound, Power, LucideIcon } from "lucide-react";

export interface MenuSubItem {
  name: string;
  module: string;
}

export interface MenuItem {
  name: string;
  href: string;
  icon: LucideIcon;
  permissions?: string[];
  subItems?: MenuSubItem[];
}

export const MENU_ITEMS: MenuItem[] = [
  { name: "Magazyn", href: '/', icon: Package, subItems: [
    { name: "Produkty", module: 'Products' },
    { name: "Raporty Stanu Mag.", module: 'InventoryReport' },
  ]},
  { name: "Kasetka", href: '/cash-ledger', icon: Banknote },
  { name: "Cennik", href: '/pricelist', icon: Tag},
  { name: "Usługi", href: '/services', icon: Stethoscope},
  { name: "Klienci", href: '/clients', icon: Users, subItems: [
    { name: "Lista Klientów", module: 'ClientList' },
    { name: "Opinie", module: 'Reviews' },
    { name: "Vouchery", module: 'Vouchers' },
    { name: "Zadłużenia", module: 'Debts' },
  ]},
  { name: "Karty Klienta", href: '/client-cards', icon: ClipboardList },
  { name: "Wizyty", href: '/visits', icon: Calendar },
  { name: "Firma", href: '/my-company', icon: Building2, permissions: ['ROLE_COMPANY'], subItems: [
    { name: "Faktury Kosztowe", module: 'Expenses' },
    { name: "KSeF", module: 'KSeF' },
    { name: "Pracownicy", module: 'Employees' },
    { name: "Premie", module: 'EmployeeBonus' },
    { name: "Statystyki", module: 'Statistics' },
    { name: "Ustawienia firmowe", module: 'CompanySettings' },
  ]},
  { name: "Ustawienia", href: '/settings', icon: Settings },
];

export type SubModuleType = 'Expenses' | 'Statistics' | 'Employees' | 'EmployeeBonus' | 'CompanySettings' | 'Products' | 'InventoryReport' | 'PriceListServices' | 'PriceListProducts' | 'KSeF' | 'ClientList' | 'Reviews' | 'Vouchers' | 'Debts';

export interface UserMenuItem {
  label: string;
  icon: LucideIcon;
  className?: string;
  permissions?: string[];
}

export const USER_MENU_ITEMS: UserMenuItem[] = [
  {
    label: "Profil",
    icon: CircleUserRound,
    className: "profile-icon"
  },
  {
    label: "Ustawienia",
    icon: Settings,
    className: "settings-icon",
    permissions: ['ROLE_COMPANY']
  },
  {
    label: "Wyloguj",
    icon: Power,
    className: "c-red logout-icon"
  }
];