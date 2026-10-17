package com.minigames.collection.runtime

import com.minigames.collection.common.GameRuntime
import com.minigames.collection.common.GameRegistry
import org.junit.Assert.*
import org.junit.Test

class RuntimeRoutingTest {
    @Test fun allRegisteredGamesUseGodot() {
        assertTrue(GameRegistry.byId("plane")!!.runtime is GameRuntime.Godot)
        GameRegistry.games.forEach { assertTrue(it.id, it.runtime is GameRuntime.Godot) }
        assertTrue(GameRegistry.byId("flight")!!.runtime is GameRuntime.Godot)
        assertTrue(GameRegistry.byId("animal")!!.runtime is GameRuntime.Godot)
        assertEquals(37, GameRegistry.games.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesSceneTraversal() { GameRuntime.Godot("res://games/../private.tscn") }

    @Test(expected = IllegalArgumentException::class)
    fun refusesExternalScene() { GameRuntime.Godot("https://example.com/game.tscn") }
}
