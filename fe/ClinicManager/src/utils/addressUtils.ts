export interface AddressParts {
  street: string;
  city: string;
  rest: string | null;
}

const ADDRESS_PATTERN = /^(.*?),?\s*(\d{2}-\d{3}[^,]*)(?:,\s*(.*))?$/;

export const splitAddress = (address: string): AddressParts | null => {
  const match = address.trim().match(ADDRESS_PATTERN);
  if (!match || !match[1].trim()) return null;
  return {
    street: match[1].trim(),
    city: match[2].trim(),
    rest: match[3]?.trim() || null,
  };
};
