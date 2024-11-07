package com.yakovliam;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.GeodeticCalculator;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeometryUtil {

  private static final Logger LOG = LoggerFactory.getLogger(GeometryUtil.class);

  private static final int SRID = 4326;
  private static final PrecisionModel PRECISION_MODEL = new PrecisionModel();

  private static CoordinateReferenceSystem crs = DefaultGeographicCRS.WGS84;
  private static GeometryFactory geomFact = new GeometryFactory(PRECISION_MODEL, SRID);


  /**
   * Find the distance along the geometry.
   * <p>
   * NOTE: currently only works for lineString. Add others as needed/
   *
   * @param geometry         the geometry type to "walk"
   * @param distanceInMeters distance to "walk" along geometry
   * @return the point along the geometry or null if we can't figure it out.
   */
  public static Point locateAlong(Geometry geometry, double distanceInMeters) {
    switch (geometry.getGeometryType()) {
      case "LineString":
        return locateAlongLineString((LineString) geometry, distanceInMeters);
      default:
        LOG.info(geometry.getGeometryType() + " is not handled.");
        return null;
    }
  }

  /**
   * Walk the line string and find the point d distance along it.
   *
   * @param lineStr  linestring to "walk" should be in order from station
   * @param distance how far along the fault is
   * @return the location of the fault or null if the distance is longer than the linestring.
   */
  private static Point locateAlongLineString(LineString lineStr, double distance) {
    try {
      //FIXME using the default WGS84 (SRID 4326). This should really read and match what
      //is stored in the geometry....
      GeodeticCalculator gc = new GeodeticCalculator(crs);

//      lineStr = flipLineStringXY(lineStr);

      double distLeft = distance;
      Point lastPoint = lineStr.getStartPoint();

      for (int i = 1; i < lineStr.getNumPoints(); i++) {
        Point p = lineStr.getPointN(i);

        gc.setStartingPosition(JTS.toDirectPosition(lastPoint.getCoordinate(), crs));
        gc.setDestinationPosition(JTS.toDirectPosition(p.getCoordinate(), crs));

        double d2 = gc.getOrthodromicDistance();
        LOG.debug("distance from: " + lastPoint + " to " + p + " is " + d2);
        if (distLeft - d2 <= 0) {
          double azimuth = gc.getAzimuth();
          gc.setDirection(azimuth, distLeft);
          Point resultP = JTS.toGeometry(gc.getDestinationPosition());
          resultP.setSRID(SRID);
          return resultP;
        } else {
          distLeft -= d2;
          lastPoint = p;
        }
      }

      LOG.info("Not able to compute location. Distance: " + distance +
          " is greater than geometry length.");
    } catch (TransformException te) {
      LOG.error("Error transforming point.", te);
    }

    return null;
  }

  /**
   * Flip lineString x and y coordinates. Lat/lon need to be lon/lat for math to work.
   *
   * @param lineStr to flip x/y in.
   * @return lineString with x/y flipped.
   */
  private static LineString flipLineStringXY(LineString lineStr) {
    Coordinate[] cords = lineStr.getCoordinates();

    for (int i = 0; i < cords.length; i++) {
      cords[i] = new Coordinate(cords[i].y, cords[i].x);
    }

    return geomFact.createLineString(cords);
  }

  private static Point flipPointXY(Point p) {
    return geomFact.createPoint(new Coordinate(p.getY(), p.getX()));
  }

  /**
   * @param geometry geometry to find length of
   * @return the length or null if not computable.
   */
  public static Double getLengthInMeters(Geometry geometry) {
    if (geometry.getGeometryType().equals("LineString")) {
      return getLineStringLength((LineString) geometry);
    }
    throw new IllegalArgumentException(geometry.getGeometryType() + " is not handled.");
  }

  /**
   * FIXME - results I'm getting from this are completely bogus. Line geometry
   * from the simulator has calculated distances >100 km when it should be <20km.
   *
   * @param lineStr the LineString
   * @return the length or null if not computable.
   */
  private static Double getLineStringLength(LineString lineStr) {
    //FIXME using the default WGS84 (SRID 4326). This should really read and match what
    //is stored in the geometry....
    GeodeticCalculator gc = new GeodeticCalculator(crs);
    double distance = 0.0;
    Point lastPoint = lineStr.getStartPoint();

    try {
      for (int i = 1; i < lineStr.getNumPoints(); i++) {
        Point p = lineStr.getPointN(i);

        gc.setStartingPosition(JTS.toDirectPosition(lastPoint.getCoordinate(), crs));
        gc.setDestinationPosition(JTS.toDirectPosition(p.getCoordinate(), crs));

        distance += gc.getOrthodromicDistance();
      }
    } catch (TransformException te) {
      LOG.warn("Transform Exception calculating LineString length.", te);
      return null;
    }

    return distance;
  }
}