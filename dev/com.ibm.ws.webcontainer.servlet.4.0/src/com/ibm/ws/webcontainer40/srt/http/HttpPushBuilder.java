/*******************************************************************************
 * Copyright (c) 2011, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.webcontainer40.srt.http;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.Cookie;
import javax.servlet.http.PushBuilder;

import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.websphere.servlet40.IRequest40;
import com.ibm.ws.webcontainer40.osgi.osgi.WebContainerConstants;
import com.ibm.ws.webcontainer40.srt.SRTServletRequest40;
import com.ibm.wsspi.genericbnf.HeaderField;
import com.ibm.wsspi.http.HttpCookie;
import com.ibm.wsspi.http.channel.values.HttpHeaderKeys;
import com.ibm.wsspi.http.ee8.Http2PushException;

public class HttpPushBuilder implements PushBuilder, com.ibm.wsspi.http.ee8.Http2PushBuilder {

    private final static TraceComponent tc = Tr.register(HttpPushBuilder.class, WebContainerConstants.TR_GROUP, WebContainerConstants.NLS_PROPS);

    private String _method = "GET";
    private String _queryString = null;
    private String _sessionId = null;
    private String _path = null;
    private String _pathURI = null;
    private String _pathQueryString = null;

    private static final String HDR_REFERER = HttpHeaderKeys.HDR_REFERER.getName();

    private final SRTServletRequest40 _inboundRequest;

    // Used to store headers for the push request
    HashMap<String, HttpHeaderField> _headers = new HashMap<String, HttpHeaderField>();
    // Used to store cookies for the push request
    HashSet<HttpCookie> _cookies;

    private static ArrayList<String> _invalidMethods = new ArrayList<String>(Arrays.asList("POST",
                                                                                           "PUT",
                                                                                           "DELETE",
                                                                                           "CONNECT",
                                                                                           "OPTIONS",
                                                                                           "TRACE"));

    public HttpPushBuilder(SRTServletRequest40 request, String sessionId, Enumeration<String> headerNames, Cookie[] addedCookies) {

        _inboundRequest = request;
        _sessionId = sessionId;

        if (headerNames != null) {

            // Note: headers passed should already have removed the conditional headers.
            while (headerNames.hasMoreElements()) {

                String headerName = headerNames.nextElement();
                Enumeration<String> headerValues = request.getHeaders(headerName);
                while (headerValues.hasMoreElements()) {
                    addHeader(headerName, headerValues.nextElement());
                }
            }
        }

        if (addedCookies != null) {
            for (Cookie cookie : addedCookies) {
                if (cookie.getMaxAge() > 0) {
                    if (_cookies == null) {
                        _cookies = new HashSet<HttpCookie>();
                    }
                    HttpCookie hc = new HttpCookie(cookie.getName(), cookie.getValue());
                    hc.setPath(cookie.getPath());
                    hc.setVersion(cookie.getVersion());
                    hc.setComment(cookie.getComment());
                    hc.setDomain(cookie.getDomain());
                    hc.setMaxAge(cookie.getMaxAge());
                    hc.setSecure(cookie.getSecure());
                    hc.setHttpOnly(cookie.isHttpOnly());
                    _cookies.add(hc);
                }
            }
        } else {
            _cookies = null;
        }

    }

    @Override
    public PushBuilder method(String method) throws IllegalArgumentException {

        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
            Tr.debug(tc, "method()", "method = " + method);
        }

        if (method == null)
            throw new NullPointerException();
        else if (method.isEmpty())
            throw new IllegalArgumentException();
        else if (_invalidMethods.contains(method.toUpperCase())) {
            throw new IllegalArgumentException();
        }
        _method = method;
        return this;
    }

    @Override
    public PushBuilder queryString(String queryString) {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
            Tr.debug(tc, "queryString()", "queryString = " + queryString);
        }
        _queryString = queryString;
        return this;
    }

    @Override
    public PushBuilder sessionId(String sessionId) {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
            Tr.debug(tc, "sessionId()", "sessionId = " + sessionId);
        }
        _sessionId = sessionId;
        return this;
    }

    @Override
    public PushBuilder setHeader(String name, String value) {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
            Tr.debug(tc, "setHeader()", "name = " + name + ", value = " + value);
        }
        removeHeader(name);
        addHeader(name, value);
        return this;
    }

    @Override
    public PushBuilder addHeader(String name, String value) {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
            Tr.debug(tc, "addHeader()", "name = " + name + ", value = " + value);
        }
        _headers.put(name, new HttpHeaderField(name, value));
        return this;
    }

    @Override
    public PushBuilder removeHeader(String name) {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
            Tr.debug(tc, "removeHeader()", "name = " + name);
        }
        _headers.remove(name);
        return this;
    }

    @Override
    public PushBuilder path(String path) {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) {
            Tr.entry(tc, "path()", "path = " + path);
        }
        _path = path;
        if (path != null && path.contains("?")) {
            String[] pathParts = path.split("\\?");
            _pathURI = pathParts[0];
            _pathQueryString = "?" + pathParts[1];
        } else {
            _pathURI = path;
            _pathQueryString = null;
        }

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) {
            Tr.entry(tc, "path()", "uri = " + _pathURI + ", queryString = " + _pathQueryString);
        }
        return this;
    }

    @Override
    public void push() throws IllegalStateException {

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) {
            Tr.entry(tc, "push()", "path = " + _path);
        }

        if (_path == null) {
            if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) {
                Tr.exit(tc, "push()", "Path not set. Throw IllegalStateException");
            }
            throw new IllegalStateException();
        }

        String referer = _inboundRequest.getRequestURI();
        if (_inboundRequest.getQueryString() != null) {
            referer += "?" + _inboundRequest.getQueryString();
        }
        this.setHeader(HDR_REFERER, referer);

        if (_queryString != null) {
            if (_pathQueryString != null) {
                _pathQueryString += "&" + _queryString;
            } else {
                _pathQueryString = "?" + _queryString;
            }
            if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
                Tr.debug(tc, "push()", "path = " + _pathURI + _pathQueryString);
            }
        }

        IRequest40 request = (IRequest40) _inboundRequest.getIRequest();
        try {
            request.getHttpRequest().pushNewRequest(this);
        } catch (Http2PushException e) {
            if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
                Tr.debug(tc, "push()", "exception from push request : " + e);
            }

            _path = null;
            _pathURI = null;
            _queryString = null;
            _pathQueryString = null;

            throw new IllegalStateException(e);
        }

        _path = null;
        _pathURI = null;
        _queryString = null;
        _pathQueryString = null;

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) {
            Tr.exit(tc, "push()");
        }

    }

    @Override
    public String getMethod() {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
            Tr.debug(tc, "getMethod()", "method = " + _method);
        }
        return _method;
    }

    @Override
    public String getQueryString() {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
            Tr.debug(tc, "getQueryString()", "queryString = " + _queryString);
        }
        return _queryString;
    }

    @Override
    public String getSessionId() {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
            Tr.debug(tc, "getSessionId()", "sessionId = " + _sessionId);
        }
        return _sessionId;
    }

    @Override
    public Set<String> getHeaderNames() {
        return Collections.unmodifiableSet(_headers.keySet());
        //return _pushRequest.getAllHeaderNames();
    }

    @Override
    public String getHeader(String name) {
        HttpHeaderField field = _headers.get(name);
        if (field != null) {
            return field.asString();
        } else
            return null;
    }

    @Override
    public String getPath() {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
            Tr.debug(tc, "getPath()", "path = " + _path);
        }
        return _path;
    }

    // Methods required by com.ibm.wsspi.http.ee8.HttpPushBuilder
    @Override
    public Set<HeaderField> getHeaders() {
        return new HashSet<HeaderField>(_headers.values());
    }

    @Override
    public String getURI() {
        return this._pathURI;
    }

    @Override
    public Set<HttpCookie> getCookies() {
        return _cookies;
    }

}