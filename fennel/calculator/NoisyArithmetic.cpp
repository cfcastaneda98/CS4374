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
//
// ---
//
// Arithmetic function on various types but Raises exceptions on various
// error conditions (overflow, divide-by-zero etc.)
//
// TODO:
//      1. How does little/big endian effect the bit shift used in
//          unsigned multiplication?
//      3. Shift/Bit/Round operator etc.
//
*/
/* TEMP. TODO. LATER, gcc>=4.1 is braindead: optimizes fp instructions until
    AFTER the fp status check (fetestexcept()). At the same time FENV_ACCESS ON
    is not yet implemented. ref. dtbug #1490.

   Ref. this macro in code and cleanup by removing redundant code
*/
#define DTBUG1490   (1)

#include "fennel/common/CommonPreamble.h"

#include <assert.h>
#ifndef DTBUG1490
#pragma STDC FENV_ACCESS ON   /* notify compiler we expect to use fp
                                 exceptions, this is the standard way of
                                 stopping compiler from introducing
                                 optimizations which don't play well with fp
                                 status registers */
#endif
#ifndef __MSVC__
#include <fenv.h>
#if __WORDSIZE == 64
#define NOISY_LONGINT
#endif
#else
#define NOISY_LONG
#endif
#include <string>

#include "NoisyArithmetic.h"

FENNEL_BEGIN_CPPFILE("$Id$");

// TODO jvs 1-Apr-2009:  try noisy on Win32 again now that we have
// upgraded the old STLport version we were using there
#ifdef __MSVC__
#define NOISY_DISABLED    (1)
#endif

/* TODO --- check these codes: 220 DATA_EXCEPTION */
#define S_OVER    "22003"        /* NUMERIC_VALUE_OUT_OF_RANGE */
#define S_UNDR    "22000"        /* ?? underflow */
#define S_INVL    "22023"        /* INVALID_PARAMETER_VALUE */
#define S_INEX    "22000"        /* ?? inexact */
#define S_DIV0    "22012"        /* DIVISION_BY_ZERO */

/* --- */
static void Raise(
    TExceptionCBData *pData,
    TProgramCounter pc,
    SqlStateInfo const &stateInfo)
    throw(CalcMessage)
{
    if (pData) {
        assert(pData->fnCB);
        (pData->fnCB)(stateInfo, pData->pData);
    }
    throw CalcMessage(stateInfo, pc);
}

#if defined(NOISY_DISABLED) && NOISY_DISABLED
/*
** Disabled all tests (to be compatible with current implementation
** in order to allow current tests to succeed
*/
#define DO(type)                                                        \
    template <> type Noisy<type>::add(                                  \
        TProgramCounter,                                                \
        const type left, const type right,                              \
        TExceptionCBData *pExData) throw(CalcMessage)                   \
    {                                                                   \
        return left + right;                                            \
    }                                                                   \
    template <> type Noisy<type>::sub(                                  \
        TProgramCounter,                                                \
        const type left, const type right,                              \
        TExceptionCBData *pExData) throw(CalcMessage)                   \
    {                                                                   \
        return left - right;                                            \
    }                                                                   \
    template <> type Noisy<type>::mul(                                  \
        TProgramCounter,                                                \
        const type left, const type right,                              \
        TExceptionCBData *pExData) throw(CalcMessage)                   \
    {                                                                   \
        return left * right;                                            \
    }                                                                   \
    template <> type Noisy<type>::div(                                  \
        TProgramCounter pc,                                             \
        const type left, const type right,                              \
        TExceptionCBData *pExData) throw(CalcMessage) {                 \
        if (right == 0) {                                               \
            Raise(pExData, pc, SqlState::instance().code22012());       \
        }                                                               \
        return left / right;                                            \
    }                                                                   \
    template <> type Noisy<type>::neg(                                  \
        TProgramCounter,                                                \
        const type right,                                               \
        TExceptionCBData *pExData) throw(CalcMessage)                   \
    {                                                                   \
        return right * -1;                                              \
    }
