package com.xc.air3upgrader

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.xc.air3upgrader.databinding.ActivityCheckPromptBinding
import kotlinx.coroutines.launch

class CheckPromptActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCheckPromptBinding
    private lateinit var dataStoreManager: DataStoreManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("CheckPromptActivity", "onCreate called")
        binding = ActivityCheckPromptBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dataStoreManager = DataStoreManager(this)

        binding.buttonSkipReminder.setOnClickListener {
            Log.d("CheckPromptActivity", "buttonSkipReminder clicked")
            lifecycleScope.launch {
                dataStoreManager.saveUnhiddenLaunchOnReboot(true)
                Log.d("CheckPromptActivity", "Skip, reminder at next start-up")
                finishAffinity() // Close the entire app
            }
        }

        binding.buttonSkipNoReminder.setOnClickListener {
            Log.d("CheckPromptActivity", "buttonSkipNoReminder clicked")
            lifecycleScope.launch {
                dataStoreManager.saveUnhiddenLaunchOnReboot(false)
                Log.d("CheckPromptActivity", "Skip, no reminder at next start-up")
                finishAffinity() // Close the entire app
            }
        }

        binding.buttonContinue.setOnClickListener {
            Log.d("CheckPromptActivity", "buttonContinue clicked")
            lifecycleScope.launch {
                dataStoreManager.saveUnhiddenLaunchOnReboot(false)
                Log.d("CheckPromptActivity", "Continue, no reminder at next start-up")
                val intent = Intent(this@CheckPromptActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                // Add the extra to indicate that the launch is from CheckPromptActivity
                intent.putExtra("isLaunchFromCheckPromptActivity", true)
                startActivity(intent)
            }
        }
    }
}