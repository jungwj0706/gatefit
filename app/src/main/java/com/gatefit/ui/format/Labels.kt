package com.gatefit.ui.format

import androidx.annotation.StringRes
import com.gatefit.R
import com.gatefit.domain.model.AccessoryType
import com.gatefit.domain.model.CheckReason
import com.gatefit.domain.model.CompatibilityStatus
import com.gatefit.domain.model.ConstraintType
import com.gatefit.domain.model.DataFreshness
import com.gatefit.domain.model.DayType
import com.gatefit.domain.model.EntranceClass
import com.gatefit.domain.model.EntranceDirection
import com.gatefit.domain.model.EntranceStatus
import com.gatefit.domain.model.FacilityCategory
import com.gatefit.domain.model.HeightBasis
import com.gatefit.domain.model.HeightLimitStructure
import com.gatefit.domain.model.PermittedVehicleType
import com.gatefit.domain.model.ReportDelivery
import com.gatefit.domain.model.ReportType
import com.gatefit.domain.model.RouteBlockReason
import com.gatefit.domain.model.RouteFailure
import com.gatefit.domain.model.RouteTargetType
import com.gatefit.domain.model.SpecConfidence
import com.gatefit.domain.model.TrustLevel
import com.gatefit.domain.model.VehicleLinkFailure
import com.gatefit.domain.usecase.ProfileField
import com.gatefit.domain.usecase.ReportIssue

@get:StringRes
val CompatibilityStatus.labelRes: Int
    get() = when (this) {
        CompatibilityStatus.CONDITION_MATCH -> R.string.status_match
        CompatibilityStatus.NEEDS_CONFIRMATION -> R.string.status_needs_confirmation
        CompatibilityStatus.CONDITION_MISMATCH -> R.string.status_mismatch
    }

@get:StringRes
val FacilityCategory.labelRes: Int
    get() = when (this) {
        FacilityCategory.HOSPITAL -> R.string.category_hospital
        FacilityCategory.SHOPPING -> R.string.category_shopping
        FacilityCategory.HOTEL -> R.string.category_hotel
        FacilityCategory.PUBLIC_OFFICE -> R.string.category_public_office
        FacilityCategory.CULTURE -> R.string.category_culture
        FacilityCategory.PARKING -> R.string.category_parking
        FacilityCategory.PARK -> R.string.category_park
        FacilityCategory.OTHER -> R.string.category_other
    }

@get:StringRes
val TrustLevel.labelRes: Int
    get() = when (this) {
        TrustLevel.VERIFIED_OFFICIAL -> R.string.trust_verified_official
        TrustLevel.VERIFIED_FIELD -> R.string.trust_verified_field
        TrustLevel.PUBLIC_BASELINE -> R.string.trust_public_baseline
        TrustLevel.COMMUNITY_REPORTED -> R.string.trust_community
        TrustLevel.UNKNOWN -> R.string.trust_unknown
    }

@get:StringRes
val DataFreshness.labelRes: Int
    get() = when (this) {
        DataFreshness.FRESH -> R.string.freshness_fresh
        DataFreshness.NEEDS_RECHECK -> R.string.freshness_needs_recheck
        DataFreshness.STALE_BASELINE -> R.string.freshness_stale_baseline
        DataFreshness.UNTRUSTED -> R.string.freshness_untrusted
    }

@get:StringRes
val ConstraintType.labelRes: Int
    get() = when (this) {
        ConstraintType.HEIGHT -> R.string.constraint_height
        ConstraintType.WIDTH -> R.string.constraint_width
        ConstraintType.LENGTH -> R.string.constraint_length
        ConstraintType.WEIGHT -> R.string.constraint_weight
        ConstraintType.DIRECTION -> R.string.constraint_direction
        ConstraintType.OPERATING_HOURS -> R.string.constraint_hours
        ConstraintType.VEHICLE_TYPE -> R.string.constraint_vehicle_type
        ConstraintType.ENTRANCE_STATUS -> R.string.constraint_entrance_status
        ConstraintType.DATA_TRUST -> R.string.constraint_data_trust
    }

