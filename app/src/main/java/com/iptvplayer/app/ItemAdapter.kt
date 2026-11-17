package com.iptvplayer.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load

class ItemAdapter(
    private val onClick: (Item) -> Unit
) : RecyclerView.Adapter<ItemAdapter.VH>() {

    private var items: List<Item> = emptyList()

    fun submit(newItems: List<Item>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(o: Int, n: Int) = items[o].id == newItems[n].id
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == newItems[n]
        })
        items = newItems
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val logo: ImageView = v.findViewById(R.id.imgLogo)
        private val name: TextView = v.findViewById(R.id.tvName)

        init {
            v.isFocusable = true
            v.isFocusableInTouchMode = false
            v.setOnClickListener { onClick(items[bindingAdapterPosition]) }
        }

        fun bind(item: Item) {
            name.text = item.name
            if (item.logo != null) {
                logo.load(item.logo) { crossfade(true); placeholder(R.drawable.ic_launcher) }
            } else {
                logo.setImageResource(R.drawable.ic_launcher)
            }
        }
    }
}
