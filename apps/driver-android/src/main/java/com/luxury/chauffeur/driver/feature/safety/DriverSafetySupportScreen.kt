package com.luxury.chauffeur.driver.feature.safety

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luxury.chauffeur.driver.core.designsystem.*
import com.luxury.chauffeur.driver.core.network.DriverApiClient
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverSafetySupportScreen(onBack: () -> Unit, rideId: String? = null, pickupAddress: String? = null, destinationAddress: String? = null, onNavigateTraining: () -> Unit = {}) {
    val scope = rememberCoroutineScope(); val context = LocalContext.current
    var data by remember { mutableStateOf<Map<String, Any>?>(null) }
    var articles by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var lessons by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }; var dialog by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }; var selectedRating by remember { mutableStateOf("") }
    var shareLocation by remember { mutableStateOf(false) }
    var lastIncidentTicket by remember { mutableStateOf<String?>(null) }
    var incidentCategory by remember { mutableStateOf("UNSAFE_PICKUP_LOCATION") }
    var privacyType by remember { mutableStateOf("DATA_PORTABILITY") }
    val evidencePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val ticket = lastIncidentTicket ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("Unable to read selected file")
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val part = MultipartBody.Part.createFormData("file", "incident-evidence", bytes.toRequestBody(mime.toMediaTypeOrNull()))
            val response = DriverApiClient.getService().uploadIncidentEvidence(ticket, part)
            message = if (response.isSuccessful) "Evidence securely attached to $ticket." else "Evidence could not be uploaded (${response.code()})."
        } catch (_: Exception) { message = "Evidence could not be uploaded. Choose a PDF or image and retry." } }
    }
    suspend fun refresh() {
        loading = true
        try { val r = DriverApiClient.getService().getSafetyOverview(); if (r.isSuccessful) { data = r.body(); error = null; shareLocation = (((r.body()?.get("safety_preferences") as? Map<*, *>)?.get("share_trip_location_with_customer") as? Boolean) ?: false) } else error = "Safety dashboard unavailable (${r.code()})." }
        catch (_: Exception) { error = "Could not load safety information. Check your connection." }
        try { val a = DriverApiClient.getService().getDriverHelpArticles(); if (a.isSuccessful) articles = a.body().orEmpty() } catch (_: Exception) { }
        try { val l = DriverApiClient.getService().getTrainingCurriculum(); if (l.isSuccessful) lessons = l.body().orEmpty() } catch (_: Exception) { }
        loading = false
    }
    LaunchedEffect(Unit) { refresh() }
    Scaffold(containerColor = DriverColors.MidnightBlack, topBar = { CenterAlignedTopAppBar(title = { Text("SAFETY & SUPPORT", color = DriverColors.ChauffeurGold) }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }, colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DriverColors.MidnightBlack)) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DriverColors.ChauffeurGold)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error); OutlinedButton(onClick = { scope.launch { refresh() } }) { Text("Retry") } }
            message?.let { Text(it, color = DriverColors.ChauffeurGold) }
            SafetyCard("Emergency help", "For immediate danger call 112. In-app reports do not dispatch emergency services.") {
                Button(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) }, colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold)) { Text("Call 112") }
                if (!rideId.isNullOrBlank()) OutlinedButton(onClick = { scope.launch { try { val r = DriverApiClient.getService().sendDriverEmergencyAlert(mapOf("ride_id" to rideId, "notes" to "Driver emergency alert")); message = if (r.isSuccessful) r.body()?.get("message")?.toString() ?: "Safety alert recorded." else "Alert could not be recorded (${r.code()}). Call 112 directly if needed." } catch (_: Exception) { message = "Alert could not be sent. Call 112 directly if needed." } } }) { Text("Send in-app safety alert") }
                Text("Emergency services: 112", color = DriverColors.TextPure, fontWeight = FontWeight.SemiBold)
                if (!rideId.isNullOrBlank()) {
                    Text("Trip reference: $rideId", color = DriverColors.TextMuted)
                    pickupAddress?.takeIf { it.isNotBlank() }?.let { Text("Pickup: $it", color = DriverColors.TextMuted) }
                    destinationAddress?.takeIf { it.isNotBlank() }?.let { Text("Destination: $it", color = DriverColors.TextMuted) }
                }
                Text("These trip details remain available from the open trip screen while it is on this device. When offline, call 112 directly; the app cannot send alerts until service returns.", color = DriverColors.TextMuted)
            }
            if (!rideId.isNullOrBlank()) SafetyCard("Trip safety check-in", "Set a reminder for yourself and record your check-in. This is not monitored as an emergency dispatch.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { try { val r = DriverApiClient.getService().scheduleDriverSafetyCheckin(mapOf("ride_id" to rideId, "minutes_from_now" to 30)); message = if (r.isSuccessful) "Safety check-in reminder scheduled." else "Could not schedule check-in (${r.code()})." } catch (_: Exception) { message = "Could not schedule check-in. Check your connection." } } }) { Text("Remind me in 30 min") }
                    OutlinedButton(onClick = { scope.launch { try { val r = DriverApiClient.getService().respondDriverSafetyCheckin(mapOf("ride_id" to rideId, "response_type" to "ALL_GOOD")); message = if (r.isSuccessful) "Safety check-in recorded." else "Check-in could not be recorded." } catch (_: Exception) { message = "Check-in could not be recorded. Check your connection." } } }) { Text("I’m safe") }
                }
                OutlinedButton(onClick = { scope.launch { try { val r = DriverApiClient.getService().respondDriverSafetyCheckin(mapOf("ride_id" to rideId, "response_type" to "NEED_HELP")); message = if (r.isSuccessful) "Safety alert recorded. Call 112 now if anyone is in immediate danger." else "Could not record alert. Call 112 directly." } catch (_: Exception) { message = "Could not record alert. Call 112 directly." } } }) { Text("I need help") }
            }
            SafetyCard("Safety check-in", "Take a moment to assess whether you are safe and fit to drive.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("FIT", "NEEDS_REST", "UNFIT").forEach { state -> OutlinedButton(onClick = { scope.launch { try { val r=DriverApiClient.getService().submitFatigueCheck(mapOf("result" to state,"ride_id" to rideId)); if(r.isSuccessful && state!="FIT") DriverApiClient.getService().setAvailability(com.luxury.chauffeur.driver.core.network.DriverAvailabilityPayload(false)); message=if(r.isSuccessful) "Check-in recorded. ${r.body()?.get("guidance")}" else "Check-in could not be saved." } catch (_:Exception) { message="Check-in could not be saved." } } }) { Text(state.replace('_',' ')) } } }
            }
            SafetyCard("Trip location privacy", "Location sharing is optional and applies to the active trip only. Review the privacy notice before changing this setting.") {
                Row { Switch(shareLocation, { shareLocation = it }); Text("Share trip location with passenger", Modifier.padding(start=8.dp), color=DriverColors.TextPure) }
                Button(onClick = { scope.launch { try { val r=DriverApiClient.getService().updateSafetyPreferences(mapOf("share_trip_location_with_customer" to shareLocation,"privacy_notice_acknowledged" to true)); message=if(r.isSuccessful) "Privacy preference saved." else "Preference could not be saved (${r.code()})." } catch (_:Exception) { message="Preference could not be saved." } } }, colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold)) { Text("Save privacy choice") }
            }
            SafetyCard("Performance & coaching", "Rating trend and category feedback from recorded trips.") {
                Text("Average: ${data?.get("rating_average") ?: "No ratings yet"}  •  ${data?.get("rating_count") ?: 0} ratings", color=DriverColors.TextPure)
                ((data?.get("ratings") as? List<*>)?.filterIsInstance<Map<*, *>>()?.take(5) ?: emptyList()).forEach { rating ->
                    Text("${rating["created_at"]?.toString()?.take(10) ?: ""}  •  ${rating["rating"]}/5   Cleanliness ${rating["cleanliness"]} · Comfort ${rating["comfort"]} · Service ${rating["service_quality"]} · Navigation ${rating["navigation"]}", color=DriverColors.TextMuted)
                    TextButton(onClick={ selectedRating=rating["id"].toString(); dialog="appeal" }) { Text("Appeal this rating") }
                }
                (data?.get("rating_appeals") as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { appeal ->
                    Text("Appeal status · ${appeal["status"]}", color=DriverColors.TextPure)
                }
                if ((data?.get("ratings") as? List<*>)?.isEmpty() == true) Text("Ratings will appear here after completed rides receive feedback.", color=DriverColors.TextMuted)
                (data?.get("coaching") as? List<*>)?.forEach { Text("• $it", color=DriverColors.ChauffeurGold) }
            }
            SafetyCard("Vehicle readiness & conditions", "Certificate reminders use recorded expiry dates. Pre-trip checks are shown from your latest inspection record; formal fleet intervals and live weather alerts are not configured.") {
                val maintenance = (data?.get("maintenance") as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
                if (maintenance.isEmpty()) Text("No active assigned vehicles are available for expiry reminders.", color=DriverColors.TextMuted)
                maintenance.forEach { v -> Text("${v["license_plate"]}: PUC expires ${v["puc_expiry_date"] ?: "not recorded"}; fitness expires ${v["fitness_expiry_date"] ?: "not recorded"}", color=DriverColors.TextPure) }
                val inspections = (data?.get("inspections") as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
                inspections.forEach { inspection ->
                    Text("${inspection["license_plate"]}: last inspection ${inspection["last_submitted_at"]?.toString()?.take(10) ?: "not submitted"} · ${inspection["last_status"] ?: "not submitted"}", color=DriverColors.TextPure)
                    if (inspection["pre_trip_check_due"] == true) Text("Pre-trip vehicle inspection is due before your next shift.", color=DriverColors.ChauffeurGold)
                }
                if (inspections.isEmpty()) Text("No active assigned vehicle is available for an inspection reminder.", color=DriverColors.TextMuted)
                Text("Weather: ${((data?.get("weather") as? Map<*, *>)?.get("status") ?: "NOT_CONFIGURED")}", color=DriverColors.TextMuted)
                Text("Check official weather and road authority alerts before departing.", color=DriverColors.TextMuted)
            }
            SafetyCard("Driver support center", "Create a case or request a callback. Callback contact is not confirmed until a support agent responds.") {
                OutlinedButton(onClick={ dialog="ticket" }) { Text("Open support ticket") }; OutlinedButton(onClick={ dialog="callback" }) { Text("Request callback") }
                (data?.get("tickets") as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { Text("${it["ticket_number"]} · ${it["subject"]} · ${it["status"]}", color=DriverColors.TextPure) }
                if ((data?.get("tickets") as? List<*>)?.isEmpty() == true) Text("No support tickets yet.", color=DriverColors.TextMuted)
                articles.forEach { Text("${it["title"]}", color=DriverColors.ChauffeurGold); Text(it["content"]?.toString()?.take(300).orEmpty(), color=DriverColors.TextMuted) }
                if (articles.isEmpty()) Text("Driver-specific help articles are not published yet.", color=DriverColors.TextMuted)
            }
            SafetyCard("Defensive driving lesson", "Short safety learning module from the driver curriculum.") {
                val lesson = lessons.firstOrNull { it["module_code"] == "DEFENSIVE_DRIVING_SAFETY" }
                if (lesson == null) Text("The defensive driving lesson is not available yet. Retry when connected.", color=DriverColors.TextMuted)
                else { Text(lesson["title"]?.toString().orEmpty(), color=DriverColors.TextPure, fontWeight=FontWeight.SemiBold); Text(lesson["content_markdown"]?.toString().orEmpty(), color=DriverColors.TextMuted); Text("${lesson["duration_minutes"] ?: ""} min · ${if (lesson["is_completed"] == true) "Completed" else "Assessment available"}", color=DriverColors.ChauffeurGold); if (lesson["is_completed"] != true) OutlinedButton(onClick = onNavigateTraining) { Text("Open training and assessment") } }
            }
            SafetyCard("Report an incident", "Report customer behavior, an unsafe pickup, or another trip issue. Evidence is stored privately and attached to your report.") {
                OutlinedButton(onClick={dialog="incident"}) { Text("Create incident report") }
                if (lastIncidentTicket != null) OutlinedButton(onClick={ evidencePicker.launch("*/*") }) { Text("Attach incident evidence to $lastIncidentTicket") }
                (data?.get("incident_cases") as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { report ->
                    Text("${report["ticket_number"]} · ${report["category"]?.toString()?.replace('_', ' ')} · ${report["status"]}", color = DriverColors.TextPure)
                    val outcome = report["resolution_notes"]?.toString()?.takeIf { it.isNotBlank() }
                    if (outcome != null) Text("Outcome: $outcome", color = DriverColors.ChauffeurGold)
                }
                (data?.get("safety_outcome_notifications") as? List<*>)?.filterIsInstance<Map<*, *>>()?.take(3)?.forEach { update -> Text("${update["title"]}: ${update["body"]}", color = DriverColors.TextMuted) }
            }
            SafetyCard("DPDP & GDPR Chauffeur Privacy Rights", "Exercise statutory data subject rights under India's Digital Personal Data Protection Act (DPDP) and GDPR. Request a full machine-readable portability export of your telemetry and trip history, or file a deletion/rectification request.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { privacyType = "DATA_PORTABILITY"; dialog = "privacy" }) { Text("Request Data Export") }
                    OutlinedButton(onClick = { privacyType = "ERASURE"; dialog = "privacy" }) { Text("Request Erasure") }
                }
                Text("Statutory Note: Tax vouchers and financial audit records have a mandatory 7-year statutory retention lock under Sec 44AB of the Income Tax Act.", color = DriverColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (dialog.isNotEmpty()) AlertDialog(onDismissRequest={dialog=""}, title={ Text(when(dialog){"appeal"->"Appeal rating";"ticket"->"Support ticket";"callback"->"Request callback";"privacy"->"DPDP / GDPR ${if(privacyType=="ERASURE") "Data Deletion" else "Portability Export"}";else->"Incident report"}) }, text={ Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(text,{text=it},label={Text(if(dialog=="appeal") "Reason (at least 20 characters)" else if(dialog=="privacy") "State your verification details and rationale" else "Details (at least 10 characters)")}, minLines=3)
        if(dialog=="incident") {
            Text("Report category", color=DriverColors.TextMuted)
            listOf("UNSAFE_PICKUP_LOCATION", "CUSTOMER_BEHAVIOR", "PASSENGER_NO_SHOW_DISPUTE", "OTHER").forEach { category ->
                Row { RadioButton(selected=incidentCategory==category, onClick={incidentCategory=category}); Text(category.replace('_',' '), Modifier.padding(top=12.dp), color=DriverColors.TextPure) }
            }
        }
    } }, confirmButton={ TextButton(onClick={
        val body=text.trim(); if(body.length < if(dialog=="appeal") 20 else 10) { message="Please provide more detail before submitting."; return@TextButton }
        if (dialog=="incident" && incidentCategory!="OTHER" && rideId==null) { message="${incidentCategory.replace('_',' ')} reports require an active ride. Open this screen from an active ride."; return@TextButton }
        val action=dialog; dialog=""; text=""
        scope.launch { try { val api=DriverApiClient.getService(); val response=when(action) { "appeal" -> api.appealRating(selectedRating,mapOf("reason" to body)); "callback" -> api.requestDriverCallback(mapOf("subject" to body,"preferred_time_window" to "WITHIN_1_HOUR")); "ticket" -> api.createDriverSupportTicket(mapOf("subject" to body.take(255),"message" to body,"category" to "SAFETY")); "privacy" -> api.submitPrivacyDsar(com.luxury.chauffeur.driver.core.network.DriverPrivacyDsarPayload(requestType = privacyType, reason = body)); else -> api.createDriverIncident(mapOf("ride_id" to rideId,"category" to incidentCategory,"description" to body,"severity" to "MEDIUM")) }; message=if(response.isSuccessful) response.body()?.get("message")?.toString() ?: "Request submitted." else "Request could not be submitted (${response.code()})."; if(action=="incident" && response.isSuccessful) lastIncidentTicket=response.body()?.get("ticket_number")?.toString(); refresh() } catch (_:Exception) { message="Request could not be submitted. Check your connection." } }
    }) { Text("Submit") } }, dismissButton={ TextButton(onClick={dialog=""}) { Text("Cancel") } })
}

@Composable private fun SafetyCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors=CardDefaults.cardColors(containerColor=DriverColors.SurfaceCard), shape=RoundedCornerShape(18.dp), modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) { Text(title, color=DriverColors.ChauffeurGold, fontWeight=FontWeight.SemiBold); Text(subtitle, color=DriverColors.TextMuted); content() }
    }
}
