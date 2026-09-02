package nl.markmaaktmedia.markmaaktai.ai.prompt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrgencyRulesTest {

    @Test
    fun `the model cannot invent urgency out of nothing`() {
        assertFalse(
            UrgencyRules.isUrgent(
                modelSaidUrgent = true,
                packageName = "com.whatsapp",
                category = "message",
                text = "Sanne: Leuke foto's van gisteren, bedankt!",
            )
        )
    }

    @Test
    fun `the model cannot make a sale urgent`() {
        assertFalse(
            UrgencyRules.isUrgent(
                modelSaidUrgent = true,
                packageName = "com.bol.shop",
                category = "other",
                text = "Laatste dag! 20% korting, de actie loopt tot vanavond 23:59.",
            )
        )
    }

    @Test
    fun `social media is never urgent`() {
        assertFalse(
            UrgencyRules.isUrgent(
                modelSaidUrgent = true,
                packageName = "com.instagram.android",
                category = "social",
                text = "Je moet dringend reageren op deze story",
            )
        )
    }

    @Test
    fun `a failed payment is urgent`() {
        assertTrue(
            UrgencyRules.isUrgent(
                modelSaidUrgent = true,
                packageName = "com.rabobank.android",
                category = "finance",
                text = "De automatische incasso is mislukt.",
            )
        )
    }

    @Test
    fun `a time today is urgent`() {
        assertTrue(
            UrgencyRules.isUrgent(
                modelSaidUrgent = true,
                packageName = "com.google.android.calendar",
                category = "calendar",
                text = "Je wordt vandaag om 15:30 verwacht bij de tandarts.",
            )
        )
    }

    @Test
    fun `a no from the model is always a no`() {
        assertFalse(
            UrgencyRules.isUrgent(
                modelSaidUrgent = false,
                packageName = "com.rabobank.android",
                category = "finance",
                text = "De automatische incasso is mislukt.",
            )
        )
    }
}
