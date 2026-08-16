package dev.tagalong.cutdebug

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/**
 * Kept side-by-side with [FfmpegCutEngineTest] for reference/debugging (see
 * notes/results.md). Expected to fail — that failure is the bake-off's documented finding,
 * not a regression to chase.
 */
@RunWith(AndroidJUnit4::class)
class Media3CutEngineTest : CutEngineContractTest() {
    override fun engine(): CutEngine = Media3CutEngine()
}
