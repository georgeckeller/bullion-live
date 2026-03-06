#!/bin/bash
# validate-apis.sh - Pre-build API connectivity validation
#
# TESTS 8 DATA SOURCES:
# 1. Gold spot price (XAU) - Swissquote public forex feed
# 2. Silver spot price (XAG) - Swissquote public forex feed
# 3. Gold daily change % (GLD ETF) - Finnhub
# 4. Silver daily change % (SLV ETF) - Finnhub
# 5. Bitcoin (BTC) - Finnhub BINANCE:BTCUSDT
# 6. Ethereum (ETH) - Finnhub BINANCE:ETHUSDT
# 7. S&P 500 (SPY) - Finnhub SPY
# 8. Stocks (AAPL) - Finnhub AAPL
#
# USAGE: ./validate-apis.sh
#
# EXIT CODES:
# 0 = All 8 data sources OK
# 1 = One or more sources failed
#
# CALLED BY: build-apk.sh before running tests/build
#
# VALIDATION RULES:
# - HTTP 200 response
# - Price field present and > 0
# - No API error in response

set -e

echo "============================================"
echo "  BULLION LIVE API VALIDATION"
echo "  Testing all data sources"
echo "============================================"
echo ""

FINNHUB_KEY=""

# Attempt to load from local.properties if it exists
if [ -f "local.properties" ]; then
    FINNHUB_KEY=$(grep -E "^FINNHUB_API_KEY=" local.properties | cut -d'=' -f2 | tr -d '"')
fi

# Fallback to environment variable or prompt
if [ -z "$FINNHUB_KEY" ] || [ "$FINNHUB_KEY" = "YOUR_FINNHUB_API_KEY" ]; then
    echo "ERROR: FINNHUB_API_KEY not found in local.properties or it is set to the default placeholder."
    echo "Please configure this in android/local.properties before building, or provide it as an environment variable."
    exit 1
fi

ERRORS=0
GOLD_OK=0
SILVER_OK=0
GLD_OK=0
SLV_OK=0
BTC_OK=0
ETH_OK=0
SPY_OK=0
STOCK_OK=0

is_valid_number() {
    if [ -z "$1" ]; then
        return 1
    fi
    echo "$1" | grep -qE '^[0-9]+\.?[0-9]*$' && [ "$1" != "0" ] && [ "$1" != "0.0" ]
}

echo "[1/8] Testing GOLD spot price (XAU/USD) - Swissquote..."
GOLD_RESPONSE=$(curl -s --max-time 10 "https://forex-data-feed.swissquote.com/public-quotes/bboquotes/instrument/XAU/USD" 2>/dev/null || echo "CURL_FAILED")

if [ "$GOLD_RESPONSE" = "CURL_FAILED" ]; then
    echo "      ERROR: Failed to connect to Swissquote"
    ERRORS=$((ERRORS + 1))
else
    GOLD_PRICE=$(echo "$GOLD_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d[0]['spreadProfilePrices'][0]['bid'])" 2>/dev/null)

    if [ -z "$GOLD_PRICE" ]; then
        echo "      ERROR: Gold price not found in Swissquote response"
        ERRORS=$((ERRORS + 1))
    elif is_valid_number "$GOLD_PRICE"; then
        echo "      Price: \$$GOLD_PRICE/troy oz"
        GOLD_OK=1
    else
        echo "      ERROR: Gold price invalid (got: '$GOLD_PRICE')"
        ERRORS=$((ERRORS + 1))
    fi
fi
echo ""

echo "[2/8] Testing SILVER spot price (XAG/USD) - Swissquote..."
SILVER_RESPONSE=$(curl -s --max-time 10 "https://forex-data-feed.swissquote.com/public-quotes/bboquotes/instrument/XAG/USD" 2>/dev/null || echo "CURL_FAILED")

if [ "$SILVER_RESPONSE" = "CURL_FAILED" ]; then
    echo "      ERROR: Failed to connect to Swissquote"
    ERRORS=$((ERRORS + 1))
else
    SILVER_PRICE=$(echo "$SILVER_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d[0]['spreadProfilePrices'][0]['bid'])" 2>/dev/null)

    if [ -z "$SILVER_PRICE" ]; then
        echo "      ERROR: Silver price not found in Swissquote response"
        ERRORS=$((ERRORS + 1))
    elif is_valid_number "$SILVER_PRICE"; then
        echo "      Price: \$$SILVER_PRICE/troy oz"
        SILVER_OK=1
    else
        echo "      ERROR: Silver price invalid (got: '$SILVER_PRICE')"
        ERRORS=$((ERRORS + 1))
    fi
fi
echo ""

echo "[3/8] Testing GOLD daily change % (GLD ETF) - Finnhub..."
GLD_RESPONSE=$(curl -s --max-time 10 "https://finnhub.io/api/v1/quote?symbol=GLD&token=$FINNHUB_KEY" 2>/dev/null || echo "CURL_FAILED")

