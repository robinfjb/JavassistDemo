package com.example.javassist

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log

class MainActivity : AppCompatActivity() {
    val  tag = this.javaClass.simpleName
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.e(tag,"onCreate")
        hello("6666666666666")
    }

    private fun hello(s: String) {
        Log.e(tag,"hello")
        Thread.sleep(2000)
    }
}