package com.hyc.dd_monitor.views

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.hyc.dd_monitor.R
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class VideoPagesDialog(context: Context, val bvid: String, val idsParams: String, val title: String, val pages: JSONArray) : Dialog(context) {
    val handler = Handler(Looper.getMainLooper())

    var onMediaOpenListener: ((value: String, type: DDPlayer.MediaType) -> Unit)? = null
    var onBVInfoLoadListener: ((bvid:String, title: String, qn: JSONArray) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_video_pages)
        val listview = findViewById<ListView>(R.id.video_pages_list_view)
        listview.adapter = object : BaseAdapter() {
            override fun getCount(): Int {
                return pages.length()
            }

            override fun getItem(p0: Int): Any {
                return pages.get(p0)
            }

            override fun getItemId(p0: Int): Long {
                return p0.toLong()
            }

            override fun getView(p0: Int, p1: View?, p2: ViewGroup?): View {
                val view: View
                val tv: TextView
                if (p1 == null) {
                    view = View.inflate(context, R.layout.item_video_page, null)
                    tv = view.findViewById(R.id.video_page_text_view)
                    view.tag = tv
                }else{
                    view = p1
                    tv = p1.tag as TextView
                }

                tv.text = pages.getJSONObject(p0).getString("part")

                return view
            }
        }
        listview.setOnItemClickListener { adapterView, view, i, l ->
            val cid = pages.getJSONObject(i).getLong("cid")
            val params = idsParams + "cid=$cid"
            Log.d("videopages", "params $params")
            // 获取视频url
            OkHttpClient().newCall(
                Request.Builder()
                    .url("https://api.bilibili.com/x/player/playurl?$params")
                    .build()
            ).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    handler.post {
                        dismiss()
                        Toast.makeText(context, "获取视频信息失败", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onResponse(call: Call, response: Response) {
                    response.body?.let { it1 ->
                        try {
                            val jo1 = JSONObject(it1.string())
                            Log.d("videopages", jo1.toString())

                            val data1 = jo1.getJSONObject("data")
                            val url0 = data1.getJSONArray("durl")
                                .getJSONObject(0)
                                .getString("url")
                            val qualities = data1.getJSONArray("support_formats")
                            handler.post {
                                dismiss()
                                onMediaOpenListener?.invoke(url0, DDPlayer.MediaType.BV)
                                onBVInfoLoadListener?.invoke(bvid,title, qualities)
                            }
                        } catch (e: Exception) {
                            handler.post {
                                dismiss()
                                Toast.makeText(context, "获取视频信息失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

            })
        }
    }
}