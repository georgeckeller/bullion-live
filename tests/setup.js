require('@testing-library/jest-dom');

global.fetch = jest.fn();

const localStorageMock = {
  store: {},
  getItem: jest.fn((key) => localStorageMock.store[key] || null),
  setItem: jest.fn((key, value) => { localStorageMock.store[key] = value; }),
  removeItem: jest.fn((key) => { delete localStorageMock.store[key]; }),
  clear: jest.fn(() => { localStorageMock.store = {}; })
};
Object.defineProperty(window, 'localStorage', { value: localStorageMock });

beforeEach(() => {
  jest.clearAllMocks();
  localStorageMock.store = {};
});
