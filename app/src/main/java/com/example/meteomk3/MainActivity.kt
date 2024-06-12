package com.example.meteomk3


import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle

import android.widget.ImageButton
import android.widget.TextView

import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import java.util.Timer
import kotlin.concurrent.timerTask


class MainActivity : AppCompatActivity() {
    var sensors: AllSensors = com.example.meteomk3.AllSensors()

    @OptIn(ExperimentalStdlibApi::class)
    @SuppressLint("SetTextI18n", "MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        sensors!!.readFromJSONFile(this.filesDir, "sensors.json")


        //button to switch to settings
        val confButton = findViewById<ImageButton>(R.id.settingsBtn)
        confButton.setOnClickListener {
            startActivity(
                Intent(
                    this@MainActivity,
                    SensorListActivity::class.java
                )
            )
        }
        // regularly get data from BLE devices (10 minutes)
        val ctx:MainActivity = this
        Timer().schedule(
            timerTask {
                BLEScanner.getBLEdata(ctx, 60000, sensors, ctx::callbackBLEScan)
            },
            1000, 1000*60*5
        ) //every 5 m

    }


    //handle meteo data data after BLE scan
    fun callbackBLEScan() {
        sensors.sensors.forEach { sensor ->
            if (sensor.sensorData != null && sensor.sensorType != null) {
                if (sensor.displayAsIndoor) {
                    findViewById<TextView>(R.id.tempInsideField).text = String.format(
                        "%.1f",
                        sensor.sensorData!!.lastTemperature
                    ) + "\n°C" //sensorValues?.get("temperature")?.toString() ?: "error"
                    if (sensor.sensorType!!.getSensorName() == "ATC (Xiaomi/pvvx)") //use humidity
                        findViewById<TextView>(R.id.detailsInsideField).text =
                            String.format("%.1f", sensor.sensorData!!.lastHumidity) + "\n%\n\n" +
                                    String.format("%.1f", sensor.sensorData!!.lastVoltage) +"\nV"
                    if (sensor.sensorType!!.getSensorName() == "gogo nRF") //use pressure
                        findViewById<TextView>(R.id.detailsInsideField).text =
                            String.format("%.1f", sensor.sensorData!!.lastPressure?.div(100f) ?: "--") + "\nhPa"
                }
                if (sensor.displayAsOutdoor) {
                    findViewById<TextView>(R.id.tempOutsideField).text = String.format(
                        "%.1f",
                        sensor.sensorData!!.lastTemperature
                    ) + "\n°C" //sensorValues?.get("temperature")?.toString() ?: "error"
                    if (sensor.sensorType!!.getSensorName() == "ATC (Xiaomi/pvvx)") //use humidity
                        findViewById<TextView>(R.id.detailsOutsideField).text =
                            String.format("%.1f", sensor.sensorData!!.lastHumidity) + "\n%\n\n" +
                                    String.format("%.1f", sensor.sensorData!!.lastVoltage) +"\nV"
                    if (sensor.sensorType!!.getSensorName() == "gogo nRF") //use pressure
                        findViewById<TextView>(R.id.detailsOutsideField).text =
                            String.format("%.1f", sensor.sensorData!!.lastPressure?.div(100f) ?: "--") + "\nhPa"
                }



                if (sensor.thingSpeakAPIparameters != null && sensor.thingSpeakAPIparameters!!.isNotEmpty())
                    sensor.sendToThingspeak(this)

            }

        }
    }
}



