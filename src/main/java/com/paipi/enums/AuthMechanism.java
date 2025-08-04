package com.paipi.enums;

import lombok.Getter;


@Getter
public enum AuthMechanism {
    KERBEROS("KERBEROS"),
    NOSASL("NOSASL"),
    PLAIN("PLAIN");
    private String name;

    AuthMechanism(String name) {
        this.name = name;
    }
}
