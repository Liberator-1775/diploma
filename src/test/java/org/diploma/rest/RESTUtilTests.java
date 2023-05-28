package org.diploma.rest;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class RESTUtilTests
{
    @Test
    public void testJSONContentMatcher()
    {
        assertFalse(
            RESTUtil.isJSONContentType(""));
        assertFalse(
            RESTUtil.isJSONContentType(null));

        assertTrue(
            RESTUtil.isJSONContentType(
                RESTUtil.JSON_CONTENT_TYPE));

        assertTrue(
            RESTUtil.isJSONContentType(
                RESTUtil.JSON_CONTENT_TYPE_WITH_CHARSET));

        assertTrue(
            RESTUtil.isJSONContentType(
                "application/json"));

        assertTrue(
            RESTUtil.isJSONContentType(
                " application/json"));

        assertTrue(
            RESTUtil.isJSONContentType(
                "application/json "));

        assertFalse(
            RESTUtil.isJSONContentType(
                "bapplication/json"));

        assertFalse(
            RESTUtil.isJSONContentType(
                "application/jsonx"));

        assertTrue(
            RESTUtil.isJSONContentType(
                "application/json;"));

        assertTrue(
            RESTUtil.isJSONContentType(
                " application/json;"));

        assertTrue(
            RESTUtil.isJSONContentType(
                "application/json; "));

        assertTrue(
            RESTUtil.isJSONContentType(
                "application/json; "));

        assertTrue(
            RESTUtil.isJSONContentType(
                "application/json;charset=UTF-8"));

        assertTrue(
            RESTUtil.isJSONContentType(
                "application/json; charset=UTF-8"));

        assertTrue(
            RESTUtil.isJSONContentType(
                "application/json; charset=UTF-8 "));

        assertFalse(
            RESTUtil.isJSONContentType(
                "application/json; charset=UTF-88"));
    }
}
