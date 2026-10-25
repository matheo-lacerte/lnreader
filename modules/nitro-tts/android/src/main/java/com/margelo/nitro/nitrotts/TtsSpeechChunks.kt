package com.margelo.nitro.nitrotts

internal data class TtsSpeechSpan(val paragraphIndex: Int, val start: Int, val end: Int)
internal data class TtsSpeechChunk(val text: String, val spans: List<TtsSpeechSpan>)

/** HayaiTTS currently truncates framework requests at 2000 UTF-16 characters. */
internal object TtsSpeechChunks {
    const val MAX_LENGTH = 1800
    private val whitespace = Regex("[\\s\\p{Z}\\u0085\\u200B\\uFEFF]+")

    fun build(paragraphs: List<TtsParagraph>, initialIndex: Int): List<TtsSpeechChunk> {
        val text = StringBuilder()
        val spans = mutableListOf<TtsSpeechSpan>()
        for (index in initialIndex until paragraphs.size) {
            val normalized = paragraphs[index].text.replace(whitespace, " ").trim()
            if (normalized.isEmpty()) continue
            if (text.isNotEmpty()) text.append(' ')
            val start = text.length
            text.append(normalized)
            spans.add(TtsSpeechSpan(index, start, text.length))
        }
        val content = text.toString()
        val chunks = mutableListOf<TtsSpeechChunk>()
        var cursor = 0
        var spanCursor = 0
        while (cursor < content.length) {
            var end = minOf(cursor + MAX_LENGTH, content.length)
            if (end < content.length) {
                // Prefer sentence endings, then word boundaries; preserve every character.
                val sentence = (end - 1 downTo cursor + MAX_LENGTH / 2).firstOrNull {
                    content[it] in ".!?。！？" && (it + 1 == content.length || content[it + 1] == ' ')
                }
                val word = content.lastIndexOf(' ', end)
                end = sentence?.plus(1) ?: if (word > cursor) word else end
                if (end < content.length && content[end - 1].isHighSurrogate() && content[end].isLowSurrogate()) end--
            }
            while (end > cursor && content[end - 1] == ' ') end--
            while (spanCursor < spans.size && spans[spanCursor].end <= cursor) spanCursor++
            val localSpans = mutableListOf<TtsSpeechSpan>()
            var index = spanCursor
            while (index < spans.size && spans[index].start < end) {
                val span = spans[index++]
                localSpans.add(TtsSpeechSpan(span.paragraphIndex, maxOf(span.start, cursor) - cursor, minOf(span.end, end) - cursor))
            }
            chunks.add(TtsSpeechChunk(content.substring(cursor, end), localSpans))
            cursor = end
            while (cursor < content.length && content[cursor] == ' ') cursor++
        }
        return chunks
    }
}
