import { describe, it, expect } from 'vitest';
import { canEdit, canSubmit, canPublish, canUnpublish, canDelete, type ProductStatus } from '../productActions';

const ALL: ProductStatus[] = ['DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'UNPUBLISHED', 'PUBLISHED'];

/** Assert a predicate is true for exactly `expected` and false for the rest. */
function onlyFor(fn: (s: ProductStatus) => boolean, expected: ProductStatus[]) {
  ALL.forEach(s => expect(fn(s)).toBe(expected.includes(s)));
}

describe('productActions (UC-PRODUCT lifecycle)', () => {
  it('canEdit is allowed in every state', () => {
    ALL.forEach(s => expect(canEdit(s)).toBe(true));
  });

  it('canSubmit only for DRAFT', () => onlyFor(canSubmit, ['DRAFT']));

  it('canPublish only for APPROVED or UNPUBLISHED', () => onlyFor(canPublish, ['APPROVED', 'UNPUBLISHED']));

  it('canUnpublish only for PUBLISHED', () => onlyFor(canUnpublish, ['PUBLISHED']));

  it('canDelete only for DRAFT or REJECTED', () => onlyFor(canDelete, ['DRAFT', 'REJECTED']));

  it('PENDING (awaiting admin) exposes no lifecycle action besides edit', () => {
    expect(canSubmit('PENDING')).toBe(false);
    expect(canPublish('PENDING')).toBe(false);
    expect(canUnpublish('PENDING')).toBe(false);
    expect(canDelete('PENDING')).toBe(false);
  });
});
