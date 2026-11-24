package com.ice.nihongo.media

/** 邊解碼邊降取樣成 16kHz，避免整首歌的原始音訊塞爆記憶體 */
class FloatBuffer16k {
    var rate = 16_000
    private var data = FloatArray(16_000 * 60)
    private var size = 0
    private var pos = 0.0     // 目前在輸出時間軸上的位置
    private var prev = 0f

    fun add(sample: Float) {
        // 簡單線性內插：每收到一個輸入樣本，往前推進 16000/rate 個輸出位置
        val step = 16_000.0 / rate
        val next = pos + step
        var t = Math.ceil(pos).toLong()
        while (t < next) {
            val frac = ((t - pos) / step).toFloat()
            put(prev + (sample - prev) * frac)
            t++
        }
        pos = next
        prev = sample
    }

    private fun put(v: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }

    fun toArray(): FloatArray = data.copyOf(size)
}
