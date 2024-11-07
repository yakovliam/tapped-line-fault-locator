package com.yakovliam;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FaultLocator {

  private static final Logger LOGGER = LoggerFactory.getLogger(FaultLocator.class);

  public Set<Point> locateFault(EdgeNodeTreeConstructor.EdgeNode rootNode, double distanceMeters) {
    // walk the tree and locate the multiple possible fault locations
    Set<Point> faultLocations = new CopyOnWriteArraySet<>();
    walkTree(rootNode, distanceMeters, faultLocations);

    return faultLocations;
  }

  private void walkTree(EdgeNodeTreeConstructor.EdgeNode node, double distanceToWalkRemaining,
                        Set<Point> faultLocations) {
    LOGGER.info("Walking tree with distance: {}", distanceToWalkRemaining);
    LOGGER.info("This edge length: {}",
        GeometryUtil.getLengthInMeters(node.getEdge().getLineString()));

    // if we have no more distance to walk, return
    if (distanceToWalkRemaining <= 0) {
      return;
    }

    // if the distance left is less than the length of the edge, we can calculate the point
    // on this edge and add it to the fault locations
    if (distanceToWalkRemaining <= GeometryUtil.getLengthInMeters(node.getEdge().getLineString())) {
      Point point =
          calculatePointOnLineString(node.getEdge().getLineString(), distanceToWalkRemaining);

      if (point == null) {
        LOGGER.warn("Point is null");
        return;
      }

      faultLocations.add(point);
      return;
    }

    // if we have more distance to walk than the length of the edge, we need to walk the children
    // edges
    for (EdgeNodeTreeConstructor.EdgeNode child : node.getChildren()) {
      walkTree(child,
          distanceToWalkRemaining - GeometryUtil.getLengthInMeters(node.getEdge().getLineString()),
          faultLocations);
    }
  }

  private Point calculatePointOnLineString(LineString lineString, double distanceMeters) {
    return GeometryUtil.locateAlong(lineString, distanceMeters);
  }
}
