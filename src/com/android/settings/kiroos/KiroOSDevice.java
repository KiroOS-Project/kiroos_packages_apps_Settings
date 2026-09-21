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

    /** True on the dual edge models (S6 edge, S6 edge+), which get the Edge screen settings. */
    public static boolean isEdge() {
        final String prop = SystemProperties.get("ro.kiroos.edge", "");
        if (!prop.isEmpty()) {
            return "1".equals(prop);
        }
        final String device = device();
        return device.startsWith("zerolte") || device.startsWith("zenlte");
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
