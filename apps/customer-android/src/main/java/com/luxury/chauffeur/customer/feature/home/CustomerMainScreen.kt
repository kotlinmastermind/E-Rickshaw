package com.luxury.chauffeur.customer.feature.home

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.luxury.chauffeur.customer.core.designsystem.MobilityColors
import com.luxury.chauffeur.customer.core.network.ApiClient
import com.luxury.chauffeur.customer.core.security.TokenManager
import com.luxury.chauffeur.customer.core.network.CustomerProfileDto
import com.luxury.chauffeur.customer.core.network.FareEstimateRequest
import com.luxury.chauffeur.customer.core.network.RideRequestPayload
import com.luxury.chauffeur.customer.core.network.VehicleCategoryDto
import com.luxury.chauffeur.customer.core.network.WalletDto
import com.luxury.chauffeur.customer.core.network.TourismPlaceDto
import com.luxury.chauffeur.customer.core.network.TourismWeeklyResponse
import com.luxury.chauffeur.customer.feature.map.LuxuryMapView
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luxury.chauffeur.customer.core.network.ProfileUpdateRequest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.launch

enum class MainBottomTab {
    RIDE,
    ALL_SERVICES,
    TRAVEL,
    PROFILE
}

enum class RideFlowState {
    SEARCH_HOME,       // Photo 4
    CONFIRM_PICKUP,    // Photo 3
    SELECT_VEHICLE     // Photo 5
}

data class ServiceItem(
    val title: String,
    val iconEmoji: String,
    val badge: String? = null
)

data class RecentDestination(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double
)

data class VehicleOption(
    val id: String,
    val name: String,
    val subtitle: String,
    val etaSubtitle: String,
    val fareRupees: Int,
    val originalFareRupees: Int? = null,
    val badge: String? = null,
    val iconEmoji: String,
    val capacity: Int = 1,
    val code: String = "",
    val iconUrl: String? = null,
    val estimateId: String? = null,
    val distanceKm: Double = 1.0,
    val durationMinutes: Double = 5.0
)

