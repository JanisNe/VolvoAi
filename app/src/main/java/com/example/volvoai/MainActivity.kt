package com.example.volvoai

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.volvoai.ui.theme.VolvoAITheme
import com.example.volvoai.yolo.DetBox
import com.example.volvoai.yolo.nms
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import androidx.compose.runtime.collectAsState
import com.example.volvoai.db.AppDatabase
import com.example.volvoai.db.ScanHistoryEntity

data class ModelHit(
    val modelName: String,
    val score: Float,
    val dets: List<DetBox>
)

class MainActivity : ComponentActivity() {

    private var tfliteInjector: Interpreter? = null
    private var tfliteFuelpump: Interpreter? = null
    private var tfliteGenerator: Interpreter? = null

    // viens izmērs visiem 3 modeļiem
    private val IN_W = 1024
    private val IN_H = 1024

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ielādē modeļus pirms UI
        loadAllModels()

        setContent {
            VolvoAITheme {
                var lastDetections by remember { mutableStateOf(emptyList<DetBox>()) }
                val db = remember { AppDatabase.get(this) }
                val dao = remember { db.scanHistoryDao() }
                val history by dao.observeAll().collectAsState(initial = emptyList())

                AppShell(
                    scanContent = {
                        CameraScreen(
                            onSingleShotInfer = { bmp, onStatus, onDetections ->

                                val winner = classifyWith3Models(bmp)
                                onDetections(winner.dets)

                                // Placeholder lauki (vēlāk aizvietot ar API)
                                val partName = if (winner.score > 0.45f && winner.dets.isNotEmpty())
                                    winner.modelName
                                else
                                    "Unknown"

                                val manufacturerPartId = when (partName) {
                                    "Injector" -> "VOLVO-INJ-PLACEHOLDER"
                                    "Fuelpump" -> "VOLVO-FP-PLACEHOLDER"
                                    "Generator" -> "VOLVO-GEN-PLACEHOLDER"
                                    else -> "N/A"
                                }

                                val priceEurText = when (partName) {
                                    "Injector" -> "€120–€180 (placeholder)"
                                    "Fuelpump" -> "€90–€160 (placeholder)"
                                    "Generator" -> "€140–€250 (placeholder)"
                                    else -> "N/A"
                                }

                                val buyLink = when (partName) {
                                    "Injector" -> "https://example.com/buy/injector"
                                    "Fuelpump" -> "https://example.com/buy/fuelpump"
                                    "Generator" -> "https://example.com/buy/generator"
                                    else -> "N/A"
                                }

                                // Saglabā DB
                                dao.insert(
                                    com.example.volvoai.db.ScanHistoryEntity(
                                        partName = partName,
                                        manufacturerPartId = manufacturerPartId,
                                        priceEurText = priceEurText,
                                        buyLink = buyLink,
                                        scannedAtMillis = System.currentTimeMillis()
                                    )
                                )

                                // Result dialogā parāda visu info
                                val msg = if (partName != "Unknown") {
                                    "Atpazīts: $partName\n" +
                                            "Manufacturer ID: $manufacturerPartId\n" +
                                            "Cena: $priceEurText\n" +
                                            "Kur pirkt: $buyLink"
                                } else {
                                    "Detala nav atpazita"
                                }
                                onStatus(msg)
                            },
                            lastDetections = lastDetections,
                            onUpdateDetections = { lastDetections = it }
                        )
                    }
                    ,
                    historyContent = {
                        HistoryScreen(items = history)
                    }
                )
            }
        }

    }

    private fun loadModel(assetName: String): MappedByteBuffer {
        val afd = assets.openFd(assetName)
        FileInputStream(afd.fileDescriptor).use { fis ->
            val fc = fis.channel
            return fc.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }

    private fun loadAllModels() {
        val opts = Interpreter.Options().apply { setNumThreads(4) }
        tfliteInjector = Interpreter(loadModel("injector_best_float32.tflite"), opts)
        tfliteFuelpump = Interpreter(loadModel("fuelpump_best_float32.tflite"), opts)
        tfliteGenerator = Interpreter(loadModel("generator_best_float32.tflite"), opts)


        logIO("Injector", tfliteInjector!!)
        logIO("Fuelpump", tfliteFuelpump!!)
        logIO("Generator", tfliteGenerator!!)
    }

    private fun logIO(name: String, itp: Interpreter) {
        val inShape = itp.getInputTensor(0).shape()
        val outShape = itp.getOutputTensor(0).shape()
        Log.i("TFLITE", "$name input=${inShape.contentToString()} output=${outShape.contentToString()}")
    }


    private fun makeInputBufferFor(interpreter: Interpreter, bitmap: Bitmap): Any {
        val shape = interpreter.getInputTensor(0).shape()

        return if (shape.size == 4 && shape[1] == 3) {

            toFloatCHW(bitmap, IN_W, IN_H)
        } else {

            bitmapToNHWCFloatBuffer(bitmap, IN_W, IN_H)
        }
    }

    private fun classifyWith3Models(bmp: Bitmap): ModelHit {
        val a = runOneModel(tfliteInjector!!, "Injector", bmp)
        val b = runOneModel(tfliteFuelpump!!, "Fuelpump", bmp)
        val c = runOneModel(tfliteGenerator!!, "Generator", bmp)
        return listOf(a, b, c).maxBy { it.score }
    }

    private fun runOneModel(interpreter: Interpreter, modelName: String, bmp: Bitmap): ModelHit {
        val input = makeInputBufferFor(interpreter, bmp)

        val outShape = interpreter.getOutputTensor(0).shape()
        val dets: List<DetBox>
        var best = 0f

        if (outShape.size == 3 && outShape[0] == 1 && outShape[2] >= 6) {
            val n = outShape[1]
            val k = outShape[2]

            val out = Array(1) { Array(n) { FloatArray(k) } }
            interpreter.run(input, out)

            val confTh = 0.45f
            val tmp = ArrayList<DetBox>(n)

            for (i in 0 until n) {
                val x1 = out[0][i][0]
                val y1 = out[0][i][1]
                val x2 = out[0][i][2]
                val y2 = out[0][i][3]
                val score = out[0][i][4]

                if (score < confTh) continue
                best = maxOf(best, score)
                tmp.add(DetBox(x1, y1, x2, y2, score, 0))
            }

            dets = nms(tmp, 0.45f)
        } else {
            throw IllegalStateException("Unexpected output shape for $modelName: ${outShape.contentToString()}")
        }

        Log.i("TFLITE", "$modelName best=$best dets=${dets.size}")
        return ModelHit(modelName, best, dets)
    }

    private fun toFloatCHW(bitmap: Bitmap, dstW: Int, dstH: Int): FloatArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, dstW, dstH, true)
        val pixels = IntArray(dstW * dstH)
        scaled.getPixels(pixels, 0, dstW, 0, 0, dstW, dstH)
        val plane = dstW * dstH
        val out = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val p = pixels[i]
            out[i] = ((p shr 16) and 0xFF) / 255f
            out[i + plane] = ((p shr 8) and 0xFF) / 255f
            out[i + 2 * plane] = (p and 0xFF) / 255f
        }
        return out
    }

    private fun bitmapToNHWCFloatBuffer(bitmap: Bitmap, dstW: Int, dstH: Int): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, dstW, dstH, true)
        val buf = ByteBuffer.allocateDirect(4 * 1 * dstW * dstH * 3).order(ByteOrder.nativeOrder())
        val pixels = IntArray(dstW * dstH)
        scaled.getPixels(pixels, 0, dstW, 0, 0, dstW, dstH)
        var idx = 0
        for (y in 0 until dstH) {
            for (x in 0 until dstW) {
                val p = pixels[idx++]
                buf.putFloat(((p shr 16) and 0xFF) / 255f)
                buf.putFloat(((p shr 8) and 0xFF) / 255f)
                buf.putFloat((p and 0xFF) / 255f)
            }
        }
        buf.rewind()
        return buf
    }
}

