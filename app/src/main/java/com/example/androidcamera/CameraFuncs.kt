package com.example.androidcamera

import android.hardware.Camera
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

class  CameraFuncs {

    companion object{

        fun onPreviewFrame(data: ByteArray, camera: Camera) : List<Int> {
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
            //Log.d("CameraPreview", "Average color - R: $avgRed, G: $avgGreen, B: $avgBlue")

            return listOf(avgRed, avgGreen, avgBlue)
        }

        // Metodo per convertire NV21 a RGB
        fun decodeYUV420SP(rgb: IntArray, yuv420sp: ByteArray, width: Int, height: Int) {
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
}