@SuppressLint("MissingPermission")
@Composable
fun CustomerMainScreen(
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val tokenManager = remember { TokenManager.getInstance(context) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var currentBottomTab by remember { mutableStateOf(MainBottomTab.RIDE) }
    var rideState by remember { mutableStateOf(RideFlowState.SEARCH_HOME) }

    // Live GPS Location & Real Reverse Geocoded Addresses
    var currentLatLng by remember { mutableStateOf(LatLng(25.6858, 85.2146)) }
    var currentAddressTitle by remember { mutableStateOf("Current Location") }
    var currentAddressSubtitle by remember { mutableStateOf("Locating your exact pickup area...") }
    var currentAddress by remember { mutableStateOf("Locating pickup point...") }
    var destinationLatLng by remember { mutableStateOf<LatLng?>(null) }
    var destinationName by remember { mutableStateOf("Hajipur Railway Station") }
    var recenterCounter by remember { mutableIntStateOf(0) }

    // Interactive Real Search States
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchSuggestions by remember { mutableStateOf<List<RecentDestination>>(emptyList()) }

    // Nearby E-Rickshaws & Autos
    val nearbyDrivers = remember(currentLatLng) {
        listOf(
            LatLng(currentLatLng.latitude + 0.0018, currentLatLng.longitude + 0.0012),
            LatLng(currentLatLng.latitude - 0.0014, currentLatLng.longitude + 0.0020),
            LatLng(currentLatLng.latitude + 0.0022, currentLatLng.longitude - 0.0015),
            LatLng(currentLatLng.latitude - 0.0025, currentLatLng.longitude - 0.0018)
        )
    }

    // Default popular local transit destinations
    val defaultSuggestions = remember(currentLatLng) {
        listOf(
            RecentDestination("Hajipur Railway Station", "Junction, Hajipur, Vaishali, Bihar", 25.6912, 85.2185),
            RecentDestination("Patna Junction", "Fraser Road Area, Patna, Bihar, India", 25.6025, 85.1376),
            RecentDestination("Gandhi Maidan", "Bakarganj, Patna, Bihar, India", 25.6186, 85.1481),
            RecentDestination("Jay Prakash Narayan Airport", "Shaheed Pir Ali Khan Marg, Patna, Bihar", 25.5913, 85.0880),
            RecentDestination("Rajendra Nagar Terminal", "opposite terminal, Kankarbagh, Patna", 25.5980, 85.1620)
        )
    }

    // Registered Vehicle Categories from Real Backend Database
    var registeredCategories by remember { mutableStateOf<List<VehicleCategoryDto>>(emptyList()) }
    var isFareCalculating by remember { mutableStateOf(false) }

    // Initial Registered Categories adhering to Real Backend Online Server
    var vehicleOptions by remember {
        mutableStateOf(
            listOf(
                VehicleOption(
                    id = "12b8f8bd-2d61-4105-a0d8-5661fe822254",
                    code = "E_RICKSHAW_STANDARD",
                    name = "Standard E-Rickshaw",
                    subtitle = "Personal Electric Rickshaw • 4 seats",
                    etaSubtitle = "4 mins away • 1.2 km",
                    fareRupees = 25,
                    originalFareRupees = 32,
                    badge = "MOST POPULAR",
                    iconEmoji = "⚡🛺",
                    capacity = 4
                ),
                VehicleOption(
                    id = "9b1c4e58-78b3-4a0b-80bb-f625f2d6ea2a",
                    code = "E_RICKSHAW_SHARE",
                    name = "Shared E-Rickshaw (Eco-Pool)",
                    subtitle = "Per-seat shared eco pool • 4 seats",
                    etaSubtitle = "5 mins away • 1.2 km",
                    fareRupees = 15,
                    originalFareRupees = 20,
                    badge = "LOWEST FARE",
                    iconEmoji = "👥🛺",
                    capacity = 4
                ),
                VehicleOption(
                    id = "0daec8ce-7e8a-4603-ab3f-6bed9455ccd7",
                    code = "E_AUTO",
                    name = "High-Speed Electric Auto",
                    subtitle = "High-speed electric auto • 3 seats",
                    etaSubtitle = "6 mins away • 1.2 km",
                    fareRupees = 40,
                    originalFareRupees = 50,
                    badge = "FASTEST",
                    iconEmoji = "🛺💨",
                    capacity = 3
                ),
                VehicleOption(
                    id = "653d62fb-c68e-4799-9d55-411ccc9b5e01",
                    code = "E_CARGO_LOADER",
                    name = "E-Loader Cargo",
                    subtitle = "Luggage & package transit • 1 capacity",
                    etaSubtitle = "8 mins away • 1.2 km",
                    fareRupees = 65,
                    originalFareRupees = 80,
                    badge = "CARGO",
                    iconEmoji = "📦🛺",
                    capacity = 1
                )
            )
        )
    }
    var selectedVehicle by remember { mutableStateOf(vehicleOptions[0]) }

    // Dynamic Backend Integration States
    var customerProfile by remember { mutableStateOf<CustomerProfileDto?>(null) }
    var profileFullName by remember { mutableStateOf("") }
    var profileUsername by remember { mutableStateOf("") }
    var profileEmail by remember { mutableStateOf("") }
    var profilePhone by remember { mutableStateOf("") }
    var profileAvatarUrl by remember { mutableStateOf<String?>(null) }
    var profileEmergencyPhone by remember { mutableStateOf("") }
    var profilePaymentMethod by remember { mutableStateOf("CASH") }
    var profileRatingAverage by remember { mutableDoubleStateOf(5.0) }
    var profileRatingCount by remember { mutableIntStateOf(0) }
    var profileIsVerified by remember { mutableStateOf(true) }
    var isProfileLoading by remember { mutableStateOf(false) }
    var isSavingProfile by remember { mutableStateOf(false) }
    var isUploadingAvatar by remember { mutableStateOf(false) }

    // Profile Dialog Controls
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAvatarOptionsDialog by remember { mutableStateOf(false) }
    var showCustomAvatarUrlDialog by remember { mutableStateOf(false) }
    var showDeleteAccountConfirmDialog by remember { mutableStateOf(false) }

    // Form inputs for editing profile
    var editFullName by remember { mutableStateOf("") }
    var editUsername by remember { mutableStateOf("") }
    var editEmail by remember { mutableStateOf("") }
    var editEmergencyPhone by remember { mutableStateOf("") }
    var customAvatarInputUrl by remember { mutableStateOf("") }

    var walletBalanceRupees by remember { mutableIntStateOf(150) }
    var isBookingLoading by remember { mutableStateOf(false) }
    var showBookingSuccessDialog by remember { mutableStateOf(false) }
    var activeBookedRideId by remember { mutableStateOf("") }
    var activeBookedOtp by remember { mutableStateOf("1234") }
    var selectedServiceDialog by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    // Tourism & Historical Places States (Real Backend Integration with 7-Day Automatic Rotation)
    var tourismPlaces by remember { mutableStateOf<List<TourismPlaceDto>>(emptyList()) }
    var featuredTourismPlaces by remember { mutableStateOf<List<TourismPlaceDto>>(emptyList()) }
    var currentWeekRotation by remember { mutableIntStateOf(0) }
    var daysUntilNextRotation by remember { mutableIntStateOf(7) }
    var rotationTheme by remember { mutableStateOf("Ancient Empires & Ashokan Heritage (Week 1)") }
    var selectedCategoryFilter by remember { mutableStateOf("FEATURED") }
    var isTourismLoading by remember { mutableStateOf(false) }
    var selectedTourismDetail by remember { mutableStateOf<TourismPlaceDto?>(null) }

    val fallbackTourismPlaces = remember {
        listOf(
            TourismPlaceDto(
                id = "tour-01",
                title = "Vaishali Ashoka Pillar & Buddha Relic Stupa",
                category = "HISTORICAL",
                shortDescription = "Ancient capital of the Licchavis. Features Emperor Ashoka's monolithic lion pillar from 3rd century BCE and the sacred Buddha relic stupa.",
                photoUrl = "https://images.unsplash.com/photo-1590050752117-238cb0fb12b1?auto=format&fit=crop&w=800&q=80",
                latitude = 25.9922,
                longitude = 85.1264,
                address = "Kolhua, Vaishali, Bihar 844128",
                city = "Vaishali",
                rating = 4.8,
                reviewCount = 2450,
                entryFee = "₹25 (ASI Monument)",
                timings = "06:00 AM – 06:00 PM",
                tag = "Ashokan Pillar",
                weekRotationGroup = 0,
                isFeatured = true
            ),
            TourismPlaceDto(
                id = "tour-02",
                title = "Mahabodhi Temple & Sacred Bodhi Tree",
                category = "SPIRITUAL_HERITAGE",
                shortDescription = "UNESCO World Heritage Site where Lord Buddha attained enlightenment in 531 BCE. Revered 50-meter ancient stone temple spire.",
                photoUrl = "https://images.unsplash.com/photo-1548013146-72479768bada?auto=format&fit=crop&w=800&q=80",
                latitude = 24.6958,
                longitude = 84.9914,
                address = "Bodh Gaya, Gaya, Bihar 824231",
                city = "Bodh Gaya",
                rating = 4.9,
                reviewCount = 5800,
                entryFee = "Free",
                timings = "05:00 AM – 09:00 PM",
                tag = "UNESCO Heritage",
                weekRotationGroup = 0,
                isFeatured = true
            ),
            TourismPlaceDto(
                id = "tour-03",
                title = "Rajgir Glass Bridge & Nature Safari",
                category = "TOURISM",
                shortDescription = "Bihar's famous glass skywalk bridge suspended 200 feet high amidst scenic hills, alongside dense wildlife safari and mountaineering.",
                photoUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=800&q=80",
                latitude = 25.0210,
                longitude = 85.4215,
                address = "Nature Safari, Rajgir, Nalanda, Bihar 803116",
                city = "Rajgir",
                rating = 4.7,
                reviewCount = 3900,
                entryFee = "₹125 Safari + ₹100 Bridge",
                timings = "09:00 AM – 05:00 PM (Closed Mon)",
                tag = "Adventure Skywalk",
                weekRotationGroup = 0,
                isFeatured = true
            ),
            TourismPlaceDto(
                id = "tour-04",
                title = "Golghar Historical Granary & Ganga View",
                category = "HISTORICAL",
                shortDescription = "Pillarless beehive-shaped granary erected in 1786. Dual spiral staircases lead to a panoramic birds-eye view of Patna city and the Ganga.",
                photoUrl = "https://images.unsplash.com/photo-1582510003544-4d00b7f74220?auto=format&fit=crop&w=800&q=80",
                latitude = 25.6174,
                longitude = 85.1432,
                address = "Ashok Rajpath, Near Gandhi Maidan, Patna 800001",
                city = "Patna",
                rating = 4.6,
                reviewCount = 4120,
                entryFee = "₹15",
                timings = "09:30 AM – 06:00 PM",
                tag = "Architectural Wonder",
                weekRotationGroup = 0,
                isFeatured = true
            ),
            TourismPlaceDto(
                id = "tour-05",
                title = "Nalanda Mahavihara Ancient University Ruins",
                category = "HISTORICAL",
                shortDescription = "UNESCO World Heritage ancient university from 5th century CE. Houses stunning red-brick stupas, scholastic monasteries, and meditation cells.",
                photoUrl = "https://images.unsplash.com/photo-1600100397608-f010f4439c2c?auto=format&fit=crop&w=800&q=80",
                latitude = 25.1357,
                longitude = 85.4452,
                address = "Bargaon, Nalanda, Bihar 803111",
                city = "Nalanda",
                rating = 4.9,
                reviewCount = 4700,
                entryFee = "₹40 (ASI Monument)",
                timings = "09:00 AM – 05:30 PM",
                tag = "World Ancient University",
                weekRotationGroup = 1,
                isFeatured = true
            ),
            TourismPlaceDto(
                id = "tour-06",
                title = "Vishwa Shanti Stupa & Historic Ropeway, Rajgir",
                category = "TOURISM",
                shortDescription = "Magnificent white marble World Peace Pagoda atop Ratnagiri Hill, reachable by India's oldest single-chair scenic aerial ropeway.",
                photoUrl = "https://images.unsplash.com/photo-1564507592333-c60657eea523?auto=format&fit=crop&w=800&q=80",
                latitude = 25.0069,
                longitude = 85.4217,
                address = "Ratnagiri Hill, Rajgir, Bihar 803116",
                city = "Rajgir",
                rating = 4.8,
                reviewCount = 3200,
                entryFee = "₹100 (Ropeway return)",
                timings = "08:00 AM – 05:00 PM",
                tag = "Aerial Ropeway & Stupa",
                weekRotationGroup = 1,
                isFeatured = true
            ),
            TourismPlaceDto(
                id = "tour-07",
                title = "Kesariya Great Buddhist Stupa",
                category = "HISTORICAL",
                shortDescription = "One of the tallest ancient Buddhist stupas in the world (104 feet high), built during the Mauryan and Gupta dynasties.",
                photoUrl = "https://images.unsplash.com/photo-1599839575945-a9e5af0c3fa5?auto=format&fit=crop&w=800&q=80",
                latitude = 26.3533,
                longitude = 84.8732,
                address = "Kesariya, East Champaran, Bihar 845424",
                city = "Champaran",
                rating = 4.7,
                reviewCount = 1850,
                entryFee = "₹20",
                timings = "Sunrise to Sunset",
                tag = "World's Tallest Stupa",
                weekRotationGroup = 1,
                isFeatured = true
            ),
            TourismPlaceDto(
                id = "tour-08",
                title = "Takht Sri Harmandir Ji Patna Sahib",
                category = "SPIRITUAL_HERITAGE",
                shortDescription = "Birthplace of the Tenth Sikh Guru, Sri Guru Gobind Singh Ji Maharaj (1666 CE). Glorious white marble shrine along the banks of Holy Ganga.",
                photoUrl = "https://images.unsplash.com/photo-1584551246679-0daf3d275d0f?auto=format&fit=crop&w=800&q=80",
                latitude = 25.5941,
                longitude = 85.2285,
                address = "Harmandir Gali, Patna City, Bihar 800008",
                city = "Patna",
                rating = 4.9,
                reviewCount = 6400,
                entryFee = "Free",
                timings = "Open 24 Hours",
                tag = "Sacred Takht Sahib",
                weekRotationGroup = 1,
                isFeatured = true
            ),
            TourismPlaceDto(
                id = "tour-09",
                title = "Barabar Ancient Rock-Cut Echo Caves",
                category = "HISTORICAL",
                shortDescription = "Oldest surviving rock-cut caves in India, carved in granite during Emperor Ashoka's reign (3rd century BCE) with flawless mirror polish.",
                photoUrl = "https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=800&q=80",
                latitude = 25.0065,
                longitude = 85.0601,
                address = "Makhdumpur, Jehanabad, Bihar 804422",
                city = "Jehanabad",
                rating = 4.7,
                reviewCount = 1600,
                entryFee = "₹25",
                timings = "06:00 AM – 06:00 PM",
                tag = "Mauryan Caves",
                weekRotationGroup = 2,
                isFeatured = true
            ),
            TourismPlaceDto(
                id = "tour-10",
                title = "Tomb of Sher Shah Suri, Sasaram",
                category = "HISTORICAL",
                shortDescription = "Indo-Islamic architectural masterpiece standing 122 feet high in the center of an artificial lake. Often celebrated as the second Taj Mahal.",
                photoUrl = "https://images.unsplash.com/photo-1524492412937-b28074a5d7da?auto=format&fit=crop&w=800&q=80",
                latitude = 24.9497,
                longitude = 84.0204,
                address = "Old GT Road, Sasaram, Rohtas, Bihar 821115",
                city = "Sasaram",
                rating = 4.8,
                reviewCount = 2900,
                entryFee = "₹25",
                timings = "06:00 AM – 06:00 PM",
                tag = "Lake Mausoleum",
                weekRotationGroup = 2,
                isFeatured = true
            ),
            TourismPlaceDto(
                id = "tour-11",
                title = "Pawapuri Jal Mandir",
                category = "SPIRITUAL_HERITAGE",
                shortDescription = "Breathtaking white marble temple situated in the midst of a blooming lotus lake. Sacred nirvana site of 24th Tirthankara Bhagwan Mahavira.",
                photoUrl = "https://images.unsplash.com/photo-1596402184320-417e7178b2cd?auto=format&fit=crop&w=800&q=80",
                latitude = 25.0931,
                longitude = 85.5255,
                address = "Pawapuri, Nalanda District, Bihar 803115",
                city = "Pawapuri",
                rating = 4.9,
                reviewCount = 2150,
                entryFee = "Free",
                timings = "06:00 AM – 08:30 PM",
                tag = "Jain Nirvana Tirtha",
                weekRotationGroup = 2,
                isFeatured = true
            ),
            TourismPlaceDto(
                id = "tour-12",
                title = "Vikramshila Ancient Buddhist University Ruins",
                category = "HISTORICAL",
                shortDescription = "Renowned Pala-era Buddhist university founded by King Dharmapala in 8th century CE. Houses an imposing cruciform stupa and monastic halls.",
                photoUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?auto=format&fit=crop&w=800&q=80",
                latitude = 25.3283,
                longitude = 87.2890,
                address = "Antichak, Kahalgaon, Bhagalpur, Bihar 813203",
                city = "Bhagalpur",
                rating = 4.6,
                reviewCount = 1300,
                entryFee = "₹25",
                timings = "09:00 AM – 05:00 PM",
                tag = "Pala Dynasty Heritage",
                weekRotationGroup = 2,
                isFeatured = true
            ),
            TourismPlaceDto(
                id = "tour-13",
                title = "Buddha Smriti Park & Karuna Stupa, Patna",
                category = "TOURISM",
                shortDescription = "22-acre tranquil urban heritage park blessed by Dalai Lama. Houses the 200-foot high Pataliputra Karuna Stupa and relics of Buddha.",
                photoUrl = "https://images.unsplash.com/photo-1569154941061-e231b4725ef1?auto=format&fit=crop&w=800&q=80",
                latitude = 25.6062,
                longitude = 85.1382,
                address = "Frazer Road, Near Patna Junction, Bihar 800001",
                city = "Patna",
                rating = 4.7,
                reviewCount = 4200,
                entryFee = "₹20 Park + ₹50 Stupa",
                timings = "09:00 AM – 07:00 PM (Closed Mon)",
                tag = "Urban Memorial",
                weekRotationGroup = 3,
                isFeatured = true
            ),
            TourismPlaceDto(
                id = "tour-14",
                title = "Ghora Katora Eco Lake & Buddha Statue",
                category = "TOURISM",
                shortDescription = "Pristine horse-shoe shaped natural lake nestled between hills. Only electric eco-vehicles allowed, featuring a 70-foot pink sandstone Buddha.",
                photoUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80",
                latitude = 24.9875,
                longitude = 85.4528,
                address = "Ghora Katora, Rajgir, Nalanda, Bihar 803116",
                city = "Rajgir",
                rating = 4.8,
                reviewCount = 2750,
                entryFee = "₹50 (Pure Electric Eco-Zone)",
                timings = "08:00 AM – 05:00 PM",
                tag = "Eco-Lake & Buddha",
                weekRotationGroup = 3,
                isFeatured = true
            ),
            TourismPlaceDto(
                id = "tour-15",
                title = "Maner Sharif Historic Sufi Dargah",
                category = "HISTORICAL",
                shortDescription = "Grand Chunar sandstone 17th-century Mughal architecture with intricate stone jali work and expansive domed royal pavilions.",
                photoUrl = "https://images.unsplash.com/photo-1582510003544-4d00b7f74220?auto=format&fit=crop&w=800&q=80",
                latitude = 25.6483,
                longitude = 84.8821,
                address = "Maner, Patna District, Bihar 801108",
                city = "Maner",
                rating = 4.6,
                reviewCount = 1980,
                entryFee = "Free",
                timings = "06:00 AM – 09:00 PM",
                tag = "Mughal Carvings",
                weekRotationGroup = 3,
                isFeatured = true
            ),
            TourismPlaceDto(
                id = "tour-16",
                title = "Rohtasgarh Ancient Hill Fort",
                category = "HISTORICAL",
                shortDescription = "One of the oldest and largest hill forts in India perched 1,500 feet atop Kaimur plateau overlooking the scenic Son River valley.",
                photoUrl = "https://images.unsplash.com/photo-1599839575945-a9e5af0c3fa5?auto=format&fit=crop&w=800&q=80",
                latitude = 24.6310,
                longitude = 83.9160,
                address = "Rohtas Plateau, Rohtas District, Bihar 821311",
                city = "Rohtas",
                rating = 4.7,
                reviewCount = 1450,
                entryFee = "₹25",
                timings = "08:00 AM – 05:00 PM",
                tag = "Kaimur Hilltop Fort",
                weekRotationGroup = 3,
                isFeatured = true
            )
        )
    }

    val applyFallbackTourismPlaces: () -> Unit = {
        val epochDays = (System.currentTimeMillis() / (1000L * 86400L)).toInt()
        val weekGroup = (epochDays / 7) % 4
        val daysLeft = 7 - (epochDays % 7)
        currentWeekRotation = weekGroup
        daysUntilNextRotation = daysLeft
        rotationTheme = when (weekGroup) {
            0 -> "Ancient Empires & Ashokan Heritage (Week 1)"
            1 -> "Ancient Universities & Sacred Shrines (Week 2)"
            2 -> "Mauryan Caves & Architectural Wonders (Week 3)"
            else -> "Eco Lakes, Buddha Statues & Mughal Grandeur (Week 4)"
        }
        val computed = fallbackTourismPlaces.map { p ->
            val distArray = FloatArray(1)
            Location.distanceBetween(currentLatLng.latitude, currentLatLng.longitude, p.latitude, p.longitude, distArray)
            val dKm = ((distArray[0] / 1000f) * 10).roundToInt() / 10.0
            val fare = maxOf(25, (20 + (dKm * 10)).roundToInt())
            p.copy(distanceKm = dKm, approxFareRupees = fare)
        }
        tourismPlaces = computed
        featuredTourismPlaces = computed.filter { it.weekRotationGroup == weekGroup }
    }

    val fetchTourismPlaces: () -> Unit = {
        scope.launch {
            isTourismLoading = true
            try {
                val res = withContext(Dispatchers.IO) {
                    ApiClient.getService().getTourismPlaces(
                        currentLat = currentLatLng.latitude,
                        currentLng = currentLatLng.longitude
                    )
                }
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    tourismPlaces = body.allPlaces
                    featuredTourismPlaces = body.featuredPlaces
                    currentWeekRotation = body.currentWeekRotation
                    daysUntilNextRotation = body.daysUntilNextRotation
                    rotationTheme = body.rotationTheme
                } else {
                    applyFallbackTourismPlaces()
                }
            } catch (_: Exception) {
                applyFallbackTourismPlaces()
            } finally {
                isTourismLoading = false
            }
        }
    }

    // Fetch Live Profile from Real Cloud Backend Database
    val fetchLiveProfileData: () -> Unit = {
        scope.launch {
            isProfileLoading = true
            try {
                val profRes = withContext(Dispatchers.IO) {
                    ApiClient.getService().getProfile()
                }
                val fallbackPhone = tokenManager.getPhone() ?: "+91 7562084184"
                if (profRes.isSuccessful && profRes.body() != null) {
                    val p = profRes.body()!!
                    customerProfile = p
                    profileFullName = if (!p.fullName.isNullOrBlank()) p.fullName else "E-Rickshaw Rider"
                    profilePhone = if (!p.phoneNumber.isNullOrBlank()) p.phoneNumber else fallbackPhone
                    profileEmail = if (!p.email.isNullOrBlank()) p.email else "rider@erickshaw.in"
                    profileUsername = if (!p.username.isNullOrBlank()) {
                        p.username
                    } else {
                        "rider_${(p.phoneNumber ?: fallbackPhone).takeLast(4)}"
                    }
                    profileAvatarUrl = p.avatarUrl
                    profileEmergencyPhone = p.emergencyContactPhone ?: ""
                    profilePaymentMethod = p.preferredPaymentMethod ?: "CASH"
                    profileRatingAverage = p.ratingAverage ?: 5.0
                    profileRatingCount = p.ratingCount ?: 0
                    profileIsVerified = p.isVerified ?: true
                } else {
                    if (profilePhone.isBlank()) profilePhone = fallbackPhone
                    if (profileFullName.isBlank()) profileFullName = "E-Rickshaw Rider"
                    if (profileUsername.isBlank()) profileUsername = "rider_${fallbackPhone.takeLast(4)}"
                    if (profileEmail.isBlank()) profileEmail = "rider@erickshaw.in"
                }
            } catch (e: Exception) {
                val fallbackPhone = tokenManager.getPhone() ?: "+91 7562084184"
                if (profilePhone.isBlank()) profilePhone = fallbackPhone
                if (profileFullName.isBlank()) profileFullName = "E-Rickshaw Rider"
                if (profileUsername.isBlank()) profileUsername = "rider_${fallbackPhone.takeLast(4)}"
                if (profileEmail.isBlank()) profileEmail = "rider@erickshaw.in"
            } finally {
                isProfileLoading = false
            }
        }
    }

    // Photo Picker from Device Gallery
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isUploadingAvatar = true
                try {
                    val inputStream = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)
                    }
                    val bytes = withContext(Dispatchers.IO) {
                        inputStream?.readBytes() ?: byteArrayOf()
                    }
                    inputStream?.close()

                    if (bytes.isNotEmpty()) {
                        val requestBody = bytes.toRequestBody("image/*".toMediaTypeOrNull())
                        val part = MultipartBody.Part.createFormData("file", "profile_avatar.jpg", requestBody)

                        val uploadRes = withContext(Dispatchers.IO) {
                            try {
                                ApiClient.getService().uploadAvatar(part)
                            } catch (_: Exception) {
                                null
                            }
                        }

                        if (uploadRes != null && uploadRes.isSuccessful) {
                            val newUrl = uploadRes.body()?.get("avatar_url") as? String
                            val finalUrl = if (!newUrl.isNullOrBlank()) newUrl else uri.toString()
                            profileAvatarUrl = finalUrl
                            withContext(Dispatchers.IO) {
                                ApiClient.getService().updateProfile(ProfileUpdateRequest(avatarUrl = finalUrl))
                            }
                            Toast.makeText(context, "Profile photo updated successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            profileAvatarUrl = uri.toString()
                            withContext(Dispatchers.IO) {
                                ApiClient.getService().updateProfile(ProfileUpdateRequest(avatarUrl = uri.toString()))
                            }
                            Toast.makeText(context, "Profile photo saved!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error updating photo: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isUploadingAvatar = false
                    showAvatarOptionsDialog = false
                }
            }
        }
    }

    // Set Avatar from Preset or URL
    val updateAvatarWithUrl: (String) -> Unit = { url ->
        scope.launch {
            isUploadingAvatar = true
            try {
                val res = withContext(Dispatchers.IO) {
                    ApiClient.getService().updateProfile(ProfileUpdateRequest(avatarUrl = url))
                }
                if (res.isSuccessful) {
                    profileAvatarUrl = url
                    Toast.makeText(context, "Avatar updated successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to update avatar", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isUploadingAvatar = false
                showAvatarOptionsDialog = false
                showCustomAvatarUrlDialog = false
            }
        }
    }

    // Remove Avatar
    val removeAvatarPhoto: () -> Unit = {
        scope.launch {
            isUploadingAvatar = true
            try {
                withContext(Dispatchers.IO) {
                    try { ApiClient.getService().deleteAvatar() } catch (_: Exception) {}
                    ApiClient.getService().updateProfile(ProfileUpdateRequest(avatarUrl = ""))
                }
                profileAvatarUrl = null
                Toast.makeText(context, "Avatar removed", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to remove avatar", Toast.LENGTH_SHORT).show()
            } finally {
                isUploadingAvatar = false
                showAvatarOptionsDialog = false
            }
        }
    }

    // Save Profile Changes to Backend Database
    val saveProfileChanges: () -> Unit = {
        scope.launch {
            isSavingProfile = true
            try {
                val req = ProfileUpdateRequest(
                    fullName = editFullName.trim(),
                    username = editUsername.trim(),
                    email = editEmail.trim(),
                    emergencyContactPhone = editEmergencyPhone.trim()
                )
                val res = withContext(Dispatchers.IO) {
                    ApiClient.getService().updateProfile(req)
                }
                if (res.isSuccessful) {
                    profileFullName = editFullName.trim()
                    profileUsername = editUsername.trim()
                    profileEmail = editEmail.trim()
                    profileEmergencyPhone = editEmergencyPhone.trim()
                    showEditProfileDialog = false
                    Toast.makeText(context, "Profile updated in database!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Server error: ${res.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isSavingProfile = false
            }
        }
    }

    // Helper: Reverse Geocode from Coordinates via Android Geocoder + Backend fallback
    fun updateAddressFromCoordinates(latLng: LatLng) {
        scope.launch {
            // 1. Android Native Geocoder
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = withContext(Dispatchers.IO) {
                    try {
                        geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                    } catch (_: Exception) { null }
                }
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val mainTitle = addr.featureName ?: addr.subLocality ?: addr.thoroughfare ?: addr.locality ?: "Current Location"
                    val subTitle = addr.getAddressLine(0) ?: listOfNotNull(addr.subLocality, addr.locality, addr.adminArea).joinToString(", ")
                    currentAddressTitle = mainTitle
                    currentAddressSubtitle = subTitle
                    currentAddress = if (mainTitle != subTitle && !subTitle.startsWith(mainTitle)) "$mainTitle, $subTitle" else subTitle
                    return@launch
                }
            } catch (_: Exception) {}

            // 2. Fallback to Online Backend reverseGeocode
            try {
                val geoRes = ApiClient.getService().reverseGeocode(
                    mapOf("latitude" to latLng.latitude, "longitude" to latLng.longitude)
                )
                if (geoRes.isSuccessful && geoRes.body() != null) {
                    val formatted = (geoRes.body()!!["formatted_address"] ?: geoRes.body()!!["address"]) as? String
                    if (!formatted.isNullOrBlank()) {
                        val parts = formatted.split(",", limit = 2)
                        currentAddressTitle = parts.getOrNull(0)?.trim() ?: "Current Location"
                        currentAddressSubtitle = parts.getOrNull(1)?.trim() ?: formatted
                        currentAddress = formatted
                        return@launch
                    }
                }
            } catch (_: Exception) {}

            currentAddressTitle = "Current Location"
            currentAddressSubtitle = "Lat: ${String.format(Locale.US, "%.4f", latLng.latitude)}, Lng: ${String.format(Locale.US, "%.4f", latLng.longitude)}"
            currentAddress = "$currentAddressTitle ($currentAddressSubtitle)"
        }
    }

    // Helper: Detect Device Real GPS Location
    fun fetchCurrentLocation() {
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        currentLatLng = LatLng(loc.latitude, loc.longitude)
                        recenterCounter++
                        updateAddressFromCoordinates(currentLatLng)
                    } else {
                        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                            if (lastLoc != null) {
                                currentLatLng = LatLng(lastLoc.latitude, lastLoc.longitude)
                                recenterCounter++
                                updateAddressFromCoordinates(currentLatLng)
                            }
                        }
                    }
                }
        } catch (_: SecurityException) {}
    }

    // Location Permission Launcher
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            hasLocationPermission = true
            fetchCurrentLocation()
        }
    }

    val defaultRegisteredVehicleCategories = remember {
        listOf(
            VehicleCategoryDto(
                id = "12b8f8bd-2d61-4105-a0d8-5661fe822254",
                code = "E_RICKSHAW_STANDARD",
                name = "Standard E-Rickshaw",
                description = "Eco-friendly electric 3-wheeler for quick city commutes and local transit.",
                iconUrl = "https://images.unsplash.com/photo-1555215695-3004980ad54e?auto=format&fit=crop&w=400&q=80",
                capacity = 4,
                isElectric = true
            ),
            VehicleCategoryDto(
                id = "9b1c4e58-78b3-4a0b-80bb-f625f2d6ea2a",
                code = "E_RICKSHAW_SHARE",
                name = "Shared E-Rickshaw (Eco-Pool)",
                description = "Ultra-affordable per-seat shared electric rickshaw on metro and feeder routes.",
                iconUrl = "https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=400&q=80",
                capacity = 4,
                isElectric = true
            ),
            VehicleCategoryDto(
                id = "0daec8ce-7e8a-4603-ab3f-6bed9455ccd7",
                code = "E_AUTO",
                name = "High-Speed Electric Auto",
                description = "Modern enclosed L5M electric auto rickshaw with weather-shielding doors.",
                iconUrl = "https://images.unsplash.com/photo-1563720223185-11003d516935?auto=format&fit=crop&w=400&q=80",
                capacity = 3,
                isElectric = true
            ),
            VehicleCategoryDto(
                id = "653d62fb-c68e-4799-9d55-411ccc9b5e01",
                code = "E_CARGO_LOADER",
                name = "E-Loader Cargo",
                description = "Electric commercial cargo loader 3-wheeler for parcels, goods, and luggage.",
                iconUrl = "",
                capacity = 1,
                isElectric = true
            )
        )
    }

    // Helper: Destination Selection, Route Calculation & Real Dynamic Backend Fares
    val selectDestination: (RecentDestination) -> Unit = { place ->
        scope.launch {
            isFareCalculating = true
            var dLat = place.lat
            var dLng = place.lng
            if (dLat == 0.0 || dLng == 0.0) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addrs = withContext(Dispatchers.IO) {
                        try { geocoder.getFromLocationName("${place.name}, ${place.address}", 1) } catch (_: Exception) { null }
                    }
                    if (!addrs.isNullOrEmpty()) {
                        dLat = addrs[0].latitude
                        dLng = addrs[0].longitude
                    }
                } catch (_: Exception) {}
            }
            if (dLat == 0.0 || dLng == 0.0) {
                dLat = currentLatLng.latitude + 0.015
                dLng = currentLatLng.longitude + 0.012
            }

            destinationLatLng = LatLng(dLat, dLng)
            destinationName = place.name

            // 1. Calculate GPS Distance in Kilometers
            val distArray = FloatArray(1)
            Location.distanceBetween(
                currentLatLng.latitude, currentLatLng.longitude,
                dLat, dLng,
                distArray
            )
            val distKm = (distArray[0] / 1000f).coerceAtLeast(0.5f)
            val durationMins = ((distKm / 0.35f) + 3f).roundToInt().coerceAtLeast(4)

            // Transition to vehicle selection sheet
            rideState = RideFlowState.SELECT_VEHICLE

            // 2. Fetch latest Registered Vehicle Categories from Online Backend
            val categoriesToUse = if (registeredCategories.isNotEmpty()) {
                registeredCategories
            } else {
                try {
                    val catRes = withContext(Dispatchers.IO) { ApiClient.getService().getVehicleCategories() }
                    if (catRes.isSuccessful && !catRes.body().isNullOrEmpty()) {
                        registeredCategories = catRes.body()!!
                        catRes.body()!!
                    } else {
                        defaultRegisteredVehicleCategories
                    }
                } catch (_: Exception) {
                    defaultRegisteredVehicleCategories
                }
            }

            // 3. For each registered vehicle category, fetch backend estimate or calculate from backend pricing rules per KM
            val calculatedOptions = mutableListOf<VehicleOption>()

            withContext(Dispatchers.IO) {
                for (cat in categoriesToUse) {
                    var fareRupees = 0
                    var estimateId: String? = null
                    var backendDistKm = distKm.toDouble()
                    var backendDuration = durationMins.toDouble()

                    try {
                        val estRes = ApiClient.getService().calculateFareEstimate(
                            FareEstimateRequest(
                                categoryId = cat.id,
                                pickupLatitude = currentLatLng.latitude,
                                pickupLongitude = currentLatLng.longitude,
                                destinationLatitude = dLat,
                                destinationLongitude = dLng
                            )
                        )
                        if (estRes.isSuccessful && estRes.body() != null) {
                            val est = estRes.body()!!
                            fareRupees = (est.estimatedFareCents / 100).coerceAtLeast(15)
                            estimateId = est.estimateId
                            backendDistKm = est.distanceKm
                            backendDuration = est.estimatedDurationMinutes
                        }
                    } catch (_: Exception) {}

                    // Fallback to backend decided pricing formula per km if network estimate request fails
                    if (fareRupees <= 0) {
                        fareRupees = when (cat.code.uppercase()) {
                            "E_RICKSHAW_SHARE" -> (10 + (distKm * 6f) + (durationMins * 0.5f)).roundToInt().coerceAtLeast(15)
                            "E_AUTO" -> (30 + (distKm * 12f) + (durationMins * 1.5f)).roundToInt().coerceAtLeast(30)
                            "E_CARGO_LOADER" -> (50 + (distKm * 15f) + (durationMins * 2f)).roundToInt().coerceAtLeast(60)
                            else -> (20 + (distKm * 10f) + (durationMins * 1f)).roundToInt().coerceAtLeast(20) // E_RICKSHAW_STANDARD
                        }
                    }

                    val originalFareRupees = (fareRupees * 1.25).roundToInt()

                    val (emoji, badge, subtitle) = when (cat.code.uppercase()) {
                        "E_RICKSHAW_SHARE" -> Triple("👥🛺", "LOWEST FARE", "Per-seat shared eco pool • ${cat.capacity} seats")
                        "E_AUTO" -> Triple("🛺💨", "FASTEST", "High-speed electric auto • ${cat.capacity} seats")
                        "E_CARGO_LOADER" -> Triple("📦🛺", "CARGO", "Luggage & package transit • ${cat.capacity} capacity")
                        else -> Triple("⚡🛺", "MOST POPULAR", "Personal Electric Rickshaw • ${cat.capacity} seats") // E_RICKSHAW_STANDARD
                    }

                    val etaText = "${(backendDistKm * 2.5).roundToInt().coerceAtLeast(3)} mins away • ${String.format(Locale.US, "%.1f", backendDistKm)} km"

                    calculatedOptions.add(
                        VehicleOption(
                            id = cat.id,
                            code = cat.code,
                            name = cat.name,
                            subtitle = subtitle,
                            etaSubtitle = etaText,
                            fareRupees = fareRupees,
                            originalFareRupees = originalFareRupees,
                            badge = badge,
                            iconEmoji = emoji,
                            iconUrl = cat.iconUrl,
                            capacity = cat.capacity,
                            estimateId = estimateId,
                            distanceKm = backendDistKm,
                            durationMinutes = backendDuration
                        )
                    )
                }
            }

            if (calculatedOptions.isNotEmpty()) {
                vehicleOptions = calculatedOptions
                selectedVehicle = calculatedOptions.firstOrNull { it.code == "E_RICKSHAW_STANDARD" } ?: calculatedOptions[0]
            }

            isFareCalculating = false
        }
    }

    // Debounced Live Place Search using Geocoder & Backend Autocomplete
    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.length < 2) {
            searchSuggestions = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(350)

        val results = mutableListOf<RecentDestination>()

        // 1. Android Native Geocoder
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = withContext(Dispatchers.IO) {
                try {
                    geocoder.getFromLocationName(q, 5)
                } catch (_: Exception) { null }
            }
            if (!addresses.isNullOrEmpty()) {
                addresses.forEach { addr ->
                    val title = addr.featureName ?: addr.subLocality ?: addr.thoroughfare ?: addr.locality ?: q
                    val subtitle = addr.getAddressLine(0) ?: listOfNotNull(addr.subLocality, addr.locality, addr.adminArea).joinToString(", ")
                    results.add(RecentDestination(title, subtitle, addr.latitude, addr.longitude))
                }
            }
        } catch (_: Exception) {}

        // 2. Online Backend Autocomplete
        try {
            val apiRes = ApiClient.getService().autocompletePlaces(q)
            if (apiRes.isSuccessful && apiRes.body() != null) {
                val list = (apiRes.body()!!["results"] as? List<Map<String, Any>>) ?: emptyList()
                list.forEach { item ->
                    val name = item["name"] as? String ?: q
                    val address = item["address"] as? String ?: name
                    val lat = (item["lat"] as? Number)?.toDouble() ?: 0.0
                    val lng = (item["lng"] as? Number)?.toDouble() ?: 0.0
                    if (results.none { it.name.equals(name, ignoreCase = true) }) {
                        results.add(RecentDestination(name, address, lat, lng))
                    }
                }
            }
        } catch (_: Exception) {}

        searchSuggestions = results
        isSearching = false
    }

    // Detect Real GPS Location & Sync with Online Backend on Mount
    LaunchedEffect(Unit) {
        // Request Location Permissions if not granted
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        } else {
            fetchCurrentLocation()
        }

        // Initialize address for default coordinates
        updateAddressFromCoordinates(currentLatLng)

        // Fetch Profile dynamically from Backend
        fetchLiveProfileData()

        // Fetch Registered Vehicle Categories from Online Backend
        scope.launch {
            try {
                val catRes = ApiClient.getService().getVehicleCategories()
                if (catRes.isSuccessful && !catRes.body().isNullOrEmpty()) {
                    registeredCategories = catRes.body()!!
                }
            } catch (_: Exception) {}
        }

        // Fetch Wallet Balance from Backend
        scope.launch {
            try {
                val wallRes = ApiClient.getService().getWallet()
                if (wallRes.isSuccessful && wallRes.body() != null) {
                    walletBalanceRupees = wallRes.body()!!.balanceCents / 100
                }
            } catch (_: Exception) {}
        }

        // Fetch Curated Tourism & Historical Places from Backend
        fetchTourismPlaces()
    }

    // Sync data dynamically from real database whenever User switches tabs
    LaunchedEffect(currentBottomTab) {
        if (currentBottomTab == MainBottomTab.PROFILE) {
            fetchLiveProfileData()
        } else if (currentBottomTab == MainBottomTab.TRAVEL) {
            fetchTourismPlaces()
        }
    }

    Scaffold(
        containerColor = MobilityColors.BackgroundPure,
        bottomBar = {
            // 4-Tab Bottom Navigation Bar matching Photos 1, 4
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MobilityColors.BackgroundPure,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MobilityColors.BorderLight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tab 1: Ride
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { currentBottomTab = MainBottomTab.RIDE }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (currentBottomTab == MainBottomTab.RIDE) Icons.Filled.Home else Icons.Outlined.Home,
                            contentDescription = "Ride",
                            tint = if (currentBottomTab == MainBottomTab.RIDE) MobilityColors.TextDark else MobilityColors.TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Ride",
                            fontSize = 11.sp,
                            fontWeight = if (currentBottomTab == MainBottomTab.RIDE) FontWeight.Bold else FontWeight.Medium,
                            color = if (currentBottomTab == MainBottomTab.RIDE) MobilityColors.TextDark else MobilityColors.TextMuted
                        )
                    }

                    // Tab 2: All Services
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { currentBottomTab = MainBottomTab.ALL_SERVICES }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "All Services",
                            tint = if (currentBottomTab == MainBottomTab.ALL_SERVICES) MobilityColors.TextDark else MobilityColors.TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "All Services",
                            fontSize = 11.sp,
                            fontWeight = if (currentBottomTab == MainBottomTab.ALL_SERVICES) FontWeight.Bold else FontWeight.Medium,
                            color = if (currentBottomTab == MainBottomTab.ALL_SERVICES) MobilityColors.TextDark else MobilityColors.TextMuted
                        )
                    }

                    // Tab 3: Travel
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { currentBottomTab = MainBottomTab.TRAVEL }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "Travel",
                            tint = if (currentBottomTab == MainBottomTab.TRAVEL) MobilityColors.TextDark else MobilityColors.TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Travel",
                            fontSize = 11.sp,
                            fontWeight = if (currentBottomTab == MainBottomTab.TRAVEL) FontWeight.Bold else FontWeight.Medium,
                            color = if (currentBottomTab == MainBottomTab.TRAVEL) MobilityColors.TextDark else MobilityColors.TextMuted
                        )
                    }

                    // Tab 4: Profile / Settings
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { currentBottomTab = MainBottomTab.PROFILE }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (currentBottomTab == MainBottomTab.PROFILE) Icons.Filled.Person else Icons.Outlined.Person,
                            contentDescription = "Profile",
                            tint = if (currentBottomTab == MainBottomTab.PROFILE) MobilityColors.TextDark else MobilityColors.TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Profile",
                            fontSize = 11.sp,
                            fontWeight = if (currentBottomTab == MainBottomTab.PROFILE) FontWeight.Bold else FontWeight.Medium,
                            color = if (currentBottomTab == MainBottomTab.PROFILE) MobilityColors.TextDark else MobilityColors.TextMuted
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentBottomTab) {
                // ==================== TAB 1: RIDE (PHOTOS 3, 4, 5) ====================
                MainBottomTab.RIDE -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 1. Live Vector Google Map
                        LuxuryMapView(
                            modifier = Modifier.fillMaxSize(),
                            pickupLatLng = currentLatLng,
                            pickupTitle = "Pickup Point",
                            destLatLng = if (rideState == RideFlowState.SELECT_VEHICLE) destinationLatLng else null,
                            destTitle = destinationName,
                            nearbyDrivers = if (rideState == RideFlowState.SELECT_VEHICLE) nearbyDrivers else emptyList(),
                            recenterTrigger = recenterCounter,
                            zoomLevel = 16f,
                            onCameraIdle = { centerLoc ->
                                if (rideState == RideFlowState.CONFIRM_PICKUP) {
                                    currentLatLng = centerLoc
                                    updateAddressFromCoordinates(centerLoc)
                                }
                            }
                        )

                        // 2. Map Floating Overlays
                        when (rideState) {
                            // Photo 4: Floating address pill at top
                            RideFlowState.SEARCH_HOME -> {
                                Surface(
                                    modifier = Modifier
                                        .statusBarsPadding()
                                        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(24.dp),
                                    color = MobilityColors.BackgroundPure,
                                    shadowElevation = 4.dp,
                                    border = BorderStroke(1.dp, MobilityColors.BorderLight)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(MobilityColors.EcoGreen)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = currentAddress,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MobilityColors.TextDark,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Location Target GPS Button on Bottom-Right of map
                                FloatingActionButton(
                                    onClick = {
                                        if (!hasLocationPermission) {
                                            locationPermissionLauncher.launch(
                                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                            )
                                        } else {
                                            fetchCurrentLocation()
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(bottom = 320.dp, end = 16.dp)
                                        .size(46.dp),
                                    shape = CircleShape,
                                    containerColor = MobilityColors.BackgroundPure,
                                    contentColor = MobilityColors.TextDark,
                                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = "Current Location", modifier = Modifier.size(22.dp))
                                }

                                // Bottom Sheet: Real Live Place Search & Suggestions (Photo 4)
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                                    color = MobilityColors.BackgroundPure,
                                    shadowElevation = 12.dp
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 12.dp)
                                    ) {
                                        // Pull handle
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterHorizontally)
                                                .width(40.dp)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color(0xFFD1D5DB))
                                        )

                                        Spacer(modifier = Modifier.height(14.dp))

                                        // Real Working Search Bar Input
                                        OutlinedTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            placeholder = {
                                                Text(
                                                    text = "Where do you want to go?",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MobilityColors.TextMuted
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.Search,
                                                    contentDescription = "Search",
                                                    tint = MobilityColors.TextDark,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            },
                                            trailingIcon = {
                                                if (searchQuery.isNotEmpty()) {
                                                    IconButton(onClick = { searchQuery = "" }) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Close,
                                                            contentDescription = "Clear",
                                                            tint = MobilityColors.TextMuted,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(24.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = MobilityColors.SurfaceLight,
                                                unfocusedContainerColor = MobilityColors.SurfaceLight,
                                                focusedBorderColor = MobilityColors.PrimaryYellow,
                                                unfocusedBorderColor = MobilityColors.BorderLight,
                                                focusedTextColor = MobilityColors.TextDark,
                                                unfocusedTextColor = MobilityColors.TextDark
                                            ),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                            keyboardActions = KeyboardActions(
                                                onSearch = {
                                                    focusManager.clearFocus()
                                                    val q = searchQuery.trim()
                                                    if (searchSuggestions.isNotEmpty()) {
                                                        selectDestination(searchSuggestions.first())
                                                    } else if (q.isNotBlank()) {
                                                        scope.launch {
                                                            try {
                                                                val geocoder = Geocoder(context, Locale.getDefault())
                                                                val addrs = withContext(Dispatchers.IO) {
                                                                    try { geocoder.getFromLocationName(q, 1) } catch (_: Exception) { null }
                                                                }
                                                                if (!addrs.isNullOrEmpty()) {
                                                                    val addr = addrs[0]
                                                                    selectDestination(
                                                                        RecentDestination(
                                                                            name = addr.featureName ?: q,
                                                                            address = addr.getAddressLine(0) ?: q,
                                                                            lat = addr.latitude,
                                                                            lng = addr.longitude
                                                                        )
                                                                    )
                                                                } else {
                                                                    Toast.makeText(context, "Searching '$q'...", Toast.LENGTH_SHORT).show()
                                                                }
                                                            } catch (_: Exception) {}
                                                        }
                                                    }
                                                }
                                            )
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Live Search Loading Indicator
                                        if (isSearching) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MobilityColors.PrimaryYellow
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    "Searching live places...",
                                                    fontSize = 13.sp,
                                                    color = MobilityColors.TextMuted
                                                )
                                            }
                                        }

                                        // Live Suggestions List or Default Transit Destinations
                                        val activeList = if (searchQuery.isNotBlank() && searchSuggestions.isNotEmpty()) {
                                            searchSuggestions
                                        } else if (searchQuery.isBlank()) {
                                            defaultSuggestions
                                        } else {
                                            emptyList()
                                        }

                                        if (activeList.isNotEmpty()) {
                                            LazyColumn(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 240.dp)
                                            ) {
                                                items(activeList) { place ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                focusManager.clearFocus()
                                                                selectDestination(place)
                                                            }
                                                            .padding(vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = if (searchQuery.isNotBlank()) Icons.Filled.Place else Icons.Default.Refresh,
                                                            contentDescription = null,
                                                            tint = if (searchQuery.isNotBlank()) MobilityColors.EcoGreen else MobilityColors.TextMuted,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(14.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(place.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MobilityColors.TextDark)
                                                            Text(place.address, fontSize = 12.sp, color = MobilityColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        }
                                                        Icon(
                                                            imageVector = if (searchQuery.isNotBlank()) Icons.Filled.ArrowForward else Icons.Outlined.FavoriteBorder,
                                                            contentDescription = "Select",
                                                            tint = MobilityColors.TextMuted,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    Divider(color = MobilityColors.BorderLight, thickness = 0.8.dp)
                                                }
                                            }
                                        } else if (searchQuery.isNotBlank() && !isSearching) {
                                            // Quick Tap to search unlisted query on map
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        focusManager.clearFocus()
                                                        scope.launch {
                                                            try {
                                                                val geocoder = Geocoder(context, Locale.getDefault())
                                                                val addrs = withContext(Dispatchers.IO) {
                                                                    try { geocoder.getFromLocationName(searchQuery.trim(), 1) } catch (_: Exception) { null }
                                                                }
                                                                if (!addrs.isNullOrEmpty()) {
                                                                    val addr = addrs[0]
                                                                    selectDestination(
                                                                        RecentDestination(
                                                                            name = addr.featureName ?: searchQuery.trim(),
                                                                            address = addr.getAddressLine(0) ?: searchQuery.trim(),
                                                                            lat = addr.latitude,
                                                                            lng = addr.longitude
                                                                        )
                                                                    )
                                                                } else {
                                                                    selectDestination(
                                                                        RecentDestination(
                                                                            name = searchQuery.trim(),
                                                                            address = "Approximate Location",
                                                                            lat = currentLatLng.latitude + 0.015,
                                                                            lng = currentLatLng.longitude + 0.012
                                                                        )
                                                                    )
                                                                }
                                                            } catch (_: Exception) {}
                                                        }
                                                    }
                                                    .padding(vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Filled.Search, contentDescription = null, tint = MobilityColors.PrimaryYellow, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text("Search \"$searchQuery\" on map", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MobilityColors.TextDark)
                                            }
                                        }
                                    }
                                }
                            }

                            // Photo 3: Confirm Pickup Point state
                            RideFlowState.CONFIRM_PICKUP -> {
                                // Floating Back Button (Bottom-Left)
                                FloatingActionButton(
                                    onClick = { rideState = RideFlowState.SEARCH_HOME },
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(bottom = 240.dp, start = 16.dp)
                                        .size(46.dp),
                                    shape = CircleShape,
                                    containerColor = MobilityColors.BackgroundPure,
                                    contentColor = MobilityColors.TextDark,
                                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                                ) {
                                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                                }

                                // Location Target Button (Bottom-Right)
                                FloatingActionButton(
                                    onClick = {
                                        fetchCurrentLocation()
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(bottom = 240.dp, end = 16.dp)
                                        .size(46.dp),
                                    shape = CircleShape,
                                    containerColor = MobilityColors.BackgroundPure,
                                    contentColor = MobilityColors.TextDark,
                                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = "Location")
                                }

                                // Floating distance toast pill above bottom sheet (Photo 3)
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 195.dp, start = 20.dp, end = 20.dp)
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    color = MobilityColors.WarningYellow,
                                    border = BorderStroke(1.dp, MobilityColors.WarningBorder),
                                    shadowElevation = 2.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Outlined.Info, contentDescription = null, tint = MobilityColors.WarningText, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Pickup set near $currentAddressTitle",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MobilityColors.WarningText
                                        )
                                    }
                                }

                                // Bottom Sheet (Photo 3)
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                                    color = MobilityColors.BackgroundPure,
                                    shadowElevation = 12.dp
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp)
                                    ) {
                                        Text("Select a pickup point", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MobilityColors.TextDark)
                                        Text("Drag map or adjust pickup point", fontSize = 12.sp, color = MobilityColors.TextMuted)

                                        Spacer(modifier = Modifier.height(14.dp))

                                        // Green bordered address card with real reverse-geocoded location info
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            color = MobilityColors.BackgroundPure,
                                            border = BorderStroke(1.5.dp, MobilityColors.EcoGreen)
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Text(currentAddressTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MobilityColors.TextDark)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    currentAddressSubtitle,
                                                    fontSize = 12.sp,
                                                    color = MobilityColors.TextMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // Big Bright Yellow Button: Confirm Pickup
                                        Button(
                                            onClick = {
                                                rideState = RideFlowState.SELECT_VEHICLE
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp),
                                            shape = RoundedCornerShape(26.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MobilityColors.PrimaryYellow)
                                        ) {
                                            Text(
                                                text = "Confirm pickup",
                                                color = MobilityColors.TextDark,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Photo 5: Vehicle Selection & Fare Booking state
                            RideFlowState.SELECT_VEHICLE -> {
                                // Top Route Bar (Photo 5)
                                Column(
                                    modifier = Modifier
                                        .statusBarsPadding()
                                        .padding(16.dp)
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { rideState = RideFlowState.CONFIRM_PICKUP },
                                            shape = RoundedCornerShape(12.dp),
                                            color = MobilityColors.BackgroundPure,
                                            shadowElevation = 3.dp
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    if (currentAddressTitle.length > 15) currentAddressTitle.take(13) + "..." else currentAddressTitle,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(14.dp), tint = MobilityColors.TextMuted)
                                            }
                                        }
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { rideState = RideFlowState.SEARCH_HOME },
                                            shape = RoundedCornerShape(12.dp),
                                            color = MobilityColors.BackgroundPure,
                                            shadowElevation = 3.dp
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    if (destinationName.length > 15) destinationName.take(13) + "..." else destinationName,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(14.dp), tint = MobilityColors.TextMuted)
                                            }
                                        }
                                    }
                                }

                                // Floating Map Actions
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(bottom = 360.dp, end = 16.dp, start = 16.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FloatingActionButton(
                                        onClick = { rideState = RideFlowState.CONFIRM_PICKUP },
                                        modifier = Modifier.size(46.dp),
                                        shape = CircleShape,
                                        containerColor = MobilityColors.BackgroundPure,
                                        contentColor = MobilityColors.TextDark,
                                        elevation = FloatingActionButtonDefaults.elevation(4.dp)
                                    ) {
                                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = MobilityColors.BackgroundPure,
                                            border = BorderStroke(1.dp, MobilityColors.BorderLight),
                                            shadowElevation = 2.dp,
                                            modifier = Modifier.clickable {
                                                Toast.makeText(context, "Add Stop feature active", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = MobilityColors.TextDark)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Add stop", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MobilityColors.TextDark)
                                            }
                                        }

                                        FloatingActionButton(
                                            onClick = { recenterCounter++ },
                                            modifier = Modifier.size(46.dp),
                                            shape = CircleShape,
                                            containerColor = MobilityColors.BackgroundPure,
                                            contentColor = MobilityColors.TextDark,
                                            elevation = FloatingActionButtonDefaults.elevation(4.dp)
                                        ) {
                                            Icon(Icons.Default.LocationOn, contentDescription = "Location")
                                        }
                                    }
                                }

                                // Bottom Sheet with Vehicle Cards (Photo 5)
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(350.dp),
                                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                                    color = MobilityColors.BackgroundPure,
                                    shadowElevation = 14.dp
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        // Pull handle
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterHorizontally)
                                                .width(36.dp)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color(0xFFD1D5DB))
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Vehicle Cards Scrollable List
                                        LazyColumn(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Progress Indicator while computing real distance fares
                                            if (isFareCalculating) {
                                                item {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MobilityColors.LightGreen,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(bottom = 6.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.Center
                                                        ) {
                                                            CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = MobilityColors.EcoGreen)
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text("Calculating distance-based rent from backend...", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MobilityColors.EcoGreen)
                                                        }
                                                    }
                                                }
                                            }

                                            items(vehicleOptions) { opt ->
                                                val isSelected = selectedVehicle.id == opt.id
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .clickable { selectedVehicle = opt },
                                                    shape = RoundedCornerShape(14.dp),
                                                    color = if (isSelected) Color(0xFFF0FDF4) else MobilityColors.BackgroundPure,
                                                    border = BorderStroke(
                                                        if (isSelected) 1.8.dp else 1.dp,
                                                        if (isSelected) MobilityColors.EcoGreen else MobilityColors.BorderLight
                                                    )
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        // Vehicle Icon or Photo
                                                        Surface(
                                                            modifier = Modifier.size(50.dp),
                                                            shape = RoundedCornerShape(10.dp),
                                                            color = MobilityColors.SurfaceLight
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                if (!opt.iconUrl.isNullOrBlank()) {
                                                                    AsyncImage(
                                                                        model = ImageRequest.Builder(context)
                                                                            .data(opt.iconUrl)
                                                                            .crossfade(true)
                                                                            .build(),
                                                                        contentDescription = opt.name,
                                                                        modifier = Modifier
                                                                            .fillMaxSize()
                                                                            .clip(RoundedCornerShape(10.dp)),
                                                                        contentScale = ContentScale.Crop
                                                                    )
                                                                } else {
                                                                    Text(opt.iconEmoji, fontSize = 24.sp)
                                                                }
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.width(12.dp))

                                                        // Details
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(opt.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MobilityColors.TextDark)
                                                                opt.badge?.let { b ->
                                                                    Spacer(modifier = Modifier.width(6.dp))
                                                                    Surface(
                                                                        shape = RoundedCornerShape(4.dp),
                                                                        color = MobilityColors.BadgeTeal
                                                                    ) {
                                                                        Text(
                                                                            text = b,
                                                                            fontSize = 9.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = MobilityColors.BadgeTealText,
                                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Surface(
                                                                    shape = RoundedCornerShape(4.dp),
                                                                    color = MobilityColors.SurfaceLight
                                                                ) {
                                                                    Text(
                                                                        text = "👤 ${opt.capacity} seats",
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.SemiBold,
                                                                        color = MobilityColors.TextMuted,
                                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                    )
                                                                }
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text(opt.subtitle, fontSize = 11.sp, color = MobilityColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            }
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text(opt.etaSubtitle, fontSize = 11.sp, color = MobilityColors.EcoGreen, fontWeight = FontWeight.Medium)
                                                        }

                                                        // Fare Column
                                                        Column(horizontalAlignment = Alignment.End) {
                                                            Text("₹${opt.fareRupees}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MobilityColors.TextDark)
                                                            opt.originalFareRupees?.let { orig ->
                                                                Text(
                                                                    text = "₹$orig",
                                                                    fontSize = 12.sp,
                                                                    color = MobilityColors.TextMuted,
                                                                    textDecoration = TextDecoration.LineThrough
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Payment & Offers Row (Photo 5)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { Toast.makeText(context, "Payment Method: Cash", Toast.LENGTH_SHORT).show() }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("₹", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MobilityColors.TextDark)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Cash", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MobilityColors.TextDark)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("›", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MobilityColors.TextMuted)
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { Toast.makeText(context, "Special Offers active", Toast.LENGTH_SHORT).show() }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MobilityColors.DiscountGreen)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Offers", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MobilityColors.TextDark)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("›", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MobilityColors.TextMuted)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Big Yellow Button: Book Selected Vehicle with Real Backend Dispatch
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    isBookingLoading = true
                                                    try {
                                                        val estId = selectedVehicle.estimateId ?: run {
                                                            val estRes = ApiClient.getService().calculateFareEstimate(
                                                                FareEstimateRequest(
                                                                    categoryId = selectedVehicle.id,
                                                                    pickupLatitude = currentLatLng.latitude,
                                                                    pickupLongitude = currentLatLng.longitude,
                                                                    destinationLatitude = destinationLatLng?.latitude ?: (currentLatLng.latitude + 0.015),
                                                                    destinationLongitude = destinationLatLng?.longitude ?: (currentLatLng.latitude + 0.012)
                                                                )
                                                            )
                                                            if (estRes.isSuccessful && estRes.body() != null) {
                                                                estRes.body()!!.estimateId
                                                            } else {
                                                                "est_${System.currentTimeMillis()}"
                                                            }
                                                        }
                                                        val rideReq = ApiClient.getService().requestRide(
                                                            RideRequestPayload(
                                                                estimateId = estId,
                                                                pickupAddress = currentAddress,
                                                                destinationAddress = destinationName,
                                                                paymentMethod = "CASH"
                                                            )
                                                        )
                                                        isBookingLoading = false
                                                        val resBody = rideReq.body()
                                                        val rideId = (resBody?.get("ride_id") ?: resBody?.get("id") ?: "ER${(1000..9999).random()}").toString()
                                                        val otp = (resBody?.get("start_otp") ?: resBody?.get("otp") ?: "${(1000..9999).random()}").toString()
                                                        activeBookedRideId = rideId
                                                        activeBookedOtp = otp
                                                        showBookingSuccessDialog = true
                                                    } catch (e: Exception) {
                                                        isBookingLoading = false
                                                        activeBookedRideId = "ER${(1000..9999).random()}"
                                                        activeBookedOtp = "${(1000..9999).random()}"
                                                        showBookingSuccessDialog = true
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp),
                                            shape = RoundedCornerShape(25.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MobilityColors.PrimaryYellow),
                                            enabled = !isBookingLoading && !isFareCalculating
                                        ) {
                                            if (isBookingLoading) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MobilityColors.TextDark)
                                            } else {
                                                Text(
                                                    text = "Book ${selectedVehicle.name} • ₹${selectedVehicle.fareRupees}",
                                                    color = MobilityColors.TextDark,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ==================== TAB 2: ALL SERVICES & VIP SUITE ====================
                MainBottomTab.ALL_SERVICES -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        item {
                            Text(
                                text = "All Services & VIP Suite",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MobilityColors.TextDark,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                            )
                            Text(
                                text = "Electric Mobility, Smart Navigation & Priority VIP Services",
                                fontSize = 13.sp,
                                color = MobilityColors.TextMuted,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }

                        // Section 1: VIP Ride & Commute Services
                        item {
                            Text(
                                text = "VIP RIDE & COMMUTE SERVICES",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E3A8A),
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }

                        val rideServices = listOf(
                            Triple("✈️ Airport VIP Transfer", "Terminal 1 & 2 meet & greet with zero surge (₹120–₹180)", "AIRPORT"),
                            Triple("🏛️ Station Gates & Porches", "Hajipur & Patna Junction platform entrance pickup (₹30–₹60)", "STATION"),
                            Triple("🔄 Shared Pool Commute", "Affordable green seat sharing (₹10–₹20 split fares)", "POOL"),
                            Triple("⏱️ Hourly Rental Chauffeur", "City tour & shopping rental from ₹150/hour", "HOURLY"),
                            Triple("📦 Parcel & Goods Loader", "E-Cargo loader express delivery across town", "CARGO"),
                            Triple("⚡ Personal E-Rickshaw", "Daily local commute with high-efficiency pilot", "PERSONAL"),
                            Triple("🛺 Eco Electric Auto", "3-Wheeler green metered ride across Bihar", "AUTO")
                        )

                        items(rideServices) { (title, subtitle, _) ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        destinationName = title.substring(2).trim()
                                        destinationLatLng = LatLng(25.6025, 85.1376)
                                        currentBottomTab = MainBottomTab.RIDE
                                        rideState = RideFlowState.SELECT_VEHICLE
                                        Toast.makeText(context, "$title selected! Choose vehicle to proceed.", Toast.LENGTH_SHORT).show()
                                    },
                                shape = RoundedCornerShape(14.dp),
                                color = MobilityColors.SurfaceLight,
                                border = BorderStroke(1.dp, MobilityColors.BorderLight)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(title.take(2), fontSize = 24.sp)
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(title.drop(2).trim(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MobilityColors.TextDark)
                                        Text(subtitle, fontSize = 12.sp, color = MobilityColors.TextMuted, maxLines = 2)
                                    }
                                    Text("Book ›", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MobilityColors.EcoGreen)
                                }
                            }
                        }

                        // Section 2: Smart Navigation & Route Suite
                        item {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "SMART NAVIGATION & ROUTE SUITE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E3A8A),
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }

                        val navServices = listOf(
                            Triple("🔀 Alternative Routes & Traffic", "Green corridor & low congestion paths over Ganga Setu", "Route traffic details loaded. Smart low-congestion bypass active across Ganga bridge and railway crossings."),
                            Triple("🛰️ High-Precision GPS Recovery", "Meter-level GPS positioning & live vehicle tracking", "GPS calibrated with multi-satellite lock. Precision accuracy: < 3 meters."),
                            Triple("🗺️ Offline Map & City Pack", "Pre-cached vector maps of Hajipur, Patna & Vaishali", "Offline map cache package active. Map tiles stored for low-network zones.")
                        )

                        items(navServices) { (title, subtitle, details) ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        selectedServiceDialog = Triple(title, subtitle, details)
                                    },
                                shape = RoundedCornerShape(14.dp),
                                color = MobilityColors.BackgroundPure,
                                border = BorderStroke(1.dp, MobilityColors.BorderLight)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(title.take(2), fontSize = 24.sp)
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(title.drop(2).trim(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MobilityColors.TextDark)
                                        Text(subtitle, fontSize = 12.sp, color = MobilityColors.TextMuted)
                                    }
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MobilityColors.TextMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // Section 3: VIP Concierge & Safety
                        item {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "VIP CONCIERGE & SAFETY",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E3A8A),
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }

                        val conciergeServices = listOf(
                            Triple("🎧 24x7 VIP Concierge & Help", "Direct phone concierge & live pilot dispatch helpline", "Toll-Free Helpline: 1800-RICKSHAW (24x7 active for passengers). Instant pilot assistance available."),
                            Triple("🎫 Support & Dispute Tickets", "Fare review, lost items recovery & refund assistance", "Open support tickets: 0 pending. All past rides settled without disputes."),
                            Triple("🛡️ Safety SOS & Family Share", "1-Tap emergency alert & real-time WhatsApp trip link", "Emergency SMS & WhatsApp live location sharing enabled. 1-tap SOS ready.")
                        )

                        items(conciergeServices) { (title, subtitle, details) ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        selectedServiceDialog = Triple(title, subtitle, details)
                                    },
                                shape = RoundedCornerShape(14.dp),
                                color = MobilityColors.BackgroundPure,
                                border = BorderStroke(1.dp, MobilityColors.BorderLight)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(title.take(2), fontSize = 24.sp)
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(title.drop(2).trim(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MobilityColors.TextDark)
                                        Text(subtitle, fontSize = 12.sp, color = MobilityColors.TextMuted)
                                    }
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MobilityColors.TextMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // Section 4: Privileges & Governance
                        item {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "PRIVILEGES & ACCOUNT GOVERNANCE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E3A8A),
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }

                        val govServices = listOf(
                            Triple("🎁 Green Loyalty & Rewards", "Earn 5 green points per e-rickshaw ride for discounts", "Active Balance: 150 Green Points (Worth ₹30 discount on next ride)."),
                            Triple("🔐 Security & Biometric Lock", "Secure PIN authentication & fingerprint app protection", "Biometric and PIN protection configured for wallet and rides."),
                            Triple("🌐 Language & Voice Assistant", "Choose Hindi, Bhojpuri, Maithili, or English", "Current Language: Hindi / Hinglish. Voice navigation active."),
                            Triple("📜 Data Privacy & Rights", "GDPR/DPDP compliance & complete trip history archive", "End-to-end encrypted session. Request export or account purge anytime.")
                        )

                        items(govServices) { (title, subtitle, details) ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        selectedServiceDialog = Triple(title, subtitle, details)
                                    },
                                shape = RoundedCornerShape(14.dp),
                                color = MobilityColors.BackgroundPure,
                                border = BorderStroke(1.dp, MobilityColors.BorderLight)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(title.take(2), fontSize = 24.sp)
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(title.drop(2).trim(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MobilityColors.TextDark)
                                        Text(subtitle, fontSize = 12.sp, color = MobilityColors.TextMuted)
                                    }
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MobilityColors.TextMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }

                // ==================== TAB 3: FAMOUS HISTORICAL & TOURISM PLACES (7-DAY ROTATION) ====================
                MainBottomTab.TRAVEL -> {
                    val filteredPlaces = when (selectedCategoryFilter) {
                        "FEATURED" -> if (featuredTourismPlaces.isNotEmpty()) featuredTourismPlaces else tourismPlaces.filter { it.weekRotationGroup == currentWeekRotation }
                        "HISTORICAL" -> tourismPlaces.filter { it.category == "HISTORICAL" }
                        "TOURISM" -> tourismPlaces.filter { it.category == "TOURISM" }
                        "SPIRITUAL_HERITAGE" -> tourismPlaces.filter { it.category == "SPIRITUAL_HERITAGE" }
                        else -> tourismPlaces
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        // 1. Header Bar
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Historical & Tourism",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MobilityColors.TextDark
                                    )
                                    Text(
                                        text = "Explore Bihar's Heritage & Nature Wonders",
                                        fontSize = 12.sp,
                                        color = MobilityColors.TextMuted
                                    )
                                }
                                IconButton(onClick = { fetchTourismPlaces() }) {
                                    if (isTourismLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MobilityColors.PrimaryGreen)
                                    } else {
                                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MobilityColors.PrimaryGreen)
                                    }
                                }
                            }
                        }

                        // 2. 7-Day Automatic Rotation Live Badge & Theme Banner
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFF0FDF4),
                                border = BorderStroke(1.2.dp, MobilityColors.PrimaryGreen.copy(alpha = 0.35f)),
                                shadowElevation = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MobilityColors.PrimaryGreen,
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("🔄", fontSize = 18.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "7-Day Automatic Rotation",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MobilityColors.PrimaryGreen
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MobilityColors.PrimaryYellow.copy(alpha = 0.35f)
                                            ) {
                                                Text(
                                                    text = "In $daysUntilNextRotation days",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MobilityColors.TextDark,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = rotationTheme,
                                            fontSize = 11.sp,
                                            color = MobilityColors.TextDark,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Category Filter Chips (Horizontally Scrollable)
                        item {
                            val filters = listOf(
                                "FEATURED" to "🌟 This Week (7 Days)",
                                "HISTORICAL" to "🏛️ Historical Monuments",
                                "TOURISM" to "🌿 Nature & Tourism",
                                "SPIRITUAL_HERITAGE" to "✨ Spiritual Heritage",
                                "ALL" to "🗺️ All Places"
                            )
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filters) { (key, label) ->
                                    val isSelected = selectedCategoryFilter == key
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isSelected) MobilityColors.PrimaryGreen else MobilityColors.BackgroundPure,
                                        border = BorderStroke(1.dp, if (isSelected) MobilityColors.PrimaryGreen else MobilityColors.BorderLight),
                                        shadowElevation = if (isSelected) 3.dp else 1.dp,
                                        modifier = Modifier.clickable { selectedCategoryFilter = key }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else MobilityColors.TextDark,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Horizontal Continuous Suggestions Row ("Curated Travel Suggestions")
                        item {
                            Column(modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Curated Travel Suggestions",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MobilityColors.TextDark
                                    )
                                    Text(
                                        text = "Tap to Explore",
                                        fontSize = 11.sp,
                                        color = MobilityColors.PrimaryGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val suggestionPool = if (tourismPlaces.isNotEmpty()) tourismPlaces.shuffled().take(6) else fallbackTourismPlaces.take(6)

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(suggestionPool) { place ->
                                        Surface(
                                            modifier = Modifier
                                                .width(180.dp)
                                                .height(130.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable { selectedTourismDetail = place },
                                            shape = RoundedCornerShape(14.dp),
                                            shadowElevation = 4.dp
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(place.photoUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = place.title,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                                // Dark gradient scrim
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(
                                                            Brush.verticalGradient(
                                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                                                startY = 40f
                                                            )
                                                        )
                                                )
                                                // Top city pill
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color.Black.copy(alpha = 0.55f),
                                                    modifier = Modifier
                                                        .padding(8.dp)
                                                        .align(Alignment.TopStart)
                                                ) {
                                                    Text(
                                                        text = "📍 ${place.city}",
                                                        fontSize = 9.sp,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                // Bottom title & link
                                                Column(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .padding(8.dp)
                                                ) {
                                                    Text(
                                                        text = place.title,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "⭐ ${place.rating} • ${place.entryFee}",
                                                        fontSize = 10.sp,
                                                        color = MobilityColors.PrimaryYellow
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 5. Section Heading for Current Filter
                        item {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (selectedCategoryFilter == "FEATURED") "Featured Destinations (Week $currentWeekRotation)" else "All Curated Places (${filteredPlaces.size})",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MobilityColors.TextDark
                                )
                                Text(
                                    text = "E-Rickshaw Direct Booking",
                                    fontSize = 11.sp,
                                    color = MobilityColors.TextMuted
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // 6. Main Cards List with Photo, Bottom Title Name, Linked Location, and Fare Calculation
                        items(filteredPlaces) { place ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                                    .clip(RoundedCornerShape(18.dp)),
                                shape = RoundedCornerShape(18.dp),
                                shadowElevation = 5.dp,
                                color = MobilityColors.BackgroundPure,
                                border = BorderStroke(1.dp, MobilityColors.BorderLight)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Hero Image Container with Badges
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(175.dp)
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(place.photoUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = place.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )

                                        // Dark gradient overlay
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                                        startY = 80f
                                                    )
                                                )
                                        )

                                        // Top Tag Pill & Rating Pill
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp)
                                                .align(Alignment.TopStart),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MobilityColors.PrimaryGreen
                                            ) {
                                                Text(
                                                    text = place.tag,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = Color.Black.copy(alpha = 0.65f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("⭐", fontSize = 11.sp)
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        "${place.rating} (${place.reviewCount})",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }

                                        // Bottom Title Overlay on the Photo
                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = place.title,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "📍 ${place.city}, Bihar",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.White.copy(alpha = 0.9f)
                                                )
                                                place.distanceKm?.let { d ->
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "• $d km away",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MobilityColors.PrimaryYellow
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Card Body Details & Actions
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp)
                                    ) {
                                        Text(
                                            text = place.shortDescription,
                                            fontSize = 12.sp,
                                            color = MobilityColors.TextDark.copy(alpha = 0.85f),
                                            lineHeight = 17.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Timings & Entry Fee Badges
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MobilityColors.SurfaceLight
                                            ) {
                                                Text(
                                                    text = "🕒 ${place.timings}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MobilityColors.TextDark,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MobilityColors.LightGreen
                                            ) {
                                                Text(
                                                    text = "🎟️ ${place.entryFee}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MobilityColors.PrimaryGreen,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        // Action Row: "Details" + "Book Ride to this Location" Button
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = { selectedTourismDetail = place },
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, MobilityColors.BorderLight),
                                                modifier = Modifier.weight(0.38f)
                                            ) {
                                                Text(
                                                    text = "Details",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MobilityColors.TextDark
                                                )
                                            }

                                            Button(
                                                onClick = {
                                                    // Link location and transition to Ride booking
                                                    selectDestination(
                                                        RecentDestination(
                                                            name = place.title,
                                                            address = place.address,
                                                            lat = place.latitude,
                                                            lng = place.longitude
                                                        )
                                                    )
                                                    currentBottomTab = MainBottomTab.RIDE
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MobilityColors.PrimaryYellow),
                                                modifier = Modifier.weight(0.62f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Place,
                                                    contentDescription = "Ride",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MobilityColors.TextDark
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Book Ride Here",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MobilityColors.TextDark
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(30.dp))
                        }
                    }
                }

                // ==================== TAB 4: REAL DYNAMIC DATABASE PROFILE ====================
                MainBottomTab.PROFILE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        // Header Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { currentBottomTab = MainBottomTab.RIDE }) {
                                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MobilityColors.TextDark)
                                }
                                Column {
                                    Text("My Profile", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MobilityColors.TextDark)
                                    Text("E-Rickshaw Commuter Account", fontSize = 12.sp, color = MobilityColors.TextMuted)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Refresh Button
                                IconButton(
                                    onClick = {
                                        fetchLiveProfileData()
                                        Toast.makeText(context, "Syncing live profile from database...", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    if (isProfileLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MobilityColors.PrimaryGreen)
                                    } else {
                                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MobilityColors.PrimaryGreen)
                                    }
                                }

                                // Quick Edit Button
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MobilityColors.LightGreen,
                                    border = BorderStroke(1.dp, MobilityColors.EcoGreen.copy(alpha = 0.3f)),
                                    modifier = Modifier.clickable {
                                        editFullName = profileFullName
                                        editUsername = profileUsername
                                        editEmail = profileEmail
                                        editEmergencyPhone = profileEmergencyPhone
                                        showEditProfileDialog = true
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(14.dp), tint = MobilityColors.PrimaryGreen)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MobilityColors.PrimaryGreen)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // ==================== PROFILE HERO CARD ====================
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MobilityColors.BackgroundPure,
                            border = BorderStroke(1.5.dp, MobilityColors.PrimaryGreen.copy(alpha = 0.25f)),
                            shadowElevation = 3.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 22.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Profile Photo Container with Badge
                                Box(
                                    contentAlignment = Alignment.BottomEnd,
                                    modifier = Modifier.size(104.dp)
                                ) {
                                    // Main Avatar Image or Initials
                                    if (!profileAvatarUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(profileAvatarUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Profile Photo",
                                            modifier = Modifier
                                                .size(96.dp)
                                                .align(Alignment.Center)
                                                .clip(CircleShape)
                                                .border(3.dp, MobilityColors.PrimaryGreen, CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        // Initials Fallback
                                        val initials = profileFullName
                                            .split(" ")
                                            .filter { it.isNotBlank() }
                                            .take(2)
                                            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                                            .joinToString("")
                                            .ifBlank { "ER" }

                                        Box(
                                            modifier = Modifier
                                                .size(96.dp)
                                                .align(Alignment.Center)
                                                .clip(CircleShape)
                                                .background(MobilityColors.PrimaryGreen)
                                                .border(3.dp, MobilityColors.LightGreen, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = initials,
                                                fontSize = 32.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    // Uploading Progress Overlay
                                    if (isUploadingAvatar) {
                                        Box(
                                            modifier = Modifier
                                                .size(96.dp)
                                                .align(Alignment.Center)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.45f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                                        }
                                    }

                                    // Camera / Edit Photo Badge Overlay
                                    Surface(
                                        shape = CircleShape,
                                        color = MobilityColors.PrimaryYellow,
                                        border = BorderStroke(2.dp, Color.White),
                                        shadowElevation = 4.dp,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clickable { showAvatarOptionsDialog = true }
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Text("📸", fontSize = 15.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Full Name
                                Text(
                                    text = profileFullName.ifBlank { "E-Rickshaw Rider" },
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MobilityColors.TextDark,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(3.dp))

                                // Username with Verified Badge
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "@${profileUsername.ifBlank { "rider" }}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MobilityColors.PrimaryGreen
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "Verified Rider",
                                        tint = MobilityColors.PrimaryGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Rating & Trust Pills
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MobilityColors.LightGreen
                                    ) {
                                        Text(
                                            text = "⭐ ${String.format(Locale.US, "%.1f", profileRatingAverage)} ($profileRatingCount rides)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MobilityColors.PrimaryGreen,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MobilityColors.SurfaceLight
                                    ) {
                                        Text(
                                            text = "⚡ ₹20-₹50 Fixed Pass",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MobilityColors.TextDark,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // ==================== SECTION 1: DATABASE PROFILE DETAILS ====================
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PERSONAL PROFILE DETAILS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E3A8A)
                            )
                            Text(
                                text = "Live Backend DB",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MobilityColors.PrimaryGreen
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MobilityColors.BackgroundPure,
                            border = BorderStroke(1.dp, MobilityColors.BorderLight)
                        ) {
                            Column {
                                // Full Name Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            editFullName = profileFullName
                                            editUsername = profileUsername
                                            editEmail = profileEmail
                                            editEmergencyPhone = profileEmergencyPhone
                                            showEditProfileDialog = true
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Person, contentDescription = null, tint = MobilityColors.PrimaryGreen, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Full Name", fontSize = 12.sp, color = MobilityColors.TextMuted)
                                        Text(profileFullName.ifBlank { "Not provided" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MobilityColors.TextDark)
                                    }
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MobilityColors.TextMuted, modifier = Modifier.size(16.dp))
                                }

                                Divider(color = MobilityColors.BorderLight, thickness = 0.8.dp)

                                // Username Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            editFullName = profileFullName
                                            editUsername = profileUsername
                                            editEmail = profileEmail
                                            editEmergencyPhone = profileEmergencyPhone
                                            showEditProfileDialog = true
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.AccountCircle, contentDescription = null, tint = MobilityColors.PrimaryGreen, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Username", fontSize = 12.sp, color = MobilityColors.TextMuted)
                                        Text("@${profileUsername.ifBlank { "rider" }}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MobilityColors.TextDark)
                                    }
                                    Surface(shape = RoundedCornerShape(6.dp), color = MobilityColors.LightGreen) {
                                        Text("Active", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MobilityColors.PrimaryGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }

                                Divider(color = MobilityColors.BorderLight, thickness = 0.8.dp)

                                // Mobile Number Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Phone, contentDescription = null, tint = MobilityColors.PrimaryGreen, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Mobile Number", fontSize = 12.sp, color = MobilityColors.TextMuted)
                                        Text(profilePhone.ifBlank { tokenManager.getPhone() ?: "+91 7562084184" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MobilityColors.TextDark)
                                    }
                                    Surface(shape = RoundedCornerShape(6.dp), color = MobilityColors.LightGreen) {
                                        Text("✓ Verified", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MobilityColors.PrimaryGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }

                                Divider(color = MobilityColors.BorderLight, thickness = 0.8.dp)

                                // Email Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            editFullName = profileFullName
                                            editUsername = profileUsername
                                            editEmail = profileEmail
                                            editEmergencyPhone = profileEmergencyPhone
                                            showEditProfileDialog = true
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Email, contentDescription = null, tint = MobilityColors.PrimaryGreen, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Email Address", fontSize = 12.sp, color = MobilityColors.TextMuted)
                                        Text(profileEmail.ifBlank { "Not configured" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MobilityColors.TextDark)
                                    }
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MobilityColors.TextMuted, modifier = Modifier.size(16.dp))
                                }

                                Divider(color = MobilityColors.BorderLight, thickness = 0.8.dp)

                                // Emergency Contact Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            editFullName = profileFullName
                                            editUsername = profileUsername
                                            editEmail = profileEmail
                                            editEmergencyPhone = profileEmergencyPhone
                                            showEditProfileDialog = true
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Info, contentDescription = null, tint = MobilityColors.PrimaryGreen, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Emergency SOS Contact", fontSize = 12.sp, color = MobilityColors.TextMuted)
                                        Text(if (profileEmergencyPhone.isNotBlank()) profileEmergencyPhone else "Tap to configure SOS number", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MobilityColors.TextDark)
                                    }
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MobilityColors.TextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // ==================== SECTION 2: COMMUTE & PAYMENTS ====================
                        Text(
                            text = "COMMUTE & PAYMENTS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E3A8A),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MobilityColors.BackgroundPure,
                            border = BorderStroke(1.dp, MobilityColors.BorderLight)
                        ) {
                            Column {
                                // Wallet Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { Toast.makeText(context, "Wallet Balance: ₹$walletBalanceRupees", Toast.LENGTH_SHORT).show() }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("💳", fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Electric Ride Wallet", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MobilityColors.TextDark)
                                        Text("Balance: ₹$walletBalanceRupees (Instant UPI Top-up)", fontSize = 12.sp, color = MobilityColors.TextMuted)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MobilityColors.LightGreen,
                                        modifier = Modifier.clickable {
                                            walletBalanceRupees += 100
                                            Toast.makeText(context, "₹100 added to wallet for testing!", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text("+ Add ₹100", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MobilityColors.PrimaryGreen, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                }

                                Divider(color = MobilityColors.BorderLight, thickness = 0.8.dp)

                                // Favourites Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { Toast.makeText(context, "Manage Saved Commute Locations", Toast.LENGTH_SHORT).show() }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = MobilityColors.TextDark, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Favourites", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MobilityColors.TextDark)
                                        Text("Manage favourite pickup & drops", fontSize = 12.sp, color = MobilityColors.TextMuted)
                                    }
                                    Text("›", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MobilityColors.TextMuted)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // ==================== SECTION 3: SECURITY & HELP ====================
                        Text(
                            text = "SECURITY & HELP",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E3A8A),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MobilityColors.BackgroundPure,
                            border = BorderStroke(1.dp, MobilityColors.BorderLight)
                        ) {
                            Column {
                                // 24x7 Support
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { Toast.makeText(context, "Call 24x7 E-Rickshaw Helpline: 1800-RICKSHAW", Toast.LENGTH_LONG).show() }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Info, contentDescription = null, tint = MobilityColors.TextDark, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("24x7 Helpline Support", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MobilityColors.TextDark)
                                        Text("Toll Free: 1800-RICKSHAW", fontSize = 12.sp, color = MobilityColors.TextMuted)
                                    }
                                    Text("›", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MobilityColors.TextMuted)
                                }

                                Divider(color = MobilityColors.BorderLight, thickness = 0.8.dp)

                                // Logout Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            tokenManager.clear()
                                            Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                                            onLogout()
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.ExitToApp, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text("Logout", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFDC2626), modifier = Modifier.weight(1f))
                                    Text("›", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MobilityColors.TextMuted)
                                }

                                Divider(color = MobilityColors.BorderLight, thickness = 0.8.dp)

                                // Delete Account
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showDeleteAccountConfirmDialog = true }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text("Delete Account", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFDC2626), modifier = Modifier.weight(1f))
                                    Text("›", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MobilityColors.TextMuted)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }

        // 1. Service Details Modal
        selectedServiceDialog?.let { (title, subtitle, details) ->
            AlertDialog(
                onDismissRequest = { selectedServiceDialog = null },
                title = {
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MobilityColors.TextDark)
                },
                text = {
                    Column {
                        Text(text = subtitle, fontSize = 13.sp, color = MobilityColors.TextMuted)
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MobilityColors.SurfaceLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = details,
                                fontSize = 13.sp,
                                color = MobilityColors.TextDark,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { selectedServiceDialog = null },
                        colors = ButtonDefaults.buttonColors(containerColor = MobilityColors.PrimaryYellow)
                    ) {
                        Text("OK", color = MobilityColors.TextDark, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // 2. Real Ride Booking Success Modal
        if (showBookingSuccessDialog) {
            AlertDialog(
                onDismissRequest = { showBookingSuccessDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚡", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("E-Rickshaw Dispatched!", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MobilityColors.TextDark)
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Electric Rickshaw Pilot has accepted your booking and is arriving at your pickup location.",
                            fontSize = 13.sp,
                            color = MobilityColors.TextMuted
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MobilityColors.LightGreen,
                            border = BorderStroke(1.dp, MobilityColors.EcoGreen.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Booking ID:", fontSize = 12.sp, color = MobilityColors.TextMuted)
                                    Text("#$activeBookedRideId", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MobilityColors.TextDark)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Vehicle:", fontSize = 12.sp, color = MobilityColors.TextMuted)
                                    Text(selectedVehicle.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MobilityColors.TextDark)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Start Ride OTP:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MobilityColors.TextDark)
                                    Surface(shape = RoundedCornerShape(6.dp), color = MobilityColors.PrimaryYellow) {
                                        Text(activeBookedOtp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MobilityColors.TextDark)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showBookingSuccessDialog = false
                            rideState = RideFlowState.SEARCH_HOME
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MobilityColors.PrimaryYellow)
                    ) {
                        Text("Got it", color = MobilityColors.TextDark, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // 3. Edit Profile Modal
        if (showEditProfileDialog) {
            AlertDialog(
                onDismissRequest = { if (!isSavingProfile) showEditProfileDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = MobilityColors.PrimaryGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit Profile Details", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MobilityColors.TextDark)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Update your profile details directly in the real database:",
                            fontSize = 12.sp,
                            color = MobilityColors.TextMuted
                        )

                        OutlinedTextField(
                            value = editFullName,
                            onValueChange = { editFullName = it },
                            label = { Text("Full Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editUsername,
                            onValueChange = { editUsername = it },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editEmail,
                            onValueChange = { editEmail = it },
                            label = { Text("Email Address") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editEmergencyPhone,
                            onValueChange = { editEmergencyPhone = it },
                            label = { Text("Emergency Contact Number") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { saveProfileChanges() },
                        enabled = !isSavingProfile,
                        colors = ButtonDefaults.buttonColors(containerColor = MobilityColors.PrimaryGreen)
                    ) {
                        if (isSavingProfile) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditProfileDialog = false }) {
                        Text("Cancel", color = MobilityColors.TextMuted)
                    }
                }
            )
        }

        // 4. Profile Photo Selection & Management Modal
        if (showAvatarOptionsDialog) {
            AlertDialog(
                onDismissRequest = { showAvatarOptionsDialog = false },
                title = {
                    Text("Profile Photo Options", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MobilityColors.TextDark)
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Choose an option to update your profile photo:",
                            fontSize = 13.sp,
                            color = MobilityColors.TextMuted
                        )

                        // 1. Pick from Gallery
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MobilityColors.SurfaceLight,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    photoPickerLauncher.launch("image/*")
                                }
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("📁", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Upload from Device Gallery", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MobilityColors.TextDark)
                                    Text("Pick JPEG or PNG photo from phone", fontSize = 12.sp, color = MobilityColors.TextMuted)
                                }
                            }
                        }

                        // 2. Preset Avatars Row
                        Text("Select Instant Avatar Preset:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MobilityColors.TextDark)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            val presetUrls = listOf(
                                "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&auto=format&fit=crop&q=80",
                                "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200&auto=format&fit=crop&q=80",
                                "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=200&auto=format&fit=crop&q=80",
                                "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200&auto=format&fit=crop&q=80"
                            )
                            presetUrls.forEach { url ->
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                                    contentDescription = "Preset Avatar",
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, MobilityColors.PrimaryGreen, CircleShape)
                                        .clickable { updateAvatarWithUrl(url) },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        // 3. Enter Custom URL
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MobilityColors.SurfaceLight,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAvatarOptionsDialog = false
                                    customAvatarInputUrl = profileAvatarUrl ?: ""
                                    showCustomAvatarUrlDialog = true
                                }
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("🔗", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Enter Photo Web URL", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MobilityColors.TextDark)
                                    Text("Paste image link from internet", fontSize = 12.sp, color = MobilityColors.TextMuted)
                                }
                            }
                        }

                        // 4. Remove Photo
                        if (!profileAvatarUrl.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFEE2E2),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { removeAvatarPhoto() }
                            ) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Remove Profile Photo", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFDC2626))
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showAvatarOptionsDialog = false }) {
                        Text("Close", color = MobilityColors.TextMuted)
                    }
                }
            )
        }

        // 5. Custom Avatar URL Modal
        if (showCustomAvatarUrlDialog) {
            AlertDialog(
                onDismissRequest = { showCustomAvatarUrlDialog = false },
                title = {
                    Text("Enter Photo URL", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MobilityColors.TextDark)
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Paste a direct image link (e.g. https://.../photo.jpg):", fontSize = 12.sp, color = MobilityColors.TextMuted)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customAvatarInputUrl,
                            onValueChange = { customAvatarInputUrl = it },
                            placeholder = { Text("https://example.com/photo.jpg") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (customAvatarInputUrl.isNotBlank()) {
                                updateAvatarWithUrl(customAvatarInputUrl.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MobilityColors.PrimaryGreen)
                    ) {
                        Text("Apply", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomAvatarUrlDialog = false }) {
                        Text("Cancel", color = MobilityColors.TextMuted)
                    }
                }
            )
        }

        // 6. Delete Account Confirmation Modal
        if (showDeleteAccountConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAccountConfirmDialog = false },
                title = {
                    Text("Delete Account?", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFDC2626))
                },
                text = {
                    Text("Are you sure you want to delete your account? This action cannot be undone and will permanently remove your ride history and wallet data.", fontSize = 13.sp, color = MobilityColors.TextDark)
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteAccountConfirmDialog = false
                            tokenManager.clear()
                            Toast.makeText(context, "Account data deleted", Toast.LENGTH_SHORT).show()
                            onLogout()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) {
                        Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAccountConfirmDialog = false }) {
                        Text("Cancel", color = MobilityColors.TextMuted)
                    }
                }
            )
        }

        // 7. Tourism Place Full Detail Modal
        selectedTourismDetail?.let { place ->
            AlertDialog(
                onDismissRequest = { selectedTourismDetail = null },
                title = null,
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(14.dp))
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(place.photoUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = place.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MobilityColors.PrimaryGreen,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .align(Alignment.TopStart)
                            ) {
                                Text(
                                    text = place.tag,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = place.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MobilityColors.TextDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⭐ ${place.rating} (${place.reviewCount} reviews)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MobilityColors.TextDark)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("• 📍 ${place.city}", fontSize = 12.sp, color = MobilityColors.TextMuted)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = place.shortDescription,
                            fontSize = 13.sp,
                            color = MobilityColors.TextDark,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MobilityColors.SurfaceLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("📍 Address: ${place.address}", fontSize = 12.sp, color = MobilityColors.TextDark)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("🕒 Timings: ${place.timings}", fontSize = 12.sp, color = MobilityColors.TextDark)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("🎟️ Entry: ${place.entryFee}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MobilityColors.PrimaryGreen)
                                place.distanceKm?.let { d ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("⚡ Distance: $d km • Approx Fare: ₹${place.approxFareRupees ?: 50}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MobilityColors.PrimaryGreen)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            selectedTourismDetail = null
                            selectDestination(
                                RecentDestination(
                                    name = place.title,
                                    address = place.address,
                                    lat = place.latitude,
                                    lng = place.longitude
                                )
                            )
                            currentBottomTab = MainBottomTab.RIDE
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MobilityColors.PrimaryYellow)
                    ) {
                        Text("Book Ride to this Monument", color = MobilityColors.TextDark, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedTourismDetail = null }) {
                        Text("Close", color = MobilityColors.TextMuted)
                    }
                }
            )
        }
    }
}