@Composable
fun CameraScreen(
    onSingleShotInfer: suspend (Bitmap, (String) -> Unit, (List<DetBox>) -> Unit) -> Unit,
    lastDetections: List<DetBox>,
    onUpdateDetections: (List<DetBox>) -> Unit
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Init") }
    var camGranted by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val scope = rememberCoroutineScope()
    var showPreview by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        camGranted = granted
        status = if (granted) "Camera granted" else "Camera denied"
    }

    LaunchedEffect(camGranted) {
        if (!camGranted) return@LaunchedEffect
        try {
            val provider = ProcessCameraProvider.getInstance(context).get()

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build().apply { setSurfaceProvider(previewView.surfaceProvider) }

            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()
            imageCapture = capture

            provider.unbindAll()
            provider.bindToLifecycle(
                context as ComponentActivity,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture
            )
            status = "Camera bound"
        } catch (e: Exception) {
            status = "Camera bind error: ${e.message}"
        }
    }

    Column(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )

        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(text = status)
            if (isProcessing) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Atpazist… Lūdzu uzgaidi")
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.weight(1f).padding(end = 6.dp)
            ) { Text("Pieprasīt kameru") }

            Button(
                onClick = {
                    val cap = imageCapture ?: run { status = "Nav camera capture"; return@Button }
                    val file = File(context.cacheDir, "snap.jpg")
                    val opts = ImageCapture.OutputFileOptions.Builder(file).build()
                    status = "Uzņemu..."
                    cap.takePicture(
                        opts,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                                if (bmp != null) {
                                    previewBitmap = bmp
                                    showPreview = true
                                    status = "Pārbaudi foto"
                                } else status = "Capture decode error"
                            }
                            override fun onError(exc: ImageCaptureException) {
                                status = "Capture error: ${exc.message}"
                            }
                        }
                    )
                },
                enabled = camGranted && !isProcessing && !showPreview,
                modifier = Modifier.weight(1f).padding(start = 6.dp)
            ) { Text("Foche/Scan") }
        }

        if (showPreview && previewBitmap != null) {
            AlertDialog(
                onDismissRequest = { showPreview = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showPreview = false
                            isProcessing = true
                            status = "Atpazist…"
                            scope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                onSingleShotInfer(
                                    previewBitmap!!,
                                    { s ->

                                        scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                            resultText = s
                                            isProcessing = false
                                            status = "Gatavs"
                                        }
                                    },
                                    { dets ->

                                        scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                            onUpdateDetections(dets)
                                        }
                                    }
                                )
                            }
                        }
                    ) { Text("✓ Apstiprināt") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showPreview = false
                        status = "Atcelts"
                    }) { Text("X Atcelt") }
                },
                title = { Text("Pārbaudīt foto") },
                text = {
                    Column(Modifier.fillMaxWidth()) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp)
                                .aspectRatio(4f / 3f),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Debug: dets=${lastDetections.size}")
                    }
                }
            )
        }

        if (resultText != null) {
            AlertDialog(
                onDismissRequest = { resultText = null },
                confirmButton = { TextButton(onClick = { resultText = null }) { Text("OK") } },
                title = { Text("Rezultāts") },
                text = { Text(resultText!!) }
            )
        }
    }
}
