package com.glasscar.launcher

import android.content.*
import android.os.Binder
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.tw.carinfoservice.CarServiceAidl
import com.tw.carinfoservice.CarServiceCallBack
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.time.Duration
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class TopwayConnectionTest {
    private class Host : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        val bindings = mutableListOf<ServiceConnection>()
        var unbound = 0
        override fun bindService(intent: Intent, conn: ServiceConnection, flags: Int): Boolean {
            bindings += conn; return true
        }
        override fun unbindService(conn: ServiceConnection) { unbound++ }
    }
    private class Vendor(val failRegistration: Boolean = false) {
        val binder = Binder()
        var callback: CarServiceCallBack? = null
        var reads = 0
        var voltage = 13.7f
        var originalVoltage = -1f
        val api = Proxy.newProxyInstance(CarServiceAidl::class.java.classLoader,
            arrayOf(CarServiceAidl::class.java)) { _, method, args ->
            when (method.name) {
                "asBinder" -> binder
                "registerCarServiceCallBack" -> {
                    if (failRegistration) throw android.os.RemoteException("not implemented")
                    callback = args!![0] as CarServiceCallBack; null
                }
                "getInstantaneous_Speed" -> { reads++; intArrayOf(45, 0) }
                "getGear_Information" -> 0
                "getCarReverse" -> 0
                "getHeadlight" -> 1
                "getElectricVoltage" -> voltage
                "getOriginalCarVoltage" -> originalVoltage
                else -> null
            }
        } as CarServiceAidl
        init { binder.attachInterface(api, "com.tw.carinfoservice.CarServiceAidl") }
    }
    private val name = ComponentName(TopwayCarInfo.PACKAGE, "${TopwayCarInfo.PACKAGE}.CarService")
    private val main get() = shadowOf(Looper.getMainLooper())

    @Test fun usesOriginalVehicleVoltageWhenElectricVoltageIsUnavailable() {
        val host = Host(); val vendor = Vendor().apply { voltage = -1f; originalVoltage = 14.2f }
        val readings = mutableListOf<TopwayReading>()
        val source = TopwayCarInfo(host, Executor { it.run() }) { readings += it }
        source.start(); host.bindings.last().onServiceConnected(name, vendor.binder); main.idle()
        assertEquals(14.2f, readings.last().voltageV!!, 0.01f)
        source.stop()
    }

    @Test fun pollsUnchangedReadingsEvenIfCallbackRegistrationFailsAndStopsCleanly() {
        val host = Host(); val vendor = Vendor(true); val readings = mutableListOf<TopwayReading>()
        val source = TopwayCarInfo(host, Executor { it.run() }) { readings += it }
        source.start(); host.bindings.last().onServiceConnected(name, vendor.binder); main.idle()
        assertEquals(1, vendor.reads)
        main.idleFor(Duration.ofSeconds(6))
        assertEquals(4, vendor.reads)
        assertEquals(Gear.D, readings.last().gear)
        assertEquals(13.7f, readings.last().voltageV!!, 0.01f)
        source.stop(); main.idleFor(Duration.ofSeconds(10))
        assertEquals(4, vendor.reads); assertEquals(1, host.unbound)
    }

    @Test fun reconnectRejectsOldCallbacksAndResumesPolling() {
        val host = Host(); val first = Vendor(); val second = Vendor(); val readings = mutableListOf<TopwayReading>()
        val source = TopwayCarInfo(host, Executor { it.run() }) { readings += it }
        source.start(); val oldConnection = host.bindings.last()
        oldConnection.onServiceConnected(name, first.binder); main.idle()
        val oldCallback = first.callback!!
        oldConnection.onServiceDisconnected(name); main.idleFor(Duration.ofSeconds(3))
        host.bindings.last().onServiceConnected(name, second.binder); main.idle()
        val count = readings.size
        oldCallback.onGear_Information(3); main.idle()
        assertEquals(count, readings.size)
        // A late disconnect from the old binding must not tear down the new one.
        oldConnection.onServiceDisconnected(name)
        main.idleFor(Duration.ofSeconds(4))
        assertEquals(3, second.reads)
        assertEquals(2, host.bindings.size)
        source.stop()
    }

    @Test fun inFlightReadAfterStopCannotBlockNextSession() {
        val host = Host(); val jobs = ArrayDeque<Runnable>(); val readings = mutableListOf<TopwayReading>()
        val source = TopwayCarInfo(host, Executor { jobs.addLast(it) }) { readings += it }
        source.start(); host.bindings.last().onServiceConnected(name, Vendor().binder)
        jobs.removeFirst().run(); main.idle() // First poll is now queued.
        source.stop(); source.start()
        host.bindings.last().onServiceConnected(name, Vendor().binder)
        while (jobs.isNotEmpty()) { jobs.removeFirst().run(); main.idle() }
        assertEquals(1, readings.size)
        main.idleFor(Duration.ofSeconds(2))
        jobs.removeFirst().run(); main.idle()
        assertEquals(2, readings.size)
        source.stop()
    }
}
