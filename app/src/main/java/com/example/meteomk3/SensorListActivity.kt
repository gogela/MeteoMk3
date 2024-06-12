package com.example.meteomk3


import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.Switch
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.updateLayoutParams
import com.example.meteomk3.databinding.ActivitySensorConfigBinding


class SensorListActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySensorConfigBinding
    var sensors:AllSensors = com.example.meteomk3.AllSensors()
    lateinit var table:TableLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySensorConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        table = findViewById<TableLayout>(R.id.tableLayout1)

        val scanBtn = findViewById<Button>(R.id.scanBtn)
        sensors.readFromJSONFile(this.filesDir,"sensors.json")


        scanBtn.setOnClickListener {
            //sensors!!.readFromJSONTEST()
            BLEScanner.scanDevices(this,20000,this::callbackScan)
            sensortable(table)
        }
        sensortable(table)
    }

    fun callbackScan(scannedSensors:MutableList<Sensor>){
        //process data after scan
        // consolidate existing sensors and scannedSensors
        //eliminate duplicate MAC
        val knownMACs = mutableSetOf<String>()
        sensors.sensors.forEach {
            knownMACs.add(it.macAddr)
        }
        scannedSensors.forEach {
            if (!knownMACs.contains(it.macAddr)){
                sensors.sensors.add(it)
            }
        }
        sensortable(table)
    }

    fun sensortable(table:TableLayout){
        //clean tablelayout
        table.removeAllViewsInLayout()
        //display sensor data
        sensors!!.sensors.forEach {
            var tr = TableRow(this)

            var cb = CheckBox(this)
            cb.isChecked = !it.ignore
            tr.addView(cb)

            var tx = TextView(this)
            tx.text =
                it.macAddr + "\n" + it.name + "\n" + (it.sensorType?.getSensorName() ?: "unknown")
            tr.addView(tx)
            table.addView(tr)
            val paramsTx = tx.layoutParams
            paramsTx.height = 130 // ViewGroup.LayoutParams.WRAP_CONTENT
            tx.layoutParams = paramsTx

            val confbutton = ImageButton(this)
            //confbutton.background =ContextCompat.getDrawable(this, R.drawable.gear_svgrepo_com_20x20)
            confbutton.setImageResource(R.drawable.gear_svgrepo_com_20x20)
            confbutton.setColorFilter(Color.argb(255, 255, 255, 255))
            confbutton.setBackgroundColor(Color.DKGRAY)
            confbutton.setTag(it)
            confbutton.setOnClickListener {
                sensorConfOnClickListener(it)
            }

            tr.addView(confbutton)
//            val paramsCB = confbutton.layoutParams
//            paramsCB.height = 30 // ViewGroup.LayoutParams.WRAP_CONTENT
//            paramsCB.width = 30
//            confbutton.layoutParams = paramsCB

            val rowdivider = View(this)
            table.addView(rowdivider)
            val paramsDiv = rowdivider.layoutParams
            paramsDiv.height = 1
            rowdivider.setBackgroundColor(Color.DKGRAY)
            rowdivider.layoutParams = paramsDiv
        }
    }

    fun sensorConfOnClickListener(v: View) {
        val sensor = v.tag as Sensor
        val mainLayout = findViewById<ConstraintLayout>(R.id.main)
        var popup = ConstraintLayout(this)
        popup.setBackgroundColor(Color.parseColor("#1C1B1F"))
        popup.id = View.generateViewId()
        mainLayout.addView(popup)
        popup.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = ViewGroup.LayoutParams.MATCH_PARENT
            width = ViewGroup.LayoutParams.MATCH_PARENT

        }

//close button
        val closeButton = Button(this)
        closeButton.id = View.generateViewId()
        closeButton.text = "CLOSE"
        popup.addView(closeButton)

//save button
        val saveButton = Button(this)
        saveButton.id = View.generateViewId()
        saveButton.text = "SAVE"
        popup.addView(saveButton)

//table for settings
        val settingsTable = TableLayout(this)
        settingsTable.id = View.generateViewId()
        popup.addView(settingsTable)

//MAC text
        val macRow = TableRow(this)
        settingsTable.addView(macRow)
        val mact1 = TextView(this)
        mact1.text="MAC addr:"
        macRow.addView(mact1)
        val mact2 = TextView(this)
        mact2.text=sensor.macAddr
        macRow.addView(mact2)


// Name - edit
        val nameRow = TableRow(this)
        settingsTable.addView(nameRow)
        val namet = TextView(this)
        namet.text = "Sensor name:"
        nameRow.addView(namet)
        val nameEdit = EditText(this)
        nameEdit.text = Editable.Factory.getInstance().newEditable(sensor.name)
        nameRow.addView(nameEdit)



