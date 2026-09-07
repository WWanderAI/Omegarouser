package com.omegarouser.browser

import android.app.DownloadManager
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DownloadsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_downloads)

        recyclerView = findViewById(R.id.downloadsRecyclerView)
        emptyView = findViewById(R.id.emptyDownloadsView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.btnCloseDownloads).setOnClickListener { finish() }
        findViewById<View>(R.id.btnClearDownloads).setOnClickListener {
            DownloadStore.clear(this)
            Toast.makeText(this, getString(R.string.downloads_cleared), Toast.LENGTH_SHORT).show()
            loadDownloads()
        }

        loadDownloads()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in_slight, R.anim.slide_out_right)
    }

    override fun onResume() {
        super.onResume()
        loadDownloads()
    }

    private fun loadDownloads() {
        val entries = DownloadStore.getEntries(this)
        if (entries.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = DownloadsAdapter(entries) { entry ->
                openDownload(entry)
            }
        }
    }

    private fun openDownload(entry: DownloadEntry) {
        try {
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val uri = dm.getUriForDownloadedFile(entry.downloadManagerId)
            if (uri == null) {
                Toast.makeText(this, getString(R.string.download_not_found), Toast.LENGTH_SHORT).show()
                return
            }
            val mime = dm.getMimeTypeForDownloadedFile(entry.downloadManagerId) ?: entry.mimeType
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.download_not_found), Toast.LENGTH_SHORT).show()
        }
    }

    private class DownloadsAdapter(
        private val items: List<DownloadEntry>,
        private val onClick: (DownloadEntry) -> Unit
    ) : RecyclerView.Adapter<DownloadsAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.itemFileName)
            val meta: TextView = view.findViewById(R.id.itemFileMeta)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_download, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = items[position]
            holder.name.text = entry.fileName
            val time = DateUtils.getRelativeTimeSpanString(
                entry.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            )
            holder.meta.text = time
            holder.itemView.setOnClickListener { onClick(entry) }
        }

        override fun getItemCount(): Int = items.size
    }
}
