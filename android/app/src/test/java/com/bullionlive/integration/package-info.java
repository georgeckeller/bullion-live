/**
 * Integration Tests Package
 *
 * Contains tests that make REAL NETWORK CALLS to verify:
 * - API connectivity and availability
 * - Response data validity
 * - Caching behavior
 * - Widget data requirements
 *
 * TEST FILES:
 * - ApiIntegrationTest.kt: Comprehensive API connectivity and data validation
 * - CacheIntegrationTest.kt: Verifies caching works correctly
 * - ResponseValidationTest.kt: Validates response format and data ranges
 *
 * RUN ALL: ./gradlew testDebugUnitTest --tests "com.bullionlive.integration.*"
 *
 * FAILS BUILD IF:
 * - Any API is unreachable
 * - Any API returns invalid/zero data
 * - Prices are outside expected ranges
 * - Widget would display "Error"
 */
package com.bullionlive.integration;