if [ "$GLD_RESPONSE" = "CURL_FAILED" ]; then
    echo "      ERROR: Failed to connect to Finnhub"
    ERRORS=$((ERRORS + 1))
elif echo "$GLD_RESPONSE" | grep -q '"error"'; then
    echo "      ERROR: Finnhub API error"
    echo "      Response: $GLD_RESPONSE"
    ERRORS=$((ERRORS + 1))
else
    GLD_DP=$(echo "$GLD_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('dp',''))" 2>/dev/null)
    GLD_PC=$(echo "$GLD_RESPONSE" | grep -o '"pc":[0-9.]*' | cut -d: -f2)

    if [ -n "$GLD_PC" ] && [ -n "$GLD_DP" ]; then
        echo "      GLD prev close: \$$GLD_PC | Daily change: ${GLD_DP}%"
        GLD_OK=1
    else
        echo "      ERROR: GLD data invalid (got: '$GLD_RESPONSE')"
        ERRORS=$((ERRORS + 1))
    fi
fi
echo ""

echo "[4/8] Testing SILVER daily change % (SLV ETF) - Finnhub..."
SLV_RESPONSE=$(curl -s --max-time 10 "https://finnhub.io/api/v1/quote?symbol=SLV&token=$FINNHUB_KEY" 2>/dev/null || echo "CURL_FAILED")

if [ "$SLV_RESPONSE" = "CURL_FAILED" ]; then
    echo "      ERROR: Failed to connect to Finnhub"
    ERRORS=$((ERRORS + 1))
elif echo "$SLV_RESPONSE" | grep -q '"error"'; then
    echo "      ERROR: Finnhub API error"
    echo "      Response: $SLV_RESPONSE"
    ERRORS=$((ERRORS + 1))
else
    SLV_DP=$(echo "$SLV_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('dp',''))" 2>/dev/null)
    SLV_PC=$(echo "$SLV_RESPONSE" | grep -o '"pc":[0-9.]*' | cut -d: -f2)

    if [ -n "$SLV_PC" ] && [ -n "$SLV_DP" ]; then
        echo "      SLV prev close: \$$SLV_PC | Daily change: ${SLV_DP}%"
        SLV_OK=1
    else
        echo "      ERROR: SLV data invalid (got: '$SLV_RESPONSE')"
        ERRORS=$((ERRORS + 1))
    fi
fi
echo ""

echo "[5/8] Testing BITCOIN (BTC) - Crypto Tab..."
BTC_RESPONSE=$(curl -s --max-time 10 "https://finnhub.io/api/v1/quote?symbol=BINANCE:BTCUSDT&token=$FINNHUB_KEY" 2>/dev/null || echo "CURL_FAILED")

if [ "$BTC_RESPONSE" = "CURL_FAILED" ]; then
    echo "      ERROR: Failed to connect to Finnhub"
    ERRORS=$((ERRORS + 1))
elif echo "$BTC_RESPONSE" | grep -q '"error"'; then
    echo "      ERROR: Finnhub API error"
    echo "      Response: $BTC_RESPONSE"
    ERRORS=$((ERRORS + 1))
else
    BTC_PRICE=$(echo "$BTC_RESPONSE" | grep -o '"c":[0-9.]*' | cut -d: -f2)

    if is_valid_number "$BTC_PRICE"; then
        echo "      Price: \$$BTC_PRICE"
        BTC_OK=1
    else
        echo "      ERROR: BTC price invalid (got: '$BTC_PRICE')"
        echo "      Response: $BTC_RESPONSE"
        ERRORS=$((ERRORS + 1))
    fi
fi
echo ""

echo "[6/8] Testing ETHEREUM (ETH) - Crypto Tab..."
ETH_RESPONSE=$(curl -s --max-time 10 "https://finnhub.io/api/v1/quote?symbol=BINANCE:ETHUSDT&token=$FINNHUB_KEY" 2>/dev/null || echo "CURL_FAILED")

if [ "$ETH_RESPONSE" = "CURL_FAILED" ]; then
    echo "      ERROR: Failed to connect to Finnhub"
    ERRORS=$((ERRORS + 1))
elif echo "$ETH_RESPONSE" | grep -q '"error"'; then
    echo "      ERROR: Finnhub API error"
    echo "      Response: $ETH_RESPONSE"
    ERRORS=$((ERRORS + 1))
else
    ETH_PRICE=$(echo "$ETH_RESPONSE" | grep -o '"c":[0-9.]*' | cut -d: -f2)

    if is_valid_number "$ETH_PRICE"; then
        echo "      Price: \$$ETH_PRICE"
        ETH_OK=1
    else
        echo "      ERROR: ETH price invalid (got: '$ETH_PRICE')"
        echo "      Response: $ETH_RESPONSE"
        ERRORS=$((ERRORS + 1))
    fi
