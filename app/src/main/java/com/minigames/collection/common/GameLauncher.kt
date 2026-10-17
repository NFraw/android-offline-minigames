package com.minigames.collection.common

import android.content.Context
import android.content.Intent
import com.minigames.collection.GameActivity
import com.minigames.collection.engine.EngineGameActivity

object GameLauncher {
    fun intent(context: Context, spec: GameSpec): Intent {
        val host = when (spec.runtime) {
            is GameRuntime.Canvas -> GameActivity::class.java
            is GameRuntime.Godot -> EngineGameActivity::class.java
        }
        return Intent(context, host).putExtra("game", spec.id)
    }
}