DO(char)
DO(signed char)
DO(unsigned char)
DO(short)
DO(unsigned short)
DO(int)
DO(unsigned int)
#ifdef NOISY_LONG
DO(long)
DO(unsigned long)
#endif
#ifdef NOISY_LONGINT
DO(long int)
DO(long unsigned int)
#else
DO(long long int)
DO(long long unsigned int)
#endif
DO(float)
DO(double)
DO(long double)

#else
#define UNSIGNED_ADD(type)                                              \
    template <> type Noisy<type>::add(                                  \
        TProgramCounter pc, const type left,                            \
        const type right, TExceptionCBData *pExData) throw(CalcMessage) \
    {                                                                   \
        register type result;                                           \
        if (left > (std::numeric_limits<type>::max() - right)) {        \
            Raise(pExData, pc, SqlState::instance().code22003());       \
        }                                                               \
        result = left + right;                                          \
        assert(result == (left + right));                               \
        return result;                                                  \
    }

#define SIGNED_ADD(type)                                                \
    template <> type Noisy<type>::add(                                  \
        TProgramCounter pc, const type left,                            \
        const type right, TExceptionCBData *pExData) throw(CalcMessage) \
    {                                                                   \
        register type result = left + right;                            \
        if (left < 0 && right < 0 && result >= 0) {                     \
            Raise(pExData, pc, SqlState::instance().code22003());       \
        }                                                               \
        if (left > 0 && right > 0 && result <= 0) {                     \
            Raise(pExData, pc, SqlState::instance().code22003());       \
        }                                                               \
        assert(result == (left + right));                               \
        return result;                                                  \
    }

#define UNSIGNED_SUB(type)                                              \
    template <> type Noisy<type>::sub(                                  \
        TProgramCounter pc, const type left,                            \
        const type right, TExceptionCBData *pExData) throw(CalcMessage) \
    {                                                                   \
        register type result;                                           \
        if (right > left) {                                             \
            Raise(pExData, pc, SqlState::instance().code22003());       \
        }                                                               \
        return left - right;                                            \
        result = left - right;                                          \
        assert(result == (left - right));                               \
        return result;                                                  \
    }

#define SIGNED_SUB(type)                                                \
    template <> type Noisy<type>::sub(                                  \
        TProgramCounter pc, const type left,                            \
        const type right, TExceptionCBData *pExData) throw(CalcMessage) \
    {                                                                   \
        register type r = right;                                        \
        register type l = left;                                         \
        register type result;                                           \
        if (r == std::numeric_limits<type>::min()) {                    \
            if (l == std::numeric_limits<type>::max()) {                \
                Raise(pExData, pc, SqlState::instance().code22003());   \
            }                                                           \
            r++;                                                        \
            l++;                                                        \
        }                                                               \
        result = Noisy<type>::add(pc, l, -r, pExData);                  \
        assert(result == (left - right));                               \
        return result;                                                  \
    }

#define UNSIGNED_MUL(type)                                                  \
    template <> type Noisy<type>::mul(                                      \
        TProgramCounter pc, const type left,                                \
        const type right, TExceptionCBData *pExData) throw(CalcMessage)     \
    {                                                                   \
        register type result;                                           \
        if (left == 0 || right == 0) {                                  \
            return 0;                                                   \
        }                                                               \
        if (left > right) {                                             \
            return Noisy<type>::mul(pc, right, left, pExData);          \
        }                                                               \
        register type r = right;                                        \
        register type l = left;                                         \
        assert(l <= r);                                                 \
        const type msb = ~(std::numeric_limits<type>::max() >> 1);      \
        result = 0;                                                     \
        while (1) {                                                     \
            if (l & 0x1) {                                              \
                result = Noisy<type>::add(pc, result, r, pExData);      \
            }                                                           \
            l >>= 1;                                                    \
            if (!l) {                                                   \
                break;                                                  \
            }                                                           \
            if (msb & r) {                                              \
                Raise(pExData, pc, SqlState::instance().code22003());   \
            }                                                           \
            r <<= 1;                                                    \
        }                                                               \
        assert(result == (left * right));                               \
        return result;                                                  \
    }

