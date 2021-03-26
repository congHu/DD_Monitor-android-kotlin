package com.hyc.dd_monitor.views

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Message
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.util.TypedValue
import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.edit
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.material.slider.Slider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.hyc.dd_monitor.R
import com.hyc.dd_monitor.models.PlayerOptions
import com.hyc.dd_monitor.utils.RoundImageTransform
import com.squareup.picasso.Picasso
import okhttp3.*
import okhttp3.internal.notify
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.math.roundToInt

class DDPlayer(context: Context, playerId: Int) : ConstraintLayout(context) {

    var playerId: Int = playerId
        set(value) {
            playerNameBtn.text = playerNameBtn.text.replace(Regex("#${field + 1}"), "#${value+1}")
            shadowTextView.text = "#${value+1}"
            field = value

        }

    var playerOptions = PlayerOptions()

    var qn: Int = 80
        set(value) {
            if (field != value) {
                field = value
                if (value == 10000) {
                    qnBtn.text = "原画"
                }else if (value == 400) {
                    qnBtn.text = "蓝光"
                }else if (value == 250) {
                    qnBtn.text = "超清"
                }else if (value == 150) {
                    qnBtn.text = "高清"
                }else if (value == 80) {
                    qnBtn.text = "流畅"
                }else{
                    qnBtn.text = "画质"
                }
                roomId = roomId
                playerOptions.qn = value
                notifyPlayerOptionsChange()
            }
        }

    var playerNameBtn: Button
    var playerView: PlayerView
    var controlBar: LinearLayout

    var danmuView: View
    var danmuListView: ListView
    var interpreterListView: ListView

    var danmuList: MutableList<String> = mutableListOf()
    var interpreterList: MutableList<String> = mutableListOf()

    var onDragAndDropListener: ((drag: Int, drop: Int) -> Unit)? = null
    var onCardDropListener: (() -> Unit)? = null

    var volumeBar: LinearLayout
    var volumeSlider: SeekBar
    var volumeAdjusting = false

    var refreshBtn: Button
    var volumeBtn: Button
    var danmuBtn: Button

    var isGlobalMuted = false
        set(value) {
            field = value
            player?.volume = if (value) 0f else volumeSlider.progress.toFloat()/100f
        }

    var qnBtn: Button

    var isHiddenBarBtns = false

    var shadowView: View
    var shadowFaceImg: ImageView
    var shadowTextView: TextView

    var hideControlTimer: Timer? = null

    var doubleClickTime: Long = 0
    var onDoubleClickListener: ((playerId: Int) -> Unit)? = null


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
        danmuView = findViewById(R.id.danmu_view)
        danmuListView = findViewById(R.id.danmu_listView)
        interpreterListView = findViewById(R.id.interpreter_listView)
        volumeBar = findViewById(R.id.volume_bar)
        volumeSlider = findViewById(R.id.volume_slider)
        qnBtn = findViewById(R.id.qn_btn)


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
                val textview = view.findViewById<TextView>(R.id.danmu_textView)
                textview.text = danmuList[p0]

