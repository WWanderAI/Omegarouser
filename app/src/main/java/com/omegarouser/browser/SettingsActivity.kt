package com.omegarouser.browser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.btnCloseSettings).setOnClickListener { finish() }

        val adblockSwitch = findViewById<Switch>(R.id.switchAdblock)
        adblockSwitch.isChecked = SettingsStore.isAdblockEnabled(this)
        adblockSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            SettingsStore.setAdblockEnabled(this, isChecked)
        }

        val searchEngineGroup = findViewById<RadioGroup>(R.id.radioGroupSearchEngine)
        val currentEngine = SettingsStore.getSearchEngine(this)
        val checkedId = when (currentEngine) {
            "yandex" -> R.id.radioYandex
            "duckduckgo" -> R.id.radioDuckDuckGo
            else -> R.id.radioGoogle
        }
        searchEngineGroup.check(checkedId)
        searchEngineGroup.setOnCheckedChangeListener { _, checkedId2 ->
            val engine = when (checkedId2) {
                R.id.radioYandex -> "yandex"
                R.id.radioDuckDuckGo -> "duckduckgo"
                else -> "google"
            }
            SettingsStore.setSearchEngine(this, engine)
        }

        val apiKeyInput = findViewById<EditText>(R.id.inputCseApiKey)
        val engineIdInput = findViewById<EditText>(R.id.inputCseEngineId)
        apiKeyInput.setText(SettingsStore.getCustomSearchApiKey(this))
        engineIdInput.setText(SettingsStore.getCustomSearchEngineId(this))

        findViewById<View>(R.id.btnSaveCseConfig).setOnClickListener {
            SettingsStore.setCustomSearchApiKey(this, apiKeyInput.text.toString())
            SettingsStore.setCustomSearchEngineId(this, engineIdInput.text.toString())
            Toast.makeText(this, getString(R.string.settings_cse_saved), Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btnSitePermissions).setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in_slight, R.anim.slide_out_right)
    }
}
