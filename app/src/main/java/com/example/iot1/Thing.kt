package com.example.iot1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class Thing(val thingName: String, val ipAddress: String)

class ThingAdapter(
    private val thingList: List<Thing>,
    private val itemClickListener: (Thing) -> Unit
) : RecyclerView.Adapter<ThingAdapter.ThingViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThingViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.items_available, parent, false)
        return ThingViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ThingViewHolder, position: Int) {
        val thing = thingList[position]
        holder.bind(thing)
        holder.itemView.setOnClickListener {
            itemClickListener(thing)
        }
    }

    override fun getItemCount() = thingList.size

    class ThingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thingNameTextView: TextView = itemView.findViewById(R.id.textViewThingName)

        fun bind(thing: Thing) {
            thingNameTextView.text = thing.thingName
        }
    }
}