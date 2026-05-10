package com.captstoyev.cameratimer.ui

import android.view.ScaleGestureDetector
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.captstoyev.cameratimer.camera.CameraManager

@Composable
fun CameraPreview(
    cameraManager: CameraManager,
    isFrontCamera: Boolean,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val preview = remember { Preview.Builder().build() }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).also { previewView ->
                preview.setSurfaceProvider(previewView.surfaceProvider)
                
                val listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val zoomState = cameraManager.getZoomState()?.value ?: return false
                        val currentZoomRatio = zoomState.zoomRatio
                        val delta = detector.scaleFactor
                        cameraManager.setZoom(currentZoomRatio * delta)
                        return true
                    }
                }
                val scaleGestureDetector = ScaleGestureDetector(ctx, listener)
                previewView.setOnTouchListener { _, event ->
                    scaleGestureDetector.onTouchEvent(event)
                    true
                }
            }
        },
        modifier = modifier.fillMaxSize(),
        update = {
            // Re-bind when camera facing changes
            cameraManager.bindCamera(lifecycleOwner, isFrontCamera, preview)
        }
    )

    // Initial binding
    LaunchedEffect(isFrontCamera) {
        cameraManager.bindCamera(lifecycleOwner, isFrontCamera, preview)
    }
}
