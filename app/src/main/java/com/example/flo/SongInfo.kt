package com.example.flo

data class Lyric(
    val time: Int,
    val lyric: String
)

data class SongInfo(
    val singer :String,
    val album: String,
    val title :String,
    val duration :Int,
    val imageUrl: String,
    val fileUrl: String,
//    val lyrics: ArrayList<Map<Int,String>>
    val lyrics: ArrayList<Lyric>
)
