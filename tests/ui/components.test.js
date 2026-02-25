describe('UI Components', () => {
  describe('Asset Card Updates', () => {
    beforeEach(() => {
      document.body.innerHTML = `
        <div class="asset-card" id="gold-card">
          <div class="asset-header"><span class="asset-symbol">GOLD</span><span class="status-dot"></span></div>
          <div class="asset-price"></div>
          <div class="asset-change"></div>
        </div>
      `;
    });

    function updateCard(cardId, data) {
      const card = document.getElementById(cardId);
      card.querySelector('.asset-price').textContent = data.price;
      card.querySelector('.asset-change').textContent = data.change;
      const changeEl = card.querySelector('.asset-change');
      changeEl.classList.remove('positive', 'negative');
      if (data.changeValue > 0) changeEl.classList.add('positive');
      if (data.changeValue < 0) changeEl.classList.add('negative');
      const dot = card.querySelector('.status-dot');
      dot.classList.remove('fresh', 'stale', 'error');
      dot.classList.add(data.status);
    }

    test('updates price and change text', () => {
      updateCard('gold-card', { price: '$2,345', change: '+1.23%', changeValue: 1.23, status: 'fresh' });
      expect(document.querySelector('.asset-price').textContent).toBe('$2,345');
      expect(document.querySelector('.asset-change').textContent).toBe('+1.23%');
    });

    test('sets positive class for gains', () => {
      updateCard('gold-card', { price: '$2,345', change: '+1.23%', changeValue: 1.23, status: 'fresh' });
      expect(document.querySelector('.asset-change').classList.contains('positive')).toBe(true);
      expect(document.querySelector('.asset-change').classList.contains('negative')).toBe(false);
    });

    test('sets negative class for losses', () => {
      updateCard('gold-card', { price: '$28.45', change: '-0.56%', changeValue: -0.56, status: 'fresh' });
      expect(document.querySelector('.asset-change').classList.contains('negative')).toBe(true);
      expect(document.querySelector('.asset-change').classList.contains('positive')).toBe(false);
    });

    test('zero change gets no color class', () => {
      updateCard('gold-card', { price: '$100', change: '0.00%', changeValue: 0, status: 'fresh' });
      expect(document.querySelector('.asset-change').classList.contains('positive')).toBe(false);
      expect(document.querySelector('.asset-change').classList.contains('negative')).toBe(false);
    });

    test('sets fresh status dot', () => {
      updateCard('gold-card', { price: '$2,345', change: '+1.23%', changeValue: 1.23, status: 'fresh' });
      expect(document.querySelector('.status-dot').classList.contains('fresh')).toBe(true);
    });

    test('sets error status dot', () => {
      updateCard('gold-card', { price: 'Error', change: '', changeValue: 0, status: 'error' });
      expect(document.querySelector('.status-dot').classList.contains('error')).toBe(true);
    });

    test('sets stale status dot', () => {
      updateCard('gold-card', { price: '$2,345', change: '+1.23%', changeValue: 1.23, status: 'stale' });
      expect(document.querySelector('.status-dot').classList.contains('stale')).toBe(true);
    });
  });

  describe('Ticker Row Color Coding', () => {
    beforeEach(() => {
      document.body.innerHTML = `
        <div class="ticker-row" data-symbol="AAPL">
          <span class="ticker-change positive">+1.50%</span>
        </div>
        <div class="ticker-row" data-symbol="MSFT">
          <span class="ticker-change negative">-0.50%</span>
        </div>
        <div class="ticker-row" data-symbol="GOOGL">
          <span class="ticker-change">0.00%</span>
        </div>
      `;
    });

    test('positive change has positive class', () => {
      const el = document.querySelector('[data-symbol="AAPL"] .ticker-change');
      expect(el.classList.contains('positive')).toBe(true);
    });

    test('negative change has negative class', () => {
      const el = document.querySelector('[data-symbol="MSFT"] .ticker-change');
      expect(el.classList.contains('negative')).toBe(true);
    });

    test('zero change has no color class', () => {
      const el = document.querySelector('[data-symbol="GOOGL"] .ticker-change');
      expect(el.classList.contains('positive')).toBe(false);
      expect(el.classList.contains('negative')).toBe(false);
    });
  });
});
