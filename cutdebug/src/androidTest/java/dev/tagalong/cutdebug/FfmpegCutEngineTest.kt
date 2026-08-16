package dev.tagalong.cutdebug

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FfmpegCutEngineTest : CutEngineContractTest() {
    override fun engine(): CutEngine = FfmpegCutEngine()
}
