package com.minigames.collection

import android.content.Intent
import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.minigames.collection.hd.HdGames

/** libGDX 游戏宿主：全屏沉浸式，通过 game 参数选择 HD 游戏。 */
class LgdxGameActivity : AndroidApplication() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gameId = intent.getStringExtra("game") ?: ""
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useWakelock = true
        }
        initialize(HdGames.create(gameId), config)
    }
}
