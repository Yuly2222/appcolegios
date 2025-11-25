package com.example.appcolegios.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.appcolegios.data.model.ClassSession

class ScheduleAdapter(private var items: List<ClassSession>) : RecyclerView.Adapter<ScheduleAdapter.VH>() {

    class VH(view: View, val subjectText: TextView, val metaText: TextView) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        // intentar resolver layout e ids dinámicamente para evitar fallos de resolución estática
        val pkg = parent.context.packageName
        val res = parent.context.resources
        val layoutId = res.getIdentifier("item_schedule_session", "layout", pkg)
        val v = if (layoutId != 0) LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
                else LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)

        val subjectId = res.getIdentifier("itemSubject", "id", pkg)
        val metaId = res.getIdentifier("itemMeta", "id", pkg)

        val subjectView = if (subjectId != 0) v.findViewById<TextView>(subjectId) else v.findViewById(android.R.id.text1)
        val metaView = if (metaId != 0) v.findViewById<TextView>(metaId) else v.findViewById(android.R.id.text2)

        return VH(v, subjectView, metaView)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.subjectText.text = s.subject
        val meta = "${s.startTime} - ${s.endTime} · ${s.classroom} · ${s.teacher}"
        holder.metaText.text = meta
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<ClassSession>) {
        items = newItems
        notifyDataSetChanged()
    }
}
