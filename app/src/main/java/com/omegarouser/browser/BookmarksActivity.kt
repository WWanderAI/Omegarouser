package com.omegarouser.browser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class BookmarksActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmarks)

        recyclerView = findViewById(R.id.bookmarksRecyclerView)
        emptyView = findViewById(R.id.emptyBookmarksView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.btnCloseBookmarks).setOnClickListener { finish() }
        findViewById<View>(R.id.btnClearBookmarks).setOnClickListener {
            BookmarkStore.clear(this)
            OmegarouserBookmarksWidgetProvider.requestUpdate(this)
            Toast.makeText(this, getString(R.string.bookmarks_cleared), Toast.LENGTH_SHORT).show()
            loadBookmarks()
        }

        loadBookmarks()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in_slight, R.anim.slide_out_right)
    }

    override fun onResume() {
        super.onResume()
        loadBookmarks()
    }

    private fun loadBookmarks() {
        val entries = BookmarkStore.getEntries(this)
        if (entries.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = BookmarksAdapter(
                entries,
                onClick = { url ->
                    val intent = Intent(this, MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = Uri.parse(url)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(intent)
                    finish()
                },
                onRemove = { url ->
                    BookmarkStore.remove(this, url)
                    OmegarouserBookmarksWidgetProvider.requestUpdate(this)
                    loadBookmarks()
                }
            )
        }
    }

    private class BookmarksAdapter(
        private val items: List<BookmarkEntry>,
        private val onClick: (String) -> Unit,
        private val onRemove: (String) -> Unit
    ) : RecyclerView.Adapter<BookmarksAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.itemTitle)
            val url: TextView = view.findViewById(R.id.itemUrl)
            val remove: TextView = view.findViewById(R.id.itemRemove)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_bookmark, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = items[position]
            holder.title.text = entry.title
            holder.url.text = entry.url
            holder.itemView.setOnClickListener { onClick(entry.url) }
            holder.remove.setOnClickListener { onRemove(entry.url) }
        }

        override fun getItemCount(): Int = items.size
    }
}
