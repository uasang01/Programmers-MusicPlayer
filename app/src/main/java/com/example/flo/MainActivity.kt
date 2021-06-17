package com.example.flo

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.flo.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.MalformedURLException
import java.net.URL


class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding

    var url: URL? = null
    var songInfo: SongInfo? = null  //singer, album, title, duration, image, file, lyrics
    var songs: ArrayList<SongInfo>? = null
    var isPlaying = false


    var curLyrlcPos: Int = 0
    var nextLyrlcPos: Int = 1

    lateinit var handler: Handler
    lateinit var mp: MediaPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 곡 정보 받을 url
        url = try {
            URL("https://grepp-programmers-challenges.s3.ap-northeast-2.amazonaws.com/2020-flo/song.json")
        }catch (e: MalformedURLException){
            Log.d("Exception", e.toString())
            null
        }

        handler = Handler(Looper.getMainLooper())

        // view 초기화
        binding.currentLyricTV.text = ""
        binding.nextLyricTV.text = ""

        // view listner
        binding.playBtn.setOnClickListener {
            if(isPlaying){
                stop()
            }else{
                play()
            }
        }
        binding.musicSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                seekBar?.max = mp.duration / 1000;
//                val str = progress.toString() + "/" + seekBar?.max
//                binding.musicProgressTV.text = str
                if(fromUser){
                    mp.seekTo(progress * 1000);
                }

                setCurrentLyric(progress*1000)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch(IO){
            // 곡 정보 받아오기
            url?.getString()?.apply {
                // default dispatcher for json parsing, cpu intensive work
                withContext(Dispatchers.Default){
                    val list = parseJson(this@apply)
                    songInfo = list?.get(0)
                }
            }

            // 받아온 곡 정보로 초기화
            songInfo?.let {
                lifecycleScope.launch(Main){    // UI thread
                    binding.titleTV.text = it.title
                    binding.albumTV.text = it.album
                    binding.singerTV.text = it.singer
                    Glide.with(applicationContext)
                        .load(it.imageUrl)
                        .into(binding.albumIV)
                    binding.currentLyricTV.text = it.lyrics[0].lyric
                    binding.nextLyricTV.text = it.lyrics[1].lyric
                }
                mp = MediaPlayer()
                mp.setDataSource(it.fileUrl)
                mp.prepare()
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    private fun play(){
        // music start
        mp.start()
        isPlaying = true
        binding.playBtn.setImageDrawable(
            ContextCompat.getDrawable(
                applicationContext,
                R.drawable.ic_stop
            )
        )

        // seekbar progress update
        runOnUiThread(object : Runnable {
            override fun run() {
                val mCurrentPosition: Int = mp.currentPosition / 1000
                binding.musicSeekBar.progress = mCurrentPosition
                handler.postDelayed(this, 1000)
            }
        })
    }
    private fun stop(){
        // music stop
        mp.pause()
        mp.seekTo(0)
        isPlaying = false
        binding.playBtn.setImageDrawable(
            ContextCompat.getDrawable(
                applicationContext,
                R.drawable.ic_play
            )
        )
    }

    private fun setCurrentLyric(pos: Int){
        // 현재 시간에 맞는 가사 뷰에 전달
        songInfo?.let{
            if( pos < songInfo!!.lyrics[0].time){
                // 0초 ~ 첫 가사 시간 전
                curLyrlcPos = 0
                nextLyrlcPos = 1
            }else{
                // 첫 가사 시간 이후
                for( i in 1 until it.lyrics.size-1){
                    if(pos >= it.lyrics[i].time-1000){
                        if(pos < it.lyrics[i+1].time-1000){
                            curLyrlcPos = i
                            nextLyrlcPos = i+1
                            break
                        }
                    }

                    // 가사가 모두 끝낫다면 다음 가사는 공백
                    if(it.lyrics[i].time-1000 < pos && it.lyrics[i+1].time-1000 < pos){
                        curLyrlcPos = i+1
                        nextLyrlcPos = -1
                    }
                }
            }

            binding.currentLyricTV.text = songInfo!!.lyrics[curLyrlcPos].lyric
            if(nextLyrlcPos>0)
                binding.nextLyricTV.text = songInfo!!.lyrics[nextLyrlcPos].lyric
            else{
                binding.nextLyricTV.text = ""
            }
        }
    }

    // extension function to get string data from url
    private fun URL.getString(): String? {
        val stream = openStream()
        return try {
            val r = BufferedReader(InputStreamReader(stream))
            val result = StringBuilder()
            var line: String?
            while (r.readLine().also { line = it } != null) {
                result.append(line).appendLine()
            }
            result.toString()
        }catch (e: IOException){
            e.toString()
        }
    }

    // parse json data
    private fun parseJson(jsonObj: String):List<SongInfo>?{
        val list = mutableListOf<SongInfo>()

        try {
            val timeLyricData : List<String>
            var lyric : String
            var time : Int

            val lyrics: ArrayList<Lyric> = arrayListOf()

            val obj = JSONObject(jsonObj)
            val singer = obj.getString("singer")
            val album = obj.getString("album")
            val title = obj.getString("title")
            val duration = obj.getInt("duration")
            val imageUrl = obj.getString("image")
            val fileUrl = obj.getString("file")
            val timeLyricDatas = obj.getString("lyrics")

            timeLyricData = timeLyricDatas.split("\n")
            timeLyricData.forEach {
                val data = it.split("[","]")
                // 시간
                val timeData = data[1].split(":")
                time = timeData[0].toInt() * 60 * 1000
                time += timeData[1].toInt() * 1000
                time += timeData[2].toInt()

                // 가사
                lyric = data[2]
                lyrics.add(Lyric(time,lyric))
            }
            list.add(SongInfo(singer, album, title, duration, imageUrl, fileUrl, lyrics))
            Log.e("lyrics","$lyrics")
        }catch (e: JSONException){
            Log.d("Exception", e.toString())
        }

        return list
    }
}
