package at.smiech.cyanbat.ui.game

import at.smiech.engine.Graphics
import at.smiech.engine.InputMethod
import kotlin.math.roundToInt

/**
 * What a prompt asks the player to do, named for its meaning rather than for a button, since each
 * [InputMethod] does it with its own: the keyboard with a key or a click, a pad with a face
 * button or its d-pad.
 */
internal enum class PromptAction {
    /** Back to the run from the pause screen: Escape or a click, the pad's bottom face button. */
    RESUME,

    /** Take what the screen offers: Enter or a click, the pad's bottom face button. */
    CONFIRM,

    /** Out, to the menu: Q, the pad's right face button. */
    BACK,

    /** Move between the level up's cards: the arrow keys, the d-pad. */
    CHOOSE,

    /** Take the card the cursor is on: Enter, the pad's bottom face button. */
    TAKE,
}

/** One prompt: the [action]'s buttons, drawn, and [label], what it does, beside them. */
internal class Prompt(val action: PromptAction, val label: String)

/**
 * The run's button prompts, drawn into the frame: each button as the player sees it on what they
 * hold, a key cap, an Xbox pad's lettered face button or a PlayStation pad's shape, followed by the
 * words for what it does.
 *
 * The buttons are pixel art drawn with the frame's own primitives rather than characters of a
 * font, since not every platform's font has the PlayStation's shapes, and so they stay on the
 * frame's grid as everything but text does. They are sized off the text beside them.
 */
internal class ButtonPrompts(private val g: Graphics) {

    /** The pictures for [action] with [method], left to right. */
    private fun glyphs(method: InputMethod, action: PromptAction): List<Glyph> = when (method) {
        InputMethod.TOUCH, InputMethod.KEYBOARD_MOUSE -> when (action) {
            PromptAction.RESUME -> listOf(Glyph.Click, Glyph.Key("Esc"))
            PromptAction.CONFIRM -> listOf(Glyph.Click, Glyph.Key("Enter"))
            PromptAction.BACK -> listOf(Glyph.Key("Q"))
            PromptAction.CHOOSE -> listOf(Glyph.Arrow(left = true), Glyph.Arrow(left = false))
            PromptAction.TAKE -> listOf(Glyph.Key("Enter"))
        }

        InputMethod.XBOX_PAD, InputMethod.PLAYSTATION_PAD -> when (action) {
            PromptAction.RESUME, PromptAction.CONFIRM, PromptAction.TAKE ->
                listOf(Glyph.Face(method, south = true))

            PromptAction.BACK -> listOf(Glyph.Face(method, south = false))
            PromptAction.CHOOSE -> listOf(Glyph.DPad)
        }
    }

    /** How wide [prompts] are as one row in text of [size]. */
    fun measure(method: InputMethod, prompts: List<Prompt>, size: Int): Int =
        prompts.sumOf { prompt ->
            val buttons = glyphs(method, prompt.action)
            buttons.sumOf { it.width(this, size) } + (buttons.size - 1) * GLYPH_GAP +
                    LABEL_GAP + g.measureString(prompt.label, size)
        } + (prompts.size - 1) * PROMPT_GAP

    /**
     * Draws [prompts] in a row, centered on [centerX], with the text's baseline at [baseline], in
     * text of [size] and [color].
     */
    fun drawCentered(
        method: InputMethod,
        prompts: List<Prompt>,
        centerX: Int,
        baseline: Int,
        size: Int,
        color: Int,
    ) {
        var x = centerX - measure(method, prompts, size) / 2
        prompts.forEachIndexed { index, prompt ->
            if (index > 0) x += PROMPT_GAP
            glyphs(method, prompt.action).forEachIndexed { i, glyph ->
                if (i > 0) x += GLYPH_GAP
                glyph.draw(this, x, baseline, size)
                x += glyph.width(this, size)
            }
            x += LABEL_GAP
            g.drawString(prompt.label, x, baseline, size, color)
            x += g.measureString(prompt.label, size)
        }
    }

    /** How tall a button is beside text of [size]. */
    private fun height(size: Int) = size + 3

    /** The top of a button beside text of [size] on [baseline]: centered on the text's capitals. */
    private fun top(baseline: Int, size: Int) =
        baseline - (size * CAP_CENTER).roundToInt() - height(size) / 2

