import { describe, expect, it } from 'vitest';
import { pageCount, paginate } from './pagination';

describe('paginate', () => {
  it('returns the requested slice', () => {
    const items = [1, 2, 3, 4, 5];
    expect(paginate(items, 0, 2)).toEqual([1, 2]);
    expect(paginate(items, 1, 2)).toEqual([3, 4]);
    expect(paginate(items, 2, 2)).toEqual([5]);
  });

  it('returns an empty array past the end', () => {
    expect(paginate([1, 2], 5, 10)).toEqual([]);
  });
});

describe('pageCount', () => {
  it('rounds up and never returns less than 1', () => {
    expect(pageCount(0, 10)).toBe(1);
    expect(pageCount(10, 10)).toBe(1);
    expect(pageCount(11, 10)).toBe(2);
    expect(pageCount(25, 10)).toBe(3);
  });
});
