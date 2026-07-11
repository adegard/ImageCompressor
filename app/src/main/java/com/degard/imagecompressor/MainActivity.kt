package com.degard.imagecompressor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.degard.imagecompressor.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val pickSource = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            prefs.sourceUri = it
            updateUI()
        }
    }

    private val pickTmp = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            prefs.tmpUri = it
            updateUI()
        }
    }

    private val pickFinal = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            prefs.finalUri = it
            updateUI()
        }
    }

    private val doneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val compressed = intent.getIntExtra("compressed", 0)
            val errors = intent.getIntExtra("errors", 0)
            val skipped = intent.getIntExtra("skipped", 0)

            binding.progressBar.visibility = View.GONE
            binding.btnStart.isEnabled = true
            binding.tvStatus.text = getString(R.string.done, compressed, errors)

            if (errors > 0) {
                Toast.makeText(this@MainActivity, "Completed with $errors errors", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)

        binding.btnSelectSource.setOnClickListener { pickSource.launch(null) }
        binding.btnSelectTmp.setOnClickListener { pickTmp.launch(null) }
        binding.btnSelectFinal.setOnClickListener { pickFinal.launch(null) }

        binding.etQuality.setText(prefs.quality.toString())
        binding.etMaxRes.setText(prefs.maxRes.toString())

        binding.btnStart.setOnClickListener { startCompression() }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(doneReceiver, IntentFilter("com.degard.imagecompressor.DONE"), RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(doneReceiver)
    }

    private fun updateUI() {
        binding.tvSourcePath.text = prefs.sourceUri?.toString() ?: getString(R.string.not_set)
        binding.tvTmpPath.text = prefs.tmpUri?.toString() ?: getString(R.string.not_set)
        binding.tvFinalPath.text = prefs.finalUri?.toString() ?: getString(R.string.not_set)

        binding.btnStart.isEnabled = prefs.sourceUri != null && prefs.tmpUri != null && prefs.finalUri != null
    }

    private fun startCompression() {
        val srcUri = prefs.sourceUri
        val tmpUri = prefs.tmpUri
        val finalUri = prefs.finalUri

        if (srcUri == null || tmpUri == null || finalUri == null) {
            Toast.makeText(this, getString(R.string.error_no_source), Toast.LENGTH_SHORT).show()
            return
        }

        val quality = binding.etQuality.text.toString().toIntOrNull() ?: 65
        val maxRes = binding.etMaxRes.text.toString().toIntOrNull() ?: 1280

        prefs.quality = quality
        prefs.maxRes = maxRes

        binding.btnStart.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = getString(R.string.compressing)

        val intent = Intent(this, CompressService::class.java).apply {
            putExtra("src", srcUri.toString())
            putExtra("tmp", tmpUri.toString())
            putExtra("final", finalUri.toString())
            putExtra("quality", quality)
            putExtra("maxres", maxRes)
        }
        startForegroundService(intent)
    }
}
