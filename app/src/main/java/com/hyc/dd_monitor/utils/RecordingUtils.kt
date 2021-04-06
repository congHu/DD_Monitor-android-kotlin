package com.hyc.dd_monitor.utils

class RecordingUtils {
    companion object {
        fun byteString(len: Long) : String {
            if (len < 1024) return "${len}B"
            if (len < 1024*1024) return "${len/1024}K"
            if (len < 100*1024*1024) return String.format("%.1fM", len.toFloat()/1024f/1024f)
            return "${len/1024/1024}M"
        }

        fun minuteString(second: Long) : String {
            val min = second / 60
            val sec = second % 60
            val secStr = if (sec >= 10) "$sec" else "0$sec"
            return "$min:$secStr"
        }
    }
}