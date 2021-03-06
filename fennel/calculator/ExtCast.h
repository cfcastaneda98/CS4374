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

#ifndef Fennel_ExtCast_Included
#define Fennel_ExtCast_Included

#include "fennel/calculator/RegisterReference.h"
#include "fennel/calculator/ExtendedInstruction.h"

FENNEL_BEGIN_NAMESPACE

//! castA. Ascii. Char & Varchar
//!
//! Casts an exact numeric to an Ascii string.
//!
//! May throw "22001" data exception - string data, right truncation
void
castExactToStrA(
    RegisterRef<char*>* result,
    RegisterRef<int64_t>* src);

//! castA. Ascii. Char & Varchar
//!
//! Casts an exact numeric with precision and scale to an Ascii string.
//!
//! May throw "22001" data exception - string data, right truncation
void
castExactToStrA(
    RegisterRef<char*>* result,
    RegisterRef<int64_t>* src,
    RegisterRef<int32_t>* precision,
    RegisterRef<int32_t>* scale);

//! castA. Ascii. Char & Varchar
//!
//! Casts an approximate numeric to an Ascii string.
//!
//! May throw "22001" data exception - string data, right truncation
void
castApproxToStrA(
    RegisterRef<char*>* result,
    RegisterRef<double>* src);

//! castA. Ascii. Char & Varchar
//!
//! Casts a string to an exact numeric.
//!
//! May throw "22018" data exception - invalid character value for cast
void
castStrtoExactA(
    RegisterRef<int64_t>* result,
    RegisterRef<char*>* src);

//! castA. Ascii. Char & Varchar
//!
//! Casts a string to an exact numeric with precision and scale.
//!
//! May throw "22018" data exception - invalid character value for cast
//! May throw "22003" data exception - numeric value out of range
void
castStrToExactA(
    RegisterRef<int64_t>* result,
    RegisterRef<char*>* src,
    RegisterRef<int32_t>* precision,
    RegisterRef<int32_t>* scale);

//! castA. Ascii. Char & Varchar
//!
//! Casts a string to an approximate numeric.
//!
//! May throw "22018" data exception - invalid character value for cast
void
castStrToApproxA(
    RegisterRef<double>* result,
    RegisterRef<char*>* src);

//! castA. Ascii. Char & Varchar
//!
//! Casts a boolean to an Ascii string.
//!
//! May throw "22018" data exception - invalid character value for cast
void
castBooleanToStrA(
    RegisterRef<char*>* result,
    RegisterRef<int64_t>* src);


//! castA. Ascii. Char & Varchar
//!
//! Casts a string to an boolean.
//!
//! May throw "22018" data exception - invalid character value for cast
void
castStrtoBooleanA(
    RegisterRef<bool>* result,
    RegisterRef<char*>* src);

//! castA. Ascii. String to Varchar
//!
//! Casts a string to a variable-length string.
//!
//! May throw "22001" string data, right truncation
void
castStrToVarCharA(
    RegisterRef<char*>* result,
    RegisterRef<char*>* src);

//! castA. Ascii. String to Char
//!
//! Casts a string to a fixed-length string.
//!
//! May throw "22001" string data, right truncation
void
castStrToCharA(
    RegisterRef<char*>* result,
    RegisterRef<char*>* src);


class ExtendedInstructionTable;

void
ExtCastRegister(ExtendedInstructionTable* eit);


FENNEL_END_NAMESPACE

#endif

// End ExtCast.h
