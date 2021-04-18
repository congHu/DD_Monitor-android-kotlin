package com.hyc.dd_monitor.views

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.hyc.dd_monitor.R

class GlobalVolumeDialog(context: Context, val ddLayout: DDLayout) : Dialog(context) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_global_volume)

        val listView = findViewById<ListView>(R.id.global_volume_list_view);
        listView.adapter = object : BaseAdapter() {
            override fun getCount(): Int {
                return ddLayout.layoutPlayerCount
            }

            override fun getItem(p0: Int): Any {
                return p0
            }

            override fun getItemId(p0: Int): Long {
                return p0.toLong()
            }

            override fun getView(p0: Int, p1: View?, p2: ViewGroup?): View {
                val view = p1 ?: View.inflate(context, R.layout.dialog_volume_single, null)

                val p = ddLayout.players[p0]
                view.findViewById<TextView>(R.id.dialog_title).text = p.playerNameBtn.text

                val volume = (p.playerOptions.volume * 100f).toInt()
                val slider = view.findViewById<SeekBar>(R.id.dialog_volume_slider)
                slider.progress = volume
                val textView = view.findViewById<TextView>(R.id.dialog_volume_textview)
                textView.text = volume.toString()

                slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
                    override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                        slider.progress = p1
                        textView.text = p1.toString()
                        p.volumeChangedListener.onProgressChanged(p0, p1, p2)
                    }

                    override fun onStartTrackingTouch(p0: SeekBar?) {
                        p.volumeChangedListener.onStartTrackingTouch(p0)
                    }

                    override fun onStopTrackingTouch(p0: SeekBar?) {
                        p.volumeChangedListener.onStopTrackingTouch(p0)
                    }

                })

                val muteBtn = view.findViewById<Button>(R.id.mute_btn)
                muteBtn.typeface = Typeface.createFromAsset(context.assets, "iconfont.ttf")
                muteBtn.setOnClickListener {
                    var v = 0
                    if (slider.progress == 0) {
                        v = 50
                    }
                    slider.progress = v
                    textView.text = v.toString()
                    p.volumeChangedListener.onProgressChanged(slider, v, true)
                    p.volumeChangedListener.onStopTrackingTouch(slider)
                }

                return view
            }

        }
    }
}