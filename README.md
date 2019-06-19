# Finder - smartphone search

<i>Application for remote mobile phone searching via SMS-requests.</i>
------------
<a href="https://f-droid.org/packages/ru.seva.finder">
    <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80">
</a>
Functionality
-------------
The main function of the Finder is to send the location of the smartphone on request. Also it has tracking feature (can’t be activated remotely, only manually), using it app periodically send coordinates to setted number. The application is designed to search for lost people or a stolen phone. SMS is used for communication because Internet may not be available. It has two ways of determining the coordinates: using GPS (must be enabled in system settings!), and using mobile net and WiFi-net info. In the second case, the response SMS will have mac-addresses of wifi access points, and to determine the location it is necessary to have this application and the Internet on the requesting phone (but on the responding side the Internet is not needed). This method can help in the case of finding the phone in a building / dense arrangement of buildings in the city.

<b>Important!</b> On Android 6.0 and newer for Wifi-networks searching feature, GPS must be enabled! (beforehand). GPS will not be used during nets scanning, but nevertheless, newer versions of Android require it to be turned on (otherwise the system returns an empty list of nets).

An important feature is that the application responds only to numbers from the "trusted list" and only when the "respond to requests" option is enabled! It is necessary to add in advance the telephone numbers from which the requests will be sent. The commands on the requesting and responding phones must be the same. It is possible to enable the remote addition mode. If this mode was enabled and the command is right it allows you to remotely register a previously unknown number in the "trusted list". Finder displays notifications for any requests/responses.

MIUI users (and possibly some other OS) need to additionally make some settings in the system for the operability of the application. This is described in detail in the built-in help.
It is also possible to send your coordinates manually to any number from "trusted list".

If you see a bug, please report it! (or commit here)

Now it is fully open source, for map used library OsmDroid version 6.0.1.

Sevastyanov Nikita, 2018-2019
nikita.sevast@gmail.com

I'm the app author, and I support inclusion in F-Droid.