@get:StringRes
val CheckReason.labelRes: Int
    get() = when (this) {
        CheckReason.WITHIN_LIMIT -> R.string.reason_within_limit
        CheckReason.EXCEEDS_LIMIT -> R.string.reason_exceeds_limit
        CheckReason.MARGIN_INSUFFICIENT -> R.string.reason_margin_insufficient
        CheckReason.VEHICLE_VALUE_MISSING -> R.string.reason_vehicle_value_missing
        CheckReason.VEHICLE_VALUE_UNCONFIRMED -> R.string.reason_vehicle_value_unconfirmed
        CheckReason.LIMIT_NOT_PROVIDED -> R.string.reason_limit_not_provided
        CheckReason.HEIGHT_STRUCTURE_NOT_INSTALLED -> R.string.reason_height_structure_not_installed
        CheckReason.DIRECTION_ENTRY -> R.string.reason_direction_entry
        CheckReason.DIRECTION_BIDIRECTIONAL -> R.string.reason_direction_bidirectional
        CheckReason.DIRECTION_EXIT_ONLY -> R.string.reason_direction_exit_only
        CheckReason.DIRECTION_UNKNOWN -> R.string.reason_direction_unknown
        CheckReason.HOURS_OPEN -> R.string.reason_hours_open
        CheckReason.HOURS_ALL_DAY -> R.string.reason_hours_all_day
        CheckReason.HOURS_OUTSIDE -> R.string.reason_hours_outside
        CheckReason.HOURS_CLOSED_DAY -> R.string.reason_hours_closed_day
        CheckReason.HOURS_NOT_PROVIDED -> R.string.reason_hours_not_provided
        CheckReason.HOLIDAY_HOURS_NOT_PROVIDED -> R.string.reason_holiday_hours_not_provided
        CheckReason.HOLIDAY_STATUS_UNKNOWN -> R.string.reason_holiday_status_unknown
        CheckReason.HOURS_EXCEPTION_NOTED -> R.string.reason_hours_exception_noted
        CheckReason.VEHICLE_TYPE_PERMITTED -> R.string.reason_vehicle_type_permitted
        CheckReason.VEHICLE_TYPE_NOT_PERMITTED -> R.string.reason_vehicle_type_not_permitted
        CheckReason.VEHICLE_TYPE_NOT_PROVIDED -> R.string.reason_vehicle_type_not_provided
        CheckReason.ENTRANCE_ACTIVE -> R.string.reason_entrance_active
        CheckReason.ENTRANCE_CLOSED -> R.string.reason_entrance_closed
        CheckReason.ENTRANCE_STATUS_UNKNOWN -> R.string.reason_entrance_status_unknown
        CheckReason.TRUST_FRESH -> R.string.reason_trust_fresh
        CheckReason.TRUST_STALE_VERIFICATION -> R.string.reason_trust_stale
        CheckReason.TRUST_VERIFICATION_DATE_MISSING -> R.string.reason_trust_date_missing
        CheckReason.TRUST_BASELINE_NOT_REVERIFIED -> R.string.reason_trust_baseline
        CheckReason.TRUST_COMMUNITY_UNVERIFIED -> R.string.reason_trust_community
        CheckReason.TRUST_SOURCE_UNKNOWN -> R.string.reason_trust_unknown
    }

@get:StringRes
val EntranceDirection.labelRes: Int
    get() = when (this) {
        EntranceDirection.ENTRY -> R.string.direction_entry
        EntranceDirection.EXIT -> R.string.direction_exit
        EntranceDirection.BIDIRECTIONAL -> R.string.direction_bidirectional
        EntranceDirection.UNKNOWN -> R.string.direction_unknown
    }

@get:StringRes
val EntranceClass.labelRes: Int
    get() = when (this) {
        EntranceClass.MAIN -> R.string.entrance_class_main
        EntranceClass.SUB -> R.string.entrance_class_sub
        EntranceClass.COMPLEX -> R.string.entrance_class_complex
        EntranceClass.RESIDENT_ONLY -> R.string.entrance_class_resident
        EntranceClass.GROUND -> R.string.entrance_class_ground
        EntranceClass.UNDERGROUND -> R.string.entrance_class_underground
        EntranceClass.OTHER -> R.string.entrance_class_other
    }

