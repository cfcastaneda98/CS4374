/*
// $Id$
// Farrago is an extensible data management system.
// Copyright (C) 2005 The Eigenbase Project
// Copyright (C) 2003 SQLstream, Inc.
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
package net.sf.farrago.catalog.codegen;

import java.io.*;

import java.lang.reflect.*;

import java.util.*;

import javax.jmi.model.*;
import javax.jmi.reflect.*;

import net.sf.farrago.*;
import net.sf.farrago.catalog.*;

import org.eigenbase.jmi.*;
import org.eigenbase.util.*;

import org.netbeans.lib.jmi.util.*;


/**
 * FactoryGen generates a factory class for a JMI model. It's purely a
 * convenience; JMI already provides factory methods, but their invocation
 * requires a long ugly expression involving lots of redundancy. For an example
 * of the generated output, see FarragoMetadataFactory.
 *
 * @author John V. Sichi
 * @version $Id$
 */
public class FactoryGen
{
    //~ Static fields/initializers ---------------------------------------------

    private static final int MAX_LARGE_NUMERICS = 4;

    //~ Methods ----------------------------------------------------------------

    /**
     * Main generator entry point invoked by build.xml (target
     * "generateMetadataFactory").
     *
     * @param args <ul>
     * <li>args[0] = target .java file for interface output
     * <li>args[1] = target .java file for class output
     * <li>args[2] = target package name
     * <li>args[3] = target interface name
     * <li>args[4] = target class name
     * <li>args[5] = source package Java metaclass name
     * <li>args[6] = source extent name
     * <li>(optional) args[7] = model timestamp
     * </ul>
     */
    public static void main(String [] args)
        throws ClassNotFoundException, IOException
    {
        assert ((args.length == 7) || (args.length == 8));
        FileWriter writerInterface = new FileWriter(args[0]);
        FileWriter writerClass = new FileWriter(args[1]);
        String targetPackageName = args[2];
        String targetInterfaceName = args[3];
        String targetClassName = args[4];
        String sourcePackageJavaMetaclassName = args[5];
        String sourceExtentName = args[6];
        String modelTimestamp = "NO_TIMESTAMP_ASSIGNED";
        if (args.length == 8) {
            modelTimestamp = args[7];
        }

        FarragoModelLoader modelLoader = null;
        PrintWriter pwInterface = new PrintWriter(writerInterface);
        PrintWriter pwClass = new PrintWriter(writerClass);
        PrintWriter pw = new ForkWriter(pwInterface, pwClass);
        try {
            pw.println("// This code generated by FactoryGen -- do not edit");
            pw.println();
            pw.print("package ");
            pw.print(targetPackageName);
            pw.println(";");
            pw.println();
            pwClass.println(
                "public class " + targetClassName
                + " implements " + targetInterfaceName);
            pwInterface.println("public interface " + targetInterfaceName);
            pw.println("{");
            pwClass.print("    private ");
            pwClass.print(sourcePackageJavaMetaclassName);
            pwClass.println(" rootPackage;");
            pwClass.println();
            pw.print("    public void setRootPackage(");
            pw.print(sourcePackageJavaMetaclassName);
            pw.println(" p)");
            pw.println("    {");
            pw.println("        this.rootPackage = p;");
            pw.println("    }");
            pw.println();
            pw.print("    public ");
            pw.print(sourcePackageJavaMetaclassName);
            pw.println(" getRootPackage()");
            pw.println("    {");
            pw.println("        return rootPackage;");
            pw.println("    }");
            pw.println();
            pw.print("    public String");
            pw.println(" getCompiledModelTimestamp()");
            pw.println("    {");
            pw.println("        return \"" + modelTimestamp + "\";");
            pw.println("    }");
            pw.println();

            modelLoader = new FarragoModelLoader();
            FarragoPackage farragoPackage =
                modelLoader.loadModel(sourceExtentName, false);
            Class sourcePackageInterface =
                Class.forName(
                    sourcePackageJavaMetaclassName);
            RefPackage rootPackage =
                findPackage(
                    farragoPackage,
                    sourcePackageInterface);
            generatePackage(pw, rootPackage, "rootPackage");

            pw.println("}");
        } finally {
            pw.flush();
            writerInterface.close();
            writerClass.close();
            if (modelLoader != null) {
                modelLoader.close();
            }
        }
    }

    private static RefPackage findPackage(RefPackage refPackage, Class iface)
    {
        if (iface.isInstance(refPackage)) {
            return refPackage;
        }
        Collection<RefPackage> allPackages = refPackage.refAllPackages();
        for (RefPackage p : allPackages) {
            RefPackage f = findPackage(p, iface);
            if (f != null) {
                return f;
            }
        }
        return null;
    }

