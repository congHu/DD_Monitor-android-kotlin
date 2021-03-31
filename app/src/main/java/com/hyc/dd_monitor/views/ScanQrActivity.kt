package com.hyc.dd_monitor.views

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.PersistableBundle
import android.view.KeyEvent
import androidx.core.app.ActivityCompat
import com.google.zxing.integration.android.IntentIntegrator
import com.hyc.dd_monitor.R
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView

class ScanQrActivity : Activity() {

    lateinit var qrView: DecoratedBarcodeView
    lateinit var captureManager: CaptureManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_scan_qr)
        ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 123)

        qrView = findViewById(R.id.qrcode_view)

        captureManager = CaptureManager(this, qrView)
        captureManager.initializeFromIntent(intent, savedInstanceState)
        captureManager.decode()

    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        captureManager.onSaveInstanceState(outState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return qrView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        captureManager.onPause()
    }

    override fun onResume() {
        super.onResume()
        captureManager.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        captureManager.onDestroy()
    }
}