package com.example.meteomk3

import android.bluetooth.le.ScanRecord
import android.content.Context
import android.provider.Settings.Global.getString
import android.util.Log

import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley

import com.google.gson.Gson
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

//interface for various sensors (keep methods to detect and to parse data)
// implement as companion object (singleton) to avoid unnecessary instantination
interface SensorType {
    fun parse(sr: ScanRecord?): SensorData?
    fun getSensorName(): String
}

//ATC sensor (pvvx custom https://github.com/pvvx/ATC_MiThermometer?tab=readme-ov-file#custom-format-all-data-little-endian)
class ATCsensor() : SensorType {

    @OptIn(ExperimentalStdlibApi::class)
    override fun parse(sr: ScanRecord?): SensorData? {
        val devname = sr?.deviceName
        Log.d("DEVICE", devname.toString())
        val sd = sr!!.serviceData
        sd.keys.forEach {
            val gattservice =
                it.uuid.toString().substring(4, 8) //GATT Service 0x181A Environmental Sensing
            Log.d("VAR", it.uuid.toString().substring(4, 8))
            if (gattservice.contains("181a", ignoreCase = true)) { // only env sensing data
                sd[it]?.let { sdata ->
                    Log.d("Sdata", sdata.toHexString())
                    //                val mac = it1[0-6]
                    val tempint = ByteBuffer.wrap(sdata,6,2).order(ByteOrder.LITTLE_ENDIAN).getShort()
                    //val tempint = sdata[6].toUInt() + sdata[7].toUInt() * 256u  //6,7
                    val tempfloat = tempint.toFloat() / 100f

                    val humint = ByteBuffer.wrap(sdata,8,2).order(ByteOrder.LITTLE_ENDIAN).getShort()//sdata[8].toUInt() + sdata[9].toUInt() * 256u
                    val humfloat = humint.toFloat() / 100f

                    val voltint = ByteBuffer.wrap(sdata,10,2).order(ByteOrder.LITTLE_ENDIAN).getShort()//sdata[10].toUInt() + sdata[11].toUInt() * 256u
                    val voltfloat = voltint.toFloat() / 1000f

                    val battlevelfloat = sdata[12].toFloat()

                    Log.d(
                        "RESULT",
                        "temp:$tempfloat hum:$humfloat volt:$voltfloat batt:$battlevelfloat"
                    )

                    return SensorData(
                        tempfloat,
                        humfloat,
                        null,
                        voltfloat,
                        battlevelfloat
                    )
                }
            }
        }
        return null
    }

    override fun getSensorName(): String {
        return "ATC (Xiaomi/pvvx)"
    }

}

class GogoNRFsensor() : SensorType {
    @OptIn(ExperimentalStdlibApi::class)
    override fun parse(sr: ScanRecord?): SensorData? {
        //CC:74:25:FC:58:48
        val mData:ByteArray? = sr?.manufacturerSpecificData?.get(5)

        Log.d("MfgData", mData!!.toHexString())
        val tempint = ByteBuffer.wrap(mData,1,2).order(ByteOrder.BIG_ENDIAN).getShort()
        val temp = tempint.toFloat()/100f
//        val temp = (mData[1].toUInt()*256u+mData[2].toUInt()).toFloat()/100f
        val pres = (mData[3].toUInt()*65536u+mData[4].toUInt()*256u+mData[2].toUInt()).toFloat()

        return SensorData(
            lastTemperature = temp,
            lastHumidity = 0f,
            lastPressure = pres,
            lastVoltage = 0f,
            lastBattery = 0f
        )
    }

    override fun getSensorName(): String {
        return "gogo nRF"
    }
}

data class SensorData(
    var lastTemperature: Float? = null,
    var lastHumidity: Float? = null,
    var lastPressure: Float? = null,
    var lastVoltage: Float? = null,
    var lastBattery: Float? = null
)

data class AllSensorsSerializable(
    val sensors: MutableList<SensorSerializable>
)

data class SensorSerializable(
    val macAddr: String,
    val name: String,
    val sensorTypeName: String,
    val thingSpeakAPIparameters: String, // if not null, send to Thingspeak (the json incl apikey)
    val displayAsIndoor: Boolean,
    val displayAsOutdoor: Boolean,
    val ignore: Boolean, // if set then ignore any interactions with given MAC
)

