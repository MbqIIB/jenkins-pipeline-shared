#!/usr/bin/env groovy
package com.anchorfree;

import groovy.json.JsonBuilder
@Grab("org.jodd:jodd-http:3.8.5")
import jodd.http.HttpRequest

class JenkinsHttpClient {

    private HttpRequest httpRequest
    private String userAgent = 'Jenkins'

    JenkinsHttpClient() {
        httpRequest = new HttpRequest()
    }

    def get(String host, String path, String user, String password) {
        def resp = httpRequest.method("GET")
                .basicAuthentication(user, password)
                .protocol("https")
                .host(host)
                .path(path)
                .port(443)
                .header("User-Agent", userAgent)
                .send()
        return resp
    }

    def postJson(String host, String path, Map<?, ?> body, String user, String password) {
        String jsonbody = new JsonBuilder(body).toString()
        def resp = httpRequest.method("POST")
                .basicAuthentication(user, password)
                .protocol("https")
                .host(host)
                .path(path)
                .port(443)
                .header("User-Agent", userAgent)
                .contentType('application/json')
                .body(jsonbody)
                .send()
        return resp
    }

    def delete(String host, String path, String user, String password) {
        def resp = httpRequest.method("DELETE")
                .basicAuthentication(user, password)
                .protocol("https")
                .host(host)
                .path(path)
                .port(443)
                .header("User-Agent", userAgent)
                .send()
        return resp
    }
}