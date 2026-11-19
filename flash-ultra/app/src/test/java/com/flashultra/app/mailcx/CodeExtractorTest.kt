package com.flashultra.app.mailcx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The only unit tests in the project: the extractCode port against the sample set the original
 * background.js CODE_PATTERNS/plausible/unesc/toText pipeline would produce.
 */
class CodeExtractorTest {

    @Test
    fun labelPatterns() {
        assertEquals("482913", CodeExtractor.extractCode("", "Your code is 482913", ""))
        assertEquals("4921", CodeExtractor.extractCode("Login", "Your login code: 4921", ""))
        assertEquals("724815", CodeExtractor.extractCode("Meta", "OTP is 72 4815", ""))
        assertEquals("118842", CodeExtractor.extractCode("", "Your confirmation code was 118842.", ""))
        assertEquals("2359", CodeExtractor.extractCode("", "PIN - 2359", ""))
    }

    @Test
    fun numberFirstPattern() {
        assertEquals("883120", CodeExtractor.extractCode("", "883120 is your Facebook code", ""))
        assertEquals("557314", CodeExtractor.extractCode("", "557314 as the verification code", ""))
    }

    @Test
    fun useEnterTypePattern() {
        assertEquals("709214", CodeExtractor.extractCode("", "Use 709214 to continue", ""))
        assertEquals("6612", CodeExtractor.extractCode("", "Enter 6612 as your passcode", ""))
        assertEquals("304497", CodeExtractor.extractCode("", "Type 304497 for verification", ""))
    }

    @Test
    fun gCodePattern() {
        assertEquals("123456", CodeExtractor.extractCode("", "Your backup code G-123456 is ready", ""))
    }

    @Test
    fun htmlBodiesAreConverted() {
        val html = "<html><style>.x{}</style><body><p>Your code is <b>841220</b></p><script>var x=1;</script></body></html>"
        assertEquals("841220", CodeExtractor.extractCode("Security code", "", html))
        assertEquals("555666", CodeExtractor.extractCode("", "&lt;p&gt;Your OTP: 555666&lt;/p&gt;", ""))
    }

    @Test
    fun ignoresUrlsAndDates() {
        assertNull(CodeExtractor.extractCode("", "Open https://example.com/?code=123456 to continue", ""))
        assertNull(CodeExtractor.extractCode("Invoice", "Order total 2024 year 2024", ""))
        assertNull(CodeExtractor.extractCode("", "Price 1995 is not a code", ""))
    }

    @Test
    fun ignoresNonOtpHtml() {
        assertNull(CodeExtractor.extractCode("Monthly newsletter", "Copyright 2024 cc notice", "<div>No code here</div>"))
    }

    @Test
    fun digitLengthBounds() {
        assertEquals("8471", CodeExtractor.extractCode("", "Your code is 8471", ""))
        assertNull(CodeExtractor.extractCode("", "Your code is 847", ""))
        assertNull(CodeExtractor.extractCode("", "Your code is 123456789", ""))
    }

    @Test
    fun firstMatchWins() {
        val body = "Your code is 481213. Ignore 99 1234 which is an old code."
        assertEquals("481213", CodeExtractor.extractCode("", body, ""))
    }

    @Test
    fun detailModelUsesAllThreeFields() {
        val detail = InboxMailDetail(
            id = "1",
            sender = "Meta",
            subject = "Your security code",
            textBody = "",
            htmlBody = "<p>Use 718294 to continue</p>",
            timestamp = "now"
        )
        assertEquals("718294", CodeExtractor.extractCode(detail))
    }
}
