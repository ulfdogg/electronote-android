package de.graetz.electronote.diagram

import org.json.JSONObject
import java.util.UUID

data class DiagramConnection(
    val id: String = UUID.randomUUID().toString(),
    var fromNodeId: String,
    var toNodeId: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("fromNodeId", fromNodeId)
        put("toNodeId", toNodeId)
    }

    companion object {
        fun fromJson(obj: JSONObject): DiagramConnection = DiagramConnection(
            id = obj.optString("id", UUID.randomUUID().toString()),
            fromNodeId = obj.optString("fromNodeId", ""),
            toNodeId = obj.optString("toNodeId", "")
        )
    }
}
