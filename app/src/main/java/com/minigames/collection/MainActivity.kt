package com.minigames.collection

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.minigames.collection.engine.EngineGameActivity

/** Android launcher bootstrap; the hall, settings and all games live in Godot. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, EngineGameActivity::class.java)
            .putExtra("game", "hall")
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