                textview.textSize = playerOptions.danmuSize.toFloat()
                return view
            }

        }

        interpreterListView.adapter = object : BaseAdapter() {
            override fun getCount(): Int {
                return interpreterList.count()
            }

            override fun getItem(p0: Int): Any {
                return p0
            }

            override fun getItemId(p0: Int): Long {
                return p0.toLong()
            }

            override fun getView(p0: Int, p1: View?, p2: ViewGroup?): View {
                val view = p1 ?: View.inflate(context, R.layout.item_danmu_text, null)
                val textview = view.findViewById<TextView>(R.id.danmu_textView)
                textview.text = interpreterList[p0]

                textview.textSize = playerOptions.danmuSize.toFloat()
                return view
            }

        }

        shadowView = findViewById(R.id.shadow_view)
        shadowFaceImg = findViewById(R.id.shadow_imageview)
        shadowTextView = findViewById(R.id.shadow_textview)

        playerNameBtn.text = "#${playerId+1}: 空"
        shadowTextView.text = "#${playerId+1}"

        playerNameBtn.setOnClickListener {
            if (roomId == null) return@setOnClickListener
            val pop = PopupMenu(context, playerNameBtn)
            val menuId = if (isHiddenBarBtns) R.menu.player_options_more else R.menu.player_options
            pop.menuInflater.inflate(menuId, pop.menu)
            pop.setOnMenuItemClickListener {
                if (it.itemId == R.id.window_close) {
                    roomId = null
                    context.getSharedPreferences("sp", AppCompatActivity.MODE_PRIVATE).edit {
                        this.putString("roomId${this@DDPlayer.playerId}", null).apply()
                    }
                }
                if (it.itemId == R.id.open_live) {
                    try {
                        val intent = Intent()
                        intent.data = Uri.parse("bilibili://live/$roomId")
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)

                        playerOptions.volume = 0f
                        notifyPlayerOptionsChange()
                    }catch (_:Exception) {
                        val intent = Intent()
                        intent.data = Uri.parse("https://live.bilibili.com/$roomId")
                        context.startActivity(intent)
                    }

                }
                if (it.itemId == R.id.refresh_btn) {
                    this.roomId = roomId
                }
                if (it.itemId == R.id.volume_btn) {
                    if (volumeBar.visibility == VISIBLE) {
                        volumeBar.visibility = INVISIBLE
                    }else{
                        volumeBar.visibility = VISIBLE
                    }
                }
                if (it.itemId == R.id.danmu_btn) {
                    showDanmuDialog()
                }
                if (it.itemId == R.id.qn_btn) {
                    showQnMenu()
                }
                return@setOnMenuItemClickListener true
            }
            pop.show()
        }



        setOnLongClickListener {
            showControlBar()

            startDragAndDrop(
                ClipData(
                    "layoutId", arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), ClipData.Item(
                        this@DDPlayer.playerId.toString()
                    )
                ),
                DragShadowBuilder(shadowView), null, View.DRAG_FLAG_GLOBAL
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
//                        this.playerId = dragPid
//                        showControlBar()
                    }
                }else if (label == "roomId") {
//                    showControlBar()

                    roomId = dragEvent.clipData.getItemAt(0).text.toString()
                    val face = dragEvent.clipData.getItemAt(1).text.toString()
                    Picasso.get().load(face).transform(RoundImageTransform()).into(shadowFaceImg)
//                    Log.d("shadowFaceImg", shadowFaceImg)
                    onCardDropListener?.invoke()
                    context.getSharedPreferences("sp", AppCompatActivity.MODE_PRIVATE).edit {
                        this.putString("roomId${this@DDPlayer.playerId}", roomId).apply()
                    }
                }
//                Log.d("drop $playerId", dragEvent.clipData.getItemAt(0).text.toString())
            }
            if (dragEvent.action == DragEvent.ACTION_DRAG_ENTERED) {
                showControlBar()
            }
            return@setOnDragListener true
        }

        setOnClickListener {
            if (controlBar.visibility == VISIBLE) {
                controlBar.visibility = INVISIBLE
            }else{
                showControlBar()
            }
            volumeBar.visibility = INVISIBLE

            if (System.currentTimeMillis() - doubleClickTime < 300) {
                doubleClickTime = 0
                Log.d("doubleclick", "doubleclick")
                onDoubleClickListener?.invoke(this.playerId)
            }else{
                doubleClickTime = System.currentTimeMillis()
            }
        }

        val typeface = Typeface.createFromAsset(context.assets, "iconfont.ttf")

        refreshBtn = findViewById<Button>(R.id.refresh_btn)
        refreshBtn.typeface = typeface
        refreshBtn.setOnClickListener {
            this.roomId = roomId
        }
        volumeBtn = findViewById<Button>(R.id.volume_btn)
        volumeBtn.typeface = typeface
        volumeBtn.setOnClickListener {
            showControlBar()
            if (volumeBar.visibility == VISIBLE) {
                volumeBar.visibility = INVISIBLE
            }else{
                volumeBar.visibility = VISIBLE
            }
        }
        danmuBtn = findViewById(R.id.danmu_btn)
        danmuBtn.typeface = typeface
        danmuBtn.setOnClickListener {
            showDanmuDialog()
        }

