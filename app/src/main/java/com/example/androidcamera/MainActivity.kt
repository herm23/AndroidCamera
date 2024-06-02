package com.example.androidcamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import kotlin.experimental.and

class MainActivity : ComponentActivity() {
    companion object{
        const val REQUEST_CAMERA_PERMISSION : Int = 1
        const val TAG : String = "Main Activity"
    }

    private lateinit var txtPermissions: TextView
    private lateinit var txtAverageColor: TextView
    private lateinit var chartBtn: Button
    private lateinit var cameraTxv: TextureView
    private lateinit var cameraCaptureSession: CameraCaptureSession

    private lateinit var cameraManager: CameraManager
    private lateinit var cameraDevice: CameraDevice
    private lateinit var handlerThread : HandlerThread
    private lateinit var handler: Handler
    private lateinit var capReq: CaptureRequest.Builder
    private lateinit var imageReader: ImageReader


    private val colorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.example.androidcamera.COLOR_UPDATE") {
                val color = intent.getIntExtra("color", 0)
                txtPermissions.text = "Average Color: $color"
            }
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Global vars init
        txtPermissions = findViewById(R.id.txtPermissions)
        txtAverageColor = findViewById(R.id.txtAverageColor)
        chartBtn = findViewById(R.id.BtnGrafici)
        cameraTxv = findViewById(R.id.txvCameraLive)

//        val intent = Intent(this, CameraService::class.java)
//        startService(intent)



        //Permission managing
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED)
        {
            txtPermissions.setText(R.string.permissios_denied)
            requestCameraPermission()
        }else{
            initCam()
        }

        //Btn listener

        chartBtn.setOnClickListener { view ->
            val intent = Intent(view.context, ChartActivity::class.java)
            startActivity(intent)
        }
    }

    //Manage with non deprecated version

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("com.example.androidcamera.COLOR_UPDATE")
        registerReceiver(colorReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(colorReceiver)
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray)
    {
        if (requestCode == REQUEST_CAMERA_PERMISSION)
        {
            if (grantResults.size != 1 ||
                grantResults[0] != PackageManager.PERMISSION_GRANTED)
            {
                txtPermissions.setText(R.string.permissios_denied)
                Log.i(TAG, "CAMERA permission has been DENIED.")
                // Handle lack of permission here
            }
            else
            {
                txtPermissions.setText(R.string.permissios_granted)
                Log.i(TAG, "CAMERA permission has been GRANTED.")

                initCam();
            }
        }
        else
        {

            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }


    private fun initCam(){
        //Camera Initialization
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler((handlerThread).looper)
        cameraTxv.surfaceTextureListener = object: TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                openCamera()
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {

            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {

            }


            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }
        }
    }

    private fun openCamera(){
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            return
        }

        // Dimensioni della fotocamera (es. 1920x1080)
        val width = 1920
        val height = 1080

        imageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            image?.let {
                processImage(it)
                image.close()
            }
        }, handler)



        cameraManager.openCamera(cameraManager.cameraIdList[0], object: CameraDevice.StateCallback(){
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                startPreview()
                /*
                capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                //capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                var surface = android.view.Surface(cameraTxv.surfaceTexture)

                capReq.addTarget(surface)
                capReq.addTarget(imageReader.surface)

                cameraDevice.createCaptureSession(listOf(surface), object: CameraCaptureSession.StateCallback(){
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        cameraCaptureSession.setRepeatingRequest(capReq.build(), null, null)


                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {

                    }

                }, handler)
*/

            }

            override fun onDisconnected(camera: CameraDevice) {

            }

            override fun onError(camera: CameraDevice, error: Int) {

            }


        }, handler)
    }

    private fun startPreview() {
        try {
            val surfaceTexture = cameraTxv.surfaceTexture!!
            surfaceTexture.setDefaultBufferSize(1920, 1080)
            val previewSurface = Surface(surfaceTexture)
            val imageReaderSurface = imageReader.surface

            capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            capReq.addTarget(previewSurface)
            capReq.addTarget(imageReaderSurface)

            cameraDevice.createCaptureSession(listOf(previewSurface, imageReaderSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    cameraCaptureSession = session
                    try {
                        cameraCaptureSession.setRepeatingRequest(capReq.build(), null, handler)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("Camera", "Configuration failed")
                }
            }, handler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun processImage(image: Image) {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val yData = ByteArray(ySize)
        val uData = ByteArray(uSize)
        val vData = ByteArray(vSize)

        yBuffer.get(yData)
        uBuffer.get(uData)
        vBuffer.get(vData)

        val width = image.width
        val height = image.height
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var pixelCount = 0

        // Conversione YUV a RGB
        for (i in 0 until height) {
            for (j in 0 until width) {
                val yIndex = i * width + j
                val uvIndex = (i / 2) * (width / 2) + (j / 2)

                val y = yData[yIndex].toInt() and 0xFF
                val u = uData[uvIndex].toInt() and 0xFF - 128
                val v = vData[uvIndex].toInt() and 0xFF - 128

                val r = (y + 1.370705 * v).toInt().coerceIn(0, 255)
                val g = (y - 0.337633 * u - 0.698001 * v).toInt().coerceIn(0, 255)
                val b = (y + 1.732446 * u).toInt().coerceIn(0, 255)

                sumR += r
                sumG += g
                sumB += b
                pixelCount++
            }
        }

        val avgR = (sumR / pixelCount).toInt()
        val avgG = (sumG / pixelCount).toInt()
        val avgB = (sumB / pixelCount).toInt()

        Log.d("AverageColor", "Average R: $avgR, G: $avgG, B: $avgB")
        txtAverageColor.setText("Average R: $avgR, G: $avgG, B: $avgB")

        // Ottieni il nome del colore
        val colorName = getColorNameFromRgb(avgR, avgG, avgB)
        Log.d("AverageColorName", "Average color name: $colorName")
        //txtAverageColor.setText("Average color: " + colorName)
    }

    // Funzione per ottenere il nome del colore (utilizza una libreria esterna o implementa una semplice mappa di colori)
    private fun getColorNameFromRgb(r: Int, g: Int, b: Int): String {
        val colorNames = mapOf(
            "Black" to Color.rgb(0, 0, 0),
            "White" to Color.rgb(255, 255, 255),
            "Red" to Color.rgb(255, 0, 0),
            "Green" to Color.rgb(0, 255, 0),
            "Blue" to Color.rgb(0, 0, 255),
            "Gray" to Color.rgb(128, 128, 128)
            // Aggiungi altri colori secondo necessità
        )

        var closestColorName = "Unknown"
        var minDistance = Int.MAX_VALUE

        for ((name, color) in colorNames) {
            val r2 = Color.red(color)
            val g2 = Color.green(color)
            val b2 = Color.blue(color)
            val distance = (r - r2) * (r - r2) + (g - g2) * (g - g2) + (b - b2) * (b - b2)
            if (distance < minDistance) {
                minDistance = distance
                closestColorName = name
            }
        }

        return closestColorName
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
}