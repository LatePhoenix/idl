package app.idl.domain

/**
 * Shape names for status marks. Color may accompany a glyph; it is never the only cue
 * (decision D-28). Names match the core_proto pack.
 */
object StatusGlyphs {
    fun availability(a: Availability): String = when (a) {
        Availability.AVAILABLE -> "circle"
        Availability.TEXT_ONLY -> "speech_bubble"
        Availability.CALL_OK -> "handset"
        Availability.GAMING -> "controller"
        Availability.BUSY -> "hourglass"
        Availability.DO_NOT_DISTURB -> "crescent"
        Availability.AFK -> "clock"
        Availability.OFFLINE -> "hollow_ring"
    }

    fun activity(a: ActivityType): String = when (a) {
        ActivityType.NONE -> ""
        ActivityType.WORKING -> "working"
        ActivityType.CODING -> "coding"
        ActivityType.GAMING -> "gaming"
        ActivityType.VR -> "vr"
        ActivityType.WATCHING -> "watching"
        ActivityType.LISTENING -> "listening"
        ActivityType.READING -> "reading"
        ActivityType.TRAVELING -> "traveling"
        ActivityType.EXERCISING -> "exercising"
        ActivityType.SLEEPING -> "sleeping"
        ActivityType.CUSTOM -> "custom"
    }
}
