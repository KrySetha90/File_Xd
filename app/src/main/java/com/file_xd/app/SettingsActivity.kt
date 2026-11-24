package com.filebox.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*

class SettingsActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val sp = P.sp(this)
        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(40), px(16), px(16))
        }
        fun label(t: String) = TextView(this).apply {
            text = t; textSize = 16f; setPadding(0, px(16), 0, px(4))
        }

        col.addView(TextView(this).apply { text = "Settings"; textSize = 22f })

        col.addView(label("Background color"))
        val colors = listOf("#121212", "#000000", "#FFFFFF", "#1E3A5F", "#2E7D32",
            "#B71C1C", "#4A148C", "#FF6F00", "#263238", "#F5F5DC")
        val row = LinearLayout(this)
        colors.forEach { hex ->
            row.addView(Button(this).apply {
                setBackgroundColor(Color.parseColor(hex))
                layoutParams = LinearLayout.LayoutParams(px(48), px(48)).apply { setMargins(px(4), 0, px(4), 0) }
                setOnClickListener {
                    sp.edit().putInt("bg", Color.parseColor(hex)).apply()
                    Toast.makeText(context, "Background set", Toast.LENGTH_SHORT).show()
                }
            })
        }
        col.addView(HorizontalScrollView(this).apply { addView(row) })

        val hexBox = EditText(this).apply { hint = "#RRGGBB custom"; setSingleLine() }
        col.addView(hexBox)
        col.addView(Button(this).apply {
            text = "Apply custom color"; isAllCaps = false
            setOnClickListener {
                try {
                    sp.edit().putInt("bg", Color.parseColor(hexBox.text.toString().trim())).apply()
                    Toast.makeText(context, "Background set", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Invalid color", Toast.LENGTH_SHORT).show()
                }
            }
        })

        col.addView(CheckBox(this).apply {
            text = "Show hidden files (.name)"
            isChecked = sp.getBoolean("hidden", false)
            setOnCheckedChangeListener { _, v -> sp.edit().putBoolean("hidden", v).apply() }
        })

        fun spinner(title: String, opts: Array<String>, key: String, def: Int) {
            col.addView(label(title))
            col.addView(Spinner(this).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, opts)
                setSelection(sp.getInt(key, def))
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        sp.edit().putInt(key, pos).apply()
                    }
                    override fun onNothingSelected(p: AdapterView<*>?) {}
                }
            })
        }
        spinner("Sort by", arrayOf("Name", "Size", "Date modified"), "sort", 0)
        spinner("Text size", arrayOf("Small", "Medium", "Large"), "size", 1)

        setContentView(ScrollView(this).apply { addView(col) })
    }
}
