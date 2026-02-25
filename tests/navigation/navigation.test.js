describe('Navigation', () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <div class="tabs">
        <button class="tab active" data-page="metals">METALS</button>
        <button class="tab" data-page="crypto">CRYPTO</button>
        <button class="tab" data-page="markets">MARKETS</button>
      </div>
      <div class="page active" id="metals-page">Metals Content</div>
      <div class="page" id="crypto-page">Crypto Content</div>
      <div class="page" id="markets-page">Markets Content</div>
    `;
  });

  function switchPage(pageName) {
    document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    const targetPage = document.getElementById(`${pageName}-page`);
    const targetTab = document.querySelector(`[data-page="${pageName}"]`);
    if (targetPage) targetPage.classList.add('active');
    if (targetTab) targetTab.classList.add('active');
  }

  function getCurrentPage() {
    const activePage = document.querySelector('.page.active');
    return activePage ? activePage.id.replace('-page', '') : null;
  }

  describe('Tab Switching', () => {
    test('metals is active by default', () => {
      expect(getCurrentPage()).toBe('metals');
    });

    test('switches to each page correctly', () => {
      switchPage('crypto');
      expect(getCurrentPage()).toBe('crypto');
      switchPage('markets');
      expect(getCurrentPage()).toBe('markets');
      switchPage('metals');
      expect(getCurrentPage()).toBe('metals');
    });

    test('deactivates previous page and tab', () => {
      switchPage('crypto');
      expect(document.getElementById('metals-page').classList.contains('active')).toBe(false);
      expect(document.querySelector('[data-page="metals"]').classList.contains('active')).toBe(false);
      expect(document.querySelector('[data-page="crypto"]').classList.contains('active')).toBe(true);
    });

    test('only one page and tab active at a time', () => {
      switchPage('markets');
      expect(document.querySelectorAll('.page.active').length).toBe(1);
      expect(document.querySelectorAll('.tab.active').length).toBe(1);
    });
  });

  describe('Swipe Navigation', () => {
    const SWIPE_THRESHOLD = 110;
    const pageOrder = ['metals', 'crypto', 'markets'];

    function getNextPage(currentPage, direction) {
      const i = pageOrder.indexOf(currentPage);
      if (direction === 'left' && i < pageOrder.length - 1) return pageOrder[i + 1];
      if (direction === 'right' && i > 0) return pageOrder[i - 1];
      return currentPage;
    }

    function calculateSwipeDirection(startX, endX, startY, endY) {
      const diffX = startX - endX;
      const absX = Math.abs(diffX);
      const absY = Math.abs(startY - endY);
      if (absX < SWIPE_THRESHOLD || absX <= absY * 1.5) return null;
      return diffX > 0 ? 'left' : 'right';
    }

    test('swipe left advances, swipe right goes back', () => {
      expect(getNextPage('metals', calculateSwipeDirection(200, 50, 100, 100))).toBe('crypto');
      expect(getNextPage('crypto', calculateSwipeDirection(50, 200, 100, 100))).toBe('metals');
    });

    test('swipe below threshold does nothing', () => {
      expect(calculateSwipeDirection(100, 50, 100, 100)).toBe(null);
    });

    test('vertical movement does not trigger swipe', () => {
      expect(calculateSwipeDirection(100, 100, 100, 250)).toBe(null);
    });

    test('mostly vertical with horizontal component does not trigger', () => {
      expect(calculateSwipeDirection(200, 100, 100, 300)).toBe(null);
    });

    test('cannot swipe past first or last page', () => {
      expect(getNextPage('metals', 'right')).toBe('metals');
      expect(getNextPage('markets', 'left')).toBe('markets');
    });
  });
});
