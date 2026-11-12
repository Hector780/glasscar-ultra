package com.glasscar.launcher

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1024dp-h600dp-land-mdpi")
class VolumeGestureTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val value = mutableFloatStateOf(0.25f)
    private val commits = mutableListOf<Float>()
    @Before fun setup() {
        compose.setContent {
            GlassTube(value.floatValue, Glyph.Speaker, Modifier.width(300.dp).height(50.dp).testTag("volume"),
                onCommit = { commits += it }, label = "10/40") { value.floatValue = it }
        }
    }
    @Test fun nativeTapCommitsExactlyOnce() {
        compose.onNodeWithTag("volume").performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        compose.runOnIdle { assertEquals(1, commits.size); assertEquals(0.5f, commits.single(), 0.03f) }
    }
    @Test fun nativeDragCommitsOnlyAtRelease() {
        compose.onNodeWithTag("volume").performTouchInput {
            down(Offset(width * 0.2f, height * 0.5f))
            moveTo(Offset(width * 0.4f, height * 0.5f))
        }
        compose.runOnIdle { assertTrue(commits.isEmpty()) }
        compose.onNodeWithTag("volume").performTouchInput {
            moveTo(Offset(width * 0.6f, height * 0.5f))
            up()
        }
        compose.runOnIdle { assertEquals(1, commits.size); assertEquals(0.6f, commits.single(), 0.03f) }
    }
    @Test fun cancelledNativeDragDoesNotSendVolume() {
        compose.onNodeWithTag("volume").performTouchInput {
            down(Offset(width * 0.2f, height * 0.5f))
            moveTo(Offset(width * 0.6f, height * 0.5f))
            cancel()
        }
        compose.runOnIdle { assertTrue(commits.isEmpty()) }
    }
}
