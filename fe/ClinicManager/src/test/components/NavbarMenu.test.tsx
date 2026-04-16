import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { NavbarMenu } from '../../components/NavbarMenu';
import { RoleType, type JwtUser } from '../../models/login';

vi.mock('../../components/User/UserProvider', () => ({
  useUser: vi.fn(),
}));

import { useUser } from '../../components/User/UserProvider';

function makeUser(roles: RoleType[]): JwtUser {
  return { id: 1, username: 'testuser', token: 'x', type: 'Bearer', avatar: '', roles, employee: null };
}

function renderNavbar(roles: RoleType[]) {
  vi.mocked(useUser).mockReturnValue({ user: makeUser(roles), setUser: vi.fn(), refreshUser: vi.fn() });
  render(<MemoryRouter><NavbarMenu /></MemoryRouter>);
}

describe('NavbarMenu — menu item visibility for USER vs ADMIN', () => {
  it('USER does not see "Firma"', () => {
    renderNavbar([RoleType.ROLE_USER]);
    expect(screen.queryByText('Firma')).not.toBeInTheDocument();
  });

  it('USER does not see "Ustawienia"', () => {
    renderNavbar([RoleType.ROLE_USER]);
    expect(screen.queryByText('Ustawienia')).not.toBeInTheDocument();
  });

  it('ADMIN sees "Firma"', () => {
    renderNavbar([RoleType.ROLE_ADMIN]);
    expect(screen.getByText('Firma')).toBeInTheDocument();
  });

  it('ADMIN sees "Ustawienia"', () => {
    renderNavbar([RoleType.ROLE_ADMIN]);
    expect(screen.getByText('Ustawienia')).toBeInTheDocument();
  });

  it('USER sees standard menu items (Magazyn, Klienci, Wizyty)', () => {
    renderNavbar([RoleType.ROLE_USER]);
    expect(screen.getByText('Magazyn')).toBeInTheDocument();
    expect(screen.getByText('Klienci')).toBeInTheDocument();
    expect(screen.getByText('Wizyty')).toBeInTheDocument();
  });

  it('ADMIN also sees standard menu items', () => {
    renderNavbar([RoleType.ROLE_ADMIN]);
    expect(screen.getByText('Magazyn')).toBeInTheDocument();
    expect(screen.getByText('Klienci')).toBeInTheDocument();
    expect(screen.getByText('Wizyty')).toBeInTheDocument();
  });
});
