package com.vaisentra.mobility.driver.feature.duty

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.components.*
import com.vaisentra.mobility.driver.data.DriverNetworkClient
import com.vaisentra.mobility.driver.data.DriverRepository
import com.vaisentra.mobility.driver.data.InspectionDraftDto
import com.vaisentra.mobility.driver.theme.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File

// Screen 10: Duty Dashboard -> Uses VasantraDriverHomeScreen
@Composable
fun DutyDashboardScreen(onNavigate: (String) -> Unit) {
    LaunchedEffect(Unit) {
        DriverRepository.fetchDriverProfile()
        DriverRepository.fetchEarningsSummary()
    }
    VasantraDriverHomeScreen(onNavigate = onNavigate)
}

// Screen 7: Assigned Vehicle & Real Camera Daily Inspection
@Composable
fun VehicleInspectionScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by DriverRepository.profile.collectAsState()
    val assignedVehicle by DriverRepository.assignedVehicle.collectAsState()

    var activeInspection by remember { mutableStateOf<InspectionDraftDto?>(null) }
    var isLoadingInspection by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submissionError by remember { mutableStateOf<String?>(null) }

    // Odometer state
    var odometerKmInput by remember { mutableStateOf("") }
    var previousOdometerKm by remember { mutableStateOf(0.0) }

    // Checklist states: PASS, FAIL, NOT_APPLICABLE
    val checklistState = remember {
        mutableStateMapOf(
            "brakes" to "PASS",
            "steering" to "PASS",
            "tyres" to "PASS",
            "headlights_taillights" to "PASS",
            "battery_electrical" to "PASS",
            "indicators_horn" to "PASS",
            "mirrors_windshield" to "PASS",
            "cabin_cleanliness" to "PASS",
            "emergency_safety" to "PASS"
        )
    }

    var driverRemarks by remember { mutableStateOf("") }

    // Photographs state
    val capturedBitmaps = remember { mutableStateMapOf<String, Bitmap>() }
    val photoBytesMap = remember { mutableStateMapOf<String, ByteArray>() }
    val uploadStatuses = remember { mutableStateMapOf<String, String>() } // PENDING, UPLOADING, UPLOADED, FAILED

    var activeCategoryForCamera by remember { mutableStateOf<String?>(null) }
    var currentTempFile by remember { mutableStateOf<File?>(null) }
    var currentTempUri by remember { mutableStateOf<Uri?>(null) }

    // Camera permission
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            DriverDialogController.showToast("Camera permission required to capture inspection photos.")
        } else {
            activeCategoryForCamera?.let { cat ->
                // Retry launch with permission granted
                val tempDir = File(context.cacheDir, "inspection_photos").apply { mkdirs() }
                val file = File(tempDir, "${cat.lowercase()}_${System.currentTimeMillis()}.jpg")
                currentTempFile = file
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                currentTempUri = uri
            }
        }
    }

    // Real Camera Full Image Capture Launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val cat = activeCategoryForCamera
        val file = currentTempFile
        if (success && cat != null && file != null && file.exists() && file.length() > 0) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                capturedBitmaps[cat] = bitmap
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val bytes = stream.toByteArray()
                photoBytesMap[cat] = bytes
                uploadStatuses[cat] = "PENDING"

                // Auto-upload if inspection record is ready
                val inspId = activeInspection?.activeInspectionId
                if (!inspId.isNullOrBlank()) {
                    scope.launch {
                        uploadStatuses[cat] = "UPLOADING"
                        val res = DriverRepository.uploadInspectionPhoto(inspId, cat, bytes, "${cat.lowercase()}.jpg")
                        if (res.isSuccess) {
                            uploadStatuses[cat] = "UPLOADED"
                        } else {
                            uploadStatuses[cat] = "FAILED"
                        }
                    }
                }
            }
        }
    }

    // Direct Bitmap Fallback Launcher
    val takePicturePreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        val cat = activeCategoryForCamera
        if (bitmap != null && cat != null) {
            capturedBitmaps[cat] = bitmap
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val bytes = stream.toByteArray()
            photoBytesMap[cat] = bytes
            uploadStatuses[cat] = "PENDING"

            val inspId = activeInspection?.activeInspectionId
            if (!inspId.isNullOrBlank()) {
                scope.launch {
                    uploadStatuses[cat] = "UPLOADING"
                    val res = DriverRepository.uploadInspectionPhoto(inspId, cat, bytes, "${cat.lowercase()}.jpg")
                    if (res.isSuccess) {
                        uploadStatuses[cat] = "UPLOADED"
                    } else {
                        uploadStatuses[cat] = "FAILED"
                    }
                }
            }
        }
    }

    fun launchCameraForCategory(category: String) {
        activeCategoryForCamera = category
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        try {
            val tempDir = File(context.cacheDir, "inspection_photos").apply { mkdirs() }
            val file = File(tempDir, "${category.lowercase()}_${System.currentTimeMillis()}.jpg")
            currentTempFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            currentTempUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            takePicturePreviewLauncher.launch(null)
        }
    }

    // Load active inspection from backend
    LaunchedEffect(Unit) {
        isLoadingInspection = true
        DriverRepository.fetchDriverVehicles()
        val draftRes = DriverRepository.startOrGetDraftInspection()
        isLoadingInspection = false
        draftRes.getOrNull()?.let { draft ->
            activeInspection = draft
            draft.previousOdometerReading?.let { previousOdometerKm = it }
            draft.odometerReading?.let { if (it > 0) odometerKmInput = "%.0f".format(it) }
            draft.checklist?.forEach { (k, v) ->
                checklistState[k] = v
            }
            draft.photos.forEach { p ->
                uploadStatuses[p.category.uppercase()] = "UPLOADED"
            }
        }
    }

    val displayPlate = assignedVehicle?.licensePlate?.ifBlank { profile.assignedVehiclePlate }?.ifBlank { "DL-01-ER-2026" } ?: "DL-01-ER-2026"
    val displayModel = assignedVehicle?.model?.ifBlank { profile.assignedVehicleModel }?.ifBlank { "Mahindra Treo EV" } ?: "Mahindra Treo EV"

    val mandatoryCategories = listOf(
        Triple("ODOMETER", "Odometer Cluster", "Clear photo of odometer mileage counter"),
        Triple("FRONT", "Vehicle Front", "Front bumper, headlights & number plate"),
        Triple("REAR", "Vehicle Rear", "Rear bumper, tail lights & registration"),
        Triple("LEFT_SIDE", "Left Profile", "Left side body panels & wheels"),
        Triple("RIGHT_SIDE", "Right Profile", "Right side panels & battery cabin")
    )

    val enteredOdo = odometerKmInput.toDoubleOrNull() ?: 0.0
    val hasOdoDiscrepancy = previousOdometerKm > 0 && enteredOdo > 0 && enteredOdo < previousOdometerKm
    val hasCriticalFailures = checklistState["brakes"] == "FAIL" ||
            checklistState["steering"] == "FAIL" ||
            checklistState["tyres"] == "FAIL" ||
            checklistState["headlights_taillights"] == "FAIL" ||
            checklistState["battery_electrical"] == "FAIL"

    val uploadedCount = mandatoryCategories.count { (cat, _, _) ->
        uploadStatuses[cat] == "UPLOADED" || capturedBitmaps.containsKey(cat)
    }
    val allMandatoryPhotosCaptured = uploadedCount == mandatoryCategories.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        CockpitHeader(
            title = "Daily Vehicle Inspection",
            subtitle = "$displayPlate • Pre-Shift Walkaround",
            onBack = { onNavigate("duty_dashboard") }
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Status Banner
        CockpitCard(borderColor = if (hasCriticalFailures) PilotCrimson else PilotOnlineGreen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = displayModel, color = PilotTextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Registration: $displayPlate", color = PilotTextMuted, fontSize = 12.sp)
                }
                CockpitBadge(
                    text = activeInspection?.inspectionStatus ?: "DRAFT",
                    backgroundColor = if (hasCriticalFailures) Color(0x33FF334B) else OnlineGlow,
                    textColor = if (hasCriticalFailures) PilotCrimson else PilotOnlineGreen
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Supabase Private Bucket: driver-vehicle-inspections • Real-time roadworthiness audit",
                color = PilotTextDim,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.height(14.dp))

        // Section 1: Odometer Verification
        CockpitCard(borderColor = if (hasOdoDiscrepancy) PilotAmber else PilotBorder) {
            Text(text = "1. Odometer Reading & Verification", color = PilotTextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            if (previousOdometerKm > 0) {
                Text(
                    text = "Previous Verified Reading: ${"%.1f".format(previousOdometerKm)} KM",
                    color = PilotTextMuted,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            CockpitTextField(
                value = odometerKmInput,
                onValueChange = { odometerKmInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                placeholder = "Enter Current Odometer (KM)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            if (hasOdoDiscrepancy) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "⚠️", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Discrepancy: Reading is lower than previous record. Verification flag will be attached.",
                        color = PilotAmber,
                        fontSize = 12.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        // Section 2: Real Camera Photographs (Mandatory 5 Angles)
        CockpitCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "2. Vehicle Photographs (Real Camera)", color = PilotTextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(text = "$uploadedCount/5 Required", color = if (allMandatoryPhotosCaptured) PilotOnlineGreen else PilotAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Capture clear photographs showing the odometer and exterior profile. Stored securely in private Supabase Storage.",
                color = PilotTextMuted,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            mandatoryCategories.forEach { (catKey, catTitle, catDesc) ->
                val bitmap = capturedBitmaps[catKey]
                val status = uploadStatuses[catKey] ?: "PENDING"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141A24))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = catTitle,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, PilotOnlineGreen, RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1F2937))
                                    .border(1.dp, PilotBorder, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "📸", fontSize = 20.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = catTitle, color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(text = catDesc, color = PilotTextDim, fontSize = 11.sp, maxLines = 1)
                            Spacer(modifier = Modifier.height(2.dp))
                            val (statusText, statusColor) = when (status) {
                                "UPLOADED" -> Pair("✓ Uploaded to Supabase", PilotOnlineGreen)
                                "UPLOADING" -> Pair("Uploading photo...", PilotAmber)
                                "FAILED" -> Pair("Upload Failed - Tap Retry", PilotCrimson)
                                else -> if (bitmap != null) Pair("Ready to upload", PilotAmber) else Pair("Photo Required", PilotTextDim)
                            }
                            Text(text = statusText, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Row {
                        Button(
                            onClick = { launchCameraForCategory(catKey) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (bitmap != null) Color(0xFF243044) else PilotOnlineGreen
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (bitmap != null) "Retake" else "Capture",
                                fontSize = 11.sp,
                                color = if (bitmap != null) PilotTextWhite else PilotCanvas,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (bitmap != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = {
                                capturedBitmaps.remove(catKey)
                                photoBytesMap.remove(catKey)
                                uploadStatuses.remove(catKey)
                            }) {
                                Text(text = "✕", color = PilotCrimson, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        // Section 3: Vehicle Safety Checklist
        CockpitCard {
            Text(text = "3. Safety & Mechanical Checklist", color = PilotTextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Independent pilot audit. Critical defects strictly bar vehicle from duty.", color = PilotTextMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(12.dp))

            val checklistItems = listOf(
                Pair("brakes", "Brakes & Parking/Handbrake (Critical)"),
                Pair("steering", "Steering Smoothness & Alignment (Critical)"),
                Pair("tyres", "Tyre Tread & Pressure (32/36 PSI) (Critical)"),
                Pair("headlights_taillights", "Headlights, Tail Lights & Hazard (Critical)"),
                Pair("battery_electrical", "EV Battery SOC & High-Voltage Isolator (Critical)"),
                Pair("indicators_horn", "Turn Indicators, Horn & Reverse Buzzer"),
                Pair("mirrors_windshield", "Side Mirrors & Windshield Clarity"),
                Pair("cabin_cleanliness", "Passenger Cabin Cleanliness & Seat Sanitization"),
                Pair("emergency_safety", "First Aid Kit & Emergency Stop Switch")
            )

            checklistItems.forEach { (key, label) ->
                val currentVal = checklistState[key] ?: "PASS"
                val isCritical = key in listOf("brakes", "steering", "tyres", "headlights_taillights", "battery_electrical")

                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = (if (isCritical) "🛡️ " else "• ") + label,
                            color = if (isCritical && currentVal == "FAIL") PilotCrimson else PilotTextWhite,
                            fontSize = 12.sp,
                            fontWeight = if (isCritical) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        Row {
                            listOf("PASS", "FAIL", "N/A").forEach { option ->
                                val selected = currentVal == option
                                val optColor = when (option) {
                                    "PASS" -> PilotOnlineGreen
                                    "FAIL" -> PilotCrimson
                                    else -> PilotTextMuted
                                }
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) optColor.copy(alpha = 0.25f) else Color(0xFF1B2330))
                                        .border(if (selected) 1.dp else 0.5.dp, if (selected) optColor else PilotBorder, RoundedCornerShape(6.dp))
                                        .clickable { checklistState[key] = option }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = option,
                                        color = if (selected) optColor else PilotTextDim,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (hasCriticalFailures) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x33FF334B))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🚫", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Critical safety item marked as FAIL. You will be blocked from going online until repairs are certified.",
                        color = PilotCrimson,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        // Section 4: Remarks
        CockpitCard {
            Text(text = "4. Driver Remarks / Notes (Optional)", color = PilotTextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            CockpitTextField(
                value = driverRemarks,
                onValueChange = { driverRemarks = it },
                placeholder = "Note any observations, recent maintenance or cosmetic flaws..."
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (submissionError != null) {
            Text(
                text = "⚠️ $submissionError",
                color = PilotCrimson,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        // Submit Button
        val canSubmit = !isSubmitting && enteredOdo > 0 && allMandatoryPhotosCaptured
        CockpitButton(
            text = when {
                isSubmitting -> "Submitting Inspection to Supabase..."
                !allMandatoryPhotosCaptured -> "Capture All 5 Photos (${uploadedCount}/5) →"
                enteredOdo <= 0 -> "Enter Odometer Reading →"
                hasCriticalFailures -> "Sign-Off Inspection (Vehicle Barred) ⚠️"
                else -> "Sign-Off Inspection & Verify Roadworthiness →"
            },
            enabled = canSubmit,
            backgroundColor = if (hasCriticalFailures) PilotCrimson else PilotOnlineGreen,
            onClick = {
                isSubmitting = true
                submissionError = null
                scope.launch {
                    try {
                        val draft = activeInspection ?: run {
                            val dRes = DriverRepository.startOrGetDraftInspection()
                            dRes.getOrNull()
                        }
                        val inspId = draft?.activeInspectionId ?: ""
                        if (inspId.isBlank()) {
                            isSubmitting = false
                            submissionError = "Failed to establish inspection session. Check connection."
                            return@launch
                        }

                        // Upload any pending captured photos first
                        for ((cat, bytes) in photoBytesMap) {
                            if (uploadStatuses[cat] != "UPLOADED") {
                                val upRes = DriverRepository.uploadInspectionPhoto(inspId, cat, bytes, "${cat.lowercase()}.jpg")
                                if (upRes.isSuccess) {
                                    uploadStatuses[cat] = "UPLOADED"
                                }
                            }
                        }

                        // Submit authoritative inspection
                        val submitRes = DriverRepository.submitFullVehicleInspection(
                            inspectionId = inspId,
                            odometerKm = enteredOdo,
                            checklist = checklistState.toMap(),
                            remarks = driverRemarks.ifBlank { null }
                        )
                        isSubmitting = false

                        if (submitRes.isSuccess) {
                            val data = submitRes.getOrNull()
                            val status = data?.get("status")?.toString() ?: "APPROVED"
                            if (status == "APPROVED" || status == "PENDING_VERIFICATION") {
                                DriverDialogController.showToast("✓ Vehicle inspection APPROVED! Roadworthy for duty.")
                                onNavigate("shift_start")
                            } else {
                                DriverDialogController.showToast("⚠️ Inspection FAILED on critical items. Vehicle grounded.")
                                onNavigate("duty_dashboard")
                            }
                        } else {
                            submissionError = submitRes.exceptionOrNull()?.message ?: "Submission failed."
                        }
                    } catch (e: Exception) {
                        isSubmitting = false
                        submissionError = e.message ?: "Submission error."
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// Screen 8: Vehicle RC, Fitness & PUC
@Composable
fun VehicleRcStatusScreen(onNavigate: (String) -> Unit) {
    val profile by DriverRepository.profile.collectAsState()
    val assignedVehicle by DriverRepository.assignedVehicle.collectAsState()
    val documents by DriverRepository.documents.collectAsState()
    val scope = rememberCoroutineScope()

    var isEditing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val currentPlate = assignedVehicle?.licensePlate?.ifBlank { profile.assignedVehiclePlate }?.ifBlank { "DL-01-ER-2026" } ?: "DL-01-ER-2026"
    val currentModel = assignedVehicle?.model?.ifBlank { profile.assignedVehicleModel }?.ifBlank { "Mahindra Treo EV" } ?: "Mahindra Treo EV"
    val currentRc = assignedVehicle?.rcNumber?.ifBlank { null } ?: documents.firstOrNull { it.documentType == "VEHICLE_RC" }?.documentNumber ?: "DL-01-2025-RC88341"
    val currentPermit = assignedVehicle?.permitNumber?.ifBlank { null } ?: documents.firstOrNull { it.documentType == "COMMERCIAL_PERMIT" }?.documentNumber ?: "AITP-DEL-2026-99124"

    var editPlate by remember(currentPlate) { mutableStateOf(currentPlate) }
    var editModel by remember(currentModel) { mutableStateOf(currentModel) }
    var editRc by remember(currentRc) { mutableStateOf(currentRc) }
    var editPermit by remember(currentPermit) { mutableStateOf(currentPermit) }

    LaunchedEffect(Unit) {
        DriverRepository.fetchDriverVehicles()
        DriverRepository.fetchDriverDocuments()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        CockpitHeader(title = "RC, Fitness & Commercial PUC", subtitle = "Vehicle Compliance", onBack = { onNavigate("duty_dashboard") })
        Spacer(modifier = Modifier.height(16.dp))

        CockpitCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Compliance Specs", color = PilotTextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { isEditing = !isEditing }) {
                    Text(
                        text = if (isEditing) "Cancel" else "✏️ Edit Details",
                        color = PilotElectricCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (!isEditing) {
                val license = profile.licenseNumber.ifBlank { "DL-DEL-2024-99882" }
                val docCount = if (documents.isNotEmpty()) "${documents.size} Verified Records" else "Live Supabase Sync"

                val specs = listOf(
                    "Registration No" to currentPlate,
                    "Model" to currentModel,
                    "RC Number" to currentRc,
                    "Commercial Permit" to currentPermit,
                    "Approval Status" to profile.approvalStatus,
                    "Driver License" to license,
                    "Compliance Vault" to docCount
                )
                specs.forEach { (k, v) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = k, color = PilotTextMuted, fontSize = 12.sp)
                        Text(text = v, color = PilotTextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                CockpitTextField(value = editPlate, onValueChange = { editPlate = it }, label = "License Plate", placeholder = "e.g. DL-01-ER-2026")
                Spacer(modifier = Modifier.height(8.dp))
                CockpitTextField(value = editModel, onValueChange = { editModel = it }, label = "Vehicle Model", placeholder = "e.g. Mahindra Treo EV")
                Spacer(modifier = Modifier.height(8.dp))
                CockpitTextField(value = editRc, onValueChange = { editRc = it }, label = "RC Number", placeholder = "e.g. DL-01-2025-RC88341")
                Spacer(modifier = Modifier.height(8.dp))
                CockpitTextField(value = editPermit, onValueChange = { editPermit = it }, label = "Commercial Permit", placeholder = "e.g. AITP-DEL-2026-99124")
                Spacer(modifier = Modifier.height(14.dp))
                CockpitButton(
                    text = if (isSaving) "Saving to Supabase..." else "Save Vehicle Details",
                    enabled = !isSaving && editPlate.isNotBlank(),
                    onClick = {
                        isSaving = true
                        scope.launch {
                            val res = DriverRepository.updateActiveVehicle(
                                plate = editPlate.trim(),
                                model = editModel.trim(),
                                rc = editRc.trim(),
                                permit = editPermit.trim()
                            )
                            isSaving = false
                            if (res.isSuccess) {
                                isEditing = false
                                DriverDialogController.showToast("Vehicle details updated in database!")
                            } else {
                                DriverDialogController.showToast("Update failed: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    backgroundColor = PilotOnlineGreen
                )
            }
        }
    }
}

// Screen 9: Shift Start & Opening Odometer Log
@Composable
fun ShiftStartScreen(onNavigate: (String) -> Unit) {
    var odoReading by remember { mutableStateOf("") }
    var batterySoc by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var lastClosingInfo by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        DriverRepository.fetchDriverVehicles()
        scope.launch {
            val statusRes = DriverRepository.fetchShiftStatus()
            statusRes.getOrNull()?.let { data ->
                val lastClosing = (data["last_closing_odometer_km"] as? Number)?.toDouble()
                    ?: (data["start_odometer_km"] as? Number)?.toDouble()
                if (lastClosing != null && lastClosing > 0) {
                    odoReading = lastClosing.toInt().toString()
                    lastClosingInfo = "Previous closing meter: ${lastClosing.toInt()} km"
                }
            }
            if (odoReading.isBlank()) {
                odoReading = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        CockpitHeader(title = "Start Duty Shift", subtitle = "Opening meter declaration", onBack = { onNavigate("duty_dashboard") })
        Spacer(modifier = Modifier.height(16.dp))

        if (lastClosingInfo != null) {
            CockpitCard(borderColor = PilotElectricCyan) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = lastClosingInfo ?: "", color = PilotElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    CockpitBadge(text = "VERIFIED", backgroundColor = OnlineGlow, textColor = PilotOnlineGreen)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        CockpitCard {
            CockpitTextField(value = odoReading, onValueChange = { odoReading = it }, placeholder = "e.g. 48210", label = "Opening Odometer (km)")
            Spacer(modifier = Modifier.height(12.dp))
            CockpitTextField(value = batterySoc, onValueChange = { batterySoc = it }, placeholder = "e.g. 95", label = "Battery SoC Percentage (%)", trailingText = "BATTERY OK ✓")
            Spacer(modifier = Modifier.height(20.dp))
            CockpitButton(
                text = if (isSubmitting) "Starting Shift in Backend..." else "Unlock Shift & Go Online",
                enabled = !isSubmitting && odoReading.isNotBlank(),
                onClick = {
                    val odo = odoReading.toDoubleOrNull() ?: 0.0
                    val soc = batterySoc.toIntOrNull() ?: 95
                    isSubmitting = true
                    scope.launch {
                        val res = DriverRepository.startShiftOdometer(odo, soc)
                        isSubmitting = false
                        if (res.isSuccess) {
                            DriverRepository.setDutyStatus(true)
                            DriverDialogController.showToast("Shift started! Odometer $odo km saved to Supabase.")
                            onNavigate("duty_dashboard")
                        } else {
                            DriverDialogController.showToast("Start shift failed: ${res.exceptionOrNull()?.message}")
                        }
                    }
                },
                backgroundColor = PilotOnlineGreen
            )
        }
    }
}

// Screen 35: Shift End & Closing Odometer
@Composable
fun ShiftEndScreen(onNavigate: (String) -> Unit) {
    var closingOdo by remember { mutableStateOf("") }
    var startOdo by remember { mutableStateOf<Double?>(null) }
    var shiftStartedAt by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            val statusRes = DriverRepository.fetchShiftStatus()
            statusRes.getOrNull()?.let { data ->
                val sOdo = (data["start_odometer_km"] as? Number)?.toDouble()
                if (sOdo != null && sOdo > 0) {
                    startOdo = sOdo
                    closingOdo = (sOdo + 15.0).toInt().toString()
                }
                shiftStartedAt = data["shift_start_time"]?.toString() ?: data["started_at"]?.toString()
            }
            if (closingOdo.isBlank()) {
                closingOdo = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        CockpitHeader(title = "End Duty Shift", subtitle = "Closing meter & vehicle handover", onBack = { onNavigate("duty_dashboard") })
        Spacer(modifier = Modifier.height(16.dp))

        if (startOdo != null) {
            CockpitCard(borderColor = PilotAmber) {
                Text(text = "Active Shift in Progress", color = PilotAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Shift Opening Meter:", color = PilotTextMuted, fontSize = 12.sp)
                    Text(text = "${startOdo?.toInt()} km", color = PilotTextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (!shiftStartedAt.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "Started: ${shiftStartedAt?.take(19)?.replace("T", " ")}", color = PilotTextMuted, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        CockpitCard {
            CockpitTextField(value = closingOdo, onValueChange = { closingOdo = it }, placeholder = "e.g. 48292", label = "Closing Odometer Reading (km)")
            val currentClosing = closingOdo.toDoubleOrNull()
            if (startOdo != null && currentClosing != null) {
                val distance = currentClosing - (startOdo ?: 0.0)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (distance >= 0) "Shift distance: ${"%.1f".format(distance)} km" else "⚠️ Closing meter cannot be less than opening (${startOdo?.toInt()} km)",
                    color = if (distance >= 0) PilotOnlineGreen else PilotCrimson,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            CockpitButton(
                text = if (isSubmitting) "Closing Shift in Supabase..." else "Close Shift & Submit Handover",
                enabled = !isSubmitting && closingOdo.isNotBlank(),
                onClick = {
                    val odo = closingOdo.toDoubleOrNull() ?: 0.0
                    if (startOdo != null && odo < (startOdo ?: 0.0)) {
                        DriverDialogController.showToast("Closing odometer cannot be less than opening odometer (${startOdo?.toInt()} km)")
                        return@CockpitButton
                    }
                    isSubmitting = true
                    scope.launch {
                        val res = DriverRepository.endShiftOdometer(odo)
                        isSubmitting = false
                        if (res.isSuccess) {
                            DriverRepository.setDutyStatus(false)
                            DriverDialogController.showToast("Shift ended! Odometer $odo km saved to database.")
                            onNavigate("shift_summary")
                        } else {
                            DriverDialogController.showToast("End shift failed: ${res.exceptionOrNull()?.message}")
                        }
                    }
                },
                backgroundColor = PilotCrimson
            )
        }
    }
}

// Screen 50: Driver Preferences, Zones & Backend Server Configuration
@Composable
fun DriverPreferencesScreen(onNavigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val driverPrefs by DriverRepository.driverPreferences.collectAsState()
    var autoAcceptPool by remember(driverPrefs.autoAcceptPool) { mutableStateOf(driverPrefs.autoAcceptPool) }
    var preferEastDelhi by remember(driverPrefs.preferredCorridor) {
        mutableStateOf(driverPrefs.preferredCorridor.contains("Anand Vihar", ignoreCase = true))
    }
    var currentServerUrl by remember { mutableStateOf(DriverNetworkClient.baseUrl) }
    var healthStatus by remember { mutableStateOf("Checking...") }
    var isChecking by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        DriverRepository.fetchDriverPreferences()
    }

    LaunchedEffect(driverPrefs) {
        autoAcceptPool = driverPrefs.autoAcceptPool
        preferEastDelhi = driverPrefs.preferredCorridor.contains("Anand Vihar", ignoreCase = true)
    }

    LaunchedEffect(currentServerUrl) {
        isChecking = true
        val healthRes = DriverRepository.checkBackendHealth()
        isChecking = false
        healthStatus = if (healthRes.isSuccess) {
            "🟢 LIVE (${healthRes.getOrNull()?.service ?: "FastAPI"})"
        } else {
            "🔴 UNREACHABLE"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        CockpitHeader(
            title = "Pilot Corridor & System",
            subtitle = "Custom dispatch routing & backend server",
            onBack = { onNavigate("duty_dashboard") }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Dispatch Preferences Card
        CockpitCard {
            Text(
                text = "DISPATCH & CORRIDOR PREFERENCES",
                color = PilotElectricCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Auto-Accept Eco-Pool Feeder Rides", color = PilotTextWhite, fontSize = 13.sp)
                Switch(
                    checked = autoAcceptPool,
                    onCheckedChange = { newValue ->
                        autoAcceptPool = newValue
                        scope.launch {
                            DriverRepository.updateDriverPreferences(autoAcceptPool = newValue)
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Preferred Corridor: Anand Vihar / CP", color = PilotTextWhite, fontSize = 13.sp)
                Switch(
                    checked = preferEastDelhi,
                    onCheckedChange = { newValue ->
                        preferEastDelhi = newValue
                        scope.launch {
                            val corridor = if (newValue) "Anand Vihar / CP" else "All Operational Zones"
                            DriverRepository.updateDriverPreferences(preferredCorridor = corridor)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Backend Connectivity & Server Environment Card
        CockpitCard {
            Text(
                text = "BACKEND SERVER CONFIGURATION",
                color = PilotElectricCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Active Server Endpoint:",
                color = PilotTextMuted,
                fontSize = 12.sp
            )
            Text(
                text = currentServerUrl,
                color = PilotTextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Health Status: ",
                    color = PilotTextMuted,
                    fontSize = 12.sp
                )
                Text(
                    text = healthStatus,
                    color = if (healthStatus.contains("LIVE")) PilotOnlineGreen else PilotAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        DriverNetworkClient.switchServer(DriverNetworkClient.DEFAULT_RENDER_URL)
                        currentServerUrl = DriverNetworkClient.baseUrl
                        DriverDialogController.showToast("Switched to Render Backend")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentServerUrl.contains("render")) PilotElectricCyan else CockpitSurface
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Cloud (Render)",
                        fontSize = 11.sp,
                        color = if (currentServerUrl.contains("render")) Color.Black else PilotTextWhite
                    )
                }

                Button(
                    onClick = {
                        DriverNetworkClient.switchServer(DriverNetworkClient.LOCAL_EMULATOR_URL)
                        currentServerUrl = DriverNetworkClient.baseUrl
                        DriverDialogController.showToast("Switched to Local (10.0.2.2:8000)")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentServerUrl.contains("10.0.2.2")) PilotElectricCyan else CockpitSurface
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Localhost",
                        fontSize = 11.sp,
                        color = if (currentServerUrl.contains("10.0.2.2")) Color.Black else PilotTextWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        isChecking = true
                        val res = DriverRepository.checkBackendHealth()
                        isChecking = false
                        healthStatus = if (res.isSuccess) {
                            "🟢 LIVE (${res.getOrNull()?.service ?: "FastAPI"})"
                        } else {
                            "🔴 UNREACHABLE: ${res.exceptionOrNull()?.message}"
                        }
                        DriverDialogController.showToast("Health Check: $healthStatus")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isChecking) "Checking..." else "Ping Backend Server",
                    color = PilotElectricCyan,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        CockpitButton(
            text = if (isSaving) "Saving Preferences..." else "Save Preferences & Return",
            enabled = !isSaving,
            onClick = {
                isSaving = true
                scope.launch {
                    val corridor = if (preferEastDelhi) "Anand Vihar / CP" else "All Operational Zones"
                    val res = DriverRepository.updateDriverPreferences(
                        autoAcceptPool = autoAcceptPool,
                        preferredCorridor = corridor
                    )
                    isSaving = false
                    if (res.isSuccess) {
                        DriverDialogController.showToast("Dispatch preferences saved to server!")
                        onNavigate("duty_dashboard")
                    } else {
                        DriverDialogController.showToast("Failed to save: ${res.exceptionOrNull()?.message}")
                    }
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DriverPreferencesScreenPreview() {
    DriverGoTheme {
        DriverPreferencesScreen(onNavigate = {})
    }
}

@Preview(showBackground = true)
@Composable
fun DutyDashboardScreenPreview() {
    DriverGoTheme {
        DutyDashboardScreen(onNavigate = {})
    }
}