//        volumeSlider.addOnChangeListener { slider, value, fromUser ->
//            player?.volume = if (isGlobalMuted) 0f else value/100f
//        }
        volumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                player?.volume = if (isGlobalMuted) 0f else p1.toFloat()/100f
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
                volumeAdjusting = true
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                volumeAdjusting = false
                showControlBar()
                playerOptions.volume = volumeSlider.progress.toFloat()/100f
                notifyPlayerOptionsChange()
            }
        })

        val muteBtn = findViewById<Button>(R.id.mute_btn)
        muteBtn.typeface = typeface
        muteBtn.setOnClickListener {
            if (volumeSlider.progress == 0) {
                volumeSlider.progress = 50
                player?.volume = if (isGlobalMuted) 0f else .5f
                playerOptions.volume = .5f
            }else{
                volumeSlider.progress = 0
                player?.volume = 0f
                playerOptions.volume = 0f
            }
            notifyPlayerOptionsChange()

        }

        qnBtn.setOnClickListener {
            showQnMenu()
        }

        context.getSharedPreferences("sp", AppCompatActivity.MODE_PRIVATE).getString("opts${this.playerId}", "")?.let {
            try {
                Log.d("playeroptions", "load $it")
                playerOptions = Gson().fromJson(it, PlayerOptions::class.java)
                qn = playerOptions.qn
                notifyPlayerOptionsChange()
            }catch (e:java.lang.Exception) {

            }
        }

        showControlBar()
    }

    fun showQnMenu() {
        val pop = PopupMenu(context, playerNameBtn)
        pop.menuInflater.inflate(R.menu.qn_menu, pop.menu)
        pop.setOnMenuItemClickListener {
            var newQn = 80
            when (it.itemId) {
                R.id.qn_10000 -> newQn = 10000
                R.id.qn_400 -> newQn = 400
                R.id.qn_250 -> newQn = 250
                R.id.qn_150 -> newQn = 150
                R.id.qn_80 -> newQn = 80
            }
            if (newQn != qn) {
                qn = newQn
            }
            return@setOnMenuItemClickListener true
        }
        pop.show()
    }

    fun showDanmuDialog() {
        val dialog = DanmuOptionsDialog(context, this.playerId)
        dialog.onDanmuOptionsChangeListener = {
            playerOptions = it
            notifyPlayerOptionsChange()
        }
        dialog.show()
    }

    var player: SimpleExoPlayer? = null

    var socket: WebSocket? = null
    var socketTimer: Timer? = null

    var roomId: String? = null
        set(value) {
            if (field != value) {
                danmuList.removeAll(danmuList)
                danmuListView.invalidateViews()
            }
            field = value
            playerView.player = null
            player?.stop()
            socketTimer?.cancel()
            socket?.close(1000, null)

            shadowFaceImg.setImageDrawable(null)

            player = null
            socket = null
            socketTimer = null

            playerNameBtn.text = "#${playerId+1}: 空"
            shadowTextView.text = "#${playerId+1}"

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
                            val face = anchorInfo.getString("face").replace("http://", "https://")
//                            Log.d("shadowFaceImg", shadowFaceImg)
                            handler.post {
                                playerNameBtn.text = "#${playerId + 1}: $liveStatus$uname"
                                shadowTextView.text = "#${playerId + 1}"
                                Picasso.get().load(face).transform(RoundImageTransform())
                                    .into(shadowFaceImg)
                            }
//                            val msg = Message()
//                            msg.what = 1
//                            msg.obj = "#${playerId + 1}: $liveStatus$uname"
//                            myHandler.sendMessage(msg)


                            OkHttpClient().newCall(
                                Request.Builder()
                                    .url("https://api.live.bilibili.com/room/v1/Room/playUrl?cid=$value&platform=web&qn=$qn")
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
                                                player!!.volume =
                                                    if (isGlobalMuted) 0f else volumeSlider.progress.toFloat() / 100f
                                                player!!.playWhenReady = true
                                                player!!.prepare()
                                            }
//                                            val msg2 = Message()
//                                            msg2.what = 2
//                                            msg2.obj = url
//                                            myHandler.sendMessage(msg2)

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
                                            Log.d("danmu", "heartbeat")
                                            socket?.send(
                                                byteArrayOf(
                                                    0x00, 0x00, 0x00, 0x10,
                                                    0x00, 0x10, 0x00, 0x01,
                                                    0x00, 0x00, 0x00, 0x02,
                                                    0x00, 0x00, 0x00, 0x01
                                                ).toByteString()
                                            )
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

                                        val bis = ByteArrayInputStream(
                                            byteArray,
                                            16,
                                            byteArray.size - 16
                                        )
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
                                                var b2 = unzipped[len + 2].toInt()
                                                if (b2 < 0) b2 += 256
                                                var b3 = unzipped[len + 3].toInt()
                                                if (b3 < 0) b3 += 256

                                                val nextLen = b2 * 256 + b3
                                                val jstr = String(
                                                    unzipped,
                                                    len + 16,
                                                    len + nextLen,
                                                    Charsets.UTF_8
                                                )
                                                val jobj = JSONObject(jstr)
                                                if (jobj.getString("cmd") == "DANMU_MSG") {
                                                    val danmu =
                                                        jobj.getJSONArray("info").getString(1)
                                                    Log.d("danmu", "$value $danmu")
                                                    handler.post {
                                                        if (danmuList.count() > 20) {
                                                            danmuList.removeFirst()
                                                        }
                                                        danmuList.add(danmu)
                                                        danmuListView.deferNotifyDataSetChanged()
                                                        danmuListView.invalidateViews()
                                                        danmuListView.setSelection(danmuListView.bottom)

                                                        if (danmu.startsWith("【")
                                                            || danmu.startsWith("[")
                                                            || danmu.startsWith("{[}")
                                                        ) {
                                                            if (interpreterList.count() > 20) {
                                                                interpreterList.removeFirst()
                                                            }
                                                            interpreterList.add(danmu)
                                                            interpreterListView.deferNotifyDataSetChanged()
                                                            interpreterListView.invalidateViews()
                                                            interpreterListView.setSelection(interpreterListView.bottom)
                                                        }
                                                    }
//                                                    val msg3 = Message()
//                                                    msg3.what = 3
//                                                    msg3.obj = danmu
//                                                    myHandler.sendMessage(msg3)
                                                }

                                                len += nextLen
                                            }
                                        } catch (e: Exception) {

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

    fun adjustControlBar() {
        Log.d("ddplayer", "width $width ${context.resources.displayMetrics.density}")
        if (width < context.resources.displayMetrics.density * 30 * 5) {
            refreshBtn.visibility = GONE
            volumeBtn.visibility = GONE
            danmuBtn.visibility = GONE
            qnBtn.visibility = GONE
            isHiddenBarBtns = true
        }else{
            refreshBtn.visibility = VISIBLE
            volumeBtn.visibility = VISIBLE
            danmuBtn.visibility = VISIBLE
            qnBtn.visibility = VISIBLE
            isHiddenBarBtns = false
        }
    }

    fun notifyPlayerOptionsChange() {
        volumeSlider.progress = (playerOptions.volume * 100f).roundToInt()
        player?.volume = if (isGlobalMuted) 0f else playerOptions.volume

        danmuView.visibility = if (playerOptions.isDanmuShow) VISIBLE else GONE
        danmuListView.visibility = if (playerOptions.interpreterStyle == 2) GONE else VISIBLE
        interpreterListView.visibility = if (playerOptions.interpreterStyle == 0) GONE else VISIBLE
        danmuListView.invalidateViews()
        interpreterListView.invalidateViews()

//        app:layout_constraintBottom_toBottomOf="parent"
//        app:layout_constraintEnd_toEndOf="parent"
//        app:layout_constraintStart_toStartOf="parent"
//        app:layout_constraintTop_toTopOf="parent"
//        app:layout_constraintHeight_percent=".8"
//        app:layout_constraintHorizontal_bias="0"
//        app:layout_constraintVertical_bias="0"
//        app:layout_constraintWidth_percent=".2"
        val layoutParams = danmuView.layoutParams as LayoutParams
        layoutParams.horizontalBias = if (playerOptions.danmuPosition == 0 || playerOptions.danmuPosition == 1) 0f else 1f
        layoutParams.verticalBias = if (playerOptions.danmuPosition == 0 || playerOptions.danmuPosition == 2) 0f else 1f
        layoutParams.matchConstraintPercentWidth = playerOptions.danmuWidth
        layoutParams.matchConstraintPercentHeight = playerOptions.danmuHeight

        danmuView.layoutParams = layoutParams

        val jstr = Gson().toJson(playerOptions)
        Log.d("playeroptions", "${this.playerId} $jstr")
        context.getSharedPreferences("sp", AppCompatActivity.MODE_PRIVATE).edit {
            this.putString("opts${this@DDPlayer.playerId}", jstr).apply()
        }
    }

    fun showControlBar() {
        controlBar.visibility = VISIBLE
        hideControlTimer?.cancel()
        hideControlTimer = null
        hideControlTimer = Timer()
        hideControlTimer!!.schedule(object : TimerTask() {
            override fun run() {
                if (!volumeAdjusting) {
                    controlBar.visibility = INVISIBLE
                    volumeBar.visibility = INVISIBLE
                }

                hideControlTimer = null
            }
        }, 5000)
    }
}