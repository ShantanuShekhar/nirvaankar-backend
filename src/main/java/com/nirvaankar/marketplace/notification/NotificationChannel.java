package com.nirvaankar.marketplace.notification;

import java.util.Map;

public interface NotificationChannel {

    String channelCode();

    void send(NotificationMessage message);
}
