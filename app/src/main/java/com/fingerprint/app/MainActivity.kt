package com.fingerprint.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fingerprint.FingerprintInitializer
import com.fingerprint.app.ui.theme.FingerprintTheme
import com.fingerprint.manager.FingerprintEvent
import com.fingerprint.manager.FingerprintManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.util.UUID


class MainActivity : ComponentActivity() {

    private lateinit var fingerprintManager: FingerprintManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initializeFingerprintManager()

        setContent {
            FingerprintTheme {
                App(fingerprintManager)
            }
        }
    }

    private fun initializeFingerprintManager() {
        val scope = CoroutineScope(Dispatchers.IO)
        fingerprintManager = FingerprintInitializer(
            context = this,
            lifecycle = lifecycle,
            scope = scope
        ).create()
    }
}

@Composable
fun App(fingerprintManager: FingerprintManager) {
    val clipboardManager = LocalClipboardManager.current
    val scope = remember { CoroutineScope(Dispatchers.IO) }
    var status by remember { mutableStateOf("") }
    var deviceInfo by remember { mutableStateOf("") }
    var scanCount by remember { mutableStateOf("5") }
    var finished by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var isBlue by remember { mutableStateOf(false) }
    var isFilter by remember { mutableStateOf(false) }
    val events by fingerprintManager.eventsFlow.collectAsStateWithLifecycle(FingerprintEvent.Idle)
    val focusManager = LocalFocusManager.current

    LaunchedEffect(key1 = events) {
        println("DEBUGGING -> Progress: ${fingerprintManager.progress} - Event: $events")
        when (events) {
            is FingerprintEvent.Connected,
            is FingerprintEvent.CapturedSuccessfully,
            is FingerprintEvent.Disconnected,
            is FingerprintEvent.ProcessCanceledTheFingerLifted -> {
                finished = true
                status = events.message
            }

            is FingerprintEvent.NewImage -> println("DEBUGGING -> NEW IMAGE")
            else -> status = events.message
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { focusManager.clearFocus() }
                }
                .padding(vertical = 48.dp)
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ScanCountInput(scanCount) { scanCount = it }
            Text(status)
            CaptureGrid(fingerprintManager)
            BestCaptureSection(fingerprintManager, scanCount)
            ScanButton(
                status = status,
                finished = finished,
                scanCount = scanCount,
                fingerprintManager = fingerprintManager,
                onFinishedChange = { finished = it })

            if (showInfo)
                AlertDialog(
                    onDismissRequest = {},
                    dismissButton = {},
                    confirmButton = {
                        TextButton(onClick = {
                            clipboardManager.setText(AnnotatedString(deviceInfo))
                            showInfo = false
                        }) {
                            Text(text = "Copy & Close")
                        }
                    },
                    title = { Text(text = "Device Info") },
                    text = { Text(text = deviceInfo) }
                )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = isFilter, onCheckedChange = {
                    isFilter = it
                    scope.launch {
                        fingerprintManager.improveTheBestCapture(
                            isApplyFilters = isFilter,
                            isBlue = isBlue
                        )
                    }
                })
                Text(text = "Apply Filter")
                VerticalDivider(
                    modifier = Modifier
                        .height(20.dp)
                        .padding(start = 14.dp)
                )
                Checkbox(
                    checked = isBlue,
                    onCheckedChange = {
                        isBlue = it
                        scope.launch {
                            fingerprintManager.improveTheBestCapture(
                                isApplyFilters = isFilter,
                                isBlue = isBlue
                            )
                        }
                    },
                    enabled = isFilter
                )
                Text(text = "Blue Pixels")
            }

            Button(onClick = {
                deviceInfo = fingerprintManager.deviceInfo.toString()
                showInfo = !showInfo
            }) {
                Text(text = "Get Info")
            }
        }
    }
}

@Composable
fun ScanCountInput(scanCount: String, onValueChange: (String) -> Unit) {
    TextField(
        value = scanCount,
        onValueChange = { onValueChange((it.toIntOrNull() ?: "").toString()) },
        label = { Text(text = "Scan Count (Max = 5)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
fun ColumnScope.CaptureGrid(fingerprintManager: FingerprintManager) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .weight(1f)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        itemsIndexed(items = fingerprintManager.captures) { index, bitmap ->
            FingerprintImage(
                bitmap = bitmap,
                index = index,
                bestCaptureIndex = fingerprintManager.bestCaptureIndex
            )
        }
    }
}

@Composable
fun FingerprintImage(bitmap: ImageBitmap, index: Int = -1, bestCaptureIndex: Int = -2) {
    val context = LocalContext.current
    Box(contentAlignment = Alignment.TopStart) {
        Image(bitmap = bitmap, contentDescription = null)
        if (index == bestCaptureIndex) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = Color.Yellow.copy(green = 0.75f)
            )
        }

        IconButton(
            modifier = Modifier.align(Alignment.BottomEnd),
            onClick = { bitmap.saveBitmapToGallery(context) }
        ) {
            Icon(
                imageVector = Icons.Rounded.Save,
                contentDescription = null,
                tint = Color.Yellow.copy(green = 0.75f)
            )
        }
    }
}

@Composable
fun BestCaptureSection(fingerprintManager: FingerprintManager, scanCount: String) {
    AnimatedVisibility(visible = fingerprintManager.captures.size == scanCount.toIntOrNull()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HorizontalDivider()
            Text(text = "Improved Best Capture")
            HorizontalDivider()
            fingerprintManager.bestCapture?.let { bitmap ->
                FingerprintImage(bitmap = bitmap)
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun ScanButton(
    status: String,
    finished: Boolean,
    scanCount: String,
    fingerprintManager: FingerprintManager,
    onFinishedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Button(
        enabled = finished,
        modifier = Modifier.fillMaxWidth(fraction = .75f),
        onClick = {
            if (fingerprintManager.scan(scanCount.toIntOrNull() ?: 1))
                onFinishedChange(false)
            else
                Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
        }
    ) { Text(text = "Scan") }
}

fun ImageBitmap.saveBitmapToGallery(context: Context, title: String? = null) = runCatching {
    val filename = "${title ?: UUID.randomUUID().leastSignificantBits.toString()}.png"
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
    }

    val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    val fos: OutputStream? = imageUri?.let { resolver.openOutputStream(it) }

    fos?.use {
        asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        Toast.makeText(context, "Image saved to gallery", Toast.LENGTH_SHORT).show()
    } ?: run {
        Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
    }
}
