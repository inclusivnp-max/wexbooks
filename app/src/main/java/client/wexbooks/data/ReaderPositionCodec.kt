package client.wexbooks.data

import client.wexbooks.reader.core.ReaderPosition
import org.json.JSONObject

object ReaderPositionCodec {
    fun encode(position: ReaderPosition): String =
        JSONObject()
            .apply {
                put("v", 1)
                put("resource", position.resource)
                put("block", position.block)
                put("offset", position.offset)
                put("progression", position.progression)
                position.payload?.let { put("payload", it) }
            }
            .toString()

    fun decode(value: String?): ReaderPosition? = value?.let {
        runCatching {
            val json = JSONObject(it)
            if (json.has("href"))
                ReaderPosition(
                    json.getString("href"),
                    progression =
                        json.optJSONObject("locations")?.optDouble("totalProgression", 0.0) ?: 0.0,
                    payload = it,
                )
            else
                ReaderPosition(
                    json.getString("resource"),
                    json.optInt("block", 0),
                    json.optInt("offset", 0),
                    json.optDouble("progression", 0.0).coerceIn(0.0, 1.0),
                    json.optString("payload").takeIf(String::isNotBlank),
                )
        }
            .getOrNull()
    }
}
