package com.noveldedup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.noveldedup.databinding.ActivityMainBinding
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var adapter: GroupAdapter
    private val io = Executors.newSingleThreadExecutor()

    private var root: File? = null
    private var groups: List<Group> = emptyList()
    private var busy = false

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshAccessUi() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        adapter = GroupAdapter()
        b.rvGroups.layoutManager = LinearLayoutManager(this)
        b.rvGroups.adapter = adapter

        b.progress.visibility = View.GONE
        b.progress.isIndeterminate = false

        b.btnPick.setOnClickListener {
            if (!hasAccess()) {
                requestAccess()
                return@setOnClickListener
            }
            val start = root ?: Environment.getExternalStorageDirectory()
            FolderPickerDialog.show(this, start) { picked ->
                root = picked
                b.tvFolder.text = "已选文件夹：\n" + picked.absolutePath
                groups = emptyList()
                adapter.submit(emptyList())
                b.tvSummary.text = "点「开始扫描」分析里面的重复小说"
                b.btnScan.isEnabled = true
                b.btnExecute.isEnabled = false
            }
        }

        b.btnScan.setOnClickListener { doScan() }
        b.btnExecute.setOnClickListener { confirmExecute() }

        refreshAccessUi()
    }

    override fun onResume() {
        super.onResume()
        refreshAccessUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        io.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshAccessUi()
    }

    // ------------------------------------------------------------------ 权限
    private fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val app = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                .setData(Uri.parse("package:$packageName"))
            try {
                permLauncher.launch(app)
            } catch (e: Exception) {
                try {
                    permLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (e2: Exception) {
                    toast("请到系统设置里手动打开本应用的「所有文件访问权限」")
                }
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                1001
            )
        }
    }

    private fun refreshAccessUi() {
        if (busy) return
        if (hasAccess()) {
            b.btnPick.text = "选择小说文件夹"
            b.btnPick.isEnabled = true
            if (root == null) {
                b.tvSummary.text = "点上面按钮，挑一个装着小说的大文件夹（里面可以有很多层小文件夹）"
            }
        } else {
            b.btnPick.text = "第一步：开启文件访问权限"
            b.btnPick.isEnabled = true
            b.btnScan.isEnabled = false
            b.tvSummary.text = "没权限就看不到你的小说。点上面按钮去开「所有文件访问权限」。"
        }
    }

    // ------------------------------------------------------------------ 扫描
    private fun doScan() {
        val r = root
        if (r == null) {
            toast("请先选择文件夹")
            return
        }
        if (busy) return
        busy = true
        b.btnScan.isEnabled = false
        b.btnExecute.isEnabled = false
        b.progress.visibility = View.VISIBLE
        b.progress.isIndeterminate = true
        b.tvSummary.text = "正在扫描…"

        io.execute {
            val t0 = System.currentTimeMillis()
            val files = Scanner.scan(r) { n ->
                runOnUiThread { b.tvSummary.text = "已读取 $n 个 txt 文件…" }
            }
            val gs = Grouper.group(files)
            Planner.plan(gs)
            val shown = gs.filter { it.losers.isNotEmpty() || it.pending.isNotEmpty() }
            val loserCount = gs.sumOf { it.losers.size }
            val pendingCount = gs.sumOf { it.pending.size }
            val cost = (System.currentTimeMillis() - t0) / 1000.0

            runOnUiThread {
                groups = gs
                adapter.submit(shown)
                b.progress.visibility = View.GONE
                b.progress.isIndeterminate = false
                b.tvSummary.text = "扫描完成：共 ${files.size} 个 txt，归成 ${gs.size} 组；" +
                    "其中 ${shown.size} 组需要处理，可移走 $loserCount 个旧版本，" +
                    "$pendingCount 个文件拿不准、不会自动动。" +
                    "（用时 " + String.format(Locale.US, "%.1f", cost) + " 秒）"
                b.btnScan.isEnabled = true
                b.btnExecute.isEnabled = loserCount > 0
                busy = false
            }
        }
    }

    // ------------------------------------------------------------------ 执行
    private fun confirmExecute() {
        val r = root ?: return
        if (busy) return
        val chosen = groups.filter { it.selected && it.losers.isNotEmpty() }
        val count = chosen.sumOf { it.losers.size }
        if (count == 0) {
            toast("没有勾选任何要整理的组")
            return
        }
        val q = File(r, Scanner.QUARANTINE)
        AlertDialog.Builder(this)
            .setTitle("确认整理")
            .setMessage(
                "即将处理 ${chosen.size} 组，移动 $count 个旧版本文件。\n\n" +
                    "移动到哪里：\n${q.absolutePath}\n\n" +
                    "原目录结构会在目标里原样保留，同时生成《_整理记录.txt》，\n" +
                    "随时可以把文件移回原位。\n\n确定开始吗？"
            )
            .setNegativeButton("取消", null)
            .setPositiveButton("开始整理") { _, _ -> runMove(r, chosen) }
            .show()
    }

    private fun runMove(r: File, chosen: List<Group>) {
        val files = chosen.flatMap { it.losers }.map { it.file }
        busy = true
        b.btnScan.isEnabled = false
        b.btnExecute.isEnabled = false
        b.progress.visibility = View.VISIBLE
        b.progress.isIndeterminate = false
        b.progress.max = files.size
        b.progress.progress = 0

        io.execute {
            val res = Mover.execute(r, files) { done, total ->
                runOnUiThread {
                    b.progress.progress = done
                    b.tvSummary.text = "正在移动 $done / $total …"
                }
            }
            runOnUiThread {
                b.progress.visibility = View.GONE
                busy = false
                b.btnScan.isEnabled = true
                AlertDialog.Builder(this)
                    .setTitle("整理完成")
                    .setMessage(
                        "成功移动 ${res.moved} 个，失败 ${res.failed} 个。\n\n" +
                            "旧版本都在这里：\n${File(r, Scanner.QUARANTINE).absolutePath}\n\n" +
                            "确认新版本没问题后，直接删掉整个「${Scanner.QUARANTINE}」文件夹就干净了。\n" +
                            "想反悔就照《_整理记录.txt》里的路径移回去。"
                    )
                    .setPositiveButton("好") { _, _ -> doScan() }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }
}
