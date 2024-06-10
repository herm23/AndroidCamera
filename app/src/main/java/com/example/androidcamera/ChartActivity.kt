package com.example.androidcamera

import android.content.Intent
import android.graphics.Color
import android.graphics.ImageFormat
import android.hardware.Camera
import android.icu.util.Calendar
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.launch

//Questa classe si occupa:
//1) Mostra i grafici in real time (ultimi 5 minuti) dei colori medi
//2) Legge/Scrive e cancella tutti i dati necessari per il punto (1)
class ChartActivity : ComponentActivity() {

    companion object{

        const val TAG : String = "Chart Activity"
        const val SpanTimeMillis : Int = 300000  //5 minuti sono 300000 millisecondi
    }

    private lateinit var lineChart: LineChart
    private lateinit var entries : ArrayList<Entry>


    private lateinit var surfaceView: SurfaceView
    private lateinit var surfaceHolder: SurfaceHolder
    private lateinit var homeBtn: Button

    private lateinit var redDataSet: LineDataSet
    private lateinit var greenDataSet: LineDataSet
    private lateinit var blueDataset: LineDataSet

    private lateinit var redEntries: ArrayList<Entry>
    private lateinit var greenEntries: ArrayList<Entry>
    private lateinit var blueEntries: ArrayList<Entry>
    private lateinit var colorsBuffer: ArrayList<MyColor>
    private lateinit var userDao: MyColorDao



