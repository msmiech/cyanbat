package at.smiech.cyanbat.ui.game

/**
 * Greedy word wrap: [text] broken at spaces into lines no wider than [width], as [measure] gives a
 * line's width. A single word wider than [width] gets a line of its own and overruns it.
 *
 * Used by the power-up cards, and shared with the test that checks every language's cards fit.
 */
internal fun wrapWords(text: String, width: Int, measure: (String) -> Int): List<String> {
    val lines = mutableListOf<String>()
    var line = ""
    for (word in text.split(' ')) {
        val longer = if (line.isEmpty()) word else "$line $word"
        if (line.isNotEmpty() && measure(longer) > width) {
            lines += line
            line = word
        } else {
            line = longer
        }
    }
    if (line.isNotEmpty()) lines += line
    return lines
}
