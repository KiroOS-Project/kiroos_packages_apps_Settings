/*
 * Copyright (C) 2026 KiroOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.kiroos;

import android.content.Context;
import android.os.SystemProperties;

import com.android.internal.os.PowerProfile;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * What the running Galaxy S6 family model is: S6 (zeroflte), S6 edge (zerolte) or
 * S6 edge+ (zenlte). The device trees set ro.kiroos.*; ro.product.device is the fallback.
 */
public final class KiroOSDevice {

    private KiroOSDevice() {
    }

    /** ro.product.device of the model: zeroflte, zerolte or zenlte (with or without "xx"). */
    public static String device() {
        return SystemProperties.get("ro.product.device", "");
    }

    private static final int S6 = 0;
    private static final int S6_EDGE = 1;
    private static final int S6_EDGE_PLUS = 2;

    /**
     * Samsung model code of the phone itself, from its bootloader version (G920FXXU6EVG1 ->
     * G920F, G925F..., G928F..., G920W8...): right whatever build is installed. Empty if the
     * bootloader string is not a Galaxy S6 family one.
     */
    private static String hardwareCode() {
        final java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(G92[058][A-Z][0-9]?)")
                .matcher(SystemProperties.get("ro.bootloader", ""));
        return m.find() ? m.group(1) : "";
    }

    /** Which model this is: the bootloader first, then the build's ro.product.device. */
    private static int family() {
        final String code = hardwareCode();
        if (code.startsWith("G928")) return S6_EDGE_PLUS;
        if (code.startsWith("G925")) return S6_EDGE;
        if (code.startsWith("G920")) return S6;
        final String device = device();
        if (device.startsWith("zenlte")) return S6_EDGE_PLUS;
        if (device.startsWith("zerolte")) return S6_EDGE;
        return S6;
    }

    /** "Samsung Galaxy S6", "Samsung Galaxy S6 edge" or "Samsung Galaxy S6 edge+". */
    public static String name() {
        switch (family()) {
            case S6_EDGE_PLUS:
                return "Samsung Galaxy S6 edge+";
            case S6_EDGE:
                return "Samsung Galaxy S6 edge";
            default:
                return "Samsung Galaxy S6";
        }
    }

    /** Model number: SM-G920F, SM-G925F, SM-G928F... from the phone, else the device tree's. */
    public static String modelNumber() {
        final String code = hardwareCode();
        if (!code.isEmpty()) {
            return "SM-" + code;
        }
        return SystemProperties.get("ro.kiroos.model", android.os.Build.MODEL);
    }

    /** Name and model number, e.g. "Samsung Galaxy S6 edge (SM-G925F)". */
    public static String displayName() {
        return name() + " (" + modelNumber() + ")";
    }

    /** The panel: 5.1" on the S6 and S6 edge, 5.7" on the S6 edge+, all 1440 × 2560. */
    public static String displaySpec() {
        if (family() == S6_EDGE_PLUS) {
            return "5.7\" Super AMOLED dual edge, 1440 × 2560 (518 ppi)";
        }
        if (isEdge()) {
            return "5.1\" Super AMOLED dual edge, 1440 × 2560 (577 ppi)";
        }
        return "5.1\" Super AMOLED, 1440 × 2560 (577 ppi)";
    }

    /** True on the dual edge models (S6 edge, S6 edge+), which get the Edge screen settings. */
    public static boolean isEdge() {
        return family() != S6;
    }

    /** Nominal battery capacity in mAh: 2550 (S6), 2600 (S6 edge), 3000 (S6 edge+). */
    public static int batteryCapacityMah(Context context) {
        final int prop = SystemProperties.getInt("ro.kiroos.battery_mah", 0);
        if (prop > 0) {
            return prop;
        }
        return (int) Math.round(new PowerProfile(context).getBatteryCapacity());
    }

    /** RAM the kernel sees (MemTotal), rounded up to the marketed size: 3 GB or 4 GB. */
    public static int ramGigabytes() {
        final long kb = memTotalKb();
        if (kb <= 0) {
            return SystemProperties.getInt("ro.kiroos.ram_gb", 0);
        }
        // MemTotal excludes the kernel and carveouts (2.7 GB on a 3 GB S6, 3.6 GB on a 4 GB edge+)
        return (int) Math.ceil(kb / (1024.0 * 1024.0));
    }

    /** MemTotal from /proc/meminfo in kB, or -1. */
    public static long memTotalKb() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    final String[] parts = line.split("\\s+");
                    return Long.parseLong(parts[1]);
                }
            }
        } catch (IOException | NumberFormatException | ArrayIndexOutOfBoundsException e) {
            // fall through
        }
        return -1;
    }
}
