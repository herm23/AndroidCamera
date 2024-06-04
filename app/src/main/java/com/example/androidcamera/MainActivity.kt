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
import android.view.TextureView
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
    private lateinit var cameraTxv: TextureView
    private lateinit var cameraCaptureSession: CameraCaptureSession

    private var buffer: ByteArray? = null
    private var camera: Camera? = null

    private lateinit var cameraManager: CameraManager
    private lateinit var cameraDevice: CameraDevice
    private lateinit var handlerThread : HandlerThread
    private lateinit var handler: Handler
    private lateinit var capReq: CaptureRequest.Builder
    private lateinit var imageReader: ImageReader



//    private val colorReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context, intent: Intent) {
//            if (intent.action == "com.example.androidcamera.COLOR_UPDATE") {
//                val color = intent.getIntExtra("color", 0)
//                txtPermissions.text = "Average Color: $color"
//            }
//        }
//    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Global vars init
        txtPermissions = findViewById(R.id.txtPermissions)
        txtAverageColor = findViewById(R.id.txtAverageColor)
        chartBtn = findViewById(R.id.BtnGrafici)

        //Permission managing
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED)
        {
            txtPermissions.setText(R.string.permissios_denied)
            requestCameraPermission()
        }else{
            initCam()
            //initCam2()
        }

        //Btn listener

        chartBtn.setOnClickListener { view ->
            val intent = Intent(view.context, ChartActivity::class.java)
            startActivity(intent)
        }


//        val intent = Intent(this, CameraService::class.java)
//        startService(intent)
    }

    //Manage with non deprecated version

    override fun onResume() {
        super.onResume()


    }

    override fun onPause() {
        super.onPause()

        //camera?.release()
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

                //initCam2();
                initCam();
            }
        }
        else
        {

            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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

    private fun initCam(){

        cameraTxv = findViewById(R.id.txvCameraLive)

        cameraTxv.surfaceTextureListener = object: TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                camera = Camera.open()
                camera?.setDisplayOrientation(90)
                val supportedPreviewSize = camera?.parameters?.getSupportedPreviewSizes()
                val supportedPreviewFpsRange = camera?.parameters?.getSupportedPreviewFpsRange()


                val minWidth = supportedPreviewSize!!.last().width
                val minHeight = supportedPreviewSize!!.last().height

                //val minParams : Camera.Parameters =
                camera?.parameters?.setPreviewSize(minWidth, minHeight)
                camera?.parameters?.setPreviewFpsRange(supportedPreviewFpsRange!!.last().min(),supportedPreviewFpsRange!!.last().max())

                camera?.setPreviewTexture(cameraTxv.surfaceTexture)
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
        Log.d("CameraPreview", "Average color - R: $avgRed, G: $avgGreen, B: $avgBlue")

        txtAverageColor.text = "RGB: $avgRed, $avgGreen, $avgBlue"
        txtAverageColor.setBackgroundColor(android.graphics.Color.rgb(avgRed, avgGreen, avgBlue))

        camera.addCallbackBuffer(buffer)
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
