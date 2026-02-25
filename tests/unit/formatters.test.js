describe('Price Formatters', () => {

  function formatPrice(value, assetType) {
    if (value === null || value === undefined) return '—';
    if (value === 0) return '$0';

    const formatter = new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: assetType === 'silver' || assetType === 'stock' ? 2 : 0,
      maximumFractionDigits: assetType === 'silver' || assetType === 'stock' ? 2 : 0
    });

    return formatter.format(value);
  }

  function formatChange(percent) {
    if (percent === null || percent === undefined) return '—';
    const sign = percent >= 0 ? '+' : '';
    return `${sign}${percent.toFixed(2)}%`;
  }

  function formatCompactNumber(value) {
    if (value >= 1000000) {
      return (value / 1000000).toFixed(1) + 'M';
    }
    if (value >= 1000) {
      return (value / 1000).toFixed(1) + 'K';
    }
    return value.toString();
  }

  describe('formatPrice', () => {
    test('formats gold with no decimals', () => {
      expect(formatPrice(2345.67, 'gold')).toBe('$2,346');
    });

    test('formats silver with 2 decimals', () => {
      expect(formatPrice(28.456, 'silver')).toBe('$28.46');
    });

    test('formats BTC with no decimals', () => {
      expect(formatPrice(43567.89, 'btc')).toBe('$43,568');
    });

    test('formats ETH with no decimals', () => {
      expect(formatPrice(2345.67, 'eth')).toBe('$2,346');
    });

    test('formats stocks with 2 decimals', () => {
      expect(formatPrice(156.789, 'stock')).toBe('$156.79');
    });

    test('handles zero', () => {
      expect(formatPrice(0, 'gold')).toBe('$0');
    });

    test('handles null gracefully', () => {
      expect(formatPrice(null, 'gold')).toBe('—');
    });

    test('handles undefined gracefully', () => {
      expect(formatPrice(undefined, 'gold')).toBe('—');
    });

    test('formats large numbers with commas', () => {
      expect(formatPrice(1234567.89, 'gold')).toBe('$1,234,568');
    });
  });

  describe('formatChange', () => {
    test('formats positive change with + sign', () => {
      expect(formatChange(1.23)).toBe('+1.23%');
    });

    test('formats negative change without extra sign', () => {
      expect(formatChange(-2.45)).toBe('-2.45%');
    });

    test('formats zero change with + sign', () => {
      expect(formatChange(0)).toBe('+0.00%');
    });

    test('formats to 2 decimal places', () => {
      expect(formatChange(1.234567)).toBe('+1.23%');
    });

    test('handles null gracefully', () => {
      expect(formatChange(null)).toBe('—');
    });

    test('handles small positive values', () => {
      expect(formatChange(0.01)).toBe('+0.01%');
    });

    test('handles small negative values', () => {
      expect(formatChange(-0.01)).toBe('-0.01%');
    });
  });

  describe('formatCompactNumber', () => {
    test('formats thousands', () => {
      expect(formatCompactNumber(1500)).toBe('1.5K');
    });

    test('formats millions', () => {
      expect(formatCompactNumber(2500000)).toBe('2.5M');
    });

    test('keeps small numbers as-is', () => {
      expect(formatCompactNumber(999)).toBe('999');
    });

    test('formats exact thousand', () => {
      expect(formatCompactNumber(1000)).toBe('1.0K');
    });

    test('formats exact million', () => {
      expect(formatCompactNumber(1000000)).toBe('1.0M');
    });
  });
});
