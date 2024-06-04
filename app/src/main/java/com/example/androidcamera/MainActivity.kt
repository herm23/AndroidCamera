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


//        val intent = Intent(this, CameraService::class.java)
//        startService(intent)
    }

    //Manage with non deprecated version

    override fun onResume() {
        super.onResume()

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
    }

    override fun onPause() {
        super.onPause()

        camera?.release()
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



    fun onPreviewFrame(data: ByteArray, camera: Camera) {
        val size = camera.parameters.previewSize
        val rgb = yuvToRgb(data, size.width, size.height)
        val averageColor = calculateAverageColor(rgb)

        txtAverageColor.text = "RGB: ${averageColor.joinToString(", ")}"
        txtAverageColor.setBackgroundColor(android.graphics.Color.rgb(averageColor[0], averageColor[1], averageColor[2]))

        camera.addCallbackBuffer(buffer)
    }

    private fun yuvToRgb(yuv: ByteArray, width: Int, height: Int): IntArray {
        val frameSize = width * height
        val rgb = IntArray(frameSize)

        var y: Int
        var u: Int
        var v: Int
        var r: Int
        var g: Int
        var b: Int
        var index = 0
        var yp = 0

        while (yp < height) {
            val uvp = frameSize + (yp shr 1) * width
            var i = 0
            while (i < width) {
                y = 0xff and yuv[yp * width + i].toInt()
                u = 0xff and yuv[uvp + (i and 1)].toInt()
                v = 0xff and yuv[uvp + (i and 1) + 1].toInt()
                u -= 128
                v -= 128
                r = y + (1.370705 * v).toInt()
                g = y - (0.698001 * v + 0.337633 * u).toInt()
                b = y + (1.732446 * u).toInt()
                r = if (r < 0) 0 else if (r > 255) 255 else r
                g = if (g < 0) 0 else if (g > 255) 255 else g
                b = if (b < 0) 0 else if (b > 255) 255 else b
                rgb[index++] = 0xff000000.toInt() or (r shl 16) or (g shl 8) or b
                i++
            }
            yp++
        }

        return rgb
    }

    private fun calculateAverageColor(rgb: IntArray): IntArray {
        var rSum = 0
        var gSum = 0
        var bSum = 0

        for (color in rgb) {
            rSum += (color shr 16) and 0xff
            gSum += (color shr 8) and 0xff
            bSum += color and 0xff
        }

        val length = rgb.size
        return intArrayOf(rSum / length, gSum / length, bSum / length)
    }


    //region Codice camera 2
    /*
    private fun initCam2(){
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


        imageReader = ImageReader.newInstance(srfWidth, srfHeight, ImageFormat.YUV_420_888,1)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
//            image?.let {
//                processImage(it)
//                image.close()
//            }
            if (image != null) {
                val rgbArray = yuvToRgb(image)
                // Calcolo del colore medio
                val colorMean = calculateMeanColor(rgbArray)
                // Aggiornamento della TextView con il colore medio
                runOnUiThread {
                    txtAverageColor.setTextColor(colorMean)
                    txtAverageColor.text = "Colore Medio: #" + Integer.toHexString(colorMean)
                }
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
            surfaceTexture.setDefaultBufferSize(srfWidth, srfHeight)
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

        Log.i(TAG,"Buffer Y : $yBuffer")
        Log.i(TAG,"Buffer U : $uBuffer")
        Log.i(TAG,"Buffer V : $vBuffer")

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

    // Funzione per convertire YUV a RGB
    fun yuvToRgb(image: Image): IntArray {
        val width = image.width
        val height = image.height
        val yuvBytes = ByteArray(width * height * 3 / 2)
        var i = 0

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        // Struttura YUV: YYYYYYYY UUUU VVVV
        yBuffer.get(yuvBytes, 0, width * height)
        vBuffer.get(yuvBytes, width * height, width * height / 4)
        uBuffer.get(yuvBytes, width * height + width * height / 4, width * height / 4)

        val rgbInts = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val yIndex = y * width + x
                val uvIndex = width * height + (y / 2) * width + (x / 2) * 2

                val yValue = yuvBytes[yIndex].toInt() and 0xff
                val uValue = yuvBytes[uvIndex + 1].toInt() and 0xff
                val vValue = yuvBytes[uvIndex].toInt() and 0xff

                val r = yValue + (1.370705 * (vValue - 128)).toInt()
                val g = yValue - (0.698001 * (vValue - 128) + 0.337633 * (uValue - 128)).toInt()
                val b = yValue + (1.732446 * (uValue - 128)).toInt()

                rgbInts[yIndex] = (0xff shl 24) or
                        ((r.coerceIn(0, 255)) shl 16) or
                        ((g.coerceIn(0, 255)) shl 8) or
                        (b.coerceIn(0, 255))
            }
        }
        return rgbInts
    }

    // Funzione per calcolare il colore medio
    fun calculateMeanColor(rgbArray: IntArray): Int {
        var sumR = 0
        var sumG = 0
        var sumB = 0
        for (color in rgbArray) {
            sumR += (color shr 16) and 0xff
            sumG += (color shr 8) and 0xff
            sumB += color and 0xff
        }
        val pixelCount = rgbArray.size
        val meanR = (sumR / pixelCount).coerceIn(0, 255)
        val meanG = (sumG / pixelCount).coerceIn(0, 255)
        val meanB = (sumB / pixelCount).coerceIn(0, 255)
        return (0xff shl 24) or (meanR shl 16) or (meanG shl 8) or meanB
    }
*/
    //endregion
}
