package com.cpe.simulator.xmpp;

import java.util.function.Supplier;

public class ScramSha1Mechanism extends ScramMechanism {

    public ScramSha1Mechanism() {
        super("SCRAM-SHA-1", "HmacSHA1", "SHA-1");
    }

    ScramSha1Mechanism(Supplier<String> nonceSupplier) {
        super("SCRAM-SHA-1", "HmacSHA1", "SHA-1", nonceSupplier);
    }
}
