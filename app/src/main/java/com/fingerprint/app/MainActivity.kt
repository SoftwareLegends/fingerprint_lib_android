package com.fingerprint.app

import android.os.Bundle
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fingerprint.FingerprintInitializer
import com.fingerprint.app.ui.theme.FingerprintHF4000Theme
import com.fingerprint.device.FingerprintEvent
import com.fingerprint.device.FingerprintManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers


class MainActivity : ComponentActivity() {

    private lateinit var fingerprintManager: FingerprintManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initializeFingerprintManager()

        setContent {
            FingerprintHF4000Theme {
                App(fingerprintManager)
            }
        }
    }

    private fun initializeFingerprintManager() {
        fingerprintManager = FingerprintInitializer(
            context = this,
            lifecycle = lifecycle,
            scope = CoroutineScope(Dispatchers.IO)
        ).create()
    }
}

@Composable
fun App(fingerprintManager: FingerprintManager) {
    var status by remember { mutableStateOf("") }
    var scanCount by remember { mutableStateOf("5") }
    var finished by remember { mutableStateOf(true) }
    val events by fingerprintManager.eventsFlow.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(key1 = events) {
        when (events) {
            is FingerprintEvent.CapturedSuccessfully,
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
            Box(contentAlignment = Alignment.TopStart) {
                Image(bitmap = bitmap, contentDescription = null)
                if (index == fingerprintManager.bestCaptureIndex) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = Color.Yellow.copy(green = 0.75f)
                    )
                }
            }
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
            Text(text = "Best Capture")
            HorizontalDivider()
            fingerprintManager.bestCapture?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.size(172.dp)
                )
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
