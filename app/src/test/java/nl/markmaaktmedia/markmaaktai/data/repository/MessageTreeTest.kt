package nl.markmaaktmedia.markmaaktai.data.repository

import nl.markmaaktmedia.markmaaktai.data.db.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTreeTest {

    private fun message(id: Long, parentId: Long?) = MessageEntity(
        id = id,
        conversationId = 1,
        parentId = parentId,
        role = "user",
        content = "m$id",
        createdAt = id,
    )

    /** 1 -> 2 -> 3, with 4 as a second version of turn 2 and 5 following it. */
    private val branched = listOf(
        message(1, null),
        message(2, 1),
        message(3, 2),
        message(4, 1),
        message(5, 4),
    )

    @Test
    fun `walks a straight thread`() {
        val path = MessageTree.pathTo(branched, leafId = 3)
        assertEquals(listOf(1L, 2L, 3L), path.map { it.id })
    }

    @Test
    fun `follows the other branch when its leaf is active`() {
        val path = MessageTree.pathTo(branched, leafId = 5)
        assertEquals(listOf(1L, 4L, 5L), path.map { it.id })
    }

    @Test
    fun `falls back to the newest message when no leaf is recorded`() {
        // What an unbranched conversation looks like before anything is edited.
        val path = MessageTree.pathTo(branched, leafId = null)
        assertEquals(listOf(1L, 4L, 5L), path.map { it.id })
    }

    @Test
    fun `an unknown leaf does not empty the thread`() {
        val path = MessageTree.pathTo(branched, leafId = 99)
        assertTrue(path.isNotEmpty())
    }

    @Test
    fun `empty in, empty out`() {
        assertEquals(emptyList<MessageEntity>(), MessageTree.pathTo(emptyList(), leafId = 1))
    }

    @Test
    fun `switching to a version restores what followed it`() {
        assertEquals(3L, MessageTree.deepestUnder(branched, startId = 2))
        assertEquals(5L, MessageTree.deepestUnder(branched, startId = 4))
    }

    @Test
    fun `a leaf is its own deepest descendant`() {
        assertEquals(3L, MessageTree.deepestUnder(branched, startId = 3))
    }

    @Test
    fun `a cycle terminates instead of hanging`() {
        val cyclic = listOf(message(1, 2), message(2, 1))
        // The assertion is really that these return at all. A guard that walked
        // forever would hang the test runner rather than fail it.
        assertTrue(MessageTree.pathTo(cyclic, leafId = 1).isNotEmpty())
        assertTrue(MessageTree.deepestUnder(cyclic, startId = 1) in listOf(1L, 2L))
    }
}
