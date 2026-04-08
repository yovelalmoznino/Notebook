package com.example.notebook.data.db

import androidx.room.TypeConverter
import com.example.notebook.data.model.Stroke
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStrokeList(value: List<Stroke>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStrokeList(value: String): List<Stroke> {
        val listType = object : TypeToken<List<Stroke>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }
}