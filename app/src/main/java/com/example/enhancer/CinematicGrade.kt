package com.example.enhancer

/**
 * تدرّج لوني سينمائي: منحنى تباين S، ظلال مائلة للتركواز، إضاءات دافئة،
 * تشبّع خفيف، وسواد "مُبهت" على طريقة الفيلم.
 * نفس الدالة تُستخدم للصور (لكل بكسل) وللفيديو (عبر جدول 3D LUT).
 */
object CinematicGrade {

    fun apply(r0: Float, g0: Float, b0: Float, k: Float, out: FloatArray) {
        val c = 0.45f * k
        var r = curve(r0, c)
        var g = curve(g0, c)
        var b = curve(b0, c)

        val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val shadow = (1f - luma) * (1f - luma)
        val high = luma * luma

        r += -0.05f * k * shadow + 0.06f * k * high
        g += 0.010f * k * shadow + 0.015f * k * high
        b += 0.06f * k * shadow - 0.06f * k * high

        val l2 = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val sat = 1f + 0.14f * k
        r = l2 + (r - l2) * sat
        g = l2 + (g - l2) * sat
        b = l2 + (b - l2) * sat

        val lift = 0.012f * k
        r = lift + r * (1f - lift)
        g = lift + g * (1f - lift)
        b = lift + b * (1f - lift)

        out[0] = r.coerceIn(0f, 1f)
        out[1] = g.coerceIn(0f, 1f)
        out[2] = b.coerceIn(0f, 1f)
    }

    private fun curve(x: Float, c: Float): Float {
        val u = 2f * x - 1f
        return x + c * (x - 0.5f) * (1f - u * u)
    }

    /** مكعب LUT بحجم size^3 بصيغة [r][g][b] بقيم ARGB مُعبّأة في Int. */
    fun buildCube(size: Int, strength: Float): Array<Array<IntArray>> {
        val tmp = FloatArray(3)
        val maxIdx = (size - 1).toFloat()
        return Array(size) { ri ->
            Array(size) { gi ->
                IntArray(size) { bi ->
                    apply(ri / maxIdx, gi / maxIdx, bi / maxIdx, strength, tmp)
                    val rr = (tmp[0] * 255f + 0.5f).toInt()
                    val gg = (tmp[1] * 255f + 0.5f).toInt()
                    val bb = (tmp[2] * 255f + 0.5f).toInt()
                    (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
                }
            }
        }
    }
}
