package com.example.androidcamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat

class MainActivity : ComponentActivity() {
    companion object{
        const val REQUEST_CAMERA_PERMISSION : Int = 1
        const val TAG : String = "Main Activity"
    }

    private lateinit var txtPermissions: TextView
    private lateinit var chartBtn: Button
    private lateinit var cameraTxv: TextureView
    private lateinit var cameraCaptureSession: CameraCaptureSession

    private lateinit var cameraManager: CameraManager
    private lateinit var cameraDevice: CameraDevice
    private lateinit var handlerThread : HandlerThread
    private lateinit var handler: Handler
    private lateinit var capReq: CaptureRequest.Builder



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Global vars init
        txtPermissions = findViewById(R.id.txtPermissions)
        chartBtn = findViewById(R.id.BtnGrafici)
        cameraTxv = findViewById(R.id.txvCameraLive)


        //Permission managing
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED)
        {
            txtPermissions.setText(R.string.permissios_denied)
            requestCameraPermission()
        }

        //Btn listener

        chartBtn.setOnClickListener { view ->
            val intent = Intent(view.context, ChartActivity::class.java)
            startActivity(intent)
        }
    }

    //Manage with non deprecated version


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
        }
        else
        {

            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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


        cameraManager.openCamera(cameraManager.cameraIdList[0], object: CameraDevice.StateCallback(){
            @SuppressLint("deprecation")
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                var surface = android.view.Surface(cameraTxv.surfaceTexture)

                capReq.addTarget(surface)

                cameraDevice.createCaptureSession(listOf(surface), object: CameraCaptureSession.StateCallback(){
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        cameraCaptureSession.setRepeatingRequest(capReq.build(), null, null)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {

                    }

              }, handler)
            }

            override fun onDisconnected(camera: CameraDevice) {

            }

            override fun onError(camera: CameraDevice, error: Int) {

            }


        }, handler)
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