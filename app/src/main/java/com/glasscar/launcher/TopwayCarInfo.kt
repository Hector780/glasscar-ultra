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
    private val sink: (TopwayReading) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private var service: CarServiceAidl? = null
    private var bound = false
    private var wanted = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val api = CarServiceAidl.Stub.asInterface(binder)
            service = api
            runCatching {
                binder.linkToDeath({ main.post { reconnect("binderDied") } }, 0)
                api.registerCarServiceCallBack(callback)
                api.getData()       // abre el envío de velocidad/rpm/trayectoria
                api.requestData()   // pide un volcado inicial
                pollOnce(api)
            }.onFailure { Log.w(TAG, "registro fallido", it); record("register_failed:${it.javaClass.simpleName}") }
            record("connected")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            record("disconnected")
            reconnect("disconnected")
        }
    }

    fun start() {
        wanted = true
        if (bound) return
        val intent = Intent(ACTION).setPackage(PACKAGE)
        bound = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        record(if (bound) "bind_requested" else "bind_unavailable")
        if (!bound) runCatching { context.unbindService(connection) }
    }

    fun stop() {
        wanted = false
        main.removeCallbacksAndMessages(null)
        // Sin closureData(): cerraría el envío también para las apps nativas.
        runCatching { service?.unRegisterCarServiceCallBack(callback) }
        service = null
        if (bound) runCatching { context.unbindService(connection) }
        bound = false
    }

    private fun reconnect(reason: String) {
        if (!wanted) return
        Log.i(TAG, "reconexión: $reason")
        if (bound) runCatching { context.unbindService(connection) }
        bound = false
        main.removeCallbacksAndMessages(null)
        main.postDelayed({ if (wanted) start() }, RETRY_MS)
    }

    /** Lectura inicial por getters: los callbacks sólo llegan cuando cambia un valor. */
    private fun pollOnce(api: CarServiceAidl) {
        val speed = runCatching { api.getInstantaneous_Speed() }.getOrNull()
        val gear = runCatching { api.getGear_Information() }.getOrNull()
        val reverse = runCatching { api.getCarReverse() }.getOrNull()
        val lights = runCatching { api.getHeadlight() }.getOrNull()
        val volts = runCatching { api.getElectricVoltage() }.getOrNull()
        val reading = TopwayReading(
            speedKmh = speed?.let { TopwayMapping.speedKmh(it.getOrNull(0) ?: -1, it.getOrNull(1) ?: 0) },
            gear = TopwayMapping.gearFromReverse(reverse) ?: gear?.let(TopwayMapping::gear),
            lightsOn = lights?.let(TopwayMapping::flag),
            voltageV = volts?.let(TopwayMapping::voltage)
        )
        record("poll speed=${speed?.toList()} gear=$gear reverse=$reverse lights=$lights volts=$volts")
        if (!reading.isEmpty()) sink(reading)
    }

    private fun emit(reading: TopwayReading, event: String) {
        record(event)
        if (!reading.isEmpty()) sink(reading)
    }

    private fun record(event: String) {
        Log.d(TAG, event)
        HardwareDiagnostics.recordCan("topway.carinfo:$event", null)
    }

    private val callback = object : CarServiceCallBack.Stub() {
        // --- Valores usados ---
        override fun onInstantaneous_Speed(mInstantaneous_Speed: Int) =
            emit(TopwayReading(speedKmh = TopwayMapping.speedKmh(mInstantaneous_Speed, 0)), "speed=$mInstantaneous_Speed")
        override fun onGear_Information(mGear_Information: Int) =
            emit(TopwayReading(gear = TopwayMapping.gear(mGear_Information)), "gear=$mGear_Information")
        override fun onCarReverse(mCarReverse: Boolean) =
            emit(TopwayReading(gear = TopwayMapping.gearFromReverse(if (mCarReverse) 1 else 0)), "reverse=$mCarReverse")
        override fun onHeadlight(mHeadlight: Boolean) =
            emit(TopwayReading(lightsOn = mHeadlight), "headlight=$mHeadlight")
        override fun onElectricVoltage(mVoltage: Float, mUnit: String?) =
            emit(TopwayReading(voltageV = TopwayMapping.voltage(mVoltage)), "voltage=$mVoltage$mUnit")
        override fun onOriginalCarVoltage(mVoltage: Float, mUnit: String?) =
            emit(TopwayReading(voltageV = TopwayMapping.voltage(mVoltage)), "car_voltage=$mVoltage$mUnit")

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
        const val PACKAGE = "com.tw.carinfoservice"
        const val ACTION = "com.tw.carinfoservice.CarService.Bind"
        private const val TAG = "GlassCarTopway"
        private const val RETRY_MS = 3_000L
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
