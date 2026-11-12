package com.glasscar.launcher

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1024dp-h600dp-land-mdpi")
class DashboardUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var prefs: LauncherPreferences
    private val drawer = mutableStateOf(false)
    private val media = MutableStateFlow(MediaInfo(hasAccess = true))
    private val actions = object : LauncherActions {
        override fun open(app: AppEntry) {}
        override fun open(shortcut: Shortcut, apps: List<AppEntry>) {}
        override fun openPackage(packageName: String) {}
        override fun openNotificationAccess() {}
        override fun setVolume(fraction: Float) {}
        override fun setBrightness(fraction: Float) {}
        override fun playPause() {}
        override fun next() {}
        override fun previous() {}
    }
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("glasscar_v4", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = LauncherPreferences(context)
        val app = AppEntry("Aplicación de prueba", "test.app", "test.app.Main", Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).asImageBitmap())
        compose.setContent {
            LauncherRoot(listOf(app), MutableStateFlow(DriveState()), media,
                MutableStateFlow(CanState()), WeatherSource(), drawer.value, { drawer.value = !drawer.value }, { drawer.value = false }, actions, prefs)
        }
    }
    @Test fun dashboardRendersWithoutFakeVoltageAndDrawerOpens() {
        compose.onNodeWithText("Tablero").assertIsDisplayed()
        compose.onNodeWithText("12V").assertDoesNotExist()
        screenshot("dashboard-night")
        compose.onNodeWithContentDescription("Abrir aplicaciones").performClick()
        compose.onNodeWithText("Aplicación de prueba").assertIsDisplayed()
        screenshot("app-drawer")
    }
    @Test fun verticalGestureOpensDrawer() {
        compose.onRoot().performTouchInput { swipe(start = Offset(width * 0.95f, height * 0.7f), end = Offset(width * 0.95f, height * 0.3f), durationMillis = 400) }
        compose.onNodeWithText("Aplicación de prueba").assertIsDisplayed()
    }
    @Test fun dayThemeAndWeatherDetailsRender() {
        compose.runOnIdle { prefs.setNight(false) }
        compose.mainClock.advanceTimeBy(1000)
        screenshot("dashboard-day")
        compose.onNodeWithText("Open-Meteo · sin lectura").performClick()
        compose.onNode(hasText("Clima exterior") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNodeWithText("Actualizar ahora").assertIsDisplayed()
        screenshot("weather-details")
    }
    @Test fun instrumentsDoNotPresentEstimatedParkWithoutCan() {
        compose.onNodeWithText("Instrumentos").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("SIN LECTURA DE MARCHA CAN").assertIsDisplayed()
        screenshot("instruments-no-can")
    }
    @Test fun carPlayWithoutProviderDoesNotOfferUnrelatedWidgets() {
        compose.onNodeWithText("CarPlay").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Esta app no ofrece un widget").assertIsDisplayed()
        compose.onNodeWithText("Añadir widget de CarPlay").assertDoesNotExist()
        screenshot("carplay-no-widget")
    }
    @Test @Config(qualifiers = "w1920dp-h1080dp-land-mdpi")
    fun largeLowDensityHeadUnitKeepsReadableControls() {
        compose.onNodeWithContentDescription("Cambiar página de accesos").assertIsDisplayed()
        compose.onNodeWithText("Clima exterior").assertIsDisplayed()
        screenshot("dashboard-1920-mdpi")
    }
    @Test fun albumArtAndLargeControlsRenderInDayTheme() {
        val art = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(art).apply {
            drawColor(android.graphics.Color.rgb(18, 56, 82))
            drawCircle(150f, 150f, 95f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(60, 190, 180) })
        }
        compose.runOnIdle {
            prefs.setNight(false)
            media.value = MediaInfo(title = "Prueba de carátula", artist = "GlassCar", art = art.asImageBitmap(),
                hasAccess = true, hasSession = true, durationMs = 180000, positionMs = 45000)
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Prueba de carátula").assertIsDisplayed()
        screenshot("music-art-day")
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            val decor = android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull { it.isShown }
                ?: compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            decor.draw(android.graphics.Canvas(bitmap))
        }
        val file = File("build/reports/screenshots/$name.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}

