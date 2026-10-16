import { describe, expect, it } from 'vitest';
import { eurosToCents, formatCents } from './money';

describe('eurosToCents', () => {
  it.each([
    ['100.50', 10050],
    ['100.5', 10050],
    ['100,50', 10050],
    ['100', 10000],
    ['0.01', 1],
    ['0.10', 10],
    ['1.00', 100],
    ['007.05', 705],
    [' 12.34 ', 1234],
    // Classic float traps: 0.1 + 0.2, 1.005 * 100, 19.99 * 100 = 1998.9999999999998
    ['0.30', 30],
    ['19.99', 1999],
    ['4.35', 435],
    ['1.15', 115],
    ['90071992547409.91', 9007199254740991],
  ])('converts %j to %d cents', (input, cents) => {
    expect(eurosToCents(input)).toBe(cents);
  });

  it.each([
    '',
    ' ',
    'abc',
    '1.234',
    '-5',
    '+5',
    '1e3',
    '.50',
    '5.',
    '1,000.00',
    '1 000',
    'NaN',
    'Infinity',
    '90071992547409.92', // beyond Number.MAX_SAFE_INTEGER cents
  ])('rejects %j', (input) => {
    expect(eurosToCents(input)).toBeNull();
  });
});

describe('formatCents', () => {
  it.each([
    [10050, 'EUR 100.50'],
    [1, 'EUR 0.01'],
    [100, 'EUR 1.00'],
    [0, 'EUR 0.00'],
  ])('formats %d', (cents, text) => {
    expect(formatCents(cents, 'EUR')).toBe(text);
  });
});