@get:StringRes
val EntranceStatus.labelRes: Int
    get() = when (this) {
        EntranceStatus.ACTIVE -> R.string.entrance_status_active
        EntranceStatus.CLOSED -> R.string.entrance_status_closed
        EntranceStatus.UNKNOWN -> R.string.entrance_status_unknown
    }

@get:StringRes
val HeightLimitStructure.labelRes: Int
    get() = when (this) {
        HeightLimitStructure.INSTALLED -> R.string.height_structure_installed
        HeightLimitStructure.NOT_INSTALLED -> R.string.height_structure_not_installed
        HeightLimitStructure.UNKNOWN -> R.string.height_structure_unknown
    }

@get:StringRes
val PermittedVehicleType.labelRes: Int
    get() = when (this) {
        PermittedVehicleType.GENERAL -> R.string.vehicle_type_general
        PermittedVehicleType.RESIDENT -> R.string.vehicle_type_resident
        PermittedVehicleType.EMERGENCY -> R.string.vehicle_type_emergency
        PermittedVehicleType.SERVICE -> R.string.vehicle_type_service
    }

@get:StringRes
val RouteTargetType.labelRes: Int
    get() = when (this) {
        RouteTargetType.ENTRANCE_POINT -> R.string.route_target_entrance
        RouteTargetType.ROAD_SNAPPED_POINT -> R.string.route_target_road
        RouteTargetType.FACILITY_CENTROID -> R.string.route_target_centroid
    }

@get:StringRes
val DayType.labelRes: Int
    get() = when (this) {
        DayType.WEEKDAY -> R.string.day_weekday
        DayType.SATURDAY -> R.string.day_saturday
        DayType.SUNDAY -> R.string.day_sunday
        DayType.HOLIDAY -> R.string.day_holiday
    }

@get:StringRes
val AccessoryType.labelRes: Int
    get() = when (this) {
        AccessoryType.ROOF_BOX -> R.string.accessory_roof_box
        AccessoryType.ROOF_CARRIER -> R.string.accessory_roof_carrier
        AccessoryType.ROOFTOP_TENT -> R.string.accessory_rooftop_tent
        AccessoryType.BIKE_CARRIER -> R.string.accessory_bike_carrier
        AccessoryType.EXTRA_CARGO -> R.string.accessory_extra_cargo
        AccessoryType.OTHER -> R.string.accessory_other
    }

@get:StringRes
val SpecConfidence.labelRes: Int
    get() = when (this) {
        SpecConfidence.VERIFIED_BY_SDK -> R.string.spec_confidence_sdk
        SpecConfidence.VERIFIED_BY_MANUFACTURER -> R.string.spec_confidence_manufacturer
        SpecConfidence.USER_CONFIRMED -> R.string.spec_confidence_user_confirmed
        SpecConfidence.USER_ESTIMATED -> R.string.spec_confidence_user_estimated
        SpecConfidence.UNKNOWN -> R.string.spec_confidence_unknown
    }

@get:StringRes
val HeightBasis.labelRes: Int
    get() = when (this) {
        HeightBasis.MEASURED_TOTAL -> R.string.height_basis_measured
        HeightBasis.FACTORY_WITH_ACCESSORIES -> R.string.height_basis_factory
        HeightBasis.UNAVAILABLE -> R.string.height_basis_unavailable
    }

@get:StringRes
val ProfileField.labelRes: Int
    get() = when (this) {
        ProfileField.HEIGHT -> R.string.field_height
        ProfileField.WIDTH -> R.string.field_width
        ProfileField.LENGTH -> R.string.field_length
        ProfileField.WEIGHT -> R.string.field_weight
        ProfileField.MEASURED_HEIGHT -> R.string.field_measured_height
        ProfileField.HEIGHT_CORRECTION -> R.string.field_height_correction
        ProfileField.SAFETY_MARGIN -> R.string.field_safety_margin
        ProfileField.ACCESSORY_HEIGHT -> R.string.field_accessory_height
        ProfileField.ACCESSORY_WIDTH -> R.string.field_accessory_width
        ProfileField.ACCESSORY_LENGTH -> R.string.field_accessory_length
    }

