package de.morhenn.ar_navigation.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import de.morhenn.ar_navigation.R
import org.json.JSONObject
import com.google.android.material.button.MaterialButton
import android.widget.Toast
import androidx.navigation.fragment.findNavController

class ShoppingListResultFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_shopping_list_result, container, false)
        val containerCategories = view.findViewById<LinearLayout>(R.id.container_categories)
        val jsonString = arguments?.getString("shopping_list_json") ?: ""
        if (jsonString.isNotEmpty()) {
            val json = JSONObject(jsonString)
            val categories = listOf("electronics", "clothes", "groceries")
            for (cat in categories) {
                if (json.has(cat)) {
                    val sectionTitle = TextView(requireContext())
                    sectionTitle.text = cat.replaceFirstChar { it.uppercase() }
                    sectionTitle.setTextAppearance(android.R.style.TextAppearance_Material_Headline)
                    sectionTitle.setPadding(0, 24, 0, 8)
                    containerCategories.addView(sectionTitle)
                    val arr = json.getJSONArray(cat)
                    for (i in 0 until arr.length()) {
                        val item = TextView(requireContext())
                        item.text = "- ${arr.getString(i)}"
                        item.setTextAppearance(android.R.style.TextAppearance_Material_Body1)
                        item.setPadding(16, 2, 0, 2)
                        containerCategories.addView(item)
                    }
                    // Add AR navigation button for this category
                    val navBtn = MaterialButton(requireContext())
                    navBtn.text = "Navigate with AR"
                    navBtn.setIconResource(R.drawable.ic_baseline_view_in_ar_24)
                    navBtn.iconPadding = 16
                    navBtn.setOnClickListener {
                        // Launch AR navigation with hardcoded directions for this category
                        val directions = when (cat.lowercase()) {
                            "electronics" -> "STRAIGHT,STRAIGHT,LEFT"
                            "groceries", "grocery" -> "STRAIGHT,STRAIGHT,RIGHT"
                            "clothes" -> "STRAIGHT,STRAIGHT,STRAIGHT"
                            else -> ""
                        }
                        findNavController().navigate(R.id.arFragment, Bundle().apply {
                            putBoolean("createMode", true)
                            putBoolean("navOnly", true)
                            putString("shelfName", cat.replaceFirstChar { it.uppercase() })
                            putString("arDirections", directions)
                        })
                    }
                    containerCategories.addView(navBtn)
                }
            }
            if (json.has("not_available")) {
                val naTitle = TextView(requireContext())
                naTitle.text = "Not available:"
                naTitle.setTextAppearance(android.R.style.TextAppearance_Material_Headline)
                naTitle.setPadding(0, 24, 0, 8)
                containerCategories.addView(naTitle)
                val naItem = TextView(requireContext())
                naItem.text = json.getString("not_available")
                naItem.setTextAppearance(android.R.style.TextAppearance_Material_Body1)
                naItem.setPadding(16, 2, 0, 2)
                containerCategories.addView(naItem)
            }
        }
        return view
    }
} 