package com.uznavigator.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.uznavigator.app.R
import com.uznavigator.app.data.GeocodingResult

class SearchAdapter(
    private var items: List<GeocodingResult> = emptyList(),
    private val onClick: (GeocodingResult) -> Unit
) : RecyclerView.Adapter<SearchAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.itemName)
        val address: TextView = view.findViewById(R.id.itemAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.address.text = item.address
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size

    fun submit(newItems: List<GeocodingResult>) {
        items = newItems
        notifyDataSetChanged()
    }
}
