/*
 * MetaInfo.java
 *
 * comments here.
 *
 * @author   Michiaki Tatsubori
 * @version  %VERSION% %DATE%
 * @see      java.lang.Object
 *
 * COPYRIGHT 1999 by Michiaki Tatsubori, ALL RIGHTS RESERVED.
 */
package openjava.mop;


import java.lang.reflect.*;
import java.io.*;
import java.util.*;

/**
 * OpenJava metadata about a class.
 *
 * @author   Michiaki Tatsubori
 * @version  1.0
 * @since    %SOFTWARE% 1.0
 * @see java.lang.Object
 */
public final class MetaInfo
{

    public static final String METACLASS_KEY = "instantiates";
    public static final String DEFAULT_METACLASS = "openjava.mop.OJClass";
    public static final String SUFFIX = "OJMI";
    public static final String FIELD_NAME = "dict";

    private Hashtable   table = new Hashtable();
    private String      packname;
    private String      simpleclassname;

    public MetaInfo( String metaclassname, String classname ) {
        table.put( METACLASS_KEY, metaclassname );
        this.simpleclassname = Environment.toSimpleName( classname );
        this.packname = Environment.toPackageName( classname );
    }

    public MetaInfo(String classname) {
        this(defaultMetaclass(classname), classname);
    }

    static String defaultMetaclass(String classname) {
        Class mc = OJSystem.getMetabind(classname);
        if (mc != null)  return mc.getName();
        return DEFAULT_METACLASS;
    }

    private String qualifiedClassName() {
        if (packname == null || packname.equals( "" ))  return simpleclassname;
        return packname + "." + simpleclassname;
    }

    public MetaInfo( Class clazz ) {
        this( clazz.getName() );
        String minfoname = clazz.getName() + SUFFIX;
        try {
            Class minfo = Class.forName( minfoname );
            Field f = minfo.getField( FIELD_NAME );
            String[][] dict = (String[][]) f.get( null );
            for (int i = 0; i < dict.length; ++i) {
                table.put( dict[i][0], dict[i][1] );
            }
        } catch ( ClassNotFoundException e ) {
            table.put(METACLASS_KEY, defaultMetaclass(clazz.getName()));
        } catch ( Exception e ) {
            System.err.println( "meta information class " + minfoname  +
                                " has an illegal structure. : " + e );
            table.put(METACLASS_KEY, defaultMetaclass(clazz.getName()));
        }
    }

    public void write( Writer destout ) throws IOException {
        PrintWriter out = new PrintWriter( destout );
        out.println( "/*this file is generated by OpenJava system.*/" );
        out.println( makePack() );
        out.println( "public final class " + simpleclassname + SUFFIX );
        out.println( "{" );
        out.println( "public static final String[][] " + FIELD_NAME + "={" );

        Enumeration key_i = keys();
        Enumeration value_i = elements();
        if (key_i.hasMoreElements()) {
            out.print( " " );
            printSet( out, key_i.nextElement(), value_i.nextElement() );
        }
        while (key_i.hasMoreElements()) {
            out.print( "," );
            printSet( out, key_i.nextElement(), value_i.nextElement() );
        }

        out.println( "};" );
        out.println( "}" );

        out.flush();
    }

    private String makePack() {
        if (packname == null || packname.equals( "" ))  return "";
        return "package " + packname + ";";
    }

    private void printSet( PrintWriter out, Object keyobj, Object valueobj ) {
        String key = (String) keyobj, value = (String) valueobj;
        out.print( "{\"" );
        out.print( toFlattenString( key ) );
        out.print( "\",\"" );
        out.print( toFlattenString( value ) );
        out.println( "\"}" );
    }

    public String put( String key, String value ) {
        return (String) table.put( key, value );
    }

    public String get( String key ) {
        return (String) table.get( key );
    }

    public Enumeration keys() {
        return table.keys();
    }

    public Enumeration elements() {
        return table.elements();
    }

    public static String toFlattenString( String src_str )  {
        StringBuffer result = null;

        /* cancel double quotes and back slashs */
        StringTokenizer canceller
            = new StringTokenizer( src_str, "\\\"", true );
        result = new StringBuffer();

        while (canceller.hasMoreTokens()) {
            String buf = canceller.nextToken();
            if(buf.equals( "\\" ) || buf.equals( "\"" )){
                result.append( "\\" );
            }
            result.append( buf );
        }

        /* remove new line chars */
        StringTokenizer lnremover
            = new StringTokenizer( result.toString(), "\n\r", false );
        result = new StringBuffer();

        while (lnremover.hasMoreTokens()) {
            result.append( " " + lnremover.nextToken() );
        }

        return result.toString().trim();
    }

}
