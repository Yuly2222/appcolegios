package com.example.appcolegios.mensajes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.appcolegios.R
import com.example.appcolegios.data.model.Message

class MessagesAdapter(private var items: List<Message>) : RecyclerView.Adapter<MessagesAdapter.VH>() {
    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val msgText: TextView = view.findViewById(R.id.messageText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = items[position]
        holder.msgText.text = m.texto
    }

    fun update(newItems: List<Message>) {
        items = newItems
        notifyDataSetChanged()
    }
}
