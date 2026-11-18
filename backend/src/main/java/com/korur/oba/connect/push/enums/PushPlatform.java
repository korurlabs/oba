package com.korur.oba.connect.push.enums;

/**
 * Operating system of a device that receives push notifications. Expo relays a push to APNs for
 * {@link #IOS} and to FCM for {@link #ANDROID}, and the payload differs per platform, such as the
 * Android notification channel.
 */
public enum PushPlatform {

    // An iPhone; pushes go through APNs.
    IOS,

    // An Android phone; pushes go through FCM.
    ANDROID
}
