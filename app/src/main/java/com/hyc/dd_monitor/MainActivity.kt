package com.hyc.dd_monitor

import android.content.ClipData
import android.content.ClipDescription
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.drawerlayout.widget.DrawerLayout
import com.hyc.dd_monitor.models.UPInfo
import com.hyc.dd_monitor.utils.RoundImageTransform
import com.hyc.dd_monitor.views.DDLayout
import com.hyc.dd_monitor.views.LayoutOptionsDialog
import com.squareup.picasso.Picasso
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    lateinit var ddLayout: DDLayout

    lateinit var uplist: MutableList<String>

    lateinit var upinfos: HashMap<String, UPInfo>

    lateinit var uplistview: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Log.d("orientation", "onCreate: ")

        val drawer = findViewById<DrawerLayout>(R.id.main_drawer)
        val drawerContent = findViewById<LinearLayout>(R.id.drawer_content)

        val dd = findViewById<LinearLayout>(R.id.stack_view)
        ddLayout = DDLayout(this)
        dd.addView(ddLayout)

        getSharedPreferences("sp", MODE_PRIVATE).getString("uplist", "")?.let {
            uplist = it.split(" ").toMutableList()
        }
        if (uplist.count() == 0) {
            uplist = mutableListOf("47377","8792912","21652717","47867")
        }


        upinfos = HashMap()

        uplistview = findViewById(R.id.up_list_view)
        uplistview.adapter = object : BaseAdapter() {
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

                val isLiveCover = view.findViewById<TextView>(R.id.up_islive_cover)


                val roomId = uplist[p0]

                if (upinfos.containsKey(roomId)) {
                    val upInfo = upinfos[roomId]
                    upInfo!!.coverImageUrl?.let {
                        Log.d("picasso", it)
                        Picasso.get().load(Uri.parse(it)).into(cover)
                    }
                    upInfo.faceImageUrl?.let {
                        Picasso.get().load(Uri.parse(it)).transform(RoundImageTransform()).into(face)
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
                        isLiveCover.text = "未开播"
                    }
                }else{
//                    loadUpInfo(roomId)
                }

                return view
            }

        }
        uplistview.setOnItemLongClickListener { adapterView, view, i, l ->
            Log.d("long click", i.toString())
            view.startDragAndDrop(
                ClipData("roomId", arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), ClipData.Item(uplist[i])),
                View.DragShadowBuilder(view), null, View.DRAG_FLAG_GLOBAL
            )
            drawer.closeDrawers()
            return@setOnItemLongClickListener true
        }

        findViewById<Button>(R.id.about_btn).setOnClickListener {
            Toast.makeText(this, "功能待完善，目前版本0.1.0", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.landscape_btn).setOnClickListener {

//            for (p in ddLayout.players) {
//                p.player?.stop()
//            }

            requestedOrientation = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_USER
            }else{
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }

        findViewById<Button>(R.id.layout_btn).setOnClickListener {
            val dialog = LayoutOptionsDialog(this)
            dialog.onLayoutOptionsSelectedListener = {
                ddLayout.layoutId = it
            }
            dialog.show()
        }

        findViewById<Button>(R.id.uplist_btn).setOnClickListener {
            drawer.openDrawer(drawerContent)
            for (up in uplist) {
                loadUpInfo(up)
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

                        uplist.add(0, realRoomId)
                        val editor = getSharedPreferences("sp", MODE_PRIVATE).edit {
                            this.putString("uplist", uplist.joinToString(" ")).apply()
                        }
//                        Log.d("getSharedPreferences", uplist.joinToString(" "))
//                        editor.putString("uplist", uplist.joinToString(" "))
//                        editor.commit()
                        runOnUiThread {
                            uplistview.invalidateViews()
                        }
                    }
                }
                .setNegativeButton("取消",null)
                .show()
        }

    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("orientation", "onConfigurationChanged")
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

                        finished?.invoke(realRoomId)

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

                        if (uplist.indexOf(roomId) == uplist.count() - 1) {
                            uplist.sortByDescending { id ->
                                if (upinfos.containsKey(id)) upinfos[id]?.isLive else false
                            }
                        }


                        runOnUiThread {
                            uplistview.invalidateViews()
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

}