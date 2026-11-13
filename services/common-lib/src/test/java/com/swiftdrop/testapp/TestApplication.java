package com.swiftdrop.testapp;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * Minimal app for common-lib context tests. It lives outside com.swiftdrop.common on purpose, so the tests prove the
 * shared outbox is found through its own auto-configuration and not through this app's package scan.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class TestApplication {
}
