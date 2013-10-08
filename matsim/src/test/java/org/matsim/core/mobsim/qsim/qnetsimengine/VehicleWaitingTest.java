/* *********************************************************************** *
 * project: org.matsim.*
 * VehicleWaitingTest.java
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
package org.matsim.core.mobsim.qsim.qnetsimengine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.mobsim.qsim.interfaces.Netsim;
import org.matsim.core.mobsim.qsim.QSimFactory;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordImpl;

/**
 * Tests the behavior of the qsim with agents waiting for vehicles.
 * <br>
 * This is not really a "unit" test anymore (thus it artificially increases test
 * coverage of the qsim), but at least, it checks that recent bugfixes actually
 * fixed something. td, mar. 2013
 *
 * @author thibautd
 */
public class VehicleWaitingTest {
	private static final Logger log =
		Logger.getLogger(VehicleWaitingTest.class);

	@Test
	public void testVehicleWaitingOneLapDoesntFailNoDummies() {
		testVehicleWaitingDoesntFail( 1 , false );
	}

	@Test
	public void testVehicleWaitingOneLapDoesntFailDummies() {
		testVehicleWaitingDoesntFail( 1 , true );
	}

	@Test
	public void testVehicleWaitingSeveralLapDoesntFailNoDummies() {
		testVehicleWaitingDoesntFail( 4 , false );
	}

	@Test
	public void testVehicleWaitingSeveralLapDoesntFailDummies() {
		testVehicleWaitingDoesntFail( 4 , true );
	}

	private static void testVehicleWaitingDoesntFail(final int nLaps, final boolean insertActivities) {
		final Config config = ConfigUtils.createConfig();
		config.addQSimConfigGroup( new QSimConfigGroup() );
		// test behavior when agents wait for the car
		config.getQSimConfigGroup().setVehicleBehavior( QSimConfigGroup.VEHICLE_BEHAVIOR_WAIT );
		// fail if the simualtion hangs forever (for instance, if some agents
		// just vanish as in MATSIM-71)
		config.getQSimConfigGroup().setEndTime( 48 * 3600 );

		final Scenario sc = ScenarioUtils.createScenario( config );
		
		final NetworkFactory netFact = sc.getNetwork().getFactory();

		final Node node1 = netFact.createNode( new IdImpl( 1 ) , new CoordImpl( 0 , 0 ) );
		final Node node2 = netFact.createNode( new IdImpl( 2 ) , new CoordImpl( 0 , 1000 ) );
		final Node node3 = netFact.createNode( new IdImpl( 3 ) , new CoordImpl( 1000 , 1000 ) );
		
		sc.getNetwork().addNode( node1 );
		sc.getNetwork().addNode( node2 );
		sc.getNetwork().addNode( node3 );

		final Link link1 = netFact.createLink( new IdImpl( 1 ) , node1 , node2 );
		final Link link2 = netFact.createLink( new IdImpl( 2 ) , node2 , node3 );
		final Link link3 = netFact.createLink( new IdImpl( 3 ) , node3 , node1 );

		sc.getNetwork().addLink( link1 );
		sc.getNetwork().addLink( link2 );
		sc.getNetwork().addLink( link3 );

		final PopulationFactory popFact = sc.getPopulation().getFactory();

		final List<Id> personIds = new ArrayList<Id>();
		final Id personId1 = new IdImpl( "A" );
		personIds.add( personId1 );
		personIds.add( new IdImpl( "B" ) );
		personIds.add( new IdImpl( "C" ) );
		personIds.add( new IdImpl( "D" ) );

		for ( Id id : personIds ) {
			final Person person = popFact.createPerson( id );
			final Plan plan = popFact.createPlan();
			plan.setPerson( person );
			person.addPlan( plan );
			sc.getPopulation().addPerson( person );

			final Activity orig = popFact.createActivityFromLinkId( "h" , link1.getId() );
			orig.setEndTime( 10 );
			plan.addActivity( orig );
			for ( int lap=0; lap < nLaps; lap++ ) {
				final Leg leg = popFact.createLeg( TransportMode.car );
				final NetworkRoute route =
					new LinkNetworkRouteImpl(
							link1.getId(),
							Collections.singletonList( link2.getId() ),
							link3.getId());
				route.setVehicleId( personId1 ); // QSim creates a vehicle per person, with the ids of the persons
				leg.setRoute( route );
				plan.addLeg( leg );

				if ( insertActivities ) {
					final Activity dummy = popFact.createActivityFromLinkId( "dummy" , link3.getId() );
					dummy.setMaximumDuration( 0 );
					plan.addActivity( dummy );
				}

				final Leg secondLeg = popFact.createLeg( TransportMode.car );
				final NetworkRoute secondRoute =
					new LinkNetworkRouteImpl(
							link3.getId(),
							Collections.<Id>emptyList(),
							link1.getId());

				secondRoute.setVehicleId( personId1 ); // QSim creates a vehicle per person, with the ids of the persons
				secondLeg.setRoute( secondRoute );
				plan.addLeg( secondLeg );

				if ( insertActivities ) {
					final Activity dummy = popFact.createActivityFromLinkId( "dummy" , link3.getId() );
					dummy.setMaximumDuration( 0 );
					plan.addActivity( dummy );
				}
			}
		}

		final Map<Id, Integer> arrivalCounts = new HashMap<Id, Integer>();
		final EventsManager events = EventsUtils.createEventsManager();
		events.addHandler( new PersonArrivalEventHandler() {
			@Override
			public void reset(int iteration) {}

			@Override
			public void handleEvent(final PersonArrivalEvent event) {
				final Integer count = arrivalCounts.get( event.getPersonId() );
				arrivalCounts.put(
					event.getPersonId(),
					count == null ? 1 : count + 1 );

			}
		});

		final Netsim qsim = new QSimFactory().createMobsim( sc , events );

//		try {
			qsim.run();
//		}
//		catch (Exception e) {
//			log.error( "exception in Mobsim" , e  );
//			Assert.fail( "got an exception in the mobsim with arrivals "+arrivalCounts+"! "+e.getMessage() );
//		}

		for ( Id id : personIds ) {
			Assert.assertNotNull(
					"no arrivals for person "+id,
					arrivalCounts.get( id ) );
			Assert.assertEquals(
					"unexpected number of arrivals for person "+id,
					nLaps * 2,
					arrivalCounts.get( id ).intValue());
		}
	}
}

