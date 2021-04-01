package com.hyc.dd_monitor.views

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import com.google.gson.Gson
import com.hyc.dd_monitor.R
import com.hyc.dd_monitor.models.PlayerOptions
import com.hyc.dd_monitor.utils.RoundImageTransform
import com.squareup.picasso.Picasso
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*
import java.util.zip.InflaterInputStream
import kotlin.math.roundToInt

class DDPlayer(context: Context, playerId: Int) : ConstraintLayout(context) {

    // 设置窗口序号#
    var playerId: Int = playerId
        set(value) {
            playerNameBtn.text = playerNameBtn.text.replace(Regex("#${field + 1}"), "#${value+1}")
            shadowTextView.text = "#${value+1}"
            field = value

        }

    var playerOptions = PlayerOptions()

    // 刷新画质
    var qn: Int = 80
        set(value) {
            if (field != value) {
                field = value
                qnBtn.text = "画质"
                when (value) {
                    10000 -> qnBtn.text = "原画"
                    400 -> qnBtn.text = "蓝光"
                    250 -> qnBtn.text = "超清"
                    150 -> qnBtn.text = "高清"
                    80 -> qnBtn.text = "流畅"
                }
                roomId = roomId
                playerOptions.qn = value
                notifyPlayerOptionsChange()
            }
        }

    var playerNameBtn: Button
    var playerView: PlayerView
    var controlBar: LinearLayout

    var danmuView: LinearLayout
    var danmuListView: ListView
    var interpreterListView: ListView
    var danmuListViewAdapter: BaseAdapter
    var interpreterViewAdapter: BaseAdapter

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

    // 设置是否全局静音
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
        danmuListViewAdapter = object : BaseAdapter() {
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
        danmuListView.adapter = danmuListViewAdapter
        danmuListView.setOnItemClickListener { adapterView, view, i, l ->
            val danmu = danmuList[i]
            val pop = PopupMenu(context, view)
            pop.menuInflater.inflate(R.menu.danmu_clear, pop.menu)
            pop.setOnMenuItemClickListener {
                if (it.itemId == R.id.danmu_clear) {
                    danmuList.remove(danmu)
                    danmuListViewAdapter.notifyDataSetInvalidated()
                }
                return@setOnMenuItemClickListener true
            }
            pop.show()
        }

        interpreterViewAdapter = object : BaseAdapter() {
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

        // 音量调节
        val volumeChangedListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                player?.volume = if (isGlobalMuted) 0f else p1.toFloat()/100f
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
                volumeAdjusting = true
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                volumeAdjusting = false
                showControlBar()
                if (p0 != null && p0 != volumeSlider) {
                    volumeSlider.progress = p0.progress
                }
                playerOptions.volume = volumeSlider.progress.toFloat()/100f
                notifyPlayerOptionsChange()
            }
        }

        interpreterListView.adapter = interpreterViewAdapter
        interpreterListView.setOnItemClickListener { adapterView, view, i, l ->
            val danmu = interpreterList[i]
            val pop = PopupMenu(context, view)
            pop.menuInflater.inflate(R.menu.danmu_clear, pop.menu)
            pop.setOnMenuItemClickListener {
                if (it.itemId == R.id.danmu_clear) {
                    interpreterList.remove(danmu)
                    interpreterViewAdapter.notifyDataSetInvalidated()
                }
                return@setOnMenuItemClickListener true
            }
            pop.show()
        }

        shadowView = findViewById(R.id.shadow_view)
        shadowFaceImg = findViewById(R.id.shadow_imageview)
        shadowTextView = findViewById(R.id.shadow_textview)

        playerNameBtn.text = "#${playerId+1}: 空"
        shadowTextView.text = "#${playerId+1}"

