package com.margelo.nitro.nitrotts

import org.junit.Assert.*
import org.junit.Test

class TtsSpeechChunksTest {
    private fun chunks(vararg text: String) = TtsSpeechChunks.build(
        text.mapIndexed { i, value -> TtsParagraph("p$i", value) }, 0,
    )

    @Test fun normalizesAllParagraphWhitespaceWithoutChangingPunctuation() {
        val result = chunks("  Hello.\r\n\r\nWorld!\t", "\u00A0\u2028\u2029", "\u200BNext?\u0085Yes.\uFEFF")
        assertEquals(listOf("Hello. World! Next? Yes."), result.map { it.text })
        assertEquals(listOf(0, 2), result.single().spans.map { it.paragraphIndex })
    }

    @Test fun packsManyShortParagraphsInsteadOfSendingOneRequestPerParagraph() {
        val result = chunks(*(1..100).map { "Line $it." }.toTypedArray())
        assertEquals(1, result.size)
        assertEquals(100, result.single().spans.size)
        assertEquals((1..100).joinToString(" ") { "Line $it." }, result.single().text)
    }

    @Test fun longParagraphsAndChaptersStayBelowHayaiTruncationLimitWithoutLosingWords() {
        val original = (1..1500).joinToString(" ") { "word$it" }
        val result = chunks(original)
        assertTrue(result.size > 1)
        assertTrue(result.all { it.text.length <= 1800 && it.text.isNotBlank() })
        assertEquals(original, result.joinToString(" ") { it.text })
        assertTrue(result.all { it.spans.all { span -> span.paragraphIndex == 0 } })
    }

    @Test fun preservesUtf16SurrogatesEvenWhenThereIsNoWordBoundary() {
        val original = "😀".repeat(2500)
        val result = chunks(original)
        assertEquals(original, result.joinToString("") { it.text })
        assertTrue(result.all { it.text.length <= 1800 && !it.text.last().isHighSurrogate() && !it.text.first().isLowSurrogate() })
    }

    @Test fun prefersSentenceEndingsAndKeepsRangeOffsetsInTheSpokenText() {
        val first = "a".repeat(1000) + "."
        val second = "b".repeat(1000) + "."
        val result = chunks(first, second)
        assertEquals(first, result[0].text)
        assertEquals(second, result[1].text)
        assertEquals(TtsSpeechSpan(1, 0, second.length), result[1].spans.single())
        val small = chunks("Alpha.", "Beta.").single()
        assertEquals("Beta.", small.text.substring(small.spans[1].start, small.spans[1].end))
    }

    @Test fun seekingStartsAtTheRequestedOriginalParagraphAndSkipsWhitespaceOnlyInput() {
        val paragraphs = listOf(TtsParagraph("a", "Before"), TtsParagraph("b", "\n\n"), TtsParagraph("c", "After."))
        val result = TtsSpeechChunks.build(paragraphs, 1)
        assertEquals("After.", result.single().text)
        assertEquals(2, result.single().spans.single().paragraphIndex)
        assertTrue(chunks("\n\n", "\u00A0").isEmpty())
    }
}
