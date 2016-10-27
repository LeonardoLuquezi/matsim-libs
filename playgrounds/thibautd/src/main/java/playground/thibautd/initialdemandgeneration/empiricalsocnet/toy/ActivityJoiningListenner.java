/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package playground.thibautd.initialdemandgeneration.empiricalsocnet.toy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Counter;
import playground.thibautd.initialdemandgeneration.empiricalsocnet.framework.AutocloserModule;
import playground.thibautd.initialdemandgeneration.empiricalsocnet.framework.Ego;
import playground.thibautd.initialdemandgeneration.empiricalsocnet.framework.SocialNetworkSampler;

import java.io.BufferedWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author thibautd
 */
@Singleton
public class ActivityJoiningListenner implements AutoCloseable {
	private final Map<Id<Person>, Set<Clique>> cliquesPerPerson = new HashMap<>();

	private final String outputFile;

	@Inject
	public ActivityJoiningListenner(
			final Config config,
			final SocialNetworkSampler sampler,
			final AutocloserModule.Closer closer ) {
		this.outputFile = config.controler().getOutputDirectory()+"/allocatedFriendsAndDistances.dat";
		sampler.addCliqueListener( this::notifyClique );
		closer.add( this );
	}

	@Override
	public void close() throws Exception {
		try ( final BufferedWriter writer = IOUtils.getBufferedWriter( outputFile ) ) {
			writer.write( "egoId\tgroupId\tdistanceToCenter" );

			final Counter counter = new Counter( "select joint activity participants # " );
			int groupId = 0;
			while ( !cliquesPerPerson.isEmpty() ) {
				counter.incCounter();
				groupId++;
				final Clique clique =
						cliquesPerPerson.values().stream()
								.findAny()
								.get()
								.stream()
								.min( (c1,c2) -> Double.compare( c1.avgDistanceToCenter ,c2.avgDistanceToCenter ) )
								.get();

				clique.egos.stream()
						.map( Ego::getId )
						.forEach( cliquesPerPerson::remove );

				for ( Ego ego : clique.egos ) {
					writer.write( ego.getId()+"\t"+groupId+"\t"+clique.avgDistanceToCenter );
				}
			}
			counter.printCounter();
		}
	}

	public void notifyClique( final Set<Ego> egos ) {
		final Clique c = new Clique( egos );
		for ( Ego e : egos ) {
			MapUtils.getSet( e.getId() , cliquesPerPerson ).add( c );
		}
	}

	private static class Clique {
		private final Set<Ego> egos;
		private final double avgDistanceToCenter;

		public Clique( final Set<Ego> egos ) {
			this.egos = egos;
			final Coord center =
					egos.stream()
							.map( Ego::getPerson )
							.map( p -> (Coord) p.getCustomAttributes().get( "coord" ) )
							.reduce( (c1,c2) -> new Coord( c1.getX() + c2.getX() , c1.getY() + c2.getY() ) )
							.map( c -> new Coord( c.getX() / egos.size() , c.getY() / egos.size() ) )
							.get();
			this.avgDistanceToCenter =
					egos.stream()
							.map( Ego::getPerson )
							.map( p -> (Coord) p.getCustomAttributes().get( "coord" ) )
							.mapToDouble( c -> CoordUtils.calcEuclideanDistance( c , center ) )
							.average()
							.getAsDouble();
		}
	}
}