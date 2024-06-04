package com.example.androidcamera

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import android.view.TextureView
import android.widget.TextView

class CameraService : Service() {

    private lateinit var cameraManager: CameraManager
    private lateinit var cameraDevice: CameraDevice
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var imageReader: ImageReader
    private lateinit var handler: Handler


    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        val handlerThread = HandlerThread("CameraBackground")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        openCamera()
    }

    private fun openCamera() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }

        try {
            cameraManager.openCamera(cameraManager.cameraIdList[0], object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startCameraSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                }
            }, handler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun startCameraSession() {
        imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.YUV_420_888, 2)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                processImage(image)
                image.close()
            }
        }, handler)

        val surface = imageReader.surface
        val captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.addTarget(surface)

        cameraDevice.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                cameraCaptureSession = session
                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, handler)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                stopSelf()
            }
        }, handler)
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
        //sendColorBroadcast("Average R: $avgR, G: $avgG, B: $avgB")

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

//    private fun sendColorBroadcast(color: String) {
//        val intent = Intent("com.example.androidcamera.COLOR_UPDATE")
//        intent.putExtra("color", color)
//        sendBroadcast(intent)
//    }

    override fun onDestroy() {
        super.onDestroy()
        cameraDevice.close()
        imageReader.close()
    }
}