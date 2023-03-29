package com.example.ai_background_eraser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.alexvasilkov.gestures.views.interfaces.GestureView
import com.canhub.cropper.CropImageContract
import com.example.autobackgroundremover.BackgroundRemover
import com.example.autobackgroundremover.OnBackgroundChangeListener
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.FileDescriptor
import java.io.IOException

class MainActivity : AppCompatActivity() {


    private val INTRO_REQUEST_CODE = 4
    private val WRITE_EXTERNAL_STORAGE_CODE = 1
    private val IMAGE_CHOOSER_REQUEST_CODE = 2
    private val CAMERA_REQUEST_CODE = 3

    private val INTRO_SHOWN = "INTRO_SHOWN"
    var loadingModal: FrameLayout? = null
    private var gestureView: GestureView? = null
    private var drawView: DrawView? = null
    private var manualClearSettingsLayout: LinearLayout? = null

    private val MAX_ERASER_SIZE: Short = 150
    private val BORDER_SIZE: Short = 45
    private val MAX_ZOOM = 4f

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.photo_edit_toolbar)
        toolbar.setBackgroundColor(Color.WHITE)
        toolbar.setTitleTextColor(Color.BLACK)
        setSupportActionBar(toolbar)

        val drawViewLayout: FrameLayout = findViewById(R.id.drawViewLayout)
        drawViewLayout.background = CheckerboardDrawable.create()

        val strokeBar: SeekBar = findViewById(R.id.strokeBar)
        strokeBar.max = MAX_ERASER_SIZE.toInt()
        strokeBar.progress = 20

        gestureView = findViewById(R.id.gestureView)

        drawView = findViewById(R.id.drawView)
        drawView!!.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        drawView!!.setStrokeWidth(strokeBar.progress)

        strokeBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                drawView!!.setStrokeWidth(seekBar.progress)
            }
        })

        loadingModal = findViewById(R.id.loadingModal)
        loadingModal!!.setVisibility(View.INVISIBLE)

        drawView!!.setLoadingModal(loadingModal)

        manualClearSettingsLayout = findViewById(R.id.manual_clear_settings_layout)

        setUndoRedo()
        initializeActionButtons()

        if (supportActionBar != null) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.setDisplayShowHomeEnabled(true)
            supportActionBar!!.setDisplayShowTitleEnabled(false)
            if (toolbar.navigationIcon != null) {
//                toolbar.navigationIcon!!.colorFilter
//                    .setColorFilter(ContextCompat.getColor(this,R.color.white), PorterDuff.Mode.SRC_ATOP)
                Toast.makeText(this, "toolbar", Toast.LENGTH_SHORT).show()
            }
        }

        var doneButton: Button = findViewById(R.id.done)
