package com.omegarouser.browser

import android.app.DownloadManager
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Текущее состояние загрузки, полученное из DownloadManager */
data class DownloadStatus(
    val status: Int, // DownloadManager.STATUS_*
    val bytesDownloaded: Long,
    val bytesTotal: Long,
    val percent: Int, // 0..100, или -1 если размер неизвестен
    val etaSeconds: Long? // null если не удалось оценить
)

class DownloadsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: DownloadsAdapter

    private val handler = Handler(Looper.getMainLooper())
    // id загрузки -> (последний известный объём скачанного, время замера) — для оценки скорости/ETA
    private val lastSample = mutableMapOf<Long, Pair<Long, Long>>()

    private val pollRunnable = object : Runnable {
        override fun run() {
            val hasActive = pollStatuses()
            if (hasActive) {
                handler.postDelayed(this, 800)
            }
        }
    }

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

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(pollRunnable)
    }

    private fun loadDownloads() {
        val entries = DownloadStore.getEntries(this)
        if (entries.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            handler.removeCallbacks(pollRunnable)
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            adapter = DownloadsAdapter(entries) { entry -> openDownload(entry) }
            recyclerView.adapter = adapter
            handler.removeCallbacks(pollRunnable)
            handler.post(pollRunnable)
        }
    }

    /** Опрашивает статус всех загрузок; возвращает true, если есть ещё активные (нужно опрашивать дальше) */
    private fun pollStatuses(): Boolean {
        if (!::adapter.isInitialized) return false
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        var anyActive = false
        val now = System.currentTimeMillis()

        for (entry in adapter.items) {
            val query = DownloadManager.Query().setFilterById(entry.downloadManagerId)
            var cursor: Cursor? = null
            try {
                cursor = dm.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                    val percent = if (total > 0) ((downloaded * 100) / total).toInt() else -1

                    var eta: Long? = null
                    if (status == DownloadManager.STATUS_RUNNING && total > 0) {
                        val prev = lastSample[entry.downloadManagerId]
                        if (prev != null) {
                            val (prevBytes, prevTime) = prev
                            val bytesDelta = downloaded - prevBytes
                            val timeDeltaSec = (now - prevTime) / 1000.0
                            if (bytesDelta > 0 && timeDeltaSec > 0) {
                                val speed = bytesDelta / timeDeltaSec
                                val remaining = total - downloaded
                                eta = (remaining / speed).toLong()
                            }
                        }
                        lastSample[entry.downloadManagerId] = downloaded to now
                    }

                    if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) {
                        anyActive = true
                    }

                    adapter.updateStatus(
                        entry.downloadManagerId,
                        DownloadStatus(status, downloaded, total, percent, eta)
                    )
                }
            } catch (e: Exception) {
                // Игнорируем — запись могла быть удалена из DownloadManager
            } finally {
                cursor?.close()
            }
        }
        return anyActive
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

    class DownloadsAdapter(
        val items: List<DownloadEntry>,
        private val onClick: (DownloadEntry) -> Unit
    ) : RecyclerView.Adapter<DownloadsAdapter.ViewHolder>() {

        private val statuses = mutableMapOf<Long, DownloadStatus>()

        fun updateStatus(id: Long, status: DownloadStatus) {
            statuses[id] = status
            val index = items.indexOfFirst { it.downloadManagerId == id }
            if (index >= 0) notifyItemChanged(index)
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.itemFileName)
            val meta: TextView = view.findViewById(R.id.itemFileMeta)
            val icon: ImageView = view.findViewById(R.id.itemFileIcon)
            val progress: CircularProgressView = view.findViewById(R.id.itemProgress)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_download, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = items[position]
            val context = holder.itemView.context
            val status = statuses[entry.downloadManagerId]

            holder.name.text = entry.fileName

            when (status?.status) {
                DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                    holder.icon.visibility = View.INVISIBLE
                    holder.progress.visibility = View.VISIBLE
                    holder.progress.progress = status.percent

                    val sizeText = if (status.bytesTotal > 0) {
                        Formatter.formatShortFileSize(context, status.bytesDownloaded) + " / " +
                            Formatter.formatShortFileSize(context, status.bytesTotal)
                    } else {
                        Formatter.formatShortFileSize(context, status.bytesDownloaded)
                    }

                    val etaText = when {
                        status.etaSeconds == null -> ""
                        status.etaSeconds < 60 -> " · осталось ~${status.etaSeconds} сек"
                        else -> " · осталось ~${status.etaSeconds / 60} мин"
                    }

                    val percentText = if (status.percent >= 0) "${status.percent}% · " else ""
                    holder.meta.text = "$percentText$sizeText$etaText"
                }
                DownloadManager.STATUS_FAILED -> {
                    holder.icon.visibility = View.VISIBLE
                    holder.progress.visibility = View.GONE
                    holder.meta.text = context.getString(R.string.download_failed)
                }
                else -> {
                    // STATUS_SUCCESSFUL или статус ещё не получен — показываем обычную иконку
                    holder.icon.visibility = View.VISIBLE
                    holder.progress.visibility = View.GONE
                    holder.meta.text = DateUtils.getRelativeTimeSpanString(
                        entry.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                    )
                }
            }

            holder.itemView.setOnClickListener { onClick(entry) }
        }

        override fun getItemCount(): Int = items.size
    }
}
