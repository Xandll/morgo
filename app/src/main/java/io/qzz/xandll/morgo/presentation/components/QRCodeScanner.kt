package io.qzz.xandll.morgo.presentation.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CompoundBarcodeView
import com.journeyapps.barcodescanner.DecoratedBarcodeView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRCodeScannerDialog(
    onDismiss: () -> Unit,
    onQRCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }
    
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("QR-Code scannen") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                if (hasCameraPermission) {
                    ZXingCameraPreview(
                        onQRCodeScanned = { qrCode ->
                            onQRCodeScanned(qrCode)
                            onDismiss()
                        }
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Kamera-Berechtigung erforderlich")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Berechtigung erteilen")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
fun ZXingCameraPreview(
    onQRCodeScanned: (String) -> Unit
) {
    var hasScanned by remember { mutableStateOf(false) }
    
    AndroidView(
        factory = { context ->
            CompoundBarcodeView(context).apply {
                val callback = object : BarcodeCallback {
                    override fun barcodeResult(result: BarcodeResult?) {
                        result?.text?.let { qrCode ->
                            if (qrCode.startsWith("untis://setschool") && !hasScanned) {
                                hasScanned = true
                                onQRCodeScanned(qrCode)
                            }
                        }
                    }
                }
                decodeContinuous(callback)
                resume()
            }
        },
        modifier = Modifier.fillMaxSize(),
        onRelease = { view ->
            view.pause()
        }
    )
}
