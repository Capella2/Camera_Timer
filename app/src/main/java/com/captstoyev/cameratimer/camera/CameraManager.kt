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

class CameraManager(private val context: Context) {

    private var imageCapture: ImageCapture? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val shutterSound = MediaActionSound()

    init {
        shutterSound.load(MediaActionSound.SHUTTER_CLICK)
    }

    fun getImageCapture(): ImageCapture {
        if (imageCapture == null) {
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
        }
        return imageCapture!!
    }

    fun takePhoto(
        settings: UserSettings,
        onImageSaved: (Uri) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        val imageCapture = imageCapture ?: run {
            onError(ImageCaptureException(ImageCapture.ERROR_UNKNOWN, "ImageCapture not initialized", null))
            return
        }

        if (settings.folderUri.isEmpty()) {
            onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, "Folder not selected", null))
            return
        }

        val name = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(System.currentTimeMillis())
        val fileName = "IMG_$name.jpg"
        val folderUri = Uri.parse(settings.folderUri)

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
                    Log.e("CameraManager", "Capture failed: ${exception.message}", exception)
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
                onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, "Selected folder is inaccessible", null))
                return
            }

            val newFile = rootFolder.createFile("image/jpeg", fileName)
            if (newFile == null) {
                onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, "Failed to create file in folder", null))
                return
            }

            val buffer: ByteBuffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                outputStream.write(bytes)
                Log.d("CameraManager", "Successfully saved image: ${newFile.uri}")
                onImageSaved(newFile.uri)
            } ?: run {
                onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, "Failed to open output stream", null))
            }
        } catch (e: Exception) {
            Log.e("CameraManager", "Error saving image: ${e.message}", e)
            onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, "Failed to save to SAF: ${e.message}", e))
        }
    }

    fun shutdown() {
        cameraExecutor.shutdown()
        shutterSound.release()
    }
}
