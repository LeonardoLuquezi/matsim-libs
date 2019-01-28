/**
 * 
 */
package org.matsim.core.utils.geometry;

import com.vividsolutions.jts.geom.*;
import org.geotools.geometry.jts.GeometryBuilder;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.opengis.feature.simple.SimpleFeature;

import java.util.*;
import java.util.Map.Entry;

/**
 * @author kainagel
 *
 */
public class GeometryUtils {
	private GeometryUtils() {} // do not instantiate

	/**
	 * Method to find all links in {@link Network} that intersect a given {@link Link}.  Convenience method that
	 * only uses MATSim objects.
	 * 
	 * @param link
	 * @param network
	 * @return
	 */
	public static List<Link> findIntersectingLinks( Link link, final Network network ) {
		LineString segment = GeometryUtils.createGeotoolsLineString(link) ;
		return GeometryUtils.findIntersectingLinks(segment, network) ;
	}

	/**
	 * Method to find all links in {@link Network} that intersect a given {@link LineString}.
	 * 
	 * @param lineString
	 * @param network
	 * @return
	 * 
	 * @see {@link GeometryUtilsTest#testIntersectingLinks()}
	 */
	public static List<Link> findIntersectingLinks(LineString lineString, final Network network) {
		// yy One could probably improve this method by using the (already existing) link quadtree to look only at
		// those links that are in the bounding box.  kai, oct'17
		
		// convert matsim links into geotools line strings:
		Map<Link,LineString> segments = new LinkedHashMap<>() ;
		for ( Link link : network.getLinks().values() ) {
			LineString theSegment = GeometryUtils.createGeotoolsLineString(link);
			segments.put( link, theSegment ) ;
		}
	
		// find the intersecting segments:
		List<Link> resultList = new ArrayList<>();
		for ( Entry<Link, LineString> entry : segments.entrySet() ) {
			if( lineString.intersects( entry.getValue() ) ){
				resultList.add( entry.getKey() ) ;
			}
		}
		return resultList;
	}

	/**
	 * Helper method for type conversion.
	 * 
	 * @param link
	 * @return
	 */
	public static LineString createGeotoolsLineString(Link link) {
		Coordinate fromCoord = MGC.coord2Coordinate( link.getFromNode().getCoord() ) ;
		Coordinate toCoord = MGC.coord2Coordinate( link.getToNode().getCoord() ) ;
		LineString theSegment = new GeometryFactory().createLineString(new Coordinate[]{ fromCoord, toCoord });
		return theSegment;
	}
	
	public static Point createGeotoolsPoint(Coord coord ) {
		Coordinate coordinate = MGC.coord2Coordinate(coord) ;
		Point point = new GeometryFactory().createPoint( coordinate ) ;
		return point ;
	}
	
	public static Polygon createGeotoolsPolygon(List<Coord> coords ) {
		Coordinate[] coordinates = new Coordinate[coords.size()] ;
		int ii=0 ;
		for ( Coord coord : coords ) {
			coordinates[ii] = new Coordinate(coord.getX(), coord.getY()); ;
			ii++ ;
		}
		return new GeometryFactory().createPolygon(coordinates);
	}

	public static Point getRandomPointInFeature( Random rnd, SimpleFeature ft ) {
		Gbl.assertNotNull(ft );
		Point p = null;
		double x, y;
		// generate a random point until a point inside the feature geometry is found
		do {
			x = ft.getBounds().getMinX() + rnd.nextDouble() * (ft.getBounds().getMaxX() - ft.getBounds().getMinX());
			y = ft.getBounds().getMinY() + rnd.nextDouble() * (ft.getBounds().getMaxY() - ft.getBounds().getMinY());
			p = MGC.xy2Point(x, y);
		} while ( ! (((Geometry) ft.getDefaultGeometry()).contains(p)) );
		return p;
	}


}
