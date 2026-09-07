package com.omegarouser.browser

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView

data class Suggestion(val title: String, val url: String)

/**
 * Адаптер подсказок для адресной строки: объединяет историю и закладки,
 * фильтрует по совпадению с введённым текстом (в заголовке или адресе).
 */
class SuggestionAdapter(
    context: Context,
    private val allItems: List<Suggestion>
) : ArrayAdapter<Suggestion>(context, 0, allItems) {

    private var filteredItems: List<Suggestion> = emptyList()

    override fun getCount(): Int = filteredItems.size
    override fun getItem(position: Int): Suggestion = filteredItems[position]

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_suggestion, parent, false)
        val item = filteredItems[position]
        view.findViewById<TextView>(R.id.suggestionTitle).text = item.title
        view.findViewById<TextView>(R.id.suggestionUrl).text = item.url
        return view
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.trim()?.lowercase() ?: ""
                val results = if (query.isEmpty()) {
                    emptyList()
                } else {
                    allItems.filter {
                        it.title.lowercase().contains(query) || it.url.lowercase().contains(query)
                    }.distinctBy { it.url }.take(6)
                }
                return FilterResults().apply {
                    values = results
                    count = results.size
                }
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredItems = (results?.values as? List<Suggestion>) ?: emptyList()
                if (results != null && results.count > 0) notifyDataSetChanged() else notifyDataSetInvalidated()
            }

            override fun convertResultToString(resultValue: Any?): CharSequence {
                return (resultValue as? Suggestion)?.url ?: ""
            }
        }
    }
}
