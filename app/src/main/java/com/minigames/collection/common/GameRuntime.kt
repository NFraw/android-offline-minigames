package com.minigames.collection.common

/** 大厅选择承载方式，不要求每款游戏都是 Android View。 */
sealed interface GameRuntime {
    data class Canvas(val viewClassName: String) : GameRuntime {
        init { require(viewClassName.isNotBlank()) }
    }
    data class Godot(val scenePath: String) : GameRuntime {
        init {
            require(scenePath.matches(Regex("res://games/[a-z0-9_]+/[a-z0-9_/]+\\.tscn")))
            require(".." !in scenePath)
        }
    }
}
