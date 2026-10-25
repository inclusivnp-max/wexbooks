package client.wexbooks.reader.nativeengine

/** Reads only PNG/JPEG headers, without allocating a decoded image or Android objects. */
object NativeImageBounds {
    fun read(bytes: ByteArray): Pair<Int, Int>? {
        fun u8(i: Int) = bytes[i].toInt() and 255
        fun u16(i: Int) = (u8(i) shl 8) or u8(i + 1)
        fun u32(i: Int) = (u16(i).toLong() shl 16) or u16(i + 2).toLong()
        fun valid(w: Long, h: Long): Pair<Int, Int>? =
            if (w in 1..16_384 && h in 1..16_384 && w * h <= 64_000_000) w.toInt() to h.toInt()
            else null
        if (
            bytes.size >= 24 &&
                bytes
                    .copyOfRange(0, 8)
                    .contentEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)) &&
                String(bytes, 12, 4, Charsets.US_ASCII) == "IHDR"
        )
            return valid(u32(16), u32(20))
        if (bytes.size < 4 || u16(0) != 0xffd8) return null
        var i = 2
        while (i + 3 < bytes.size) {
            if (u8(i) != 255) return null
            while (i < bytes.size && u8(i) == 255) i++
            if (i + 2 >= bytes.size) return null
            val marker = u8(i++)
            if (marker == 0xd9 || marker == 0xda) return null
            if (marker == 0x01 || marker in 0xd0..0xd8) continue
            val length = u16(i)
            if (length < 2 || i.toLong() + length > bytes.size) return null
            if (
                marker in
                    setOf(
                        0xc0,
                        0xc1,
                        0xc2,
                        0xc3,
                        0xc5,
                        0xc6,
                        0xc7,
                        0xc9,
                        0xca,
                        0xcb,
                        0xcd,
                        0xce,
                        0xcf,
                    ) && length >= 7
            )
                return valid(u16(i + 5).toLong(), u16(i + 3).toLong())
            i += length
        }
        return null
    }
}
