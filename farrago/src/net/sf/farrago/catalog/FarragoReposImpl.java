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
package net.sf.farrago.catalog;

import java.util.*;
import java.util.concurrent.locks.*;
import java.util.logging.*;

import javax.jmi.model.*;
import javax.jmi.reflect.*;

import net.sf.farrago.*;
import net.sf.farrago.cwm.core.*;
import net.sf.farrago.cwm.relational.*;
import net.sf.farrago.fem.config.*;
import net.sf.farrago.fem.sql2003.*;
import net.sf.farrago.resource.*;
import net.sf.farrago.trace.*;
import net.sf.farrago.util.*;

import org.eigenbase.enki.mdr.*;
import org.eigenbase.jmi.*;
import org.eigenbase.util.*;


/**
 * Implementation of {@link FarragoRepos} using a MDR repository.
 *
 * @author John V. Sichi
 * @version $Id$
 */
public abstract class FarragoReposImpl
    extends FarragoMetadataFactoryImpl
    implements FarragoRepos
{
    //~ Static fields/initializers ---------------------------------------------

    private static final Logger tracer = FarragoTrace.getReposTracer();

    /**
     * TODO: look this up from repository
     */
    private static final int maxNameLength = 128;

    //~ Instance fields --------------------------------------------------------

    private boolean isFennelEnabled;

    protected final FarragoCompoundAllocation allocations =
        new FarragoCompoundAllocation();

    private final Map<String, String> localizedClassNames =
        new HashMap<String, String>();

    private final List<ResourceBundle> resourceBundles =
        new ArrayList<ResourceBundle>();

    private JmiModelGraph modelGraph;

    private JmiModelView modelView;

    private final Map<String, FarragoSequenceAccessor> sequenceMap;

    private final ReentrantReadWriteLock sxLock = new ReentrantReadWriteLock();

    private ThreadLocal<ReposCache> cache;

    private Boolean exclusiveAccess;

    //~ Constructors -----------------------------------------------------------

    /**
     * Opens a Farrago repository.
     *
     * @param owner Allocation owner
     */
    public FarragoReposImpl(
        FarragoAllocationOwner owner)
    {
        owner.addAllocation(this);
        sequenceMap = new HashMap<String, FarragoSequenceAccessor>();
        cache =
            new ThreadLocal<ReposCache>() {
                @Override protected ReposCache initialValue()
                {
                    return new ReposCache();
                }
            };
        exclusiveAccess = false;
    }

    //~ Methods ----------------------------------------------------------------

    // TODO jvs 30-Nov-2005:  rename these methods; initGraph initializes
    // other stuff besides the model graph

    /**
     * Initializes the model graph. The constructor of a concrete subclass must
     * call this after the repository has been initialized, and {@link
     * #getRootPackage()} is available.
     */
    protected void initGraph()
    {
        isFennelEnabled = !getDefaultConfig().isFennelDisabled();
        initGraphOnly();
    }

    protected void initGraphOnly()
    {
        // TODO: SWZ: 2008-03-27: Obtain the classloader from
        // EnkiMDRepository.getDefaultClassLoader().
        ClassLoader classLoader = MDRepositoryFactory.getDefaultClassLoader();

        modelGraph =
            new JmiModelGraph(
                getRootPackage(),
                classLoader,
                true);
        modelView = new JmiModelView(modelGraph);
    }

    protected FemFarragoConfig getDefaultConfig()
    {
        // TODO: multiple named configurations.  For now, build should have
        // imported exactly one configuration named Current.
        Collection<FemFarragoConfig> configs =
            (Collection<FemFarragoConfig>) getConfigPackage()
            .getFemFarragoConfig().refAllOfClass();

        assert (configs.size() == 1);
        FemFarragoConfig defaultConfig = configs.iterator().next();
        assert (defaultConfig.getName().equals("Current"));
        return defaultConfig;
    }

    protected static String getLocalizedClassKey(RefClass refClass)
    {
        String className =
            refClass.refMetaObject().refGetValue("name").toString();
        return "Uml" + className;
    }

    // implement FarragoRepos
    public JmiModelGraph getModelGraph()
    {
        return modelGraph;
    }

    // implement FarragoRepos
    public JmiModelView getModelView()
    {
        return modelView;
    }

    // implement FarragoRepos
    public CwmCatalog getSelfAsCatalog()
    {
        return getCatalog(FarragoCatalogInit.LOCALDB_CATALOG_NAME);
    }

    // implement FarragoRepos
    public int getIdentifierPrecision()
    {
        return maxNameLength;
    }

    // implement FarragoRepos
    public String getDefaultCharsetName()
    {
        // REVIEW jvs 21-Jan-2009:  Do we really want an implicit
        // session here?
        beginReposSession();
        try {
            CwmCatalog catalog = getSelfAsCatalog();
            if (catalog == null) {
                return SaffronProperties.instance().defaultCharset.get();
            } else {
                return catalog.getDefaultCharacterSetName();
            }
        } finally {
            endReposSession();
        }
    }

    // implement FarragoRepos
    public String getDefaultCollationName()
    {
        beginReposSession();
        try {
            CwmCatalog catalog = getSelfAsCatalog();
            if (catalog == null) {
                return SaffronProperties.instance().defaultCollation.get();
            } else {
                return catalog.getDefaultCollationName();
            }
        } finally {
            endReposSession();
        }
    }

    // implement FarragoRepos
    public boolean isFennelEnabled()
    {
        return isFennelEnabled;
    }

    // implement FarragoRepos
    public String getLocalizedObjectName(
        CwmModelElement modelElement)
    {
        return getLocalizedObjectName(
            modelElement,
            modelElement.refClass());
    }

    // implement FarragoRepos
    public String getLocalizedObjectName(
        String name)
    {
        return getLocalizedObjectName(null, name, null);
    }

    // implement FarragoRepos
    public String getLocalizedObjectName(
        CwmModelElement modelElement,
        RefClass refClass)
    {
        String qualifierName = null;
        CwmNamespace namespace = modelElement.getNamespace();
        if (namespace != null) {
            qualifierName = namespace.getName();
        }
        return getLocalizedObjectName(
            qualifierName,
            modelElement.getName(),
            refClass);
    }

    // implement FarragoRepos
    public String getLocalizedObjectName(
        String qualifierName,
        String objectName,
        RefClass refClass)
    {
        StringBuilder sb = new StringBuilder();

        // TODO:  escaping
        if (refClass != null) {
            sb.append(getLocalizedClassName(refClass));
            sb.append(" ");
        }
        if (qualifierName != null) {
            sb.append("\"");
            sb.append(qualifierName);
            sb.append("\".");
        }
        sb.append("\"");
        sb.append(objectName);
        sb.append("\"");
        return sb.toString();
    }

    // implement FarragoRepos
    public String getLocalizedClassName(RefClass refClass)
    {
        String umlKey = getLocalizedClassKey(refClass);
        String name = localizedClassNames.get(umlKey);
        if (name != null) {
            return name;
        } else {
            // NOTE jvs 12-Jan-2005:  we intentionally return something
            // nasty so that if it shows up in user-level error messages,
            // someone nice will maybe log a bug and get it fixed
            return "NOT_YET_LOCALIZED_" + umlKey;
        }
    }

    // implement FarragoRepos
    public CwmCatalog getCatalog(String catalogName)
    {
        Map<String, Pair<RefClass, String>> catalogCache =
            cache.get().catalogCache;
        Pair<RefClass, String> catalogDesc = catalogCache.get(catalogName);

        CwmCatalog catalog;
        if (catalogDesc != null) {
            catalog =
                (CwmCatalog) getEnkiMdrRepos().getByMofId(
                    catalogDesc.right,
                    catalogDesc.left);
        } else {
            catalog =
                FarragoCatalogUtil.getModelElementByName(
                    allOfType(CwmCatalog.class),
                    catalogName);

            if (catalog != null) {
                catalogDesc =
                    new Pair<RefClass, String>(
                        catalog.refClass(),
                        catalog.refMofId());
                catalogCache.put(catalogName, catalogDesc);
            }
        }

        return catalog;
    }

    // implement FarragoRepos
    public FemTagAnnotation getTagAnnotation(
        FemAnnotatedElement element,
        String tagName)
    {
        for (FemTagAnnotation tag : element.getTagAnnotation()) {
            if (tag.getName().equals(tagName)) {
                return tag;
            }
        }
        return null;
    }

    // implement FarragoRepos
    public void setTagAnnotationValue(
        FemAnnotatedElement element,
        String tagName,
        String tagValue)
    {
        FemTagAnnotation tag = getTagAnnotation(element, tagName);
        if (tag == null) {
            tag = newFemTagAnnotation();
            tag.setName(tagName);
            element.getTagAnnotation().add(tag);
        }
        tag.setValue(tagValue);
    }

    // implement FarragoRepos
    public String getTagAnnotationValue(
        FemAnnotatedElement element,
        String tagName)
    {
        FemTagAnnotation tag = getTagAnnotation(element, tagName);
        if (tag == null) {
            return null;
        } else {
            return tag.getValue();
        }
    }

    // implement FarragoRepos
    public CwmTaggedValue getTag(
        CwmModelElement element,
        String tagName)
    {
        Collection tags =
            getCorePackage().getTaggedElement().getTaggedValue(element);
        for (Object o : tags) {
            CwmTaggedValue tag = (CwmTaggedValue) o;
            if (tag.getTag().equals(tagName)) {
                return tag;
            }
        }
        return null;
    }

    // implement FarragoRepos
    public void setTagValue(
        CwmModelElement element,
        String tagName,
        String tagValue)
    {
        CwmTaggedValue tag = getTag(element, tagName);
        if (tag == null) {
            tag = newCwmTaggedValue();
            tag.setTag(tagName);
            getCorePackage().getTaggedElement().add(element, tag);
        }
        tag.setValue(tagValue);
    }

    // implement FarragoRepos
    public String getTagValue(
        CwmModelElement element,
        String tagName)
    {
        CwmTaggedValue tag = getTag(element, tagName);
        if (tag == null) {
            return null;
        } else {
            return tag.getValue();
        }
    }

    // implement FarragoRepos
    public List<FarragoReposIntegrityErr> verifyIntegrity(
        RefObject refObj)
    {
        Collection exceptions;
        if (refObj == null) {
            return verifyIntegrityAll();
        } else if (refObj instanceof CwmDependency) {
            // REVIEW jvs 3-Sept-2006:  CwmDependency does not allow
            // for dangling dependencies, but we rely on those, so
            // skip them for now.
            exceptions = null;
        } else {
            exceptions = refObj.refVerifyConstraints(false);
        }

        if (exceptions == null) {
            return Collections.emptyList();
        }

        List<FarragoReposIntegrityErr> errs =
            new ArrayList<FarragoReposIntegrityErr>();

        for (Object obj : exceptions) {
            JmiException ex = (JmiException) obj;
            String description = ex.getClass().getName();
            if (ex.getMessage() != null) {
                description += ":  " + ex.getMessage();
            }
            RefObject metaObj = ex.getElementInError();
            if (metaObj != null) {
                description += ", ";
                description +=
                    ReflectUtil.getUnqualifiedClassName(metaObj.getClass());
                description += " = ";
                if (metaObj instanceof ModelElement) {
                    description += ((ModelElement) metaObj).getName();
                } else if (metaObj instanceof CwmModelElement) {
                    description += ((CwmModelElement) metaObj).getName();
                } else {
                    description += metaObj;
                }
            }
            if (ex.getObjectInError() != null) {
                description += ", extra = " + ex.getObjectInError();
            }
            if (refObj != null) {
                RefClass refClass = refObj.refClass();
                String className = JmiObjUtil.getMetaObjectName(refClass);
                String objectName = null;
                try {
                    // If it has a name attribute, use that
                    objectName = (String) refObj.refGetValue("name");
                } catch (Throwable t) {
                    // Otherwise, fall through to dump below.
                }
                if (objectName == null) {
                    objectName = refObj.toString();
                }
                description += ", " + className;
                description += " = " + objectName;
            }
            FarragoReposIntegrityErr err =
                new FarragoReposIntegrityErr(description, ex, refObj);
            errs.add(err);
        }
        return errs;
    }

    private List<FarragoReposIntegrityErr> verifyIntegrityAll()
    {
        List<FarragoReposIntegrityErr> errs =
            new ArrayList<FarragoReposIntegrityErr>();
        for (JmiClassVertex classVertex : modelGraph.vertexSet()) {
            RefClass refClass = classVertex.getRefClass();
            for (Object obj : refClass.refAllOfClass()) {
                RefObject refObj = (RefObject) obj;
                errs.addAll(verifyIntegrity(refObj));
            }
        }
        return errs;
    }

    // implement FarragoRepos
    public void addResourceBundles(List<ResourceBundle> bundles)
    {
        resourceBundles.addAll(bundles);
        Iterator<ResourceBundle> iter = bundles.iterator();
        for (ResourceBundle resourceBundle : bundles) {
            Enumeration<String> e = resourceBundle.getKeys();
            while (e.hasMoreElements()) {
                // NOTE jvs 12-Apr-2005:  This early binding won't
                // work once we have sessions with different locales, but
                // I'll leave that for someone wiser in the ways of i18n.
                String key = e.nextElement();
                if (key.startsWith("Uml")) {
                    localizedClassNames.put(
                        key,
                        resourceBundle.getString(key));
                }
            }
        }
    }

    public Object getMetadataFactory(String prefix)
    {
        if (prefix.equals("Fem")) {
            return (FarragoMetadataFactory) this;
        }
        throw Util.newInternal("Unknown metadata factory '" + prefix + "'");
    }

    public FarragoSequenceAccessor getSequenceAccessor(
        String mofId)
    {
        synchronized (sequenceMap) {
            FarragoSequenceAccessor sequence = sequenceMap.get(mofId);
            if (sequence != null) {
                return sequence;
            }
            sequence = new FarragoSequenceAccessor(this, mofId);
            allocations.addAllocation(sequence);
            sequenceMap.put(mofId, sequence);
            return sequence;
        }
    }

    public String expandProperties(String value)
    {
        return FarragoProperties.instance().expandProperties(value);
    }

    private RefClass findRefClass(Class<? extends RefObject> clazz)
    {
        JmiClassVertex vertex = modelGraph.getVertexForJavaInterface(clazz);
        if (vertex == null) {
            // May happen if classpath is inconsistent with model.
            throw new RuntimeException("no vertex found for " + clazz);
        }
        return vertex.getRefClass();
    }

    public <T extends RefObject> Collection<T> allOfClass(Class<T> clazz)
    {
        RefClass refClass = findRefClass(clazz);
        return (Collection<T>) refClass.refAllOfClass();
    }

    public <T extends RefObject> Collection<T> allOfType(Class<T> clazz)
    {
        RefClass refClass = findRefClass(clazz);
        return (Collection<T>) refClass.refAllOfType();
    }

    // implement FarragoRepos
    public FarragoModelLoader getModelLoader()
    {
        return null;
    }

    // implement FarragoRepos
    public FarragoReposTxnContext newTxnContext()
    {
        return newTxnContext(false);
    }

    // implement FarragoRepos
    public FarragoReposTxnContext newTxnContext(boolean manageReposSession)
    {
        return new FarragoReposTxnContext(this, manageReposSession);
    }

    /**
     * Places either a shared or exclusive lock on the repository. Multiple
     * shared locks are allowed from different threads when no thread holds an
     * exclusive lock, but only one thread can hold an exclusive lock at a time,
     * preventing shared locks from other threads. If a conflicting lock is
     * requested, that requester will wait until the requested lock is
     * available. Locks are reentrant: a thread can take the same lock more than
     * once, but must make a matching number of calls to {@link #unlockRepos} in
     * order to release the lock. Upgrade and downgrade are not supported.
     *
     * <p>This lock is independent of MDR transaction state (i.e. it can be held
     * even when no MDR transaction is in progress; an MDR transaction can be
     * started without taking this lock; and an exclusive lock can be taken even
     * for a read-only MDR transaction). Currently, its only public exposure is
     * via {@link FarragoReposTxnContext}, which matches shared with read and
     * exclusive with write.
     *
     * @param lockLevel 1 for a shared lock, 2 for an exclusive lock
     */
    public void lockRepos(int lockLevel)
    {
        synchronized (exclusiveAccess) {
            if (exclusiveAccess.booleanValue()) {
                throw FarragoResource.instance().NeedExclusiveAccess.ex();
            }
        }
        if (lockLevel == 1) {
            sxLock.readLock().lock();
        } else if (lockLevel == 2) {
            sxLock.writeLock().lock();
        } else {
            assert (false);
        }
    }

    /**
     * Releases either a shared or exclusive lock on the repository that was
     * previously acquired (caller must ensure consistency).
     *
     * @param lockLevel 1 for a shared lock, 2 for an exclusive lock
     */
    public void unlockRepos(int lockLevel)
    {
        if (lockLevel == 1) {
            sxLock.readLock().unlock();
        } else if (lockLevel == 2) {
            sxLock.writeLock().unlock();
        } else {
            assert (false);
        }
    }

    // TODO: SWZ: 2008-03-27: implement on platform side and remove
    // implement FarragoRepos (for red-zone components ignorant of Enki)
    public EnkiMDRepository getEnkiMdrRepos()
    {
        return (EnkiMDRepository) getMdrRepos();
    }

    // TODO: SWZ: 2008-03-27: implement on platform side and call this
    public void beginReposSession()
    {
        cache.get().beginSession();
    }

    // TODO: SWZ: 2008-03-27: implement on platform side and call this
    public void endReposSession()
    {
        cache.get().endSession();
    }

    /**
     * Puts the repository in exclusive access mode. When in this mode,
     * subsequent attempts to lock the repository will return an exception
     * immediately rather than wait for a required repository lock to become
     * available.
     */
    public void beginExclusiveAccess()
    {
        synchronized (exclusiveAccess) {
            if (exclusiveAccess.booleanValue()) {
                throw FarragoResource.instance().NeedExclusiveAccess.ex();
            }
            exclusiveAccess = true;
        }
    }

    /**
     * Ends exclusive access mode for the repository.
     */
    public void endExclusiveAccess()
    {
        synchronized (exclusiveAccess) {
            exclusiveAccess = false;
        }
    }

    //~ Inner Classes ----------------------------------------------------------

    private static class ReposCache
    {
        protected int sessionDepth;
        protected Map<String, Pair<RefClass, String>> catalogCache;

        private ReposCache()
        {
            this.sessionDepth = 0;
        }

        private void beginSession()
        {
            if (sessionDepth++ == 0) {
                catalogCache = new HashMap<String, Pair<RefClass, String>>();
            }
        }

        protected void endSession()
        {
            if (--sessionDepth == 0) {
                catalogCache.clear();
            }
        }
    }
}

// End FarragoReposImpl.java