fi
echo ""

echo "[7/8] Testing S&P 500 (SPY) - Major Indices..."
SPY_RESPONSE=$(curl -s --max-time 10 "https://finnhub.io/api/v1/quote?symbol=SPY&token=$FINNHUB_KEY" 2>/dev/null || echo "CURL_FAILED")

if [ "$SPY_RESPONSE" = "CURL_FAILED" ]; then
    echo "      ERROR: Failed to connect to Finnhub"
    ERRORS=$((ERRORS + 1))
elif echo "$SPY_RESPONSE" | grep -q '"error"'; then
    echo "      ERROR: Finnhub API error"
    ERRORS=$((ERRORS + 1))
else
    SPY_PRICE=$(echo "$SPY_RESPONSE" | grep -o '"c":[0-9.]*' | cut -d: -f2)

    if is_valid_number "$SPY_PRICE"; then
        echo "      Price: \$$SPY_PRICE"
        SPY_OK=1
    else
        echo "      ERROR: SPY price invalid (got: '$SPY_PRICE')"
        ERRORS=$((ERRORS + 1))
    fi
fi
echo ""

echo "[8/8] Testing AAPL Stock - Watchlist..."
STOCK_RESPONSE=$(curl -s --max-time 10 "https://finnhub.io/api/v1/quote?symbol=AAPL&token=$FINNHUB_KEY" 2>/dev/null || echo "CURL_FAILED")

if [ "$STOCK_RESPONSE" = "CURL_FAILED" ]; then
    echo "      ERROR: Failed to connect to Finnhub"
    ERRORS=$((ERRORS + 1))
elif echo "$STOCK_RESPONSE" | grep -q '"error"'; then
    echo "      ERROR: Finnhub API error"
    echo "      Response: $STOCK_RESPONSE"
    ERRORS=$((ERRORS + 1))
else
    STOCK_PRICE=$(echo "$STOCK_RESPONSE" | grep -o '"c":[0-9.]*' | cut -d: -f2)

    if is_valid_number "$STOCK_PRICE"; then
        echo "      Price: \$$STOCK_PRICE"
        STOCK_OK=1
    else
        echo "      ERROR: Stock price invalid (got: '$STOCK_PRICE')"
        echo "      Response: $STOCK_RESPONSE"
        ERRORS=$((ERRORS + 1))
    fi
fi
echo ""

echo "============================================"
echo "  APP DATA SOURCE STATUS"
echo "============================================"
echo ""

echo "  Metals Tab:"
if [ $GOLD_OK -eq 1 ]; then
    echo "    [OK] Gold spot price (Swissquote XAU/USD)"
else
    echo "    [FAIL] Gold spot price (Swissquote XAU/USD)"
fi

if [ $SILVER_OK -eq 1 ]; then
    echo "    [OK] Silver spot price (Swissquote XAG/USD)"
else
    echo "    [FAIL] Silver spot price (Swissquote XAG/USD)"
fi

if [ $GLD_OK -eq 1 ]; then
    echo "    [OK] Gold daily change % (Finnhub GLD)"
else
    echo "    [FAIL] Gold daily change % (Finnhub GLD)"
fi

if [ $SLV_OK -eq 1 ]; then
    echo "    [OK] Silver daily change % (Finnhub SLV)"
else
    echo "    [FAIL] Silver daily change % (Finnhub SLV)"
fi

echo ""
echo "  Crypto Tab:"
if [ $BTC_OK -eq 1 ]; then
    echo "    [OK] Bitcoin (BTC)"
else
    echo "    [FAIL] Bitcoin (BTC)"
fi

if [ $ETH_OK -eq 1 ]; then
    echo "    [OK] Ethereum (ETH)"
else
    echo "    [FAIL] Ethereum (ETH)"
fi

echo ""
echo "  Major Indices:"
if [ $SPY_OK -eq 1 ]; then
    echo "    [OK] S&P 500 (SPY)"
else
    echo "    [FAIL] S&P 500 (SPY)"
fi

echo ""
echo "  Watchlist:"
if [ $STOCK_OK -eq 1 ]; then
    echo "    [OK] Stocks (AAPL)"
else
    echo "    [FAIL] Stocks (AAPL)"
fi

echo ""
echo "============================================"

PASSED=$((GOLD_OK + SILVER_OK + GLD_OK + SLV_OK + BTC_OK + ETH_OK + SPY_OK + STOCK_OK))

if [ $PASSED -eq 8 ]; then
    echo "  VALIDATION PASSED: 8/8 data sources OK"
    echo "============================================"
    exit 0
else
    echo "  VALIDATION FAILED: $PASSED/8 data sources OK"
    echo "  $ERRORS error(s) detected"
    echo "============================================"
    exit 1
fi
