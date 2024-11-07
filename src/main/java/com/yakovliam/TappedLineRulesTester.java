package com.yakovliam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.slf4j.Logger;

public class TappedLineRulesTester {

  private static final Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(TappedLineRulesTester.class);

  public boolean passes(MultiLineString geometry, EdgeNodeTreeConstructor.EdgeNode root) {
    // check for closed loops
    if (containsClosedLoop(geometry)) {
      LOGGER.error("Geometry contains a closed loop");
      return false;
    }

    // check that LineStrings are connected
    if (!connectedLineStrings(geometry)) {
      LOGGER.error("LineStrings are not connected");
      return false;
    }

    // check that LineStrings do not have shared points unless the points are at either end of the LineString
    if (!sharedPoints(geometry)) {
      LOGGER.error("LineStrings have shared points that are not at the end of the LineString");
      return false;
    }

    // check that the root node is not null
    if (root == null) {
      LOGGER.error("Root node is null");
      return false;
    }

    return true;
  }

  /**
   * Check for closed loops, Log message if we find a closed loop
   *
   * @param geometry MultiLineString
   * @return boolean
   */
  private boolean containsClosedLoop(MultiLineString geometry) {
    List<LineString> lineStrings = new ArrayList<>();
    for (int i = 0; i < geometry.getNumGeometries(); i++) {
      LineString lineString = (LineString) geometry.getGeometryN(i);
      lineStrings.add(lineString);
    }

    for (LineString lineString : lineStrings) {
      if (lineString.isClosed()) {
        return true;
      }
    }

    return false;
  }

  /**
   * LineStrings must be connected at least one of it's end points. If there is more then one line string then all LineStrings must be connected. Log error message if there are LineStrings that are not connected.
   *
   * @param geometry MultiLineString
   * @return boolean
   */
  private boolean connectedLineStrings(MultiLineString geometry) {
    List<LineString> lineStrings = new ArrayList<>();
    for (int i = 0; i < geometry.getNumGeometries(); i++) {
      LineString lineString = (LineString) geometry.getGeometryN(i);
      lineStrings.add(lineString);
    }

    boolean foundLineThatIsNotConnected = false;

    for (LineString lineString : lineStrings) {
      // check if either the start/end point is shared with another line string's start/end point
      boolean thisLineStringIsConnected = false;

      for (LineString otherLineString : lineStrings) {
        if (lineString.equals(otherLineString)) {
          continue;
        }

        if (lineString.getStartPoint().equals(otherLineString.getStartPoint()) ||
            lineString.getStartPoint().equals(otherLineString.getEndPoint()) ||
            lineString.getEndPoint().equals(otherLineString.getStartPoint()) ||
            lineString.getEndPoint().equals(otherLineString.getEndPoint())) {
          thisLineStringIsConnected = true;
          break;
        }
      }

      if (!thisLineStringIsConnected) {
        foundLineThatIsNotConnected = true;
        break;
      }
    }

    return !foundLineThatIsNotConnected;
  }

  /**
   * check that LineStrings do not have shared points unless the points are at either end of the LineString.
   * Log error message if there are shared points that are not at the end of the LineString.
   *
   * @param geometry MultiLineString
   * @return boolean
   */
  private boolean sharedPoints(MultiLineString geometry) {
    List<LineString> lineStrings = new ArrayList<>();
    for (int i = 0; i < geometry.getNumGeometries(); i++) {
      LineString lineString = (LineString) geometry.getGeometryN(i);
      lineStrings.add(lineString);
    }

    boolean foundSharedPointNotAtEnd = false;

    for (LineString lineString : lineStrings) {
      // check if any of the points in this lineString are contained
      // within any other lineStrings, not at that lineString's start/end
      for (LineString otherLineString : lineStrings) {
        if (lineString.equals(otherLineString)) {
          continue;
        }

        for (Coordinate coordinate : lineString.getCoordinates()) {
          if (Arrays.asList(otherLineString.getCoordinates()).contains(coordinate) &&
              !coordinate.equals(otherLineString.getCoordinateN(0)) && !coordinate.equals(
              otherLineString.getCoordinateN(otherLineString.getNumPoints() - 1))) {
            foundSharedPointNotAtEnd = true;
            break;
          }
        }
      }

      if (foundSharedPointNotAtEnd) {
        break;
      }
    }

    return !foundSharedPointNotAtEnd;
  }
}