    /** One button's picture. */
    private sealed interface Glyph {
        fun width(prompts: ButtonPrompts, size: Int): Int
        fun draw(prompts: ButtonPrompts, x: Int, baseline: Int, size: Int)

        /** A key cap with [label] on it. */
        class Key(val label: String) : Glyph {
            override fun width(prompts: ButtonPrompts, size: Int): Int = maxOf(
                prompts.height(size),
                prompts.g.measureString(label, keySize(size)) + 2 * KEY_PADDING,
            )

            override fun draw(prompts: ButtonPrompts, x: Int, baseline: Int, size: Int) {
                val g = prompts.g
                val w = width(prompts, size)
                prompts.keyCap(x, prompts.top(baseline, size), w, prompts.height(size))
                val textSize = keySize(size)
                g.drawString(
                    label,
                    x + (w - g.measureString(label, textSize)) / 2,
                    prompts.top(baseline, size) + prompts.height(size) / 2 +
                            (textSize * CAP_CENTER).roundToInt() - 1,
                    textSize,
                    KEY_TEXT,
                )
            }

            private fun keySize(size: Int) = size - 4
        }

        /** A key cap with an arrow on it, pointing left or right. */
        class Arrow(val left: Boolean) : Glyph {
            override fun width(prompts: ButtonPrompts, size: Int) = prompts.height(size)

            override fun draw(prompts: ButtonPrompts, x: Int, baseline: Int, size: Int) {
                val h = prompts.height(size)
                val top = prompts.top(baseline, size)
                prompts.keyCap(x, top, h, h)
                // A solid triangle, a column at a time, its point toward the way it goes.
                val reach = h / 4
                val centerY = top + (h - 2) / 2
                val tipX = x + (h - reach) / 2 + if (left) 0 else reach
                for (i in 0..reach) {
                    val column = if (left) tipX + i else tipX - i
                    prompts.g.drawRect(column, centerY - i, 1, 2 * i + 1, KEY_TEXT)
                }
            }
        }

        /** A mouse, its left button lit: a click. */
        data object Click : Glyph {
            override fun width(prompts: ButtonPrompts, size: Int) =
                (prompts.height(size) * 0.62f).roundToInt() or 1

            override fun draw(prompts: ButtonPrompts, x: Int, baseline: Int, size: Int) {
                val g = prompts.g
                val w = width(prompts, size)
                val h = prompts.height(size)
                val top = prompts.top(baseline, size)
                val buttons = h * 2 / 5
                // A body rounded two pixels at each corner, its buttons along the top split down the
                // middle, the left one lit.
                g.drawRect(x + 2, top + 1, w - 4, h - 2, KEY_FILL)
                g.drawRect(x + 1, top + 2, w - 2, h - 4, KEY_FILL)
                g.drawRect(x + 2, top + 1, w / 2 - 2, buttons - 1, KEY_TEXT)
                g.drawRect(x + 1, top + 2, 1, buttons - 2, KEY_TEXT)
                g.drawRect(x + 2, top, w - 4, 1, KEY_RIM)
                g.drawRect(x + 2, top + h - 1, w - 4, 1, KEY_RIM)
                g.drawRect(x, top + 2, 1, h - 4, KEY_RIM)
                g.drawRect(x + w - 1, top + 2, 1, h - 4, KEY_RIM)
                g.drawRect(x + 1, top + 1, 1, 1, KEY_RIM)
                g.drawRect(x + w - 2, top + 1, 1, 1, KEY_RIM)
                g.drawRect(x + 1, top + h - 2, 1, 1, KEY_RIM)
                g.drawRect(x + w - 2, top + h - 2, 1, 1, KEY_RIM)
                g.drawRect(x + 1, top + buttons, w - 2, 1, KEY_RIM)
                g.drawRect(x + w / 2, top + 1, 1, buttons - 1, KEY_RIM)
            }
        }

        /** A pad's bottom ([south]) or right face button, as [pad] marks it. */
        class Face(val pad: InputMethod, val south: Boolean) : Glyph {
            override fun width(prompts: ButtonPrompts, size: Int) = prompts.height(size)

