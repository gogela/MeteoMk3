package com.example.meteomk3

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class BLEScanner(){
    @SuppressLint("SetTextI18n", "MissingPermission")
    companion object {

        fun scanDevices(context: Context, duration:Long, dataShowCallback :(MutableList<Sensor>)->Unit){ //performs Bluetooth LE scan
            val btDeviceMap = mutableMapOf<String, ScanRecord>()
            val leScanCallback: ScanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    super.onScanResult(callbackType, result)
                    val mac = result.device.address.toString()
                    btDeviceMap[mac]=result.scanRecord!!
                }
            }

            val bluetoothManager: BluetoothManager? =
                ContextCompat.getSystemService(context, BluetoothManager::class.java)
            val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
            val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
            val scanset: ScanSettings =  ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            Log.d("BLE Scan", "STARTED")
            bluetoothLeScanner?.startScan(null, scanset, leScanCallback)
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                bluetoothLeScanner!!.stopScan(leScanCallback)
//                Log.d("DONE", btDeviceMap.toString())
                val retSensorList = mutableListOf<Sensor>()
                btDeviceMap.keys.forEach {
                    Log.d("MAC addr", it)
                    val devName = btDeviceMap[it]?.deviceName ?:""
                    retSensorList.add(Sensor(macAddr = it, name = devName))
                }
//                Log.d("BLE Scan", "DONE")
                dataShowCallback(retSensorList)
            }, duration)
        }

// same as above but returns AllSensors object with only known sensors (saved in json)
        fun getBLEdata(context: Context, duration:Long, sensors:AllSensors, dataShowCallback :()->Unit){ //performs Bluetooth LE scan
            val btDeviceMap = mutableMapOf<String, ScanRecord>()
            val leScanCallback: ScanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    super.onScanResult(callbackType, result)
                    val mac = result.device.address.toString()
                    btDeviceMap[mac]=result.scanRecord!!
                }
            }

            val bluetoothManager: BluetoothManager? =
                ContextCompat.getSystemService(context, BluetoothManager::class.java)
            val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
            val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
//            val scanset: ScanSettings =  ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            val scanset: ScanSettings =  ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
            Log.d("BLE Scan", "STARTED")
            bluetoothLeScanner?.startScan(null, scanset, leScanCallback)
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                bluetoothLeScanner!!.stopScan(leScanCallback)
//                Log.d("DONE", btDeviceMap.toString())

                btDeviceMap.keys.forEach { macAddr ->
                    Log.d("MAC addr", macAddr)
                    sensors.sensors.forEach {
                        if (!it.ignore && it.macAddr==macAddr) {// populate sensor data
                            if (it.sensorType!=null) {
                                it.sensorData = it.sensorType!!.parse(btDeviceMap[macAddr])
                            }
                        }
                    }
                }
                dataShowCallback()
            }, duration)
        }

    }
}
