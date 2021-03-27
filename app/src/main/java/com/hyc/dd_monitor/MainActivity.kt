package com.hyc.dd_monitor

import android.content.*
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.text.InputType
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.drawerlayout.widget.DrawerLayout
import com.hyc.dd_monitor.models.UPInfo
import com.hyc.dd_monitor.utils.RoundImageTransform
import com.hyc.dd_monitor.views.DDLayout
import com.hyc.dd_monitor.views.DanmuOptionsDialog
import com.hyc.dd_monitor.views.LayoutOptionsDialog
import com.squareup.picasso.Picasso
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.util.*
import kotlin.collections.HashMap

class MainActivity : AppCompatActivity() {

    lateinit var ddLayout: DDLayout

    lateinit var uplist: MutableList<String>

    lateinit var upinfos: HashMap<String, UPInfo>

    lateinit var uplistview: ListView
    lateinit var uplistviewAdapter: BaseAdapter

    lateinit var cancelDragView: TextView

    lateinit var drawer: DrawerLayout
    lateinit var drawerContent: LinearLayout

    lateinit var volumeBtn: Button

    var isGlobalMuted = false

    var autoSleepMinutes: Int = 0
    var autoSleepTimer: Timer? = null

    lateinit var timerTextView: TextView

//    var isLiveMap: HashMap<String, Boolean> = HashMap()
//    var roomIdToCheck: String? = null