//Sensor type
        val typeRow = TableRow(this)
        settingsTable.addView(typeRow)
        val typet = TextView(this)
        typet.text = "Sensor type:"
        typeRow.addView(typet)

        val typeSelect = Spinner(this)
        typeSelect.id = View.generateViewId()
        val sensorTypeList = Sensor.getSupportedSensors()
        val adapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            sensorTypeList
        )
        typeSelect.setAdapter(adapter)

        //pre set the actual value of sensorTypeName in the adapter list as default, if unknown keep the 0th element
        val sensorTypeName = sensor.sensorType?.getSensorName()
        if (sensorTypeName!=null) {
            val sensorTypeIndex = sensorTypeList.indexOf(sensorTypeName)
            if (sensorTypeIndex>-1)
                typeSelect.setSelection(sensorTypeIndex)
        }
        typeRow.addView(typeSelect)

// show as indoor
        val indoorRow = TableRow(this)
        settingsTable.addView(indoorRow)
        val indoort = TextView(this)
        indoort.text = "Show as indoor:"
        indoorRow.addView(indoort)

        val indoorRB = CheckBox(this)
        indoorRB.id =  View.generateViewId()
        indoorRB.isChecked = sensor.displayAsIndoor
        indoorRow.addView(indoorRB)

// show as outdoor
        val outdoorRow = TableRow(this)
        settingsTable.addView(outdoorRow)
        val outdoort = TextView(this)
        outdoort.text = "Show as outdoor:"
        outdoorRow.addView(outdoort)

        val outdoorRB = CheckBox(this)
        outdoorRB.id =  View.generateViewId()
        outdoorRB.isChecked = sensor.displayAsOutdoor
        outdoorRow.addView(outdoorRB)

// ignore flag
        val ignoreRow = TableRow(this)
        settingsTable.addView(ignoreRow)
        val ignoret = TextView(this)
        ignoret.text = "Ignore sensor:"
        ignoreRow.addView(ignoret)

        val ignoreRB = CheckBox(this)
        ignoreRB.id =  View.generateViewId()
        ignoreRB.isChecked = sensor.ignore
        ignoreRow.addView(ignoreRB)

// Thingspeak API key
        val tspeakRow = TableRow(this)
        settingsTable.addView(tspeakRow)
        val tspeakt = TextView(this)
        tspeakt.text = "ThingSpeak API key: \n"
        tspeakRow.addView(tspeakt)
        val tspeakEdit = EditText(this)
        tspeakEdit.text = Editable.Factory.getInstance().newEditable(sensor.thingSpeakAPIparameters ?:"")
        tspeakRow.addView(tspeakEdit)

//constraints /close btn
        val constraintSet1 = ConstraintSet()
        constraintSet1.clone(popup)
        constraintSet1.connect(closeButton.id,ConstraintSet.RIGHT,ConstraintSet.PARENT_ID,ConstraintSet.RIGHT)
        constraintSet1.connect(closeButton.id,ConstraintSet.TOP,ConstraintSet.PARENT_ID,ConstraintSet.TOP)
        constraintSet1.connect(closeButton.id,ConstraintSet.BOTTOM,ConstraintSet.PARENT_ID,ConstraintSet.BOTTOM)
        constraintSet1.connect(closeButton.id,ConstraintSet.LEFT,ConstraintSet.PARENT_ID,ConstraintSet.LEFT)
        constraintSet1.connect(saveButton.id,ConstraintSet.RIGHT,closeButton.id,ConstraintSet.LEFT)
        constraintSet1.connect(saveButton.id,ConstraintSet.TOP,closeButton.id,ConstraintSet.TOP)




        constraintSet1.setHorizontalBias(closeButton.id, 0.99f)
        constraintSet1.setVerticalBias(closeButton.id, 0.99f)
        constraintSet1.applyTo(popup)

        closeButton.setOnClickListener {
            mainLayout.removeView(popup)
        }

        saveButton.setOnClickListener{
            sensor.name = nameEdit.text.toString()
            sensor.sensorType = Sensor.findSensorTypeByName(typeSelect.selectedItem.toString())
            sensor.displayAsIndoor = indoorRB.isChecked
            sensor.displayAsOutdoor = outdoorRB.isChecked
            sensor.ignore = ignoreRB.isChecked
            sensor.thingSpeakAPIparameters = tspeakEdit.text.toString()

            sensors?.writeToJson(this.filesDir,"sensors.json")
            mainLayout.removeView(popup)
        }
    }
}



