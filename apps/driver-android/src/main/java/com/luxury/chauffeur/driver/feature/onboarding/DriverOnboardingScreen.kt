package com.luxury.chauffeur.driver.feature.onboarding

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luxury.chauffeur.driver.core.designsystem.*
import com.luxury.chauffeur.driver.core.network.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

@Composable
fun DriverOnboardingScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var overview by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
    var background by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
    var expiries by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var documents by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var lessons by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var vehicles by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var tickets by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var preview by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
    var identityType by remember { mutableStateOf("AADHAAR") }
    var identityNumber by remember { mutableStateOf("") }
    var ticketSubject by remember { mutableStateOf("") }
    var ticketMessage by remember { mutableStateOf("") }
    var documentType by remember { mutableStateOf("DRIVING_LICENSE") }
    var guideVisible by remember { mutableStateOf(false) }
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var schedule by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var languages by remember { mutableStateOf("English, Hindi") }
    var zones by remember { mutableStateOf("AIRPORT_CORRIDOR, SOUTH_MUMBAI") }
    var categories by remember { mutableStateOf("EXECUTIVE_SEDAN") }
    var etiquette by remember { mutableStateOf("Professional") }
    var music by remember { mutableStateOf("Passenger preference") }
    var contactRelationship by remember { mutableStateOf("Emergency contact") }
    var consentChecked by remember { mutableStateOf(false) }
    var temperature by remember { mutableIntStateOf(21) }
    var inspectionItems by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var inspectionPhotos by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var inspectionResults by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var inspectionPhotoRefs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var pendingPhotoKey by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        loading = true
        scope.launch {
            try {
                val api = DriverApiClient.getService()
                val resultOverview = api.getOnboardingOverview()
                val resultBackground = api.getBackgroundCheck()
                val resultExpiries = api.getDocumentExpiries()
                val resultDocuments = api.getOnboardingDocuments()
                val resultLessons = api.getTrainingCurriculum()
                val resultVehicles = api.getOnboardingVehicles()
                val resultTickets = api.getOnboardingTickets()
                val resultPreview = api.getPublicPreview()
                val resultSchedule = api.getSchedule()
                overview = resultOverview.body() ?: emptyMap()
                languages = overview["languages_spoken"]?.toString() ?: languages
                zones = (overview["preferred_operating_zones"] as? List<*>)?.joinToString(", ") ?: zones
                categories = (overview["preferred_vehicle_categories"] as? List<*>)?.joinToString(", ") ?: categories
                etiquette = overview["service_etiquette_level"]?.toString() ?: etiquette
                music = overview["cabin_music_preference"]?.toString() ?: music
                temperature = (overview["cabin_temp_celsius"] as? Number)?.toInt() ?: temperature
                contactName = overview["emergency_contact_name"]?.toString() ?: ""
                contactPhone = overview["emergency_contact_phone"]?.toString() ?: ""
                contactRelationship = overview["emergency_contact_relationship"]?.toString() ?: contactRelationship
                background = resultBackground.body() ?: emptyMap()
                expiries = resultExpiries.body() ?: emptyList()
                documents = resultDocuments.body() ?: emptyList()
                lessons = resultLessons.body() ?: emptyList()
                vehicles = resultVehicles.body() ?: emptyList()
                tickets = resultTickets.body() ?: emptyList()
                preview = resultPreview.body() ?: emptyMap()
                schedule = resultSchedule.body()?.takeIf { it.isNotEmpty() } ?: (0..6).map { day -> mapOf("day_of_week" to day, "day_name" to listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[day], "start_time" to "08:00", "end_time" to "20:00", "is_active" to (day < 6), "is_rest_day" to (day == 6)) }
                loading = false
                if (listOf(resultOverview.isSuccessful, resultBackground.isSuccessful, resultExpiries.isSuccessful, resultDocuments.isSuccessful, resultLessons.isSuccessful, resultVehicles.isSuccessful, resultTickets.isSuccessful, resultPreview.isSuccessful, resultSchedule.isSuccessful).any { !it }) { error = true; message = "Some onboarding details could not be loaded. Retry to refresh." }
            } catch (e: Exception) {
                loading = false; error = true; message = e.localizedMessage ?: "Unable to connect. Check your connection and try again."
            }
        }
    }
    fun action(block: suspend DriverApiService.() -> retrofit2.Response<Map<String, Any>>) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val response = DriverApiClient.getService().block()
                val body = response.body()
                if (response.isSuccessful) { error = false; message = body?.get("message")?.toString() ?: "Your update was saved."; refresh() }
                else { error = true; message = "Could not save your update (${response.code()}). Review the details and retry." }
            } catch (e: Exception) { error = true; message = e.localizedMessage ?: "Could not save. Try again." }
            finally { busy = false }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        val key = pendingPhotoKey
        if (bitmap != null && key != null) {
            scope.launch {
                try {
                    val bytes = ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out); out.toByteArray() }
                    val vehicleId = vehicles.firstOrNull()?.get("vehicle_id")?.toString() ?: throw IllegalStateException("No assigned vehicle is available.")
                    val part = MultipartBody.Part.createFormData("file", "$key.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
                    val response = DriverApiClient.getService().uploadInspectionPhoto(vehicleId, key, part)
                    if (response.isSuccessful) { inspectionPhotoRefs = inspectionPhotoRefs + (key to response.body()?.get("photo_id").toString()); error = false; message = "$key photo stored privately." }
                    else { error = true; message = "Photo upload failed (${response.code()})." }
                } catch (e: Exception) { error = true; message = e.localizedMessage ?: "Photo upload failed." }
                pendingPhotoKey = null
            }
        } else if (bitmap != null) capturedImage = bitmap
    }

    if (guideVisible) AlertDialog(
        onDismissRequest = { guideVisible = false },
        title = { Text("Guided document capture", color = DriverColors.ChauffeurGold) },
        text = { Text("Place the original document on a flat, dark surface. Use bright even light, avoid glare, include all four corners, and keep text in focus. Do not submit someone else's document.", color = DriverColors.TextPure) },
        confirmButton = { TextButton(onClick = { guideVisible = false; camera.launch(null) }) { Text("OPEN CAMERA", color = DriverColors.ChauffeurGold) } },
        dismissButton = { TextButton(onClick = { guideVisible = false }) { Text("CANCEL") } },
        containerColor = DriverColors.SurfaceCard
    )

    Scaffold(containerColor = DriverColors.MidnightBlack, topBar = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("DRIVER ONBOARDING", style = DriverTypography.Title.copy(fontSize = 18.sp, color = DriverColors.ChauffeurGold)); Text("Your application, one clear step at a time", style = DriverTypography.Body.copy(fontSize = 12.sp, color = DriverColors.TextMuted)) }
            TextButton(onClick = onBack) { Text("BACK", color = DriverColors.ChauffeurGold) }
        }
    }) { padding ->
        if (loading) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DriverColors.ChauffeurGold); Text("  Loading your application…", color = DriverColors.TextPure) }
        else LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                if (message != null) Card(colors = CardDefaults.cardColors(containerColor = if (error) DriverColors.AlertDanger.copy(alpha = .14f) else DriverColors.StatusOnline.copy(alpha = .12f)), border = BorderStroke(1.dp, if (error) DriverColors.AlertDanger else DriverColors.StatusOnline)) { Text(message!!, Modifier.padding(14.dp), color = DriverColors.TextPure); if (error) TextButton(onClick = ::refresh) { Text("RETRY", color = DriverColors.ChauffeurGold) } }
                OnboardingCard("APPLICATION STATUS", "${overview["onboarding_step"] ?: "IN REVIEW"}  •  ${overview["approval_status"] ?: "PENDING"}") {
                    val percent = (overview["profile_completeness_percentage"] as? Number)?.toInt() ?: 0
                    Text("Profile completeness  $percent%", color = DriverColors.TextPure)
                    LinearProgressIndicator(progress = { percent.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = DriverColors.ChauffeurGold, trackColor = DriverColors.SurfaceBorder)
                    (overview["checklist_stages"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { stage -> Text("${if (stage["status"] == "COMPLETED") "✓" else "○"}  ${stage["title"]}: ${stage["status"]}", color = DriverColors.TextPure, modifier = Modifier.padding(vertical = 3.dp)) }
                    Text("Driver identity and background checks remain pending until an approved provider or reviewer confirms them.", color = DriverColors.TextMuted, fontSize = 12.sp)
                }
            }
            item { OnboardingCard("IDENTITY & RE-VERIFICATION", "${if (overview["identity_verified"] == true) "Verified" else "Review pending"}") {
                OutlinedTextField(identityType, { identityType = it.uppercase() }, label = { Text("ID type (for example, Aadhaar or passport)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(identityNumber, { identityNumber = it.filter(Char::isLetterOrDigit).take(24) }, label = { Text("ID number") }, modifier = Modifier.fillMaxWidth())
                Text("Your ID number is used for this submission and is not returned in full. Verification is not automatic.", color = DriverColors.TextMuted, fontSize = 12.sp)
                GoldAction("SUBMIT FOR IDENTITY REVIEW", busy, enabled = identityNumber.length >= 6) { action { submitIdentity(IdentityVerificationPayload(identityType, identityNumber)) } }
                Text("Background check: ${background["background_check_status"] ?: "Loading"}", color = DriverColors.ChauffeurGold)
                if (background["provider_configured"] == false) Text("Screening provider is not configured; no police clearance is claimed.", color = DriverColors.TextMuted, fontSize = 12.sp)
            } }
            item { OnboardingCard("DOCUMENTS & EXPIRY REMINDERS", "${expiries.size} document record(s)") {
                if (expiries.isEmpty()) Text("No documents submitted yet. Capture each original clearly; a reviewer will assess it.", color = DriverColors.TextMuted)
                expiries.forEach { doc -> Text("${doc["document_type"]}: ${doc["status"]} • ${doc["days_remaining"]} days remaining", color = DriverColors.TextPure) }
                documents.forEach { doc ->
                    Text("${doc["document_type"]}: ${doc["verification_status"]} • ${doc["file_name"]}", color = DriverColors.TextPure)
                    (doc["rejection_reason"] as? String)?.takeIf { it.isNotBlank() }?.let { Text("Reviewer: $it", color = DriverColors.AlertDanger, fontSize = 12.sp) }
                    doc["quality_assessment_notes"]?.toString()?.takeIf { it.isNotBlank() }?.let { Text(it, color = DriverColors.TextMuted, fontSize = 12.sp) }
                }
                var expanded by remember { mutableStateOf(false) }
                Box { OutlinedButton(onClick = { expanded = true }) { Text("DOCUMENT: $documentType", color = DriverColors.ChauffeurGold) }; DropdownMenu(expanded, { expanded = false }) { listOf("DRIVING_LICENSE", "VEHICLE_REGISTRATION", "INSURANCE", "IDENTITY_PROOF").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { documentType = it; expanded = false }) } } }
                GoldAction("GUIDED PHOTO CAPTURE", busy) { guideVisible = true }
                capturedImage?.let { bitmap ->
                    androidx.compose.foundation.Image(bitmap.asImageBitmap(), null, Modifier.fillMaxWidth().height(130.dp))
                    Text("Capture ${bitmap.width} × ${bitmap.height} px. Minimum preview size: 160 × 120. Automated OCR/blur analysis is not configured; a reviewer will check legibility.", color = DriverColors.TextMuted, fontSize = 12.sp)
                    GoldAction("SUBMIT DOCUMENT", busy, enabled = bitmap.width >= 160 && bitmap.height >= 120) {
                        val bytes = ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out); out.toByteArray() }
                        busy = true; scope.launch {
                            try { val api = DriverApiClient.getService(); val response = api.resubmitOnboardingDocument(documentType.toRequestBody("text/plain".toMediaType()), "true".toRequestBody("text/plain".toMediaType()), MultipartBody.Part.createFormData("file", "${documentType.lowercase()}_capture.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))); error = !response.isSuccessful; message = response.body()?.get("message")?.toString() ?: if (error) "Document upload failed (${response.code()})." else "Document submitted for review."; if (!error) { capturedImage = null; refresh() } } catch (e: Exception) { error = true; message = e.localizedMessage ?: "Document upload failed." } finally { busy = false }
                        }
                    }
                }
            } }
            item { OnboardingCard("TRAINING & KNOWLEDGE", "${lessons.count { it["assessment_passed"] == true }} of ${lessons.size} lessons passed") {
                lessons.forEach { lesson ->
                    Text(lesson["title"]?.toString() ?: "Lesson", color = DriverColors.ChauffeurGold, fontWeight = FontWeight.SemiBold)
                    Text(lesson["description"]?.toString() ?: "", color = DriverColors.TextMuted)
                    Text(lesson["content_markdown"]?.toString() ?: "", color = DriverColors.TextPure, fontSize = 12.sp, maxLines = 5)
                    Text("${lesson["duration_minutes"] ?: ""} min • ${if (lesson["assessment_passed"] == true) "Passed" else "Assessment required"}", color = DriverColors.TextMuted, fontSize = 12.sp)
                    val questions = lesson["questions"] as? List<*> ?: emptyList<Any>()
                    val answers = remember(lesson["module_id"]) { mutableStateListOf<Int>().apply { repeat(questions.size) { add(-1) } } }
                    questions.forEachIndexed { index, raw ->
                        val q = raw as? Map<*, *> ?: return@forEachIndexed
                        Text(q["question"]?.toString() ?: "", color = DriverColors.TextPure)
                        (q["options"] as? List<*>)?.forEachIndexed { optionIndex, text -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(answers.getOrNull(index) == optionIndex, { answers[index] = optionIndex }, colors = RadioButtonDefaults.colors(selectedColor = DriverColors.ChauffeurGold)); Text(text.toString(), color = DriverColors.TextMuted) } }
                    }
                    if (questions.isNotEmpty() && lesson["assessment_passed"] != true) GoldAction("SUBMIT KNOWLEDGE ASSESSMENT", busy, enabled = answers.all { it >= 0 }) { action { submitAssessment(KnowledgeAssessmentPayload(lesson["module_id"].toString(), answers.toList())) } }
                    HorizontalDivider(color = DriverColors.SurfaceBorder, modifier = Modifier.padding(vertical = 8.dp))
                }
            } }
            item { OnboardingCard("VEHICLES & INSPECTION", "${vehicles.size} assigned vehicle(s)") {
                if (vehicles.isEmpty()) Text("Your fleet has not assigned a vehicle yet. Contact onboarding support to attach one before inspection.", color = DriverColors.TextMuted)
                vehicles.forEach { vehicle -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text(vehicle["model"]?.toString() ?: "Vehicle", color = DriverColors.TextPure); Text(vehicle["license_plate"]?.toString() ?: "", color = DriverColors.TextMuted) }; if (vehicle["is_active_vehicle"] == true) GoldBadge("ACTIVE") else TextButton(onClick = { val id = vehicle["vehicle_id"].toString(); action { activateOnboardingVehicle(id) } }) { Text("SET ACTIVE", color = DriverColors.ChauffeurGold) } } }
                Text("20 inspection items and six original vehicle angles are required. Upload is available after a fleet-assigned vehicle and secure photo storage are configured.", color = DriverColors.TextMuted, fontSize = 12.sp)
                GoldAction("LOAD INSPECTION CHECKLIST", busy) { scope.launch { try { val r = DriverApiClient.getService().getInspectionTemplate(); if (!r.isSuccessful) throw IllegalStateException("Inspection template unavailable (${r.code()})."); val body = r.body() ?: emptyMap(); inspectionItems = (body["checklist_items"] as? List<*>)?.filterIsInstance<Map<String, Any>>() ?: emptyList(); inspectionPhotos = (body["required_photos"] as? List<*>)?.filterIsInstance<Map<String, Any>>() ?: emptyList(); message = "Inspection template loaded: ${inspectionItems.size} checks and ${inspectionPhotos.size} photo angles."; error = false } catch (e: Exception) { error = true; message = e.localizedMessage ?: "Inspection template unavailable." } } }
                if (inspectionItems.isNotEmpty()) {
                    val vehicleId = vehicles.firstOrNull()?.get("vehicle_id")?.toString()
                    inspectionPhotos.forEach { spec ->
                        val key = spec["key"]?.toString() ?: return@forEach
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${spec["label"] ?: key}\n${if (inspectionPhotoRefs.containsKey(key)) "Photo uploaded privately" else "Required photo"}", Modifier.weight(1f), color = DriverColors.TextPure, fontSize = 12.sp)
                            TextButton(enabled = vehicleId != null && !busy, onClick = { pendingPhotoKey = key; guideVisible = true }) { Text("CAPTURE", color = DriverColors.ChauffeurGold) }
                        }
                    }
                    inspectionItems.forEach { item ->
                        val code = item["code"]?.toString() ?: return@forEach
                        var expanded by remember(code) { mutableStateOf(false) }
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("${item["label"]}: ${inspectionResults[code] ?: "SELECT RESULT"}", color = DriverColors.TextPure) }
                            DropdownMenu(expanded, { expanded = false }) { listOf("PASS", "FAIL", "NOT_APPLICABLE").forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { inspectionResults = inspectionResults + (code to value); expanded = false }) } }
                        }
                    }
                    GoldAction("SUBMIT VEHICLE INSPECTION", busy, enabled = vehicleId != null && inspectionResults.keys.containsAll(inspectionItems.mapNotNull { it["code"]?.toString() }) && inspectionPhotoRefs.keys.containsAll(inspectionPhotos.mapNotNull { it["key"]?.toString() })) {
                        action { submitVehicleInspection(VehicleInspectionPayload(vehicleId!!, inspectionResults, inspectionPhotoRefs, "Driver submitted inspection for fleet review.")) }
                    }
                }
            } }
            item { OnboardingCard("AVAILABILITY & PREFERENCES", "Set a schedule that suits your service") {
                if (schedule.isNotEmpty()) {
                    if (schedule.first()["id"] == null) Text("Suggested hours are not saved until you submit them.", color = DriverColors.TextMuted, fontSize = 12.sp)
                    schedule.forEachIndexed { index, day ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = day["is_active"] == true, onCheckedChange = { checked -> schedule = schedule.mapIndexed { i, current -> if (i == index) current + ("is_active" to checked) + ("is_rest_day" to !checked) else current } }, colors = CheckboxDefaults.colors(checkedColor = DriverColors.ChauffeurGold))
                            Text(day["day_name"]?.toString() ?: "Day", Modifier.width(82.dp), color = DriverColors.TextPure)
                            OutlinedTextField(day["start_time"]?.toString() ?: "08:00", { value -> schedule = schedule.mapIndexed { i, current -> if (i == index) current + ("start_time" to value.take(5)) else current } }, label = { Text("From") }, modifier = Modifier.weight(1f), singleLine = true)
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(day["end_time"]?.toString() ?: "20:00", { value -> schedule = schedule.mapIndexed { i, current -> if (i == index) current + ("end_time" to value.take(5)) else current } }, label = { Text("To") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                    }
                    GoldAction("SAVE WEEKLY AVAILABILITY", busy) { action { updateSchedule(SchedulePayload(schedule)) } }
                }
                OutlinedTextField(languages, { languages = it.take(128) }, label = { Text("Languages spoken") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(etiquette, { etiquette = it.take(64) }, label = { Text("Service style") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(music, { music = it.take(64) }, label = { Text("Cabin music preference") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Cabin temperature  $temperature°C", color = DriverColors.TextPure); Slider(temperature.toFloat(), { temperature = it.toInt() }, valueRange = 16f..28f, modifier = Modifier.weight(1f)) }
                GoldAction("SAVE SERVICE PREFERENCES", busy) { action { updateServicePreferences(ServicePreferencesPayload(languages, etiquette, music, temperature)) } }
                OutlinedTextField(contactName, { contactName = it.take(128) }, label = { Text("Emergency contact name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(contactPhone, { contactPhone = it.filter { c -> c.isDigit() || c == '+' }.take(16) }, label = { Text("Emergency contact phone") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(contactRelationship, { contactRelationship = it.take(64) }, label = { Text("Relationship") }, modifier = Modifier.fillMaxWidth())
                GoldAction("SAVE EMERGENCY CONTACT", busy, enabled = contactName.isNotBlank() && contactPhone.length >= 8 && contactRelationship.isNotBlank()) { action { updateEmergencyContact(EmergencyContactPayload(contactName, contactPhone, contactRelationship)) } }
                OutlinedTextField(zones, { zones = it.take(255) }, label = { Text("Preferred operating zones (comma separated)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(categories, { categories = it.take(255) }, label = { Text("Preferred vehicle categories (comma separated)") }, modifier = Modifier.fillMaxWidth())
                GoldAction("SAVE PREFERRED ZONES & VEHICLES", busy, enabled = zones.isNotBlank() && categories.isNotBlank()) { action { updateZones(ZonesPayload(zones.split(",").map(String::trim).filter(String::isNotBlank), categories.split(",").map(String::trim).filter(String::isNotBlank))) } }
            } }
            item { OnboardingCard("PUBLIC PROFILE PREVIEW & CONSENT", "Preview only; visible after approval") {
                Text("${preview["chauffeur_name"] ?: "Your name"} • ${preview["academy_level"] ?: "Training in progress"}", color = DriverColors.TextPure)
                Text("${preview["languages_spoken"] ?: "Languages not set"}", color = DriverColors.TextMuted)
                Text("Please review the policies in your application materials before acknowledging:", color = DriverColors.TextMuted, fontSize = 12.sp)
                listOf("Zero-Tolerance Alcohol & Substance Policy", "VIP Non-Disclosure & Privacy Agreement", "High-Value Fleet Care & Safe Driving Protocol", "Customer Harassment Zero-Tolerance Standard").forEach { Text("• $it", color = DriverColors.TextPure, fontSize = 12.sp) }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(consentChecked, { consentChecked = it }, colors = CheckboxDefaults.colors(checkedColor = DriverColors.ChauffeurGold)); Text("I confirm I reviewed these policy titles and acknowledge the current policies.", color = DriverColors.TextPure, fontSize = 12.sp) }
                GoldAction("ACKNOWLEDGE CURRENT POLICIES", busy, enabled = consentChecked) { action { acknowledgePolicies() } }
            } }
            item { OnboardingCard("SUPPORT & ACCOUNT", "Your requests are linked to this driver account") {
                OutlinedTextField(ticketSubject, { ticketSubject = it.take(120) }, label = { Text("Support request subject") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(ticketMessage, { ticketMessage = it.take(2000) }, label = { Text("How can onboarding support help?") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                GoldAction("SEND SUPPORT REQUEST", busy, enabled = ticketSubject.isNotBlank() && ticketMessage.length >= 10) { action { createOnboardingTicket(OnboardingTicketPayload(ticketSubject, ticketMessage)) } }
                tickets.forEach { Text("${it["status"]}: ${it["subject"]}", color = DriverColors.TextPure) }
                Text("Account recovery uses the configured sign-in OTP flow. Recovery cannot be completed until the OTP provider is available.", color = DriverColors.TextMuted, fontSize = 12.sp)
                var showDelete by remember { mutableStateOf(false) }
                TextButton(onClick = { showDelete = true }) { Text("REQUEST ACCOUNT DELETION", color = DriverColors.AlertDanger) }
                if (showDelete) AlertDialog(onDismissRequest = { showDelete = false }, title = { Text("Request account deletion?") }, text = { Text("This starts the account deletion review and grace period. Pending payouts or legal retention requirements may delay removal.") }, confirmButton = { TextButton(onClick = { showDelete = false; action { requestAccountDeletion() } }) { Text("SUBMIT REQUEST") } }, dismissButton = { TextButton(onClick = { showDelete = false }) { Text("CANCEL") } })
            } }
        }
    }
    LaunchedEffect(Unit) { refresh() }
}

@Composable private fun OnboardingCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = DriverColors.SurfaceCard), border = BorderStroke(1.dp, DriverColors.SurfaceBorder)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Text(title, color = DriverColors.ChauffeurGold, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(subtitle, color = DriverColors.TextMuted, fontSize = 12.sp); content() }
    }
}
@Composable private fun GoldAction(label: String, busy: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 46.dp), enabled = enabled && !busy, colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold, contentColor = DriverColors.MidnightBlack, disabledContainerColor = DriverColors.SurfaceBorder)) { Text(if (busy) "SAVING…" else label, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
}
