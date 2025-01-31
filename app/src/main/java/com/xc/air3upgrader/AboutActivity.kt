package com.xc.air3upgrader

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        // Find the TextViews
        val versionTextView = findViewById<TextView>(R.id.versionTextView)
        val copyrightTextView = findViewById<TextView>(R.id.copyrightTextView)

        // Set the text
        versionTextView.text = getString(R.string.app_version) + " " + BuildConfig.VERSION_NAME
        copyrightTextView.text = getString(R.string.copyright)
    }
}