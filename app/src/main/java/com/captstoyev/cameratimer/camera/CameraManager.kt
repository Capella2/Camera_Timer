package com.captstoyev.cameratimer.camera

import android.content.Context
import android.media.MediaActionSound
import android.net.Uri
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.documentfile.provider.DocumentFile
import com.captstoyev.cameratimer.data.UserSettings
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

    fun takePhoto(
        settings: UserSettings,
        onImageSaved: (Uri) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        Log.d("CameraManager", "takePhoto triggered. Sound: ${settings.isSoundEnabled}, Folder: ${settings.folderUri}")

        if (settings.folderUri.isEmpty()) {
            val err = "Folder not selected in settings"
            Log.e("CameraManager", err)
            onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, err, null))
            return
        }

        val name = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(System.currentTimeMillis())
        val fileName = "IMG_$name.jpg"
        val folderUri = Uri.parse(settings.folderUri)

        imageCapture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    Log.d("CameraManager", "onCaptureSuccess: Image captured, processing...")
                    try {
                        if (settings.isSoundEnabled) {
                            shutterSound.play(MediaActionSound.SHUTTER_CLICK)
                        }
                        saveImageToSaf(image, folderUri, fileName, onImageSaved, onError)
                    } catch (e: Exception) {
                        Log.e("CameraManager", "Error in success callback: ${e.message}")
                        onError(ImageCaptureException(ImageCapture.ERROR_UNKNOWN, "Processing error", e))
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraManager", "Capture failed! Code: ${exception.imageCaptureError}, Message: ${exception.message}", exception)
                    onError(exception)
                }
            }
        )
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
                val err = "Selected folder is inaccessible or doesn't exist"
                Log.e("CameraManager", err)
                onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, err, null))
                return
            }

            val newFile = rootFolder.createFile("image/jpeg", fileName)
            if (newFile == null) {
                val err = "Could not create file '$fileName'. Check folder permissions."
                Log.e("CameraManager", err)
                onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, err, null))
                return
            }

            val buffer: ByteBuffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                outputStream.write(bytes)
                Log.d("CameraManager", "File successfully written: ${newFile.uri}")
                onImageSaved(newFile.uri)
            } ?: run {
                val err = "Failed to open output stream for ${newFile.uri}"
                Log.e("CameraManager", err)
                onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, err, null))
            }
        } catch (e: Exception) {
            Log.e("CameraManager", "Critical error saving image", e)
            onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, "Save error: ${e.message}", e))
        }
    }

    fun shutdown() {
        cameraExecutor.shutdown()
        shutterSound.release()
    }
}
