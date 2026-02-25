/**
 * BULLION LIVE - Real-Time Financial Asset Tracker
 *
 * ARCHITECTURE:
 * - Single-page app with 3 tabs: Metals, Crypto, Markets
 * - Three-tier caching (persistent + in-memory Kotlin + in-memory JS), 1-min fresh / 15-min stale
 * - Lazy loading: fetch data only when tab becomes active
 * - Touch-enabled: swipe gestures for tab navigation (110px threshold)
 *
 * DATA SOURCES:
 * - Metals (Gold/Silver): GoldPrice.org - no auth, CORS-friendly
 *   Endpoint: data-asg.goldprice.org/dbXRates/USD
 *   Response: { items: [{ xauPrice, xagPrice, xauClose, xagClose, pcXau, pcXag }] }
 *
 * - Crypto (BTC/ETH) & Stocks: Finnhub - requires API key
 *   Endpoint: finnhub.io/api/v1/quote?symbol=X&token=KEY
 *   Response: { c: current, pc: prevClose, dp: changePercent }
 *
 * STATE MANAGEMENT:
 * - CONFIG: API endpoints, intervals, timeouts
 * - priceCache: { metals/crypto/stocks: { data, timestamp } }
 * - localStorage key "bullion_tickers_v2": watchlist persistence
 *
 * KEY FUNCTIONS:
 * - fetchMetals/fetchCrypto/fetchMarkets: Data fetching with cache
 * - updateAssetCard: UI update for price display cards
 * - renderTickers: Watchlist grid with sorting and delete
 * - switchPage: Tab navigation with lazy data loading
 *
 * UI COMPONENTS:
 * - Asset cards: price, change %, status indicator (dot)
 * - Ticker grid: sorted A-Z, editable watchlist
 * - Modal: add symbols, reset to defaults
 *
 * @see README.md for full architecture and API documentation
 */
