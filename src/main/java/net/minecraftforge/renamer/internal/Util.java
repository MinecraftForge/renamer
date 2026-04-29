/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class Util {
    public static void forZip(ZipFile zip, IOConsumer<ZipEntry> consumer) throws IOException {
        for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
            consumer.accept(entries.nextElement());
        }
    }

    @FunctionalInterface
    public static interface IOConsumer<T> {
        public void accept(T value) throws IOException;
    }

    public static byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return output.toByteArray();
    }

    public static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buf = new byte[0x100];
        int cnt = 0;
        while ((cnt = input.read(buf, 0, buf.length)) != -1) {
            output.write(buf, 0, cnt);
        }
    }

    public static String nameToBytecode(Class<?> cls) {
        return cls == null ? null : cls.getName().replace('.', '/');
    }
    public static String nameToBytecode(String cls) {
        return cls == null ? null : cls.replace('.', '/');
    }


    static List<String> tokenize(String line) {
        if (line.length() == 0)
            return Collections.emptyList();

        // This is unrolled instead of using String.split because that uses RegEx in the back end which is slow
        List<String> ret = new ArrayList<>();
        int start = 0;
        for (int x = 0; x < line.length(); x++) {
            char c = line.charAt(x);
            if (c == ' ' || c == '\t') {
                if (start == x)
                    ret.add("");
                else
                    ret.add(line.substring(start, x));

                // Skip all consecutive whitespace
                do {
                    x++;
                    if (x == line.length())
                        break;
                    c = line.charAt(x);
                } while (c == ' ' || c == '\t');

                if (c == '#')
                    break;

                start = x--;
            } else if (x == line.length() - 1) {
                ret.add(line.substring(start));
            } else if (c == '#') {
                if (start != x)
                    ret.add(line.substring(start, x));
                break;
            }
        }
        return ret;
    }
}
