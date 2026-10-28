package com.luxury.chauffeur.driver.feature.documents

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luxury.chauffeur.driver.core.designsystem.*
import com.luxury.chauffeur.driver.core.network.DriverApiClient
import com.luxury.chauffeur.driver.core.network.DriverDocumentDto
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Specification for mandatory KYC and vehicle verification documents.
 */
data class RequiredDocumentSpec(val type: String, val title: String, val description: String)

/**
 * Document & KYC verification screen for pilot onboarding.
 * Displays approval, pending, and rejection states with explicit rejection reasons.
 */
@Composable
fun DocumentUploadScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var serverDocuments by remember { mutableStateOf<List<DriverDocumentDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isUploading by remember { mutableStateOf(false) }
    var activeUploadType by remember { mutableStateOf<String?>(null) }
    var uploadStatusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessMessage by remember { mutableStateOf(true) }

    val requiredSpecs = remember {
        listOf(
            RequiredDocumentSpec("DRIVING_LICENSE", "Driving License (Commercial / Transport)", "Valid transport license or commercial badge"),
            RequiredDocumentSpec("VEHICLE_REGISTRATION", "Registration Certificate (RC)", "Valid commercial certificate of registration"),
            RequiredDocumentSpec("IDENTITY_PROOF", "Aadhaar Card / PAN Card", "Government verified identification"),
            RequiredDocumentSpec("VEHICLE_PHOTOS", "Vehicle Photos (Exterior & Interior)", "Clear photos of vehicle front, back, and passenger seating"),
            RequiredDocumentSpec("DRIVER_SELFIE", "Driver Selfie (Portrait Photo)", "Clear front-facing portrait photo in well-lit area")
        )
    }

    fun refreshDocuments() {
        isLoading = true
        scope.launch {
            try {
                val res = DriverApiClient.getService().getDocuments()
                if (res.isSuccessful && res.body() != null) {
                    serverDocuments = res.body()!!
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshDocuments()
    }

    // Overall KYC status calculation
    val allApproved = requiredSpecs.all { spec ->
        val doc = serverDocuments.firstOrNull { it.documentType.equals(spec.type, ignoreCase = true) }
        doc?.verificationStatus.equals("APPROVED", ignoreCase = true) || doc?.verificationStatus.equals("VERIFIED", ignoreCase = true)
    }
    val anyRejected = serverDocuments.any {
        it.verificationStatus.equals("REJECTED", ignoreCase = true) || it.verificationStatus.equals("FAILED", ignoreCase = true)
    }

    // System File Picker for PDF / Images
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val docType = activeUploadType ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult

        isUploading = true
        uploadStatusMessage = "Uploading document securely..."
        isSuccessMessage = true

        scope.launch {
            try {
                var fileName = "compliance_document.jpg"
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) fileName = it.getString(nameIndex)
                    }
                }

                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    isSuccessMessage = false
                    uploadStatusMessage = "Failed to read file from storage."
                    isUploading = false
                    return@launch
                }

                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val requestFile = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", fileName, requestFile)
                val typePart = docType.toRequestBody("text/plain".toMediaTypeOrNull())

                val res = DriverApiClient.getService().uploadDocument(typePart, filePart)
                if (res.isSuccessful) {
                    isSuccessMessage = true
                    uploadStatusMessage = "Document uploaded successfully! Compliance verification in progress."
                    refreshDocuments()
                } else {
                    isSuccessMessage = false
                    uploadStatusMessage = "Upload failed: Server returned ${res.code()}"
                }
            } catch (e: Exception) {
                isSuccessMessage = false
                uploadStatusMessage = "Error during upload: ${e.localizedMessage}"
            } finally {
                isUploading = false
            }
        }
    }

    Scaffold(
        containerColor = DriverColors.MidnightBlack,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("DOCUMENT COMPLIANCE & KYC", style = DriverTypography.Title.copy(fontSize = 18.sp, color = DriverColors.ChauffeurGold))
                GoldBadge(
                    when {
                        allApproved -> "KYC APPROVED"
                        anyRejected -> "ACTION REQUIRED"
                        else -> "UNDER REVIEW"
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // KYC status banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        allApproved -> DriverColors.StatusOnline.copy(alpha = 0.15f)
                        anyRejected -> DriverColors.AlertDanger.copy(alpha = 0.15f)
                        else -> DriverColors.StatusBusy.copy(alpha = 0.15f)
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            allApproved -> DriverColors.StatusOnline
                            anyRejected -> DriverColors.AlertDanger
                            else -> DriverColors.StatusBusy
                        }
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = when {
                                allApproved -> "✓ All Documents Verified & Approved"
                                anyRejected -> "⚠️ Document Rejection Detected"
                                else -> "⏳ Verification Pending Approval"
                            },
                            style = DriverTypography.Subtitle.copy(
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    allApproved -> DriverColors.StatusOnline
                                    anyRejected -> DriverColors.AlertDanger
                                    else -> DriverColors.StatusBusy
                                }
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                allApproved -> "You are fully certified. You may toggle online in the Cockpit to receive ride dispatches."
                                anyRejected -> "One or more documents were rejected. Please review the reasons below and re-upload clear photos."
                                else -> "Our compliance team is reviewing your documents. Going online is blocked until all documents are Approved."
                            },
                            style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextPure)
                        )
                    }
                }

                if (uploadStatusMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSuccessMessage) DriverColors.StatusOnline.copy(alpha = 0.15f) else DriverColors.AlertDanger.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (isSuccessMessage) DriverColors.StatusOnline else DriverColors.AlertDanger),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = uploadStatusMessage!!,
                            color = if (isSuccessMessage) DriverColors.StatusOnline else DriverColors.AlertDanger,
                            style = DriverTypography.Body.copy(fontSize = 12.sp),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DriverColors.ChauffeurGold)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(requiredSpecs) { spec ->
                            val existingDoc = serverDocuments.firstOrNull { it.documentType.equals(spec.type, ignoreCase = true) }
                            val status = existingDoc?.verificationStatus ?: "NOT_UPLOADED"
                            val isVerified = status.equals("APPROVED", ignoreCase = true) || status.equals("VERIFIED", ignoreCase = true)
                            val isPending = status.equals("PENDING", ignoreCase = true) || status.equals("UNDER_REVIEW", ignoreCase = true)
                            val isRejected = status.equals("REJECTED", ignoreCase = true) || status.equals("FAILED", ignoreCase = true)
                            val rejectionReason = existingDoc?.rejectionReason

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DriverColors.SurfaceCard,
                                border = BorderStroke(
                                    1.dp,
                                    if (isRejected) DriverColors.AlertDanger else DriverColors.SurfaceBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(spec.title, style = DriverTypography.Body.copy(fontWeight = FontWeight.SemiBold))
                                            Text(
                                                text = if (existingDoc != null) "File: ${existingDoc.fileName}" else spec.description,
                                                style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted)
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = when {
                                                isVerified -> DriverColors.StatusOnline.copy(alpha = 0.2f)
                                                isPending -> DriverColors.StatusBusy.copy(alpha = 0.2f)
                                                isRejected -> DriverColors.AlertDanger.copy(alpha = 0.2f)
                                                else -> DriverColors.SurfaceBorder
                                            }
                                        ) {
                                            Text(
                                                text = status.uppercase(),
                                                color = when {
                                                    isVerified -> DriverColors.StatusOnline
                                                    isPending -> DriverColors.StatusBusy
                                                    isRejected -> DriverColors.AlertDanger
                                                    else -> DriverColors.TextMuted
                                                },
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    // Explicit Rejection Reason Notice
                                    if (isRejected && !rejectionReason.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = DriverColors.AlertDanger.copy(alpha = 0.12f),
                                            border = BorderStroke(0.5.dp, DriverColors.AlertDanger),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Rejection Reason: $rejectionReason",
                                                color = DriverColors.AlertDanger,
                                                style = DriverTypography.Body.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Upload / Replace Action Button
                                    Button(
                                        onClick = {
                                            if (!isUploading) {
                                                activeUploadType = spec.type
                                                filePickerLauncher.launch("*/*")
                                            }
                                        },
                                        enabled = !isUploading,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isRejected) DriverColors.AlertDanger else if (existingDoc != null) DriverColors.CockpitDark else DriverColors.ChauffeurGold,
                                            contentColor = if (isRejected) DriverColors.TextPure else if (existingDoc != null) DriverColors.ChauffeurGold else DriverColors.MidnightBlack
                                        ),
                                        modifier = Modifier.fillMaxWidth().height(36.dp)
                                    ) {
                                        Text(
                                            text = if (isUploading && activeUploadType == spec.type) "UPLOADING..."
                                            else if (isRejected) "RE-UPLOAD REJECTED DOCUMENT"
                                            else if (existingDoc != null) "REPLACE / RE-UPLOAD DOCUMENT"
                                            else "CHOOSE FILE & UPLOAD",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onNavigateBack,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold, contentColor = DriverColors.MidnightBlack),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("BACK TO COCKPIT")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DocumentUploadScreenPreview() {
    DocumentUploadScreen(onNavigateBack = {})
}
