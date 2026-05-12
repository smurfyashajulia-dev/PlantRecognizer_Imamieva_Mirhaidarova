package com.example.plantrecognizer

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ImageClassifier(private val context: Context) {
    private var interpreter: Interpreter? = null
    private val labels: List<String> = loadLabels("inat_plant_labels.txt")
    private var isQuantized: Boolean = false
    private var inputScale: Float = 1.0f
    private var inputZeroPoint: Int = 0
    private var outputScale: Float = 1.0f
    private var outputZeroPoint: Int = 0
    private var numClasses: Int = 0

    init {
        val model = loadModelFile()
        interpreter = Interpreter(model)
        analyzeModel()
    }

    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd("mobilenet_v2_1.0_224_inat_plant_quant.tflite")
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength
        )
    }

    private fun analyzeModel() {
        interpreter?.let { interp ->
            val inputTensor = interp.getInputTensor(0)
            val outputTensor = interp.getOutputTensor(0)
            val inputType = inputTensor.dataType()
            isQuantized = (inputType == DataType.UINT8 || inputType == DataType.INT8)

            try {
                val inputParams = inputTensor.quantizationParams()
                inputScale = inputParams.scale
                inputZeroPoint = inputParams.zeroPoint
            } catch (_: Exception) {
                // Если параметры недоступны, оставляем значения по умолчанию
            }
            try {
                val outputParams = outputTensor.quantizationParams()
                outputScale = outputParams.scale
                outputZeroPoint = outputParams.zeroPoint
            } catch (_: Exception) {
                // Если параметры недоступны, оставляем значения по умолчанию
            }

            numClasses = outputTensor.shape()[1]
            Log.d("PlantClassifier", "isQuantized=$isQuantized, numClasses=$numClasses, outputScale=$outputScale, outputZeroPoint=$outputZeroPoint")
        }
    }

    private fun loadLabels(filename: String): List<String> {
        val reader = InputStreamReader(context.assets.open(filename), "UTF-8")
        return reader.readLines()
    }

    fun classify(bitmap: Bitmap): List<Pair<String, Float>> {
        interpreter?.let { interp ->
            val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
            val pixels = IntArray(224 * 224)
            resized.getPixels(pixels, 0, 224, 0, 0, 224, 224)

            if (isQuantized) {
                // Квантованный вход: uint8 [0,255]
                val inputBytes = ByteArray(224 * 224 * 3)
                for (i in pixels.indices) {
                    val pixel = pixels[i]
                    inputBytes[i * 3]     = ((pixel shr 16) and 0xFF).toByte()  // R
                    inputBytes[i * 3 + 1] = ((pixel shr 8) and 0xFF).toByte()   // G
                    inputBytes[i * 3 + 2] = (pixel and 0xFF).toByte()           // B
                }
                val inputBuffer = ByteBuffer.wrap(inputBytes)
                inputBuffer.order(ByteOrder.nativeOrder())

                val outputBytes = ByteArray(numClasses)
                val outputBuffer = ByteBuffer.wrap(outputBytes)
                outputBuffer.order(ByteOrder.nativeOrder())

                interp.run(inputBuffer, outputBuffer)

                outputBuffer.rewind()
                val outputProbabilities = FloatArray(numClasses)
                for (i in outputBytes.indices) {
                    val raw = outputBytes[i].toInt() and 0xFF // беззнаковое
                    outputProbabilities[i] = (raw - outputZeroPoint) * outputScale
                }

                return labels.take(numClasses).mapIndexed { index, label ->
                    label to outputProbabilities[index]
                }.sortedByDescending { it.second }.take(3)

            } else {
                // Float модель
                val inputBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3)
                inputBuffer.order(ByteOrder.nativeOrder())
                for (pixel in pixels) {
                    val r = ((pixel shr 16) and 0xFF) / 255.0f
                    val g = ((pixel shr 8) and 0xFF) / 255.0f
                    val b = (pixel and 0xFF) / 255.0f
                    inputBuffer.putFloat(r)
                    inputBuffer.putFloat(g)
                    inputBuffer.putFloat(b)
                }

                val outputBuffer = ByteBuffer.allocateDirect(4 * numClasses)
                outputBuffer.order(ByteOrder.nativeOrder())
                interp.run(inputBuffer, outputBuffer)

                outputBuffer.rewind()
                val outputProbabilities = FloatArray(numClasses)
                outputBuffer.asFloatBuffer().get(outputProbabilities)

                return labels.take(numClasses).mapIndexed { index, label ->
                    label to outputProbabilities[index]
                }.sortedByDescending { it.second }.take(3)
            }
        }
        return emptyList()
    }

    fun close() {
        interpreter?.close()
    }
}