@get:StringRes
val VehicleLinkFailure.labelRes: Int
    get() = when (this) {
        VehicleLinkFailure.SERVICE_NOT_INSTALLED -> R.string.vehicle_failure_service_missing
        VehicleLinkFailure.CONNECTION_FAILED -> R.string.vehicle_failure_connection
        VehicleLinkFailure.NO_RESPONSE -> R.string.vehicle_failure_no_response
        VehicleLinkFailure.PERMISSION_DENIED -> R.string.vehicle_failure_permission
        VehicleLinkFailure.NO_DATA -> R.string.vehicle_failure_no_data
        VehicleLinkFailure.SDK_ERROR -> R.string.vehicle_failure_sdk_error
    }

@get:StringRes
val RouteBlockReason.labelRes: Int
    get() = when (this) {
        RouteBlockReason.COORDINATES_MISSING -> R.string.route_block_coordinates_missing
        RouteBlockReason.COORDINATES_INVALID -> R.string.route_block_coordinates_invalid
        RouteBlockReason.FACILITY_CENTROID_ONLY -> R.string.route_block_centroid
        RouteBlockReason.DEMO_DATA -> R.string.route_block_demo
        RouteBlockReason.ENTRANCE_CLOSED -> R.string.route_block_closed
        RouteBlockReason.EXIT_ONLY -> R.string.route_block_exit_only
    }

@get:StringRes
val RouteFailure.labelRes: Int
    get() = when (this) {
        RouteFailure.NAVI_SERVICE_UNAVAILABLE -> R.string.route_failure_service_missing
        RouteFailure.CONNECTION_FAILED -> R.string.route_failure_connection
        RouteFailure.PERMISSION_DENIED -> R.string.route_failure_permission
        RouteFailure.REQUEST_REJECTED -> R.string.route_failure_rejected
        RouteFailure.ROUTE_SEARCH_FAILED -> R.string.route_failure_search
        RouteFailure.ROUTE_TOO_FAR -> R.string.route_failure_too_far
        RouteFailure.ROUTE_TOO_SHORT -> R.string.route_failure_too_short
        RouteFailure.LOW_BATTERY -> R.string.route_failure_low_battery
        RouteFailure.NO_RESPONSE -> R.string.route_failure_no_response
        RouteFailure.CANCELLED -> R.string.route_failure_cancelled
    }

@get:StringRes
val ReportType.labelRes: Int
    get() = when (this) {
        ReportType.WRONG_LOCATION -> R.string.report_type_wrong_location
        ReportType.ENTRANCE_CLOSED -> R.string.report_type_closed
        ReportType.HEIGHT_CHANGED -> R.string.report_type_height
        ReportType.WRONG_DIRECTION -> R.string.report_type_direction
        ReportType.HOURS_CHANGED -> R.string.report_type_hours
        ReportType.RESTRICTION_CHANGED -> R.string.report_type_restriction
        ReportType.OTHER -> R.string.report_type_other
    }

@get:StringRes
val ReportDelivery.labelRes: Int
    get() = when (this) {
        ReportDelivery.QUEUED -> R.string.report_delivery_queued
        ReportDelivery.SENT -> R.string.report_delivery_sent
        ReportDelivery.FAILED -> R.string.report_delivery_failed
        ReportDelivery.REJECTED -> R.string.report_delivery_rejected
    }

@get:StringRes
val ReportIssue.labelRes: Int
    get() = when (this) {
        ReportIssue.ENTRANCE_NOT_FOUND -> R.string.report_issue_not_found
        ReportIssue.DEMO_ENTRANCE -> R.string.report_issue_demo
        ReportIssue.DESCRIPTION_TOO_LONG -> R.string.report_issue_too_long
        ReportIssue.DESCRIPTION_REQUIRED -> R.string.report_issue_description_required
        ReportIssue.PROPOSED_HEIGHT_REQUIRED -> R.string.report_issue_height_required
        ReportIssue.PROPOSED_HEIGHT_OUT_OF_RANGE -> R.string.report_issue_height_range
        ReportIssue.PROPOSED_DIRECTION_REQUIRED -> R.string.report_issue_direction_required
    }
