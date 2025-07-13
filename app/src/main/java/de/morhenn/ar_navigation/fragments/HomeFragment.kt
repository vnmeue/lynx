package de.morhenn.ar_navigation.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import de.morhenn.ar_navigation.R

class HomeFragment : Fragment() {
    private lateinit var searchEditText: EditText
    private lateinit var recentShelvesRecyclerView: RecyclerView
    private lateinit var browseShelvesButton: MaterialButton
    private lateinit var addShelfButton: MaterialButton
    private lateinit var myLocationButton: MaterialButton
    private lateinit var recentShelvesButton: MaterialButton
    private lateinit var arNavigationButton: MaterialButton
    private lateinit var mapViewButton: MaterialButton
    private lateinit var viewAllButton: TextView

    // Remove mock data for recent shelves
    private val recentShelves = emptyList<Shelf>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        
        // Initialize views
        searchEditText = view.findViewById(R.id.et_search)
        recentShelvesRecyclerView = view.findViewById(R.id.rv_recent_shelves)
        browseShelvesButton = view.findViewById(R.id.btn_browse_shelves)
        addShelfButton = view.findViewById(R.id.btn_add_shelf)
        myLocationButton = view.findViewById(R.id.btn_my_location)
        recentShelvesButton = view.findViewById(R.id.btn_recent_shelves)
        arNavigationButton = view.findViewById(R.id.btn_ar_navigation)
        mapViewButton = view.findViewById(R.id.btn_map_view)
        viewAllButton = view.findViewById(R.id.btn_view_all)

        setupRecyclerView()
        setupClickListeners()
        setupSearch()
        
        return view
    }

    private fun setupRecyclerView() {
        recentShelvesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        recentShelvesRecyclerView.adapter = RecentShelfAdapter(recentShelves) { shelf ->
            // Navigate to maps fragment to show shelf location
            findNavController().navigate(R.id.action_homeFragment_to_mapsFragment)
        }
        // Show empty state if no shelves
        if (recentShelves.isEmpty()) {
            recentShelvesRecyclerView.visibility = View.GONE
            // Optionally show a TextView with 'No recent shelves' (add to layout if not present)
            view?.findViewById<TextView>(R.id.tv_no_recent_shelves)?.visibility = View.VISIBLE
        } else {
            recentShelvesRecyclerView.visibility = View.VISIBLE
            view?.findViewById<TextView>(R.id.tv_no_recent_shelves)?.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        // Go to Shelf
        browseShelvesButton.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_mapsFragment)
        }
        // New Shelf Add
        addShelfButton.setOnClickListener {
            // Prompt for shelf name before navigating
            val context = requireContext()
            val input = EditText(context)
            input.hint = "Shelf name"
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("Enter Shelf Name")
                .setView(input)
                .setPositiveButton("OK") { _, _ ->
                    val shelfName = input.text.toString().trim()
                    if (shelfName.isNotEmpty()) {
                        val action = HomeFragmentDirections.actionHomeFragmentToArFragment(createMode = true, shelfName = shelfName)
                        findNavController().navigate(action)
                    } else {
                        Toast.makeText(context, "Shelf name required", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        // My Location
        myLocationButton.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_mapsFragment)
        }
        // Recent Shelves (no-op or show message)
        recentShelvesButton.setOnClickListener {
            Toast.makeText(requireContext(), "No recent shelves", Toast.LENGTH_SHORT).show()
        }
        // AR Navigation
        arNavigationButton.setOnClickListener {
            val action = HomeFragmentDirections.actionHomeFragmentToArFragment(createMode = false)
            findNavController().navigate(action)
        }
        // Map View
        mapViewButton.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_mapsFragment)
        }
        // View All
        viewAllButton.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_mapsFragment)
        }
    }

    private fun setupSearch() {
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = searchEditText.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query)
                }
                true
            } else {
                false
            }
        }
    }

    private fun performSearch(query: String) {
        // TODO: Implement search functionality
        Toast.makeText(requireContext(), "Searching for: $query", Toast.LENGTH_SHORT).show()
    }

    // Data class for recent shelf
    data class Shelf(val name: String, val description: String, val lastVisited: String)

    // RecyclerView Adapter for recent shelves
    class RecentShelfAdapter(
        private val shelves: List<Shelf>,
        private val onShelfClick: (Shelf) -> Unit
    ) : RecyclerView.Adapter<RecentShelfAdapter.ShelfViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShelfViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_shelf, parent, false)
            return ShelfViewHolder(view)
        }

        override fun onBindViewHolder(holder: ShelfViewHolder, position: Int) {
            val shelf = shelves[position]
            holder.bind(shelf, onShelfClick)
        }

        override fun getItemCount() = shelves.size

        class ShelfViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            fun bind(shelf: Shelf, onClick: (Shelf) -> Unit) {
                val nameView = itemView.findViewById<TextView>(R.id.shelf_name)
                val descView = itemView.findViewById<TextView>(R.id.shelf_desc)
                val timeView = itemView.findViewById<TextView>(R.id.shelf_time)

                nameView.text = shelf.name
                descView.text = shelf.description
                timeView.text = shelf.lastVisited

                itemView.setOnClickListener { onClick(shelf) }
            }
        }
    }
} 