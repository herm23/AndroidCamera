package com.example.androidcamera

import android.graphics.Color
import android.graphics.ImageFormat
import android.hardware.Camera
import android.icu.util.Calendar
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

class ChartActivity : ComponentActivity() {

    companion object{

        const val TAG : String = "Chart Activity"
        const val SpanTimeMillis : Int = 300000  //5 minuti sono 300000 millisecondi
    }

    private lateinit var lineChart: LineChart
    private lateinit var entries : ArrayList<Entry>


    private lateinit var surfaceView: SurfaceView
    private lateinit var surfaceHolder: SurfaceHolder

    private lateinit var redDataSet: LineDataSet
    private lateinit var greenDataSet: LineDataSet
    private lateinit var blueDataset: LineDataSet

    private lateinit var redEntries: ArrayList<Entry>
    private lateinit var greenEntries: ArrayList<Entry>
    private lateinit var blueEntries: ArrayList<Entry>

    private  var buffer: ByteArray? = null
    private var camera: Camera? = null
    private var calendar: Calendar? = null
    private var startActvityTime : Long = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chart)

        //Vars
        surfaceView = findViewById(R.id.sfvCameraLive)
        startActvityTime = System.currentTimeMillis()

        //Db
//        val db = DatabaseProvider.getDatabase(applicationContext)
//        val userDao = db.userDao()
//
//        lifecycleScope.launch {
//            // Inserisci un nuovo utente
//            val newColor = MyColor(createdDateInMillis = System.currentTimeMillis(), avgRed = 0f, avgBlue = 0f, avgGreen = 0f)
//            userDao.insert(newColor)
//
//            // Recupera tutti gli utenti
//            val colors = userDao.getAllColors()
//            // Usa i dati recuperati
//
//        }

        //Grafici e camera
        initChart()
        initHolder()
    }

    override fun onPause() {
        super.onPause()

        releaseCamera()

        Log.i(MainActivity.TAG, "Pause")
    }

    private fun initHolder(){
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
            // Gestisci i cambiamenti della superficie se necessario
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            releaseCamera()
        }
    }

    private fun initChart(){
        // Inizializza il grafico
        lineChart = findViewById(R.id.lineChartR)

        redEntries = ArrayList<Entry>()
        redDataSet = LineDataSet(redEntries, "Red DataSet")
        redDataSet.color = Color.RED
        redDataSet.setCircleColor(Color.RED)
        redDataSet.setDrawCircleHole(false) // Imposta il pallino pieno senza il pallino bianco
        redDataSet.setDrawCircles(false) // Disegna i pallini

        greenEntries = ArrayList<Entry>()
        greenDataSet = LineDataSet(greenEntries, "Green Dataset")
        greenDataSet.color = Color.GREEN
        greenDataSet.setCircleColor(Color.GREEN)
        greenDataSet.setDrawCircleHole(false) // Imposta il pallino pieno senza il pallino bianco
        greenDataSet.setDrawCircles(false) // Disegna i pallini

        blueEntries = ArrayList<Entry>()
        blueDataset = LineDataSet(blueEntries, "Blue Dataset")
        blueDataset.color = Color.BLUE
        blueDataset.setCircleColor(Color.BLUE)
        blueDataset.setDrawCircleHole(false) // Imposta il pallino pieno senza il pallino bianco
        blueDataset.setDrawCircles(false) // Disegna i pallini

        val lineData = LineData(redDataSet,greenDataSet , blueDataset)

        lineChart.data = lineData

        // Personalizza l'asse X
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM

        // Nascondi la descrizione del grafico
        lineChart.description.isEnabled = false

        // Aggiorna il grafico
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
        camera?.setPreviewCallbackWithBuffer(Camera.PreviewCallback { data, camera ->
            meanColors =  CameraFuncs.onPreviewFrame(data, camera)

            avgRed = meanColors[0]
            avgGreen = meanColors[1]
            avgBlue = meanColors[2]

            var unixTime = System.currentTimeMillis()

            Log.i(TAG, "unixTime: $unixTime")
            Log.i(TAG, "startActvityTime: $startActvityTime")

            unixTime = unixTime.minus(startActvityTime)

            updateChart(unixTime.toFloat(), avgRed.toFloat(), avgGreen.toFloat(), avgBlue.toFloat())

            camera.addCallbackBuffer(buffer)
        })
        camera?.startPreview()
    }

    // Funzione per aggiornare il grafico con nuovi dati
    // Funzione per aggiornare il grafico con nuovi dati
    fun updateChart(xCoord: Float, avgRed: Float, avgGreen: Float, avgBlue: Float) {
        val data = lineChart.data

        val currentTime = System.currentTimeMillis() - startActvityTime
        val minTime = currentTime - SpanTimeMillis

        // Rimuove le vecchie entry in base allo span di tempo
        val redEntriesToRemove = redEntries.filter { it.x < minTime }
        redEntries.removeAll(redEntriesToRemove)

        val greenEntriesToRemove = greenEntries.filter { it.x < minTime }
        greenEntries.removeAll(greenEntriesToRemove)

        val blueEntriesToRemove = blueEntries.filter { it.x < minTime }
        blueEntries.removeAll(blueEntriesToRemove)

        // Aggiungi le nuove entry
        redEntries.add(Entry(xCoord, avgRed))
        redDataSet.addEntry(Entry(xCoord, avgRed))

        greenEntries.add(Entry(xCoord, avgGreen))
        greenDataSet.addEntry(Entry(xCoord, avgGreen))

        blueEntries.add(Entry(xCoord, avgBlue))
        blueDataset.addEntry(Entry(xCoord, avgBlue))

        // Imposta i limiti dell'asse x
        val xAxis = lineChart.xAxis
        xAxis.axisMinimum = minTime.toFloat()
        xAxis.axisMaximum = currentTime.toFloat()

        // Aggiorna il grafico
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
}