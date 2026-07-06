package dev.jem.offlinegit.diff

import dev.jem.offlinegit.domain.DiffError
import dev.jem.offlinegit.domain.DiffFile
import dev.jem.offlinegit.domain.DiffLineType
import dev.jem.offlinegit.domain.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UnifiedDiffParserTest {

    private val parser = UnifiedDiffParser()

    @Test
    fun `parses modified file hunk with line numbers`() {
        val patch = """
            @@ -1,3 +1,4 @@
             context
            -old
            +new
            +added
             tail
        """.trimIndent()

        val diff = assertIs<Result.Success<DiffFile>>(
            parser.parse(
                path = "README.md",
                previousPath = null,
                status = "modified",
                patch = patch,
            ),
        ).data

        assertEquals("README.md", diff.path)
        assertEquals("modified", diff.status)
        assertEquals(1, diff.hunks.single().oldStart)
        assertEquals(3, diff.hunks.single().oldLineCount)
        assertEquals(1, diff.hunks.single().newStart)
        assertEquals(4, diff.hunks.single().newLineCount)
        assertEquals(
            expected = listOf(
                DiffLineType.HUNK_HEADER,
                DiffLineType.CONTEXT,
                DiffLineType.DELETION,
                DiffLineType.ADDITION,
                DiffLineType.ADDITION,
                DiffLineType.CONTEXT,
            ),
            actual = diff.hunks.single().lines.map { it.type },
        )
        assertEquals(2, diff.hunks.single().lines[2].oldLineNumber)
        assertEquals(null, diff.hunks.single().lines[2].newLineNumber)
        assertEquals(null, diff.hunks.single().lines[3].oldLineNumber)
        assertEquals(2, diff.hunks.single().lines[3].newLineNumber)
        assertEquals("new", diff.hunks.single().lines[3].text)
    }

    @Test
    fun `parses added file hunk`() {
        val patch = """
            @@ -0,0 +1,2 @@
            +first
            +second
        """.trimIndent()

        val diff = assertIs<Result.Success<DiffFile>>(
            parser.parse(
                path = "src/App.kt",
                previousPath = null,
                status = "added",
                patch = patch,
            ),
        ).data

        assertEquals(0, diff.hunks.single().oldStart)
        assertEquals(0, diff.hunks.single().oldLineCount)
        assertEquals(listOf(null, null), diff.hunks.single().lines.drop(1).map { it.oldLineNumber })
        assertEquals(listOf(1, 2), diff.hunks.single().lines.drop(1).map { it.newLineNumber })
    }

    @Test
    fun `returns invalid patch for unexpected hunk line`() {
        val result = parser.parse(
            path = "README.md",
            previousPath = null,
            status = "modified",
            patch = """
                @@ -1 +1 @@
                invalid
            """.trimIndent(),
        )

        assertEquals(Result.Error(DiffError.InvalidPatch), result)
    }
}