            override fun draw(prompts: ButtonPrompts, x: Int, baseline: Int, size: Int) {
                val g = prompts.g
                val d = prompts.height(size)
                val top = prompts.top(baseline, size)
                if (pad == InputMethod.PLAYSTATION_PAD) {
                    g.drawOval(x, top, d, d, PS_FILL)
                    g.drawOvalOutline(x, top, d, d, PS_RIM)
                    val inset = d * 3 / 10
                    if (south) {
                        // The cross, two pixels thick.
                        val far = d - 1 - inset
                        for (shift in 0..1) {
                            val left = x + inset + shift
                            val right = x + far + shift
                            g.drawLine(left, top + inset, right, top + far, PS_CROSS)
                            g.drawLine(left, top + far, right, top + inset, PS_CROSS)
                        }
                    } else {
                        // The circle, two pixels thick.
                        val ring = d - 2 * inset
                        g.drawOvalOutline(x + inset, top + inset, ring, ring, PS_CIRCLE)
                        val inner = ring - 2
                        g.drawOvalOutline(x + inset + 1, top + inset + 1, inner, inner, PS_CIRCLE)
                    }
                } else {
                    g.drawOval(x, top, d, d, if (south) XBOX_A else XBOX_B)
                    val letter = if (south) "A" else "B"
                    val textSize = size - 3
                    g.drawString(
                        letter,
                        x + (d - g.measureString(letter, textSize) + 1) / 2,
                        top + d / 2 + (textSize * CAP_CENTER).roundToInt(),
                        textSize,
                        XBOX_LETTER,
                    )
                }
            }
        }

        /** A d-pad, its left and right lit. */
        data object DPad : Glyph {
            override fun width(prompts: ButtonPrompts, size: Int) = prompts.height(size)

            override fun draw(prompts: ButtonPrompts, x: Int, baseline: Int, size: Int) {
                val g = prompts.g
                val h = prompts.height(size)
                val top = prompts.top(baseline, size)
                val arm = h / 3 or 1
                val mid = (h - arm) / 2
                g.drawRect(x + mid - 1, top - 1, arm + 2, h + 2, KEY_FILL)
                g.drawRect(x - 1, top + mid - 1, h + 2, arm + 2, KEY_FILL)
                g.drawRect(x + mid, top, arm, h, DPAD_DIM)
                g.drawRect(x, top + mid, h, arm, KEY_TEXT)
            }
        }
    }

    /** A key cap: dark, with a light rim whose corners are cut, and a shadow along its bottom. */
    private fun keyCap(x: Int, top: Int, w: Int, h: Int) {
        g.drawRect(x + 1, top + 1, w - 2, h - 2, KEY_FILL)
        g.drawRect(x + 1, top + h - 3, w - 2, 2, KEY_SHADOW)
        outline(g, x, top, w, h, KEY_RIM)
    }

    private companion object {
        /** Where a line's capitals are centered, above its baseline, as a share of its size. */
        const val CAP_CENTER = 0.36f

        /** Between two buttons of one prompt, between them and their words, and between prompts. */
        const val GLYPH_GAP = 3
        const val LABEL_GAP = 5
        const val PROMPT_GAP = 24

        /** Room each side of a key's name on its cap. */
        const val KEY_PADDING = 4

        const val KEY_FILL = 0xFF1C2433.toInt()
        const val KEY_SHADOW = 0xFF0E131C.toInt()
        const val KEY_RIM = 0xFFC8D2DE.toInt()
        const val KEY_TEXT = 0xFFFFFFFF.toInt()
        const val DPAD_DIM = 0xFF8A94A4.toInt()

        const val XBOX_A = 0xFF3FA535.toInt()
        const val XBOX_B = 0xFFD8392B.toInt()
        const val XBOX_LETTER = 0xFFFFFFFF.toInt()

        const val PS_FILL = 0xFF20242C.toInt()
        const val PS_RIM = 0xFFB8C0CC.toInt()
        const val PS_CROSS = 0xFF7FB2FF.toInt()
        const val PS_CIRCLE = 0xFFFF6A6A.toInt()

        /** A one-pixel rim around the [w] by [h] box at [x], [top], its corner pixels left out. */
        fun outline(g: Graphics, x: Int, top: Int, w: Int, h: Int, color: Int) {
            g.drawRect(x + 1, top, w - 2, 1, color)
            g.drawRect(x + 1, top + h - 1, w - 2, 1, color)
            g.drawRect(x, top + 1, 1, h - 2, color)
            g.drawRect(x + w - 1, top + 1, 1, h - 2, color)
        }
    }
}
