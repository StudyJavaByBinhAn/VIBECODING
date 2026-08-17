package com.vibecode.emailservice.event;

public final class KafkaTopics {

    public static final String APPOINTMENT_BOOKED = "appointment.booked";
    public static final String APPOINTMENT_CANCELLED = "appointment.cancelled";
    public static final String AUTH_PASSWORD_RESET_REQUESTED = "auth.password-reset-requested";

    private KafkaTopics() {
    }
}
