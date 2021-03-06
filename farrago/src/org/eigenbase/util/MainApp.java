/*
// $Id$
// Package org.eigenbase is a class library of data management components.
// Copyright (C) 2005 The Eigenbase Project
// Copyright (C) 2002 SQLstream, Inc.
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
package org.eigenbase.util;

/**
 * Abstract base class for a Java application invoked from the command-line.
 *
 * <p>Example usage:
 *
 * <blockquote>
 * <pre>public class MyClass extends MainApp {
 *     public static void main(String[] args) {
 *         new MyClass(args).run();
 *     }
 *     public void mainImpl() {
 *         System.out.println("Hello, world!");
 *     }
 * }</pre>
 * </blockquote>
 * </p>
 *
 * @author jhyde
 * @version $Id$
 * @since Aug 31, 2003
 */
public abstract class MainApp
{
    //~ Instance fields --------------------------------------------------------

    protected final String [] args;
    private OptionsList options = new OptionsList();
    private int exitCode;

    //~ Constructors -----------------------------------------------------------

    protected MainApp(String [] args)
    {
        this.args = args;
        exitCode = 0;
    }

    //~ Methods ----------------------------------------------------------------

    /**
     * Does the work of the application. Derived classes must implement this
     * method; they can throw any exception they like, and {@link #run} will
     * clean up after them.
     */
    public abstract void mainImpl()
        throws Exception;

    /**
     * Does the work of the application, handles any errors, then calls {@link
     * System#exit} to terminate the application.
     */
    public final void run()
    {
        try {
            initializeOptions();
            mainImpl();
        } catch (Throwable e) {
            handle(e);
        }
        System.exit(exitCode);
    }

    /**
     * Sets the code which this program will return to the operating system.
     *
     * @param exitCode Exit code
     *
     * @see System#exit
     */
    public void setExitCode(int exitCode)
    {
        this.exitCode = exitCode;
    }

    /**
     * Handles an error. Derived classes may override this method to provide
     * their own error-handling.
     *
     * @param throwable Error to handle.
     */
    public void handle(Throwable throwable)
    {
        throwable.printStackTrace();
    }

    public void parseOptions(OptionsList.OptionHandler values)
    {
        options.parse(args);
    }

    /**
     * Initializes the application.
     */
    protected void initializeOptions()
    {
        options.add(
            new OptionsList.BooleanOption(
                "-h",
                "help",
                "Prints command-line parameters",
                false,
                false,
                false,
                null));
    }
}

// End MainApp.java
