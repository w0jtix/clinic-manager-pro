import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { UserMenu } from '../../components/UserMenu';
import { RoleType, type JwtUser } from '../../models/login';

vi.mock('../../components/User/UserProvider', () => ({
  useUser: vi.fn(),
}));

vi.mock('../../services/AuthService', () => ({
  default: {
    getCurrentUser: vi.fn().mockReturnValue(undefined),
    logout: vi.fn(),
  },
}));

import { useUser } from '../../components/User/UserProvider';

function makeUser(roles: RoleType[]): JwtUser {
  return { id: 1, username: 'testuser', token: 'x', type: 'Bearer', avatar: '', roles, employee: null };
}

function renderUserMenu(roles: RoleType[]) {
  vi.mocked(useUser).mockReturnValue({ user: makeUser(roles), setUser: vi.fn(), refreshUser: vi.fn() });
  render(<MemoryRouter><UserMenu username="testuser" /></MemoryRouter>);
}

async function openDropdown() {
  await userEvent.click(screen.getByRole('button'));
}

describe('UserMenu — dropdown item visibility for USER vs ADMIN', () => {
  it('dropdown is hidden by default', () => {
    renderUserMenu([RoleType.ROLE_USER]);

    expect(screen.queryByText('Profil')).not.toBeInTheDocument();
    expect(screen.queryByText('Wyloguj')).not.toBeInTheDocument();
  });

  it('USER sees "Profil" and "Wyloguj" after opening dropdown', async () => {
    renderUserMenu([RoleType.ROLE_USER]);
    await openDropdown();

    expect(screen.getByText('Profil')).toBeInTheDocument();
    expect(screen.getByText('Wyloguj')).toBeInTheDocument();
  });

  it('USER does not see "Ustawienia" in dropdown', async () => {
    renderUserMenu([RoleType.ROLE_USER]);
    await openDropdown();

    expect(screen.queryByText('Ustawienia')).not.toBeInTheDocument();
  });

  it('ADMIN sees "Ustawienia" in dropdown', async () => {
    renderUserMenu([RoleType.ROLE_ADMIN]);
    await openDropdown();

    expect(screen.getByText('Ustawienia')).toBeInTheDocument();
  });

  it('ADMIN sees all 3 items: "Profil", "Ustawienia", "Wyloguj"', async () => {
    renderUserMenu([RoleType.ROLE_ADMIN]);
    await openDropdown();

    expect(screen.getByText('Profil')).toBeInTheDocument();
    expect(screen.getByText('Ustawienia')).toBeInTheDocument();
    expect(screen.getByText('Wyloguj')).toBeInTheDocument();
  });
});