(function () {
  'use strict';

  const CONFIG = {
    api: {
      goldPrice: 'https://data-asg.goldprice.org/dbXRates/USD',
      finnhub: 'https://finnhub.io/api/v1',
      finnhubKey: 'YOUR_FINNHUB_API_KEY' // Replaced dynamically on init if native bridge is present
    },
    intervals: {
      metals: 15000,    // 15s — GoldPriceApi, no rate limit
      crypto: 30000,    // 30s — Finnhub, cache-deduped with native
      stocks: 60000     // 60s — matches CACHE_FRESH_MS, 24 symbols per fetch
    },
    timeout: 5000,
    cacheMaxAge: 1 * 60 * 1000, // 1 minute TTL to match native cache
    delays: {
      cryptoInit: 1000,       // Delay before first crypto fetch (ms)
      marketsInit: 2000,      // Delay before first markets fetch (ms)
      marketsDebounce: 500    // Debounce for markets fetch (ms)
    },
    ui: {
      swipeThreshold: 95,     // Minimum px for swipe gesture
      swipeRatio: 1.5         // Horizontal must exceed vertical by this factor
    },
    validation: {
      maxChangePercent: 50    // Hide stocks with change > this (sanity check)
    }
  };

  // ===== Security: HTML entity escaping to prevent XSS =====
  function escapeHtml(str) {
    const map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
    return String(str).replace(/[&<>"']/g, c => map[c]);
  }

  // Unified cache interface - uses native bridge when available, falls back to in-memory
  const priceCache = {
    metals: { data: null, timestamp: 0 },
    crypto: { data: null, timestamp: 0 },
    stocks: {}
  };

  // Check if native bridge is available
  function hasNativeBridge() {
    return typeof window.BullionCache !== 'undefined';
  }

  function isCacheValid(timestamp) {
    return timestamp && (Date.now() - timestamp) < CONFIG.cacheMaxAge;
  }

  // Unified cache getters - check native bridge first, then in-memory
  function getCachedMetals() {
    if (hasNativeBridge()) {
      try {
        const cached = window.BullionCache.getCachedMetals();
        const timestamp = window.BullionCache.getMetalsTimestamp();
        if (cached && timestamp) {
          const data = JSON.parse(cached);
          return { data: data, timestamp: timestamp };
        }
      } catch (e) {
        console.warn('[Bullion] Bridge error getting metals:', e);
      }
    }
    // Fallback to in-memory cache
    return priceCache.metals.data ? { data: priceCache.metals.data, timestamp: priceCache.metals.timestamp } : null;
  }

  function getCachedCrypto() {
    if (hasNativeBridge()) {
      try {
        const cached = window.BullionCache.getCachedCrypto();
        const timestamp = window.BullionCache.getCryptoTimestamp();
        if (cached && timestamp) {
          const data = JSON.parse(cached);
          return { data: data, timestamp: timestamp };
        }
      } catch (e) {
        console.warn('[Bullion] Bridge error getting crypto:', e);
      }
    }
    // Fallback to in-memory cache
    return priceCache.crypto.data ? { data: priceCache.crypto.data, timestamp: priceCache.crypto.timestamp } : null;
  }

  function getCachedStock(symbol) {
    if (hasNativeBridge()) {
      try {
        const cached = window.BullionCache.getCachedStock(symbol);
        const timestamp = window.BullionCache.getStockTimestamp(symbol);
        if (cached && timestamp) {
          const data = JSON.parse(cached);
          return { data: data, timestamp: timestamp };
        }
      } catch (e) {
        console.warn(`[Bullion] Bridge error getting stock ${symbol}:`, e);
      }
    }
    // Fallback to in-memory cache
    const cached = priceCache.stocks[symbol];
    return cached && cached.data ? { data: cached.data, timestamp: cached.timestamp } : null;
  }

  // Unified cache setters - save to native bridge and in-memory
  function saveCachedMetals(data) {
    // Save to native bridge
    if (hasNativeBridge()) {
      try {
        const json = JSON.stringify({
          goldPrice: data.xauPrice,
          goldPreviousClose: data.xauClose || data.xauPrice,
          goldChangePercent: data.pcXau || 0,
          silverPrice: data.xagPrice,
          silverPreviousClose: data.xagClose || data.xagPrice,
          silverChangePercent: data.pcXag || 0
        });
        window.BullionCache.saveMetals(json);
      } catch (e) {
        console.warn('[Bullion] Bridge error saving metals:', e);
      }
    }
    // Also save to in-memory cache
    priceCache.metals.data = data;
    priceCache.metals.timestamp = Date.now();
  }

  function saveCachedCrypto(btcData, ethData) {
    // Save to native bridge
    if (hasNativeBridge()) {
      try {
        const json = JSON.stringify({
          btcPrice: btcData.c,
          btcPrevClose: btcData.pc || btcData.c,
          btcChangePercent: btcData.dp || 0,
          ethPrice: ethData.c,
          ethPrevClose: ethData.pc || ethData.c,
          ethChangePercent: ethData.dp || 0
        });
        window.BullionCache.saveCrypto(json);
      } catch (e) {
        console.warn('[Bullion] Bridge error saving crypto:', e);
      }
    }
    // Also save to in-memory cache
    priceCache.crypto.data = { btcData, ethData };
    priceCache.crypto.timestamp = Date.now();
  }

  function saveCachedStock(symbol, data) {
    // Save to native bridge
    if (hasNativeBridge()) {
      try {
        const json = JSON.stringify({
          price: data.price,
          prevClose: data.previousClose,
          changePercent: data.percent
        });
        window.BullionCache.saveStock(symbol, json);
      } catch (e) {
        console.warn(`[Bullion] Bridge error saving stock ${symbol}:`, e);
      }
    }
    // Also save to in-memory cache
    if (!priceCache.stocks[symbol]) {
      priceCache.stocks[symbol] = {};
    }
    priceCache.stocks[symbol].data = data;
    priceCache.stocks[symbol].timestamp = Date.now();
  }

  const DEFAULT_TICKERS = [
    { symbol: 'AMD', name: 'AMD' },
    { symbol: 'GOOG', name: 'Google' },
    { symbol: 'AMZN', name: 'Amazon' },
    { symbol: 'AAPL', name: 'Apple' },
    { symbol: 'AMAT', name: 'Applied Materials' },
    { symbol: 'ARM', name: 'ARM Holdings' },
    { symbol: 'ASML', name: 'ASML' },
    { symbol: 'COST', name: 'Costco' },
    { symbol: 'DE', name: 'Deere & Co' },
    { symbol: 'EQIX', name: 'Equinix' },
    { symbol: 'HON', name: 'Honeywell' },
    { symbol: 'IBM', name: 'IBM' },
    { symbol: 'INTC', name: 'Intel' },
    { symbol: 'META', name: 'Meta' },
    { symbol: 'MSFT', name: 'Microsoft' },
    { symbol: 'NVDA', name: 'NVIDIA' },
    { symbol: 'PLTR', name: 'Palantir' },
    { symbol: 'PANW', name: 'Palo Alto Networks' },
    { symbol: 'RUM', name: 'Rumble' },
    { symbol: 'TSLA', name: 'Tesla' },
    { symbol: 'WMT', name: 'Walmart' },
    { symbol: 'SPY', name: 'S&P 500' },
    { symbol: 'DIA', name: 'Dow Jones' },
    { symbol: 'QQQ', name: 'NASDAQ' }
  ];

  const state = {
    tickers: [],
    tickerData: [],
    sortAscending: false,
    metalsLoaded: false,
    cryptoLoaded: false,
    marketsLoaded: false,
    lastUpdated: {
      metals: null,
      crypto: null,
      markets: null
    }
  };

  const $ = (sel) => document.querySelector(sel);
  const $$ = (sel) => document.querySelectorAll(sel);

  function formatPrice(value, decimals = 2) {
    if (typeof value !== 'number' || isNaN(value)) return '---.--';
    return value.toLocaleString('en-US', {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals
    });
  }

  function calculateChange(current, previous) {
    if (!previous || previous === 0) return { change: 0, percent: 0 };
    const change = current - previous;
    const percent = (change / previous) * 100;
    return { change, percent };
  }

  function formatChange(change, percent) {
    const sign = change >= 0 ? '+' : '';
    return `${sign}${change.toFixed(2)} (${sign}${percent.toFixed(2)}%)`;
  }

  function getChangeClass(value) {
    if (value > 0) return 'positive';
    if (value < 0) return 'negative';
    return 'neutral';
  }

  function formatTimestamp(date) {
    if (!date) return '';
    return date.toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  }

  function loadTickers() {
    try {
      const saved = localStorage.getItem('bullion_tickers_v2');
      if (saved) {
        const parsed = JSON.parse(saved);
        if (Array.isArray(parsed) && parsed.length > 0) {
          state.tickers = parsed;
          return;
        }
      }
    } catch (e) {
      console.error('[Bullion] Failed to load tickers:', e);
    }
    state.tickers = [...DEFAULT_TICKERS];
  }

  function saveTickers() {
    try {
      localStorage.setItem('bullion_tickers_v2', JSON.stringify(state.tickers));
    } catch (e) {
      console.error('Failed to save tickers:', e);
    }
  }

  function resetToDefaults() {
    state.tickers = [...DEFAULT_TICKERS];
    state.tickerData = [];
    state.marketsLoaded = false;
    priceCache.stocks = {};
    saveTickers();
    renderTickerChips();
    hideModal();
    fetchMarketsDebounced();
  }

  async function fetchWithTimeout(url, timeout = CONFIG.timeout) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeout);
    try {
      const response = await fetch(url, { signal: controller.signal });
      clearTimeout(timeoutId);
      return response;
    } catch (e) {
      clearTimeout(timeoutId);
      throw e;
    }
  }

  async function fetchJSON(url) {
    const response = await fetchWithTimeout(url);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return response.json();
  }

  async function fetchMetals() {
    const statusDot = $('#metals-status .status-dot');
    const statusText = $('#metals-status .status-text');
    const statusTimestamp = $('#metals-status .status-timestamp');

    // Check unified cache first
    const cached = getCachedMetals();
    if (cached && isCacheValid(cached.timestamp)) {
      const item = cached.data;
      const age = Math.floor((Date.now() - cached.timestamp) / 1000);

      // Convert from bridge format if needed
      const xauPrice = item.xauPrice || item.goldPrice;
      const xagPrice = item.xagPrice || item.silverPrice;
      const xauClose = item.xauClose || item.goldPreviousClose || xauPrice;
      const xagClose = item.xagClose || item.silverPreviousClose || xagPrice;

      updateAssetCard('gold', xauPrice, xauClose, 2);
      updateAssetCard('silver', xagPrice, xagClose, 2);

      statusDot.className = 'status-dot live';
      statusText.textContent = `Cached (${Math.floor(age / 60)}m ${age % 60}s)`;
      statusTimestamp.textContent = 'Updated: ' + formatTimestamp(state.lastUpdated.metals);
      return;
    }

    const sources = [
      {
        name: 'GoldPrice.org',
        url: CONFIG.api.goldPrice,
        fetch: async (url) => {
          const data = await fetchJSON(url);
          if (data?.items?.[0]) {
            const item = data.items[0];
            return {
              xauPrice: item.xauPrice,
              xauClose: item.xauClose || item.xauPrice,
              pcXau: item.pcXau || 0,
              xagPrice: item.xagPrice,
              xagClose: item.xagClose || item.xagPrice,
              pcXag: item.pcXag || 0
            };
          }
          throw new Error('Invalid GoldPrice.org format');
        }
      },
      {
        name: 'Swissquote',
        url: 'https://forex-data-feed.swissquote.com/public-quotes/bboquotes/instrument/',
        fetch: async (baseUrl) => {
          const [goldResp, silverResp] = await Promise.all([
            fetchJSON(baseUrl + 'XAU/USD'),
            fetchJSON(baseUrl + 'XAG/USD')
          ]);
          const goldPrice = goldResp?.[0]?.spreadProfilePrices?.[0]?.bid;
          const silverPrice = silverResp?.[0]?.spreadProfilePrices?.[0]?.bid;

          if (goldPrice && silverPrice) {
            return {
              xauPrice: goldPrice,
              xauClose: goldPrice, // Swissquote public doesn't easily provide prev close
              xagPrice: silverPrice,
              xagClose: silverPrice
            };
          }
          throw new Error('Invalid Swissquote format');
        }
      }
    ];

    for (const source of sources) {
      try {
        const result = await source.fetch(source.url);

        // Save to unified cache
        saveCachedMetals(result);

        updateAssetCard('gold', result.xauPrice, result.xauClose, 2);
        updateAssetCard('silver', result.xagPrice, result.xagClose, 2);

        state.metalsLoaded = true;
        state.lastUpdated.metals = new Date();

        statusDot.className = 'status-dot live';
        statusText.textContent = source.name === 'GoldPrice.org' ? 'Live' : `Live (${source.name})`;
        statusTimestamp.textContent = 'Updated: ' + formatTimestamp(state.lastUpdated.metals);
        return; // Success
      } catch (error) {
        console.warn(`[Bullion] ${source.name} fetch error:`, error);
      }
    }

    // If we reach here, all sources failed
    statusDot.className = 'status-dot error';
    statusText.textContent = 'Connection error';
  }

  async function fetchCrypto() {
    const statusDot = $('#crypto-status .status-dot');
    const statusText = $('#crypto-status .status-text');
    const statusTimestamp = $('#crypto-status .status-timestamp');

    // Check unified cache first
    const cached = getCachedCrypto();
    if (cached && isCacheValid(cached.timestamp)) {
      const data = cached.data;
      const age = Math.floor((Date.now() - cached.timestamp) / 1000);

      // Convert from bridge format if needed
      if (data.btcPrice) {
        // Bridge format
        updateAssetCard('btc', data.btcPrice, data.btcPrevClose || data.btcPrice, 0);
        updateAssetCard('eth', data.ethPrice, data.ethPrevClose || data.ethPrice, 2);
      } else {
        // In-memory format
        const { btcData, ethData } = data;
        if (btcData && btcData.c && btcData.c > 0) {
          updateAssetCard('btc', btcData.c, btcData.pc || btcData.c, 0);
        }
        if (ethData && ethData.c && ethData.c > 0) {
          updateAssetCard('eth', ethData.c, ethData.pc || ethData.c, 2);
        }
      }

      statusDot.className = 'status-dot live';
      statusText.textContent = `Cached (${Math.floor(age / 60)}m ${age % 60}s)`;
      statusTimestamp.textContent = 'Updated: ' + formatTimestamp(state.lastUpdated.crypto);
      return;
    }

    try {
      const [btcResponse, ethResponse] = await Promise.all([
        fetchWithTimeout(`${CONFIG.api.finnhub}/quote?symbol=BINANCE:BTCUSDT&token=${CONFIG.api.finnhubKey}`),
        fetchWithTimeout(`${CONFIG.api.finnhub}/quote?symbol=BINANCE:ETHUSDT&token=${CONFIG.api.finnhubKey}`)
      ]);

      const btcData = await btcResponse.json();
      const ethData = await ethResponse.json();

      // Save to unified cache
      saveCachedCrypto(btcData, ethData);

      if (btcData.c && btcData.c > 0) {
        updateAssetCard('btc', btcData.c, btcData.pc || btcData.c, 0);
      }

      if (ethData.c && ethData.c > 0) {
        updateAssetCard('eth', ethData.c, ethData.pc || ethData.c, 2);
      }

      state.cryptoLoaded = true;
      state.lastUpdated.crypto = new Date();
      statusDot.className = 'status-dot live';
      statusText.textContent = 'Live';
      statusTimestamp.textContent = 'Updated: ' + formatTimestamp(state.lastUpdated.crypto);

    } catch (error) {
      console.error('Crypto fetch error:', error);
      statusDot.className = 'status-dot error';
      statusText.textContent = 'Connection error';
    }
  }

  async function fetchStockQuote(symbol) {
    // Check unified cache first
    const cached = getCachedStock(symbol);
    if (cached && isCacheValid(cached.timestamp)) {
      // Convert from bridge format if needed
      if (cached.data.price !== undefined) {
        // Bridge format
        return {
          price: cached.data.price,
          previousClose: cached.data.prevClose,
          percent: cached.data.changePercent
        };
      }
      // In-memory format
      return cached.data;
    }

    try {
      const url = `${CONFIG.api.finnhub}/quote?symbol=${symbol}&token=${CONFIG.api.finnhubKey}`;
      const response = await fetchWithTimeout(url);
      if (!response.ok) return null;
      const data = await response.json();
      if (data.c && data.pc && data.c > 0) {
        const { percent } = calculateChange(data.c, data.pc);
        const result = { price: data.c, previousClose: data.pc, percent: percent };
        // Save to unified cache
        saveCachedStock(symbol, result);
        return result;
      }
    } catch (e) {
      console.error(`Finnhub error for ${symbol}:`, e);
    }
    return null;
  }

  let fetchMarketsTimer = null;
  let isFetchingMarkets = false;

  function fetchMarketsDebounced() {
    if (fetchMarketsTimer) clearTimeout(fetchMarketsTimer);
    fetchMarketsTimer = setTimeout(() => {
      fetchMarketsTimer = null;
      fetchMarkets();
    }, CONFIG.delays.marketsDebounce);
  }

  async function fetchMarkets() {
    if (isFetchingMarkets) {
      return;
    }
    isFetchingMarkets = true;

    const statusDot = $('#markets-status .status-dot');
    const statusText = $('#markets-status .status-text');
    const statusTimestamp = $('#markets-status .status-timestamp');
    const tickerList = $('#tickerList');

    const allCached = state.tickers.every(ticker => {
      const cached = priceCache.stocks[ticker.symbol];
      return cached && isCacheValid(cached.timestamp);
    });

    if (allCached && state.tickers.length > 0) {
      let oldestTimestamp = Date.now();
      state.tickers.forEach(ticker => {
        const cached = priceCache.stocks[ticker.symbol];
        if (cached && cached.timestamp < oldestTimestamp) {
          oldestTimestamp = cached.timestamp;
        }
      });
    }

    if (!state.marketsLoaded || state.tickerData.length === 0) {
      tickerList.innerHTML = '<div class="ticker-loading">Loading...</div>';
    }

    statusDot.className = 'status-dot';
    statusText.textContent = 'Loading...';
    let hasValidationIssue = false;
    let cachedCount = 0;
    let freshCount = 0;

    try {
      const results = await Promise.allSettled(
        state.tickers.map(async (ticker) => {
          const wasCached = priceCache.stocks[ticker.symbol] &&
            isCacheValid(priceCache.stocks[ticker.symbol].timestamp);

          const quoteData = await fetchStockQuote(ticker.symbol);

          if (wasCached) cachedCount++;
          else freshCount++;

          if (quoteData && quoteData.price && quoteData.previousClose) {
            const price = quoteData.price;
            const prevClose = quoteData.previousClose;

            if (price <= 0 || prevClose <= 0 || isNaN(price) || isNaN(prevClose)) {
              return null;
            }

            const { change, percent } = calculateChange(price, prevClose);

            if (Math.abs(percent) > CONFIG.validation.maxChangePercent) {
              hasValidationIssue = true;
              return null;
            }

            return {
              symbol: ticker.symbol,
              name: ticker.name,
              price: price,
              change,
              percent
            };
          }
          return null;
        })
      );

      state.tickerData = results
        .filter(r => r.status === 'fulfilled' && r.value !== null)
        .map(r => r.value);

      if (state.tickerData.length > 0) {
        renderTickers();
        state.marketsLoaded = true;
        state.lastUpdated.markets = new Date();

        if (hasValidationIssue) {
          statusDot.className = 'status-dot warning';
          statusText.textContent = 'Some stocks hidden (validation)';
        } else if (cachedCount > 0 && freshCount === 0) {
          statusDot.className = 'status-dot live';
          statusText.textContent = `Cached (${cachedCount} stocks)`;
        } else if (cachedCount > 0) {
          statusDot.className = 'status-dot live';
          statusText.textContent = `Live (${freshCount} fetched, ${cachedCount} cached)`;
        } else {
          statusDot.className = 'status-dot live';
          statusText.textContent = 'Live';
        }
        statusTimestamp.textContent = 'Updated: ' + formatTimestamp(state.lastUpdated.markets);
      } else {
        tickerList.innerHTML = '<div class="ticker-loading">No data available</div>';
        statusDot.className = 'status-dot error';
        statusText.textContent = 'No data';
      }
    } catch (error) {
      console.error('Markets fetch error:', error);
      tickerList.innerHTML = '<div class="ticker-loading">Connection error</div>';
      statusDot.className = 'status-dot error';
      statusText.textContent = 'Connection error';
    } finally {
      isFetchingMarkets = false;
    }
  }

  function updateAssetCard(asset, current, previous, decimals) {
    const card = $(`[data-asset="${asset}"]`);
    if (!card) return;

    const { change, percent } = calculateChange(current, previous);
    const changeClass = getChangeClass(change);

    card.querySelector('.asset-bar').className = `asset-bar ${changeClass}`;

    const priceEl = card.querySelector('.asset-price');
    priceEl.textContent = formatPrice(current, decimals);
    priceEl.classList.remove('loading', 'error');

    const changeEl = card.querySelector('.asset-change');
    changeEl.className = `asset-change ${changeClass}`;
    changeEl.querySelector('.change-value').textContent = formatChange(change, percent);
  }

  function isIndex(ticker) {
    // Only recognize major indices: SPY (S&P 500), DIA (Dow Jones), QQQ (NASDAQ)
    const symbol = (ticker.symbol || '').toUpperCase();
    return symbol === 'SPY' || symbol === 'DIA' || symbol === 'QQQ';
  }

  function renderTickers() {
    const tickerList = $('#tickerList');

    const sorted = [...state.tickerData].sort((a, b) => {
      // Group indexes at the top
      const aIsIndex = isIndex(a);
      const bIsIndex = isIndex(b);

      if (aIsIndex && !bIsIndex) return -1;
      if (!aIsIndex && bIsIndex) return 1;

      // Within same group, sort by percentage change
      return state.sortAscending ? a.percent - b.percent : b.percent - a.percent;
    });

    // Separate indexes and stocks
    const indexes = sorted.filter(t => isIndex(t));
    const stocks = sorted.filter(t => !isIndex(t));

    let html = '';

    // Render indexes
    if (indexes.length > 0) {
      html += indexes.map(ticker => {
        const changeClass = getChangeClass(ticker.percent);
        const arrow = ticker.percent >= 0 ? '▲' : '▼';
        const sign = ticker.percent >= 0 ? '+' : '';
        const priceDisplay = ticker.price >= 1000
          ? formatPrice(ticker.price, 0)
          : formatPrice(ticker.price, 2);

        return `
              <div class="ticker-row index-fund">
                <div class="ticker-info">
                  <button class="ticker-delete" data-symbol="${escapeHtml(ticker.symbol)}">✕</button>
                  <span class="ticker-symbol">${escapeHtml(ticker.name)}</span>
                </div>
                <span class="ticker-price">${priceDisplay}</span>
                <span class="ticker-change ${changeClass}">${arrow} ${sign}${ticker.percent.toFixed(2)}%</span>
              </div>
            `;
      }).join('');
    }

    // Add hashed red divider if both sections exist
    if (indexes.length > 0 && stocks.length > 0) {
      html += '<div class="ticker-divider-hashed"></div>';
    }

    // Render stocks
    if (stocks.length > 0) {
      html += stocks.map(ticker => {
        const changeClass = getChangeClass(ticker.percent);
        const arrow = ticker.percent >= 0 ? '▲' : '▼';
        const sign = ticker.percent >= 0 ? '+' : '';
        const priceDisplay = ticker.price >= 1000
          ? formatPrice(ticker.price, 0)
          : formatPrice(ticker.price, 2);

        return `
              <div class="ticker-row">
                <div class="ticker-info">
                  <button class="ticker-delete" data-symbol="${escapeHtml(ticker.symbol)}">✕</button>
                  <span class="ticker-symbol">${escapeHtml(ticker.name)}</span>
                </div>
                <span class="ticker-price">${priceDisplay}</span>
                <span class="ticker-change ${changeClass}">${arrow} ${sign}${ticker.percent.toFixed(2)}%</span>
              </div>
            `;
      }).join('');
    }

    tickerList.innerHTML = html;

    tickerList.querySelectorAll('.ticker-delete').forEach(btn => {
      btn.onclick = () => deleteTicker(btn.dataset.symbol);
    });
  }

  function deleteTicker(symbol) {
    state.tickers = state.tickers.filter(t => t.symbol !== symbol);
    state.tickerData = state.tickerData.filter(t => t.symbol !== symbol);
    saveTickers();
    renderTickers();
  }

  function switchPage(page) {
    $$('.tab').forEach(t => t.classList.toggle('active', t.dataset.page === page));
    $$('.page').forEach(p => p.classList.toggle('active', p.id === page));

    if (page === 'crypto' && !state.cryptoLoaded) {
      fetchCrypto();
    }
    if (page === 'markets' && !state.marketsLoaded) {
      fetchMarkets();
    }
  }

  function renderTickerChips() {
    const container = $('#tickerChips');
    if (!container) {
      console.error('[Bullion] tickerChips container not found');
      return;
    }

    if (state.tickers.length === 0) {
      container.innerHTML = '<div style="color:#666;font-size:12px;">No tickers. Add some below.</div>';
      return;
    }

    container.innerHTML = state.tickers.map(t =>
      `<div class="ticker-chip">
            <span>${escapeHtml(t.symbol)}</span>
            <span class="remove" data-symbol="${escapeHtml(t.symbol)}">&times;</span>
          </div>`
    ).join('');

    container.querySelectorAll('.remove').forEach(btn => {
      btn.onclick = (e) => {
        e.stopPropagation();
        removeTicker(btn.dataset.symbol);
      };
    });
  }

  function removeTicker(symbol) {
    state.tickers = state.tickers.filter(t => t.symbol !== symbol);
    saveTickers();
    renderTickerChips();
    state.tickerData = state.tickerData.filter(t => t.symbol !== symbol);
    renderTickers();
  }

  function showModal() {
    const modal = $('#modal');
    const input = $('#tickerInput');
    const error = $('#tickerError');

    if (!modal) {
      console.error('[Bullion] Modal not found');
      return;
    }

    renderTickerChips();
    modal.classList.add('visible');
    input.value = '';
    error.classList.remove('visible');
    setTimeout(() => input.focus(), 100);
  }

  function hideModal() {
    $('#modal').classList.remove('visible');
  }

  async function addTicker() {
    const input = $('#tickerInput');
    const error = $('#tickerError');
    const symbol = input.value.toUpperCase().trim();

    if (!symbol) {
      error.textContent = 'Enter a symbol';
      error.classList.add('visible');
      return;
    }

    // Security: strict validation — only allow alphanumeric + dots (e.g., BRK.A)
    if (!/^[A-Z0-9.]{1,10}$/.test(symbol)) {
      error.textContent = 'Invalid symbol (letters, numbers, dots only)';
      error.classList.add('visible');
      return;
    }

    if (state.tickers.some(t => t.symbol === symbol)) {
      error.textContent = 'Already in watchlist';
      error.classList.add('visible');
      return;
    }

    state.tickers.push({ symbol, name: symbol });
    saveTickers();
    renderTickerChips();
    input.value = '';
    error.classList.remove('visible');
    fetchMarketsDebounced();
  }

  function init() {

    // Get API key from native bridge if available (synchronous — key must be set before fetches start)
    if (hasNativeBridge() && typeof window.BullionCache.getFinnhubApiKey === 'function') {
      CONFIG.api.finnhubKey = window.BullionCache.getFinnhubApiKey();
    }

    // Startup validation: warn if API key is still placeholder
    if (CONFIG.api.finnhubKey === 'YOUR_FINNHUB_API_KEY') {
      console.warn('[Bullion] WARNING: Finnhub API key not configured. Crypto and Markets tabs will show cached data only.');
    }

    loadTickers();
    $$('.tab').forEach(tab => {
      tab.onclick = () => switchPage(tab.dataset.page);
    });

    $('#sortBtn').onclick = () => {
      state.sortAscending = !state.sortAscending;
      $('#sortBtn').classList.toggle('desc', state.sortAscending);
      renderTickers();
    };

    const manageBtn = $('#manageBtn');
    const closeModalBtn = $('#closeModalBtn');
    const confirmBtn = $('#confirmBtn');
    const resetBtn = $('#resetBtn');

    if (manageBtn) manageBtn.onclick = showModal;
    if (closeModalBtn) closeModalBtn.onclick = hideModal;
    if (confirmBtn) confirmBtn.onclick = addTicker;
    if (resetBtn) resetBtn.onclick = resetToDefaults;

    $('#tickerInput').onkeydown = (e) => {
      if (e.key === 'Enter') addTicker();
      if (e.key === 'Escape') hideModal();
    };

    $('#modal').onclick = (e) => {
      if (e.target === $('#modal')) hideModal();
    };

    let touchStartX = 0;
    let touchStartY = 0;
    document.addEventListener('touchstart', (e) => {
      touchStartX = e.touches[0].screenX;
      touchStartY = e.touches[0].screenY;
    }, { passive: true });

    document.addEventListener('touchend', (e) => {
      const diffX = e.changedTouches[0].screenX - touchStartX;
      const diffY = e.changedTouches[0].screenY - touchStartY;
      const absX = Math.abs(diffX);
      const absY = Math.abs(diffY);

      if (absX > CONFIG.ui.swipeThreshold && absX > absY * CONFIG.ui.swipeRatio) {
        const pages = ['metals', 'crypto', 'markets'];
        const current = pages.indexOf($('.page.active').id);
        const next = diffX > 0
          ? (current - 1 + pages.length) % pages.length
          : (current + 1) % pages.length;
        switchPage(pages[next]);
      }
    }, { passive: true });

    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) {
        fetchMetals();
        if (state.cryptoLoaded) fetchCrypto();
        if (state.marketsLoaded) fetchMarkets();
      }
    });

    window.onBullionDataUpdated = function () {
      fetchMetals();
      fetchCrypto();
      if (state.marketsLoaded) {
        fetchMarketsDebounced();
      }
    };

    // Initial fetches
    fetchMetals();
    setTimeout(fetchCrypto, CONFIG.delays.cryptoInit);
    setTimeout(fetchMarkets, CONFIG.delays.marketsInit);

    // Auto-refresh data - 15s polling in foreground, 60s in background (via AlarmManager)
    // The ApiRequestQueue on the bridge handles deduplication and rate limits automatically.
    setInterval(fetchMetals, CONFIG.intervals.metals);
    setInterval(fetchCrypto, CONFIG.intervals.crypto);
    setInterval(() => {
      if ($('#markets').classList.contains('active')) fetchMarketsDebounced();
    }, CONFIG.intervals.stocks);

  }

  // Expose switchPage globally for MainActivity.kt evaluateJavascript calls
  window.switchPage = switchPage;

  init();
})();