        // 点击窗口名称弹出菜单
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
                // 下面是工具栏宽度不足时，要呈现的菜单项
                if (it.itemId == R.id.refresh_btn) {
                    this.roomId = roomId
                }
                if (it.itemId == R.id.volume_btn) {
                    if (height < context.resources.displayMetrics.density * 130) {
                        val dialog = VolumeControlDialog(context)
                        dialog.title = "音量调节: ${playerNameBtn.text}"
                        dialog.onSeekBarListener = volumeChangedListener
                        dialog.volume = volumeSlider.progress
                        dialog.show()
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

        // 长按拖动功能
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

        // 接收拖放
        setOnDragListener { view, dragEvent ->
            if (dragEvent.action == DragEvent.ACTION_DROP) {
                Log.d("drop", dragEvent.clipData.description.label.toString())
                val label = dragEvent.clipData.description.label.toString()

                if (label == "layoutId") { // 从其他窗口拖放进来
                    val dragPid = dragEvent.clipData.getItemAt(0).text.toString().toInt()
                    Log.d("drop", "isSelf? $dragPid ${this.playerId}")
                    if (dragPid != this.playerId) { // 判断不是自己拖放给自己
                        Log.d("drop", "change $dragPid")
                        // 与其他窗口交换，交给上级ddlayout处理
                        onDragAndDropListener?.invoke(dragPid, this.playerId)
//                        this.playerId = dragPid
//                        showControlBar()
                    }
                }else if (label == "roomId") { // 从列表的卡片拖动进来
                    // roomId setter 开始播放
                    roomId = dragEvent.clipData.getItemAt(0).text.toString()
                    val face = dragEvent.clipData.getItemAt(1).text.toString()
                    try {
                        Picasso.get().load(face).transform(RoundImageTransform()).into(shadowFaceImg) // 用于拖动的头像view
                    }catch (e: Exception) {}
//                    Log.d("shadowFaceImg", shadowFaceImg)
                    onCardDropListener?.invoke()
                    context.getSharedPreferences("sp", AppCompatActivity.MODE_PRIVATE).edit {
                        this.putString("roomId${this@DDPlayer.playerId}", roomId).apply()
                    }
                }
//                Log.d("drop $playerId", dragEvent.clipData.getItemAt(0).text.toString())
            }
            // 拖动进入的时候显示一下东西
            if (dragEvent.action == DragEvent.ACTION_DRAG_ENTERED) {
                showControlBar()
            }
            return@setOnDragListener true
        }

        setOnClickListener {
            // 单击显示/隐藏工具条
            if (controlBar.visibility == VISIBLE) {
                controlBar.visibility = INVISIBLE
            }else{
                showControlBar()
            }
            volumeBar.visibility = INVISIBLE

            // 双击全屏
            if (System.currentTimeMillis() - doubleClickTime < 300) {
                doubleClickTime = 0
                Log.d("doubleclick", "doubleclick")
                onDoubleClickListener?.invoke(this.playerId) // 全屏需要刷新layout
            }else{
                doubleClickTime = System.currentTimeMillis()
            }
        }

        val typeface = Typeface.createFromAsset(context.assets, "iconfont.ttf")

        // 刷新按钮
        refreshBtn = findViewById<Button>(R.id.refresh_btn)
        refreshBtn.typeface = typeface
        refreshBtn.setOnClickListener {
            this.roomId = roomId
        }

        // 音量按钮
        volumeBtn = findViewById<Button>(R.id.volume_btn)
        volumeBtn.typeface = typeface
        volumeBtn.setOnClickListener {
            showControlBar()

            if (height < context.resources.displayMetrics.density * 130) {
                val dialog = VolumeControlDialog(context)
                dialog.title = "音量调节: ${playerNameBtn.text}"
                dialog.onSeekBarListener = volumeChangedListener
                dialog.volume = volumeSlider.progress
                dialog.show()
            }else{
                if (volumeBar.visibility == VISIBLE) {
                    volumeBar.visibility = INVISIBLE
                }else{
                    volumeBar.visibility = VISIBLE
                }
            }


        }
        // 弹幕按钮
        danmuBtn = findViewById(R.id.danmu_btn)
        danmuBtn.typeface = typeface
        danmuBtn.setOnClickListener {
            showDanmuDialog()
        }

//        volumeSlider.addOnChangeListener { slider, value, fromUser ->
//            player?.volume = if (isGlobalMuted) 0f else value/100f
//        }
        volumeSlider.setOnSeekBarChangeListener(volumeChangedListener)

        // 静音按钮
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

        // 画质按钮
        qnBtn.setOnClickListener {
            showQnMenu()
        }

        // 读取播放器设置
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
        val pop = PopupMenu(context, qnBtn)
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

    /**
     * roomId setter 设置后立即开始加载播放
     */
    var roomId: String? = null
        set(value) {
            if (field != value) {
                // 新的id则弹幕清屏
                danmuList.removeAll(danmuList)
//                danmuListView.invalidateViews()
                danmuListViewAdapter.notifyDataSetInvalidated()
            }
            field = value

            // 初始化播放器相关、弹幕socket相关的对象
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

            // set null 表示关闭了当前的播放 窗口置空
            if (value == null) {
                return
            }

            // 到这了就表示不为空了，开始加载

            playerNameBtn.text = "#${playerId+1}: 加载中"

            // 加载基础信息
            OkHttpClient().newCall(
                Request.Builder()
                    .url("https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom?room_id=$value")
//                    .addHeader("Connection", "close")
                    .build()
            ).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {

                }

                override fun onResponse(call: Call, response: Response) {
                    response.body?.let {
                        try {
                            val jo = JSONObject(it.string())
                            val data = jo.getJSONObject("data")
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
                                try {
                                    Picasso.get().load(face).transform(RoundImageTransform())
                                        .into(shadowFaceImg)
                                }catch (e: Exception) {
                                    shadowFaceImg.setImageDrawable(null)
                                }

                            }
                        }catch (e: Exception) {

                        }

                    }
                }

            })

