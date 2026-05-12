package com.example.plantrecognizer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var imageView: ImageView
    private lateinit var resultTextView: TextView
    private var classifier: ImageClassifier? = null
    private var currentPhotoPath: String? = null

    // Лаунчер для галереи
    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                imageView.setImageURI(it)
                safeClassify(it)
            }
        }

    // Лаунчер для камеры (использует TakePicture)
    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                currentPhotoPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        imageView.setImageURI(Uri.fromFile(file))
                        safeClassify(Uri.fromFile(file))
                    } else {
                        resultTextView.text = "Файл фото не найден"
                        Toast.makeText(this, "Файл не создан", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                resultTextView.text = "Съёмка не удалась"
                Toast.makeText(this, "Не удалось сделать снимок", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        resultTextView = findViewById(R.id.resultTextView)
        val cameraButton: Button = findViewById(R.id.cameraButton)
        val galleryButton: Button = findViewById(R.id.galleryButton)

        // Инициализация классификатора
        try {
            classifier = ImageClassifier(this)
        } catch (e: Exception) {
            resultTextView.text = "Ошибка загрузки модели: ${e.message}"
            Log.e("PlantRecognizer", "Model init failed", e)
            Toast.makeText(this, "Модель не загружена", Toast.LENGTH_LONG).show()
        }

        cameraButton.setOnClickListener {
            if (checkCameraPermission()) {
                openCamera()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        galleryButton.setOnClickListener {
            galleryLauncher.launch("image/*")
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                openCamera()
            } else {
                resultTextView.text = "Нет разрешения на камеру"
                Toast.makeText(this, "Разрешите доступ к камере в настройках", Toast.LENGTH_LONG).show()
            }
        }

    private fun openCamera() {
        val photoFile: File = try {
            createImageFile()
        } catch (ex: IOException) {
            resultTextView.text = "Ошибка создания файла: ${ex.message}"
            Log.e("PlantRecognizer", "File creation error", ex)
            return
        }

        val photoURI: Uri = FileProvider.getUriForFile(
            this,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            photoFile
        )
        cameraLauncher.launch(photoURI)
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Используем внутренний кэш приложения – не требует разрешений
        val storageDir = cacheDir
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg",         /* suffix */
            storageDir      /* directory */
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun safeClassify(uri: Uri) {
        classifier?.let { clf ->
            try {
                val bitmap = loadBitmapFromUri(uri)
                val results = clf.classify(bitmap)
                displayResults(results)
            } catch (e: Exception) {
                resultTextView.text = "Ошибка классификации: ${e.message}"
                Log.e("PlantRecognizer", "Classify error", e)
            }
        } ?: run {
            resultTextView.text = "Классификатор не инициализирован"
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= 29) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }.copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun displayResults(results: List<Pair<String, Float>>) {
        val sb = StringBuilder("Результаты:\n")
        results.forEach { (label, prob) ->
            sb.append("${label}: ${"%.2f".format(prob * 100)}%\n")
        }
        resultTextView.text = sb.toString()
    }

    override fun onDestroy() {
        classifier?.close()
        super.onDestroy()
    }
}