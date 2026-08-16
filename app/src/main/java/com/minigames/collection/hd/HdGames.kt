package com.minigames.collection.hd

/** HD 游戏工厂：id -> libGDX 游戏实例。 */
object HdGames {

    fun create(gameId: String): Game2D = when (gameId) {
        "2048_hd" -> Lgdx2048()
        else -> Lgdx2048()
    }
}
