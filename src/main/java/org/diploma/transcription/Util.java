package org.diploma.transcription;

import org.jitsi.utils.logging.*;
import org.json.*;

import java.io.*;
import java.net.*;

public class Util
{
    private final static Logger logger = Logger.getLogger(Util.class);

    public static void postJSON(String address, JSONObject json)
    {
        try
        {
            URL url = new URL(address);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            OutputStream os = conn.getOutputStream();
            os.write(json.toString().getBytes());
            os.flush();

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK)
            {
                logger.error("Error for action post received: "
                    + conn.getResponseCode()
                    + "(" + conn.getResponseMessage() + ")");
            }

            conn.disconnect();
        }
        catch (IOException e)
        {
            logger.error("Error posting transcription", e);
        }
    }
}
