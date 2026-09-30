package com.noveldedup

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.noveldedup.databinding.ItemGroupBinding
import java.util.Locale

class GroupAdapter : RecyclerView.Adapter<GroupAdapter.VH>() {

    private val items = ArrayList<Group>()

    fun submit(list: List<Group>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    class VH(private val b: ItemGroupBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(g: Group) {
            b.cbSelected.setOnCheckedChangeListener(null)
            b.cbSelected.isChecked = g.selected
            b.cbSelected.isEnabled = g.losers.isNotEmpty()
            b.cbSelected.setOnCheckedChangeListener { _, checked -> g.selected = checked }

            val head = StringBuilder("《").append(g.title).append("》")
            head.append(if (g.author.isNotEmpty()) "　作者：" + g.author else "　作者：未写")
            b.tvTitle.text = head.toString()

            val sub = StringBuilder(g.reason)
            if (g.mergeNote.isNotEmpty()) sub.append("　｜　").append(g.mergeNote)
            b.tvSub.text = sub.toString()
            b.tvSub.setTextColor(if (g.uncertain) 0xFFD84315.toInt() else 0xFF777777.toInt())

            val k = g.keep
            if (k != null) {
                b.tvKeep.text = "保留：" + k.name + "\n　　　" + fmt(k.size) + detail(k)
                b.tvKeep.setTextColor(0xFF2E7D32.toInt())
            } else {
                b.tvKeep.text = "保留：（无）"
                b.tvKeep.setTextColor(0xFF777777.toInt())
            }

            if (g.losers.isEmpty()) {
                b.tvLosers.visibility = View.GONE
            } else {
                b.tvLosers.visibility = View.VISIBLE
                val sb = StringBuilder("移走 ").append(g.losers.size).append(" 个：\n")
                for ((i, f) in g.losers.withIndex()) {
                    if (i >= 8) {
                        sb.append("… 另外 ").append(g.losers.size - 8).append(" 个\n")
                        break
                    }
                    sb.append("· ").append(f.name).append("　").append(fmt(f.size)).append('\n')
                }
                b.tvLosers.text = sb.toString().trimEnd()
                b.tvLosers.setTextColor(0xFFC62828.toInt())
            }

            if (g.pending.isEmpty()) {
                b.tvPending.visibility = View.GONE
            } else {
                b.tvPending.visibility = View.VISIBLE
                val sb = StringBuilder("未处理（需你自己看一眼）")
                sb.append(g.pending.size).append(" 个：\n")
                for ((i, f) in g.pending.withIndex()) {
                    if (i >= 5) {
                        sb.append("…\n")
                        break
                    }
                    sb.append("· ").append(f.name).append('\n')
                }
                b.tvPending.text = sb.toString().trimEnd()
                b.tvPending.setTextColor(0xFF6A1B9A.toInt())
            }
        }

        private fun detail(f: BookFile): String {
            val sb = StringBuilder()
            if (f.done) sb.append("完结")
            if (f.chap > 0) {
                if (sb.isNotEmpty()) sb.append("　")
                sb.append("第").append(f.chap).append("章")
            }
            if (f.extra > 0) {
                if (sb.isNotEmpty()) sb.append("　")
                sb.append("番外").append(f.extra)
            }
            return sb.toString()
        }
    }

    companion object {
        fun fmt(bytes: Long): String = when {
            bytes >= 1024L * 1024L -> String.format(Locale.US, "%.2f MB", bytes / 1048576.0)
            bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> bytes.toString() + " B"
        }
    }
}
