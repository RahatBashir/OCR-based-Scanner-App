package com.rahat.dealkarocr

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException

class OcrActivity : AppCompatActivity() {

    private val REQUEST_CAMERA = 101
    private val REQUEST_GALLERY = 102

    private lateinit var imageView: ImageView
    private lateinit var edtBrand: EditText
    private lateinit var edtModel: EditText
    private lateinit var edtPrice: EditText
    private lateinit var edtRam: EditText
    private lateinit var edtStorage: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ocr)

        imageView = findViewById(R.id.imageView)
        edtBrand = findViewById(R.id.edtBrand)
        edtModel = findViewById(R.id.edtModel)
        edtPrice = findViewById(R.id.edtPrice)
        edtRam = findViewById(R.id.edtRam)
        edtStorage = findViewById(R.id.edtStorage)

        findViewById<Button>(R.id.btnCapture).setOnClickListener { openCamera() }
        findViewById<Button>(R.id.btnGallery).setOnClickListener { openGallery() }

        requestPermissions()
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, REQUEST_CAMERA)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_GALLERY)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK && data != null) {
            val bitmap: Bitmap? = when (requestCode) {
                REQUEST_CAMERA -> data.extras?.get("data") as? Bitmap
                REQUEST_GALLERY -> {
                    try {
                        val uri = data.data
                        MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    } catch (e: IOException) {
                        e.printStackTrace()
                        null
                    }
                }

                else -> null
            }

            bitmap?.let {
                imageView.setImageBitmap(it)
                runTextRecognition(it)
            }
        }
    }

    private fun runTextRecognition(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val parsedInfo = parseMobileText(visionText.text)
                edtBrand.setText(parsedInfo.brand ?: "")
                edtModel.setText(parsedInfo.model ?: "")
                edtPrice.setText(parsedInfo.price ?: "")
                edtRam.setText(parsedInfo.ram ?: "")
                edtStorage.setText(parsedInfo.storage ?: "")
            }
            .addOnFailureListener {
                Toast.makeText(this, "OCR Failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun parseMobileText(text: String): MobileInfo {
        val lines = text.split("\n").map { it.trim() }

        val brand = lines.find { it.contains("Samsung|Xiaomi|Infinix|Vivo|Oppo", true) }
        val model =
            lines.find { it.contains(Regex("Galaxy|Note|Redmi|A\\d+", RegexOption.IGNORE_CASE)) }
        val price = lines.find {
            it.contains(
                Regex(
                    "Rs.?\\s*\\d+",
                    RegexOption.IGNORE_CASE
                )
            ) || it.matches(Regex(".*\\d{4,}.*"))
        }
        val ram = lines.find { it.contains(Regex("\\d+\\s*GB\\s*RAM", RegexOption.IGNORE_CASE)) }
        val storage = lines.find {
            it.contains(
                Regex(
                    "\\d+\\s*GB\\s*(ROM|Storage)?",
                    RegexOption.IGNORE_CASE
                )
            )
        }

        return MobileInfo(brand, model, price, ram, storage)
    }
}
