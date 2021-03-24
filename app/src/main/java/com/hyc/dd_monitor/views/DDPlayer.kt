package com.hyc.dd_monitor.views

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ui.PlayerView
import com.hyc.dd_monitor.R
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

class DDPlayer(context: Context, playerId: Int) : ConstraintLayout(context) {

    var playerId: Int = playerId
        set(value) {
            playerNameBtn.text = playerNameBtn.text.replace(Regex("#${field + 1}"), "#${value+1}")
            field = value

        }

    var playerNameBtn: Button
    var playerView: PlayerView
    var controlBar: LinearLayout
    var danmuListView: ListView

    var danmuList: MutableList<String> = mutableListOf()
    var onDragAndDropListener: ((drag: Int, drop: Int) -> Unit)? = null

    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        View.inflate(context, R.layout.dd_player, this)
        Log.d("DDPlayer", "init")

        playerNameBtn = findViewById(R.id.player_name_btn)
        playerView = findViewById(R.id.dd_player_view)
        controlBar = findViewById(R.id.player_control_bar)
        danmuListView = findViewById(R.id.danmu_listView)
//        danmuTextView.movementMethod = ScrollingMovementMethod.getInstance()
        danmuListView.adapter = object : BaseAdapter() {
            override fun getCount(): Int {
                return danmuList.count()
            }

            override fun getItem(p0: Int): Any {
                return p0
            }

            override fun getItemId(p0: Int): Long {
                return p0.toLong()
            }

            override fun getView(p0: Int, p1: View?, p2: ViewGroup?): View {
                val view = p1 ?: View.inflate(context, R.layout.item_danmu_text, null)
                view.findViewById<TextView>(R.id.danmu_textView).text = danmuList[p0]
                return view
            }

        }

        val pid = playerId + 1
        playerNameBtn.text = "#$pid: 空"

        playerNameBtn.setOnClickListener {

        }

