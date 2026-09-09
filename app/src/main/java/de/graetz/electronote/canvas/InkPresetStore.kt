package de.graetz.electronote.canvas

import android.content.Context
import android.graphics.Color
import org.json.JSONArray

/** App-wide saved pen configurations, same idea as the iPad app's InkPresetStore. */
object InkPresetStore {
    private const val PREFS_NAME = "electronote_prefs"
    private const val KEY_PRESETS = "ink_presets"

    private fun defaults(): List<InkPreset> = listOf(
        InkPreset(name = "Schwarz Stift", tool = DrawTool.PEN, colorArgb = Color.BLACK, widthPx = 4.5f),
        InkPreset(name = "Blau Marker", tool = DrawTool.MARKER, colorArgb = Color.parseColor("#1E88E5"), widthPx = 7f),
        InkPreset(name = "Rot Fein", tool = DrawTool.PEN, colorArgb = Color.parseColor("#E53935"), widthPx = 2.5f)
    )

    fun load(context: Context): MutableList<InkPreset> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PRESETS, null) ?: return defaults().toMutableList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<InkPreset>()
            for (i in 0 until arr.length()) {
                list.add(InkPreset.fromJson(arr.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            defaults().toMutableList()
        }
    }

    fun save(context: Context, presets: List<InkPreset>) {
        val arr = JSONArray()
        for (p in presets) arr.put(p.toJson())
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRESETS, arr.toString())
            .apply()
    }
}
