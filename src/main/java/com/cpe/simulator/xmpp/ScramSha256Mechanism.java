package com.cpe.simulator.xmpp;

import java.util.function.Supplier;

public class ScramSha256Mechanism extends ScramMechanism {

    public ScramSha256Mechanism() {
        super("SCRAM-SHA-256", "HmacSHA256", "SHA-256");
    }

    ScramSha256Mechanism(Supplier<String> nonceSupplier) {
        super("SCRAM-SHA-256", "HmacSHA256", "SHA-256", nonceSupplier);
    }
}
