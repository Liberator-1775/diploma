package org.diploma.rest;

import java.util.regex.*;

public class RESTUtil
{
    public static final String JSON_CONTENT_TYPE = "application/json";

    public static final String JSON_CONTENT_TYPE_WITH_CHARSET
        = JSON_CONTENT_TYPE + ";charset=UTF-8";

    private static final Pattern JsonContentTypeMatcher
        = Pattern.compile(
        "^\\s?application/json((\\s?)|(;\\s?(charset=UTF-8\\s?)?$))?");

    static public boolean isJSONContentType(String contentType)
    {
        return contentType != null &&
            JsonContentTypeMatcher.matcher(contentType).matches();
    }
}
