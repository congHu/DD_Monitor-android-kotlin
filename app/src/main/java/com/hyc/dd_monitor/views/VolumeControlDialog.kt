package com.hyc.dd_monitor.views

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import com.hyc.dd_monitor.R

class VolumeControlDialog(context: Context) : Dialog(context) {

    var onSeekBarListener: SeekBar.OnSeekBarChangeListener? = null
    var volume: Int = 100
    var title = "音量调节"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_volume_single)

        findViewById<TextView>(R.id.dialog_title).text = title

        val textView = findViewById<TextView>(R.id.dialog_volume_textview)
        textView.text = volume.toString()

        val slider = findViewById<SeekBar>(R.id.dialog_volume_slider)
        slider.progress = volume
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                onSeekBarListener?.onProgressChanged(p0, p1, p2)
                textView.text = p1.toString()
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
                onSeekBarListener?.onStartTrackingTouch(p0)
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                onSeekBarListener?.onStopTrackingTouch(p0)
            }

        })

        val muteBtn = findViewById<Button>(R.id.mute_btn)
        muteBtn.typeface = Typeface.createFromAsset(context.assets, "iconfont.ttf")
        muteBtn.setOnClickListener {
            var v = 0
            if (slider.progress == 0) {
                v = 50
            }
            slider.progress = v
            onSeekBarListener?.onProgressChanged(slider, v, true)
            onSeekBarListener?.onStopTrackingTouch(slider)
        }
    }
}