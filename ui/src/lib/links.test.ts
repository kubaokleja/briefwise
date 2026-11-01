import { describe, expect, it } from 'vitest';
import { safeHref } from './links';

describe('safeHref', () => {
  it('keeps http and https URLs', () => {
    expect(safeHref('https://news.example.com/a?b=1')).toBe('https://news.example.com/a?b=1');
    expect(safeHref('http://example.com/')).toBe('http://example.com/');
  });

  it('drops script, data and relative URLs', () => {
    expect(safeHref('javascript:alert(1)')).toBeUndefined();
    expect(safeHref(' JavaScript:alert(1)')).toBeUndefined();
    expect(safeHref('data:text/html,<script>alert(1)</script>')).toBeUndefined();
    expect(safeHref('/api/news')).toBeUndefined();
    expect(safeHref('')).toBeUndefined();
    expect(safeHref(null)).toBeUndefined();
  });
});
