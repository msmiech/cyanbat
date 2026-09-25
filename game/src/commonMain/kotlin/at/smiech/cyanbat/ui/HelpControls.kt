package at.smiech.cyanbat.ui

import org.jetbrains.compose.resources.StringResource

/**
 * The help dialog's controls section, which is the one part of it that differs by platform:
 * a phone is flown by touch and paused with Back, a desktop by mouse or keys and paused with Esc.
 */
internal expect val helpControls: StringResource
