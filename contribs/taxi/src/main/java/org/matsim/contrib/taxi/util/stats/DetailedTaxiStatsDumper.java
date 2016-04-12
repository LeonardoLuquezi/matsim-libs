/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
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

package org.matsim.contrib.taxi.util.stats;

import org.matsim.contrib.taxi.data.TaxiData;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;


public class DetailedTaxiStatsDumper
    implements AfterMobsimListener
{
    private final TaxiData taxiData;
    private final MatsimServices matsimServices;
    private final int hours;


    public DetailedTaxiStatsDumper(TaxiData taxiData, MatsimServices matsimServices, int hours)
    {
        this.taxiData = taxiData;
        this.matsimServices = matsimServices;
        this.hours = hours;
    }


    @Override
    public void notifyAfterMobsim(AfterMobsimEvent event)
    {
        String prefix = matsimServices.getControlerIO().getIterationFilename(event.getIteration(),
                "taxi_");

        DetailedTaxiStatsCalculator calculator = new DetailedTaxiStatsCalculator(
                taxiData.getVehicles().values(), hours);
        HourlyTaxiStats.printAllStats(calculator.getStats(), prefix + "hourly_stats.txt");
        HourlyHistograms.printAllHistograms(calculator.getHourlyHistograms(),
                prefix + "hourly_histograms.txt");
        calculator.getDailyHistograms().printHistograms(prefix + "daily_histograms.txt");
    }
}
