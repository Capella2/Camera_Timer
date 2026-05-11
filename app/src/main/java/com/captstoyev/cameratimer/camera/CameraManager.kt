package com.captstoyev.cameratimer.camera

import android.content.Context
import android.media.MediaActionSound
import android.net.Uri
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LifecycleOwner
import com.captstoyev.cameratimer.data.UserSettings
import com.google.common.util.concurrent.ListenableFuture
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager private constructor(private val context: Context) {

    private var imageCapture: ImageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()
        
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val shutterSound = MediaActionSound()
    private var camera: Camera? = null

    companion object {
        @Volatile
        private var INSTANCE: CameraManager? = null

        fun getInstance(context: Context): CameraManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CameraManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        shutterSound.load(MediaActionSound.SHUTTER_CLICK)
    }

    fun getImageCapture(): ImageCapture = imageCapture

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        isFrontCamera: Boolean,
        preview: Preview? = null
    ): ListenableFuture<ProcessCameraProvider> {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val cameraSelector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider.unbindAll()
                
                val useCases = mutableListOf<UseCase>()
                useCases.add(imageCapture)
                preview?.let { useCases.add(it) }
                
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    *useCases.toTypedArray()
                )
                Log.d("CameraManager", "Camera bound successfully. Front: $isFrontCamera")
            } catch (e: Exception) {
                Log.e("CameraManager", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
        return cameraProviderFuture
    }

    fun setZoom(ratio: Float) {
        val currentCamera = camera ?: return
        val zoomState = currentCamera.cameraInfo.zoomState.value ?: return
        
        // Clamp the ratio within supported limits
        val clampedRatio = ratio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        currentCamera.cameraControl.setZoomRatio(clampedRatio)
        Log.d("CameraManager", "Zoom set to: $clampedRatio (requested: $ratio)")
    }

    fun getZoomState() = camera?.cameraInfo?.zoomState

    fun takePhoto(
        settings: UserSettings,
        onImageSaved: (Uri) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        if (settings.folderUri.isEmpty()) {
            onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, "Folder not selected", null))
            return
        }

        val name = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(System.currentTimeMillis())
        val fileName = "IMG_$name.jpg"
        val folderUri = Uri.parse(settings.folderUri)

        try {
            imageCapture.takePicture(
                cameraExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            if (settings.isSoundEnabled) {
                                shutterSound.play(MediaActionSound.SHUTTER_CLICK)
                            }
                            saveImageToSaf(image, folderUri, fileName, onImageSaved, onError)
                        } finally {
                            image.close()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("CameraManager", "Capture failed: ${exception.message}")
                        onError(exception)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("CameraManager", "takePicture call failed", e)
            onError(ImageCaptureException(ImageCapture.ERROR_UNKNOWN, "Call failed: ${e.message}", e))
        }
    }

    private fun saveImageToSaf(
        image: ImageProxy,
        folderUri: Uri,
        fileName: String,
        onImageSaved: (Uri) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        try {
            val rootFolder = DocumentFile.fromTreeUri(context, folderUri)
            if (rootFolder == null || !rootFolder.exists()) {
                onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, "Folder inaccessible", null))
                return
            }

            val newFile = rootFolder.createFile("image/jpeg", fileName)
            if (newFile == null) {
                onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, "File creation failed", null))
                return
            }

            val buffer: ByteBuffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                outputStream.write(bytes)
                onImageSaved(newFile.uri)
            } ?: onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, "Stream open failed", null))
        } catch (e: Exception) {
            onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, "Save error: ${e.message}", e))
        }
    }

    fun shutdown() {
        cameraExecutor.shutdown()
        shutterSound.release()
    }
}
