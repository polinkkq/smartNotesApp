package com.example.smartnotes.utils

object TextPaginator {

    /**
     * Делит текст на страницы примерно одинакового объёма.
     * - Не режет слова посередине (по возможности)
     * - Сохраняет переносы строк
     */
    fun splitIntoPages(fullText: String, maxCharsPerPage: Int): List<String> {
        val text = fullText.trim()
        if (text.isBlank()) return emptyList()

        val pages = mutableListOf<String>()
        var i = 0

        while (i < text.length) {
            val endLimit = (i + maxCharsPerPage).coerceAtMost(text.length)

            // если дошли до конца — остаток в страницу
            if (endLimit == text.length) {
                pages.add(text.substring(i).trim())
                break
            }

            // пытаемся аккуратно найти место разреза: сначала по пустой строке, потом по концу строки, потом по пробелу
            val slice = text.substring(i, endLimit)

            val cutIndexInSlice =
                slice.lastIndexOf("\n\n").takeIf { it >= maxCharsPerPage * 0.6 } // предпочтительно абзац
                    ?: slice.lastIndexOf('\n').takeIf { it >= maxCharsPerPage * 0.6 }        // потом строка
                    ?: slice.lastIndexOf(' ').takeIf { it >= maxCharsPerPage * 0.6 }         // потом пробел
                    ?: slice.length                                                         // иначе режем как есть

            val cut = i + cutIndexInSlice

            val pageText = text.substring(i, cut).trim()
            if (pageText.isNotBlank()) pages.add(pageText)

            // двигаем указатель (пропуская пробелы/переносы)
            i = cut
            while (i < text.length && (text[i] == ' ' || text[i] == '\n' || text[i] == '\t')) {
                i++
            }
        }

        return pages
    }
}