#define SIGNED_MUL(type)                                                \
    template <> type Noisy<type>::mul(                                  \
        TProgramCounter pc, const type left,                            \
        const type right, TExceptionCBData *pExData) throw(CalcMessage) \
    {                                                                   \
        register type result;                                           \
        if (left == 0 || right == 0) {                                  \
            return 0;                                                   \
        }                                                               \
        if (right > left) {                                             \
            return Noisy<type>::mul(pc, right, left, pExData);          \
        }                                                               \
        register type r = right;                                        \
        register type l = left;                                         \
        assert(r <= l);                                                 \
        if (l < 0 /* infers r<0, both negative */) {                    \
            if (r == std::numeric_limits<type>::min()) {                \
                Raise(pExData, pc, SqlState::instance().code22003());   \
            }                                                           \
            assert(l != std::numeric_limits<type>::min());              \
            l = -l;                                                     \
            r = -r;                                                     \
        }                                                               \
        assert(l > 0);                                                  \
        const type n_max = std::numeric_limits<type>::min() >> 1;       \
        const type p_max = (-n_max) - 1;                                \
        result = 0;                                                     \
        while (1) {                                                     \
            if (l & 0x1) {                                              \
                result = Noisy<type>::add(pc, result, r, pExData);      \
            }                                                           \
            l >>= 1;                                                    \
            if (!l) {                                                   \
                break;                                                  \
            }                                                           \
            if (r < n_max || r > p_max) {                               \
                Raise(pExData, pc, SqlState::instance().code22003());   \
            }                                                           \
            r *= 2;                                                     \
        }                                                               \
        assert(result == (left * right));                               \
        return result;                                                  \
    }

#define UNSIGNED_DIV(type)                                              \
    template <> type Noisy<type>::div(                                  \
        TProgramCounter pc, const type left,                            \
        const type right, TExceptionCBData *pExData) throw(CalcMessage) \
    {                                                                   \
        if (right == 0) {                                               \
            Raise(pExData, pc, SqlState::instance().code22012());       \
        }                                                               \
        register type result = left / right;                            \
        assert(result == (left / right));                               \
        return result;                                                  \
    }

#define SIGNED_DIV(type)                                                \
    template <> type Noisy<type>::div(                                  \
        TProgramCounter pc, const type left,                            \
        const type right, TExceptionCBData *pExData) throw(CalcMessage) \
    {                                                                   \
        /* this only holds for 2's complement representations */        \
        if (left == std::numeric_limits<type>::min() && right == -1) {  \
            Raise(pExData, pc, SqlState::instance().code22003());       \
        }                                                               \
        if (right == 0) {                                               \
            Raise(pExData, pc, SqlState::instance().code22012());       \
        }                                                               \
        register type result = left / right;                            \
        assert(result == (left / right));                               \
        return result;                                                  \
    }

#define UNSIGNED_NEG(type)                                              \
    template <> type Noisy<type>::neg(                                  \
        TProgramCounter pc,                                             \
        const type right, TExceptionCBData *pExData) throw(CalcMessage) \
    {                                                                   \
        if (right != 0) {                                               \
            Raise(pExData, pc, SqlState::instance().code22003());       \
        }                                                               \
        return 0;                                                       \
    }

#define SIGNED_NEG(type)                                                \
    template <> type Noisy<type>::neg(                                  \
        TProgramCounter pc,                                             \
        const type right, TExceptionCBData *pExData) throw(CalcMessage) \
    {                                                                   \
        /* this only holds for 2's complement representations */        \
        if (right == std::numeric_limits<type>::min()) {                \
            Raise(pExData, pc, SqlState::instance().code22003());       \
        }                                                               \
        return -(right);                                                \
    }

/* --- */
inline void maybe_raise_fe_exception(
    TExceptionCBData *pExData,
    TProgramCounter pc)
    throw(CalcMessage)
{
    int fe = ::fetestexcept(FE_ALL_EXCEPT);
    if (0) {
    } else if (fe & FE_DIVBYZERO) {
        Raise(pExData, pc, SqlState::instance().code22012());
    } else if (fe & FE_UNDERFLOW) {
        Raise(pExData, pc, SqlState::instance().code22000());
    } else if (fe & FE_OVERFLOW) {
        Raise(pExData, pc, SqlState::instance().code22003());
    } else if (fe & FE_INVALID) {
        Raise(pExData, pc, SqlState::instance().code22023());

    /* leave this last because it occurs in conjunction with other
        flags */
    } else if (fe & FE_INEXACT) {
/* disabling inexact. for <float> 200.0 + 0.3 == 200.300003 and S_INEX
 gets set., so is too pedestrian a case to raise an error for
        Raise(pExData, pc, S_INEX); */
        }
}

