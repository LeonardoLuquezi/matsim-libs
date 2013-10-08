/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2010 by the members listed in the COPYING,        *
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

package org.matsim.core.mobsim.qsim.pt;

import java.util.List;
import java.util.ListIterator;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.PassengerAgent;
import org.matsim.core.mobsim.framework.PlanAgent;
import org.matsim.core.mobsim.qsim.InternalInterface;
import org.matsim.core.mobsim.qsim.agents.PersonDriverAgentImpl;
import org.matsim.core.mobsim.qsim.interfaces.MobsimVehicle;
import org.matsim.core.mobsim.qsim.interfaces.Netsim;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public abstract class AbstractTransitDriver implements TransitDriverAgent, PlanAgent {

	private static final Logger log = Logger.getLogger(AbstractTransitDriver.class);

	private TransitVehicle vehicle = null;

	private int nextLinkIndex = 0;
	private Person dummyPerson;
	private TransitRouteStop currentStop = null;
	protected TransitRouteStop nextStop;
	private ListIterator<TransitRouteStop> stopIterator;
	private InternalInterface internalInterface;
	
	private final PassengerAccessEgressImpl accessEgress;
	
	/* package */ MobsimAgent.State state = MobsimAgent.State.ACTIVITY ; 
	// yy not so great: implicit instantiation at activity.  kai, nov'11
	@Override
	public final MobsimAgent.State getState() {
		return this.state ;
	}

	@Override
	public abstract void endLegAndComputeNextState(final double now);
	public abstract NetworkRoute getCarRoute();
	public abstract TransitLine getTransitLine();
	public abstract TransitRoute getTransitRoute();
	public abstract Departure getDeparture();
	@Override
	public abstract double getActivityEndTime();

	public AbstractTransitDriver(InternalInterface internalInterface, TransitStopAgentTracker agentTracker2) {
		super();
		this.internalInterface = internalInterface;
		accessEgress = new PassengerAccessEgressImpl(this.internalInterface, agentTracker2);
	}

	protected final void init() {
		if (getTransitRoute() != null) {
			this.stopIterator = getTransitRoute().getStops().listIterator();
			this.nextStop = (stopIterator.hasNext() ? stopIterator.next() : null);
		} else {
			this.nextStop = null;
		}
		this.nextLinkIndex = 0;
	}

	protected final void setDriver(Person personImpl) {
		this.dummyPerson = personImpl;
	}

	@Override
	public final Id chooseNextLinkId() {
		NetworkRoute netR = getCarRoute();
		List<Id> linkIds = netR.getLinkIds();
		if (this.nextLinkIndex < linkIds.size()) {
			return linkIds.get(this.nextLinkIndex);
		}
		if (this.nextLinkIndex == linkIds.size()) {
			if (linkIds.size() == 0 && netR.getStartLinkId().equals(netR.getEndLinkId())) {
				// unfortunate exception for the unlikely but legal and test-covered case where a whole transit line
				// takes place on a single link.
				// would not need to be here if a route were simply a list of linkIds to traverse, with 1 being an allowed length.
				// this used to return endLinkId, which would then require a transit-specific extra case for arriving vehicles
				// in QLinkImpl, which was even worse.
				return null;
			}
			return netR.getEndLinkId();
		}
		assertAllStopsServed();
		return null;
	}
	
	@Override
	public final void abort( final double now ) {
		this.state = MobsimAgent.State.ABORT ;
	}

	@Override
	public final Id getCurrentLinkId() {
		int currentLinkIndex = this.nextLinkIndex - 1;
		if (currentLinkIndex < 0) {
			return getCarRoute().getStartLinkId();
		} else if (currentLinkIndex >= getCarRoute().getLinkIds().size()) {
			return getCarRoute().getEndLinkId();
		} else {
			return getCarRoute().getLinkIds().get(currentLinkIndex);
		}
	}

	@Override
	public final void notifyMoveOverNode(Id nextLinkId) {
		this.nextLinkIndex++;
	}

	@Override
	public final TransitStopFacility getNextTransitStop() {
		if (this.nextStop == null) {
			return null;
		}
		return this.nextStop.getStopFacility();
	}

	@Override
	public double handleTransitStop(final TransitStopFacility stop, final double now) {
		// yy can't make this final because of tests.  kai, oct'12
		
		assertExpectedStop(stop);
		processEventVehicleArrives(stop, now);
		
		TransitRoute route = this.getTransitRoute();
		List<TransitRouteStop> stopsToCome = route.getStops().subList(stopIterator.nextIndex(), route.getStops().size());
		/*
		 * If there are passengers leaving or entering, the stop time must be not greater than 1.0 in order to let them (de-)board every second. 
		 * If a stopTime greater than 1.0 is used, this method is not necessarily triggered by the qsim, so (de-)boarding will not happen. Dg, 10-2012
		 */
		double stopTime = this.accessEgress.calculateStopTimeAndTriggerBoarding(getTransitRoute(), getTransitLine(), this.vehicle, stop, stopsToCome, now);
		
		if(stopTime == 0.0){
			stopTime = longerStopTimeIfWeAreAheadOfSchedule(now, stopTime);
		}
		if (stopTime == 0.0) {
			depart(now);
		}
		return stopTime;
	}

	protected final void sendTransitDriverStartsEvent(final double now) {
		// check if "Wenden"
		if(getTransitLine() == null){
			this.internalInterface.getMobsim().getEventsManager().processEvent(new TransitDriverStartsEvent(now, this.dummyPerson.getId(),
					this.vehicle.getId(), new IdImpl("Wenden"), new IdImpl("Wenden"), new IdImpl("Wenden")));
		} else {
			this.internalInterface.getMobsim().getEventsManager().processEvent(new TransitDriverStartsEvent(now, this.dummyPerson.getId(),
					this.vehicle.getId(), getTransitLine().getId(), getTransitRoute().getId(), getDeparture().getId()));
		}
	}

	@Override
	public void notifyArrivalOnLinkByNonNetworkMode(final Id linkId) {
	}

	final Netsim getSimulation(){
		return this.internalInterface.getMobsim();
	}

	/**Design comments:<ul>
	 * <li> Keeping this for the time being, since the derived methods somehow need to get the selected plan.  Might
	 * keep track of the selected plan directly, but someone would need to look more into the design. kai, jun'11
	 * <li> For that reason, I made the method package-private.  There is, however, probably not much harm to make 
	 * it public again as long as it is not part of the PlanDriverAgent interface.  kai, jun'11
	 * </ul>
	 */
	final Person getPerson() {
		return this.dummyPerson;
	}

	@Override
	public final TransitVehicle getVehicle() {
		return this.vehicle;
	}

	@Override
	public final void setVehicle(final MobsimVehicle vehicle) {
		// MobsimVehicle to fulfill the interface; should be a TransitVehicle at runtime!
		this.vehicle = (TransitVehicle) vehicle;
	}

	private void processEventVehicleArrives(final TransitStopFacility stop,
			final double now) {
		EventsManager events = this.internalInterface.getMobsim().getEventsManager();
		if (this.currentStop == null) {
			this.currentStop = this.nextStop;
			double delay = now - this.getDeparture().getDepartureTime();
			if (! ( Double.isNaN(this.currentStop.getArrivalOffset()) 
					&&   Double.isInfinite(this.currentStop.getArrivalOffset())) ){
				delay = delay - this.currentStop.getArrivalOffset();
			}
			else if (! (Double.isNaN(this.currentStop.getDepartureOffset()) &&   Double.isInfinite(this.currentStop.getDepartureOffset()))) {
				delay =  delay - this.currentStop.getDepartureOffset();
			}
			else {
				log.warn("Could not calculate delay!");
			}
			events.processEvent(new VehicleArrivesAtFacilityEvent(now, this.vehicle.getVehicle().getId(), stop.getId(),
					delay));
		}
	}

	private void assertExpectedStop(final TransitStopFacility stop) {
		if (stop != this.nextStop.getStopFacility()) {
			throw new RuntimeException("Expected different stop.");
		}
	}

	protected double longerStopTimeIfWeAreAheadOfSchedule(final double now,
			final double stopTime) {
		if ((this.nextStop.isAwaitDepartureTime()) && (this.nextStop.getDepartureOffset() != Time.UNDEFINED_TIME)) {
			double earliestDepTime = getActivityEndTime() + this.nextStop.getDepartureOffset();
			if (now + stopTime < earliestDepTime) {
				return earliestDepTime - now;
			}
		}
		return stopTime;
	}

	private boolean isBadDouble(double d){
		if (Double.isNaN(d) || Double.isInfinite(d)){
			return true;
		}
		return false;
	}
	
	private void depart(final double now) {
		EventsManager events = this.internalInterface.getMobsim().getEventsManager();
		double delay = now - this.getDeparture().getDepartureTime();
		if (this.isBadDouble(this.getDeparture().getDepartureTime())){ //this is the case if the next stop is null
			delay = 0;
		}
		if (! this.isBadDouble(this.currentStop.getDepartureOffset())){
			delay =  delay - this.currentStop.getDepartureOffset();
		}
		else if (! this.isBadDouble(this.currentStop.getArrivalOffset()) ){
			delay = delay - this.currentStop.getArrivalOffset();
		}
		else {
			log.warn("Could not calculate delay!");
		}
		events.processEvent(new VehicleDepartsAtFacilityEvent(now, this.vehicle.getVehicle().getId(),
				this.currentStop.getStopFacility().getId(),
				delay));
		this.nextStop = (stopIterator.hasNext() ? stopIterator.next() : null);
		if(this.nextStop == null) {
			assertVehicleIsEmpty();
		}
		this.currentStop = null;
	}

	private void assertAllStopsServed() {
		if (this.nextStop != null) {
			RuntimeException e = new RuntimeException("Transit vehicle is not yet at last stop! vehicle-id = " 
					+ this.vehicle.getVehicle().getId() + "; next-stop = " + this.nextStop.getStopFacility().getId());
			log.error(e);
			throw e;
		}
	}
	
	private void assertVehicleIsEmpty() {
		if (this.vehicle.getPassengers().size() > 0) {
			RuntimeException e = new RuntimeException("Transit vehicle is at last stop but still contains passengers that did not leave the vehicle!");
			log.error("Transit vehicle must be empty after last stop! vehicle-id = " + this.vehicle.getVehicle().getId(), e);
			for (PassengerAgent agent : this.vehicle.getPassengers()) {
				if (agent instanceof PersonDriverAgentImpl) {
				log.error("Agent is still in transit vehicle: agent-id = " + ((PersonDriverAgentImpl) agent).getPerson().getId());
				}
			}
			throw e;
		}
	}


	protected final NetworkRouteWrapper getWrappedCarRoute(NetworkRoute carRoute) {
		return new NetworkRouteWrapper(carRoute);
	}

	@Override
	public Id getId() {
		return this.dummyPerson.getId() ;
	}
	
	/**
	 * for junit tests in same package
	 */
	abstract /*package*/ Leg getCurrentLeg() ;


	/**
	 * A simple wrapper that delegates all get-Methods to another instance, blocks set-methods
	 * so this will be read-only, and returns in getVehicleId() a vehicle-Id specific to this driver,
	 * and not to the NetworkRoute. This allows to share one NetworkRoute from a TransitRoute with
	 * multiple transit drivers, thus saving memory.
	 *
	 * @author mrieser
	 */
	protected final class NetworkRouteWrapper implements NetworkRoute, Cloneable {

		private final NetworkRoute delegate;

		public NetworkRouteWrapper(final NetworkRoute route) {
			this.delegate = route;
		}

		@Override
		public List<Id> getLinkIds() {
			return this.delegate.getLinkIds();
		}

		@Override
		public NetworkRoute getSubRoute(final Id fromLinkId, final Id toLinkId) {
			return this.delegate.getSubRoute(fromLinkId, toLinkId);
		}

		@Override
		public double getTravelCost() {
			return this.delegate.getTravelCost();
		}

		@Override
		public Id getVehicleId() {
			return AbstractTransitDriver.this.vehicle.getVehicle().getId();
		}

		@Override
		public void setLinkIds(final Id startLinkId, final List<Id> srcRoute, final Id endLinkId) {
			throw new UnsupportedOperationException("read only route.");
		}

		@Override
		public void setTravelCost(final double travelCost) {
			throw new UnsupportedOperationException("read only route.");
		}

		@Override
		public void setVehicleId(final Id vehicleId) {
			throw new UnsupportedOperationException("read only route.");
		}

		@Override
		public void setEndLinkId(final Id  linkId) {
			throw new UnsupportedOperationException("read only route.");
		}

		@Override
		public void setStartLinkId(final Id linkId) {
			throw new UnsupportedOperationException("read only route.");
		}

		@Override
		@Deprecated
		public double getDistance() {
			return this.delegate.getDistance();
		}

		@Override
		public Id getEndLinkId() {
			return this.delegate.getEndLinkId();
		}

		@Override
		public Id getStartLinkId() {
			return this.delegate.getStartLinkId();
		}

		@Deprecated
		@Override
		public double getTravelTime() {
			return this.delegate.getTravelTime();
		}

		@Override
		public void setDistance(final double distance) {
			throw new UnsupportedOperationException("read only route.");
		}

		@Override
		public void setTravelTime(final double travelTime) {
			throw new UnsupportedOperationException("read only route.");
		}

		@Override
		public NetworkRouteWrapper clone() {
			try {
				return (NetworkRouteWrapper) super.clone();
			} catch (CloneNotSupportedException e) {
				throw new AssertionError(e);
			}
		}

	}


}