package com.hyc.dd_monitor.views

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.ImageView
import com.hyc.dd_monitor.R

class LayoutOptionsDialog(context: Context) : Dialog(context) {

    lateinit var onLayoutOptionsSelectedListener: (layout: Int) -> Unit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_layout_options)

        val gv = findViewById<GridView>(R.id.layout_grid_view)

        gv.adapter = object : BaseAdapter() {
            override fun getCount(): Int {
                return 27
            }

            override fun getItem(p0: Int): Any {
                return p0
            }

            override fun getItemId(p0: Int): Long {
                return p0.toLong()
            }

            override fun getView(p0: Int, p1: View?, p2: ViewGroup?): View {
                val view: View
                val iv: ImageView
                if (p1 == null) {
                    view = View.inflate(context, R.layout.item_layout_image, null)
                    iv = view.findViewById(R.id.layout_image)
                    view.tag = iv
                }else{
                    view = p1
                    iv = p1.tag as ImageView
                }

                val layoutId = p0 + 1
                val resId = context.resources.getIdentifier("layout$layoutId", "mipmap", context.packageName)

                iv.setImageResource(resId)

                return view
            }

        }

        gv.setOnItemClickListener { adapterView, view, i, l ->
            Log.d("layout item", i.toString())
            onLayoutOptionsSelectedListener(i+1)
            dismiss()
        }

        findViewById<Button>(R.id.layout_dialog_cancel_btn).setOnClickListener {
            dismiss()
        }
    }
}