/* ---
Removing the following dev. time sanity checks here b/c they may fail
because of an ignored S_INEX, see above comment.
    assert(result == (left+right));                                       \
    assert(result == (left-right));                                       \
    assert(result == (left*right));                                       \
    assert(result == (left/right));                                       \
    assert(result == (-right));                                           \
--- */



#if defined(DTBUG1490) && DTBUG1490
    /* instruct gcc *NEVER* to inline functions, thereby forcing the actual fp
    operation to occur before we test it's result.... */
#   define OP_FN_PROLOG static void __attribute__((noinline))
#else
    /* optimize to inplace operations, even at -O1 */
#   define inline void
#endif
template <typename TYPE> OP_FN_PROLOG na_add(
    TYPE &res, const TYPE &r, const TYPE &l)
{
    res = r + l;
}

template <typename TYPE> OP_FN_PROLOG na_sub(
    TYPE &res, const TYPE &r, const TYPE &l)
{
    res = r - l;
}

template <typename TYPE> OP_FN_PROLOG na_mul(
    TYPE &res, const TYPE &r, const TYPE &l)
{
    res = r * l;
}

template <typename TYPE> OP_FN_PROLOG na_div(
    TYPE &res, const TYPE &r, const TYPE &l)
{
    res = r / l;
}

template <typename TYPE> OP_FN_PROLOG na_neg(TYPE &res, const TYPE &r)
{
    res = -r;
}

#define FLOATING_ADD(type)                                              \
    template <> type Noisy<type>::add(                                  \
        TProgramCounter pc, const type left,                            \
        const type right, TExceptionCBData *pExData) throw(CalcMessage) \
    {                                                                   \
        type result;                                                    \
        ::feclearexcept(FE_ALL_EXCEPT);                                 \
        na_add<type>(result, left, right);                              \
        maybe_raise_fe_exception(pExData, pc);                          \
        return result;                                                  \
    }

#define FLOATING_SUB(type)                                              \
    template <> type Noisy<type>::sub(                                  \
        TProgramCounter pc, const type left,                            \
        const type right, TExceptionCBData *pExData) throw(CalcMessage) \
    {                                                                       \
        type result;                                                    \
        ::feclearexcept(FE_ALL_EXCEPT);                                 \
        na_sub<type>(result, left, right);                              \
        maybe_raise_fe_exception(pExData, pc);                          \
        return result;                                                  \
    }

#define FLOATING_MUL(type)                                              \
    template <> type Noisy<type>::mul(                                  \
        TProgramCounter pc, const type left,                            \
        const type right, TExceptionCBData *pExData) throw(CalcMessage) \
    {                                                                   \
        type result;                                                    \
        ::feclearexcept(FE_ALL_EXCEPT);                                 \
        na_mul<type>(result, left, right);                              \
        maybe_raise_fe_exception(pExData, pc);                          \
        return result;                                                  \
    }

#define FLOATING_DIV(type)                                              \
    template <> type Noisy<type>::div(                                  \
        TProgramCounter pc, const type left,                            \
        const type right, TExceptionCBData *pExData) throw(CalcMessage) \
    {                                                                   \
        type result;                                                    \
        ::feclearexcept(FE_ALL_EXCEPT);                                 \
        na_div<type>(result, left, right);                              \
        maybe_raise_fe_exception(pExData, pc);                          \
        return result;                                                  \
    }

#define FLOATING_NEG(type)                                              \
    template <> type Noisy<type>::neg(                                  \
        TProgramCounter pc,                                             \
        const type right, TExceptionCBData *pExData) throw(CalcMessage) \
    {                                                                   \
        type result;                                                    \
        ::feclearexcept(FE_ALL_EXCEPT);                                 \
        result = (-right);                                              \
        na_neg<type>(result, right);                                    \
        maybe_raise_fe_exception(pExData, pc);                          \
        return result;                                                  \
    }

