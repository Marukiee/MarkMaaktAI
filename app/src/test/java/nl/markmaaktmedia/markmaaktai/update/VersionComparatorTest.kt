package nl.markmaaktmedia.markmaaktai.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun `strips a leading v from a tag`() {
        assertEquals("1.2.3", VersionComparator.normalise("v1.2.3"))
        assertEquals("1.2.3", VersionComparator.normalise(" V1.2.3 "))
    }

    @Test
    fun `compares numerically rather than as text`() {
        // The bug this guards against: "1.10.0" sorting before "1.9.0".
        assertTrue(VersionComparator.isNewer("1.10.0", "1.9.0"))
        assertFalse(VersionComparator.isNewer("1.9.0", "1.10.0"))
    }

    @Test
    fun `handles a missing patch component`() {
        assertTrue(VersionComparator.isNewer("1.1", "1.0.9"))
        assertFalse(VersionComparator.isNewer("1.0", "1.0.0"))
    }

    @Test
    fun `treats a suffixed build as older than the plain release`() {
        assertTrue(VersionComparator.isNewer("1.2.0", "1.2.0-rc1"))
        assertFalse(VersionComparator.isNewer("1.2.0-rc1", "1.2.0"))
    }

    @Test
    fun `an identical version is not newer`() {
        assertFalse(VersionComparator.isNewer("2.0.0", "v2.0.0"))
    }

    @Test
    fun `survives a tag that is not a version at all`() {
        assertFalse(VersionComparator.isNewer("nightly", "1.0.0"))
    }
}