//
class AllSensors(
    var sensors: MutableList<Sensor> = mutableListOf<Sensor>()
) {
    fun readFromJSONFile(path:File, jsonfile: String) {
        val file = File(path, jsonfile)
        if (file.exists()) {
            val jsonString = file.readText()
            readFromJSONString(jsonString)
        }
    }

    fun readFromJSONTEST() {
        val jsonTESTString = """
            {"sensors":[
            {
               "macAddr": "10:10:10:10:10:10",
                "name": "Test Sensor",
                "sensorTypeName": "ATC (Xiaomi/pvvx)",
                "thingSpeakAPIparameters": "{\"apitest\":\"apitest\"}", 
                "displayAsIndoor": false ,
                "displayAsOutdoor": true ,
                "ignore": false
            },
            {
               "macAddr": "20:20:20:20:20:20",
                "name": "Test Sensor2",
                "sensorTypeName": "gogo nRF",
                "thingSpeakAPIparameters": "{\"apitest\":\"apitest\"}", 
                "displayAsIndoor": true ,
                "displayAsOutdoor": false ,
                "ignore": false
            }
            ]}
        """.trimIndent()
        readFromJSONString(jsonTESTString)
    }

    fun readFromJSONString(jsonString: String) {

        val jsondata = Gson().fromJson(jsonString, AllSensorsSerializable::class.java)
        jsondata.sensors.forEach {
            val s = Sensor(
                macAddr = it.macAddr,
                name = it.name,
                thingSpeakAPIparameters = it.thingSpeakAPIparameters,
                displayAsIndoor = it.displayAsIndoor,
                displayAsOutdoor = it.displayAsOutdoor,
                ignore = it.ignore,
                sensorType = Sensor.findSensorTypeByName(it.sensorTypeName)
            )
            this.sensors.add(s)
        }
    }

    fun writeToJson(path:File, jsonfile: String) {
        val file = File(path, jsonfile)
        val a = AllSensorsSerializable(sensors = mutableListOf<SensorSerializable>())
        this.sensors.forEach {
            val s = SensorSerializable(
                macAddr = it.macAddr,
                name = it.name,
                thingSpeakAPIparameters = it.thingSpeakAPIparameters ?: "",
                displayAsIndoor = it.displayAsIndoor,
                displayAsOutdoor = it.displayAsOutdoor,
                ignore = it.ignore,
                sensorTypeName = it.sensorType?.getSensorName() ?: ""
            )
            a.sensors.add(s)
        }
        val jsondata = Gson().toJson(a)
        file.writeText(jsondata)
    }

    override fun toString(): String {
        val l = this.sensors.size
        return "Sensors, length $l"
    }
}


// a class to keep data about BT sensor
class Sensor(
    val macAddr: String,
    var name: String = "",
    var sensorType: SensorType? = null,
    var thingSpeakAPIparameters: String? = null, // if not null, send to Thingspeak, api key value here
    var displayAsIndoor: Boolean = false,
    var displayAsOutdoor: Boolean = false,
    var ignore: Boolean = false, // if set then ignore any interactions with given MAC
    var sensorData: SensorData? = null

) {
    companion object { // make the stuff static if possible
        private val supportedSensors = listOf(ATCsensor(), GogoNRFsensor())
        fun getSupportedSensors(): ArrayList<String> {
            var ret = ArrayList<String>()
            supportedSensors.forEach {
                ret.add(it.getSensorName())
            }
            return ret
        }

        fun findSensorTypeByName(name: String): SensorType? {
            supportedSensors.forEach {
                if (it.getSensorName() == name)
                    return it
            }
            return null
        }
    }

    fun setValues(sr: ScanRecord?) { // parse scanrecord according to sensor type and fill sensorData properties
        if (sr == null) {
            Log.e("METEO BT ERR", "ScanRecord == null in Sensor.setValues")
            return
        }
        if (this.sensorType == null) {
            Log.e("METEO BT ERR", "Attempt to parse data when SensorType not set")
            return
        }
        this.sensorData = sensorType!!.parse(sr)
    }

    fun sendToThingspeak(context : Context) {

            // http client for post requests to thingspeak using Volley
            val postUrl: String =context.getString(R.string.url_thingspeak) //in ./res/values/meteo_values.xml
            val requestQueue: RequestQueue = Volley.newRequestQueue(context)
            val postData = JSONObject()
            try {
                postData.put("api_key", this.thingSpeakAPIparameters)
                postData.put("field1", this.sensorData?.lastTemperature?.toString() ?: "0")
                postData.put("field2",this.sensorData?.lastPressure?.toString() ?: "0")
                postData.put("field3", this.sensorData?.lastHumidity?.toString() ?: "0")
                postData.put("field4", this.sensorData?.lastBattery?.toString() ?: "0")
                postData.put("field5", this.sensorData?.lastVoltage?.toString() ?: "0")
            } catch (e: JSONException) {
                Log.d("Thingspeak","Error building thingspeak json")
                e.printStackTrace()
            }

            val jsonObjectRequest: JsonObjectRequest = JsonObjectRequest(
                Request.Method.POST,
                postUrl,
                postData,
                { response ->
                    Log.d("Thingspeak", response.toString())
                },
                { err ->
                    Log.d("Thingspeak", err.toString())

                })

            requestQueue.add(jsonObjectRequest)

        }
}

