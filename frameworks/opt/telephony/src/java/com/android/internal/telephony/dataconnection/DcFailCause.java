/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony.dataconnection;

import android.content.res.Resources;

import java.util.HashMap;

/**
 * Returned as the reason for a connection failure as defined
 * by RIL_DataCallFailCause in ril.h and some local errors.
 */
public enum DcFailCause {
    NONE(0),

    // This series of errors as specified by the standards
    // specified in ril.h
    OPERATOR_BARRED(0x08),                  /* no retry */
    MBMS_CAPABILITIES_INSUFFICIENT(0x18),
    LLC_SNDCP_FAILURE(0x19),
    INSUFFICIENT_RESOURCES(0x1A),
    MISSING_UNKNOWN_APN(0x1B),              /* no retry */
    UNKNOWN_PDP_ADDRESS_TYPE(0x1C),         /* no retry */
    USER_AUTHENTICATION(0x1D),              /* no retry */
    ACTIVATION_REJECT_GGSN(0x1E),           /* no retry */
    ACTIVATION_REJECT_UNSPECIFIED(0x1F),
    SERVICE_OPTION_NOT_SUPPORTED(0x20),     /* no retry */
    SERVICE_OPTION_NOT_SUBSCRIBED(0x21),    /* no retry */
    SERVICE_OPTION_OUT_OF_ORDER(0x22),
    NSAPI_IN_USE(0x23),                     /* no retry */
    REGULAR_DEACTIVATION(0x24),             /* possibly restart radio, based on config */
    QOS_NOT_ACCEPTED(0x25),
    NETWORK_FAILURE(0x26),
    REACTIVATION_REQUESTED(0x27),
    FEATURE_NOT_SUPPORTED(0x28),
    SEMANTIC_ERROR_IN_TFT(0x29),
    SYNTACTICAL_ERROR_IN_TFT(0x2A),
    UNKNOWN_PDP_CONTEXT(0x2B),
    SEMANTIC_ERROR_IN_PACKET_FILTER(0x2C),
    SYNTACTICAL_ERROR_IN_PACKET_FILTER(0x2D),
    PDP_CONTEXT_WITHOU_TFT_ALREADY_ACTIVATED(0x2E),
    MULTICAST_GROUP_MEMBERSHIP_TIMEOUT(0x2F),
    BCM_VIOLATION(0x30),
    ONLY_IPV4_ALLOWED(0x32),                /* no retry */
    ONLY_IPV6_ALLOWED(0x33),                /* no retry */
    ONLY_SINGLE_BEARER_ALLOWED(0x34),
    COLLISION_WITH_NW_INITIATED_REQUEST(0x38),
    BEARER_HANDLING_NOT_SUPPORT(0x3C),
    MAX_PDP_NUMBER_REACHED(0x41),
    APN_NOT_SUPPORT_IN_RAT_PLMN(0x42),
    INVALID_TRANSACTION_ID_VALUE(0x51),
    SEMENTICALLY_INCORRECT_MESSAGE(0x5F),
    INVALID_MANDATORY_INFO(0x60),
    MESSAGE_TYPE_NONEXIST_NOT_IMPLEMENTED(0x61),
    MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE(0x62),
    INFO_ELEMENT_NONEXIST_NOT_IMPLEMENTED(0x63),
    CONDITIONAL_IE_ERROR(0x64),
    MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE(0x65),
    PROTOCOL_ERRORS(0x6F),                  /* no retry */
    PN_RESTRICTION_VALUE_INCOMPATIBLE_WITH_PDP_CONTEXT(0x70),

    // Local errors generated by Vendor RIL
    // specified in ril.h
    REGISTRATION_FAIL(-1),
    GPRS_REGISTRATION_FAIL(-2),
    SIGNAL_LOST(-3),
    PREF_RADIO_TECH_CHANGED(-4),            /* no retry */
    RADIO_POWER_OFF(-5),                    /* no retry */
    TETHERED_CALL_ACTIVE(-6),               /* no retry */
    INSUFFICIENT_LOCAL_RESOURCES(0xFFFFE),
    ERROR_UNSPECIFIED(0xFFFF),

