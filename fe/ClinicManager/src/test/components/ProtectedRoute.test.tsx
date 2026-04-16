import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { ProtectedRoute } from '../../components/ProtectedRoute';
import { RoleType, type JwtUser } from '../../models/login';

vi.mock('../../components/User/UserProvider', () => ({
  useUser: vi.fn(),
}));

import { useUser } from '../../components/User/UserProvider';

function makeUser(roles: RoleType[]): JwtUser {
  return {
    id: 1,
    username: 'testuser',
    token: 'fake-token',
    type: 'Bearer',
    avatar: '',
    roles,
    employee: null,
  };
}

function renderProtectedRoute(permissions: string[], user: JwtUser | undefined) {
  vi.mocked(useUser).mockReturnValue({
    user,
    setUser: vi.fn(),
    refreshUser: vi.fn(),
  });

  render(
    <MemoryRouter initialEntries={['/protected']}>
      <Routes>
        <Route
          path="/protected"
          element={
            <ProtectedRoute permissions={permissions}>
              <div>Treść tylko dla admina</div>
            </ProtectedRoute>
          }
        />
        <Route path="/login" element={<div>Strona logowania</div>} />
        <Route path="/no-access" element={<div>Brak dostępu</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('ProtectedRoute', () => {
  it('redirects to /login when user is not logged in', () => {
    renderProtectedRoute(['ROLE_ADMIN'], undefined);

    expect(screen.getByText('Strona logowania')).toBeInTheDocument();
    expect(screen.queryByText('Treść tylko dla admina')).not.toBeInTheDocument();
  });

  it('redirects to /no-access when USER tries to access ADMIN-only page', () => {
    renderProtectedRoute(['ROLE_ADMIN'], makeUser([RoleType.ROLE_USER]));

    expect(screen.getByText('Brak dostępu')).toBeInTheDocument();
    expect(screen.queryByText('Treść tylko dla admina')).not.toBeInTheDocument();
  });

  it('renders children when ADMIN accesses ROLE_ADMIN-only page', () => {
    renderProtectedRoute(['ROLE_ADMIN'], makeUser([RoleType.ROLE_ADMIN]));

    expect(screen.getByText('Treść tylko dla admina')).toBeInTheDocument();
  });

  it('renders children when route requires no permissions', () => {
    renderProtectedRoute([], makeUser([RoleType.ROLE_USER]));

    expect(screen.getByText('Treść tylko dla admina')).toBeInTheDocument();
  });
});
