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

import gov.nasa.worldwind.cache.BasicDataFileStore;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.globes.Earth;
import gov.nasa.worldwind.globes.Globe;
import gov.nasa.worldwind.layers.BasicLayerFactory;
import gov.nasa.worldwind.layers.BasicTiledImageLayer;
import gov.nasa.worldwind.layers.BasicTiledImageLayerBulkDownloader;
import gov.nasa.worldwind.retrieve.Progress;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.StringTokenizer;

public class BulkDownloadCli {

    private static class FileUtils extends SimpleFileVisitor<Path> {

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attr) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException ex) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }

        static void deleteDirectory(File f) throws IOException {
            Files.walkFileTree(Path.of(f.getAbsolutePath()), new FileUtils());
        }
    }

    private static final String VERSION = "1.1";
    private static final long OSM_AVERAGE_TILE_SIZE = 15000;

    public static String makeSizeDescription(long size) {
        double sizeInMegaBytes = size / 1024 / 1024;
        if (sizeInMegaBytes < 1024) {
            return String.format("%,.1f MB", sizeInMegaBytes);
        } else if (sizeInMegaBytes < 1024 * 1024) {
            return String.format("%,.1f GB", sizeInMegaBytes / 1024);
        }
        return String.format("%,.1f TB", sizeInMegaBytes / 1024 / 1024);
    }

    public static void cliDownload(String[] args) {
        System.out.println("WorldWind Bulk Download Tool v" + VERSION);
        Globe globe = new Earth();
        String usage = "Usage: BulkDownload -sector [centerLat,centerLon,radius meters] -path [path for download] -estimate";
        String outputPath = null;
        Sector sector = null;
        boolean estimate = false;
        int i = 0;
        while (i < args.length) {
            switch (args[i]) {
                case "-help":
                    System.out.println(usage);
                    return;
                case "-sector":
                    i++;
                    StringTokenizer st = new StringTokenizer(args[i], ",");
                    if (st.countTokens() != 3) {
                        System.out.println("Error: Invalid sector specification.");
                        System.out.println(usage);
                        return;
                    }
                    double lat = Double.parseDouble(st.nextToken());
                    double lon = Double.parseDouble(st.nextToken());
                    double radius = Double.parseDouble(st.nextToken());
                    sector = Sector.boundingSector(globe, LatLon.fromDegrees(lat, lon), radius);
                    break;
                case "-path":
                    i++;
                    outputPath = args[i];
                    break;
                case "-estimate":
                    estimate = true;
                    break;
                default:
                    System.out.println("Unknown argument: " + args[i]);
                    System.out.println(usage);
                    break;
            }
            i++;
        }

        if (sector == null) {
            System.out.println("Error: Sector not specified.");
            System.out.println(usage);
            return;
        }

        System.out.println("Sector: " + sector);
        BasicLayerFactory factory = new BasicLayerFactory();
        BasicTiledImageLayer layer = (BasicTiledImageLayer) factory.createFromConfigSource("config/Earth/OpenStreetMap2.xml", null);
        layer.setAverageFileSize(OSM_AVERAGE_TILE_SIZE);
        if (estimate) {
            System.out.println(layer.getName() + " estimated download size: " + makeSizeDescription(layer.getEstimatedMissingDataSize(sector, 0)));
            return;
        }

        if (outputPath == null) {
            System.out.println("Error: Output path not specified.");
            System.out.println(usage);
            return;
        }

        try {
            String earthPath = outputPath + "/Earth";
            String osmPath = earthPath + "/OpenStreetMap2";
            File previousTilePath = new File(earthPath);
            File previousOsmPath = new File(osmPath);
            if (previousTilePath.exists() && previousTilePath.isDirectory() && previousOsmPath.exists()) {
                FileUtils.deleteDirectory(previousTilePath);
            }

            BasicDataFileStore cache = new BasicDataFileStore(new File(outputPath));
            BasicTiledImageLayerBulkDownloader downloadThread = (BasicTiledImageLayerBulkDownloader) layer.makeLocal(sector, 0, cache, null);
            downloadThread.setAverageFileSize(OSM_AVERAGE_TILE_SIZE);
            Progress progress = downloadThread.getProgress();
            System.out.println("Downloading " + layer.getName() + " Tiles");
            int lastPercent = -1;
            long lastCurrentSize = -1;
            while (downloadThread.isAlive()) {
                int percent = 0;
                if (progress.getTotalCount() > 0) {
                    percent = (int) ((float) progress.getCurrentCount() / progress.getTotalCount() * 100f);
                }
                if (lastPercent != percent || lastCurrentSize != progress.getCurrentSize()) {
                    lastPercent = percent;
                    lastCurrentSize = progress.getCurrentSize();
                    String text = percent + "% ";
                    text += " (" + makeSizeDescription(lastCurrentSize)
                            + " / " + makeSizeDescription(progress.getTotalSize())
                            + ")";
                    System.out.println(text);
                }
                Thread.sleep(1000);
            }
            int nRemoveLevels = 9;
            for (i = 0; i < nRemoveLevels; i++) {
                File levelPath = new File(osmPath + "/" + i);
                if (levelPath.exists() && levelPath.isDirectory()) {
                    FileUtils.deleteDirectory(levelPath);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {
         cliDownload(args);
    }
}