    private static void generatePackage(
        PrintWriter pw,
        RefPackage refPackage,
        String packageAccessor)
        throws ClassNotFoundException
    {
        TagProvider tagProvider = new TagProvider();

        // first generate accessor for package
        Class pkgInterface =
            JmiObjUtil.getJavaInterfaceForRefPackage(refPackage);
        pw.print("    public ");
        pw.print(pkgInterface.getName());
        pw.print(" get");
        pw.print(ReflectUtil.getUnqualifiedClassName(pkgInterface));
        pw.println("()");
        pw.println("    {");
        pw.print("        return ");
        pw.print(packageAccessor);
        pw.println(";");
        pw.println("    }");
        pw.println();

        // then generate factory methods for all classes in package
        Collection<RefClass> allClasses = refPackage.refAllClasses();
        for (RefClass refClass : allClasses) {
            MofClass mofClass = (MofClass) refClass.refMetaObject();
            if (mofClass.isAbstract()) {
                continue;
            }
            Class classInterface =
                JmiObjUtil.getJavaInterfaceForRefObject(refClass);

            validateClass(classInterface);

            String unqualifiedInterfaceName =
                ReflectUtil.getUnqualifiedClassName(classInterface);
            pw.print("    public ");
            pw.print(classInterface.getName());
            pw.print(" new");
            pw.print(unqualifiedInterfaceName);
            pw.println("()");
            pw.println("    {");
            pw.print("        return get");
            pw.print(ReflectUtil.getUnqualifiedClassName(pkgInterface));
            pw.print("().get");
            pw.print(unqualifiedInterfaceName);
            pw.print("().create");
            pw.print(unqualifiedInterfaceName);
            pw.println("();");
            pw.println("    }");
            pw.println();
        }
        Collection<RefPackage> allPackages = refPackage.refAllPackages();
        for (RefPackage refSubPackage : allPackages) {
            MofPackage mofSubPackage =
                (MofPackage) refSubPackage.refMetaObject();

            String subPackageName = mofSubPackage.getName();

            // This is a trick to detect and skip the package aliases which are
            // created for imports.
            Package javaPackage = refPackage.getClass().getPackage();
            Package childJavaPackage = refSubPackage.getClass().getPackage();
            if (!childJavaPackage.getName().equals(
                    javaPackage.getName() + "."
                    + subPackageName.toLowerCase()))
            {
                continue;
            }

            subPackageName = tagProvider.getSubstName(mofSubPackage);

            generatePackage(
                pw,
                refSubPackage,
                packageAccessor + ".get" + subPackageName + "()");
        }
    }

    /**
     * Validates the given class. In particular, this method detects classes
     * that can trigger intermittent bugs in Farrago's MDR implementation.
     *
     * @param classInterface class to validate
     *
     * @see #countLargeNumerics(Class)
     */
    private static void validateClass(Class classInterface)
    {
        int numLargeNumerics = countLargeNumerics(classInterface);

        if (numLargeNumerics > MAX_LARGE_NUMERICS) {
            throw new RuntimeException(
                classInterface.getName()
                + ": A maximum of 4 long and/or double attributes are allowed "
                + "in any catalog class. See FRG-295.");
        }
    }

    /**
     * Counts the number of attributes in the given class that are of type <tt>
     * long</tt> or <tt>double</tt>. The count includes attributes from super
     * interfaces. This method looks at only the methods whose names begin with
     * "set" and are therefore mutators/setters. Attributes of type {@link
     * java.lang.Long} or {@link java.lang.Double} are not counted as large
     * numberics (because they do not require extra operand stack space).
     *
     * <p>This method can be removed when the corresponding bug in MDR is fixed.
     *
     * @param classInterface a Farrago catalog interface class
     *
     * @return the number of longs and doubles in the class
     *
     * @see <a href="http://issues.eigenbase.org/browse/FRG-295">FRG-295</a>
     */
    private static int countLargeNumerics(Class classInterface)
    {
        if ((classInterface == null)
            || !classInterface.getName().startsWith("net.sf.farrago.fem"))
        {
            return 0;
        }

        int numLargeNumerics = 0;
        for (Class superInterface : classInterface.getInterfaces()) {
            numLargeNumerics += countLargeNumerics(superInterface);
        }

        for (Method method : classInterface.getDeclaredMethods()) {
            if (method.getName().startsWith("set")) {
                Class [] paramTypes = method.getParameterTypes();
                if (((paramTypes.length == 1)
                        && (paramTypes[0] == long.class))
                    || (paramTypes[0] == double.class))
                {
                    numLargeNumerics++;
                }
            }
        }

        return numLargeNumerics;
    }

    //~ Inner Classes ----------------------------------------------------------

    /**
     * Writer which takes two underlying writers, and automatically writes
     * method bodies to one, and method prototypes to the other.
     */
    private static class ForkWriter
        extends PrintWriter
    {
        private boolean inMethod;
        private final PrintWriter pwInterface;
        private final PrintWriter pwClass;

        protected ForkWriter(PrintWriter pwInterface, PrintWriter pwClass)
        {
            super(pwClass);
            this.pwInterface = pwInterface;
            this.pwClass = pwClass;
        }

        public void print(String x)
        {
            pwClass.print(x);
            if (!inMethod) {
                pwInterface.print(x);
            }
        }

        public void println(String x)
        {
            pwClass.println(x);
            if (x.equals("    {")) {
                inMethod = true;
            }
            if (!inMethod) {
                pwInterface.println(x);
            }
            if (x.equals("    }")) {
                inMethod = false;
                pwInterface.println(";");
            }
        }

        public void flush()
        {
            pwInterface.flush();
            pwClass.flush();
        }
    }
}

// End FactoryGen.java
