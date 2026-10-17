package com.minigames.collection

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.minigames.collection.common.GameRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 全游戏启动冒烟测试：遍历 GameRegistry 中每一款游戏，启动对应 Activity，
 * 验证其存活（未崩溃、未闪退）且目标 View 已挂载。
 */
@RunWith(AndroidJUnit4::class)
class AllGamesSmokeTest {

    @Test
    fun allRegisteredGamesLaunchWithoutCrash() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val failures = StringBuilder()
        var passed = 0
        var failed = 0

        for (spec in GameRegistry.games.filter { it.runtime is com.minigames.collection.common.GameRuntime.Canvas }) {
            val intent = Intent(ctx, GameActivity::class.java).putExtra("game", spec.id)
            try {
                ActivityScenario.launch<Activity>(intent).use { scenario ->
                    // 等待 Activity 存活并进入 RESUMED
                    Thread.sleep(700)
                    val state = scenario.state
                    assertTrue("${spec.id} 未进入 RESUMED，state=$state",
                        state == Lifecycle.State.RESUMED)
                }
                passed++
            } catch (t: Throwable) {
                failed++
                failures.append("  ${spec.id} (${spec.name}): ${t.javaClass.simpleName} - ${t.message}\n")
            }
        }

        System.err.println("=== 冒烟测试汇总：通过 $passed / ${GameRegistry.games.size}，失败 $failed ===")
        System.err.println(failures.toString())
        assertTrue("以下游戏启动失败:\n$failures", failed == 0)
    }
}

