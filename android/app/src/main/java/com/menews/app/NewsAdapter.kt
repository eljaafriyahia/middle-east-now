package com.menews.app

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class NewsAdapter(
    private var items: List<NewsItem>,
    private val onClick: (NewsItem) -> Unit
) : RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {

    class NewsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtSource: TextView = view.findViewById(R.id.txtSource)
        val txtCategory: TextView = view.findViewById(R.id.txtCategory)
        val txtTitle: TextView = view.findViewById(R.id.txtTitle)
        val txtSummary: TextView = view.findViewById(R.id.txtSummary)
        val txtTime: TextView = view.findViewById(R.id.txtTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_news, parent, false)
        return NewsViewHolder(view)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        val item = items[position]

        holder.txtSource.text = item.source_name ?: "مجهول"
        holder.txtCategory.text = item.category ?: item.source_cat ?: ""

        val titleText = buildString {
            append(item.source_name ?: "")
            append(" | ")
            append(item.title)
        }
        holder.txtTitle.text = titleText

        if (!item.summary.isNullOrBlank()) {
            holder.txtSummary.text = item.summary
            holder.txtSummary.visibility = View.VISIBLE
        } else {
            holder.txtSummary.visibility = View.GONE
        }

        holder.txtTime.text = formatTime(item.published)

        holder.itemView.setOnClickListener {
            if (item.link.isNotBlank()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.link))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    holder.itemView.context.startActivity(intent)
                } catch (_: Exception) { }
            }
            onClick(item)
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<NewsItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "الآن"
            diff < TimeUnit.HOURS.toMillis(1) -> "${diff / TimeUnit.MINUTES.toMillis(1)} دقيقة"
            diff < TimeUnit.DAYS.toMillis(1) -> "${diff / TimeUnit.HOURS.toMillis(1)} ساعة"
            else -> {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }
}
