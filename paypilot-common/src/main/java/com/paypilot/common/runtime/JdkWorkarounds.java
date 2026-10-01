package com.paypilot.common.runtime;

import java.io.File;
import java.util.Locale;

/**
 * Works around a JDK-on-Windows problem that stops the JDK's HTTP client and NIO selectors from
 * starting ("Unable to establish loopback connection").
 *
 * <p>On Windows the JDK creates a Unix-domain socket in the temp directory for every NIO selector.
 * That fails when the temp path contains a space or an 8.3 short name such as
 * {@code C:\Users\ANISHK~1\AppData\Local\Temp}, which Windows uses for user names with a space.
 * Pointing {@code jdk.net.unixdomain.tmpdir} at {@code C:\Users\Public} avoids it.
 *
 * <p>Call {@link #apply()} first thing in {@code main}, before anything touches the network.
 */
public final class JdkWorkarounds {

    static final String UDS_TMPDIR = "jdk.net.unixdomain.tmpdir";

    private JdkWorkarounds() {
    }

    public static void apply() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")
                || System.getProperty(UDS_TMPDIR) != null) {
            return;
        }
        String tmp = System.getProperty("java.io.tmpdir", "");
        if (!tmp.contains(" ") && !tmp.contains("~")) {
            return;
        }
        String shared = System.getenv("PUBLIC");
        if (shared != null && !shared.contains(" ") && !shared.contains("~") && new File(shared).isDirectory()) {
            System.setProperty(UDS_TMPDIR, shared);
        }
    }
}
