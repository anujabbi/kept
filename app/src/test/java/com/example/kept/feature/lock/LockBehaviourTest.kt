package com.example.kept.feature.lock

import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.example.kept.core.ui.shots.Shot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.lang.reflect.Proxy

/** What the lock screen does when touched (issue #127). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class LockBehaviourTest {

    @get:Rule val rule = createComposeRule()

    private class Calls {
        val completed = mutableListOf<Long>()
        val undone = mutableListOf<Long>()
        val photos = mutableListOf<Pair<Long, String>>()
        var phone = 0
        var breaks = 0
        var opened = 0
        fun actions() = LockActions(
            onComplete = { completed += it },
            onUndo = { undone += it },
            onPhoto = { id, path -> photos += id to path },
            onPhone = { phone++ },
            onBreak = { breaks++ },
            onOpenApp = { opened++ },
        )
    }

    /**
     * Stands in for the system: grants the camera permission and reports every picture as taken,
     * recording what was asked for.
     */
    private class FakeSystem : ActivityResultRegistry() {
        val launched = mutableListOf<String>()
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            when (contract) {
                is ActivityResultContracts.RequestPermission -> { launched += "permission:$input"; dispatchResult(requestCode, true) }
                is ActivityResultContracts.TakePicture -> { launched += "camera:$input"; dispatchResult(requestCode, true) }
                else -> launched += "other"
            }
        }
    }

    private val current = mutableStateOf(LockFixtures.state(2, 0))

    /**
     * `FileProvider` keeps its path roots in a static cache keyed by authority. Robolectric reuses
     * a sandbox (and so that cache) across tests but gives each test a new files directory, so a
     * cached root from an earlier test would not contain this test's proof file. Clear it.
     *
     * Then, only on a JVM whose path separator is not `/` (the agents' Windows machine): androidx
     * `FileProvider` 1.15.0 decides whether a file is under a root with `startsWith(root + '/')`,
     * which is right on a device and can never be true for `C:\...\files\proof\proof_1.jpg`.
     * [separatorAwareStrategy] stands in for that one comparison and nothing else.
     */
    @Before fun prepareFileProvider() {
        val f = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val cache = f.get(null) as MutableMap<String, Any>
        synchronized(cache) { cache.clear() }
        if (File.separatorChar == '/') return
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val authority = "${ctx.packageName}.files"
        // The library's own strategy, built from the manifest and `file_paths.xml` for this
        // test's files directory, so the configured roots are still the real ones.
        val real = FileProvider::class.java
            .getDeclaredMethod("getPathStrategy", Context::class.java, String::class.java, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(null, ctx, authority, 0)!!
        synchronized(cache) { cache[authority] = separatorAwareStrategy(real, authority) }
    }

    /**
     * `FileProvider`'s `SimplePathStrategy.getUriForFile` with the host's separator in place of
     * the hard-coded `/`: same roots (read from [real]), same longest-root rule, same
     * `content://authority/root-name/relative/path` result, same error when no root matches.
     * Everything else goes to [real].
     */
    private fun separatorAwareStrategy(real: Any, authority: String): Any {
        val strategy = Class.forName("androidx.core.content.FileProvider\$PathStrategy", true, FileProvider::class.java.classLoader)
        val rootsField = real.javaClass.getDeclaredField("mRoots").apply { isAccessible = true }
        return Proxy.newProxyInstance(strategy.classLoader, arrayOf(strategy)) { _, method, args ->
            if (method.name != "getUriForFile") {
                return@newProxyInstance method.apply { isAccessible = true }.invoke(real, *(args ?: emptyArray()))
            }
            @Suppress("UNCHECKED_CAST")
            val roots = rootsField.get(real) as Map<String, File>
            val path = (args[0] as File).canonicalPath
            val root = roots.entries
                .filter { path == it.value.path || path.startsWith(it.value.path + File.separator) }
                .maxByOrNull { it.value.path.length }
                ?: throw IllegalArgumentException("Failed to find configured root that contains $path")
            val relative = path.substring(root.value.path.length).trimStart(File.separatorChar).replace(File.separatorChar, '/')
            Uri.Builder().scheme("content").authority(authority)
                .encodedPath(Uri.encode(root.key) + '/' + Uri.encode(relative, "/")).build()
        }
    }

    private fun show(calls: Calls = Calls(), system: FakeSystem = FakeSystem(), label: String = "Chrome"): Calls {
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = system }
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                Shot { LockScreen(current.value, label, calls.actions()) }
            }
        }
        return calls
    }

    @Test fun headlineAndLineNameTheLockedApp() {
        show(label = "Instagram")
        rule.onNodeWithTag("lock_headline").assertTextEquals("Instagram can wait.")
        rule.onNodeWithTag("lock_line").assertTextEquals("Finish your promised habits to unlock.")
        rule.onNodeWithTag("lock_count").assertTextEquals("0 of 2")
    }

    @Test fun tapTicksAManualHabitAndHoldUndoesADoneOne() {
        current.value = LockFixtures.state(2, 1)
        val calls = show()
        rule.onNodeWithTag("lock_habit_2").performClick()
        assertEquals(listOf(2L), calls.completed)
        rule.onNodeWithTag("lock_habit_1").performClick()
        assertEquals("tapping a done row does not tick it again", listOf(2L), calls.completed)
        rule.onNodeWithTag("lock_habit_1").performTouchInput { longClick() }
        assertEquals(listOf(1L), calls.undone)
    }

    @Test fun aPhotoHabitOpensTheCameraAndSavesTheProof() {
        // Habit 5 is "Tidy room", a photo habit.
        current.value = LockFixtures.state(6, 0)
        val system = FakeSystem()
        val calls = show(system = system)
        rule.onNodeWithTag("lock_habit_5").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals("permission:android.permission.CAMERA", system.launched.first())
        assertTrue("the camera was opened: ${system.launched}", system.launched.getOrNull(1)?.startsWith("camera:content://") == true)
        assertTrue("with a URI under the configured proof root: ${system.launched}", system.launched[1].contains(".files/proof/proof_"))
        assertTrue("a photo tap is not a plain tick", calls.completed.isEmpty())
        val (id, path) = calls.photos.single()
        assertEquals(5L, id)
        assertTrue("saved under files/proof like Home: $path", path.replace(File.separatorChar, '/').contains("/proof/proof_") && path.endsWith(".jpg"))
    }

    /**
     * A second photo in the same JVM. `FileProvider` caches its roots statically, and a reused
     * Robolectric sandbox gives each test a new files directory, so without [prepareFileProvider]
     * whichever photo test ran second failed ("Failed to find configured root").
     */
    @Test fun aPhotoHabitWorksWhateverRanBefore() {
        current.value = LockFixtures.state(6, 0)
        val system = FakeSystem()
        val calls = show(system = system)
        rule.onNodeWithTag("lock_habit_5").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(5L, calls.photos.single().first)
    }

    @Test fun aLateTickReadsTooLateNotDone() {
        current.value = LockFixtures.state(3, 2, late = 1)
        show()
        rule.onNodeWithText("Too late").assertIsDisplayed()
        // Only the on-time tick reads "Done".
        assertEquals(1, rule.onAllNodesWithText("Done").fetchSemanticsNodes().size)
    }

    @Test fun phoneOpensTheDialerInOneTapAndBreakGoesToTheBreakFlow() {
        val calls = show()
        rule.onNodeWithTag("emergency_call").assertTextEquals("Phone").performClick()
        assertEquals(1, calls.phone)
        rule.onNodeWithTag("break_lock").assertTextEquals("Break the lock").performClick()
        assertEquals(1, calls.breaks)
        rule.onNodeWithText("Open KEPT").assertDoesNotExist()
        rule.onNodeWithTag("lock_open_app").assertDoesNotExist()
    }

    @Test fun phoneIsThereWhateverIsLeft() {
        show()
        for ((total, done) in listOf(1 to 0, 2 to 1, 10 to 9)) {
            current.value = LockFixtures.state(total, done)
            rule.waitForIdle()
            rule.onNodeWithTag("emergency_call").assertIsDisplayed()
        }
    }

    @Test fun theLastTickOpensTheScreenWithOneButton() {
        current.value = LockFixtures.state(2, 1)
        val calls = show()
        current.value = LockFixtures.state(2, 2)
        rule.waitForIdle()
        rule.onNodeWithTag("lock_headline").assertTextEquals("Chrome is open.")
        rule.onNodeWithTag("lock_line").assertTextEquals("Promise kept. Enjoy it.")
        rule.onNodeWithTag("emergency_call").assertDoesNotExist()
        rule.onNodeWithTag("break_lock").assertDoesNotExist()
        rule.onNodeWithTag("lock_open_app").assertTextEquals("Open Chrome").performClick()
        assertEquals(1, calls.opened)
    }

    @Test fun onlyTheListScrollsWithTenHabits() {
        current.value = LockFixtures.state(10, 2)
        show()
        rule.waitForIdle()
        fun top(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y
        val headline = top("lock_headline")
        val count = top("lock_count")
        val phone = top("emergency_call")
        val first = top("lock_habit_1")
        rule.onNodeWithTag("lock_habit_list").performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertTrue("the list scrolled", top("lock_habit_1") < first)
        assertEquals("the headline stays put", headline, top("lock_headline"), 0.5f)
        assertEquals("'Today' stays put", count, top("lock_count"), 0.5f)
        assertEquals("the footer stays put", phone, top("emergency_call"), 0.5f)
        rule.onNodeWithTag("lock_habit_10").assertIsDisplayed()
    }
}
