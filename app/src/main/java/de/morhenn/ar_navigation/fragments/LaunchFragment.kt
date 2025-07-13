package de.morhenn.ar_navigation.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import de.morhenn.ar_navigation.R
import android.app.AlertDialog
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.view.isVisible

class LaunchFragment : Fragment(), OnMapReadyCallback {
    private lateinit var map: GoogleMap
    private lateinit var fabAddShelf: FloatingActionButton
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var shelvesRecyclerView: RecyclerView
    private lateinit var navigateButton: MaterialButton
    private var selectedShelf: Shelf? = null
    private var shelfMarkers: MutableList<Marker> = mutableListOf()
    private var userMarker: Marker? = null
    private var userLocation: LatLng? = null

    // Mock data for shelves (replace with DB integration)
    private val shelves = mutableListOf(
        Shelf("Aisle 1", "Snacks & Chips", LatLng(37.4219999, -122.0840575)),
        Shelf("Aisle 2", "Beverages", LatLng(37.4221, -122.0842)),
        Shelf("Aisle 3", "Dairy", LatLng(37.4222, -122.0843))
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_launch, container, false)
        fabAddShelf = view.findViewById(R.id.fab_add_shelf)
        shelvesRecyclerView = view.findViewById(R.id.rv_shelves)
        navigateButton = view.findViewById(R.id.btn_navigate)
        val bottomSheet = view.findViewById<View>(R.id.shelf_bottom_sheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        shelvesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        shelvesRecyclerView.adapter = ShelfAdapter(shelves) { shelf ->
            selectShelf(shelf)
        }
        navigateButton.setOnClickListener {
            selectedShelf?.let { launchARNavigation(it) }
        }
        fabAddShelf.setOnClickListener { showAddShelfDialog() }
        setupMap()
        return view
    }

    private fun setupMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map_fragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isMyLocationButtonEnabled = true
        enableLocation()
        showShelvesOnMap()
        map.setOnMarkerClickListener { marker ->
            val shelf = marker.tag as? Shelf
            if (shelf != null) {
                selectShelf(shelf)
                true
            } else {
                false
            }
        }
    }

    private fun enableLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map.isMyLocationEnabled = true
            // TODO: Get actual user location
            // For now, use a mock location
            userLocation = LatLng(37.4219999, -122.0840575)
            val safeUserLocation = userLocation
            if (safeUserLocation != null) {
                userMarker = map.addMarker(
                    MarkerOptions().position(safeUserLocation).title("You").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                )
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(safeUserLocation, 17f))
            }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            enableLocation()
        } else {
            Toast.makeText(requireContext(), "Location permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showShelvesOnMap() {
        shelfMarkers.forEach { it.remove() }
        shelfMarkers.clear()
        for (shelf in shelves) {
            val marker = map.addMarker(
                MarkerOptions().position(shelf.location).title(shelf.name).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
            )
            marker?.tag = shelf
            marker?.let { shelfMarkers.add(it) }
        }
    }

    private fun selectShelf(shelf: Shelf) {
        selectedShelf = shelf
        (shelvesRecyclerView.adapter as? ShelfAdapter)?.setSelectedShelf(shelf)
        navigateButton.isEnabled = true
    }

    private fun launchARNavigation(shelf: Shelf) {
        // TODO: Replace with actual AR navigation launch
        // Example: navigate to AugmentedRealityFragment with shelf location as argument
        Toast.makeText(requireContext(), "Launching AR navigation to ${shelf.name}", Toast.LENGTH_SHORT).show()
        // Example navigation (replace with your actual navigation logic):
        // findNavController().navigate(R.id.action_launchFragment_to_augmentedRealityFragment, bundleOf("targetLat" to shelf.location.latitude, "targetLng" to shelf.location.longitude))
    }

    private fun showAddShelfDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_shelf, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.input_shelf_name)
        val descInput = dialogView.findViewById<TextInputEditText>(R.id.input_shelf_desc)
        AlertDialog.Builder(requireContext())
            .setTitle("Add Shelf")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text?.toString()?.trim()
                val desc = descInput.text?.toString()?.trim()
                val safeUserLocation = userLocation
                if (!name.isNullOrEmpty() && safeUserLocation != null) {
                    val newShelf = Shelf(name, desc ?: "", safeUserLocation)
                    shelves.add(0, newShelf)
                    showShelvesOnMap()
                    shelvesRecyclerView.adapter?.notifyDataSetChanged()
                } else {
                    Toast.makeText(requireContext(), "Name and location required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Data class for shelf
    data class Shelf(val name: String, val description: String, val location: LatLng)

    // RecyclerView Adapter for shelves
    class ShelfAdapter(
        private val shelves: List<Shelf>,
        private val onShelfClick: (Shelf) -> Unit
    ) : RecyclerView.Adapter<ShelfAdapter.ShelfViewHolder>() {
        private var selectedShelf: Shelf? = null
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShelfViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_shelf, parent, false)
            return ShelfViewHolder(view)
        }
        override fun onBindViewHolder(holder: ShelfViewHolder, position: Int) {
            val shelf = shelves[position]
            holder.bind(shelf, shelf == selectedShelf, onShelfClick)
        }
        override fun getItemCount() = shelves.size
        fun setSelectedShelf(shelf: Shelf) {
            selectedShelf = shelf
            notifyDataSetChanged()
        }
        class ShelfViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            fun bind(shelf: Shelf, selected: Boolean, onClick: (Shelf) -> Unit) {
                val nameView = itemView.findViewById<TextView>(R.id.shelf_name)
                val descView = itemView.findViewById<TextView>(R.id.shelf_desc)
                nameView.text = shelf.name
                descView.text = shelf.description
                itemView.setBackgroundResource(if (selected) R.drawable.rounded_corners_highlighted else R.drawable.rounded_corners)
                itemView.setOnClickListener { onClick(shelf) }
            }
        }
    }
} 