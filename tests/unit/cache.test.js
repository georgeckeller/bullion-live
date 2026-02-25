describe('Cache Logic', () => {
  const CACHE_MAX_AGE = 5 * 60 * 1000;

  function isCacheValid(cache, maxAge) {
    if (!cache || !cache.data) return false;
    return (Date.now() - cache.timestamp) < maxAge;
  }

  test('returns false for null or empty cache', () => {
    expect(isCacheValid(null, CACHE_MAX_AGE)).toBe(false);
    expect(isCacheValid({ data: null, timestamp: Date.now() }, CACHE_MAX_AGE)).toBe(false);
  });

  test('returns true for fresh cache, false for stale', () => {
    const fresh = { data: { gold: 2000 }, timestamp: Date.now() };
    const stale = { data: { gold: 2000 }, timestamp: Date.now() - (6 * 60 * 1000) };
    expect(isCacheValid(fresh, CACHE_MAX_AGE)).toBe(true);
    expect(isCacheValid(stale, CACHE_MAX_AGE)).toBe(false);
  });

  test('boundary: valid at 4:59, invalid at 5:01', () => {
    const justUnder = { data: { gold: 2000 }, timestamp: Date.now() - (4 * 60 * 1000 + 59 * 1000) };
    const justOver = { data: { gold: 2000 }, timestamp: Date.now() - (5 * 60 * 1000 + 1000) };
    expect(isCacheValid(justUnder, CACHE_MAX_AGE)).toBe(true);
    expect(isCacheValid(justOver, CACHE_MAX_AGE)).toBe(false);
  });

  test('per-symbol stock cache works independently', () => {
    const stockCache = {
      'AAPL': { data: { price: 150 }, timestamp: Date.now() },
      'MSFT': { data: { price: 300 }, timestamp: Date.now() - (6 * 60 * 1000) }
    };
    expect(isCacheValid(stockCache['AAPL'], CACHE_MAX_AGE)).toBe(true);
    expect(isCacheValid(stockCache['MSFT'], CACHE_MAX_AGE)).toBe(false);
    expect(isCacheValid(stockCache['GOOGL'], CACHE_MAX_AGE)).toBe(false);
  });
});
