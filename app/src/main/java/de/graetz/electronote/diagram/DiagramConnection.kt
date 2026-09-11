package de.graetz.electronote.diagram

import org.json.JSONObject
import java.util.UUID

enum class DiagramPort { TOP, BOTTOM, LEFT, RIGHT }

data class DiagramConnection(
    val id: String = UUID.randomUUID().toString(),
    var fromNodeId: String,
    var toNodeId: String,
    // PAP: "ja"/"nein"/"wahr"/"falsch"/"überspringen" etc. Unused (but harmless) for MindMap.
    var label: String = "",
    var fromPort: DiagramPort = DiagramPort.BOTTOM
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("fromNodeId", fromNodeId)
        put("toNodeId", toNodeId)
        put("label", label)
        put("fromPort", fromPort.name)
    }

    companion object {
        fun fromJson(obj: JSONObject): DiagramConnection {
            val port = try {
                DiagramPort.valueOf(obj.optString("fromPort", DiagramPort.BOTTOM.name))
            } catch (e: IllegalArgumentException) {
                DiagramPort.BOTTOM
            }
            return DiagramConnection(
                id = obj.optString("id", UUID.randomUUID().toString()),
                fromNodeId = obj.optString("fromNodeId", ""),
                toNodeId = obj.optString("toNodeId", ""),
                label = obj.optString("label", ""),
                fromPort = port
            )
        }
    }
}
