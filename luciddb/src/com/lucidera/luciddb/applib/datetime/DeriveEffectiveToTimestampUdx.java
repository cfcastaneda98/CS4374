/*
// $Id$
// LucidDB is a DBMS optimized for business intelligence.
// Copyright (C) 2006-2007 LucidEra, Inc.
// Copyright (C) 2006-2007 The Eigenbase Project
//
// This program is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License as published by the Free
// Software Foundation; either version 2 of the License, or (at your option)
// any later version approved by The Eigenbase Project.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package com.lucidera.luciddb.applib.datetime;

/**
 * UDX for calculating the "effective to" timestamps out of a list where a new
 * timestamp invalidates an old one.
 *
 * @author Oscar Gothberg
 * @version $Id$
 */

import com.lucidera.luciddb.applib.resource.*;
import java.sql.*;
import java.util.GregorianCalendar;

public class DeriveEffectiveToTimestampUdx {
    
    /**
     * Takes a sorted list of IDs and timestamps. 
     * 
     * The "effective to" timestamp is calculated where two rows have the same
     * ID: the one with a newer timestamp invalidates that with an older, and
     * the "effective to" timestamp for row N is equal to the "effective from"
     * timestamp for row N+1 minus a supplied number of timeunits. Rows that
     * are not invalidated by another row with the same ID and a newer
     * timestamp will have their "effective to" set as NULL to indicate that
     * they're currently valid.
     *
     * @param inputSet cursor for the input 2-col table.
     * @param timeUnitsToSubtract the number of time units to subtract when
     * calculating the "effective to" timestamps.
     * @param typeOfTimeUnitToSubtract the type of time units to subtract when
     * calculating the "effective to" timestamps. Legal values are
     * "MILLISECOND", "SECOND", "MINUTE", "HOUR", "DAY", "WEEK", "MONTH",
     * "YEAR".
     * @param resultInserter pointer for returning the resulting 3-col table.
     */

    public static void execute(
        ResultSet inputSet,
        int timeUnitsToSubtract,
        String typeOfTimeUnitToSubtract,
        PreparedStatement resultInserter) 
        throws ApplibException, SQLException {
        
        // quick sanity checks
        if (inputSet.getMetaData().getColumnCount() != 2) {
            // input should have two columns
            throw ApplibResourceObject.get().WrongNumberInputColumns.ex(
                String.valueOf(inputSet.getMetaData().getColumnCount()));
        }

        String currIdStr, nextIdStr;
        Timestamp currTimestamp, nextTimestamp;
        
        // loop with one row of read-ahead. but if there isn't even a
        // single row of input, now is a good time to get out.
        if (!inputSet.next()) {
            return;
        }
        currIdStr = inputSet.getString(1);
        currTimestamp = inputSet.getTimestamp(2);
        
        while (inputSet.next()) {
            nextIdStr = inputSet.getString(1);
            nextTimestamp = inputSet.getTimestamp(2);

            // if input isn't ordered, fail and let the user know
            if (currIdStr.compareTo(nextIdStr) > 0
                || (currIdStr.equals(nextIdStr) 
                    && currTimestamp.getTime() > nextTimestamp.getTime())) {
                throw ApplibResourceObject.get().
                    IdOrEffectiveFromTimestampsNotOrdered.ex();
            }
            
            // pass through id and "effective from" timestamp
            resultInserter.setString(1, currIdStr);
            resultInserter.setTimestamp(2, currTimestamp);
            
            // if id of the next row is equal to that of the current one,
            // calculate "effective to" timestamp for current row.
            if (currIdStr.equals(nextIdStr)) {
                resultInserter.setTimestamp(3, 
                    subtractFromTimestamp(
                        nextTimestamp, 
                        timeUnitsToSubtract, 
                        typeOfTimeUnitToSubtract));
            } else {
                // otherwise set to NULL
                resultInserter.setNull(3, Types.TIMESTAMP);
            }
            
            // execute changes and make the next row the current
            resultInserter.executeUpdate();            
            currIdStr = nextIdStr;
            currTimestamp = nextTimestamp;
        }
        
        // last "effective to" in multi-row input is always NULL
        resultInserter.setString(1, currIdStr);
        resultInserter.setTimestamp(2, currTimestamp);
        resultInserter.setNull(3, Types.TIMESTAMP);
        resultInserter.executeUpdate();
    }

    /**
     * Does the actual time subtraction using java's Calendar class.
     * @param fromTimestamp time to be subtracted from.
     * @param timeUnitsToSubtract number of time units to subtract.
     * @param typeOfTimeUnitToSubtract time unit type identifier
     */
    private static Timestamp subtractFromTimestamp(
        Timestamp theTimestamp, 
        int timeUnitsToSubtract, 
        String typeOfTimeUnitToSubtract) 
        throws ApplibException, SQLException {
        
        // set up a calendar object
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTimeInMillis(theTimestamp.getTime());

        // get Calendar constant for time unit to be subtracted
        int calendarField;        
        if (typeOfTimeUnitToSubtract.equals("MILLISECOND")) {
            calendarField = GregorianCalendar.MILLISECOND;
        } else if (typeOfTimeUnitToSubtract.equals("SECOND")) {
            calendarField = GregorianCalendar.SECOND;
        } else if (typeOfTimeUnitToSubtract.equals("MINUTE")) {
            calendarField = GregorianCalendar.MINUTE;
        } else if (typeOfTimeUnitToSubtract.equals("HOUR")) {
            calendarField = GregorianCalendar.HOUR;
        } else if (typeOfTimeUnitToSubtract.equals("DAY")) {
            calendarField = GregorianCalendar.DAY_OF_MONTH;
        } else if (typeOfTimeUnitToSubtract.equals("WEEK")) {
            calendarField = GregorianCalendar.WEEK_OF_YEAR;
        } else if (typeOfTimeUnitToSubtract.equals("MONTH")) {
            calendarField = GregorianCalendar.MONTH;
        } else if (typeOfTimeUnitToSubtract.equals("YEAR")) {
            calendarField = GregorianCalendar.YEAR;
        } else {
            throw ApplibResourceObject.get().IllegalTimeUnitIdentifier.ex(
                typeOfTimeUnitToSubtract);
        }

        // do the subtraction
        cal.add(calendarField, -timeUnitsToSubtract);
        
        return new Timestamp(cal.getTimeInMillis());
    }
}

// End DeriveEffectiveToDateUdx.java
