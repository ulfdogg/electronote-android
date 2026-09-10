package de.graetz.electronote.electrical

enum class CircuitCategory(val label: String) {
    PASSIVE("Passive Bauteile"),
    SOURCES("Quellen & Masse"),
    SEMICONDUCTORS("Halbleiter & Op-Amps"),
    SWITCHES("Schalter & Kontakte"),
    METERING("Messtechnik & Lasten"),
    LOGIC("Digital & Logik")
}

enum class CircuitSymbolType(val category: CircuitCategory, val displayName: String, val keywords: List<String>) {
    RESISTOR(CircuitCategory.PASSIVE, "Widerstand", listOf("resistor", "ohm", "r")),
    CAPACITOR(CircuitCategory.PASSIVE, "Kondensator", listOf("capacitor", "farad", "c")),
    INDUCTOR(CircuitCategory.PASSIVE, "Spule", listOf("inductor", "henry", "l", "coil")),
    POTENTIOMETER(CircuitCategory.PASSIVE, "Potentiometer", listOf("poti", "trimmer")),

    BATTERY(CircuitCategory.SOURCES, "Batterie", listOf("battery", "zelle")),
    VOLTAGE_SOURCE(CircuitCategory.SOURCES, "Spannungsquelle", listOf("source", "quelle", "dc", "u")),
    AC_SOURCE(CircuitCategory.SOURCES, "Wechselspannungsquelle", listOf("ac", "wechselspannung")),
    GROUND(CircuitCategory.SOURCES, "Masse", listOf("ground", "gnd", "erde")),

    DIODE(CircuitCategory.SEMICONDUCTORS, "Diode", listOf("diode")),
    LED(CircuitCategory.SEMICONDUCTORS, "LED", listOf("led", "leuchtdiode")),
    NPN_TRANSISTOR(CircuitCategory.SEMICONDUCTORS, "NPN-Transistor", listOf("transistor", "npn", "bjt")),
    OP_AMP(CircuitCategory.SEMICONDUCTORS, "Operationsverstärker", listOf("opamp", "op-amp", "verstärker")),

    SWITCH(CircuitCategory.SWITCHES, "Schalter", listOf("switch", "schalter")),
    PUSH_BUTTON(CircuitCategory.SWITCHES, "Taster", listOf("button", "taster")),
    RELAY(CircuitCategory.SWITCHES, "Relais", listOf("relay", "relais")),
    FUSE(CircuitCategory.SWITCHES, "Sicherung", listOf("fuse", "sicherung")),

    AMMETER(CircuitCategory.METERING, "Amperemeter", listOf("ammeter", "strommesser", "a")),
    VOLTMETER(CircuitCategory.METERING, "Voltmeter", listOf("voltmeter", "spannungsmesser", "v")),
    LAMP(CircuitCategory.METERING, "Lampe", listOf("lamp", "lampe", "glühbirne")),
    MOTOR(CircuitCategory.METERING, "Motor", listOf("motor", "m")),

    AND_GATE(CircuitCategory.LOGIC, "UND-Gatter", listOf("and", "und")),
    OR_GATE(CircuitCategory.LOGIC, "ODER-Gatter", listOf("or", "oder")),
    NOT_GATE(CircuitCategory.LOGIC, "NICHT-Gatter", listOf("not", "nicht", "inverter")),
    XOR_GATE(CircuitCategory.LOGIC, "XOR-Gatter", listOf("xor", "exklusiv-oder"))
}
