/*
// $Id$
// Fennel is a library of data storage and processing components.
// Copyright (C) 2005 The Eigenbase Project
// Copyright (C) 2004 SQLstream, Inc.
// Copyright (C) 2009 Dynamo BI Corporation
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
#ifndef Fennel_CalcMessage_Included
#define Fennel_CalcMessage_Included

#include "fennel/calculator/CalcTypedefs.h"
#include "fennel/calculator/SqlState.h"

FENNEL_BEGIN_NAMESPACE

/**
 * An object for passing warning and error messages from execution.
 *
 * Values are copied, as they may refer to program state that could
 * change before execution terminates, or change before a Calculator
 * XO can read the message.
 */
class FENNEL_CALCULATOR_EXPORT CalcMessage
{
public:
    /**
     * Creates a message.
     *
     * strA is a 5 character long string, per SQL99 Part 2 Section 22.1.
     * strA can be either null terminated or simply 5 characters long.
     */
    explicit
    CalcMessage(SqlStateInfo const &info, TProgramCounter pcA)
        : pc(pcA)
    {
        strncpy(str, info.str().c_str(), 5);
        str[5] = 0; // insure null termination
    }

    char str[6];
    TProgramCounter pc;
};

FENNEL_END_NAMESPACE

#endif

// End CalcMessage.h

