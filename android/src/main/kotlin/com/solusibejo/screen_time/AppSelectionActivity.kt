package com.solusibejo.screen_time

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*

class AppSelectionActivity : Activity() {
  private lateinit var listView: ListView
  private lateinit var btnCancel: Button
  private lateinit var btnDone: Button

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val root = LinearLayout(this)
    root.orientation = LinearLayout.VERTICAL
    val title = TextView(this)
    title.text = "选择要限制的应用"
    title.textSize = 18f
    title.setPadding(32, 32, 32, 16)
    listView = ListView(this)
    btnCancel = Button(this)
    btnCancel.text = "取消"
    btnDone = Button(this)
    btnDone.text = "完成"
    val btnRow = LinearLayout(this)
    btnRow.orientation = LinearLayout.HORIZONTAL
    btnRow.addView(btnCancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    btnRow.addView(btnDone, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    root.addView(title)
    root.addView(listView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    root.addView(btnRow)
    setContentView(root)

    val selectionStorageKey = intent.getStringExtra("selectionStorageKey") ?: "screenTimeSelection"
    val prefs = getSharedPreferences("screen_time", MODE_PRIVATE)
    val savedPackages: MutableSet<String> =
      (prefs.getStringSet("${selectionStorageKey}_packages", emptySet())?.toMutableSet()
        ?: mutableSetOf()) as MutableSet<String>

    val pm: PackageManager = packageManager
    val installedApplications = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
    } else {
      @Suppress("DEPRECATION")
      pm.getInstalledApplications(PackageManager.GET_META_DATA)
    }
    val items = installedApplications.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }

    listView.adapter = object : BaseAdapter() {
      override fun getCount(): Int = items.size
      override fun getItem(position: Int): Any = items[position]
      override fun getItemId(position: Int): Long = position.toLong()
      override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val row = convertView ?: LinearLayout(this@AppSelectionActivity).apply {
          orientation = LinearLayout.HORIZONTAL
          setPadding(24, 24, 24, 24)
          addView(CheckBox(this@AppSelectionActivity))
          addView(TextView(this@AppSelectionActivity).apply { setPadding(16, 0, 0, 0) })
        }
        val checkBox = (row as LinearLayout).getChildAt(0) as CheckBox
        val label = row.getChildAt(1) as TextView
        val app = items[position]
        val appName = app.loadLabel(pm).toString()
        label.text = "$appName (${app.packageName})"
        checkBox.setOnCheckedChangeListener(null)
        checkBox.isChecked = savedPackages.contains(app.packageName)
        checkBox.setOnCheckedChangeListener { _, isChecked ->
          if (isChecked) savedPackages.add(app.packageName) else savedPackages.remove(app.packageName)
        }
        return row
      }
    }

    btnCancel.setOnClickListener {
      setResult(Activity.RESULT_CANCELED)
      finish()
    }

    btnDone.setOnClickListener {
      val editor = prefs.edit()
      editor.putStringSet("${selectionStorageKey}_packages", savedPackages)
      val appCount = savedPackages.size
      editor.putInt("${selectionStorageKey}_app_count", appCount)
      editor.putInt("${selectionStorageKey}_category_count", 0)
      editor.putInt("${selectionStorageKey}_total_selected", appCount)
      editor.apply()
      val data = intent
      data.putExtra("appCount", appCount)
      data.putExtra("categoryCount", 0)
      setResult(Activity.RESULT_OK, data)
      finish()
    }
  }
}