    private var buffer: ByteArray? = null
    private var camera: Camera? = null
    private var calendar: Calendar? = null
    private var startActvityTime : Long = 0
    private var startXOffset : Long = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chart)

        //Vars
        surfaceView = findViewById(R.id.sfvCameraLive)
        homeBtn = findViewById(R.id.BtnHome)
        //Db
        val db = AppDatabase.getDatabase(this)
        userDao = db.myColorDao()
    }

    override fun onResume() {
        super.onResume()
        startActvityTime = System.currentTimeMillis()

        lifecycleScope.launch {
            // Recupero tutti i colori salvati
            colorsBuffer = ArrayList(userDao.getAllColors())

            //Log.i(TAG, "Sono stati estratti ${colorsBuffer.size} colori :)")

            //Controllo che i colori rientrino negli ultimi 5 minuti
            val colorsToRemove = colorsBuffer.filter { (it.createdColorInMillis) < (startActvityTime - SpanTimeMillis)  }
            colorsBuffer.removeAll(colorsToRemove.toSet())
            //Inizializzo i grafici in base al colore
            initChart()
            initHolder()
        }

        //Log.i(MainActivity.TAG, "Resume")
    }

    override fun onPause() {
        super.onPause()

        releaseCamera()
        writeData(false)

        //Log.i(MainActivity.TAG, "Pause")
    }

    private fun initHolder(){

        homeBtn.setOnClickListener { view ->
            //Prima rilascio la camera e poi scrivo i dati
            releaseCamera()
            writeData(true)
        }

        surfaceHolder = surfaceView.holder
        surfaceHolder.addCallback(surfaceCallback)
        if (surfaceHolder.surface.isValid) {
            initCam(surfaceHolder)
        }
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            initCam(holder)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            releaseCamera()
        }
    }

    private fun initChart(){
        // Inizializza il grafico
        lineChart = findViewById(R.id.lineChartR)
        redEntries = ArrayList<Entry>()
        greenEntries = ArrayList<Entry>()
        blueEntries = ArrayList<Entry>()

        var counter: Int = 0
        var tmp : Long = 0
        var modulateVal : Long = 0
        var firstOldModulateVal : Long = 0

       //Inizializzo i valori dei colori vecchi
        for (color in colorsBuffer) {
            tmp = (color.createdColorInMillis - startActvityTime) / SpanTimeMillis
            modulateVal = (color.createdColorInMillis - startActvityTime) - (SpanTimeMillis * tmp)

            redEntries.add(Entry(modulateVal.toFloat(), color.avgRed.toFloat()))
            greenEntries.add(Entry(modulateVal.toFloat(), color.avgGreen.toFloat()))
            blueEntries.add(Entry(modulateVal.toFloat(), color.avgBlue.toFloat()))

            counter++

            //Log.i(TAG, "Old Value ===> $modulateVal")

            if(counter == 0){
                firstOldModulateVal = modulateVal
            }

            if(colorsBuffer.size == counter){
                startXOffset = modulateVal
            }
        }

        redDataSet = LineDataSet(redEntries, "Red DataSet")
        redDataSet.color = Color.RED
        redDataSet.setCircleColor(Color.RED)
        redDataSet.setDrawCircleHole(false)
        redDataSet.setDrawCircles(false)
        redDataSet.setDrawValues(false)


        greenDataSet = LineDataSet(greenEntries, "Green Dataset")
        greenDataSet.color = Color.GREEN
        greenDataSet.setCircleColor(Color.GREEN)
        greenDataSet.setDrawCircleHole(false)
        greenDataSet.setDrawCircles(false)
        greenDataSet.setDrawValues(false)


        blueDataset = LineDataSet(blueEntries, "Blue Dataset")
        blueDataset.color = Color.BLUE
        blueDataset.setCircleColor(Color.BLUE)
        blueDataset.setDrawCircleHole(false)
        blueDataset.setDrawCircles(false)
        blueDataset.setDrawValues(false)


        val lineData = LineData(redDataSet,greenDataSet , blueDataset)

        lineChart.data = lineData

        // Personalizzo l'asse x
        val xAxis = lineChart.xAxis
        xAxis.granularity = SpanTimeMillis.toFloat() / 5
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.axisMinimum = firstOldModulateVal.toFloat()
        lineChart.description.isEnabled = false

        lineChart.invalidate() // refresh del grafico
    }

    private fun initCam(holder: SurfaceHolder?){
        camera = Camera.open()
        camera?.setDisplayOrientation(90)
        val supportedPreviewSize = camera?.parameters?.getSupportedPreviewSizes()
        val supportedPreviewFpsRange = camera?.parameters?.getSupportedPreviewFpsRange()


        val minWidth = supportedPreviewSize!!.last().width
        val minHeight = supportedPreviewSize!!.last().height

        camera?.parameters?.apply {
            setPreviewSize(minWidth, minHeight)
            setPreviewFpsRange(supportedPreviewFpsRange!!.last()[0], supportedPreviewFpsRange!!.last()[1])
        }

        if(holder != null)
            camera?.setPreviewDisplay(holder)

        val params = camera?.parameters
        val size = params?.previewSize
        val bufferSize = size?.width!! * size.height * ImageFormat.getBitsPerPixel(params.previewFormat) / 8
        buffer = ByteArray(bufferSize)
        var meanColors : List<Int>
        var avgRed : Int
        var avgGreen : Int
        var avgBlue : Int
        camera?.addCallbackBuffer(buffer)
        var unixTime : Long = 0
        camera?.setPreviewCallbackWithBuffer(Camera.PreviewCallback { data, camera ->
            meanColors =  CameraFuncs.onPreviewFrame(data, camera)

            avgRed = meanColors[0]
            avgGreen = meanColors[1]
            avgBlue = meanColors[2]

            unixTime = System.currentTimeMillis()

            unixTime = unixTime - startActvityTime + startXOffset

            //Log.i(TAG, "New Value ===> $unixTime")

            updateChart(unixTime, avgRed, avgGreen, avgBlue)
            camera.addCallbackBuffer(buffer)
        })
        camera?.startPreview()
    }

    // Funzione per aggiornare il grafico con nuovi dati
    fun updateChart(xCoord: Long, avgRed: Int, avgGreen: Int, avgBlue: Int) {
        val data = lineChart.data

        val currentTime = System.currentTimeMillis() - startActvityTime
        val minTime = currentTime - SpanTimeMillis

        // Rimuove le vecchie entry in base allo span di tempo
        val redEntriesToRemove = redEntries.filter { it.x < minTime }
        redEntries.removeAll(redEntriesToRemove.toSet())

        val greenEntriesToRemove = greenEntries.filter { it.x < minTime }
        greenEntries.removeAll(greenEntriesToRemove.toSet())

        val blueEntriesToRemove = blueEntries.filter { it.x < minTime }
        blueEntries.removeAll(blueEntriesToRemove.toSet())

        // Aggiungi le nuove entry
        redEntries.add(Entry(xCoord.toFloat(), avgRed.toFloat()))
        redDataSet.addEntry(Entry(xCoord.toFloat(), avgRed.toFloat()))

        greenEntries.add(Entry(xCoord.toFloat(), avgGreen.toFloat()))
        greenDataSet.addEntry(Entry(xCoord.toFloat(), avgGreen.toFloat()))

        blueEntries.add(Entry(xCoord.toFloat(), avgBlue.toFloat()))
        blueDataset.addEntry(Entry(xCoord.toFloat(), avgBlue.toFloat()))

        colorsBuffer.add(MyColor(createdColorInMillis = (startActvityTime + xCoord), avgRed = avgRed, avgGreen = avgGreen, avgBlue = avgBlue))

        // Imposto i limiti dell'asse x
        val xAxis = lineChart.xAxis
        xAxis.axisMinimum = minTime.toFloat()
        xAxis.axisMaximum = currentTime.toFloat()

       //Refresh
        data.notifyDataChanged()
        lineChart.notifyDataSetChanged()
        lineChart.invalidate()
    }

    private fun releaseCamera() {
        camera?.apply {
            stopPreview()
            setPreviewCallbackWithBuffer(null)
            release()
        }
        camera = null
    }

    private fun writeData(changeActivity: Boolean){
        lifecycleScope.launch {
            userDao.deleteAll() //tolto tutti i dati precedenti
            //Scrivo in base al buffer corrente
            userDao.insertAll(colorsBuffer)

            if(changeActivity)
            {
                val intent = Intent(this@ChartActivity, MainActivity::class.java)
                startActivity(intent)
            }
        }
    }
}