package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }
  @Test
  fun paytrCheckoutUrl_isRestrictedToOfficialHttpsPath() {
    assertTrue(
      com.example.integration.isAllowedPaytrCheckoutUrl(
        "https://www.paytr.com/odeme/guvenli/example-token"
      )
    )
    assertFalse(
      com.example.integration.isAllowedPaytrCheckoutUrl(
        "http://www.paytr.com/odeme/guvenli/example-token"
      )
    )
    assertFalse(
      com.example.integration.isAllowedPaytrCheckoutUrl(
        "https://www.paytr.com.evil.example/odeme/guvenli/example-token"
      )
    )
    assertFalse(
      com.example.integration.isAllowedPaytrCheckoutUrl(
        "https://www.paytr.com/account"
      )
    )
  }

  @Test
  fun marketplaceViewModelKey_isolatedPerAuthenticatedUid() {
    assertNotEquals(
      marketplaceViewModelKey("uid-alice"),
      marketplaceViewModelKey("uid-batuhan")
    )
    assertEquals("marketplace-local", marketplaceViewModelKey(""))
  }

}
