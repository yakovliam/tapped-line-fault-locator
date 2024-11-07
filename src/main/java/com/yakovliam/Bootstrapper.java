package com.yakovliam;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.wololo.jts2geojson.GeoJSONReader;
import org.wololo.jts2geojson.GeoJSONWriter;

public class Bootstrapper {
  private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(Bootstrapper.class);

  private static final Point STARTING_POINT =
      new GeometryFactory().createPoint(new Coordinate(-111.94005548, 33.48386668));

  public static void main(String[] args) throws IOException {
    // src/main/resources/test.geojson
    InputStream inputStream =
        Bootstrapper.class.getResourceAsStream("/papago-buttes-scottsdale.geojson");
    InputStreamReader streamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
    BufferedReader reader = new BufferedReader(streamReader);
    StringBuilder stringBuilder = new StringBuilder();
    for (String line; (line = reader.readLine()) != null; ) {
      stringBuilder.append(line).append("\n");
    }

    String geoJson = stringBuilder.toString();

    GeoJSONReader geoJSONReader = new GeoJSONReader();
    Geometry geometry = geoJSONReader.read(geoJson);

    LOGGER.info("Geometry: {}", geometry);

    if (geometry == null) {
      throw new RuntimeException("Geometry is null");
    }

    if (!(geometry instanceof org.locationtech.jts.geom.MultiLineString)) {
      throw new IllegalArgumentException("Geometry is not a MultiLineString");
    }

    EdgeNodeTreeConstructor.EdgeNode root = new EdgeNodeTreeConstructor().constructEdgeNodeTree(
        (org.locationtech.jts.geom.MultiLineString) geometry, STARTING_POINT);

    double startDistance = 0.0;
    double endDistance = 1000.0;
    double increment = 100.0;

    FaultLocator faultLocator = new FaultLocator();
    // loop from start to end with increment, create a file for each
    for (double distance = startDistance; distance <= endDistance; distance += increment) {
      Set<Point> faultLocations = faultLocator.locateFault(root, distance);

      if (faultLocations.isEmpty()) {
        LOGGER.info("No fault locations found for distance: {}", distance);
        continue;
      }

      for (Point faultLocation : faultLocations) {
        LOGGER.info("Fault location: {}", faultLocation);
      }

      MultiPoint multiPoint = JTSFactoryFinder.getGeometryFactory()
          .createMultiPoint(faultLocations.toArray(new Point[0]));

      GeoJSONWriter writer = new GeoJSONWriter();
      String faultLocationsGeoJson = writer.write(multiPoint).toString();

      try {
        // write to file
        String fileName = "fault-locations-" + distance + ".geojson";
        LOGGER.info("Writing to file: {}", fileName);
        // write to file
        File file = new File(fileName);
        Files.write(file.toPath(), faultLocationsGeoJson.getBytes());
      } catch (Exception e) {
        LOGGER.error("Failed to write to file", e);
      }
    }
  }
}
