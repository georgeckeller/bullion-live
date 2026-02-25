// Major Indices (displayed separately at top of Markets tab)
const majorIndices = [
  { symbol: 'SPY', name: 'S&P 500' },
  { symbol: 'DIA', name: 'Dow Jones' },
  { symbol: 'QQQ', name: 'Nasdaq' }
];

// Default Watchlist (18 symbols)
// Note: Index ETFs (SPY, DIA, QQQ) are tracked in Major Indices section
// Redundant ETFs (GLD, VOO, VTI) removed from defaults
const defaultWatchlist = [
  'AMD', 'GOOG', 'AMZN', 'AAPL', 'AMAT', 'ARM',
  'ASML', 'COST', 'DE', 'EQIX', 'HON', 'IBM',
  'INTC', 'META', 'MSFT', 'NVDA', 'PLTR', 'PANW',
  'RUM', 'TSLA', 'WMT'
];

const emptyWatchlist = [];

const singleItemWatchlist = ['AAPL'];

const unsortedWatchlist = ['TSLA', 'AAPL', 'MSFT', 'GOOG'];

const sortedWatchlist = ['AAPL', 'GOOG', 'MSFT', 'TSLA'];

module.exports = {
  majorIndices,
  defaultWatchlist,
  emptyWatchlist,
  singleItemWatchlist,
  unsortedWatchlist,
  sortedWatchlist
};
