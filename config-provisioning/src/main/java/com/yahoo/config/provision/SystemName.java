// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.EnumSet;
import java.util.Set;

/**
 * Systems in hosted Vespa
 *
 * @author mpolden
 */
public enum SystemName {

    /** Continuous deployment system */
    cd(false, true),

    /** Production system */
    main(false, false),

    /** System accessible to the public */
    Public(true, false),

    /** Continuous deployment system for testing the Public system */
    PublicCd(true, true),

    /** Local development system */
    dev(false, false),

    /** VaaS */
    vaas(true, true); // TODO: Remove this and use public everywhere

    private final boolean isPublic;
    private final boolean isCd;

    SystemName(boolean isPublic, boolean isCd) {
        this.isPublic = isPublic;
        this.isCd = isCd;
    }

    public static SystemName defaultSystem() {
        return main;
    }

    public static SystemName from(String value) {
        switch (value.toLowerCase()) {
            case "dev": return dev;
            case "cd": return cd;
            case "main": return main;
            case "public": return Public;
            case "publiccd": return PublicCd;
            case "vaas": return vaas;
            default: throw new IllegalArgumentException(String.format("'%s' is not a valid system", value));
        }
    }

    public String value() {
        switch (this) {
            case dev: return "dev";
            case cd: return "cd";
            case main: return "main";
            case Public: return "public";
            case PublicCd: return "publiccd";
            case vaas: return "vaas";
            default : throw new IllegalStateException();
        }
    }

    /** Whether the system is similar to Public, e.g. PublicCd. */
    public boolean isPublic() { return isPublic; }

    /** Whether the system is used for continuous deployment. */
    public boolean isCd() { return isCd; }

    public static Set<SystemName> all() { return EnumSet.allOf(SystemName.class); }
}
