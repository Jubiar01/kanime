package com.kanime.app

import com.kanime.app.ui.player.SubtitleOffsetHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleOffsetHelperTest {

    @Test
    fun shiftSubtitleText_shiftsSrtTimestampsForwardAndBackward() {
        val srt = """
            1
            00:00:01,500 --> 00:00:04,000
            Hello world!

            2
            00:01:00,000 --> 00:01:02,250
            Second line
        """.trimIndent()

        val delayed = SubtitleOffsetHelper.shiftSubtitleText(srt, +1500L)
        assertEquals(
            """
                1
                00:00:03,000 --> 00:00:05,500
                Hello world!

                2
                00:01:01,500 --> 00:01:03,750
                Second line
            """.trimIndent(),
            delayed
        )

        val advanced = SubtitleOffsetHelper.shiftSubtitleText(srt, -2000L)
        assertEquals(
            """
                1
                00:00:00,000 --> 00:00:02,000
                Hello world!

                2
                00:00:58,000 --> 00:01:00,250
                Second line
            """.trimIndent(),
            advanced
        )
    }

    @Test
    fun shiftSubtitleText_shiftsWebVttShortAndLongTimestamps() {
        val vtt = """
            WEBVTT

            00:10.500 --> 00:12.000
            Short VTT cue

            00:01:00.000 --> 00:01:03.500
            Long VTT cue
        """.trimIndent()

        val shifted = SubtitleOffsetHelper.shiftSubtitleText(vtt, -500L)
        assertEquals(
            """
                WEBVTT

                00:10.000 --> 00:11.500
                Short VTT cue

                00:00:59.500 --> 00:01:03.000
                Long VTT cue
            """.trimIndent(),
            shifted
        )
    }
}
