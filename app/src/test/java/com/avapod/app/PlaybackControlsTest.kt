package com.avapod.app

import androidx.media3.common.Player
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

/** Exercises the actual Play button command logic, including idle and ended recovery. */
class PlaybackControlsTest {
    private fun player(state: Int, wantsPlay: Boolean, calls: MutableList<String>): Player =
        Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.name) {
                "getPlaybackState" -> state
                "getPlayWhenReady" -> wantsPlay
                "getPlayerError" -> null
                "play", "pause", "prepare", "seekTo" -> { calls.add(method.name); null }
                "toString" -> "TestPlayer"
                else -> throw AssertionError("Unexpected call: " + method.name)
            }
        } as Player
    @Test fun idlePlayerIsPreparedBeforePlaying() {
        val calls = mutableListOf<String>()
        PlayerManager.toggle(player(Player.STATE_IDLE, false, calls))
        assertEquals(listOf("prepare", "play"), calls)
    }
    @Test fun endedEpisodeSeeksBackBeforePlaying() {
        val calls = mutableListOf<String>()
        PlayerManager.toggle(player(Player.STATE_ENDED, true, calls))
        assertEquals(listOf("seekTo", "play"), calls)
    }
    @Test fun stoppedPlayerWithAnOldPlayRequestCanStillRestart() {
        val calls = mutableListOf<String>()
        PlayerManager.toggle(player(Player.STATE_IDLE, true, calls))
        assertEquals(listOf("prepare", "play"), calls)
    }
    @Test fun bufferedPlayRequestCanBePaused() {
        val calls = mutableListOf<String>()
        PlayerManager.toggle(player(Player.STATE_BUFFERING, true, calls))
        assertEquals(listOf("pause"), calls)
    }
    @Test fun pausedPreparedPlayerResumesWithoutResettingPosition() {
        val calls = mutableListOf<String>()
        PlayerManager.toggle(player(Player.STATE_READY, false, calls))
        assertEquals(listOf("play"), calls)
    }
}
