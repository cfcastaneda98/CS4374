/*
// $Id$
// Farrago is an extensible data management system.
// Copyright (C) 2005 The Eigenbase Project
// Copyright (C) 2005 SQLstream, Inc.
// Copyright (C) 2005 Dynamo BI Corporation
// Portions Copyright (C) 2003 John V. Sichi
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
package net.sf.farrago.catalog;

import net.sf.farrago.resource.*;

/**
 * FarragoReposTxnContext manages the state of at most one repository
 * transaction. A context may be inactive, meaning it has no current
 * transaction.
 *
 * <p>Always use the following exception-safe transaction pattern:
 *
 * <pre><code>
 *   FarragoReposTxnContext txn = repos.newTxnContext();
 *   try {
 *       txn.beginWriteTxn();
 *       ... do stuff which accesses repository ...
 *       txn.commit();
 *   } finally {
 *       // no effect if already committed or beginWriteTxn failed
 *       txn.rollback();
 *   }
 *</code></pre>
 *
 * @author John V. Sichi
 * @version $Id$
 */
public class FarragoReposTxnContext
{
    //~ Enums ------------------------------------------------------------------

    private enum State
    {
        NO_TXN,

        READ_TXN,

        WRITE_TXN
    }

    //~ Instance fields --------------------------------------------------------

    private FarragoRepos repos;

    private State state;
    private int lockLevel;
    private final boolean manageReposSession;
    private boolean readOnly;

    //~ Constructors -----------------------------------------------------------

    /**
     * Creates a new inactive transaction context with manual repository session
     * management.
     *
     * @param repos the repos against which transactions are to be performed
     */
    public FarragoReposTxnContext(FarragoRepos repos)
    {
        this(repos, false);
    }

    /**
     * Creates a new inactive transaction context.
     *
     * @param repos the repos against which transactions are to be performed
     * @param manageRepoSession if true, a repository session is wrapped around
     * each transaction
     */
    public FarragoReposTxnContext(
        FarragoRepos repos,
        boolean manageRepoSession)
    {
        this(repos, manageRepoSession, false);
    }

    /**
     * Creates a new inactive transaction context that's optionally readonly.
     *
     * @param repos the repos against which transactions are to be performed
     * @param manageRepoSession if true, a repository session is wrapped around
     * each transaction
     */
    public FarragoReposTxnContext(
        FarragoRepos repos,
        boolean manageRepoSession,
        boolean readOnly)
    {
        this.repos = repos;
        state = State.NO_TXN;
        lockLevel = 0;
        this.manageReposSession = manageRepoSession;
        this.readOnly = readOnly;
    }

    //~ Methods ----------------------------------------------------------------

    /**
     * @return whether a transaction is currently in progress
     */
    public boolean isTxnInProgress()
    {
        return state != State.NO_TXN;
    }

    /**
     * @return whether a read-only transaction is currently in progress
     */
    public boolean isReadTxnInProgress()
    {
        return state == State.READ_TXN;
    }

    /**
     * Begins a new read-only transaction.
     */
    public void beginReadTxn()
    {
        assert (!isTxnInProgress());

        if (manageReposSession) {
            repos.beginReposSession();
        }

        repos.beginReposTxn(false);
        state = State.READ_TXN;
    }

    /**
     * Begins a new read/write transaction.
     */
    public void beginWriteTxn()
    {
        assert (!isTxnInProgress());

        if (readOnly) {
            throw FarragoResource.instance().CatalogReadOnly.ex();
        }

        if (manageReposSession) {
            repos.beginReposSession();
        }

        // NOTE jvs 12-Jan-2007:  don't change state until AFTER successfully
        // beginning a transaction; if beginReposTxn throws an excn,
        // we want to stay in State.NO_TXN

        repos.beginReposTxn(true);
        state = State.WRITE_TXN;
    }

    /**
     * Commits the active transaction, if any.
     */
    public void commit()
    {
        if (!isTxnInProgress()) {
            return;
        }

        // NOTE jvs 12-Jan-2007:  change state BEFORE attempting
        // to end transaction; if endReposTxn throws an excn,
        // we're in an unknown state, but further calls could just
        // mask the original excn, so pretend we're back to
        // State.NO_TXN regardless.

        state = State.NO_TXN;
        repos.endReposTxn(false);

        if (manageReposSession) {
            repos.endReposSession();
        }
    }

    /**
     * Rolls back the active transaction, if any.
     */
    public void rollback()
    {
        if (!isTxnInProgress()) {
            return;
        }

        // NOTE jvs 12-Jan-2007:  see comment in commit() for ordering rationale

        state = State.NO_TXN;
        repos.endReposTxn(true);

        if (manageReposSession) {
            repos.endReposSession();
        }
    }

    /**
     * Acquires a repository lock and begins a matching MDR transaction (shared
     * lock for read, or exclusive lock for write). Typical usage is start of
     * SQL statement preparation (e.g. readOnly=true for DML or query, false for
     * DDL).
     *
     * @param readOnly if true, a shared lock is acquired on the catalog;
     * otherwise, an exclusive lock is acquired
     */
    public void beginLockedTxn(boolean readOnly)
    {
        int level = (readOnly) ? 1 : 2;

        // TODO jvs 24-Jan-2007:  Get rid of downcast here and below by
        // making all creation of FarragoReposTxnContext go through
        // factory method interface on FarragoRepos.

        ((FarragoReposImpl) repos).lockRepos(level);

        // Don't set lockLevel until we've successfully acquired the lock
        lockLevel = level;

        if (readOnly) {
            beginReadTxn();
        } else {
            beginWriteTxn();
        }
    }

    /**
     * Releases lock acquired by beginLockedTxn. Caller should already have
     * ended transaction with either commit or rollback.
     */
    public void unlockAfterTxn()
    {
        if (lockLevel != 0) {
            ((FarragoReposImpl) repos).unlockRepos(lockLevel);
            lockLevel = 0;
        }
    }

    /**
     * Puts the repository in exclusive access mode. When in this mode,
     * subsequent attempts to lock the repository will return an exception
     * immediately rather than wait for a required repository lock to become
     * available.
     */
    public void beginExclusiveAccess()
    {
        ((FarragoReposImpl) repos).beginExclusiveAccess();
    }

    /**
     * Ends exclusive access mode for the repository.
     */
    public void endExclusiveAccess()
    {
        ((FarragoReposImpl) repos).endExclusiveAccess();
    }

    /**
     * Switches the repository to read-only mode.
     */
    public void setReposReadOnly()
    {
        readOnly = true;
    }
}

// End FarragoReposTxnContext.java
