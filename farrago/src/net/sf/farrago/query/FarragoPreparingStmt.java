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
package net.sf.farrago.query;

import java.io.*;

import java.net.*;

import java.sql.*;

import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

import net.sf.farrago.catalog.*;
import net.sf.farrago.cwm.core.*;
import net.sf.farrago.cwm.relational.*;
import net.sf.farrago.defimpl.*;
import net.sf.farrago.fem.fennel.*;
import net.sf.farrago.fem.med.*;
import net.sf.farrago.fem.security.*;
import net.sf.farrago.fem.sql2003.*;
import net.sf.farrago.fennel.*;
import net.sf.farrago.namespace.*;
import net.sf.farrago.resource.*;
import net.sf.farrago.session.*;
import net.sf.farrago.trace.*;
import net.sf.farrago.type.*;
import net.sf.farrago.util.*;

import openjava.mop.*;

import openjava.ptree.*;

import org.eigenbase.jmi.*;
import org.eigenbase.oj.rel.*;
import org.eigenbase.oj.stmt.*;
import org.eigenbase.oj.util.*;
import org.eigenbase.rel.*;
import org.eigenbase.rel.convert.*;
import org.eigenbase.rel.metadata.*;
import org.eigenbase.relopt.*;
import org.eigenbase.reltype.*;
import org.eigenbase.rex.*;
import org.eigenbase.sql.*;
import org.eigenbase.sql.advise.*;
import org.eigenbase.sql.util.*;
import org.eigenbase.sql.validate.*;
import org.eigenbase.sql2rel.*;
import org.eigenbase.util.*;


/**
 * FarragoPreparingStmt subclasses OJPreparingStmt to implement the {@link
 * FarragoSessionPreparingStmt} interface.
 *
 * @author John V. Sichi
 * @version $Id$
 */
