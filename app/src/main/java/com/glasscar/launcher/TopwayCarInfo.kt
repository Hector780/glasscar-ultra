package com.glasscar.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.Executor
import com.tw.carinfoservice.CarServiceAidl
import com.tw.carinfoservice.CarServiceCallBack

/**
 * Datos del vehículo desde el servicio nativo de Topway (`com.tw.carinfoservice`).
 *
 * Evidencia (TS10.2.2_20230517, carinfoservice versionName 251230):
 * - `CarService` exportado, sin permiso, acción `com.tw.carinfoservice.CarService.Bind`.
 * - Contrato tomado de los .aidl incluidos en su propio APK (copiados sin cambios a
 *   app/src/main/aidl/com/tw/carinfoservice/), por lo que los códigos de transacción coinciden.
 * - Topway no publica estos datos por broadcast; los intents genéricos de [CanConfig] no aparecieron
 *   en el historial de broadcasts del equipo.
 *
 * Sólo se usan velocidad, marcha, reversa, faros y voltaje. No se envía nada al vehículo.
 */
class TopwayCarInfo(
    private val context: Context,
    private val worker: Executor = readWorker,
    private val sink: (TopwayReading) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private var service: CarServiceAidl? = null
    private var bound = false
    private var wanted = false
    private var generation = 0
    private var connection: ServiceConnection? = null
    private var callback: CarServiceCallBack? = null
    private var death: Pair<IBinder, IBinder.DeathRecipient>? = null

    private fun poll(api: CarServiceAidl, token: Int) {
        if (!wanted || token != generation || service !== api) return
        worker.execute {
            val reading = pollOnce(api)
            main.post {
                if (wanted && token == generation && service === api) {
                    if (!reading.isEmpty()) sink(reading)
                    if (!api.asBinder().isBinderAlive) reconnect("binder_dead")
                    else main.postDelayed({ poll(api, token) }, POLL_MS)
                }
            }
        }
    }

    fun start() {
        wanted = true
        if (bound) return
        main.removeCallbacksAndMessages(null)
        val token = ++generation
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (!wanted || token != generation || service != null) return
                val api = CarServiceAidl.Stub.asInterface(binder)
                service = api
                val cb = createCallback(token)
                callback = cb
                val recipient = IBinder.DeathRecipient {
                    main.post { if (wanted && token == generation) reconnect("binderDied") }
                }
                runCatching { binder.linkToDeath(recipient, 0); death = binder to recipient }
                worker.execute {
                    // A failed optional transaction must not block the available getters.
                    runCatching { api.registerCarServiceCallBack(cb) }.onFailure { record("register_failed:${it.javaClass.simpleName}") }
                    runCatching { api.getData() }.onFailure { record("getData_failed:${it.javaClass.simpleName}") }
                    runCatching { api.requestData() }.onFailure { record("requestData_failed:${it.javaClass.simpleName}") }
                    main.post { poll(api, token) }
                }
                record("connected")
            }
            override fun onServiceDisconnected(name: ComponentName) {
                if (token == generation) reconnect("disconnected")
            }
            override fun onBindingDied(name: ComponentName) {
                if (token == generation) reconnect("binding_died")
            }
            override fun onNullBinding(name: ComponentName) {
                if (token == generation) reconnect("null_binding")
            }
        }
        connection = conn
        val intent = Intent(ACTION).setComponent(ComponentName(PACKAGE, "$PACKAGE.CarService"))
        bound = runCatching { context.bindService(intent, conn, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        record(if (bound) "bind_requested" else "bind_unavailable")
        main.postDelayed({
            if (wanted && token == generation && service == null) reconnect("connect_timeout")
        }, if (bound) 10_000L else RETRY_MS)
    }

    fun stop() {
        wanted = false
        generation++
        main.removeCallbacksAndMessages(null)
        val old = service
        val cb = callback
        if (old != null && cb != null) worker.execute { runCatching { old.unRegisterCarServiceCallBack(cb) } }
        death?.let { (binder, recipient) -> runCatching { binder.unlinkToDeath(recipient, 0) } }
        death = null; service = null; callback = null
        if (bound) connection?.let { runCatching { context.unbindService(it) } }
        connection = null; bound = false
        // Do not call closureData(): that would stop delivery to the OEM apps too.
    }

    private fun reconnect(reason: String) {
        if (!wanted) return
        record(reason)
        stop()
        wanted = true
        main.postDelayed({ if (wanted) start() }, RETRY_MS)
    }

    /** Lecturas periódicas por getters: los callbacks sólo llegan cuando cambia un valor. */
    private fun pollOnce(api: CarServiceAidl): TopwayReading {
        val speed = runCatching { api.getInstantaneous_Speed() }.getOrNull()
        val gear = runCatching { api.getGear_Information() }.getOrNull()
        val reverse = runCatching { api.getCarReverse() }.getOrNull()
        val lights = runCatching { api.getHeadlight() }.getOrNull()
        val volts = runCatching { api.getElectricVoltage() }.getOrNull()
        val originalVolts = runCatching { api.getOriginalCarVoltage() }.getOrNull()
        val reading = TopwayReading(
            speedKmh = speed?.let { TopwayMapping.speedKmh(it.getOrNull(0) ?: -1, it.getOrNull(1) ?: 0) },
            gear = TopwayMapping.gearFromReverse(reverse) ?: gear?.let(TopwayMapping::gear),
            lightsOn = lights?.let(TopwayMapping::flag),
            voltageV = volts?.let(TopwayMapping::voltage) ?: originalVolts?.let(TopwayMapping::voltage)
        )
        record("poll speed=${speed?.toList()} gear=$gear reverse=$reverse lights=$lights volts=$volts originalVolts=$originalVolts")
        return reading
    }

    private fun emit(token: Int, reading: TopwayReading, event: String) {
        record(event)
        main.post { if (wanted && token == generation && !reading.isEmpty()) sink(reading) }
    }

    private fun record(event: String) {
        Log.d(TAG, event)
        HardwareDiagnostics.recordCan("topway.carinfo:$event", null)
    }

    private fun createCallback(token: Int) = object : CarServiceCallBack.Stub() {
        // --- Valores usados ---
        override fun onInstantaneous_Speed(mInstantaneous_Speed: Int) =
            emit(token, TopwayReading(speedKmh = TopwayMapping.speedKmh(mInstantaneous_Speed, 0)), "speed=$mInstantaneous_Speed")
        override fun onGear_Information(mGear_Information: Int) =
            emit(token, TopwayReading(gear = TopwayMapping.gear(mGear_Information)), "gear=$mGear_Information")
        override fun onCarReverse(mCarReverse: Boolean) =
            emit(token, TopwayReading(gear = TopwayMapping.gearFromReverse(if (mCarReverse) 1 else 0)), "reverse=$mCarReverse")
        override fun onHeadlight(mHeadlight: Boolean) =
            emit(token, TopwayReading(lightsOn = mHeadlight), "headlight=$mHeadlight")
        override fun onElectricVoltage(mVoltage: Float, mUnit: String?) =
            emit(token, TopwayReading(voltageV = TopwayMapping.voltage(mVoltage)), "voltage=$mVoltage$mUnit")
        override fun onOriginalCarVoltage(mVoltage: Float, mUnit: String?) =
            emit(token, TopwayReading(voltageV = TopwayMapping.voltage(mVoltage)), "car_voltage=$mVoltage$mUnit")

        // --- Valores no usados (se registran sólo en diagnóstico cuando son útiles) ---
        override fun onDrivingTime(mDrivingTime: Int) {}
        override fun onTotal_Mileage(mTotal_Mileage: Float, mUnit: String?) {}
        override fun onRecharge_Mileage(mRecharge_Mileage: Float, mUnit: String?) {}
        override fun onAverage_Fuel_Consumption(mAverage_Fuel_Consumption: Float, mUnit: String?) {}
        override fun onInstant_Fuel_Consumption(mInstant_Fuel_Consumption: Float, mUnit: String?) {}
        override fun onMain_Driving_Seat_Belt(mMain_Driving_Seat_Belt: Boolean) {}
        override fun onCo_Pilot_Seat_Belt(mCo_Pilot_Seat_Belt: Boolean) {}
        override fun onHandbrake(mHandbrake: Boolean) = record("handbrake=$mHandbrake")
        override fun onLeft_Turn_Signal(mLeft_Turn_Signal: Boolean) {}
        override fun onRight_Turn_Signal(mRight_Turn_Signal: Boolean) {}
        override fun onDouble_flash(mDouble_flash: Boolean) {}
        override fun onEngine_Speed(mEngine_Speed: Int) {}
        override fun onWater_Temp(mWater_Temp: Int, mUnit: String?) {}
        override fun onAmbient_Temp(mAmbient_Temp: Float, mUnit: String?) = record("ambient=$mAmbient_Temp$mUnit")
        override fun onEngine_Oil_Temp(mEngine_Oil_Temp: Float, mUnit: String?) {}
        override fun onOil_Volume(mOil_Volume: Float, mUnit: String?) {}
        override fun onElectricity(mElectricity: Float) {}
        override fun onCharging(mCharging: Int) {}
        override fun onVoltageWarning(mVoltageWarning: Boolean) {}
        override fun onLeftFrontDoor(mLeftFrontDoor: Boolean) {}
        override fun onRightFrontDoor(mRightFrontDoor: Boolean) {}
        override fun onBackDoor(mBackDoor: Boolean) {}
        override fun onLeftRearDoor(mLeftRearDoor: Boolean) {}
        override fun onRightRearDoor(mRightRearDoor: Boolean) {}
        override fun onExpand(bundle: Bundle?) {}
        override fun onTransTemp(mTransTemp: Float, mUnit: String?) {}
        override fun onIntkeTemp(mIntkeTemp: Float, mUnit: String?) {}
        override fun onIntkePressure(mIntkePressure: Float, mUnit: String?) {}
        override fun onTurbocharged(mTurbocharged: Float, mUnit: String?) {}
        override fun onDrivingMode(mDrivingMode: Int) {}
        override fun onOffSign(mOffSign: Boolean) {}
        override fun onOnSign(mOnSign: Boolean) {}
        override fun onLFTirePressure(mLFTirePres: Float, mUnit: String?) {}
        override fun onRFTirePressure(mRFTirePres: Float, mUnit: String?) {}
        override fun onLRTirePressure(mLRTirePres: Float, mUnit: String?) {}
        override fun onRRTirePressure(mRRTirePres: Float, mUnit: String?) {}
        override fun onThrottle(mThrottle: Float, mUnit: String?) {}
        override fun onSteeringWheelAngle(mteeringWheelAngle: Float, mUnit: String?) {}
        override fun onEngineLoad(mEngineLoad: Float, mUnit: String?) {}
        override fun onFRAngle(mFRAngle: Float, mUnit: String?) {}
        override fun onLRAngle(mLRAngle: Float, mUnit: String?) {}
        override fun onMaintenanceMileage(mMaintenanceMileage: Float, mUnit: String?) {}
        override fun onMaintenanceTime(mMaintenanceTime: String?) {}
        override fun onOilWarning(mOilWarning: Boolean) {}
        override fun onDrivenDistance(mDrivenDistance: Int, mUnit: String?) {}
        override fun onLFTireWarning(mTireWarningType: Int, mTireWarningValue: Int) {}
        override fun onRFTireWarning(mTireWarningType: Int, mTireWarningValue: Int) {}
        override fun onLRTireWarning(mTireWarningType: Int, mTireWarningValue: Int) {}
        override fun onRRTireWarning(mTireWarningType: Int, mTireWarningValue: Int) {}
        override fun onLFTireTemp(mTireTemp: Int, mUnit: String?) {}
        override fun onRFTireTemp(mTireTemp: Int, mUnit: String?) {}
        override fun onLRTireTemp(mTireTemp: Int, mUnit: String?) {}
        override fun onRRTireTemp(mTireTemp: Int, mUnit: String?) {}
        override fun onWashingFluidWarning(mWashingFluidWarning: Boolean) {}
        override fun onRadar(
            mFLR: Int, mFCLR: Int, mFCRR: Int, mFRR: Int, mRightFR: Int, mRightCFR: Int, mRightCRR: Int, mRightRR: Int,
            mRRR: Int, mRCRR: Int, mRCLR: Int, mRLR: Int, mLeftRR: Int, mLeftCRR: Int, mLeftCFR: Int, mLeftFR: Int
        ) {}
        override fun onTrack(mTrack: Int) {}
        override fun onTurn_Signal(mTurn_Signal: Int) {}
        override fun onAverage_Fuel_Consumption_For_This_Drive(mAverage_Fuel_Consumption: Float, mUnit: String?) {}
        override fun onExtendedInterface(bundle: Bundle?) {}
        override fun onMileage_For_This_Drive(mMileage: Float, mUnit: String?) {}
    }

    companion object {
        private val readWorker = Executors.newSingleThreadExecutor { task ->
            Thread(task, "TopwayReadOnly").apply { isDaemon = true }
        }
        const val PACKAGE = "com.tw.carinfoservice"
        const val ACTION = "com.tw.carinfoservice.CarService.Bind"
        private const val TAG = "GlassCarTopway"
        private const val RETRY_MS = 3_000L
        private const val POLL_MS = 2_000L
    }

}

