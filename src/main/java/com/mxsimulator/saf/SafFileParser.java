package com.mxsimulator.saf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class SafFileParser {
    public static byte[] parse(byte[] rawBytes) {
        int index = 0;
        StringBuilder headerContent = new StringBuilder();
        // Build headers for file size/paths
        while ((char) rawBytes[index] != "-".charAt(0)) {
            headerContent.append((char) rawBytes[index]);
            index += 1;
        }
        // now building content of files
        // skip - and \n
        index += 2;
        List<SafFile> safFileList = new ArrayList<>();
        String[] headerLines = headerContent.toString().split("\n");
        for (String line : headerLines) {
            // Determine filesize/path
            String[] pieces = line.replace("\n", "").split(" ");
            int byteCount = Integer.parseInt(pieces[0]);
            String path = String.join("", Arrays.asList(Arrays.copyOfRange(pieces, 1, pieces.length)));
            // Read bytes based on filesize
            byte[] bytes = Arrays.copyOfRange(rawBytes, index, index + byteCount);
            SafFile safFile = SafFile.builder()
                    .byteCount(byteCount)
                    .bytes(bytes)
                    .path(path)
                    .build();
            safFileList.add(safFile);
            index += byteCount;
        }

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
                for (SafFile safFile : safFileList) {
                    ZipEntry zipEntry = new ZipEntry(safFile.getPath());
                    zipOutputStream.putNextEntry(zipEntry);
                    zipOutputStream.write(safFile.getBytes());
                    zipOutputStream.closeEntry();
                }
            }

            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static byte[] create(byte[] fileBytes) {
        List<SafFile> safFileList = new ArrayList<>();
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(fileBytes);
             ZipInputStream zipInputStream = new ZipInputStream(byteArrayInputStream)) {
            ZipEntry zipEntry;

            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                ByteArrayOutputStream streamBuilder = new ByteArrayOutputStream();
                int bytesRead;
                byte[] tempBuffer = new byte[8192 * 2];

                while ((bytesRead = zipInputStream.read(tempBuffer)) != -1) {
                    streamBuilder.write(tempBuffer, 0, bytesRead);
                }

                SafFile safFile = SafFile.builder()
                        .byteCount((int) zipEntry.getSize())
                        .bytes(streamBuilder.toByteArray())
                        .path(zipEntry.getName())
                        .build();
                safFileList.add(safFile);
            }
            // Build saf headers
            StringBuilder stringBuilder = new StringBuilder();
            for (SafFile safFile : safFileList) {
                stringBuilder.append(safFile.getByteCount());
                stringBuilder.append(" ");
                stringBuilder.append(safFile.getPath());
                stringBuilder.append("\n");
            }
            stringBuilder.append("-\n");
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                outputStream.write(stringBuilder.toString().getBytes());

                for (SafFile safFile : safFileList) {
                    outputStream.write(safFile.getBytes());
                }

                return outputStream.toByteArray();
            }
        } catch (IOException e) {
            return null;
        }
    }
}
