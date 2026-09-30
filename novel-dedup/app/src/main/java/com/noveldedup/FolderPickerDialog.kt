package com.noveldedup

import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.noveldedup.databinding.DialogFolderBinding
import java.io.File

/**
 * 自己写的目录选择器。
 * 比系统的 ACTION_OPEN_DOCUMENT_TREE 好用的地方：Android 11+ 系统选择器
 * 不允许选内部存储根目录和 Download 目录，而小说经常就放在那儿。
 */
object FolderPickerDialog {

    fun show(activity: AppCompatActivity, startDir: File, onPick: (File) -> Unit) {
        var current = if (startDir.isDirectory) startDir else File("/storage/emulated/0")

        val b = DialogFolderBinding.inflate(LayoutInflater.from(activity))
        val names = ArrayList<String>()
        var dirs: List<File> = emptyList()

        fun reload() {
            b.tvPath.text = current.absolutePath
            names.clear()
            dirs = try {
                current.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
                    ?.sortedBy { it.name.lowercase() } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            names.add("⬆  返回上一级")
            for (d in dirs) names.add("📁  " + d.name)
            (b.list.adapter as ArrayAdapter<String>).notifyDataSetChanged()
            b.list.setSelection(0)
        }

        b.list.adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, names)
        b.list.setOnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
            if (position == 0) {
                val p = current.parentFile
                if (p != null && p.canRead()) {
                    current = p
                    reload()
                }
            } else {
                val d = dirs.getOrNull(position - 1) ?: return@setOnItemClickListener
                current = d
                reload()
            }
        }
        reload()

        val dlg = AlertDialog.Builder(activity)
            .setTitle("选择小说所在的大文件夹")
            .setView(b.root)
            .setPositiveButton("就选这个文件夹", null)
            .setNegativeButton("取消", null)
            .create()

        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            onPick(current)
            dlg.dismiss()
        }
    }
}