data class TopwayReading(
    val speedKmh: Float? = null,
    val gear: Gear? = null,
    val lightsOn: Boolean? = null,
    val voltageV: Float? = null
) {
    fun isEmpty() = speedKmh == null && gear == null && lightsOn == null && voltageV == null
}

/** Conversión pura según los comentarios del propio CarServiceAidl.aidl. Probada en TopwayCarInfoTest. */
object TopwayMapping {
    /** AIDL: -1 inválido · 0:D 1:N 2:R 3:P 4:S */
    fun gear(raw: Int): Gear? = when (raw) {
        0 -> Gear.D
        1 -> Gear.N
        2 -> Gear.R
        3 -> Gear.P
        4 -> Gear.S
        else -> null
    }

    /** Reversa activa gana; salir de reversa no prueba ninguna otra marcha. */
    fun gearFromReverse(raw: Int?): Gear? = if (raw == 1) Gear.R else null

    /** AIDL: valor -1 inválido; unidad 0 = km, 1 = millas. */
    fun speedKmh(value: Int, unit: Int): Float? {
        if (value < 0) return null
        val kmh = if (unit == 1) value * 1.609344f else value.toFloat()
        return kmh.takeIf { it in 0f..400f }
    }

    fun flag(raw: Int): Boolean? = when (raw) { 1 -> true; 0 -> false; else -> null }

    fun voltage(raw: Float): Float? = raw.takeIf { it in 6f..30f }
}
