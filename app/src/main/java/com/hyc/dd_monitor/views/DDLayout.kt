package com.hyc.dd_monitor.views

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit

class DDLayout(context: Context?) : LinearLayout(context) {
    var layoutId : Int = 4
        set(value) {
            field = value
            reloadLayout()
        }

    var players: ArrayList<DDPlayer>

    var onCardDropListener: (() -> Unit)? = null

    var onPlayerClickListener: (() -> Unit)? = null

    var layoutPlayerCount = 0

    var fullScreenPlayerId: Int? = null
    var layoutBeforeFullScreen: Int? = null

    init {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
//        this.layoutId = 2


        this.players = ArrayList()

        // 开始就把最多9个对象准备好，交换窗口时无缝切换
        for (i in 0..8) {
            val p = DDPlayer(context!!, i)

            // 处理窗口交换
            p.onDragAndDropListener = { drag, drop ->
                Log.d("drop", "drag drop $drag $drop")
                val dragViewId = context.resources.getIdentifier(
                    "dd_layout_${drag+1}",
                    "id",
                    context.packageName
                )
                val dropViewId = context.resources.getIdentifier(
                    "dd_layout_${drop+1}",
                    "id",
                    context.packageName
                )
                val dragView = stackview?.findViewById<LinearLayout>(dragViewId)
                val dropView = stackview?.findViewById<LinearLayout>(dropViewId)
                (players[drop].parent as ViewGroup?)?.removeView(players[drop])
                dragView?.addView(players[drop])
                (players[drag].parent as ViewGroup?)?.removeView(players[drag])
                dropView?.addView(players[drag])
                players[drag].playerId = drop
                players[drop].playerId = drag

                val temp = players[drag]
                players[drag] = players[drop]
                players[drop] = temp

                // 在post里面重新获取宽度调整工具栏，因为post之后获取的才是正确的宽度
                post {
                    players[drag].adjustControlBar()
                    players[drop].adjustControlBar()
                }

                // 交换音量设置
                val volume2 = players[drop].playerOptions.volume
                players[drop].playerOptions.volume = players[drag].playerOptions.volume
                players[drop].notifyPlayerOptionsChange()
                players[drag].playerOptions.volume = volume2
                players[drag].notifyPlayerOptionsChange()

                context.getSharedPreferences("sp", AppCompatActivity.MODE_PRIVATE).edit {
                    this.putString("roomId$drop", players[drop].roomId).apply()
                    this.putString("roomId$drag", players[drag].roomId).apply()
                }
            }
            p.onDoubleClickListener = {
                if (layoutBeforeFullScreen == null) {
                    val target = players[it]
                    players[it] = players[0]
                    players[0] = target
                    fullScreenPlayerId = it
                    layoutBeforeFullScreen = layoutId
                    layoutId = 1
                }else{
                    val target = players[fullScreenPlayerId!!]
                    players[fullScreenPlayerId!!] = players[0]
                    players[0] = target
                    layoutId = layoutBeforeFullScreen!!
                    fullScreenPlayerId = null
                    layoutBeforeFullScreen = null
                }


            }
            p.onCardDropListener = {
                onCardDropListener?.invoke()
            }
            p.onPlayerClickListener = {
                Log.d("ddclick", "ddclick")
                onPlayerClickListener?.invoke()
            }
            this.players.add(p)
        }

//        reloadLayout()

        context?.getSharedPreferences("sp", AppCompatActivity.MODE_PRIVATE)?.getInt("layout", 4)?.let {
            this.layoutId = it
        }
    }

    var stackview: View? = null

    // 重新刷新布局
    fun reloadLayout() {
        if (stackview != null) {
            removeView(stackview)
        }

        Log.d("ffffff", "dd_layout_$layoutId")
        val resId = context.resources.getIdentifier("dd_layout_$layoutId", "layout", context.packageName)
        stackview = inflate(context, resId, null)
        stackview?.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        addView(stackview)

        layoutPlayerCount = 0

        for (i in 1..9) {
            val layoutId = context.resources.getIdentifier("dd_layout_$i", "id", context.packageName)
            val v = stackview?.findViewById<LinearLayout>(layoutId)
            val p = players[i-1]
            (p.parent as ViewGroup?)?.removeView(p)
            v?.addView(p)

            if (v != null) {
//                if (fullScreenPlayerId != null && p.roomId != null) { // 判断是否双击全屏触发的
//                    p.roomId = p.roomId
//                }else
                if (p.roomId == null) {
                    p.roomId = context?.getSharedPreferences("sp", AppCompatActivity.MODE_PRIVATE)?.getString("roomId${p.playerId}", null)
                }

                post {
                    p.adjustControlBar()
                }

                layoutPlayerCount += 1
            }else{
                p.roomId = null
            }
        }

    }


    // 刷新全部
    fun refreshAll() {
        for (i in 0 until layoutPlayerCount) {
            val p = players[i]
            p.roomId = p.roomId
        }
    }
}