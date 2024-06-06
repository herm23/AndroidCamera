package com.example.androidcamera

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.hardware.Camera
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat

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


        chartBtn.setOnClickListener { view ->
            val intent = Intent(view.context, ChartActivity::class.java)
            startActivity(intent)
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

        Log.i(TAG, "Resume")
    }

    override fun onPause() {
        super.onPause()

        releaseCamera()

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

    private fun openCameraANDPreview(holder: SurfaceHolder){
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

        camera?.setPreviewDisplay(holder)

        val params = camera?.parameters
        val size = params?.previewSize
        val bufferSize = size?.width!! * size.height * ImageFormat.getBitsPerPixel(params.previewFormat) / 8
        buffer = ByteArray(bufferSize)

        camera?.addCallbackBuffer(buffer)
        camera?.setPreviewCallbackWithBuffer(Camera.PreviewCallback { data, camera ->
            onPreviewFrame(data, camera)
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

    private fun onPreviewFrame(data: ByteArray, camera: Camera) {
        val params = camera.parameters
        val size = params.previewSize
        val width = size.width
        val height = size.height

        // Buffer per immagazzinare i valori RGB
        val rgb = IntArray(width * height)
        decodeYUV420SP(rgb, data, width, height)

        // Calcolo del colore medio
        var redSum = 0
        var greenSum = 0
        var blueSum = 0
        for (color in rgb) {
            redSum += (color shr 16) and 0xFF
            greenSum += (color shr 8) and 0xFF
            blueSum += color and 0xFF
        }
        val pixelCount = width * height
        val avgRed = redSum / pixelCount
        val avgGreen = greenSum / pixelCount
        val avgBlue = blueSum / pixelCount

        // Log del colore medio
        // Log.d("CameraPreview", "Average color - R: $avgRed, G: $avgGreen, B: $avgBlue")

        txtAverageColor.text = "$avgRed, $avgGreen, $avgBlue"

        // Aggiorna il colore della vista con i valori RGB
        updateColorIndicator(avgRed, avgGreen, avgBlue)

        camera.addCallbackBuffer(buffer)
    }

    // Funzione per aggiornare il colore della vista
    fun updateColorIndicator(red: Int, green: Int, blue: Int) {
        val color = Color.rgb(red, green, blue)
        val background = colorIndicator.background as GradientDrawable
        background.setColor(color)
    }

    // Metodo per convertire NV21 a RGB
    private fun decodeYUV420SP(rgb: IntArray, yuv420sp: ByteArray, width: Int, height: Int) {
        val frameSize = width * height
        var yp = 0
        for (j in 0 until height) {
            var uvp = frameSize + (j shr 1) * width
            var u = 0
            var v = 0
            for (i in 0 until width) {
                var y = (0xff and yuv420sp[yp].toInt()) - 16
                if (y < 0) y = 0
                if ((i and 1) == 0) {
                    v = (0xff and yuv420sp[uvp++].toInt()) - 128
                    u = (0xff and yuv420sp[uvp++].toInt()) - 128
                }
                val y1192 = 1192 * y
                var r = y1192 + 1634 * v
                var g = y1192 - 833 * v - 400 * u
                var b = y1192 + 2066 * u
                if (r < 0) r = 0
                else if (r > 262143) r = 262143
                if (g < 0) g = 0
                else if (g > 262143) g = 262143
                if (b < 0) b = 0
                else if (b > 262143) b = 262143
                rgb[yp++] = 0xff000000.toInt() or ((r shl 6) and 0xff0000) or ((g shr 2) and 0xff00) or ((b shr 10) and 0xff)
            }
        }
    }
}
