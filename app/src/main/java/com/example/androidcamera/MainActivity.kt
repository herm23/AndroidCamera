package com.example.androidcamera

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.Camera
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object{
        const val REQUEST_CAMERA_PERMISSION : Int = 1
        const val TAG : String = "Main Activity"
        const val srfWidth = 1020
        const val srfHeight = 574
    }

    private lateinit var txtPermissions: TextView
    private lateinit var txtAverageColor: TextView
    private lateinit var chartBtn: Button
    private lateinit var surfaceView: SurfaceView
    private lateinit var surfaceHolder: SurfaceHolder
    private lateinit var colorIndicator : View
    private lateinit var userDao: MyColorDao
    private var startActvityTime : Long = 0
    private var SkipCounter : Int = 0
    private var colorsBuffer: ArrayList<MyColor> = ArrayList()


    private var buffer: ByteArray? = null
    private var camera: Camera? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Global vars init
        txtPermissions = findViewById(R.id.txtPermissions)
        txtAverageColor = findViewById(R.id.txtAverageColor)
        chartBtn = findViewById(R.id.BtnGrafici)
        colorIndicator = findViewById(R.id.colorIndicator)
        surfaceView = findViewById(R.id.sfvCameraLive)

        //Db
        val db = AppDatabase.getDatabase(this)
        userDao = db.myColorDao()


        chartBtn.setOnClickListener { view ->
            writeData(true)
        }

        //Permission managing
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED)
        {
            txtPermissions.setText(R.string.permissios_denied)
            requestCameraPermission()
        }else{
            initHolder()
        }



        Log.i(TAG, "Create")

    }

    override fun onResume() {
        super.onResume()
        startActvityTime = System.currentTimeMillis()

        Log.i(TAG, "Resume")
    }

    override fun onPause() {
        super.onPause()

        releaseCamera()
        writeData(false)

        Log.i(TAG, "Pause")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                txtPermissions.setText(R.string.permissios_granted)
                Log.i(TAG, "CAMERA permission has been GRANTED.")

                // Ensure the Surface is valid before starting the preview
                initHolder()
            } else {
                txtPermissions.setText(R.string.permissios_denied)
                Log.i(TAG, "CAMERA permission has been DENIED.")
                // Handle lack of permission here
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun initHolder(){
        surfaceHolder = surfaceView.holder
        surfaceHolder.addCallback(surfaceCallback)
        if (surfaceHolder.surface.isValid) {
            openCameraANDPreview(surfaceHolder)
        }
    }

    private fun requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,android.Manifest.permission.CAMERA))
        {
            // Show dialog to the user explaining why the permission is required
        }

        ActivityCompat.requestPermissions(
            this, arrayOf(android.Manifest.permission.CAMERA),
            REQUEST_CAMERA_PERMISSION
        )
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            openCameraANDPreview(holder)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            // Gestisci i cambiamenti della superficie se necessario
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            releaseCamera()
        }
    }

    private fun openCameraANDPreview(holder: SurfaceHolder?){
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

            txtAverageColor.text = "$avgRed, $avgGreen, $avgBlue"


            if(SkipCounter == 10)
                SkipCounter = 0


            if(SkipCounter == 0){
                unixTime = System.currentTimeMillis()

                unixTime = unixTime - startActvityTime

                colorsBuffer.add(MyColor(createdActivityInMillis = startActvityTime, relativeToSpanInMillis =  unixTime, avgRed = avgRed, avgGreen = avgGreen, avgBlue = avgBlue))
            }
            SkipCounter++;

            // Aggiorna il colore della vista con i valori RGB
            updateColorIndicator(avgRed, avgGreen, avgBlue)
            camera.addCallbackBuffer(buffer)
        })
        camera?.startPreview()
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
        if(colorsBuffer.size > 0){
            lifecycleScope.launch {
                //userDao.deleteAll() //tolto tutti i dati precedenti
                //scrivo in base ai 3 buffer

                Log.i(TAG, "I colori che sto pe scrivere sono ${colorsBuffer.size} :)")

                userDao.insertAll(colorsBuffer)
                colorsBuffer.clear()

                if(changeActivity)
                {
                    val intent = Intent(this@MainActivity, ChartActivity::class.java)
                    startActivity(intent)
                }
            }
        }
    }

    // Funzione per aggiornare il colore della vista
    fun updateColorIndicator(red: Int, green: Int, blue: Int) {
        val color = Color.rgb(red, green, blue)
        val background = colorIndicator.background as GradientDrawable
        background.setColor(color)
    }
}
