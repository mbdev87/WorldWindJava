/*
 * Copyright 2006-2009, 2017, 2020 United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 * 
 * The NASA World Wind Java (WWJ) platform is licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 * 
 * NASA World Wind Java (WWJ) also contains the following 3rd party Open Source
 * software:
 * 
 *     Jackson Parser – Licensed under Apache 2.0
 *     GDAL – Licensed under MIT
 *     JOGL – Licensed under  Berkeley Software Distribution (BSD)
 *     Gluegen – Licensed under Berkeley Software Distribution (BSD)
 * 
 * A complete listing of 3rd Party software notices and licenses included in
 * NASA World Wind Java (WWJ)  can be found in the WorldWindJava-v2.2 3rd-party
 * notices and licenses PDF found in code directory.
 */
package gov.nasa.worldwindx.tools;

import gov.nasa.worldwind.util.EGM2008;
import gov.nasa.worldwind.util.WWXML;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Exports EGM2008 data as separate latitude row files. Used in cases where
 * EGM2008 access is desired without using a web service.
 */
public class EGM2008Export {

    private static final String VERSION = "1.0";

    public static void cliExport(String[] args) throws IOException {
        System.out.println("WorldWind EGM2008 Export Tool v" + VERSION);
        String usage = "Usage: EGM2008Export -egmPath [path for EGM2008 source file] -outputPath [path for EGM2008 output]";
        String outputPath = null;
        String egmPath = null;

        int i = 0;
        while (i < args.length) {
            switch (args[i]) {
                case "-help":
                    System.out.println(usage);
                    return;
                case "-outputPath":
                    i++;
                    outputPath = args[i];
                    break;
                case "-egmPath":
                    i++;
                    egmPath = args[i];
                    break;
                default:
                    System.out.println("Unknown argument: " + args[i]);
                    System.out.println(usage);
                    break;
            }
            i++;
        }

        if (egmPath == null) {
            System.out.println("Error: Output path not specified.");
            System.out.println(usage);
            return;
        }

        if (outputPath == null) {
            System.out.println("Error: Output path not specified.");
            System.out.println(usage);
            return;
        }

        EGM2008 egm2008Offsets = new EGM2008(egmPath);
        boolean egmAvailable = egm2008Offsets.isEGMDataAvailable();
        if (!egmAvailable) {
            System.out.println("*** EGM 2008 data not available.");
            return;
        }

        Document metaDoc = WWXML.createDocumentBuilder(false).newDocument();
        Element docEl = WWXML.setDocumentElement(metaDoc, "EGM2008");
        WWXML.appendInteger(docEl, "nRowMarkers", EGM2008.N_ROW_MARKERS);
        WWXML.appendInteger(docEl, "nLongitudeCols", EGM2008.N_LONGITUDE_COLS);
        WWXML.appendInteger(docEl, "nLatitudeRows", EGM2008.N_LATITUDE_ROWS);
        WWXML.appendDouble(docEl, "gridResolution", EGM2008.GRID_RESOLUTION);
        WWXML.appendDouble(docEl, "cellArea", EGM2008.CELL_AREA);
        WWXML.appendInteger(docEl, "nLatRowBytes", EGM2008.N_LAT_ROW_BYTES);
        WWXML.saveDocumentToFile(metaDoc, outputPath + "/egm2008.xml");

        byte[] latByteData = new byte[EGM2008.N_LAT_ROW_BYTES * 2];
        ByteBuffer latByteBuffer = ByteBuffer.wrap(latByteData).order(ByteOrder.LITTLE_ENDIAN);
        // Write the data out in pairs for interpolation purposes.
        for (i = 0; i < EGM2008.N_LATITUDE_ROWS; i++) {
            float[][] latRow = egm2008Offsets.getLatRows(i);
            String fileName = String.format("%04d.dat", i);
            try (RandomAccessFile rowFile = new RandomAccessFile(outputPath + "/" + fileName, "rw")) {
                FloatBuffer floatData = latByteBuffer.asFloatBuffer();
                floatData.put(latRow[0]);
                if (i == EGM2008.N_LATITUDE_ROWS - 1) { // no next row.
                    floatData.put(latRow[0]);
                } else {
                    floatData.put(latRow[1]);
                }
                rowFile.write(latByteData);
            }
        }
    }

    public static void main(String[] args) {
        try {
            cliExport(args);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
