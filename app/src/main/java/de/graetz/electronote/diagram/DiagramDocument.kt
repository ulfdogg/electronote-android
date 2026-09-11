package de.graetz.electronote.diagram

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class DiagramDocument(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var type: String,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var nodes: MutableList<DiagramNode> = mutableListOf(),
    var connections: MutableList<DiagramConnection> = mutableListOf()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("nodes", JSONArray(nodes.map { it.toJson() }))
        put("connections", JSONArray(connections.map { it.toJson() }))
    }

    companion object {
        const val TYPE_PAP = "pap"
        const val TYPE_MINDMAP = "mindmap"

        fun fromJson(obj: JSONObject): DiagramDocument {
            val nodesArr = obj.optJSONArray("nodes") ?: JSONArray()
            val nodes = mutableListOf<DiagramNode>()
            for (i in 0 until nodesArr.length()) nodes.add(DiagramNode.fromJson(nodesArr.getJSONObject(i)))

            val connArr = obj.optJSONArray("connections") ?: JSONArray()
            val connections = mutableListOf<DiagramConnection>()
            for (i in 0 until connArr.length()) connections.add(DiagramConnection.fromJson(connArr.getJSONObject(i)))

            return DiagramDocument(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "Diagramm"),
                type = obj.optString("type", TYPE_PAP),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                nodes = nodes,
                connections = connections
            )
        }
    }
}

data class DiagramSummary(
    val id: String,
    val name: String,
    val type: String,
    val updatedAt: Long
)
