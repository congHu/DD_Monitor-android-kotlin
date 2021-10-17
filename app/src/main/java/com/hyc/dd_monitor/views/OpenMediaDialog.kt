package com.hyc.dd_monitor.views

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import com.hyc.dd_monitor.R
import com.hyc.dd_monitor.models.PlayerOptions
import com.hyc.dd_monitor.utils.RoundImageTransform
import com.squareup.picasso.Picasso
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.util.regex.Pattern

class OpenMediaDialog(context: Context)  : Dialog(context) {
    val handler = Handler(Looper.getMainLooper())

    var onMediaOpenListener: ((value: String, type: DDPlayer.MediaType) -> Unit)? = null
    var onBVInfoLoadListener: ((bvid:String, title: String, qn: JSONArray) -> Unit)? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_open_media)

        findViewById<Button>(R.id.dialog_cancel_btn).setOnClickListener {
            dismiss()
        }

        val valueInput = findViewById<EditText>(R.id.open_media_input)
        val mediaTypeOptions = findViewById<RadioGroup>(R.id.open_media_type_group)

        mediaTypeOptions.setOnCheckedChangeListener { radioGroup, i ->
            valueInput.text.clear()
            when (i) {
                R.id.media_type_live -> valueInput.hint = "直播间id"
                R.id.media_type_bv -> valueInput.hint = "BV号/av号"
                R.id.media_type_http -> valueInput.hint = "视频地址"
            }
        }

        val confirmBtn = findViewById<Button>(R.id.dialog_confirm_btn)
        confirmBtn.setOnClickListener {
            val input = valueInput.text.toString()
            when (mediaTypeOptions.checkedRadioButtonId) {
                R.id.media_type_live -> {
                    dismiss()
                    if (Pattern.compile("\\d+").matcher(input).matches()) {
                        onMediaOpenListener?.invoke(input, DDPlayer.MediaType.LIVE)
                    }else{
                        Toast.makeText(context, "无效的直播间id", Toast.LENGTH_SHORT).show()
                    }
                }
                R.id.media_type_bv -> {
                    val reqData = hashMapOf<String,String>()
                    when {
                        Pattern.compile("^BV[a-zA-Z0-9]{10}$").matcher(input).matches() -> {
                            reqData["bvid"] = input
                        }
                        Pattern.compile("^av\\d+$").matcher(input).matches() -> {
                            reqData["aid"] = input.replace("av", "")
                        }
                        else -> {
                            Toast.makeText(context, "无效的视频id", Toast.LENGTH_SHORT).show()
                            dismiss()
                        }
                    }
                    var idsParams = ""
                    for ((k,v) in reqData) {
                        idsParams += "$k=$v&"
                    }
                    confirmBtn.isEnabled = false

                    // 加载分P
                    OkHttpClient().newCall(
                        Request.Builder()
                            .url("https://api.bilibili.com/x/player/pagelist?$idsParams")
                            .build()
                    ).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            e.printStackTrace()
                            handler.post {
                                dismiss()
                                Toast.makeText(context, "获取视频信息失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                        override fun onResponse(call: Call, response: Response) {
                            response.body?.let {
                                try {
                                    val jo = JSONObject(it.string())
                                    val data = jo.getJSONArray("data")
                                    if (data.length() < 1) {
                                        handler.post {
                                            dismiss()
                                            Toast.makeText(context, "获取视频信息失败", Toast.LENGTH_SHORT).show()
                                        }
                                        return
                                    }
                                    if (data.length() > 1) {
                                        // 有分P信息，弹出对话框选择分P
                                        handler.post {
                                            val dialog = VideoPagesDialog(context, input, idsParams, "B站视频", data)
                                            dialog.onMediaOpenListener = { value, type ->
                                                dismiss()
                                                onMediaOpenListener?.invoke(value, type)
                                            }
                                            dialog.onBVInfoLoadListener = onBVInfoLoadListener
                                            dialog.show()
                                        }
                                        return
                                    }

                                    val cid = data.getJSONObject(0).getLong("cid")
                                    val params = idsParams + "cid=$cid"
                                    // 获取视频url
                                    OkHttpClient().newCall(
                                        Request.Builder()
                                            .url("https://api.bilibili.com/x/player/playurl?$params")
                                            .build()
                                    ).enqueue(object : Callback {
                                        override fun onFailure(call: Call, e: IOException) {
                                            e.printStackTrace()
                                            handler.post {
                                                dismiss()
                                                Toast.makeText(context, "获取视频信息失败", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        override fun onResponse(call: Call, response: Response) {
                                            response.body?.let { it1 ->
                                                try {
                                                    val jo1 = JSONObject(it1.string())
                                                    val data1 = jo1.getJSONObject("data")
                                                    val url0 = data1.getJSONArray("durl")
                                                        .getJSONObject(0)
                                                        .getString("url")
                                                    val qualities = data1.getJSONArray("support_formats")
                                                    handler.post {
                                                        dismiss()
                                                        onMediaOpenListener?.invoke(url0, DDPlayer.MediaType.BV)
                                                        onBVInfoLoadListener?.invoke(input, "B站视频", qualities)
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    handler.post {
                                                        dismiss()
                                                        Toast.makeText(context, "获取视频信息失败", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }

                                    })

                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    handler.post {
                                        dismiss()
                                        Toast.makeText(context, "获取视频信息失败", Toast.LENGTH_SHORT).show()
                                    }
                                }

                            }
                        }

                    })
                }
                R.id.media_type_http -> {
                    dismiss()
                    try {
                        URL(input)
                        onMediaOpenListener?.invoke(input, DDPlayer.MediaType.HTTP)
                    }catch (ex: MalformedURLException) {
                        Toast.makeText(context, "获取视频信息失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}