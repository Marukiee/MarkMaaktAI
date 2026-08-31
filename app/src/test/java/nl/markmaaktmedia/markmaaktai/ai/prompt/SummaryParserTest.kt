package nl.markmaaktmedia.markmaaktai.ai.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryParserTest {

    @Test
    fun `reads a clean object`() {
        val parsed = SummaryParser.parse(
            """{"summary":"Tom moved the meeting","is_urgent":true,"action_items":["Reply to Tom"],"category":"message"}"""
        )
        assertEquals("Tom moved the meeting", parsed.summary)
        assertTrue(parsed.isUrgent)
        assertEquals(listOf("Reply to Tom"), parsed.actionItems)
        assertEquals("message", parsed.category)
    }

    @Test
    fun `digs the object out of surrounding chatter`() {
        val parsed = SummaryParser.parse(
            """
            Sure, here is the JSON you asked for:
            ```json
            {"summary":"Parcel is out for delivery","is_urgent":false,"action_items":[],"category":"delivery"}
            ```
            Let me know if you need anything else.
            """.trimIndent()
        )
        assertEquals("Parcel is out for delivery", parsed.summary)
        assertEquals("delivery", parsed.category)
    }

    @Test
    fun `is not fooled by a brace inside a string`() {
        val parsed = SummaryParser.parse(
            """{"summary":"He wrote { and then left","is_urgent":false,"action_items":[],"category":"other"}"""
        )
        assertEquals("He wrote { and then left", parsed.summary)
    }

    @Test
    fun `falls back to the raw text when there is no JSON`() {
        val parsed = SummaryParser.parse("Three messages about the planning for tomorrow.")
        assertEquals("Three messages about the planning for tomorrow.", parsed.summary)
        assertFalse(parsed.isUrgent)
        assertEquals("other", parsed.category)
    }

    @Test
    fun `rejects a category the app does not know`() {
        val parsed = SummaryParser.parse(
            """{"summary":"Something","is_urgent":false,"action_items":[],"category":"banana"}"""
        )
        assertEquals("other", parsed.category)
    }

    @Test
    fun `caps the action items at three`() {
        val parsed = SummaryParser.parse(
            """{"summary":"Lots to do","is_urgent":false,"action_items":["a","b","c","d","e"],"category":"other"}"""
        )
        assertEquals(3, parsed.actionItems.size)
    }

    @Test
    fun `drops blank action items`() {
        val parsed = SummaryParser.parse(
            """{"summary":"One thing","is_urgent":false,"action_items":["Call back","  "],"category":"other"}"""
        )
        assertEquals(listOf("Call back"), parsed.actionItems)
    }
}
