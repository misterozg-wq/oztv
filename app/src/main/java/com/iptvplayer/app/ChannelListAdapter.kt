package com.iptvplayer.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

/** Liste déroulante des chaînes affichée par-dessus le lecteur. */
class ChannelListAdapter(
    private val items: List<Item>,
    private val onPick: (Int) -> Unit
) : RecyclerView.Adapter<ChannelListAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_channel_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val logo: ImageView = v.findViewById(R.id.rowLogo)
        private val name: TextView = v.findViewById(R.id.rowName)

        init {
            v.isFocusable = true
            v.setOnClickListener { onPick(bindingAdapterPosition) }
        }

        fun bind(item: Item) {
            name.text = item.name
            if (item.logo != null) logo.load(item.logo) { placeholder(R.drawable.ic_launcher) }
            else logo.setImageResource(R.drawable.ic_launcher)
        }
    }
}
