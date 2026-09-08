package com.omegarouser.browser

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PasswordsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_passwords)

        recyclerView = findViewById(R.id.passwordsRecyclerView)
        emptyView = findViewById(R.id.emptyPasswordsView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.btnClosePasswords).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAddPassword).setOnClickListener { showAddDialog() }

        loadPasswords()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in_slight, R.anim.slide_out_right)
    }

    override fun onResume() {
        super.onResume()
        loadPasswords()
    }

    private fun showAddDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_password, null)
        val siteInput = view.findViewById<EditText>(R.id.inputSite)
        val userInput = view.findViewById<EditText>(R.id.inputUsername)
        val passInput = view.findViewById<EditText>(R.id.inputPassword)

        AlertDialog.Builder(this)
            .setTitle(R.string.passwords_add_title)
            .setView(view)
            .setPositiveButton(R.string.passwords_save) { _, _ ->
                val site = siteInput.text.toString().trim()
                val user = userInput.text.toString().trim()
                val pass = passInput.text.toString()
                if (site.isNotEmpty() && user.isNotEmpty() && pass.isNotEmpty()) {
                    PasswordStore.save(this, site, user, pass)
                    loadPasswords()
                } else {
                    Toast.makeText(this, R.string.passwords_fill_all, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.passwords_cancel, null)
            .show()
    }

    private fun loadPasswords() {
        val entries = PasswordStore.getAll(this)
        if (entries.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = PasswordsAdapter(entries) { cred ->
                PasswordStore.remove(this, cred.site, cred.username)
                loadPasswords()
            }
        }
    }

    private class PasswordsAdapter(
        private val items: List<Credential>,
        private val onRemove: (Credential) -> Unit
    ) : RecyclerView.Adapter<PasswordsAdapter.ViewHolder>() {

        private val revealed = mutableSetOf<Int>()

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val site: TextView = view.findViewById(R.id.itemSite)
            val username: TextView = view.findViewById(R.id.itemUsername)
            val password: TextView = view.findViewById(R.id.itemPassword)
            val remove: TextView = view.findViewById(R.id.itemRemove)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_password, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.site.text = item.site
            holder.username.text = item.username
            holder.password.text = if (revealed.contains(position)) item.password else "••••••••"
            holder.password.setOnClickListener {
                if (revealed.contains(position)) revealed.remove(position) else revealed.add(position)
                notifyItemChanged(position)
            }
            holder.remove.setOnClickListener { onRemove(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
