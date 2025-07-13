package de.morhenn.ar_navigation.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import de.morhenn.ar_navigation.MainViewModel
import de.morhenn.ar_navigation.R
import de.morhenn.ar_navigation.persistance.Place
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class HomeFragment : Fragment(), OnMapReadyCallback {
    private lateinit var recentShelvesRecyclerView: RecyclerView
    private lateinit var browseShelvesButton: MaterialButton
    private lateinit var addShelfButton: MaterialButton
    private lateinit var arNavigationButton: MaterialButton
    private lateinit var mapViewButton: MaterialButton
    private lateinit var viewAllButton: TextView
    private lateinit var billToShelvesButton: MaterialButton

    private val viewModel: MainViewModel by viewModels()
    private lateinit var recentShelvesAdapter: RecentShelfAdapter
    private var shelves: List<Place> = emptyList()
    private var mapPreview: GoogleMap? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        
        // Initialize views
        recentShelvesRecyclerView = view.findViewById(R.id.rv_recent_shelves)
        browseShelvesButton = view.findViewById(R.id.btn_browse_shelves)
        addShelfButton = view.findViewById(R.id.btn_add_shelf)
        arNavigationButton = view.findViewById(R.id.btn_ar_navigation)
        mapViewButton = view.findViewById(R.id.btn_map_view)
        viewAllButton = view.findViewById(R.id.btn_view_all)
        billToShelvesButton = view.findViewById(R.id.btn_bill_to_shelves)

        setupRecyclerView()
        setupClickListeners()
        observeShelves()
        
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Setup map preview
        val mapPreviewFragment = childFragmentManager.findFragmentById(R.id.map_preview_fragment) as? SupportMapFragment
        mapPreviewFragment?.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mapPreview = googleMap
        // Set preview location (e.g., center of a store or city)
        val previewLatLng = LatLng(37.4221, -122.0841) // Example: Googleplex
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(previewLatLng, 17f))
        // Enable gestures so user can pan and zoom to see markers
        googleMap.uiSettings.setAllGesturesEnabled(true)
        googleMap.uiSettings.isMapToolbarEnabled = false
        googleMap.uiSettings.isMyLocationButtonEnabled = false

        // Add preview shelf markers
        googleMap.addMarker(MarkerOptions().position(LatLng(37.4221, -122.0841)).title("Electronics Shelf"))
        googleMap.addMarker(MarkerOptions().position(LatLng(37.4222, -122.0842)).title("Grocery Shelf"))
        googleMap.addMarker(MarkerOptions().position(LatLng(37.4223, -122.0843)).title("Clothes Shelf"))
    }

    private fun observeShelves() {
        viewModel.places.observe(viewLifecycleOwner) { places ->
            shelves = places.sortedByDescending { it.id } // or use a timestamp if available
            recentShelvesAdapter.updateShelves(shelves)
            if (shelves.isEmpty()) {
            recentShelvesRecyclerView.visibility = View.GONE
            view?.findViewById<TextView>(R.id.tv_no_recent_shelves)?.visibility = View.VISIBLE
        } else {
            recentShelvesRecyclerView.visibility = View.VISIBLE
            view?.findViewById<TextView>(R.id.tv_no_recent_shelves)?.visibility = View.GONE
        }
        }
        viewModel.fetchPlaces()
    }

    private fun setupRecyclerView() {
        recentShelvesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        recentShelvesAdapter = RecentShelfAdapter(shelves) { shelf ->
            // Navigate to AR with only allowed parameters
            val action = HomeFragmentDirections.actionHomeFragmentToArFragment(createMode = false, shelfName = shelf.name)
            findNavController().navigate(action)
        }
        recentShelvesRecyclerView.adapter = recentShelvesAdapter
    }

    private fun setupClickListeners() {
        // Go to Shelf
        browseShelvesButton.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_mapsFragment)
        }
        // New Shelf Add
        addShelfButton.setOnClickListener {
            // Show modern dialog for shelf name entry
            val context = requireContext()
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_enter_shelf_name, null)
            val inputLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.input_shelf_name_layout)
            val input = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.input_shelf_name)
            val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
                .setView(dialogView)
                .setPositiveButton("OK", null)
                .setNegativeButton("Cancel", null)
                .create()
            dialog.setOnShowListener {
                val button = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                button.setOnClickListener {
                    val shelfName = input.text?.toString()?.trim() ?: ""
                    if (shelfName.isNotEmpty()) {
                        val action = HomeFragmentDirections.actionHomeFragmentToArFragment(createMode = true, shelfName = shelfName)
                        findNavController().navigate(action)
                        dialog.dismiss()
                    } else {
                        inputLayout.error = "Shelf name required"
                    }
                }
            }
            dialog.show()
        }
        // Bill to Shelves (Image to Text to Navigate) - TODO
        billToShelvesButton.setOnClickListener {
            Toast.makeText(requireContext(), "Bill to Shelves (Image to Text to Navigate) - TODO", Toast.LENGTH_SHORT).show()
        }
        // AR Navigation
        arNavigationButton.setOnClickListener {
            // Show modern bottom sheet for shelf selection
            val dialog = BottomSheetDialog(requireContext())
            val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_select_shelf, null)
            val electronics = sheetView.findViewById<LinearLayout>(R.id.option_electronics)
            val grocery = sheetView.findViewById<LinearLayout>(R.id.option_grocery)
            val clothes = sheetView.findViewById<LinearLayout>(R.id.option_clothes)
            electronics.setOnClickListener {
                Toast.makeText(requireContext(), "Navigate to: Electronics Shelf (TODO)", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            grocery.setOnClickListener {
                Toast.makeText(requireContext(), "Navigate to: Grocery Shelf (TODO)", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            clothes.setOnClickListener {
                Toast.makeText(requireContext(), "Navigate to: Clothes Shelf (TODO)", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            dialog.setContentView(sheetView)
            dialog.show()
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

    // RecyclerView Adapter for recent shelves
    class RecentShelfAdapter(
        private var shelves: List<Place>,
        private val onShelfClick: (Place) -> Unit
    ) : RecyclerView.Adapter<RecentShelfAdapter.ShelfViewHolder>() {
        fun updateShelves(newShelves: List<Place>) {
            this.shelves = newShelves
            notifyDataSetChanged()
        }
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
            fun bind(shelf: Place, onClick: (Place) -> Unit) {
                val nameView = itemView.findViewById<TextView>(R.id.shelf_name)
                val descView = itemView.findViewById<TextView>(R.id.shelf_desc)
                val navButton = itemView.findViewById<MaterialButton>(R.id.btn_navigate)
                nameView.text = shelf.name
                descView.text = shelf.description
                navButton.setOnClickListener { onClick(shelf) }
            }
        }
    }
} 