package com.swiftdrop.merchant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.*;
import java.util.*;

@Component
public class WebhookUrlPolicy {
    private final Set<String> localHosts;

    public WebhookUrlPolicy(@Value("${WEBHOOK_ALLOWED_LOCAL_HOSTS:demo-store}") String hosts) {
        localHosts = new HashSet<>(Arrays.asList(hosts.toLowerCase(Locale.ROOT).split(",")));
    }

    public URI validate(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null
                    || url.length() > 2048) throw new IllegalArgumentException();
            boolean local = localHosts.contains(host.toLowerCase(Locale.ROOT));
            if (local) {
                if (!Set.of("http", "https").contains(uri.getScheme()))
                    throw new IllegalArgumentException();
            } else {
                if (!"https".equals(uri.getScheme())
                        || (uri.getPort() != -1 && uri.getPort() != 443))
                    throw new IllegalArgumentException();
                for (InetAddress address : InetAddress.getAllByName(host)) {
                    byte[] bytes = address.getAddress();
                    if (address.isAnyLocalAddress()
                            || address.isLoopbackAddress()
                            || address.isLinkLocalAddress()
                            || address.isSiteLocalAddress()
                            || address.isMulticastAddress()
                            || (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc)
                            || (bytes.length == 4
                                    && ((bytes[0] & 255) == 0
                                            || (bytes[0] & 255) == 127
                                            || ((bytes[0] & 255) == 100
                                                    && (bytes[1] & 255) >= 64
                                                    && (bytes[1] & 255) <= 127))))
                        throw new IllegalArgumentException();
                }
            }
            return uri;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Webhook URL must use public HTTPS or an explicitly allowed demo host");
        }
    }
}
