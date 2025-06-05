package com.rahat.dealkarocr

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var ivPlaceholder: ImageView
    private lateinit var extractedTextView: TextView
    private lateinit var playButton: Button
    private lateinit var pauseButton: Button
    private lateinit var imageCountText: TextView
    private lateinit var wordCountText: TextView
    private lateinit var textToSpeech: TextToSpeech

    private var recognizedText: String = ""
    private var ttsInitialized = false
    private var imageCount = 0

    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var pdfPickerLauncher: ActivityResultLauncher<Intent>

    private var currentImageBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Views
        ivPlaceholder = findViewById(R.id.iv_placeholder)
        extractedTextView = findViewById(R.id.placeholder_text_section)
        playButton = findViewById(R.id.btn_play)
        pauseButton = findViewById(R.id.btn_pause)
        imageCountText = findViewById(R.id.image_count_text)
        wordCountText = findViewById(R.id.word_count_text)

        // Text-to-Speech Setup
        textToSpeech = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.US
                ttsInitialized = true
            }
        }

        // Image picker
        imagePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val imageUri: Uri? = result.data?.data
                    if (imageUri != null) {
                        currentImageBitmap = uriToBitmap(imageUri)
                        ivPlaceholder.setImageBitmap(currentImageBitmap)
                        imageCount++
                        updateImageCountDisplay()
                        recognizeTextFromImage(currentImageBitmap!!)
                    }
                }
            }

        // PDF picker
        pdfPickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val pdfUri: Uri? = result.data?.data
                    if (pdfUri != null) {
                        extractTextFromPdf(pdfUri)
                    }
                }
            }

        // UI Clicks
        ivPlaceholder.setOnClickListener { showImagePickerDialog() }

        playButton.setOnClickListener {
            if (ttsInitialized && recognizedText.isNotEmpty()) {
                textToSpeech.speak(recognizedText, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }

        pauseButton.setOnClickListener {
            if (ttsInitialized && textToSpeech.isSpeaking) {
                textToSpeech.stop()
            }
        }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Camera", "Gallery", "PDF")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Choose Input Source")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> openCamera()
                1 -> openGallery()
                2 -> openPdfPicker()
            }
        }
        builder.show()
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            startActivityForResult(intent, 100)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun openPdfPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/pdf"
        pdfPickerLauncher.launch(intent)
    }

    private fun uriToBitmap(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            val imageBitmap = data?.extras?.get("data") as Bitmap?
            imageBitmap?.let {
                currentImageBitmap = it
                ivPlaceholder.setImageBitmap(it)
                imageCount++
                updateImageCountDisplay()
                recognizeTextFromImage(it)
            }
        }
    }

    private fun recognizeTextFromImage(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                recognizedText = visionText.text
                extractedTextView.text = if (recognizedText.isEmpty()) {
                    "No text found."
                } else {
                    recognizedText
                }
                updateWordCountDisplay()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to recognize text", Toast.LENGTH_SHORT).show()
            }
    }

    private fun extractTextFromPdf(pdfUri: Uri) {
        try {
            val fileDescriptor: ParcelFileDescriptor =
                contentResolver.openFileDescriptor(pdfUri, "r") ?: return
            val renderer = PdfRenderer(fileDescriptor)

            val combinedText = StringBuilder()
            var lastRenderedBitmap: Bitmap? = null

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                lastRenderedBitmap = bitmap
                page.close()

                imageCount++
                val image = InputImage.fromBitmap(bitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        combinedText.append(visionText.text).append("\n")
                        recognizedText = combinedText.toString()
                        extractedTextView.text = recognizedText
                        updateWordCountDisplay()
                        updateImageCountDisplay()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to extract from page $i", Toast.LENGTH_SHORT)
                            .show()
                    }
            }

            lastRenderedBitmap?.let {
                ivPlaceholder.setImageBitmap(it)
            }

            renderer.close()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Error reading PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateImageCountDisplay() {
        imageCountText.text = "$imageCount"
    }

    private fun updateWordCountDisplay() {
        val wordCount = recognizedText.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
        wordCountText.text = "$wordCount"
    }

    override fun onDestroy() {
        textToSpeech.shutdown()
        super.onDestroy()
    }
}
