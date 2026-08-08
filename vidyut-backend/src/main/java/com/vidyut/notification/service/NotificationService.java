package com.vidyut.notification.service;

import com.vidyut.notification.entity.Notification;
import com.vidyut.notification.entity.NotificationType;

import java.util.List;

public interface NotificationService {
    void sendNotification(Long userId, String title, String message, NotificationType type);
    List<Notification> getNotificationsForUser(Long userId);
}