    // Errors generated by the Framework
    // specified here
    UNKNOWN(0x10000),
    RADIO_NOT_AVAILABLE(0x10001),                   /* no retry */
    UNACCEPTABLE_NETWORK_PARAMETER(0x10002),        /* no retry */
    CONNECTION_TO_DATACONNECTIONAC_BROKEN(0x10003),
    LOST_CONNECTION(0x10004),
    RESET_BY_FRAMEWORK(0x10005),
    GMM_ERROR(0x0D19);


    private final boolean mRestartRadioOnRegularDeactivation = Resources.getSystem().getBoolean(
            com.android.internal.R.bool.config_restart_radio_on_pdp_fail_regular_deactivation);
    private final int mErrorCode;
    private static final HashMap<Integer, DcFailCause> sErrorCodeToFailCauseMap;
    static {
        sErrorCodeToFailCauseMap = new HashMap<Integer, DcFailCause>();
        for (DcFailCause fc : values()) {
            sErrorCodeToFailCauseMap.put(fc.getErrorCode(), fc);
        }
    }

    DcFailCause(int errorCode) {
        mErrorCode = errorCode;
    }

    public int getErrorCode() {
        return mErrorCode;
    }

    /** Radio has failed such that the radio should be restarted. */
    public boolean isRestartRadioFail() {
        return (this == REGULAR_DEACTIVATION && mRestartRadioOnRegularDeactivation);
    }

    public boolean isPermanentFail() {
        return (this == OPERATOR_BARRED) || (this == MISSING_UNKNOWN_APN) ||
                (this == UNKNOWN_PDP_ADDRESS_TYPE) || (this == USER_AUTHENTICATION) ||
                (this == ACTIVATION_REJECT_GGSN) || (this == SERVICE_OPTION_NOT_SUPPORTED) ||
                (this == SERVICE_OPTION_NOT_SUBSCRIBED) || (this == NSAPI_IN_USE) ||
                (this == ONLY_IPV4_ALLOWED) || (this == ONLY_IPV6_ALLOWED) ||
                (this == PROTOCOL_ERRORS) ||
                (this == RADIO_POWER_OFF) || (this == TETHERED_CALL_ACTIVE) ||
                (this == RADIO_NOT_AVAILABLE) || (this == UNACCEPTABLE_NETWORK_PARAMETER) ||
                (this == SIGNAL_LOST);
    }

    public boolean isEventLoggable() {
        return (this == OPERATOR_BARRED) || (this == INSUFFICIENT_RESOURCES) ||
                (this == UNKNOWN_PDP_ADDRESS_TYPE) || (this == USER_AUTHENTICATION) ||
                (this == ACTIVATION_REJECT_GGSN) || (this == ACTIVATION_REJECT_UNSPECIFIED) ||
                (this == SERVICE_OPTION_NOT_SUBSCRIBED) ||
                (this == SERVICE_OPTION_NOT_SUPPORTED) ||
                (this == SERVICE_OPTION_OUT_OF_ORDER) || (this == NSAPI_IN_USE) ||
                (this == ONLY_IPV4_ALLOWED) || (this == ONLY_IPV6_ALLOWED) ||
                (this == PROTOCOL_ERRORS) || (this == SIGNAL_LOST) ||
                (this == RADIO_POWER_OFF) || (this == TETHERED_CALL_ACTIVE) ||
                (this == UNACCEPTABLE_NETWORK_PARAMETER);
    }

    /**
     *
     * fromInt: Transfors the errorCode to DcFailCause Enum.
     *
     * @param errorCode
     * @return DcFailCause
     */
    public static DcFailCause fromInt(int errorCode) {
        DcFailCause fc = sErrorCodeToFailCauseMap.get(errorCode);
        if (fc == null) {
            fc = UNKNOWN;
        }
        return fc;
    }
}