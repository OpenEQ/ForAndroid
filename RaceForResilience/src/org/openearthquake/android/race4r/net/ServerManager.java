package org.openearthquake.android.race4r.net;

import android.content.Context;

public class ServerManager {
    /**
     * Sends the registration ID to your server over HTTP, so it can use GCM/HTTP or CCS to send
     * messages to your app. Not needed for this demo since the device sends upstream messages
     * to a server that echoes back the message using the 'from' address in the message.
     */
    public void sendRegistrationIdToBackend(Context context, String regId, String myUuid, double latitude, double longitude) {
      // Your implementation here.
        new HttpPostAsyncTask(context).execute("register", regId, myUuid, String.valueOf(latitude), String.valueOf(longitude));
    }
}
