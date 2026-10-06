package at.smiech.cyanbat.ui.game

/**
 * Greedy word wrap: [text] broken on its spaces into lines no wider than [width], as [measure]
 * gives a line's width. A word wider than [width] on its own still gets a line, and runs over it.
 *
 * All the power-up cards need, and shared with the test that holds every language's cards to them.
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
