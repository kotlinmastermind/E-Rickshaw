package com.vaisentra.mobility.core.error

/**
 * Standardized client-side validation boundary.
 * Aligns strictly with backend schema contracts without replacing authoritative server validation.
 */
object DataValidator {

    private val PHONE_REGEX = Regex("""^(\+91[\-\s]?)?[6-9]\d{9}$""")
    private val VEHICLE_PLATE_REGEX = Regex("""^[A-Z]{2}[ -]?[0-9]{1,2}[ -]?[A-Z]{1,3}[ -]?[0-9]{4}$""")

    fun validatePhoneNumber(phone: String?): AppResult<String> {
        val clean = phone?.trim().orEmpty()
        if (clean.isBlank()) {
            return AppResult.Failure(
                AppError.ValidationError(
                    fieldName = "phone_number",
                    userMessage = "Mobile number is required."
                )
            )
        }
        val digitsOnly = clean.removePrefix("+91").replace(Regex("""[\s\-]"""), "")
        if (digitsOnly.length != 10 || !digitsOnly.all { it.isDigit() } || digitsOnly.first() !in '6'..'9') {
            return AppResult.Failure(
                AppError.ValidationError(
                    fieldName = "phone_number",
                    userMessage = "Please enter a valid 10-digit mobile number starting with 6, 7, 8, or 9."
                )
            )
        }
        return AppResult.Success("+91$digitsOnly")
    }

    fun validateCoordinates(latitude: Double?, longitude: Double?): AppResult<Pair<Double, Double>> {
        if (latitude == null || longitude == null || latitude.isNaN() || longitude.isNaN() || latitude.isInfinite() || longitude.isInfinite()) {
            return AppResult.Failure(
                AppError.ValidationError(
                    fieldName = "coordinates",
                    userMessage = "Location coordinates are invalid."
                )
            )
        }
        if (latitude < -90.0 || latitude > 90.0) {
            return AppResult.Failure(
                AppError.ValidationError(
                    fieldName = "latitude",
                    userMessage = "Latitude must be between -90 and +90 degrees."
                )
            )
        }
        if (longitude < -180.0 || longitude > 180.0) {
            return AppResult.Failure(
                AppError.ValidationError(
                    fieldName = "longitude",
                    userMessage = "Longitude must be between -180 and +180 degrees."
                )
            )
        }
        return AppResult.Success(Pair(latitude, longitude))
    }

    fun validateVehicleRegistration(plate: String?): AppResult<String> {
        val clean = plate?.trim()?.uppercase().orEmpty()
        if (clean.isBlank()) {
            return AppResult.Failure(
                AppError.ValidationError(
                    fieldName = "license_plate",
                    userMessage = "Vehicle registration number is required."
                )
            )
        }
        if (!VEHICLE_PLATE_REGEX.matches(clean) && clean.length < 5) {
            return AppResult.Failure(
                AppError.ValidationError(
                    fieldName = "license_plate",
                    userMessage = "Please enter a valid vehicle registration number (e.g., DL-01-AB-1234)."
                )
            )
        }
        return AppResult.Success(clean)
    }

    fun validateFareAmount(amount: Double?): AppResult<Double> {
        if (amount == null || amount.isNaN() || amount.isInfinite() || amount <= 0.0) {
            return AppResult.Failure(
                AppError.ValidationError(
                    fieldName = "fare_amount",
                    userMessage = "Fare amount must be a positive number."
                )
            )
        }
        return AppResult.Success(amount)
    }

    fun validateRequiredField(fieldName: String, value: String?): AppResult<String> {
        val clean = value?.trim().orEmpty()
        if (clean.isBlank()) {
            return AppResult.Failure(
                AppError.ValidationError(
                    fieldName = fieldName,
                    userMessage = "This field cannot be empty."
                )
            )
        }
        return AppResult.Success(clean)
    }

    fun validateRideId(rideId: String?): AppResult<String> {
        val clean = rideId?.trim().orEmpty()
        if (clean.isBlank()) {
            return AppResult.Failure(
                AppError.ValidationError(
                    fieldName = "ride_id",
                    userMessage = "Active ride identifier is missing."
                )
            )
        }
        return AppResult.Success(clean)
    }
}

