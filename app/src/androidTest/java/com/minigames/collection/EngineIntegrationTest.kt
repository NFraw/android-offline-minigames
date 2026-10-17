package com.minigames.collection

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.minigames.collection.common.*
import com.minigames.collection.engine.EngineGameActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EngineIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val endpoint get() = Uri.parse("content://${context.packageName}.game-platform")
    private fun call(method: String, extras: Bundle = Bundle()): Bundle =
        requireNotNull(context.contentResolver.call(endpoint, method, null, extras.apply { putString("game", "plane") }))

    @Test fun launcherRoutesToRegisteredRuntime() {
        assertEquals(EngineGameActivity::class.java.name, GameLauncher.intent(context, GameRegistry.byId("plane")!!).component!!.className)
        for (spec in GameRegistry.games) assertEquals(spec.id, EngineGameActivity::class.java.name, GameLauncher.intent(context, spec).component!!.className)
    }

    @Test fun platformSettingsShareTheNativeStore() {
        val settings = SettingsStore(context)
        val sound = settings.soundEnabled
        val haptic = settings.hapticEnabled
        try {
            call("settings", Bundle().apply { putBoolean("sound", false); putBoolean("haptic", false) })
            assertFalse(settings.soundEnabled)
            assertFalse(settings.hapticEnabled)
            assertFalse(call("configuration").getBoolean("sound"))
        } finally {
            settings.soundEnabled = sound
            settings.hapticEnabled = haptic
        }
    }

    @Test fun resultReceiptPersistsAndRejectsDuplicateHistory() {
        val prefs = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
        val snapshot = prefs.all.toMap()
        try {
            val before = ScoreStore(context).history("plane")
            val oldBest = ScoreStore(context).best("plane")
            val id = UUID.randomUUID().toString() + ":1"
            val payload = Bundle().apply { putString("round", id); putInt("score", 10) }
            assertTrue(call("complete", payload).getBoolean("accepted"))
            assertFalse(call("complete", payload).getBoolean("accepted"))
            assertEquals((listOf(10) + before).take(20), ScoreStore(context).history("plane"))
            assertEquals(maxOf(oldBest, 10), ScoreStore(context).best("plane"))
            SettingsStore(context).clearScores()
            assertFalse(ScoreStore(context).saveOnce("plane", 0, id))
        } finally {
            val editor = prefs.edit()
            editor.clear()
            snapshot.forEach { (key, value) ->
                when (value) { is Int -> editor.putInt(key, value); is String -> editor.putString(key, value) }
            }
            // clearScores 的作用仅在本测试的隔离模拟器执行；恢复其他游戏原有最高分。
            editor.commit()
        }
    }

    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command)
        .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText() } }
    private fun waitFor(description: String, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 45000
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(200)
        }
        fail("等待超时：$description")
    }
    @Test fun backgroundAndLauncherResumeKeepCurrentGame() {
        context.startActivity(GameLauncher.intent(context, GameRegistry.byId("snake")!!).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var pid = ""
        waitFor("贪吃蛇已就绪") {
            pid = shell("pidof ${context.packageName}:godot").trim()
            pid.matches(Regex("[0-9]+")) && shell("logcat -d --pid=$pid -s godot:I").contains("GAME_READY:snake ")
        }
        shell("logcat -c")
        shell("input keyevent KEYCODE_HOME")
        waitFor("切后台自动暂停") { shell("logcat -d --pid=$pid -s godot:I").contains("GAME_PAUSED:snake") }
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitFor("原引擎恢复到前台") {
            shell("dumpsys activity activities").lineSequence().any { it.contains("topResumedActivity") && it.contains("EngineGameActivity") }
        }
        Thread.sleep(500)
        assertEquals(pid, shell("pidof ${context.packageName}:godot").trim())
        val log = shell("logcat -d --pid=$pid -s godot:I AndroidRuntime:E")
        assertFalse(log, log.contains("GAME_READY:") || log.contains("GAME_HALL_READY") || log.contains("SCRIPT ERROR"))
        shell("input keyevent KEYCODE_BACK")
        waitFor("回大厅") { shell("logcat -d --pid=$pid -s godot:I").contains("GAME_HALL_READY") }
    }
    @Test fun gameDirectionFollowsRegistryAcrossOppositeWindows() {
        for (id in listOf("flight", "snake", "flight")) {
            shell("logcat -c")
            context.startActivity(GameLauncher.intent(context, GameRegistry.byId(id)!!).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            var log = ""
            waitFor("固定方向与窗口：$id") {
                val pid = shell("pidof ${context.packageName}:godot").trim()
                if (!pid.matches(Regex("[0-9]+"))) false else {
                    log = shell("logcat -d --pid=$pid -s godot:I AndroidRuntime:E")
                    assertFalse(log, log.contains("SCRIPT ERROR") || log.contains("FATAL EXCEPTION"))
                    log.contains("GAME_READY:$id ")
                }
            }
            val line = log.lineSequence().last { it.contains("GAME_VIEWPORT:$id ") }
            val dimensions = Regex("[0-9]+(?:\\.[0-9]+)?").findAll(line.substringAfter("pixels=")).map { it.value.toDouble() }.toList()
            assertEquals(line, 4, dimensions.size)
            val landscape = GameRegistry.byId(id)!!.landscape
            assertEquals(line, landscape, dimensions[0] > dimensions[1])
            assertEquals(line, landscape, dimensions[2] > dimensions[3])
            assertEquals("画布应铺满窗口：$line", dimensions[0]/dimensions[1], dimensions[2]/dimensions[3], 0.01)
        }
        shell("input keyevent KEYCODE_BACK")
        waitFor("飞行棋暂停") { shell("logcat -d -s godot:I").contains("GAME_PAUSED:flight") }
        Thread.sleep(400)
        shell("input keyevent KEYCODE_BACK")
        waitFor("回大厅") { shell("logcat -d -s godot:I").contains("GAME_HALL_READY") }
    }

    @Test fun allGodotGamesStartPauseAndReturnWithinOneEngine() {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var enginePid = ""
        waitFor("Godot 大厅") {
            enginePid = shell("pidof ${context.packageName}:godot").trim()
            enginePid.matches(Regex("[0-9]+")) && shell("logcat -d --pid=$enginePid -s godot:I AndroidRuntime:E").contains("GAME_HALL_READY")
        }
        val initialPid = enginePid
        for (spec in GameRegistry.games) {
            shell("logcat -c")
            context.startActivity(GameLauncher.intent(context, spec).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitFor("Godot 游戏 ${spec.id}") {
                enginePid = shell("pidof ${context.packageName}:godot").trim()
                assertEquals("引擎应复用：${spec.id}", initialPid, enginePid)
                val log = shell("logcat -d --pid=$enginePid -s godot:I AndroidRuntime:E")
                assertFalse(log, log.contains("FATAL EXCEPTION") || log.contains("SCRIPT ERROR"))
                log.contains("GAME_READY:${spec.id} ")
            }
            shell("input keyevent KEYCODE_BACK")
            if (spec.id != "plane") {
                waitFor("${spec.id} 暂停") { shell("logcat -d --pid=$enginePid -s godot:I").contains("GAME_PAUSED:${spec.id}") }
                Thread.sleep(400)
                shell("input keyevent KEYCODE_BACK")
            }
            waitFor("${spec.id} 返回 Godot 大厅") {
                val log = shell("logcat -d --pid=$enginePid -s godot:I AndroidRuntime:E")
                assertFalse(log, log.contains("FATAL EXCEPTION") || log.contains("SCRIPT ERROR"))
                log.contains("GAME_HALL_READY")
            }
            assertEquals(initialPid, shell("pidof ${context.packageName}:godot").trim())
        }
    }
}
