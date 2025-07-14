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
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import de.morhenn.ar_navigation.util.GeoUtils
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import okhttp3.*
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody.Part
import android.util.Log
import android.widget.Button
import androidx.navigation.fragment.findNavController

class HomeFragment : Fragment(), OnMapReadyCallback {
    private lateinit var recentShelvesRecyclerView: RecyclerView
    private lateinit var browseShelvesButton: MaterialButton
    private lateinit var addShelfButton: MaterialButton
    private lateinit var arNavigationButton: MaterialButton
    private lateinit var viewAllButton: ImageView
    private lateinit var billToShelvesButton: MaterialButton
    private lateinit var listToShelvesButton: MaterialButton
    private lateinit var gpsButton: MaterialButton
    private var photoUri: Uri? = null
    private lateinit var takePictureLauncher: androidx.activity.result.ActivityResultLauncher<Uri>

    private val viewModel: MainViewModel by viewModels()
    private lateinit var recentShelvesAdapter: RecentShelfAdapter
    private var shelves: List<Place> = emptyList()
    private var mapPreview: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userLatLng: LatLng? = null
    // Buffer for last received shopping list
    private var lastShoppingListJson: String? = null
    private var showAllShelvesOnMap = false

    // Hardcoded AR directions for demo shelves
    data class ShelfWithDirections(val name: String, val description: String, val directions: List<Direction>)
    enum class Direction { STRAIGHT, LEFT, RIGHT }
    private val hardcodedShelves = listOf(
        ShelfWithDirections(
            name = "Electronics",
            description = "Find electronics section",
            directions = listOf(Direction.STRAIGHT, Direction.STRAIGHT, Direction.LEFT)
        ),
        ShelfWithDirections(
            name = "Grocery",
            description = "Find grocery section",
            directions = listOf(Direction.STRAIGHT, Direction.STRAIGHT, Direction.RIGHT)
        ),
        ShelfWithDirections(
            name = "Clothes",
            description = "Find clothes section",
            directions = listOf(Direction.STRAIGHT, Direction.STRAIGHT, Direction.STRAIGHT)
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        
        // Initialize views
        recentShelvesRecyclerView = view.findViewById(R.id.rv_recent_shelves)
        browseShelvesButton = view.findViewById(R.id.btn_browse_shelves)
        addShelfButton = view.findViewById(R.id.btn_add_shelf)
        arNavigationButton = view.findViewById(R.id.bottom_btn_ar_navigation)
        viewAllButton = view.findViewById(R.id.btn_view_all)
        billToShelvesButton = view.findViewById(R.id.btn_list_to_shelves)
        listToShelvesButton = view.findViewById(R.id.btn_list_to_shelves)
        gpsButton = view.findViewById(R.id.btn_center_map)

        setupRecyclerView()
        setupClickListeners()
        observeShelves()
        
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        val mapPreviewFragment = childFragmentManager.findFragmentById(R.id.map_preview_fragment) as? SupportMapFragment
        mapPreviewFragment?.getMapAsync(this)
        fetchUserLocationAndUpdateMap()
        takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && photoUri != null) {
                // Next step: send image to API and preview result
                handleCapturedImage(photoUri!!)
            } else {
                Toast.makeText(requireContext(), "Image capture cancelled", Toast.LENGTH_SHORT).show()
            }
        }
        // Show or hide the continue button based on buffer
        val continueBtn = view.findViewById<Button>(R.id.btn_continue_shopping_list)
        if (lastShoppingListJson != null) {
            continueBtn.visibility = View.VISIBLE
        } else {
            continueBtn.visibility = View.GONE
        }
        continueBtn.setOnClickListener {
            continueWithLastShoppingList()
        }
    }

    private fun fetchUserLocationAndUpdateMap() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    userLatLng = LatLng(location.latitude, location.longitude)
                    mapPreview?.let { updateMapMarkers(it) }
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mapPreview = googleMap
        googleMap.uiSettings.setAllGesturesEnabled(true)
        googleMap.uiSettings.isMapToolbarEnabled = false
        googleMap.uiSettings.isMyLocationButtonEnabled = false
        fetchUserLocationAndUpdateMap()
        userLatLng?.let {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 17f))
        }
        showHardcodedShelvesOnMap(googleMap)
    }

    private fun showHardcodedShelvesOnMap(googleMap: GoogleMap) {
        googleMap.clear()
        val user = userLatLng
        if (user != null) {
            // Show user marker
            googleMap.addMarker(MarkerOptions().position(user).title("You").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))
            // Hardcoded shelves near user
            val electronics = GeoUtils.getLatLngByDistanceAndBearing(user.latitude, user.longitude, 0.0, 0.00008) // ~8m north
            val grocery = GeoUtils.getLatLngByDistanceAndBearing(user.latitude, user.longitude, 120.0, 0.00009) // ~9m east
            val clothes = GeoUtils.getLatLngByDistanceAndBearing(user.latitude, user.longitude, 240.0, 0.0001) // ~10m southwest
            val shelves = listOf(
                Triple(electronics, "Electronics Shelf", BitmapDescriptorFactory.HUE_BLUE),
                Triple(grocery, "Grocery Shelf", BitmapDescriptorFactory.HUE_GREEN),
                Triple(clothes, "Clothes Shelf", BitmapDescriptorFactory.HUE_ORANGE)
            )
            // Only show the shelf that is within 100 meters and closest
            val closest = shelves
                .map { Triple(it.first, it.second, it.third) to GeoUtils.distanceBetweenTwoCoordinates(user, it.first) }
                .filter { it.second <= 100.0 }
                .minByOrNull { it.second }
            closest?.let {
                googleMap.addMarker(
                    MarkerOptions().position(it.first.first).title(it.first.second).icon(BitmapDescriptorFactory.defaultMarker(it.first.third))
                )
            }
        }
    }

    private fun updateMapMarkers(googleMap: GoogleMap) {
        googleMap.clear()
        val user = userLatLng
        if (user != null) {
            // Show user marker
            googleMap.addMarker(MarkerOptions().position(user).title("You").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))
            // Show only shelves within 500 meters
            val nearbyShelves = shelves.filter {
                GeoUtils.distanceBetweenTwoCoordinates(user, LatLng(it.lat, it.lng)) <= 500.0
            }
            for (shelf in nearbyShelves) {
                googleMap.addMarker(MarkerOptions().position(LatLng(shelf.lat, shelf.lng)).title(shelf.name))
            }
        }
    }

    private fun observeShelves() {
        // Instead of fetching from DB, show hardcoded shelves with AR directions
        val demoPlaces = hardcodedShelves.mapIndexed { idx, shelf ->
            Place(
                id = idx.toString(),
                name = shelf.name,
                lat = 0.0,
                lng = 0.0,
                alt = 0.0,
                heading = 0.0,
                description = shelf.description,
                author = "",
                ardata = shelf.directions.joinToString(",") { it.name }
            )
        }
        shelves = demoPlaces
        recentShelvesAdapter.updateShelves(shelves)
        recentShelvesRecyclerView.visibility = View.VISIBLE
        view?.findViewById<TextView>(R.id.tv_no_recent_shelves)?.visibility = View.GONE
    }

    private fun setupRecyclerView() {
        recentShelvesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        recentShelvesAdapter = RecentShelfAdapter(shelves) { shelf ->
            // Launch AR navigation with hardcoded directions for this shelf
            val directions = when (shelf.name.trim().lowercase()) {
                "electronics" -> "STRAIGHT,STRAIGHT,LEFT"
                "grocery" -> "STRAIGHT,STRAIGHT,RIGHT"
                "clothes" -> "STRAIGHT,STRAIGHT,STRAIGHT"
                else -> ""
            }
            findNavController().navigate(R.id.arFragment, Bundle().apply {
                putBoolean("createMode", false)
                putString("shelfName", shelf.name)
                putString("arDirections", directions)
            })
        }
        recentShelvesRecyclerView.adapter = recentShelvesAdapter
    }

    private fun createImageFile(): File? {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = requireContext().getExternalFilesDir(null)
        return try {
            File.createTempFile(
                "JPEG_${timeStamp}_", ".jpg", storageDir
            )
        } catch (ex: IOException) {
            null
        }
    }

    private fun launchCamera() {
        val photoFile = createImageFile()
        if (photoFile != null) {
            photoUri = FileProvider.getUriForFile(
                requireContext(),
                requireContext().packageName + ".provider",
                photoFile
            )
            takePictureLauncher.launch(photoUri)
        } else {
            Toast.makeText(requireContext(), "Failed to create image file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCapturedImage(imageUri: Uri) {
        // Show loading toast
        Toast.makeText(requireContext(), "Uploading image...", Toast.LENGTH_SHORT).show()
        // Launch in background
        Thread {
            try {
                val inputStream: InputStream? = requireContext().contentResolver.openInputStream(imageUri)
                val imageBytes = inputStream?.readBytes()
                inputStream?.close()
                if (imageBytes == null || imageBytes.isEmpty()) {
                    showErrorOnMainThread("Failed to read image or image is empty")
                    return@Thread
                }
                val mimeType = requireContext().contentResolver.getType(imageUri) ?: "image/jpeg"
                Log.d("UploadDebug", "Image URI: $imageUri, size: ${imageBytes.size}, mimeType: $mimeType")
                val fileRequestBody = RequestBody.create(mimeType.toMediaType(), imageBytes)
                val multipartBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "image", "bill.jpg", fileRequestBody
                    )
                    .build()
                val request = Request.Builder()
                    .url("https://lynx-proto.onrender.com/extract-shopping-list")
                    .post(multipartBody)
                    .build()
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: response.message
                    showErrorOnMainThread("Upload failed: ${response.code}\n$errorBody")
                    return@Thread
                }
                val responseBody = response.body?.string()
                if (responseBody == null) {
                    showErrorOnMainThread("Empty response from server")
                    return@Thread
                }
                // Save to buffer
                lastShoppingListJson = responseBody
                requireActivity().runOnUiThread {
                    val continueBtn = view?.findViewById<Button>(R.id.btn_continue_shopping_list)
                    continueBtn?.visibility = View.VISIBLE
                }
                navigateToShoppingListResult(responseBody)
            } catch (e: Exception) {
                showErrorOnMainThread("Error: ${e.localizedMessage}")
            }
        }.start()
    }

    // Call this to continue with the last shopping list if available
    private fun continueWithLastShoppingList() {
        lastShoppingListJson?.let {
            navigateToShoppingListResult(it)
        } ?: run {
            Toast.makeText(requireContext(), "No shopping list to continue.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToShoppingListResult(jsonString: String) {
        requireActivity().runOnUiThread {
            val bundle = Bundle().apply {
                putString("shopping_list_json", jsonString)
            }
            findNavController().navigate(R.id.action_homeFragment_to_shoppingListResultFragment, bundle)
        }
    }

    private fun showErrorOnMainThread(message: String) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    private fun setupClickListeners() {
        // Go to Shelf now toggles all shelf markers
        browseShelvesButton.setOnClickListener {
            showAllShelvesOnMap = !showAllShelvesOnMap
            mapPreview?.let { map ->
                if (showAllShelvesOnMap) {
                    showAllHardcodedShelvesOnMap(map)
                } else {
                    showHardcodedShelvesOnMap(map)
                }
            }
        }
        // GPS button now centers map on user location
        gpsButton.setOnClickListener {
            if (userLatLng != null && mapPreview != null) {
                mapPreview?.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng!!, 17f))
            } else {
                Toast.makeText(requireContext(), "Location not available", Toast.LENGTH_SHORT).show()
            }
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
            // Show modern bottom sheet for shelf selection (same as before)
            val dialog = BottomSheetDialog(requireContext())
            val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_select_shelf, null)
            val electronics = sheetView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.option_electronics)
            val grocery = sheetView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.option_grocery)
            val clothes = sheetView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.option_clothes)
            electronics.setOnClickListener {
                // Open AR with camera and hardcoded arrows for electronics
                findNavController().navigate(R.id.arFragment, Bundle().apply {
                    putBoolean("createMode", true)
                    putBoolean("navOnly", true)
                    putString("shelfName", "Electronics")
                    putString("arDirections", "STRAIGHT,STRAIGHT,LEFT")
                })
                dialog.dismiss()
            }
            grocery.setOnClickListener {
                // Open AR with camera and hardcoded arrows for grocery
                findNavController().navigate(R.id.arFragment, Bundle().apply {
                    putBoolean("createMode", true)
                    putBoolean("navOnly", true)
                    putString("shelfName", "Grocery")
                    putString("arDirections", "STRAIGHT,STRAIGHT,RIGHT")
                })
                dialog.dismiss()
            }
            clothes.setOnClickListener {
                // Open AR with camera and hardcoded arrows for clothes
                findNavController().navigate(R.id.arFragment, Bundle().apply {
                    putBoolean("createMode", true)
                    putBoolean("navOnly", true)
                    putString("shelfName", "Clothes")
                    putString("arDirections", "STRAIGHT,STRAIGHT,STRAIGHT")
                })
                dialog.dismiss()
            }
            dialog.setContentView(sheetView)
            dialog.show()
        }
        // View All
        viewAllButton.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_mapsFragment)
        }
        listToShelvesButton.setOnClickListener {
            launchCamera()
        }
    }

    // Add this helper to show all hardcoded shelves
    private fun showAllHardcodedShelvesOnMap(googleMap: GoogleMap) {
        googleMap.clear()
        val user = userLatLng
        if (user != null) {
            // Show user marker
            googleMap.addMarker(MarkerOptions().position(user).title("You").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))
            // Place all shelves at least 40m apart
            val electronics = GeoUtils.getLatLngByDistanceAndBearing(user.latitude, user.longitude, 0.0, 0.00040) // ~40m north
            val grocery = GeoUtils.getLatLngByDistanceAndBearing(user.latitude, user.longitude, 90.0, 0.00080) // ~80m east
            val clothes = GeoUtils.getLatLngByDistanceAndBearing(user.latitude, user.longitude, 210.0, 0.00120) // ~120m southwest
            val shelves = listOf(
                Triple(electronics, "Electronics Shelf", BitmapDescriptorFactory.HUE_BLUE),
                Triple(grocery, "Grocery Shelf", BitmapDescriptorFactory.HUE_GREEN),
                Triple(clothes, "Clothes Shelf", BitmapDescriptorFactory.HUE_ORANGE)
            )
            for (shelf in shelves) {
                googleMap.addMarker(
                    MarkerOptions().position(shelf.first).title(shelf.second).icon(BitmapDescriptorFactory.defaultMarker(shelf.third))
                )
            }
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
                // Show AR directions visually
                val directionsLayout = itemView.findViewById<LinearLayout?>(R.id.directions_layout)
                directionsLayout?.removeAllViews()
                val directions = shelf.ardata?.split(",") ?: emptyList()
                for (dir in directions) {
                    val icon = ImageView(itemView.context)
                    when (dir.trim().uppercase()) {
                        "STRAIGHT" -> icon.setImageResource(R.drawable.ic_baseline_arrow_upward_24)
                        "LEFT" -> icon.setImageResource(R.drawable.ic_baseline_arrow_left_48)
                        "RIGHT" -> icon.setImageResource(R.drawable.ic_baseline_arrow_right_48)
                    }
                    val params = LinearLayout.LayoutParams(36, 36)
                    params.setMargins(4, 0, 4, 0)
                    icon.layoutParams = params
                    icon.scaleType = ImageView.ScaleType.FIT_CENTER
                    icon.adjustViewBounds = true
                    directionsLayout?.addView(icon)
                }
                navButton.setOnClickListener {
                    // Open AR in hardcoded navigation mode (camera open, arrows shown, no editing)
                    val directions = when (shelf.name.trim().lowercase()) {
                        "electronics" -> "STRAIGHT,STRAIGHT,LEFT"
                        "grocery" -> "STRAIGHT,STRAIGHT,RIGHT"
                        "clothes" -> "STRAIGHT,STRAIGHT,STRAIGHT"
                        else -> ""
                    }
                    androidx.navigation.Navigation.findNavController(itemView).navigate(R.id.arFragment, Bundle().apply {
                        putBoolean("createMode", true)
                        putBoolean("navOnly", true)
                        putString("shelfName", shelf.name)
                        putString("arDirections", directions)
                    })
                }
            }
        }
    }
} 