        setOnLongClickListener {
            startDragAndDrop(
                ClipData(
                    "layoutId", arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), ClipData.Item(
                        this.playerId.toString()
                    )
                ),
                DragShadowBuilder(playerNameBtn), null, View.DRAG_FLAG_GLOBAL
            )
            return@setOnLongClickListener true
        }

        setOnDragListener { view, dragEvent ->
            if (dragEvent.action == DragEvent.ACTION_DROP) {
                Log.d("drop", dragEvent.clipData.description.label.toString())
                val label = dragEvent.clipData.description.label.toString()
                if (label == "layoutId") {
                    val dragPid = dragEvent.clipData.getItemAt(0).text.toString().toInt()
                    Log.d("drop", "isSelf? $dragPid ${this.playerId}")
                    if (dragPid != this.playerId) {
                        Log.d("drop", "change $dragPid")
                        onDragAndDropListener?.invoke(dragPid, this.playerId)
                        this.playerId = dragPid

                    }
                }else if (label == "roomId") {
                    roomId = dragEvent.clipData.getItemAt(0).text.toString()
                }
//                Log.d("drop $playerId", dragEvent.clipData.getItemAt(0).text.toString())

            }
            return@setOnDragListener true
        }

        setOnClickListener {
            if (controlBar.visibility == VISIBLE) {
                controlBar.visibility = INVISIBLE
            }else{
                controlBar.visibility = VISIBLE
            }
        }
    }

    var player: SimpleExoPlayer? = null

    var socket: WebSocket? = null
    var socketTimer: Timer? = null

    var roomId: String? = null
        set(value) {
            field = value
            player?.stop()
            socketTimer?.cancel()
            socket?.close(1000, null)

            player = null
            socket = null
            socketTimer = null

            if (value == null) {
                return
            }

            OkHttpClient().newCall(
                Request.Builder()
                    .url("https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom?room_id=$value")
                    .build()
            ).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {

                }

                override fun onResponse(call: Call, response: Response) {
                    response.body?.let {
                        val jo = JSONObject(it.string())
                        jo.optJSONObject("data")?.let { data ->
                            val roomInfo = data.getJSONObject("room_info")
                            val anchorInfo =
                                data.getJSONObject("anchor_info").getJSONObject("base_info")

                            val liveStatus =
                                if (roomInfo.getInt("live_status") == 1) "" else "(未开播)"
                            val uname = anchorInfo.getString("uname")

                            val pid = playerId + 1

                            handler.post {
                                playerNameBtn.text = "#$pid: $liveStatus$uname"
                            }

                            OkHttpClient().newCall(
                                Request.Builder()
                                    .url("https://api.live.bilibili.com/room/v1/Room/playUrl?cid=$value&platform=web&qn=80")
                                    .build()
                            ).enqueue(object : Callback {
                                override fun onFailure(call: Call, e: IOException) {

                                }

                                override fun onResponse(call: Call, response: Response) {
                                    response.body?.let { it2 ->
                                        val jo1 = JSONObject(it2.string())
                                        jo1.optJSONObject("data")?.let { data1 ->
                                            val url = data1.getJSONArray("durl").getJSONObject(0)
                                                .getString(
                                                    "url"
                                                )
                                            handler.post {
                                                player = SimpleExoPlayer.Builder(context).build()
                                                playerView.player = player
                                                player!!.setMediaItem(MediaItem.fromUri(url))
                                                player!!.playWhenReady = true
                                                player!!.prepare()
                                            }


                                        }

                                    }
                                }

                            })

                            socket = OkHttpClient.Builder().build().newWebSocket(Request.Builder()
                                .url(
                                    "wss://broadcastlv.chat.bilibili.com:2245/sub"
                                ).build(), object : WebSocketListener() {
                                override fun onOpen(webSocket: WebSocket, response: Response) {
                                    super.onOpen(webSocket, response)
                                    Log.d("danmu", "open")
                                    val req = "{\"roomid\":$value}"
                                    val payload = ByteArray(16 + req.length)

                                    val head = byteArrayOf(
                                        0x00, 0x00, 0x00, (16 + req.length).toByte(),
                                        0x00, 0x10, 0x00, 0x01,
                                        0x00, 0x00, 0x00, 0x07,
                                        0x00, 0x00, 0x00, 0x01
                                    )

                                    System.arraycopy(head, 0, payload, 0, head.size)
                                    val reqBytes = req.toByteArray()
                                    System.arraycopy(
                                        reqBytes,
                                        0,
                                        payload,
                                        head.size,
                                        reqBytes.size
                                    )

                                    socket?.send(payload.toByteString(0, payload.size))

                                    socketTimer = Timer()
                                    socketTimer!!.schedule(object : TimerTask() {
                                        override fun run() {
                                            socket?.send(byteArrayOf(
                                                0x00, 0x00, 0x00, 0x10,
                                                0x00, 0x10, 0x00, 0x01,
                                                0x00, 0x00, 0x00, 0x02,
                                                0x00, 0x00, 0x00, 0x01
                                            ).toByteString())
                                        }

                                    }, 0, 30000)
                                }

                                override fun onMessage(
                                    webSocket: WebSocket,
                                    bytes: ByteString
                                ) {
                                    super.onMessage(webSocket, bytes)
                                    val byteArray = bytes.toByteArray()
                                    if (byteArray[7] == 2.toByte()) {
//                                        val infalter = Inflater()
//                                        infalter.setInput(byteArray,16,byteArray.size-16)

                                        val bis = ByteArrayInputStream(byteArray, 16,byteArray.size-16)
                                        val iis = InflaterInputStream(bis)
                                        val buf = ByteArray(1024)

                                        val bos = ByteArrayOutputStream()


                                        while (true) {
                                            val c = iis.read(buf)
                                            if (c == -1) break
                                            bos.write(buf, 0, c)
                                        }
                                        bos.flush()
                                        iis.close()

//                                        val strRes = String(bos.toByteArray(), Charsets.UTF_8)
//                                        Log.d("danmu", strRes)
                                        val unzipped = bos.toByteArray()
                                        var len = 0
                                        try {
                                            while (len < unzipped.size) {
                                                var b2 = unzipped[len+2].toInt()
                                                if (b2 < 0) b2 += 256
                                                var b3 = unzipped[len+3].toInt()
                                                if (b3 < 0) b3 += 256

                                                val nextLen = b2*256 + b3
                                                val jstr = String(unzipped, len+16, len+nextLen, Charsets.UTF_8)
                                                val jobj = JSONObject(jstr)
                                                if (jobj.getString("cmd") == "DANMU_MSG") {
                                                    val danmu = jobj.getJSONArray("info").getString(1)
                                                    Log.d("danmu", "$value $danmu")
                                                    handler.post {
//                                                        danmuTextView.text = "${danmuTextView.text}$danmu\n\n"
//                                                        danmuTextView.scrollTo(0, danmuTextView.lineCount*danmuTextView.lineHeight - danmuTextView.layout.height)
                                                        if (danmuList.count() > 20) {
                                                            danmuList.removeAt(0)
                                                        }
                                                        danmuList.add(danmu)
                                                        danmuListView.deferNotifyDataSetChanged()
                                                        danmuListView.setSelection(danmuListView.bottom)
                                                    }
                                                }

                                                len += nextLen
                                            }
                                        }catch (e: Exception) {

                                        }

                                    }

                                }

                                override fun onFailure(
                                    webSocket: WebSocket,
                                    t: Throwable,
                                    response: Response?
                                ) {
                                    super.onFailure(webSocket, t, response)
                                    Log.d("danmu", "fail ${t.message}")
                                    t.printStackTrace()
                                }

                                override fun onClosing(
                                    webSocket: WebSocket,
                                    code: Int,
                                    reason: String
                                ) {
                                    super.onClosing(webSocket, code, reason)
                                    Log.d("danmu", "closing")

                                }

                                override fun onClosed(
                                    webSocket: WebSocket,
                                    code: Int,
                                    reason: String
                                ) {
                                    super.onClosed(webSocket, code, reason)
                                    Log.d("danmu", "close")

                                }
                            })

                        }

                    }
                }

            })
        }
}