# Changelog

All notable changes to Bullion Live are documented here.

## [1.5.1] - 2026-03-05

### Changed
- **Metals API overhaul** — replaced GoldPrice.org (blocked by Cloudflare WAF, HTTP 403 since March 2026) with a zero-cost two-source strategy:
  - Spot price: Swissquote public forex feed (XAU/USD, XAG/USD) — no auth, no rate limit
  - Daily change %: Finnhub GLD/SLV ETF `dp` field — back-calculated prevClose as `spotPrice / (1 + dp/100)`
  - Degraded to price-only mode if Finnhub unavailable (no crash)
- Updated `validate-apis.sh` to test new 8-source config (Swissquote XAU + XAG, Finnhub GLD + SLV, BTC, ETH, SPY, AAPL)
- Removed DIA/QQQ from validate-apis.sh (indices still fetched by app, just not tested separately)

### Added
- `android/upload-apk.sh` — build + Google Drive upload script using `gcloud auth print-access-token`. Handles update-vs-create, no duplicates. Requires `gcloud auth login`.

## [1.5.0] - 2026-02-24

### Added
- Sortable watchlist — sort stocks by change % or alphabetically
- Request deduplication (5-second window) in ApiRequestQueue
- Exponential retry with backoff (500ms initial, 1.5x multiplier, 5 max retries)
- Stale cache fallback (up to 15 minutes) when API requests fail
- Comprehensive test suite: 50+ Jest tests, 20+ Kotlin unit tests
- CI pipeline via GitHub Actions (JS tests, Android unit tests, lint)

### Changed
- Upgraded to Kotlin 2.0.20 and Android Gradle Plugin 8.6.1
- Reduced stock fetch burst impact with 500ms stagger between requests

## [1.4.0] - 2026-01-15

### Added
- SingleStockWidgetProvider (1x1 home screen widget for GOOG)
- Per-second rate limit cap (30/sec) alongside existing per-minute cap
- HTTP 429 backoff — 1-minute global cooldown on rate limit response

### Changed
- Widget update flow now uses CACHE_UPDATED broadcast for immediate refresh

## [1.3.0] - 2025-12-10

### Added
- MetalsWidgetProvider (2x2 home screen widget for Gold, Silver, BTC, ETH)
- PersistentCacheManager backed by SharedPreferences
- CacheBridge enabling JS-to-Kotlin data access via WebView interface

## [1.2.0] - 2025-11-05

### Added
- Markets tab with customizable 21-symbol watchlist
- Add/remove ticker modal with duplicate prevention
- FetchService background polling (60-second AlarmManager interval)

## [1.1.0] - 2025-10-01

### Added
- Crypto tab (BTC, ETH) via Finnhub API with Binance symbol prefix
- ApiRequestQueue with centralized 60/min rate limiting
- Swipe gesture navigation between tabs (110px threshold)

## [1.0.0] - 2025-09-01

### Added
- Initial release
- Metals tab with live Gold (XAU) and Silver (XAG) prices from GoldPrice.org
- Dark theme WebView UI
- In-memory JavaScript price cache with 1-minute TTL
