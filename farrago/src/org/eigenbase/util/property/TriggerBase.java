/*
// $Id$
// Package org.eigenbase is a class library of data management components.
// Copyright (C) 2005 The Eigenbase Project
// Copyright (C) 2002 SQLstream, Inc.
// Copyright (C) 2005 Dynamo BI Corporation
// Portions Copyright (C) 2003 John V. Sichi
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published by the
// Free Software Foundation; either version 2 of the License, or (at your
// option) any later version approved by The Eigenbase Project.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package org.eigenbase.util.property;

/**
 * Basic implementation of a trigger, which doesn't do anything.
 *
 * @author Julian Hyde
 * @version $Id$
 * @since 5 July 2005
 */
public class TriggerBase
    implements Trigger
{
    //~ Instance fields --------------------------------------------------------

    private final boolean persistent;

    //~ Constructors -----------------------------------------------------------

    public TriggerBase(boolean persistent)
    {
        this.persistent = persistent;
    }

    //~ Methods ----------------------------------------------------------------

    public boolean isPersistent()
    {
        return persistent;
    }

    public int phase()
    {
        return Trigger.PRIMARY_PHASE;
    }

    public void execute(Property property, String value)
        throws VetoRT
    {
        // do nothing
    }
}

// End TriggerBase.java
