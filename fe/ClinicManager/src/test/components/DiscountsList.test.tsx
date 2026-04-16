import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import DiscountsList from '../../components/Clients/DiscountsList';
import { RoleType, type JwtUser } from '../../models/login';
import type { Discount } from '../../models/visit';
import type { ListAttribute } from '../../constants/list-headers';

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

const attributes: ListAttribute[] = [
  { name: 'Opcje', width: '10%' },
];

const discounts: Discount[] = [
  { id: 1, name: 'VIP', percentageValue: 10, clientCount: 3 },
];

describe('DiscountsList — button visibility "Usuń Rabat"', () => {
  it('ADMIN sees button "Usuń Rabat"', () => {
    vi.mocked(useUser).mockReturnValue({
      user: makeUser([RoleType.ROLE_ADMIN]),
      setUser: vi.fn(),
      refreshUser: vi.fn(),
    });

    render(
      <DiscountsList
        attributes={attributes}
        items={discounts}
        setEditDiscountId={vi.fn()}
        setRemoveDiscountId={vi.fn()}
      />
    );

    expect(screen.getByRole('button', { name: 'Usuń Rabat' })).toBeInTheDocument();
  });

  it('USER does not see button "Usuń Rabat"', () => {
    vi.mocked(useUser).mockReturnValue({
      user: makeUser([RoleType.ROLE_USER]),
      setUser: vi.fn(),
      refreshUser: vi.fn(),
    });

    render(
      <DiscountsList
        attributes={attributes}
        items={discounts}
        setEditDiscountId={vi.fn()}
        setRemoveDiscountId={vi.fn()}
      />
    );

    expect(screen.queryByRole('button', { name: 'Usuń Rabat' })).not.toBeInTheDocument();
  });

  it('button "Edytuj Rabat" visible for both roles', () => {
    vi.mocked(useUser).mockReturnValue({
      user: makeUser([RoleType.ROLE_USER]),
      setUser: vi.fn(),
      refreshUser: vi.fn(),
    });

    render(
      <DiscountsList
        attributes={attributes}
        items={discounts}
        setEditDiscountId={vi.fn()}
        setRemoveDiscountId={vi.fn()}
      />
    );

    expect(screen.getByRole('button', { name: 'Edytuj Rabat' })).toBeInTheDocument();
  });

  it('renders a row for each discount', () => {
    vi.mocked(useUser).mockReturnValue({
      user: makeUser([RoleType.ROLE_ADMIN]),
      setUser: vi.fn(),
      refreshUser: vi.fn(),
    });

    const multipleDiscounts: Discount[] = [
      { id: 1, name: 'VIP', percentageValue: 10 },
      { id: 2, name: 'Senior', percentageValue: 15 },
    ];

    render(
      <DiscountsList
        attributes={attributes}
        items={multipleDiscounts}
        setEditDiscountId={vi.fn()}
        setRemoveDiscountId={vi.fn()}
      />
    );

    expect(screen.getAllByRole('button', { name: 'Usuń Rabat' })).toHaveLength(2);
  });
});
