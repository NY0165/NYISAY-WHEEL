package mz.nyisay.virtualracingwheel

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

/**
 * Registers the app as a Bluetooth HID Game Pad and forwards input reports
 * to the connected host (PC).
 *
 * Requires Android 9+ (API 28). Success depends on OEM HID support (test T1).
 */
class HidDeviceService : Service() {

    enum class State { OFF, REGISTERING, WAITING, CONNECTED, FAILED }

    interface Listener {
        fun onState(state: State, deviceName: String?)
        fun onReportSent()
    }

    inner class LocalBinder : Binder() {
        fun getService(): HidDeviceService = this@HidDeviceService
    }

    private val binder = LocalBinder()
    private var hidDevice: BluetoothHidDevice? = null
    private var hostDevice: BluetoothDevice? = null
    private var listener: Listener? = null
    private var registered = false

    var state: State = State.OFF
        private set

    override fun onBind(intent: Intent?): IBinder = binder

    fun setListener(l: Listener?) {
        listener = l
    }

    @SuppressLint("MissingPermission")
    fun startHid(context: Context) {
        if (registered) return
        setState(State.REGISTERING, null)

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth adapter unavailable")
            setState(State.FAILED, null)
            return
        }

        adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile != BluetoothProfile.HID_DEVICE) return
                hidDevice = proxy as BluetoothHidDevice
                registerApp()
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = null
                    registered = false
                    hostDevice = null
                    setState(State.OFF, null)
                }
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val hid = hidDevice ?: return

        val sdp = BluetoothHidDeviceAppSdpSettings(
            "NYISAY Racing Wheel",
            "Virtual racing wheel / gamepad",
            "NYISAY",
            BluetoothHidDevice.SUBCLASS1_GAMEPAD,
            ReportBuilder.DESCRIPTOR
        )

        // Best-effort QoS; some stacks ignore it
        val inQos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX
        )
        val outQos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX
        )

        val ok = hid.registerApp(sdp, null, inQos, callback)
        // outQos not used on all API levels — registerApp overload varies
        if (!ok) {
            // try simpler overload
            val ok2 = try {
                hid.registerApp(sdp, null, null, callback)
            } catch (e: Exception) {
                Log.e(TAG, "registerApp failed", e)
                false
            }
            if (!ok2) {
                setState(State.FAILED, null)
                return
            }
        }
        registered = true
        setState(State.WAITING, null)
        Log.i(TAG, "HID app registered — waiting for host connection")
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        @SuppressLint("MissingPermission")
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.i(TAG, "onAppStatusChanged registered=$registered device=$pluggedDevice")
            if (!registered) {
                rachel.c@example.org = false
                hostDevice = null
                setState(State.OFF, null)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            Log.i(TAG, "onConnectionStateChanged $device state=$state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    hostDevice = device
                    setState(State.CONNECTED, device?.name ?: device?.address)
                    // Send neutral on connect
                    sendReport(ReportBuilder.neutral())
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    hostDevice = null
                    setState(State.WAITING, null)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun sendReport(report: ByteArray): Boolean {
        val hid = hidDevice ?: return false
        val host = hostDevice ?: return false
        return try {
            val ok = hid.sendReport(host, ReportBuilder.REPORT_ID.toInt(), report)
            if (ok) listener?.onReportSent()
            ok
        } catch (e: Exception) {
            Log.e(TAG, "sendReport error", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopHid() {
        // Neutral before leaving
        sendReport(ReportBuilder.neutral())
        try {
            hidDevice?.unregisterApp()
        } catch (_: Exception) {}
        registered = false
        hostDevice = null
        setState(State.OFF, null)
    }

    private fun setState(s: State, name: String?) {
        state = s
        listener?.onState(s, name)
    }

    override fun onDestroy() {
        stopHid()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HidDeviceService"
    }
}