SIGNED_ADD(char)
SIGNED_ADD(signed char)
SIGNED_ADD(short)
SIGNED_ADD(int)
#ifdef NOISY_LONG
SIGNED_ADD(long)
#endif
SIGNED_ADD(long long int)

UNSIGNED_ADD(unsigned char)
UNSIGNED_ADD(unsigned short)
UNSIGNED_ADD(unsigned int)
#ifdef NOISY_LONG
UNSIGNED_ADD(unsigned long)
#endif
UNSIGNED_ADD(unsigned long long int)

SIGNED_SUB(char)
SIGNED_SUB(signed char)
SIGNED_SUB(short)
SIGNED_SUB(int)
#ifdef NOISY_LONG
SIGNED_SUB(long)
#endif
SIGNED_SUB(long long int)

UNSIGNED_SUB(unsigned char)
UNSIGNED_SUB(unsigned short)
UNSIGNED_SUB(unsigned int)
#ifdef NOISY_LONG
UNSIGNED_SUB(unsigned long)
#endif
UNSIGNED_SUB(unsigned long long int)

SIGNED_MUL(char)
SIGNED_MUL(signed char)
SIGNED_MUL(short)
SIGNED_MUL(int)
#ifdef NOISY_LONG
SIGNED_MUL(long)
#endif
SIGNED_MUL(long long int)

UNSIGNED_MUL(unsigned char)
UNSIGNED_MUL(unsigned short)
UNSIGNED_MUL(unsigned int)
#ifdef NOISY_LONG
UNSIGNED_MUL(unsigned long)
#endif
UNSIGNED_MUL(unsigned long long int)

SIGNED_DIV(char)
SIGNED_DIV(signed char)
SIGNED_DIV(short)
SIGNED_DIV(int)
#ifdef NOISY_LONG
SIGNED_DIV(long)
#endif
SIGNED_DIV(long long int)

SIGNED_NEG(char)
SIGNED_NEG(signed char)
SIGNED_NEG(short)
SIGNED_NEG(int)
#ifdef NOISY_LONG
SIGNED_NEG(long)
#endif
SIGNED_NEG(long long int)

UNSIGNED_DIV(unsigned char)
UNSIGNED_DIV(unsigned short)
UNSIGNED_DIV(unsigned int)
#ifdef NOISY_LONG
UNSIGNED_DIV(unsigned long)
#endif
UNSIGNED_DIV(unsigned long long int)

UNSIGNED_NEG(unsigned char)
UNSIGNED_NEG(unsigned short)
UNSIGNED_NEG(unsigned int)
#ifdef NOISY_LONG
UNSIGNED_NEG(unsigned long)
#endif
UNSIGNED_NEG(unsigned long long int)

#if __WORDSIZE == 64
SIGNED_ADD(long int)
UNSIGNED_ADD(unsigned long int)
SIGNED_SUB(long int)
UNSIGNED_SUB(unsigned long int)
SIGNED_MUL(long int)
UNSIGNED_MUL(unsigned long int)
SIGNED_DIV(long int)
SIGNED_NEG(long int)
UNSIGNED_DIV(unsigned long int)
UNSIGNED_NEG(unsigned long int)
#endif

FLOATING_ADD(float)
FLOATING_ADD(double)
FLOATING_ADD(long double)

FLOATING_SUB(float)
FLOATING_SUB(double)
FLOATING_SUB(long double)

FLOATING_MUL(float)
FLOATING_MUL(double)
FLOATING_MUL(long double)

FLOATING_DIV(float)
FLOATING_DIV(double)
FLOATING_DIV(long double)

FLOATING_NEG(float)
FLOATING_NEG(double)
FLOATING_NEG(long double)

#undef UNSIGNED_ADD
#undef SIGNED_ADD
#undef FLOATING_ADD
#undef UNSIGNED_SUB
#undef SIGNED_SUB
#undef FLOATING_SUB
#undef UNSIGNED_MUL
#undef SIGNED_MUL
#undef FLOATING_MUL
#undef UNSIGNED_DIV
#undef SIGNED_DIV
#undef FLOATING_DIV
#undef UNSIGNED_NEG
#undef SIGNED_NEG
#undef FLOATING_NEG

#endif /* disabled or not */

FENNEL_END_CPPFILE("$Id$");

// End NoisyArithmetic.cpp
