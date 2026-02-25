module.exports = {
  testEnvironment: 'jsdom',
  setupFilesAfterEnv: ['./setup.js'],
  testMatch: ['**/*.test.js'],
  collectCoverageFrom: [
    '../index.html'
  ],
  verbose: true
};
