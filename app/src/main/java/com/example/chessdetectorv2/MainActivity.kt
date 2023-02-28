package com.example.cameraxapp

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.cameraxapp.databinding.ActivityMainBinding
import com.example.chessdetectorv2.*
import com.example.chessdetectorv2.Utils.rotateBitmap
import com.google.android.gms.tflite.client.TfLiteInitializationOptions
import org.pytorch.*
import com.example.chessdetectorv2.PrePostProcessor
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.gms.vision.TfLiteVision
import org.tensorflow.lite.task.gms.vision.detector.ObjectDetector
import java.io.*
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


lateinit var module: PyObject
var path: File? = null
lateinit var assetManager: AssetManager
lateinit var modulePytorch: Module
lateinit var modulePytorchStrong: Module
lateinit var objectDetectorMobileNet: ObjectDetector
lateinit var objectDetectorEfficientNet: ObjectDetector


class MainActivity : AppCompatActivity() {
    lateinit var viewBinding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installSplashScreen()
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        cameraExecutor = Executors.newSingleThreadExecutor()
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }


        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        val py = Python.getInstance()

        module = py.getModule("chessboard_detection")
        assetManager = assets

        modulePytorch =
            LiteModuleLoader.load(assetFilePath(this, "bestYolov5n_300epoch.torchscript.ptl"))
        modulePytorchStrong =
            LiteModuleLoader.load(assetFilePath(this, "best_YoloV5s_300epoch.torchscript.ptl"))

        //val module = Module.load(assetFilePath(this, "bestv5nano_300epoch.torchscript_half.ptl"), Device.VULKAN)

        val br = BufferedReader(InputStreamReader(assets.open("classes.txt")))
        var line: String?
        val classes: MutableList<String> = ArrayList()
        while (br.readLine().also { line = it } != null) {
            line?.let { classes.add(it) }
        }
        br.close()
        PrePostProcessor.mClasses = Array(classes.size) { i -> classes[i] }
        MyApplication.chessboard = Chessboard.getInstance(PrePostProcessor.mClasses, assetManager)


        val options = TfLiteInitializationOptions.builder()
            .setEnableGpuDelegateSupport(true)
            .build()

        val efficientDetModelName = "efficientdet_lite2_300epoch.tflite"
        val mobileNetModelName = "mobilenetFinal_quant_fiq_with_metadata.tflite"

        TfLiteVision.initialize(this, options).addOnSuccessListener {
            Log.d(TAG, "TF inizializzato con successo con GPU")
            val baseOptionsEfficientNet = BaseOptions.builder().useNnapi().build()
            val optionsBuilderEfficientNet = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptionsEfficientNet).setScoreThreshold(0.5F).setMaxResults(50)

            objectDetectorEfficientNet = ObjectDetector.createFromFileAndOptions(
                this,
                efficientDetModelName,
                optionsBuilderEfficientNet.build()
            )

            val baseOptionsMobileNet = BaseOptions.builder().setNumThreads(4).build()
            val optionsBuilderMobileNet =
                ObjectDetector.ObjectDetectorOptions.builder().setBaseOptions(baseOptionsMobileNet)
                    .setScoreThreshold(0.5F).setMaxResults(50)

            objectDetectorMobileNet = ObjectDetector.createFromFileAndOptions(
                this,
                mobileNetModelName,
                optionsBuilderMobileNet.build()
            )


        }

        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }
        viewBinding.evalBoardButton.setOnClickListener { evalBoard() }
        viewBinding.viewFinder.scaleType = PreviewView.ScaleType.FILL_CENTER


        viewBinding.buttonSwitchSide.setOnClickListener { switchBlackWhitePosition() }
        setContentView(viewBinding.root)


    }

    private fun switchBlackWhitePosition() {
        MyApplication.chessboard.add90DegreeRotation()
        when(MyApplication.chessboard.rotation){
            0->{
                viewBinding.Player0.text = "White"
                viewBinding.Player180.text = "Black"
                viewBinding.Player90.text = ""
                viewBinding.Player270.text = ""
            }
            90->{
                viewBinding.Player0.text = ""
                viewBinding.Player180.text = ""
                viewBinding.Player90.text = "White"
                viewBinding.Player270.text = "Black"
            }
            180-> {
                viewBinding.Player0.text = "Black"
                viewBinding.Player180.text = "White"
                viewBinding.Player90.text = ""
                viewBinding.Player270.text = ""
            }
            270->{
                viewBinding.Player0.text = ""
                viewBinding.Player180.text = ""
                viewBinding.Player90.text = "Black"
                viewBinding.Player270.text = "White"
            }
        }
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                @OptIn(ExperimentalGetImage::class)
                override fun onCaptureSuccess(image: ImageProxy) {
                    val data = ByteArray(image.planes[0].buffer.remaining())
                    image.planes[0].buffer.get(data)


                    var photo = BitmapFactory.decodeByteArray(
                        data,
                        0,
                        data.size,
                        BitmapFactory.Options().also { it.inScaled = false })
                    photo = photo.copy(photo.config, false)
                    val photoCropped = Bitmap.createBitmap(
                        photo,
                        (photo.width - photo.height) / 2,
                        0,
                        photo.height,
                        photo.height
                    )
                    val photoResized = Bitmap.createScaledBitmap(photoCropped, 640, 640, true)
                    if (!MyApplication.chessboard.pointsAreUpdate) {
                        val size = photoResized.rowBytes * photoResized.height
                        val byteBuffer = ByteBuffer.wrap(ByteArray(size))
                        photoResized.copyPixelsToBuffer(byteBuffer)
                        Utils.callPythonModule(byteBuffer.array(), 640,640,viewBinding.debugSwitch.isChecked, photoResized)

                    } else {
                        MyApplication.chessboard.leftPointOfPieces = MyApplication.chessboard.leftPointNew
                        MyApplication.chessboard.rightPointOfPieces = MyApplication.chessboard.rightPointNew
                        MyApplication.chessboard.upPointOfPieces = MyApplication.chessboard.upPointNew
                        MyApplication.chessboard.downPointOfPieces = MyApplication.chessboard.downPointNew
                    }
                    val rotatedBitmap = photoResized.rotateBitmap(90f)

                    val start = System.nanoTime()
                    Utils.inferenceYoloAndDrawPieceOnRes(rotatedBitmap, strong=true)
                    Log.d(TAG, "Inference done in: ${(System.nanoTime() - start) / 1_000_000_000.0}s")

                    if (MyApplication.chessboard.pointsAreUpdate) {
                        val bitmapChessboard = MyApplication.chessboard.returnChessBoardWithPieces()
                        viewBinding.imageView4.setImageBitmap(bitmapChessboard)
                        MyApplication.chessboard.currentPositionInFen()
                        if (viewBinding.debugSwitch.isChecked) {
                            try {
                                FileOutputStream("/data/data/com.example.cameraxapp/files/outputDetection.png")
                                    .use { out -> bitmapChessboard.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                                FileOutputStream("/data/data/com.example.cameraxapp/files/inference.png")
                                    .use { out -> val inference = Utils.drawRectOnPieces(MyApplication.chessboard.pieces, rotatedBitmap)
                                    inference.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                        image.close()
                    } else {
                        Toast.makeText(baseContext, "Non è stato possible riconoscere la scacchiera, riprova", Toast.LENGTH_SHORT).show()
                        image.close()
                    }
                }
            }
        )
    }



    private fun evalBoard() {
        if (MyApplication.chessboard.pieces.isEmpty()) {
            Toast.makeText(this, "Non è stata fatto nessun riconoscimento.", Toast.LENGTH_SHORT).show()
            return
        }
        val myIntent = Intent(applicationContext, EvaluationActivity::class.java)
        this@MainActivity.startActivity(myIntent)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            imageCapture =
                ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(ImageCapture.FLASH_MODE_AUTO).setJpegQuality(100)
                    .setTargetResolution(Size(4000, 3000))
                    .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(
                        ContextCompat.getMainExecutor(this), ChessBoardAnalyzer(viewBinding)
                    )
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }


    private class ChessBoardAnalyzer(val viewBinding2: ActivityMainBinding) :
        ImageAnalysis.Analyzer {
        val viewBinding: ActivityMainBinding = viewBinding2
        var first = false
        var start = 0L
        var format = 0
        var width = 1
        var height = 1
        var res: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var data = ByteArray(0)
        var counter = 0
        var counterPoints = 0
        var counterInferenceYolo = 0
        var counterInferenceEfficientNet = 0
        var counterInferenceMobileNet = 0

        var tempoTotInferenceYolo = 0L
        var tempoTotInferenceEfficientNet = 0L
        var tempoTotInferenceMobileNet = 0L
        var startFPS = 0L
        var fps = 0
        var startI = 0L

        override fun analyze(image: ImageProxy) {
            if (!first) {
                format = image.format
                width = image.width
                height = image.height
                first = !first
            }
            if (startFPS == 0L) {
                startFPS = System.nanoTime()
            }
            val currentTime = System.nanoTime()
            if ((currentTime - startFPS) > 1_000_000_000.0) {
                startFPS = 0L
                viewBinding.fpsTextView.text = fps.toString() + " FPS"
                fps = 0
            }
            fps += 1

            start = System.nanoTime()
            counter += 1

            res = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)   // creo bitmap vuota
            data = ByteArray(image.planes[0].buffer.remaining())
            image.planes[0].buffer.get(data)
            image.planes[0].buffer.rewind()

            res.copyPixelsFromBuffer(image.planes[0].buffer) // riempo bitmap con immagine ricevuta
            image.planes[0].buffer.rewind()

            res = Utils.rotateCutResizeBitmap(res)      // ruoto 90 gradi, taglio per farla diventare quadrata, scalo a 640x640pixel


            if (viewBinding2.switchAR1.isChecked && (counter % 2) == 0) {
                val startP = System.nanoTime()
                val result = Utils.callPythonModule(data, width, height, viewBinding2.debugSwitch.isChecked, res)
                if (result) {
                    counterPoints = 0
                } else {
                    counterPoints += 1
                    if (counterPoints % 4 == 0) {
                        MyApplication.chessboard.pointsAreUpdate = false
                    }
                }
                Log.d(TAG, "Tempo python script: ${(System.nanoTime() - startP) / 1_000_000_000.0}")
            }

            if (viewBinding2.switchAR2.isChecked) {

                startI = System.nanoTime()
                if (res.height != 640 || res.width != 640) {
                    res = Bitmap.createBitmap(res, 0, 0, res.width, res.width)
                    res = Bitmap.createScaledBitmap(res, 640, 640, true)
                }

                when (viewBinding.spinner.selectedItem.toString()) {
                    "YoloV5n" -> {
                        res = Utils.inferenceYoloAndDrawPieceOnRes(res)
                        tempoTotInferenceYolo += (System.nanoTime() - startI)
                        counterInferenceYolo += 1
                        Log.d(TAG, "Media Tempo inference Yolov5: ${(tempoTotInferenceYolo / counterInferenceYolo) / 1_000_000_000.0}s")
                        Log.d(TAG, "Num inferenze totali Yolov5: $counterInferenceYolo")
                    }
                    "EfficientDetLite" -> res = inferenceTFLiteEfficientNetAndDrawPiecesOnRes(res)
                    "MobilenetV2" -> res = inferenceTFLiteMobileNetV2AndDrawPiecesOnRes(res)
                    else -> Log.d(TAG, "Rete non riconosciuta")
                }
                Log.d(TAG, "Tempo inference: ${(System.nanoTime() - startI) / 1_000_000_000.0}s")
            }

            if (viewBinding2.switchAR1.isChecked && MyApplication.chessboard.pointsAreUpdate) {
                res = Utils.drawLinesVertical(
                    MyApplication.chessboard.upPointNew,
                    MyApplication.chessboard.downPointNew,
                    res.copy(res.config, true)
                )
                res = Utils.drawLinesHorizontal(
                    MyApplication.chessboard.leftPointNew,
                    MyApplication.chessboard.rightPointNew,
                    res
                )
            }

            if (viewBinding2.switchAR1.isChecked && viewBinding2.switchAR2.isChecked && MyApplication.chessboard.pointsAreUpdate) {
                MyApplication.chessboard.upPointOfPieces = MyApplication.chessboard.upPointNew
                MyApplication.chessboard.downPointOfPieces = MyApplication.chessboard.downPointNew
                MyApplication.chessboard.leftPointOfPieces = MyApplication.chessboard.leftPointNew
                MyApplication.chessboard.rightPointOfPieces = MyApplication.chessboard.rightPointNew

                viewBinding2.imageView4.setImageBitmap(MyApplication.chessboard.returnChessBoardWithPieces())
                MyApplication.chessboard.currentPositionInFen()
            }

            viewBinding.imageView2.setImageBitmap(res)
            val stop = System.nanoTime()
            Log.d(TAG, "Frame analizzato in: ${(stop - start) / 1_000_000_000.0}s")
            image.close()

        }

        private fun inferenceTFLiteMobileNetV2AndDrawPiecesOnRes(res: Bitmap): Bitmap {
            val tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(res)
            val results = objectDetectorMobileNet.detect(tensorImage)
            val listPieces = List(results.size) { i ->
                Piece(
                    results[i].categories[0].index,
                    results[i].categories[0].score,
                    Rect(
                        results[i].boundingBox.left.toInt(),
                        results[i].boundingBox.top.toInt(),
                        results[i].boundingBox.right.toInt(),
                        results[i].boundingBox.bottom.toInt()
                    ),
                    mutableListOf(Pair(0,0.0f),Pair(0,0.0f),Pair(0,0.0f),Pair(0,0.0f),
                                Pair(0,0.0f),Pair(0,0.0f),Pair(0,0.0f),Pair(0,0.0f),
                                Pair(0,0.0f),Pair(0,0.0f),Pair(0,0.0f),Pair(0,0.0f))
                )
            }
            MyApplication.chessboard.addPieces(listPieces, true)
            tempoTotInferenceMobileNet += (System.nanoTime() - startI)
            counterInferenceMobileNet += 1
            Log.d(
                TAG,
                "Media Tempo inference MobileNet: ${(tempoTotInferenceMobileNet / counterInferenceMobileNet) / 1_000_000_000.0}s"
            )
            Log.d(TAG, "Num inferenze totali MobileNet: $counterInferenceMobileNet")
            return MyApplication.chessboard.drawPiecesOnImage(res)
        }

        private fun inferenceTFLiteEfficientNetAndDrawPiecesOnRes(res: Bitmap): Bitmap {
            val tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(res)
            val results = objectDetectorEfficientNet.detect(tensorImage)
            val listPieces = List(results.size) { i ->
                Piece(
                    results[i].categories[0].index,
                    results[i].categories[0].score,
                    Rect(
                        results[i].boundingBox.left.toInt(),
                        results[i].boundingBox.top.toInt(),
                        results[i].boundingBox.right.toInt(),
                        results[i].boundingBox.bottom.toInt()
                    ),
                    mutableListOf(Pair(0,0.0f),Pair(0,0.0f),Pair(0,0.0f),Pair(0,0.0f),
                        Pair(0,0.0f),Pair(0,0.0f),Pair(0,0.0f),Pair(0,0.0f),
                        Pair(0,0.0f),Pair(0,0.0f),Pair(0,0.0f),Pair(0,0.0f))
                )
            }
            MyApplication.chessboard.addPieces(listPieces, true)
            tempoTotInferenceEfficientNet += (System.nanoTime() - startI)
            counterInferenceEfficientNet += 1
            Log.d(TAG, "Media Tempo inference EfficientNet: ${(tempoTotInferenceEfficientNet / counterInferenceEfficientNet) / 1_000_000_000.0}s")
            Log.d(TAG, "Num inferenze totali EfficientNet: $counterInferenceEfficientNet")
            return MyApplication.chessboard.drawPiecesOnImage(res)
        }


    }

    @Throws(IOException::class)
    fun assetFilePath(context: Context, assetName: String): String? {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }
        context.assets.open(assetName).use { `is` ->
            FileOutputStream(file).use { os ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (`is`.read(buffer).also { read = it } != -1) {
                    os.write(buffer, 0, read)
                }
                os.flush()
            }
            return file.absolutePath
        }
    }

}

class MyApplication : Application() {
    companion object {
        lateinit var chessboard: Chessboard
    }

}


