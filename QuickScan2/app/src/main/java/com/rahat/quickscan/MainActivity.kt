package com.rahat.quickscan

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var ivPlaceholder: ImageView
    private lateinit var extractedTextView: TextView
    private lateinit var playButton: Button
    private lateinit var pauseButton: Button
    private lateinit var imageCountText: TextView
    private lateinit var wordCountText: TextView
    private lateinit var pdfRecyclerView: RecyclerView
    private lateinit var clearAllButton: ImageView
    private lateinit var imagePreviewRecyclerView: RecyclerView
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var tvNoImagePreview: TextView
    private lateinit var tvNoPdfPreview: TextView

    private var recognizedText = ""
    private var ttsInitialized = false
    private var imageCount = 0
    private var currentImageBitmap: Bitmap? = null
    private var uploadedBitmaps = mutableListOf<Bitmap>()

    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var pdfPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var openCameraLauncher: ActivityResultLauncher<Intent>

    private val CAMERA_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ivPlaceholder = findViewById(R.id.iv_placeholder)
        extractedTextView = findViewById(R.id.placeholder_text_section)
        playButton = findViewById(R.id.btn_play)
        pauseButton = findViewById(R.id.btn_pause)
        imageCountText = findViewById(R.id.image_count_text)
        wordCountText = findViewById(R.id.word_count_text)
        pdfRecyclerView = findViewById(R.id.pdfRecyclerView)
        clearAllButton = findViewById(R.id.btn_clear_all)
        imagePreviewRecyclerView = findViewById(R.id.imagePreviewRecyclerView)
        tvNoImagePreview = findViewById(R.id.tv_no_image_preview)
        tvNoPdfPreview = findViewById(R.id.tv_no_pdf_preview)

        imagePreviewRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        textToSpeech = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.US
                ttsInitialized = true
            }
        }

        findViewById<ImageView>(R.id.btn_convert_to_pdf).setOnClickListener {
            convertBitmapsToPdf(uploadedBitmaps)
        }

        imagePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val imageUri: Uri? = result.data?.data
                    imageUri?.let {
                        currentImageBitmap = uriToBitmap(it)
                        ivPlaceholder.setImageBitmap(currentImageBitmap)
                        currentImageBitmap?.let { bmp ->
                            uploadedBitmaps.add(bmp)
                            imageCount++
                            updateImageCountDisplay()
                            updateImagePreviewRecycler()
                            recognizeTextFromImage(bmp)
                        }
                    }
                }
            }

        openCameraLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val imageBitmap = result.data?.extras?.get("data") as? Bitmap
                    imageBitmap?.let {
                        currentImageBitmap = it
                        ivPlaceholder.setImageBitmap(it)
                        uploadedBitmaps.add(it)
                        imageCount++
                        updateImageCountDisplay()
                        updateImagePreviewRecycler()
                        recognizeTextFromImage(it)
                    }
                }
            }

        pdfPickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val pdfUri = result.data?.data
                    pdfUri?.let {
                        extractTextFromPdf(it)
                    }
                }
            }

        clearAllButton.setOnClickListener {
            uploadedBitmaps.clear()
            currentImageBitmap = null
            ivPlaceholder.setImageResource(R.drawable.upload)
            extractedTextView.text = ""
            imageCount = 0
            imageCountText.text = "0"
            wordCountText.text = "0"
            pdfRecyclerView.adapter = null
            updateImagePreviewRecycler()

            // Show placeholders
            tvNoImagePreview.visibility = View.VISIBLE
            tvNoPdfPreview.visibility = View.VISIBLE
            imagePreviewRecyclerView.visibility = View.GONE
            pdfRecyclerView.visibility = View.GONE

            playButton.isEnabled = false
            pauseButton.isEnabled = false
        }

        extractedTextView.setOnLongClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Extracted Text", extractedTextView.text.toString())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
            true
        }

        ivPlaceholder.setOnClickListener { showImagePickerBottomSheet() }

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

        // Initial view state
        updateImagePreviewRecycler()
        tvNoPdfPreview.visibility = View.VISIBLE
        pdfRecyclerView.visibility = View.GONE
    }

    private fun updateImagePreviewRecycler() {
        if (uploadedBitmaps.isEmpty()) {
            tvNoImagePreview.visibility = View.VISIBLE
            imagePreviewRecyclerView.visibility = View.GONE
        } else {
            tvNoImagePreview.visibility = View.GONE
            imagePreviewRecyclerView.visibility = View.VISIBLE
        }

        val adapter = ImagePreviewAdapter(uploadedBitmaps) { clickedBitmap ->
            ivPlaceholder.setImageBitmap(clickedBitmap)
            recognizeTextFromImage(clickedBitmap)
        }
        imagePreviewRecyclerView.adapter = adapter
    }

    private fun extractTextFromPdf(pdfUri: Uri) {
        val bitmaps = renderPdfPages(pdfUri, this)
        if (bitmaps.isEmpty()) {
            tvNoPdfPreview.visibility = View.VISIBLE
            pdfRecyclerView.visibility = View.GONE
            Toast.makeText(this, "Failed to render PDF", Toast.LENGTH_SHORT).show()
            return
        }

        tvNoPdfPreview.visibility = View.GONE
        pdfRecyclerView.visibility = View.VISIBLE

        pdfRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        pdfRecyclerView.adapter = PdfPageAdapter(bitmaps) { bitmap, _ ->
            currentImageBitmap = bitmap
            ivPlaceholder.setImageBitmap(bitmap)
            uploadedBitmaps.add(bitmap)
            imageCount++
            updateImageCountDisplay()
            updateImagePreviewRecycler()
            recognizeTextFromImage(bitmap)
        }
    }

    private fun renderPdfPages(uri: Uri, context: Context): List<Bitmap> {
        val fileDescriptor =
            context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
        val renderer = PdfRenderer(fileDescriptor)
        val bitmaps = mutableListOf<Bitmap>()

        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val bitmap = createBitmap(page.width, page.height)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmaps.add(bitmap)
            page.close()
        }

        renderer.close()
        fileDescriptor.close()
        return bitmaps
    }

    private fun updateImageCountDisplay() {
        imageCountText.text = imageCount.toString()
    }

    private fun convertBitmapsToPdf(bitmapList: List<Bitmap>) {
        if (bitmapList.isEmpty()) {
            Toast.makeText(this, "No images to convert", Toast.LENGTH_SHORT).show()
            return
        }

        val document = PdfDocument()
        bitmapList.forEachIndexed { index, bitmap ->
            val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            val pageInfo =
                PdfDocument.PageInfo.Builder(softwareBitmap.width, softwareBitmap.height, index + 1)
                    .create()
            val page = document.startPage(pageInfo)
            page.canvas.drawBitmap(softwareBitmap, 0f, 0f, null)
            document.finishPage(page)
        }

        val fileName = "QuickScan_${System.currentTimeMillis()}.pdf"
        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

        try {
            document.writeTo(FileOutputStream(file))
            Toast.makeText(this, "PDF saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
            openPdfFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save PDF: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            document.close()
        }
    }

    private fun openPdfFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/pdf")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Open PDF with"))
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun openPdfPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
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

    private fun recognizeTextFromImage(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                recognizedText = visionText.text
                extractedTextView.text =
                    if (recognizedText.isEmpty()) "No text found." else recognizedText
                wordCountText.text = recognizedText.split("\\s+".toRegex())
                    .filter { it.isNotBlank() }.size.toString()
                playButton.isEnabled = recognizedText.isNotEmpty()
                pauseButton.isEnabled = recognizedText.isNotEmpty()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to recognize text", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showImagePickerBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_picker, null)

        view.findViewById<CardView>(R.id.btn_capture_photo).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                openCameraLauncher.launch(intent)
            } else {
                requestPermissions(
                    arrayOf(android.Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_CODE
                )
            }
            dialog.dismiss()
        }

        view.findViewById<CardView>(R.id.btn_select_image).setOnClickListener {
            openGallery()
            dialog.dismiss()
        }

        view.findViewById<CardView>(R.id.btn_upload_pdf).setOnClickListener {
            openPdfPicker()
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    override fun onDestroy() {
        textToSpeech.shutdown()
        super.onDestroy()
    }
}