public class FarragoPreparingStmt
    extends OJPreparingStmt
    implements FarragoSessionPreparingStmt,
        RelOptConnection,
        RelOptSchemaWithSampling,
        SqlValidatorCatalogReader
{
    //~ Static fields/initializers ---------------------------------------------

    // NOTE jvs 8-June-2004: this tracer is special in that it controls
    // preservation of dynamically generated Java code
    private static final Logger dynamicTracer = FarragoTrace.getDynamicTracer();
    private static final Logger streamGraphTracer =
        FarragoTrace.getPreparedStreamGraphTracer();
    private static final Logger planDumpTracer =
        FarragoTrace.getPlanDumpTracer();

    // REVIEW jvs 11-Oct-2006:  For debugging it would be handier to
    // use the db-level stmt ID here.
    private static final AtomicLong idGen = new AtomicLong();

    //~ Instance fields --------------------------------------------------------

    private final String sql;
    private final FarragoSessionStmtValidator stmtValidator;
    private boolean needRestore;
    protected SqlToRelConverter sqlToRelConverter;
    private OJClass savedDeclarer;
    private FarragoAllocation javaCodeDir;
    protected SqlValidatorImpl sqlValidator;
    private final Set<CwmModelElement> directDependencies;
    protected final Set<CwmModelElement> allDependencies;
    private final Set<URL> jarUrlSet;
    protected SqlOperatorTable sqlOperatorTable;
    private final FarragoUserDefinedRoutineLookup routineLookup;
    private int expansionDepth;
    private RelDataType originalRowType;
    private SqlIdentifier dmlTarget;
    private PrivilegedAction dmlAction;
    private TableAccessMap tableAccessMap;
    protected ChainedRelMetadataProvider relMetadataProvider;
    private boolean allowPartialImplementation;
    private final Map<String, RelDataType> resultSetTypeMap;
    private final Map<String, RelDataType> iterCalcTypeMap;
    private boolean cachingDisabled;
    private FarragoPreparingStmt parentStmt;
    private final FarragoSessionStmtContext rootStmtContext;

    /**
     * Name of Java package containing code generated for this statement.
     */
    private String packageName;

    /**
     * Directory containing code generated for this statement.
     */
    private File packageDir;

    /**
     * Root directory for all generated Java.
     */
    private File classesRoot;

    // attributes of the openjava code generated to implement the statement:
    private ClassDeclaration implementingClassDecl;
    private Argument [] implementingArgs;
    private Set<String> loadedServerClassNameSet;
    private FarragoSessionPlanner planner;
    private FarragoRelImplementor relImplementor;

    //~ Constructors -----------------------------------------------------------

    /**
     * Creates a new FarragoPreparingStmt object.
     *
     * @param rootStmtContext the root statement context
     * @param stmtValidator generic stmt validator
     * @param sql SQL text of statement being prepared
     */
    public FarragoPreparingStmt(
        FarragoSessionStmtContext rootStmtContext,
        FarragoSessionStmtValidator stmtValidator,
        String sql)
    {
        super(null);

        timingTracer = stmtValidator.getTimingTracer();

        this.stmtValidator = stmtValidator;
        this.sql = sql;
        stmtValidator.addAllocation(this);

        loadedServerClassNameSet = new HashSet<String>();

        super.setResultCallingConvention(CallingConvention.ITERATOR);

        directDependencies = new HashSet<CwmModelElement>();
        allDependencies = new HashSet<CwmModelElement>();
        jarUrlSet = new LinkedHashSet<URL>();

        classesRoot = new File(FarragoProperties.instance().homeDir.get(true));
        classesRoot = new File(classesRoot, "classes");

        // Save some global state for reentrancy
        needRestore = true;
        savedDeclarer = OJUtil.threadDeclarers.get();
        OJSystem.env.pushThreadTempFrame();

        routineLookup =
            new FarragoUserDefinedRoutineLookup(
                stmtValidator,
                this,
                null);

        resultSetTypeMap = new HashMap<String, RelDataType>();
        iterCalcTypeMap = new HashMap<String, RelDataType>();

        clearDmlValidation();

        relMetadataProvider = new DefaultRelMetadataProvider();

        // Chain Farrago metadata just above the default. This allows it to
        // provide metadata for rels that are not handled by other providers,
        // but not to override the behavior of other providers.
        relMetadataProvider.addProvider(
            new FarragoRelMetadataProvider(getRepos()));

        this.rootStmtContext = rootStmtContext;
        this.parentStmt = null;
    }

    //~ Methods ----------------------------------------------------------------

    // implement FarragoSessionPreparingStmt: cache everything
    public boolean mayCacheImplementation()
    {
        if (cachingDisabled) {
            return false;
        }
        return true;
    }

    // implement FarragoSessionPreparingStmt
    public void disableStatementCaching()
    {
        cachingDisabled = true;
    }

    // implement FarragoSessionPreparingStmt
    public FarragoSessionStmtContext getRootStmtContext()
    {
        return rootStmtContext;
    }

    public FarragoSessionStmtValidator getStmtValidator()
    {
        return stmtValidator;
    }

    /**
     * @return the FarragoPreparingStmt that's the parent of this one
     */
    public FarragoPreparingStmt getParentStmt()
    {
        return parentStmt;
    }

    /**
     * Sets the parent FarragoPreparingStmt corresponding to this statement.
     * This is used when this is a reentrant statement. The parent corresponds
     * to the statement that requires the reentrant statement.
     *
     * @param parentStmt the parent of this statement
     */
    public void setParentStmt(FarragoPreparingStmt parentStmt)
    {
        this.parentStmt = parentStmt;
    }

    public void setPlanner(FarragoSessionPlanner planner)
    {
        assert (this.planner == null);
        this.planner = planner;
        if (rootStmtContext != null) {
            planner.setCancelFlag(rootStmtContext.getCancelFlag());
        }
        getSession().getPersonality().definePlannerListeners(planner);
    }

    /**
     * Tells this statement not to throw an exception if optimizer can't find a
     * valid physical plan. This is intended for use mainly by unit tests which
     * need to peer into intermediate query optimization states.
     */
    public void enablePartialImplementation()
    {
        allowPartialImplementation = true;
    }

    public FarragoSessionPlanner getPlanner()
    {
        return planner;
    }

    // implement FarragoSessionPreparingStmt
    public SqlOperatorTable getSqlOperatorTable()
    {
        if (sqlOperatorTable != null) {
            return sqlOperatorTable;
        }

        SqlOperatorTable systemOperators =
            getSession().getPersonality().getSqlOperatorTable(this);

        ChainedSqlOperatorTable table = new ChainedSqlOperatorTable();
        table.add(routineLookup);
        table.add(systemOperators);

        sqlOperatorTable = table;
        return sqlOperatorTable;
    }

    // implement FarragoSessionPreparingStmt
    public SqlValidator getSqlValidator()
    {
        if (sqlValidator == null) {
            sqlValidator =
                new FarragoSqlValidator(
                    this,
                    SqlConformance.Default);
        }
        return sqlValidator;
    }

    // implement FarragoSessionPreparingStmt
    public FarragoSessionExecutableStmt prepare(
        SqlNode sqlNode,
        SqlNode sqlNodeOriginal)
    {
        // REVIEW(rchen 2006-08-08)
        // Should the state needValidation be kept in FarragoPreparingStmt, and
        // modified in the method postValidate()?
        // Deriving it locally from sqlValidator does not seem very clean:
        // a validator might have been allocated but no validation has been
        // performed.
        boolean needValidation = false;
        if (sqlValidator == null) {
            getSqlValidator();
            needValidation = true;
        }

        definePackageName();
        PreparedResult preparedResult =
            prepareSql(
                sqlNode,
                sqlNodeOriginal,
                getSession().getPersonality().getRuntimeContextClass(
                    this),
                sqlValidator,
                needValidation);
        return implement(preparedResult);
    }

    // implement FarragoSessionPreparingStmt
    public void preImplement()
    {
        definePackageName();
        initClassDecl();
    }

    protected PreparedResult implement0(
        RelDataType rowType,
        RelNode rootRel,
        SqlKind sqlKind,
        ClassDeclaration decl,
        Argument[] args)
    {
        // Ignore passed in class declaration and args, and use the ones created
        // by preIncrement.
        return implement(
            rowType, rootRel, sqlKind, implementingClassDecl, implementingArgs);
    }

    // implement FarragoSessionPreparedStmt
    public SqlAdvisor getAdvisor()
    {
        SqlValidatorWithHints validator =
            new SqlAdvisorValidator(
                getSqlOperatorTable(),
                this,
                getSqlValidator().getTypeFactory(),
                SqlConformance.Default);
        return new SqlAdvisor(validator);
    }

    protected void initClassDecl()
    {
        if (implementingClassDecl == null) {
            implementingArgs =
                new Argument[] {
                    new Argument(
                        connectionVariable,
                        getSession().getPersonality().getRuntimeContextClass(
                            this),
                        this)
                };
            implementingClassDecl = super.init(implementingArgs);
        }
    }

    protected TableAccessMap getTableAccessMap()
    {
        return tableAccessMap;
    }

    protected Map<String, RelDataType> getResultSetTypeMap()
    {
        return resultSetTypeMap;
    }

    protected Map<String, RelDataType> getIterCalcTypeMap()
    {
        return iterCalcTypeMap;
    }

    protected List<FarragoTransformDef> getTransformDefs()
    {
        return relImplementor.getTransforms();
    }

    // implement FarragoSessionPreparingStmt
    public void postValidate(SqlNode sqlNode)
    {
        analyzeRoutineDependencies(sqlNode);

        // Now that we're done with validation, perform any deferred
        // privilege checks.
        stmtValidator.getPrivilegeChecker().checkAccess();
    }

    // implement FarragoSessionPreparingStmt
    public FarragoSessionExecutableStmt implement(
        RelNode rootRel,
        SqlKind sqlKind,
        boolean logical)
    {
        PreparedResult preparedResult =
            prepareSql(
                rootRel.getRowType(),
                rootRel,
                sqlKind,
                logical,
                implementingClassDecl,
                implementingArgs);

        // When rootRel is a logical plan, optimize() sets the map, but for a
        // physical plan, set it here:
        if (!logical) {
            tableAccessMap = new TableAccessMap(rootRel);
        }
        return implement(preparedResult);
    }

    /**
     * @return lookup table for user-defined routines
     */
    public FarragoUserDefinedRoutineLookup getRoutineLookup()
    {
        return routineLookup;
    }

    void addJarUrl(String jarUrl)
    {
        try {
            jarUrlSet.add(new URL(jarUrl));
        } catch (MalformedURLException ex) {
            // this shouldn't happen, because the caller is already
            // supposed to have verified the URL
            throw Util.newInternal();
        }
    }

    public void prepareForCompilation()
    {
        // REVIEW jvs 20-Jan-2005: The idea here is to gather up all jars
        // referenced by external user-defined routines and provide them to the
        // classloader.  However, this loses the associations between jars and
        // routines, meaning if two classes in different jars have the same
        // name, there will be trouble.  The alternative is to always use
        // reflection, which would be bad for UDF performance.  What to do?
        // Also, need to implement jar paths.

        URL [] urls = jarUrlSet.toArray(new URL[jarUrlSet.size()]);
        URLClassLoader urlClassLoader =
            URLClassLoader.newInstance(
                urls,
                getSession().getPluginClassLoader());
        javaCompiler.getArgs().setClassLoader(urlClassLoader);
    }

    private void definePackageName()
    {
        String packageNameUnqualified = "stmt" + idGen.incrementAndGet();

        // NOTE:  we're not actually creating the directory here, because
        // we might decide we don't actually need any Java compilation;
        // but we need to know its name during preparation in case it
        // turns out that we are going to compile something
        packageDir = classesRoot;
        packageDir = new File(packageDir, "net");
        packageDir = new File(packageDir, "sf");
        packageDir = new File(packageDir, "farrago");
        packageDir = new File(packageDir, "dynamic");
        packageDir = new File(packageDir, packageNameUnqualified);

        packageName = "net.sf.farrago.dynamic." + packageNameUnqualified;
    }

    // expose to this package, for FarragoTransformDef
    protected Class compileClass(
        String packageName,
        String className,
        String source)
    {
        return super.compileClass(packageName, className, source);
    }

    // Override OJPreparingStmt
    protected BoundMethod compileAndBind(
        ClassDeclaration decl,
        ParseTree parseTree,
        Argument [] arguments)
    {
        if (packageDir.exists()) {
            // Blow away existing package; could be left over from
            // a previous run.
            new FarragoFileAllocation(packageDir).closeAllocation();
        }

        // Normally, we want to make sure all generated code gets cleaned up.
        // To disable this for debugging, you can explicitly set
        // net.sf.farrago.dynamic.level=FINE.  (This is not inherited via
        // parent logger.)
        if (!shouldAlwaysWriteJavaFile()) {
            javaCodeDir = new FarragoFileAllocation(packageDir);
        }

        packageDir.mkdirs();

        BoundMethod boundMethod =
            super.compileAndBind(decl, parseTree, arguments);

        streamGraphTracer.finer("compile FarragoTransforms");
        relImplementor.compileTransforms(getTempPackageName());

        return boundMethod;
    }

    protected boolean treeContainsJava(RelNode rootRel)
    {
        // if the topmost node isn't a FennelToIteratorConverter, then
        // we know we don't have a pure Fennel plan; otherwise, check
        // the rest of the tree, ignoring the converter, which we'll strip
        // off later if it turns out we do have a pure Fennel plan
        if (!(rootRel instanceof FennelToIteratorConverter)) {
            return true;
        }
        return super.treeContainsJava(rootRel.getInput(0));
    }

    protected FarragoSessionExecutableStmt implement(
        PreparedResult preparedResult)
    {
        FarragoSessionExecutableStmt executableStmt;
        if (preparedResult instanceof PreparedExecution) {
            PreparedExecution preparedExecution =
                (PreparedExecution) preparedResult;
            RelDataType rowType = preparedExecution.getPhysicalRowType();
            if (fieldOrigins == null
                || rowType.getFieldCount() != fieldOrigins.size())
            {
                fieldOrigins =
                    Collections.nCopies(rowType.getFieldCount(), null);
            }
            RelDataType dynamicParamRowType = getParamRowType();

            String streamName = null;
            if (!containsJava) {
                RelNode rootRel = preparedExecution.getRootRel();
                if (relImplementor == null) {
                    relImplementor =
                        newRelImplementor(rootRel.getCluster().getRexBuilder());
                }
                FemExecutionStreamDef streamDef =
                    relImplementor.visitFennelChild((FennelRel) rootRel, 0);
                streamName = streamDef.getName();
            }

            String xmiFennelPlan = null;
            Set<FemExecutionStreamDef> streamDefSet =
                relImplementor.getStreamDefSet();
            if (!streamDefSet.isEmpty()) {
                FemCmdPrepareExecutionStreamGraph cmdPrepareStream =
                    getRepos().newFemCmdPrepareExecutionStreamGraph();

                // FIXME jvs 22-Jul-2008:  this does not play well
                // with statement caching, since different sessions
                // may have different settings for DOP, but the DOP
                // is not part of the cache key
                cmdPrepareStream.setDegreeOfParallelism(
                    getSession().getSessionVariables().getInteger(
                        FarragoDefaultSessionPersonality
                            .DEGREE_OF_PARALLELISM));

                Collection<FemExecutionStreamDef> streamDefs =
                    cmdPrepareStream.getStreamDefs();
                streamDefs.addAll(streamDefSet);
                xmiFennelPlan =
                    JmiObjUtil.exportToXmiString(
                        Collections.singleton(cmdPrepareStream));
                streamGraphTracer.fine(xmiFennelPlan);
            }

            assert (tableAccessMap != null);
            if (containsJava) {
                OJClass ojRowClass =
                    OJUtil.typeToOJClass(
                        rowType,
                        getFarragoTypeFactory());
                Class rowClass;
                try {
                    String ojRowClassName = ojRowClass.getName();
                    int i = ojRowClassName.lastIndexOf('.');
                    assert (i != -1);
                    ojRowClassName =
                        OJUtil.replaceDotWithDollar(ojRowClassName, i);
                    rowClass =
                        Class.forName(
                            ojRowClassName,
                            true,
                            javaCompiler.getClassLoader());
                } catch (ClassNotFoundException ex) {
                    throw Util.newInternal(ex);
                }
                final RelDataType preparedRowType =
                    (originalRowType == null) ? rowType : originalRowType;
                if (fieldOrigins == null
                    || preparedRowType.getFieldCount() != fieldOrigins.size())
                {
                    fieldOrigins =
                        Collections.nCopies(
                            preparedRowType.getFieldCount(), null);
                }
                executableStmt =
                    new FarragoExecutableJavaStmt(
                        packageDir,
                        rowClass,
                        javaCompiler.getClassLoader(),
                        preparedRowType,
                        fieldOrigins,
                        dynamicParamRowType,
                        preparedExecution.getMethod(),
                        getTransformDefs(),
                        xmiFennelPlan,
                        preparedResult.isDml(),
                        preparedResult.getTableModOp(),
                        getReferencedObjectTimestampMap(),
                        tableAccessMap,
                        resultSetTypeMap,
                        iterCalcTypeMap,
                        javaCompiler.getTotalByteCodeSize());
            } else {
                executableStmt =
                    new FarragoExecutableFennelStmt(
                        rowType,
                        fieldOrigins,
                        dynamicParamRowType,
                        xmiFennelPlan,
                        streamName,
                        preparedResult.isDml(),
                        preparedResult.getTableModOp(),
                        getReferencedObjectTimestampMap(),
                        tableAccessMap,
                        resultSetTypeMap);
            }
        } else {
            assert (preparedResult instanceof PreparedExplanation);
            executableStmt =
                new FarragoExecutableExplainStmt(
                    getFarragoTypeFactory().createStructType(
                        Collections.<Pair<String, RelDataType>>emptyList()),
                    preparedResult.getCode());
        }

        // generated code is now the responsibility of executableStmt
        if (javaCodeDir != null) {
            executableStmt.addAllocation(javaCodeDir);
            javaCodeDir = null;
        }

        return executableStmt;
    }

    // implement FarragoSessionPreparingStmt
    public void analyzeSql(
        SqlNode sqlNode,
        final FarragoSessionAnalyzedSql analyzedSql)
    {
        RelNode rootRel = null;

        getSqlToRelConverter();
        if (analyzedSql.paramRowType == null) {
            // query expression
            rootRel = sqlToRelConverter.convertQuery(
                sqlNode, false, SqlToRelConverter.QueryContext.TOP);
            analyzedSql.setResultType(rootRel.getRowType());
            analyzedSql.paramRowType = getParamRowType();
        } else {
            // parameterized row expression
            analyzedSql.resultType =
                getSqlValidator().getRootNodeType(sqlNode);
        }
        analyzedSql.dependencies =
            Collections.unmodifiableSet(directDependencies);

        // walk the expression looking for dynamic parameters
        SqlVisitor<Void> dynamicParamFinder =
            new SqlBasicVisitor<Void>() {
                public Void visit(SqlDynamicParam param)
                {
                    analyzedSql.hasDynamicParams = true;
                    return super.visit(param);
                }
            };
        sqlNode.accept(dynamicParamFinder);

        // For queries, fill in some more information.
        if (rootRel != null) {
            if (analyzedSql.optimized) {
                rootRel = flattenTypes(rootRel, true);

                rootRel =
                    optimize(
                        rootRel.getRowType(),
                        rootRel);

                // From here on, use the planner's notion of root, because the
                // rootRel it returned to us may have lost some metadata.
                // TODO: clean this up.
                rootRel = planner.getRoot();
            }

            // Derive information about origin of each column
            List<Set<RelColumnOrigin>> columnOrigins =
                new ArrayList<Set<RelColumnOrigin>>();
            List<RelDataTypeField> fieldList =
                analyzedSql.resultType.getFieldList();
            for (int i = 0; i < fieldList.size(); ++i) {
                Set<RelColumnOrigin> rcoSet =
                    RelMetadataQuery.getColumnOrigins(rootRel, i);
                if (rcoSet == null) {
                    // If we don't know, assume none.
                    rcoSet = Collections.emptySet();
                }
                columnOrigins.add(rcoSet);
            }
            analyzedSql.columnOrigins =
                Collections.unmodifiableList(columnOrigins);

            if (analyzedSql.optimized) {
                analyzedSql.rowCount =
                    RelMetadataQuery.getRowCount(
                        rootRel);
            }
        }
    }

    void analyzeRoutineDependencies(SqlNode sqlNode)
    {
        // Round up all the dependencies on UDF's.  We can't do this during
        // function lookup because overloads need to be resolved first.  And we
        // can't do this during SqlToRelConverter because then we stop
        // collecting direct dependencies.
        SqlVisitor<Void> udfInvocationFinder =
            new SqlBasicVisitor<Void>() {
                public Void visit(SqlCall call)
                {
                    if (call.getOperator()
                        instanceof FarragoUserDefinedRoutine)
                    {
                        FarragoUserDefinedRoutine function =
                            (FarragoUserDefinedRoutine) call.getOperator();
                        addDependency(
                            function.getFemRoutine(),
                            PrivilegedActionEnum.EXECUTE);
                    }
                    return super.visit(call);
                }
            };
        sqlNode.accept(udfInvocationFinder);
    }

    protected Map<String, String> getReferencedObjectTimestampMap()
    {
        Map<String, String> map = new HashMap<String, String>();
        for (CwmModelElement refObj : allDependencies) {
            String modTime = null;
            if (refObj instanceof FemAnnotatedElement) {
                FemAnnotatedElement annotated = (FemAnnotatedElement) refObj;
                modTime = annotated.getModificationTimestamp();
            }
            map.put(refObj.refMofId(), modTime);
        }
        return map;
    }

    // implement FarragoSessionPreparingStmt
    public SqlToRelConverter getSqlToRelConverter()
    {
        return getSqlToRelConverter(getSqlValidator(), this);
    }

    // implement FarragoSessionPreparingStmt
    public RelOptCluster getRelOptCluster()
    {
        return getSqlToRelConverter().getCluster();
    }

    protected RelNode flattenTypes(
        RelNode rootRel,
        boolean restructure)
    {
        boolean dumpPlan = planDumpTracer.isLoggable(Level.FINE);

        if (dumpPlan) {
            planDumpTracer.fine(
                RelOptUtil.dumpPlan(
                    "Plan before flattening",
                    rootRel,
                    false,
                    SqlExplainLevel.EXPPLAN_ATTRIBUTES));
        }

        RelNode newRootRel =
            getSqlToRelConverter().flattenTypes(rootRel, restructure);

        if (timingTracer != null) {
            timingTracer.traceTime("end type flattening and view expansion");
        }
        if (dumpPlan) {
            planDumpTracer.fine(
                RelOptUtil.dumpPlan(
                    "Plan after flattening",
                    newRootRel,
                    false,
                    SqlExplainLevel.EXPPLAN_ATTRIBUTES));
        }
        return newRootRel;
    }

    protected RelNode decorrelate(
        SqlNode query,
        RelNode rootRel)
    {
        boolean dumpPlan = planDumpTracer.isLoggable(Level.FINE);

        // Now that all plugins have been seen (flattening above expanded
        // views), finalize the relational expression metadata query providers
        // to use during decorrelation.
        finalizeRelMetadata(rootRel);

        RelNode newRootRel = getSqlToRelConverter().decorrelate(query, rootRel);

        if (dumpPlan) {
            planDumpTracer.fine(
                RelOptUtil.dumpPlan(
                    "Plan after decorrelating RelNode",
                    newRootRel,
                    false,
                    SqlExplainLevel.EXPPLAN_ATTRIBUTES));
        }
        return newRootRel;
    }

    protected RelNode trimUnusedFields(RelNode rootRel)
    {
        return getSqlToRelConverter().trimUnusedFields(rootRel);
    }

    protected RelNode optimize(RelDataType rowType, RelNode rootRel)
    {
        boolean dumpPlan = planDumpTracer.isLoggable(Level.FINE);
        originalRowType = rowType;

        // Since rootRel might have changed, first finalize the relational
        // expression metadata query providers to use during optimization.
        finalizeRelMetadata(rootRel);

        RelTraitSet desiredTraits = getDesiredRootTraitSet(rootRel);

        rootRel = super.optimize(rowType, rootRel);
        if (dumpPlan) {
            planDumpTracer.fine(
                RelOptUtil.dumpPlan(
                    "Plan after optimization",
                    rootRel,
                    false,
                    SqlExplainLevel.ALL_ATTRIBUTES));
        }

        // Validate that plan satisfies all required trait conversions.  This
        // implicitly validates that a physical implementation was found for
        // every node.
        if (!allowPartialImplementation) {
            try {
                validatePlan(rootRel, desiredTraits);
            } catch (InvalidPlanException e) {
                // Dump plan unless we already did above.
                if (!dumpPlan) {
                    planDumpTracer.severe(
                        RelOptUtil.dumpPlan(
                            "Plan without full implementation",
                            rootRel,
                            false,
                            SqlExplainLevel.ALL_ATTRIBUTES));
                }
                throw FarragoResource.instance().SessionOptimizerFailed.ex(
                    e.rel.toString(),
                    e.getMessage(),
                    getSql());
            }
        }

        // REVIEW jvs 9-Mar-2006: Perhaps we should compute two
        // tableAccessMaps, one before and one after optimization, and then
        // merge them.  Leaving out ones from before could lead us to avoid
        // locking a table which gets pruned, which might be good for
        // concurrency in some circumstances, but bad for correctness in
        // others.  Leaving out ones from after would screw up an optimizer
        // which supports materialized view rewrite.
        tableAccessMap = new TableAccessMap(rootRel);

        return rootRel;
    }

    protected void validatePlan(RelNode rel, RelTraitSet desiredTraits)
        throws InvalidPlanException
    {
        if (!rel.getTraits().matches(desiredTraits)) {
            throw new InvalidPlanException(
                "Node's traits (" + rel.getTraits()
                + ") do not match required traits (" + desiredTraits + ")",
                rel);
        }
        if (rel instanceof ConverterRel) {
            ConverterRel converterRel = (ConverterRel) rel;
            validatePlan(
                converterRel.getChild(),
                converterRel.getInputTraits());
        } else {
            for (RelNode child : rel.getInputs()) {
                validatePlan(child, rel.getTraits());
            }
        }
    }

    public void finalizeRelMetadata(RelNode rootRel)
    {
        if (relMetadataProvider == null) {
            // already finalized
            return;
        }

        // Give personality priority over anything set up so far.
        getSession().getPersonality().registerRelMetadataProviders(
            relMetadataProvider);

        // Add caching on top of all that.
        CachingRelMetadataProvider cacheProvider =
            new CachingRelMetadataProvider(relMetadataProvider, planner);

        // Put the planner at the head of its own chain before all the rest.
        // It's a bad idea to cache the planner's results.

        ChainedRelMetadataProvider plannerChain =
            new ChainedRelMetadataProvider();
        plannerChain.addProvider(cacheProvider);
        planner.registerMetadataProviders(plannerChain);
        rootRel.getCluster().setMetadataProvider(plannerChain);

        // Remind ourselves that we're done setting this guy up,
        // so any further access to it is an error.
        relMetadataProvider = null;
    }

    protected RelDataType getParamRowType()
    {
        return getFarragoTypeFactory().createStructType(
            new RelDataTypeFactory.FieldInfo() {
                public int getFieldCount()
                {
                    return sqlToRelConverter.getDynamicParamCount();
                }

                public String getFieldName(int index)
                {
                    return "?" + index;
                }

                public RelDataType getFieldType(int index)
                {
                    return sqlToRelConverter.getDynamicParamType(index);
                }
            });
    }

    // implement FarragoAllocation
    public void closeAllocation()
    {
        if (!needRestore) {
            // already closed or else never opened
            return;
        }
        OJSystem.env.popThreadTempFrame();
        OJUtil.threadDeclarers.set(savedDeclarer);

        // TODO:  obtain locks to ensure that objects we intend to operate
        // on don't change after we end repository txn.
        if (javaCodeDir != null) {
            javaCodeDir.closeAllocation();
            javaCodeDir = null;
        }
        needRestore = false;
    }

    /**
     * Returns a relational expression which is to be substituted for an access
     * to a SQL view.
     *
     * @param rowType Row type of the view
     * @param queryString Body of the view
     *
     * @return Relational expression
     */
    protected RelNode expandView(RelDataType rowType, String queryString)
    {
        expansionDepth++;

        FarragoSessionParser parser =
            getSession().getPersonality().newParser(getSession());
        final SqlNode sqlQuery =
            (SqlNode) parser.parseSqlText(
                stmtValidator,
                null,
                queryString,
                true);
        RelNode relNode =
            sqlToRelConverter.convertQuery(
                sqlQuery, true, SqlToRelConverter.QueryContext.VIEW);

        --expansionDepth;
        return relNode;
    }

    RexNode expandInvocationExpression(
        SqlNode sqlExpr,
        FarragoRoutineInvocation invocation)
    {
        expansionDepth++;

        // NOTE jvs 2-Jan-2005: We already validated the expression during DDL,
        // but we stored the original pre-validation expression, and validation
        // may have involved rewrites relied on by sqlToRelConverter.  So we
        // must recapitulate here.
        sqlExpr =
            getSqlValidator().validateParameterizedExpression(
                sqlExpr,
                invocation.getParamNameToTypeMap());

        // TODO jvs 1-Jan-2005: support a RexVariableBinding (like "let" in
        // Lisp), and avoid expansion of parameters which are referenced more
        // than once

        RexNode rexNode =
            sqlToRelConverter.convertExpression(
                sqlExpr,
                invocation.getParamNameToArgMap());
        --expansionDepth;
        return rexNode;
    }

    public void setDmlValidation(SqlIdentifier target, PrivilegedAction action)
    {
        dmlTarget = target;
        dmlAction = action;
    }

    public void clearDmlValidation()
    {
        dmlTarget = null;
        dmlAction = null;
    }

    /**
     * @return true iff currently expanding a view or function
     */
    public boolean isExpandingDefinition()
    {
        return expansionDepth > 0;
    }

    protected SqlToRelConverter getSqlToRelConverter(
        SqlValidator validator,
        RelOptConnection connection)
    {
        // REVIEW:  recycling may be dangerous since SqlToRelConverter is
        // stateful
        if (sqlToRelConverter == null) {
            sqlToRelConverter =
                new SqlToRelConverter(
                    validator,
                    connection.getRelOptSchema(),
                    getEnvironment(),
                    planner,
                    connection,
                    new FarragoRexBuilder(this));
            sqlToRelConverter.setDefaultValueFactory(
                new ReposDefaultValueFactory(this));
            if (getSession().getSessionVariables().getBoolean(
                    FarragoDefaultSessionPersonality
                    .REDUCE_NON_CORRELATED_SUBQUERIES))
            {
                sqlToRelConverter.setSubqueryConverter(
                    new ScalarSubqueryConverter(this));
            }
            sqlToRelConverter.enableTableAccessConversion(false);
            sqlToRelConverter.setTrimUnusedFields(false);

            // currently the only physical implementation available
            // for ValuesRel is FennelValuesRel
            sqlToRelConverter.enableValuesRelCreation(
                getRepos().isFennelEnabled());
        }
        return sqlToRelConverter;
    }

    // implement FarragoSessionPreparingStmt
    public JavaRelImplementor getRelImplementor(RexBuilder rexBuilder)
    {
        if (relImplementor == null) {
            relImplementor = newRelImplementor(rexBuilder);
        }
        return relImplementor;
    }

    protected FarragoRelImplementor newRelImplementor(RexBuilder rexBuilder)
    {
        return new FarragoRelImplementor(this, rexBuilder);
    }

    // implement FarragoSessionPreparingStmt
    public FarragoRepos getRepos()
    {
        return stmtValidator.getRepos();
    }

    // implement FarragoSessionPreparingStmt
    public FennelDbHandle getFennelDbHandle()
    {
        return stmtValidator.getFennelDbHandle();
    }

    // implement FarragoSessionPreparingStmt
    public FarragoTypeFactory getFarragoTypeFactory()
    {
        return stmtValidator.getTypeFactory();
    }

    // implement FarragoSessionPreparingStmt
    public FarragoSessionIndexMap getIndexMap()
    {
        return stmtValidator.getIndexMap();
    }

    // implement FarragoSessionPreparingStmt
    public FarragoSession getSession()
    {
        return stmtValidator.getSession();
    }

    // implement FarragoSessionPreparingStmt
    public String getSql()
    {
        return sql;
    }

    // implement RelOptConnection
    public RelOptSchema getRelOptSchema()
    {
        return this;
    }

    // implement RelOptConnection
    public Object contentsAsArray(
        String qualifier,
        String tableName)
    {
        throw new UnsupportedOperationException(
            "FarragoPreparingStmt.contentsAsArray() should have been replaced");
    }

    // implement FarragoSessionPreparingStmt
    public RelOptTable loadColumnSet(SqlIdentifier name)
    {
        return getTableForMember(name.names);
    }

    // implement RelOptSchema
    public RelOptTable getTableForMember(String [] names)
    {
        return getTableForMember(names, null, null);
    }

    // implement RelOptSchemaWithSampling
    public RelOptTable getTableForMember(
        String [] names,
        String datasetName,
        boolean [] usedDataset)
    {
        FarragoSessionResolvedObject<CwmNamedColumnSet> resolved =
            stmtValidator.resolveSchemaObjectName(
                names,
                CwmNamedColumnSet.class);

        if (resolved == null) {
            return null;
        }

        if (resolved.object == null) {
            return getForeignTableFromNamespace(resolved);
        }

        assert (resolved.object instanceof CwmNamedColumnSet);

        CwmNamedColumnSet columnSet = resolved.object;

        // If they requested a sample, see if there's a sample with that name.
        // Set oldColumnSet to remind us to cast & reorder columns.
        CwmNamedColumnSet oldColumnSet = null;
        if (datasetName != null) {
            CwmNamedColumnSet sampleColumnSet =
                stmtValidator.getSampleDataset(columnSet, datasetName);
            if (sampleColumnSet != null) {
                oldColumnSet = columnSet;
                columnSet = sampleColumnSet;
                if (usedDataset != null) {
                    assert usedDataset.length == 1;
                    usedDataset[0] = true;
                }
            }
        }

        if (columnSet instanceof FemLocalTable) {
            FemLocalTable table = (FemLocalTable) columnSet;

            // REVIEW:  maybe defer this until physical implementation?
            if (table.isTemporary()) {
                getIndexMap().instantiateTemporaryTable(
                    stmtValidator.getDataWrapperCache(),
                    table);
            }
        }

        RelOptTable relOptTable;
        if (columnSet instanceof FemBaseColumnSet) {
            FemBaseColumnSet table = (FemBaseColumnSet) columnSet;
            FemDataServer femServer = table.getServer();
            loadDataServerFromCache(femServer);
            relOptTable =
                stmtValidator.getDataWrapperCache().loadColumnSetFromCatalog(
                    table,
                    getFarragoTypeFactory());
        } else if (columnSet instanceof FemLocalView) {
            Pair<RelDataType, List<RelDataTypeField>> pair =
                createTableRowType(columnSet);
            FemLocalView view = (FemLocalView) columnSet;
            relOptTable =
                new FarragoView(
                    view,
                    pair.left,
                    pair.right,
                    datasetName,
                    view.getModality());
        } else {
            throw Util.needToImplement(columnSet);
        }
        initializeQueryColumnSet(relOptTable, columnSet);

        if (oldColumnSet != null) {
            Pair<RelDataType, List<RelDataTypeField>> pair =
                createTableRowType(oldColumnSet);
            relOptTable =
                new PermutingRelOptTable(
                    this,
                    oldColumnSet.getName(),
                    pair.left,
                    relOptTable);
        }

        return relOptTable;
    }

    private void initializeQueryColumnSet(
        RelOptTable relOptTable,
        CwmNamedColumnSet cwmColumnSet)
    {
        if (relOptTable == null) {
            return;
        }
        if (!(relOptTable instanceof FarragoQueryColumnSet)) {
            return;
        }
        FarragoQueryColumnSet queryColumnSet =
            (FarragoQueryColumnSet) relOptTable;
        queryColumnSet.setPreparingStmt(this);
        queryColumnSet.setCwmColumnSet(cwmColumnSet);
    }

    private FarragoMedColumnSet getForeignTableFromNamespace(
        FarragoSessionResolvedObject<CwmNamedColumnSet> resolved)
    {
        FemDataServer femServer =
            FarragoCatalogUtil.getModelElementByName(
                getRepos().allOfType(FemDataServer.class),
                resolved.catalogName);
        if (femServer == null) {
            return null;
        }

        // TODO jvs 27-Aug-2005:  decide on required privileges for direct
        // access to foreign tables

        // When a foreign table is referenced directly via a namespace, we have
        // nothing to hang a direct dependency on.  Instead, we remember the
        // dependency on the server, so that if the server gets dropped,
        // dependent views will cascade.
        addDependency(femServer, null);

        FarragoMedDataServer server = loadDataServerFromCache(femServer);

        try {
            FarragoMedNameDirectory directory = server.getNameDirectory();
            if (directory == null) {
                return null;
            }
            directory = directory.lookupSubdirectory(resolved.schemaName);
            if (directory == null) {
                return null;
            }
            FarragoMedColumnSet medColumnSet =
                directory.lookupColumnSet(
                    getFarragoTypeFactory(),
                    resolved.objectName,
                    resolved.getQualifiedName());
            initializeQueryColumnSet(medColumnSet, null);
            return medColumnSet;
        } catch (Throwable ex) {
            // TODO:  better name formatting
            throw FarragoResource.instance().ValidatorForeignTableLookupFailed
            .ex(
                Arrays.asList(resolved.getQualifiedName()).toString(),
                ex);
        }
    }

    private FarragoMedDataServer loadDataServerFromCache(
        FemDataServer femServer)
    {
        FarragoMedDataServer server =
            stmtValidator.getDataWrapperCache().loadServerFromCatalog(
                femServer);
        if (loadedServerClassNameSet.add(server.getClass().getName())) {
            // This is the first time we've seen this server class, so give it a
            // chance to register any planner info such as calling conventions
            // and rules.  REVIEW: the discrimination is based on class name, on
            // the assumption that it should be unique regardless of
            // classloader, JAR, etc.  Is that correct?
            planner.beginMedPluginRegistration(server.getClass().getName());
            server.registerRules(planner);
            assert (relMetadataProvider != null);
            server.registerRelMetadataProviders(relMetadataProvider);
            planner.endMedPluginRegistration();
        }
        return server;
    }

    // implement RelOptSchema
    public RelDataTypeFactory getTypeFactory()
    {
        return getFarragoTypeFactory();
    }

    // implement RelOptSchema
    public void registerRules(RelOptPlanner planner)
    {
        // nothing to do
    }

    // implement SqlValidator.CatalogReader
    public SqlValidatorTable getTable(String [] names)
    {
        FarragoSessionResolvedObject<CwmNamedColumnSet> resolved =
            stmtValidator.resolveSchemaObjectName(
                names,
                CwmNamedColumnSet.class);

        if (resolved == null) {
            return null;
        }

        if (resolved.object == null) {
            return getForeignTableFromNamespace(resolved);
        }

        if (!(resolved.object instanceof CwmNamedColumnSet)) {
            // TODO:  give a more helpful error
            // in case a non-relational object is referenced
            return null;
        }

        CwmNamedColumnSet table = resolved.object;
        ModalityType modality = ModalityTypeEnum.MODALITYTYPE_RELATIONAL;
        if (table instanceof FemAbstractColumnSet) {
            modality = ((FemAbstractColumnSet) table).getModality();
        }

        PrivilegedAction action = PrivilegedActionEnum.SELECT;
        if (dmlTarget != null) {
            if (Arrays.equals(names, dmlTarget.names)) {
                assert (dmlAction != null);
                action = dmlAction;

                // REVIEW jvs 27-Aug-2005:  This is a hack to handle the case
                // of self-insert, where the same table is both the source and
                // target.  We need to require SELECT for the source role
                // and INSERT for the target role.  It only works because
                // SqlValidatorImpl happens to validate the target first, so
                // this is very brittle.
                clearDmlValidation();
            }
        }

        // If the session has a label setting, only allow access to local
        // tables that were created prior to when the label was created.
        Timestamp labelTimestamp =
            getSession().getSessionLabelCreationTimestamp();
        if ((labelTimestamp != null) && (table instanceof FemLocalTable)) {
            FemAnnotatedElement annotated = (FemAnnotatedElement) table;
            Timestamp objectCreateTimestamp =
                Timestamp.valueOf(annotated.getCreationTimestamp());
            if (objectCreateTimestamp.compareTo(labelTimestamp) > 0) {
                throw FarragoResource.instance()
                .ValidatorAccessObjectNonVisibleToLabel.ex(
                    getRepos().getLocalizedObjectName(table));
            }
        }

        addDependency(table, action);

        if (table.getVisibility() == null) {
            throw new FarragoUnvalidatedDependencyException();
        }

        Pair<RelDataType, List<RelDataTypeField>> pair =
            createTableRowType(table);

        SqlAccessType allowedAccess =
            FarragoCatalogUtil.getTableAllowedAccess(table);
        return newValidatorTable(
            resolved.getQualifiedName(),
            pair.left,
            allowedAccess,
            modality);
    }

    /**
     * Creates a row-type for a given table. This row-type includes any system
     * columns which are implicit for this type of table.
     *
     * @param table Repository table
     *
     * @return Pair consisting of row type (not including system columns)
     *    and list of system columns
     */
    protected Pair<RelDataType, List<RelDataTypeField>> createTableRowType(
        CwmNamedColumnSet table)
    {
        RelDataType rowType =
            getFarragoTypeFactory().createStructTypeFromClassifier(table);

        if (table instanceof FemLocalTable) {
            int nColumnsTotal = rowType.getFieldCount();
            int nColumnsActual = nColumnsTotal;

            // If table is undergoing ALTER TABLE ADD COLUMN,
            // hide the new (last) column.
            DependencySupplier supplier =
                getRepos().getCorePackage().getDependencySupplier();
            for (CwmDependency dep : supplier.getSupplierDependency(table)) {
                if (dep.getNamespace() instanceof FemRecoveryReference) {
                    FemRecoveryReference recoveryRef =
                        (FemRecoveryReference) dep.getNamespace();
                    if (recoveryRef.getRecoveryType()
                        == RecoveryTypeEnum.ALTER_TABLE_ADD_COLUMN)
                    {
                        --nColumnsActual;
                        break;
                    }
                }
            }

            // If a label is set, hide any columns which were created
            // after the label
            Timestamp labelTimestamp =
                getSession().getSessionLabelCreationTimestamp();
            if (labelTimestamp != null) {
                while (nColumnsActual > 1) {
                    FemStoredColumn column =
                        (FemStoredColumn) table.getFeature().get(
                            nColumnsActual - 1);
                    Timestamp columnCreateTimestamp =
                        Timestamp.valueOf(column.getCreationTimestamp());
                    if (columnCreateTimestamp.compareTo(labelTimestamp) > 0) {
                        --nColumnsActual;
                    } else {
                        // Once we see the first visible column, everything
                        // before it is guaranteed to be visible since
                        // we only allow creation of columns at the end.
                        break;
                    }
                }
            }

            // Now truncate the row if necessary
            if (nColumnsActual < nColumnsTotal) {
                rowType =
                    getFarragoTypeFactory().createStructType(
                        rowType.getFieldList().subList(
                            0,
                            nColumnsActual));
            }
        }

        return Pair.of(rowType, Collections.<RelDataTypeField>emptyList());
    }

    /**
     * Factory method, creates a table.
     */
    protected SqlValidatorTable newValidatorTable(
        String [] qualifiedName,
        RelDataType rowType,
        SqlAccessType allowedAccess,
        ModalityType modality)
    {
        return new ValidatorTable(
            qualifiedName,
            rowType,
            allowedAccess,
            modality);
    }

    // implement SqlValidator.CatalogReader
    public RelDataType getNamedType(SqlIdentifier typeName)
    {
        CwmSqldataType cwmType = stmtValidator.findSqldataType(typeName);
        if (!(cwmType instanceof FemSqlobjectType)) {
            // TODO jvs 12-Feb-2005:  throw an excn stating that only
            // user-defined structured type is allowed here
            return null;
        }

        // FIXME jvs 27-Aug-2005:  this should be USAGE, not REFERENCES;
        // need to add to FEM
        addDependency(cwmType, PrivilegedActionEnum.REFERENCES);
        return getFarragoTypeFactory().createCwmType(cwmType);
    }

    // implement SqlValidatorCatalogReader
    public List<SqlMoniker> getAllSchemaObjectNames(List<String> names)
    {
        return stmtValidator.getAllSchemaObjectNames(names);
    }

    // implement SqlValidatorCatalogReader
    public String getSchemaName()
    {
        return getSession().getSessionVariables().schemaName;
    }

    public void addDependency(
        CwmModelElement supplier,
        PrivilegedAction action)
    {
        if (!isExpandingDefinition()) {
            directDependencies.add(supplier);
            if (action != null) {
                stmtValidator.requestPrivilege(
                    supplier,
                    action.toString());
            }
        }
        allDependencies.add(supplier);

        // Add the dependency to all of the parent FarragoPreparingStmt's
        // as well.
        FarragoPreparingStmt parent = parentStmt;
        while (parent != null) {
            parent.addDependency(supplier, action);
            parent = parent.getParentStmt();
        }
    }

    public void mapResultSetType(
        String resultSetName,
        RelDataType rowType)
    {
        resultSetTypeMap.put(resultSetName, rowType);
    }

    public void mapIterCalcType(
        String iterCalcName,
        RelDataType rowType)
    {
        iterCalcTypeMap.put(iterCalcName, rowType);
    }

    public Variable getConnectionVariable()
    {
        return new Variable(connectionVariable);
    }

    public TypeName getResultSetTupleIterTypeName()
    {
        return TypeName.forOJClass(
            OJClass.forClass(
                org.eigenbase.runtime.ResultSetTupleIter.class));
    }

    // override OJPreparingStmt
    protected String getCompilerClassName()
    {
        return getRepos().getCurrentConfig().getJavaCompilerClassName();
    }

    // override OJPreparingStmt
    protected boolean shouldSetConnectionInfo()
    {
        return false;
    }

    // override OJPreparingStmt
    protected boolean shouldAlwaysWriteJavaFile()
    {
        Level dynamicLevel = dynamicTracer.getLevel();
        return (dynamicLevel != null && dynamicTracer.isLoggable(Level.FINE));
    }

    protected File getPackageDir()
    {
        return packageDir;
    }

    // override OJPreparingStmt
    protected String getClassRoot()
    {
        return classesRoot.getPath();
    }

    // override OJPreparingStmt
    protected String getJavaRoot()
    {
        return classesRoot.getPath();
    }

    // override OJPreparingStmt
    protected String getTempPackageName()
    {
        return packageName;
    }

    // override OJPreparingStmt
    protected String getTempClassName()
    {
        return "ExecutableStmt";
    }

    // override OJPreparingStmt
    protected String getTempMethodName()
    {
        return "execute";
    }

    //~ Inner Classes ----------------------------------------------------------

    protected static class ValidatorTable
        implements SqlValidatorTable
    {
        private final String [] qualifiedName;
        private final RelDataType rowType;
        private final SqlAccessType accessType;
        private final ModalityType modality;

        /**
         * Creates a new ValidatorTable object.
         */
        public ValidatorTable(
            String [] qualifiedName,
            RelDataType rowType,
            SqlAccessType accessType,
            ModalityType modality)
        {
            this.qualifiedName = qualifiedName;
            this.rowType = rowType;
            this.accessType = accessType;
            this.modality = modality;
        }

        // implement SqlValidatorTable
        public String [] getQualifiedName()
        {
            return qualifiedName;
        }

        // implement SqlValidatorTable
        public SqlMonotonicity getMonotonicity(String columnName)
        {
            return SqlMonotonicity.NotMonotonic;
        }

        // implement SqlValidatorTable
        public SqlAccessType getAllowedAccess()
        {
            return accessType;
        }

        // implement SqlValidatorTable
        public RelDataType getRowType()
        {
            return rowType;
        }

        public ModalityType getModality()
        {
            return modality;
        }
    }

    /**
     * Transform which permutes the columns of an input table, applying casts
     * amd renaming columns if necessary, to make the output rowtype match the
     * desired rowtype.
     *
     * <p>The input table must have a superset of the columns of the output
     * rowtype, and their types must be coercible into the output types.
     * Conversion is performed by the {@link SqlToRelConverter#convertField}
     * method.
     *
     * <p>The transform generally generates an extra {@link CalcRel} during the
     * conversion to a {@link RelNode}.
     */
    protected class PermutingRelOptTable
        extends RelOptAbstractTable
    {
        private final RelOptTable inputTable;

        /**
         * Creates a table which converts an input row type to a desired output
         * row type, shuffling and casting columns in order to do so.
         *
         * @param schema Schema the table belongs to
         * @param name Name of this table
         * @param rowType Output row type of this table
         * @param inputTable Input table
         */
        protected PermutingRelOptTable(
            RelOptSchema schema,
            String name,
            RelDataType rowType,
            RelOptTable inputTable)
        {
            super(
                schema, name, rowType,
                Collections.<RelDataTypeField>emptyList());
            assert inputTable != null : "inputTable";
            this.inputTable = inputTable;
        }

        public RelNode toRel(
            RelOptCluster cluster,
            RelOptConnection connection)
        {
            final RelNode inputRel = inputTable.toRel(cluster, connection);
            final RelDataType inputRowType = inputRel.getRowType();

            // Add projections to make the columns the desired types.
            List<String> fieldNames = new ArrayList<String>();
            List<RexNode> fieldExprs = new ArrayList<RexNode>();
            for (RelDataTypeField field : rowType.getFields()) {
                RexNode expr =
                    sqlToRelConverter.convertField(inputRowType, field);
                fieldExprs.add(expr);
                fieldNames.add(field.getName());
            }

            return CalcRel.createProject(
                inputRel,
                fieldExprs,
                fieldNames);
        }
    }

    /**
     * Exception describing why a plan is invalid.
     *
     * <p>Not localized.
     */
    protected static class InvalidPlanException
        extends Exception
    {
        private final RelNode rel;

        public InvalidPlanException(String message, RelNode rel)
        {
            super(message);
            this.rel = rel;
        }
    }
}

// End FarragoPreparingStmt.java
