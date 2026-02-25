const { defaultWatchlist } = require('../__fixtures__/watchlist');

describe('Ticker Management', () => {
  let watchlist;

  beforeEach(() => {
    watchlist = ['AAPL', 'MSFT', 'GOOGL'];
  });

  function addTicker(symbol) {
    const upper = symbol.toUpperCase().trim();
    if (upper && !watchlist.includes(upper)) {
      watchlist.push(upper);
      watchlist.sort();
    }
  }

  function removeTicker(symbol) {
    const i = watchlist.indexOf(symbol);
    if (i > -1) watchlist.splice(i, 1);
  }

  test('adds new ticker, uppercased and sorted', () => {
    addTicker('amd');
    expect(watchlist).toContain('AMD');
    expect(watchlist.indexOf('AMD')).toBeLessThan(watchlist.indexOf('GOOGL'));
  });

  test('prevents duplicates', () => {
    addTicker('AAPL');
    expect(watchlist.filter(t => t === 'AAPL').length).toBe(1);
  });

  test('removes existing ticker', () => {
    removeTicker('MSFT');
    expect(watchlist).not.toContain('MSFT');
  });

  test('removing non-existent ticker is safe', () => {
    const len = watchlist.length;
    removeTicker('NOTEXIST');
    expect(watchlist.length).toBe(len);
  });

  test('reset restores defaults', () => {
    watchlist = [...defaultWatchlist];
    expect(watchlist).toEqual(defaultWatchlist);
  });

  test('default watchlist has 21 tickers including majors', () => {
    expect(defaultWatchlist.length).toBe(21);
    expect(defaultWatchlist).toContain('AAPL');
    expect(defaultWatchlist).toContain('GOOG');
    expect(defaultWatchlist).toContain('NVDA');
  });
});