    var lastClipboard: String? = null
    override fun onResume() {
        super.onResume()
        Log.d("resume", "onResume")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        window.decorView.post {
//            for (p in 0 until ddLayout.layoutPlayerCount) {
//                ddLayout.players[p].adjustControlBar()
//            }

            (getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager)?.let {
                it.primaryClip?.let { clip ->
                    Log.d("clipboard", clip.itemCount.toString())
                    if (clip.itemCount == 0) return@post
                    clip.getItemAt(0)?.let { item ->
                        if (item.text == null) return@post
                        val clipboard = item.text.toString()
                        if (clipboard == lastClipboard) {
                            return@post
                        }
                        Log.d("clipboard", clipboard)
                        lastClipboard = clipboard
                        try {
                            val url = URL(clipboard)
                            if (url.host == "live.bilibili.com") {
                                Log.d("clipboard", url.path)
                                val urlId = url.path.replace("/", "")
                                urlId.toIntOrNull()?.let { roomId ->
                                    AlertDialog.Builder(this)
                                        .setTitle("添加id $roomId ?")
                                        .setPositiveButton("是") { _, _ ->
                                            if (uplist.contains(roomId.toString())) {
                                                Toast.makeText(this, "已存在", Toast.LENGTH_SHORT).show()
                                                return@setPositiveButton
                                            }
                                            loadUpInfo(roomId.toString()) { realRoomId ->
                                                runOnUiThread {
                                                    if (uplist.contains(realRoomId)) {
                                                        Toast.makeText(this, "已存在", Toast.LENGTH_SHORT).show()
                                                        return@runOnUiThread
                                                    }

                                                    uplist.add(0, realRoomId)
                                                    getSharedPreferences("sp", MODE_PRIVATE).edit {
                                                        this.putString("uplist", uplist.joinToString(" ")).apply()
                                                    }
                                                    uplistview.invalidateViews()
                                                    drawer.openDrawer(drawerContent)
                                                    for (up in uplist) {
                                                        loadUpInfo(up)
                                                    }
                                                }
                                            }
                                        }
                                        .setNegativeButton("否", null)
                                        .show()
                                    it.setPrimaryClip(ClipData.newPlainText("",""))
                                }
                            }
                        }catch (_:Exception) {}
                    }
                }
            }
        }



    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d("orientation", "onCreate: ")

        ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE,android.Manifest.permission.READ_EXTERNAL_STORAGE), 111)

        drawer = findViewById(R.id.main_drawer)
        drawerContent = findViewById(R.id.drawer_content)

        val dd = findViewById<LinearLayout>(R.id.stack_view)
        ddLayout = DDLayout(this)
        ddLayout.onCardDropListener = {
            cancelDragView.visibility = View.GONE
        }
        dd.addView(ddLayout)

        getSharedPreferences("sp", MODE_PRIVATE).getString("uplist", "")?.let {
            uplist = it.split(" ").toMutableList()
            Log.d("uplist", it)
        }
        Log.d("uplist", uplist[0])
        if (uplist.count() == 0 || uplist[0].isEmpty()) {
            uplist = mutableListOf("47377","8792912","21652717","47867")
        }


        upinfos = HashMap()

        uplistview = findViewById(R.id.up_list_view)
        uplistviewAdapter = object : BaseAdapter() {
            override fun getCount(): Int {
                return uplist.count()
            }

            override fun getItem(p0: Int): Any {
                return p0
            }

            override fun getItemId(p0: Int): Long {
                return p0.toLong()
            }

            override fun getView(p0: Int, p1: View?, p2: ViewGroup?): View {
                val view = p1 ?: View.inflate(this@MainActivity, R.layout.list_item_up, null)

                val cover = view.findViewById<ImageView>(R.id.up_cover_image)
                val face = view.findViewById<ImageView>(R.id.up_face_image)
                val uname = view.findViewById<TextView>(R.id.up_uname_textview)
                val title = view.findViewById<TextView>(R.id.up_title_textview)
                val shadow = view.findViewById<ImageView>(R.id.shadow_imageview)

                val isLiveCover = view.findViewById<TextView>(R.id.up_islive_cover)


                val roomId = uplist[p0]

                if (upinfos.containsKey(roomId)) {
                    val upInfo = upinfos[roomId]
                    upInfo!!.coverImageUrl?.let {
                        Log.d("picasso", it)
                        Picasso.get().load(it).into(cover)
                    }
                    upInfo.faceImageUrl?.let {
                        Picasso.get().load(it).transform(RoundImageTransform()).into(face)
                        Picasso.get().load(it).transform(RoundImageTransform()).into(shadow)
                    }
                    if (upInfo.uname != null) {
                        uname.text = upInfo.uname
                        uname.setBackgroundColor(Color.TRANSPARENT)
                    }
                    if (upInfo.title != null) {
                        title.text = upInfo.title
                        title.setBackgroundColor(Color.TRANSPARENT)
                    }

                    if (upInfo.isLive) {
                        isLiveCover.visibility = View.GONE
                    }else{
                        isLiveCover.visibility = View.VISIBLE
                        isLiveCover.text = "未开播"
                    }
                }

                return view
            }

        }
        uplistview.adapter = uplistviewAdapter

        cancelDragView = findViewById(R.id.cancel_drag_view)
        cancelDragView.setOnDragListener { view, dragEvent ->
            if (dragEvent.action == DragEvent.ACTION_DROP) {
                cancelDragView.visibility = View.GONE
            }
            if (dragEvent.action == DragEvent.ACTION_DRAG_ENTERED) {
                cancelDragView.setBackgroundColor(resources.getColor(R.color.teal_200, theme))
            }
            if (dragEvent.action == DragEvent.ACTION_DRAG_EXITED) {
                cancelDragView.setBackgroundColor(resources.getColor(R.color.teal_700, theme))
            }
            return@setOnDragListener true
        }

        // 卡片拖拽
        uplistview.setOnItemLongClickListener { adapterView, view, i, l ->
            Log.d("long click", i.toString())

            val clipData = ClipData("roomId", arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), ClipData.Item(uplist[i]))
            clipData.addItem(ClipData.Item(upinfos[uplist[i]]?.faceImageUrl))

            view.startDragAndDrop(clipData, View.DragShadowBuilder(view.findViewById(R.id.shadow_imageview)), null, View.DRAG_FLAG_GLOBAL)

            drawer.closeDrawers()
            cancelDragView.visibility = View.VISIBLE
            cancelDragView.setBackgroundColor(resources.getColor(R.color.teal_700, theme))
            return@setOnItemLongClickListener true
        }

        uplistview.setOnItemClickListener { adapterView, view, i, l ->
            val pop = PopupMenu(this, view)
            pop.menuInflater.inflate(R.menu.up_item_card, pop.menu)
            pop.setOnMenuItemClickListener {
                if (it.itemId == R.id.delete_item) {
                    Log.d("menu", "delete")
                    uplist.removeAt(i)
//                    uplistview.invalidateViews()
                    uplistviewAdapter.notifyDataSetInvalidated()
                    getSharedPreferences("sp", MODE_PRIVATE).edit {
                        this.putString("uplist", uplist.joinToString(" ")).apply()
                    }
                }
                if (it.itemId == R.id.open_live) {
                    try {
                        val intent = Intent()
                        intent.data = Uri.parse("bilibili://live/${uplist[i]}")
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    }catch (_:Exception) {
                        val intent = Intent()
                        intent.data = Uri.parse("https://live.bilibili.com/${uplist[i]}")
                        startActivity(intent)
                    }
                }
                return@setOnMenuItemClickListener true
            }
            pop.show()

        }

        val typeface = Typeface.createFromAsset(assets, "iconfont.ttf")

        val refreshBtn = findViewById<Button>(R.id.refresh_btn)
        refreshBtn.typeface = typeface
        refreshBtn.setOnClickListener {
            ddLayout.reloadLayout()
        }

        volumeBtn = findViewById<Button>(R.id.volume_btn)
        volumeBtn.typeface = typeface
        volumeBtn.setOnClickListener {
            isGlobalMuted = !isGlobalMuted
            for (p in ddLayout.players) {
                p.isGlobalMuted = isGlobalMuted
            }
            volumeBtn.text = if (isGlobalMuted) "\ue607" else "\ue606"
        }

        val danmuBtn = findViewById<Button>(R.id.danmu_btn)
        danmuBtn.typeface = typeface
        danmuBtn.setOnClickListener {
            val dialog = DanmuOptionsDialog(this, null)
            dialog.onDanmuOptionsChangeListener = {
                for (p in ddLayout.players) {
                    p.playerOptions = it
                    p.notifyPlayerOptionsChange()
                }
            }
            dialog.show()
        }

        val qnBtn = findViewById<Button>(R.id.qn_btn)
        qnBtn.setOnClickListener {
            val pop = PopupMenu(this, qnBtn)
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

                for (p in ddLayout.players) {
                    p.qn = newQn
                }
                return@setOnMenuItemClickListener true
            }
            pop.show()
        }

        val aboutBtn = findViewById<Button>(R.id.about_btn)
        aboutBtn.typeface = typeface
        aboutBtn.setOnClickListener {
            val ver = packageManager.getPackageInfo(packageName, 0).versionName
            AlertDialog.Builder(this)
                .setTitle("DD监控室 v${ver} by CongHu")
                .setMessage("· 点击右上角“UP”按钮添加UP主，长按拖动到播放器窗口内。\n· 观看多个直播时请注意带宽网速、流量消耗、电池电量、机身发热、系统卡顿等软硬件环境问题。\n· 本软件仅读取公开API数据，不涉及账号登录，欢迎查看源码进行监督。因此，本软件不支持弹幕互动、直播打赏等功能，若要使用请前往原版B站APP。")
                .setNegativeButton("关闭", null)
                .show()
        }

        timerTextView = findViewById(R.id.timer_textview)

        val timerBtn = findViewById<Button>(R.id.timer_btn)
        timerBtn.typeface = typeface
        timerBtn.setOnClickListener {
            val pop = PopupMenu(this, timerBtn)
            pop.menuInflater.inflate(R.menu.timer_menu, pop.menu)
            pop.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.timer_set_15 -> autoSleepTimerSet(15)
                    R.id.timer_set_30 -> autoSleepTimerSet(30)
                    R.id.timer_set_60 -> autoSleepTimerSet(60)
                    R.id.timer_cancel -> autoSleepTimerSet(0)
                    R.id.timer_custom -> {
                        val et = EditText(this)
                        et.inputType = InputType.TYPE_CLASS_NUMBER
                        AlertDialog.Builder(this)
                            .setTitle("定时关闭（分钟）")
                            .setView(et)
                            .setPositiveButton("确定") { _, _ ->
                                et.text.toString().toIntOrNull()?.let { min ->
                                    if (min in 1..99)
                                        autoSleepTimerSet(min)
                                }
                            }
                            .setNegativeButton("取消",null)
                            .show()
                        et.requestFocus()
                    }
                }
                return@setOnMenuItemClickListener true
            }
            pop.show()
        }

        val landScapeBtn = findViewById<Button>(R.id.landscape_btn)
        landScapeBtn.typeface = typeface
        landScapeBtn.setOnClickListener {

//            for (p in ddLayout.players) {
//                p.player?.stop()
//            }

            requestedOrientation = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_USER
            }else{
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }

        val layoutBtn = findViewById<Button>(R.id.layout_btn)
        layoutBtn.typeface = typeface
        layoutBtn.setOnClickListener {
            val dialog = LayoutOptionsDialog(this)
            dialog.onLayoutOptionsSelectedListener = {
                if (ddLayout.layoutBeforeFullScreen != null) {
                    val target = ddLayout.players[ddLayout.fullScreenPlayerId!!]
                    ddLayout.players[ddLayout.fullScreenPlayerId!!] = ddLayout.players[0]
                    ddLayout.players[0] = target
                    ddLayout.fullScreenPlayerId = null
                    ddLayout.layoutBeforeFullScreen = null
                }
                ddLayout.layoutId = it
                getSharedPreferences("sp", MODE_PRIVATE).edit {
                    this.putInt("layout", it).apply()
                }

            }
            dialog.show()
        }

        findViewById<Button>(R.id.uplist_btn).setOnClickListener {
//            throw java.lang.Exception("oopss")
            drawer.openDrawer(drawerContent)
            for (up in 0 until uplist.count()) {
                loadUpInfo(uplist[up]) {
                    if (it == uplist.last()) {
                        uplist.sortByDescending { id ->
                            upinfos[id]?.isLive
                        }
                        Log.d("sort", "sort")
                        runOnUiThread {
//                            uplistview.invalidateViews()
                            uplistviewAdapter.notifyDataSetInvalidated()
                        }
                    }

                }
            }

        }

        findViewById<Button>(R.id.add_up_btn).setOnClickListener {
            val et = EditText(this)
            et.inputType = InputType.TYPE_CLASS_NUMBER
            AlertDialog.Builder(this)
                .setTitle("直播间id")
                .setView(et)
                .setPositiveButton("确定") { _, _ ->
                    val roomId = et.text.toString().toIntOrNull()
                    if (roomId == null) {
                        Toast.makeText(this@MainActivity, "无效的id", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    if (uplist.contains(roomId.toString())) {
                        Toast.makeText(this@MainActivity, "已存在", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    loadUpInfo(roomId.toString()) { realRoomId ->
                        if (uplist.contains(realRoomId)) {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "已存在", Toast.LENGTH_SHORT).show()
                            }
                            return@loadUpInfo
                        }


//                        Log.d("getSharedPreferences", uplist.joinToString(" "))
//                        editor.putString("uplist", uplist.joinToString(" "))
//                        editor.commit()
                        runOnUiThread {
                            uplist.add(0, realRoomId)
                            getSharedPreferences("sp", MODE_PRIVATE).edit {
                                this.putString("uplist", uplist.joinToString(" ")).apply()
                            }
//                            uplistview.invalidateViews()
                            uplistviewAdapter.notifyDataSetInvalidated()
                        }
                    }
                }
                .setNegativeButton("取消",null)
                .show()
//            et.requestFocus()
        }

//        val timer = Timer()
//        timer.schedule(object : TimerTask() {
//            override fun run() {
//                if (roomIdToCheck == null) {
//                    roomIdToCheck = uplist[0].
//                }
//                loadUpInfo(roomIdToCheck) {
//                    if (isLiveMap.containsKey(up)) {
//                        if (isLiveMap[up] == false && upinfos[up]?.isLive == true) {
//                            runOnUiThread {
//                                Log.d("isLive", "${upinfos[up]?.uname ?: "?"} 开播了")
//                                Toast.makeText(this@MainActivity, "${upinfos[up]?.uname ?: "?"} 开播了", Toast.LENGTH_SHORT).show()
//                            }
//                        }
//                    }else{
//                        isLiveMap[up] = upinfos[up]?.isLive ?: false
//                    }
//
//                }
//            }
//        }, 10000, 10000)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("orientation", "onConfigurationChanged")
        for (p in 0 until ddLayout.layoutPlayerCount) {
            ddLayout.post {
                ddLayout.players[p].adjustControlBar()
            }
        }
    }

    fun loadUpInfo(roomId: String, finished: ((realRoomId: String) -> Unit)? = null) {
        OkHttpClient().newCall(Request.Builder()
            .url("https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom?room_id=$roomId").build()
        ).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {

            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.let {
                    val jo = JSONObject(it.string())
                    jo.optJSONObject("data")?.let { data ->

                        val roomInfo = data.getJSONObject("room_info")
                        val anchorInfo = data.getJSONObject("anchor_info").getJSONObject("base_info")

                        val realRoomId = roomInfo.getInt("room_id").toString()


                        val upInfo = UPInfo()
                        upInfo.title = roomInfo.getString("title")
                        var keyframe = roomInfo.getString("keyframe")
                        if (keyframe.startsWith("http://")) {
                            keyframe = keyframe.replace("http://", "https://")
                        }
                        upInfo.coverImageUrl = keyframe
                        upInfo.isLive = roomInfo.getInt("live_status") == 1
                        upInfo.uname = anchorInfo.getString("uname")
                        var face = anchorInfo.getString("face")
                        if (face.startsWith("http://")) {
                            face = face.replace("http://", "https://")
                        }
                        upInfo.faceImageUrl = face

                        upinfos[realRoomId] = upInfo
                        finished?.invoke(realRoomId)

//                        if (uplist.indexOf(roomId) == uplist.count() - 1) {
//                            uplist.sortByDescending { id ->
//                                if (upinfos.containsKey(id)) upinfos[id]?.isLive else false
//                            }
//                        }


                        runOnUiThread {
//                            uplistview.invalidateViews()
                            uplistviewAdapter.notifyDataSetInvalidated()
                        }

                        return
                    }

                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "查询id失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }

        })
    }

    fun autoSleepTimerSet(min: Int) {
        autoSleepMinutes = min
        autoSleepTimer?.cancel()
        autoSleepTimer = null
        if (autoSleepMinutes <= 0) {
            timerTextView.text = ""
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            return
        }
        timerTextView.text = "$autoSleepMinutes"


        autoSleepTimer = Timer()
        autoSleepTimer!!.schedule(object : TimerTask() {
            override fun run() {
                autoSleepMinutes -= 1
                runOnUiThread {
                    timerTextView.text = "$autoSleepMinutes"
                }
                if (autoSleepMinutes == 0) {
                    runOnUiThread {
                        timerTextView.text = ""
                        autoSleepTimer?.cancel()
                        for (p in ddLayout.players) {
                            p.player?.pause()
                        }
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        Toast.makeText(this@MainActivity, "已恢复系统自动锁屏", Toast.LENGTH_SHORT).show()
                    }

                }
            }

        }, 60000, 60000)
    }

    override fun onDestroy() {
        super.onDestroy()
        for (p in ddLayout.players) {
            p.player?.stop()
        }
    }

    var backPressTime: Long = 0
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (System.currentTimeMillis() - backPressTime > 2000) {
                Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show()
                backPressTime = System.currentTimeMillis()
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }
}