//        doneButton.setOnClickListener { v: View? -> startSaveDrawingTask() }

        start()

    }

    private fun fromGallery() {
        val iGallery = Intent(Intent.ACTION_PICK)
        iGallery.type = "image/*"
        resultLauncher.launch(iGallery)

    }

    private var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent? = result.data
                if (data != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        setDrawViewBitmap(data.data)
                        Toast.makeText(this, "setDrawViewBitmap calling", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                setResult(RESULT_CANCELED)
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun getExtraSource(): Uri? {
        Toast.makeText(this, "getExtraSource", Toast.LENGTH_SHORT).show()
        return if (intent.hasExtra(CutOut.CUTOUT_EXTRA_SOURCE)) {
            intent.getParcelableExtra(CutOut.CUTOUT_EXTRA_SOURCE, CutOut::class.java) as Uri
        } else null
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun start() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            val uri: Uri? = getExtraSource()

            if (intent.getBooleanExtra(CutOut.CUTOUT_EXTRA_CROP, false)) {
//                var cropImageBuilder: CutOut.ActivityBuilder = if (uri != null) {
//                    cropImage.activity(uri)
//                }
//                else {
////                    if (ContextCompat.checkSelfPermission(this,
////                            Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
////                        cropImage.activity()
////                    } else {
////                        ActivityCompat.requestPermissions(this,
////                            arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST_CODE
////                        )
//                        return
////                    }
//                }
//                cropImageBuilder = cropImageBuilder.setGuidelines(CropImageView.Guidelines.ON)
//                cropImageBuilder.start(this)
                Toast.makeText(this, "cropper...............", Toast.LENGTH_SHORT).show()
            } else {
                if (uri != null) {
                    setDrawViewBitmap(uri)
                } else {
                    fromGallery()
                }

            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), WRITE_EXTERNAL_STORAGE_CODE
            )
        }
    }

    private fun activateGestureView() {
        gestureView!!.controller.settings
            .setMaxZoom(MAX_ZOOM)
            .setDoubleTapZoom(-1f) // Falls back to max zoom level
            .setPanEnabled(true)
            .setZoomEnabled(true)
            .setDoubleTapEnabled(true)
            .setOverscrollDistance(0f, 0f).overzoomFactor = 2f
    }

    private fun deactivateGestureView() {
        gestureView!!.controller.settings
            .setPanEnabled(false)
            .setZoomEnabled(false).isDoubleTapEnabled = false
    }

    private fun initializeActionButtons() {
        val bottom_navigationView = findViewById<BottomNavigationView>(R.id.bottom_navView)
        drawView!!.setAction(DrawView.DrawViewAction.MANUAL_CLEAR)
        deactivateGestureView()
        bottom_navigationView.setOnItemReselectedListener {
            when (it.itemId) {
                R.id.auto_selfie -> {
                    Toast.makeText(this, "Auto selfie", Toast.LENGTH_SHORT).show()
                    drawView!!.setAction(DrawView.DrawViewAction.AUTO_SELFIE)
                    manualClearSettingsLayout!!.visibility = View.INVISIBLE
                    autoSelfie()
                    deactivateGestureView()
                }

                R.id.auto_clear_button -> {
                    Toast.makeText(this, "Auto Color", Toast.LENGTH_SHORT).show()
                    drawView!!.setAction(DrawView.DrawViewAction.AUTO_CLEAR)
                    manualClearSettingsLayout!!.visibility = View.INVISIBLE
                    deactivateGestureView()
                }

                R.id.manual_clear_button -> {
                    Toast.makeText(this, "manual clear", Toast.LENGTH_SHORT).show()
                    drawView!!.setAction(DrawView.DrawViewAction.MANUAL_CLEAR)
                    manualClearSettingsLayout!!.visibility = View.VISIBLE
                    deactivateGestureView()
                }

                R.id.zoom_button -> {
                    Toast.makeText(this, "zoom", Toast.LENGTH_SHORT).show()
                    drawView!!.setAction(DrawView.DrawViewAction.ZOOM)
                    manualClearSettingsLayout!!.visibility = View.INVISIBLE
                    activateGestureView()
                }
            }
        }
    }

    private fun autoSelfie() {
        drawView!!.beforeAutoSelfie()
        BackgroundRemover.bitmapForProcessing(
            drawView!!.getBitmap()!!,
            true,
            object : OnBackgroundChangeListener {
                override fun onSuccess(bitmap: Bitmap) {
                    drawView!!.setBitmap(bitmap)
                    drawView!!.afterAutoSelfie()
                }

                override fun onFailed(exception: Exception) {
                    Toast.makeText(this@MainActivity, "Error Occur", Toast.LENGTH_SHORT).show()
                }

            })

    }

    private fun setUndoRedo() {
        val undoButton: Button = findViewById(R.id.undo)
        undoButton.isEnabled = false
        undoButton.setOnClickListener { v: View? -> undo() }
        val redoButton: Button = findViewById(R.id.redo)
        redoButton.isEnabled = false
        redoButton.setOnClickListener { v: View? -> redo() }
        drawView!!.setButtons(undoButton, redoButton)
    }

    private fun exitWithError(e: java.lang.Exception?) {
        val intent = Intent()
        intent.putExtra(CutOut.CUTOUT_EXTRA_RESULT, e)
        setResult(CutOut.CUTOUT_ACTIVITY_RESULT_ERROR_CODE.toInt(), intent)
        finish()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun setDrawViewBitmap(uri: Uri?) {
        try {
            val bitmap: Bitmap? = uriToBitmap(uri!!)
            drawView!!.setBitmap(bitmap)
//            Toast.makeText(this,"setting to autoColorAndManual",Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            exitWithError(e)
        }
    }

    private fun uriToBitmap(selectedFileUri: Uri): Bitmap? {
        try {
            val parcelFileDescriptor = contentResolver.openFileDescriptor(selectedFileUri, "r")
            val fileDescriptor: FileDescriptor = parcelFileDescriptor!!.fileDescriptor
            val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
            parcelFileDescriptor.close()
            return image
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    private fun undo() {
        drawView!!.undo()
    }

    private fun redo() {
        drawView!!.redo()
    }

}