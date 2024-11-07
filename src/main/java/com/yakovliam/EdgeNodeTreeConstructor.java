package com.yakovliam;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.wololo.geojson.GeoJSON;
import org.wololo.jts2geojson.GeoJSONWriter;

/**
 * Context:
 * We are operating under the assumption that the MultiLineString contains many LineStrings,
 * all are connected somehow.
 * E.g. The end of line A splits into the beginning of line B and line C,
 * and B splits into D and E, and so on.
 * |
 * A
 * |
 * / \
 * B  C
 * |  |
 */
public class EdgeNodeTreeConstructor {

  private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

  private static final Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(EdgeNodeTreeConstructor.class);

  public EdgeNode constructEdgeNodeTree(MultiLineString geometry, Point startingPoint) {
    /*
     * 1. Node the LineStrings
     *    - This ensures that the LineStrings do not cross and are properly split
     */

    List<LineString> lineStrings = new ArrayList<>();
    for (int i = 0; i < geometry.getNumGeometries(); i++) {
      LineString lineString = (LineString) geometry.getGeometryN(i);
      lineStrings.add(lineString);
    }

    // find the line that contains a start/end point closest to the starting point
    Point closestPoint = null;
    double closestDistance = Double.MAX_VALUE;

    for (LineString lineString : lineStrings) {
      Point start = lineString.getFactory().createPoint(lineString.getCoordinateN(0));
      Point end = lineString.getFactory()
          .createPoint(lineString.getCoordinateN(lineString.getNumPoints() - 1));

      double startDistance = start.distance(startingPoint);
      double endDistance = end.distance(startingPoint);

      if (startDistance < closestDistance) {
        closestPoint = start;
        closestDistance = startDistance;
      }

      if (endDistance < closestDistance) {
        closestPoint = end;
        closestDistance = endDistance;
      }
    }

    if (closestPoint == null) {
      throw new RuntimeException("No closest point found");
    }

    LOGGER.info("Closest point to start: {}", closestPoint);

    // find the lineString with the closest point
    LineString closestLineString = null;
    for (LineString lineString : lineStrings) {
      Point start = lineString.getFactory().createPoint(lineString.getCoordinateN(0));
      Point end = lineString.getFactory()
          .createPoint(lineString.getCoordinateN(lineString.getNumPoints() - 1));

      if (start.equals(closestPoint) || end.equals(closestPoint)) {
        closestLineString = lineString;
        break;
      }
    }

    if (closestLineString == null) {
      throw new RuntimeException("No closest line string found");
    }

    // if the closest point to the starting point is the end
    // of the line, then reverse the line
    if (closestLineString.getCoordinateN(closestLineString.getNumPoints() - 1)
        .equals(closestPoint.getCoordinate())) {
      closestLineString = closestLineString.reverse();
    }

    // start the edge tree
    EdgeNode edgeNode = new EdgeNode(new Edge(closestLineString));
    List<LineString> remainingLineStrings = new CopyOnWriteArrayList<>(lineStrings);
    remainingLineStrings.remove(closestLineString);

    EdgeNode output = constructEdgeTree(edgeNode, remainingLineStrings);

    debugPrintEdgeTree(output, 0);

    boolean passes = new TappedLineRulesTester().passes(geometry, output);
    if (!passes) {
      throw new RuntimeException("Edge tree does not pass rules");
    } else {
      LOGGER.info("Edge tree passes rules");
    }

    return output;
  }

  private void debugPrintEdgeTree(EdgeNode edgeNode, int depth) {
    String stringBuilder = "  ".repeat(Math.max(0, depth)) + edgeNode.getEdge().getLineString();

    LOGGER.info(stringBuilder);

    for (EdgeNode child : edgeNode.getChildren()) {
      debugPrintEdgeTree(child, depth + 1);
    }
  }

  private EdgeNode constructEdgeTree(EdgeNode currentRoot, List<LineString> remainingLineStrings) {
    // match any lineStrings with a start/end point that are the same as the currentRoot edge
    // end point.
    // If found, add them as children to the currentRoot edge

    // reminder: may need to reverse the lineString if the end point is the same as the start point

    for (LineString lineString : remainingLineStrings) {
      Point start = lineString.getFactory().createPoint(lineString.getCoordinateN(0));
      Point end = lineString.getFactory()
          .createPoint(lineString.getCoordinateN(lineString.getNumPoints() - 1));

      if (currentRoot.getEdge().getEnd().equals(start)) {
        EdgeNode edgeNode = new EdgeNode(new Edge(lineString));
        currentRoot.addChild(edgeNode);
        remainingLineStrings.remove(lineString);
        constructEdgeTree(edgeNode, remainingLineStrings);
      }

      if (currentRoot.getEdge().getEnd().equals(end)) {
        EdgeNode edgeNode = new EdgeNode(new Edge(lineString.reverse()));
        currentRoot.addChild(edgeNode);
        remainingLineStrings.remove(lineString);
        constructEdgeTree(edgeNode, remainingLineStrings);
      }
    }

    return currentRoot;
  }

  private void writeToFile(Geometry geometry) {
    GeoJSONWriter writer = new GeoJSONWriter();
    GeoJSON geoJson = writer.write(geometry);
    String json = geoJson.toString();

    // write to file
    File file = new File("output.geojson");
    try {
      java.io.FileWriter fileWriter = new java.io.FileWriter(file);
      fileWriter.write(json);
      fileWriter.close();
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static class Edge {

    private final LineString lineString;

    private final Point start;

    private final Point end;

    public Edge(LineString lineString) {
      this.lineString = lineString;
      this.start = lineString.getFactory().createPoint(lineString.getCoordinateN(0));
      this.end = lineString.getFactory()
          .createPoint(lineString.getCoordinateN(lineString.getNumPoints() - 1));
    }

    public LineString getLineString() {
      return lineString;
    }

    public Point getStart() {
      return start;
    }

    public Point getEnd() {
      return end;
    }
  }

  public static class EdgeNode {

    private final Edge edge;

    private final List<EdgeNode> children;

    public EdgeNode(Edge edge) {
      this.edge = edge;
      this.children = new CopyOnWriteArrayList<>();
    }

    public Edge getEdge() {
      return edge;
    }

    public java.util.List<EdgeNode> getChildren() {
      return children;
    }

    public void addChild(EdgeNode edgeNode) {
      children.add(edgeNode);
    }
  }
}