            // 加载视频流信息
            OkHttpClient().newCall(
                    Request.Builder()
                        .url("https://api.live.bilibili.com/room/v1/Room/playUrl?cid=$value&platform=web&qn=$qn")
//                        .addHeader("Connection", "close")
                        .build()
            ).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {

                }

                override fun onResponse(call: Call, response: Response) {
                    response.body?.let { it2 ->
                        var url = ""
                        try {
                            url = JSONObject(it2.string())
                                .getJSONObject("data")
                                .getJSONArray("durl")
                                .getJSONObject(0)
                                .getString("url")
                        }catch (e: Exception) {

                        }
                        handler.post {
                            player = SimpleExoPlayer.Builder(context).build()
                            playerView.player = player
                            player!!.setMediaItem(MediaItem.fromUri(url))
                            player!!.volume =
                                if (isGlobalMuted) 0f else volumeSlider.progress.toFloat() / 100f
                            player!!.playWhenReady = true
                            player!!.prepare()
                        }

                    }
                }

            })

            // 连接弹幕socket
            socket = OkHttpClient.Builder().build().newWebSocket(Request.Builder()
                    .url(
                            "wss://broadcastlv.chat.bilibili.com:2245/sub"
                    ).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    super.onOpen(webSocket, response)
                    Log.d("danmu", "open")

                    // 连接成功，发送加入直播间的请求
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

                    // 开始心跳包发送
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

                        // 解压
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

                        val unzipped = bos.toByteArray()

                        // 解压后是多条json连在一条字符串里，可根据每一条json前面16个字节的头，获取到每条json的长度
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
                                val cmd = jobj.getString("cmd")
                                if (cmd == "DANMU_MSG") {
                                    val danmu = jobj.getJSONArray("info").getString(1)
                                    Log.d("danmu", "$value $danmu")
                                    handler.post {
                                        // 弹幕目前最多显示20条，是否要搞一个设置项？
                                        if (danmuList.count() > 20) {
                                            danmuList.removeFirst()
                                        }
                                        danmuList.add(danmu)
                                        danmuListViewAdapter.notifyDataSetInvalidated()
                                        danmuListView.setSelection(danmuListView.bottom)

                                        // 过滤同传弹幕
                                        if (danmu.contains("【")
                                                || danmu.contains("[")
                                                || danmu.contains("{")
                                        ) {
                                            if (interpreterList.count() > 20) {
                                                interpreterList.removeFirst()
                                            }
                                            interpreterList.add(danmu)
                                            interpreterViewAdapter.notifyDataSetInvalidated()
                                            interpreterListView.setSelection(interpreterListView.bottom)
                                        }
                                    }
                                }else if (cmd == "SUPER_CHAT_MESSAGE") {
                                    Log.d("SC", jobj.toString())
                                    val danmu = jobj.getJSONObject("data").getString("message")
                                    handler.post {
                                        if (danmuList.count() > 20) {
                                            danmuList.removeFirst()
                                        }
                                        danmuList.add("[SC] $danmu")
                                        danmuListViewAdapter.notifyDataSetInvalidated()
                                        danmuListView.setSelection(danmuListView.bottom)

                                        if (interpreterList.count() > 20) {
                                            interpreterList.removeFirst()
                                        }
                                        interpreterList.add("[SC] $danmu")
                                        interpreterViewAdapter.notifyDataSetInvalidated()
                                        interpreterListView.setSelection(interpreterListView.bottom)
                                    }

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
                    Log.d("danmu", "$roomId fail ${t.message}")
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

    // 宽高变化时调用，调整工具条，使得按钮隐藏，不超出宽度
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

    // 每当修改了播放器设置，做出相应的界面改变，然后保存设置
    fun notifyPlayerOptionsChange() {
        volumeSlider.progress = (playerOptions.volume * 100f).roundToInt()
        player?.volume = if (isGlobalMuted) 0f else playerOptions.volume

        danmuView.visibility = if (playerOptions.isDanmuShow) VISIBLE else GONE
        danmuListView.visibility = if (playerOptions.interpreterStyle == 2) GONE else VISIBLE
        interpreterListView.visibility = if (playerOptions.interpreterStyle == 0) GONE else VISIBLE
//        danmuListView.invalidateViews()
//        interpreterListView.invalidateViews()
        danmuListViewAdapter.notifyDataSetInvalidated()
        interpreterViewAdapter.notifyDataSetInvalidated()

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

    // 显示工具条，然后自动隐藏
    fun showControlBar() {
        controlBar.visibility = VISIBLE
        hideControlTimer?.cancel()
        hideControlTimer = null
        hideControlTimer = Timer()
        hideControlTimer!!.schedule(object : TimerTask() {
            override fun run() {
                if (!volumeAdjusting) {
                    Handler(Looper.getMainLooper()).post {
                        controlBar.visibility = INVISIBLE
                        volumeBar.visibility = INVISIBLE
                    }

                }

                hideControlTimer = null
            }
        }, 5000)
    }
}