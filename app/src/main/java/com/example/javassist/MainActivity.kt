package com.example.javassist

import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.just.agentweb.AgentWeb

class MainActivity : AppCompatActivity() {
    val  tag = this.javaClass.simpleName
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.e(tag,"onCreate")
        hello("6666666666666")
        val contentV = findViewById<FrameLayout>(R.id.fl_content)
        AgentWeb.with(this)
            .setAgentWebParent(contentV, LinearLayout.LayoutParams(-1, -1))
            .useDefaultIndicator()
            .createAgentWeb()
            .ready()
            .go("https://m.baidu.com");
    }

    private fun hello(s: String) {
        Log.e(tag,"hello")
        Thread.sleep(2000)
    }
}