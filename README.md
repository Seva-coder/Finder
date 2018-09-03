# Finder - smartphone search
Application for remote phone search via SMS requests

At this moment it is not fully open-source because of usage Google map (but in the plans is usage of OsmDroid instead of Google maps).

For for compilation <b>you need 2 API keys</b> - first for <b>geolocation api</b> and second for Google maps. Google geolocation API key is located in <i>app/src/main/res/values/keys.xml</i> (here is blank of this file). Getting from https://developers.google.com/maps/documentation/geolocation/get-api-key it's free.

Second key, <b>for maps</b>, is located in <i>app/src/release/res/values/google_maps_api.xml</i> Note, that you should enter SHA1 fingerprint of package during getting this key in Google console (map will not work without this step). Page on Google - https://developers.google.com/maps/documentation/android-sdk/signup

Also in the nearest plans is description and block-scheme of all components of this application here.
