# Finder — smartphone search

<i>Application for remote mobile phone searching via SMS requests.</i>
------------
<a href="https://f-droid.org/packages/ru.seva.finder">
    <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80">
</a>

Functionality
-------------

The main function of Finder is to send the location of the smartphone it's installed on when requested. It also has a "tracker" feature (it can’t be activated remotely, only manually). When it's activated, the app periodically sends coordinates to a set number. The application is designed to search for missing people or stolen phones. SMS messages are used for communication because an Internet connection may not be available. It has two ways of determining the device's coordinates: using GPS (must be enabled in system settings!), and using mobile networks and WiFi info. In the second case, the response SMS will include MAC addresses of WiFi access points, and to determine the location it is necessary to have this application and a connection the Internet on the requesting phone (not needed on the remote [responding] side). This method can help in the case of trying to find a phone in a building / dense arrangement of buildings in the city.

<b>Important!</b> On Android 6.0 and newer for the Wifi networks searching feature, GPS must be enabled! (beforehand). GPS will not be used during network scanning, but nevertheless, newer versions of Android require it to be turned on (otherwise the system returns an empty list of networks).

An important feature is the fact that the application only responds to numbers from the "trusted list" and only when the "respond to requests" option is enabled! It is necessary to add the telephone numbers from which the requests will be sent in advance. The commands on the requesting and responding phones must be the same. It is possible to enable remote adding of phone nimbers to the "trusted list". If this mode is enabled (and the command is right) it allows you to remotely register a previously unknown number in the "trusted list". Finder displays notifications for any requests/responses.
It is also possible to send your coordinates manually to any number from "trusted list".

MIUI users (and possibly some other OSes) need to apply additional system settings to make the aplication operate reliably. This is described in detail in the built-in help.


If you see a bug, please report it (or make a pull request here)!

The app is now fully open source. The map library used to display maps is OsmDroid version 6.0.1.

Sevastyanov Nikita, 2018-2020
nikita.sevast@gmail.com

I'm the app author, and I support inclusion in F-Droid.

Activate Location automatically
-------------------------------
This app can automatically activate location when a request is received.

Requirements:
* Min. Android 4.4
* Grant permission via ADB

To grant the permission you need to do the following:
1. Install ADB (https://developer.android.com/studio/releases/platform-tools.html)
2. Activate Developer options on your phone (https://developer.android.com/studio/debug/dev-options#enable)
3. In the Developer options enable USB debugging (https://developer.android.com/studio/debug/dev-options#debugging)
4. Connect your phone with your computer via USB
5. On your computer open a terminal, change to the directory where you extracted the platform tools and run the following command

```
adb shell pm grant ru.seva.finder android.permission.WRITE_SECURE_SETTINGS
```

Why app absent in Google Play?
-------------------------------

Unfortunately, google changed its rules for apps. Now (after spring 2019) apps with SMS_SEND permission must be an app for messaging, default app in system for sms.
Finder was published before this changes in Play store, but then has been deleted by google.. Changes described here - https://play.google.com/about/privacy-security-deception/permissions/ and https://support.google.com/googleplay/android-developer/answer/9047303#intended
It seems that we can try use exception - "Physical safety / emergency alerts to send SMS", but next saying that "Family or device locator" won't be permitted...