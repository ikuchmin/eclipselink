/*******************************************************************************
 * Copyright (c) 1998, 2015 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0 
 * which accompanies this distribution. 
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at 
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *     Oracle - initial API and implementation from Oracle TopLink
 ******************************************************************************/  
package org.eclipse.persistence.sessions.factories;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.persistence.exceptions.ValidationException;
import org.eclipse.persistence.internal.security.PrivilegedAccessHelper;
import org.eclipse.persistence.internal.security.PrivilegedGetClassLoaderForClass;
import org.eclipse.persistence.internal.sessions.AbstractSession;
import org.eclipse.persistence.internal.sessions.factories.model.SessionConfigs;
import org.eclipse.persistence.logging.AbstractSessionLog;
import org.eclipse.persistence.logging.SessionLog;
import org.eclipse.persistence.sessions.DatabaseSession;
import org.eclipse.persistence.sessions.Session;

/**
 * <p>
 * <b>Purpose</b>: Global session location.</p>
 *
 * <p><b>Description</b>: This allows for a global session local which can
 * be accessed globally from other classes.  This is needed for EJB data stores
 * as they must have a globally accessible place to access the session.
 * This can be by EJB session beans, BMP beans and CMP beans as well as Servlets and
 * other three-tier services.</p>
 *
 * <p><b>Responsibilities</b>:</p>
 * <ul>
 * <li> Store a global session.
 * <li> Allow the storage of alternative sessions as well.
 * </ul>
 *
 * @author James Sutherland
 * @since TOPLink/Java 3.0
 */
public class SessionManager {
    /** Allow for usage of schema validation to be configurable. */
    protected static boolean shouldUseSchemaValidation = true;
    
    protected static SessionManager manager;
    protected AbstractSession defaultSession;
    protected ConcurrentMap<String, Session> sessions = null;
    protected static boolean shouldPerformDTDValidation;
    private static final ConcurrentMap<String, SessionManager> managers = new ConcurrentHashMap<>(4, 0.9f, 1);
    private String context;
    private static final Object[] lock = new Object[0];
    private static final ContextHelper ctxHelper = ContextHelper.getContextHelper();

    /**
     * PUBLIC:
     * Return if schema validation will be used when parsing the 10g (10.1.3) sessions XML.
     *
     * @return {@code true} if schema validation will be used when parsing
     * the 10g (10.1.3) sessions XML, {@code false} otherwise
     */
    public static boolean shouldUseSchemaValidation() {
        return shouldUseSchemaValidation;
    }

    /**
     * PUBLIC:
     * Set if schema validation will be used when parsing the 10g (10.1.3) sessions XML.
     * By default schema validation is on, but can be turned off if validation problems occur,
     * or to improve parsing performance.
     *
     * @param useSchemaValidation {@code true} if schema validation should be used when parsing
     * the 10g (10.1.3) sessions XML, {@code false} otherwise
     */
    public static void setShouldUseSchemaValidation(boolean useSchemaValidation) {
        shouldUseSchemaValidation = useSchemaValidation;
    }
    
    /**
     * PUBLIC:
     * The default constructor to create a new session manager.
     */
    public SessionManager() {
        sessions = new ConcurrentHashMap<>(5);
    }

    /**
     * INTERNAL:
     * Add an named session to the hashtable.
     * Session must have a name prior to setting into session manager.
     *
     * @param session session to be added to the session manager
     */
    public void addSession(Session session) {
        getSessions().put(session.getName(), session);
    }

    /**
     * ADVANCED:
     * add an named session to the hashtable.
     *
     * @param sessionName session name
     * @param session session to be added to the session manager
     */
    public void addSession(String sessionName, Session session) {
        session.setName(sessionName);
        getSessions().put(sessionName, session);
    }

    /**
     * PUBLIC:
     * Return the default session.
     * The session configuration is stored in a sessions.xml file in a
     * directory on your classpath. Other sessions are supported through the
     * getSession by name API.
     *
     * @return default session
     */
    public Session getDefaultSession() {
        if (defaultSession == null) {
            defaultSession = getSession("default");
        }
        return defaultSession;
    }

    /**
     * PUBLIC:
     * Destroy current session manager instance.
     */
    public void destroy() {
        if (context != null) {
            managers.remove(context);
        } else {
            //should not happen
            AbstractSessionLog.getLog().log(SessionLog.WARNING, "session_manager_no_partition");
        }
        context = null;
        manager = null;
    }

    /**
     * INTERNAL:
     * Destroy the session defined by sessionName on this manager.
     *
     * @param sessionName name of the session to be destroyed
     */
    public void destroySession(String sessionName) {
        Session session = getSessions().get(sessionName);

        if (session != null) {
            destroy(session);
        } else {
            logAndThrowException(SessionLog.WARNING, ValidationException.noSessionRegisteredForName(sessionName));
        }
    }

    private void destroy(Session session) {
        try {
            if (session.isConnected()) {
                ((DatabaseSession) session).logout();
            }
        } catch (Throwable ignore) {
            // EL Bug 321843 - Must handle errors from logout.
            AbstractSessionLog.getLog().logThrowable(SessionLog.WARNING, AbstractSessionLog.CONNECTION, ignore);
        }

        sessions.remove(session.getName());
        session = null;
    }

    /**
     * INTERNAL:
     * Destroy all sessions held onto by this manager.
     */
    public void destroyAllSessions() {
        Iterator<Session> toRemoveSessions = new ArrayList<>(getSessions().values()).iterator();

        while (toRemoveSessions.hasNext()) {
            destroy(toRemoveSessions.next());
        }
    }

    /**
     * INTERNAL:
     * This method is to be used to load config objects for the Mapping Workbench
     * only. Do not call this method.
     *
     * @param resourceName resource to load
     * @param objectClassLoader ClassLoader used to load the resource
     * @return parsed session configuration
     */
    public synchronized SessionConfigs getInternalMWConfigObjects(String resourceName, ClassLoader objectClassLoader) {
        return getInternalMWConfigObjects(resourceName, objectClassLoader, true);
    }

    /**
     * INTERNAL:
     * This method is to be used to load config objects for the Mapping Workbench
     * only. Do not call this method.
     *
     * @param resourceName resource to load
     * @param objectClassLoader ClassLoader used to load the resource
     * @param validate whether to validate the resource passed in
     * @return parsed session configuration
     */
    public synchronized SessionConfigs getInternalMWConfigObjects(String resourceName, ClassLoader objectClassLoader, boolean validate) {
        return new XMLSessionConfigLoader(resourceName).loadConfigsForMappingWorkbench(objectClassLoader, validate);
    }

    /**
     * PUBLIC:
     * Return the session manager for current context.
     * This allow global access to a set of named sessions.
     *
     * @return the session manager for current context
     */
    public static SessionManager getManager() {
        if (ctxHelper == null) {
            if (manager == null) {
                synchronized (lock) {
                    if (manager == null) {
                        manager = initializeManager();
                    }
                }
            }
            return manager;
        }
        String currentCtx = ctxHelper.getPartitionID();
        if (manager != null && currentCtx.equals(manager.context)) {
            return manager;
        }
        manager = managers.get(currentCtx);
        if (manager == null) {
            synchronized (lock) {
                if (manager == null) {
                    manager = initializeManager();
                    manager.context = currentCtx;
                    managers.put(manager.context, manager);
                }
            }
        }
        return manager;
    }

    /**
     * ADVANCED:
     * Return all session managers.
     * This allows global access to all instances of SessionManager.
     *
     * @return all SessionManager instances
     */
    public static Collection<SessionManager> getAllManagers() {
        return managers.values();
    }

    /**
     * INTERNAL:
     * Initialize the singleton session manager.
     *
     * @return initialized session manager
     */
    protected static SessionManager initializeManager() {
        return new SessionManager();
    }

    /**
     * PUBLIC:
     * Return the session by name.
     *
     * @param sessionName session name
     * @return session with given session name
     */
    public org.eclipse.persistence.internal.sessions.AbstractSession getSession(String sessionName) {
        XMLSessionConfigLoader loader = new XMLSessionConfigLoader();
        loader.setSessionName(sessionName);
        return getSession(loader);
    }

    /**
     * PUBLIC:
     * Return the session by name.
     * Log the session in only if specified.
     *
     * @param sessionName session name
     * @param shouldLoginSession whether the session should be logged in
     * @return session with given session name
     */
    public org.eclipse.persistence.internal.sessions.AbstractSession getSession(String sessionName, boolean shouldLoginSession) {
        XMLSessionConfigLoader loader = new XMLSessionConfigLoader();
        loader.setSessionName(sessionName);
        loader.setShouldLogin(shouldLoginSession);
        return getSession(loader);
    }

    /**
     * PUBLIC:
     * Return the session by name.
     * Log the session in only if specified.
     * Refresh the session only if specified.
     *
     * @param sessionName session name
     * @param shouldLoginSession whether the session should be logged in
     * @param shouldRefreshSession whether the session should be refreshed
     * @return session with given session name
     */
    public org.eclipse.persistence.internal.sessions.AbstractSession getSession(String sessionName, boolean shouldLoginSession, boolean shouldRefreshSession) {
        XMLSessionConfigLoader loader = new XMLSessionConfigLoader();
        loader.setSessionName(sessionName);
        loader.setShouldLogin(shouldLoginSession);
        loader.setShouldRefresh(shouldRefreshSession);
        return getSession(loader);
    }

    /**
     * PUBLIC:
     * Return the session by name, using the classloader of the Object specified.
     * This method is used in older versions of BMP support
     * This method will cause the class loader of the provided object to be
     * compared with the classloader used to load the original session of this
     * name, with this classloader.  If they are not the same then the session
     * will be refreshed.
     *
     * @param sessionName session name
     * @param objectBean object to get the ClassLoader from
     * @return session with given session name
     */
    public org.eclipse.persistence.internal.sessions.AbstractSession getSession(String sessionName, Object objectBean) {
        XMLSessionConfigLoader loader = new XMLSessionConfigLoader();
        loader.setSessionName(sessionName);
        ClassLoader classLoader = null;
        if (PrivilegedAccessHelper.shouldUsePrivilegedAccess()){
            try{
                classLoader = (ClassLoader) AccessController.doPrivileged(new PrivilegedGetClassLoaderForClass(objectBean.getClass()));
            }catch (PrivilegedActionException ex){
                throw (RuntimeException) ex.getCause();
            }
        }else{
            classLoader = PrivilegedAccessHelper.getClassLoaderForClass(objectBean.getClass());
        }
        loader.setClassLoader(classLoader);
        return getSession(loader);
    }

    /**
     * PUBLIC:
     * Return the session by name, in the file specified.
     * Login the session.
     *
     * @param sessionName session name
     * @param filename file name containing session definition
     * @return session with given session name
     */
    public org.eclipse.persistence.internal.sessions.AbstractSession getSession(String sessionName, String filename) {
        XMLSessionConfigLoader loader = new XMLSessionConfigLoader();
        loader.setSessionName(sessionName);
        loader.setResourceName(filename);
        return getSession(loader);
    }
    
    /**
     * PUBLIC:
     * Return the session by name, in the file specified, using the class loader to find the resource.
     * This method will cause the class loader to be compared with the classloader
     * used to load the original session of this name.
     * If they are not the same then the session will be refreshed.
     *
     * @param sessionName session name
     * @param filename file name containing session definition
     * @param classLoader ClassLoader used to load the original session
     * @return session with given session name
     */
    public AbstractSession getSession(String sessionName, String filename, ClassLoader classLoader) {
        XMLSessionConfigLoader loader = new XMLSessionConfigLoader();
        loader.setSessionName(sessionName);
        loader.setResourceName(filename);
        loader.setClassLoader(classLoader);
        loader.setShouldCheckClassLoader(true);
        return getSession(loader);
    }

    /**
     * PUBLIC:
     * Return the session by name.
     * Provide the class loader for loading the project, the configuration file
     * and the deployed classes.
     * E.g. SessionManager.getManager().getSession("mySession", MySessionBean.getClassLoader());
     * This method will cause the class loader to be compared with the classloader
     * used to load the original session of this name.
     * If they are not the same then the session will be refreshed.
     *
     * @param sessionName session name
     * @param objectClassLoader ClassLoader used to load the original session
     * @return session with given session name
     */
    public AbstractSession getSession(String sessionName, ClassLoader objectClassLoader) {
        XMLSessionConfigLoader loader = new XMLSessionConfigLoader();
        loader.setSessionName(sessionName);
        loader.setClassLoader(objectClassLoader);
        loader.setShouldCheckClassLoader(true);
        return getSession(loader);
    }

    /**
     * PUBLIC:
     * Return the session by name, loading the configuration from the file
     * specified in the XMLSessionConfigLoader. Provide the class loader for
     * loading the project, the configuration file and the deployed classes.
     * This method will cause the class loader to be compared with the classloader
     * used to load the original session of this name.
     * If they are not the same then the session will be refreshed.
     *
     * @param loader {@link XMLSessionConfigLoader} containing session configuration
     * @param sessionName session name
     * @param objectClassLoader ClassLoader used to load the original session
     * @return session with given session name
     */
    public AbstractSession getSession(XMLSessionConfigLoader loader, String sessionName, ClassLoader objectClassLoader) {
        if (loader == null){
            loader = new XMLSessionConfigLoader();
        }
        loader.setSessionName(sessionName);
        loader.setClassLoader(objectClassLoader);
        loader.setShouldCheckClassLoader(true);
        return getSession(loader);
    }


    /**
     * PUBLIC:
     * Return the session by name, loading the configuration from the file
     * specified in the loader. Provide the class loader for loading the
     * project, the configuration file and the deployed classes. Pass in true for
     * shouldLoginSession if the session returned should be logged in before
     * returned otherwise false. Pass in true for shouldRefreshSession if the
     * XMLSessionConfigLoader should reparse the configuration file for new
     * sessions. False, will cause the XMLSessionConfigLoader not to parse the
     * file again.
     *
     * @param loader {@link XMLSessionConfigLoader} containing session configuration
     * @param sessionName session name
     * @param objectClassLoader ClassLoader used to load the original session
     * @param shouldLoginSession whether the session should be logged in
     * @param shouldRefreshSession whether the session should be refreshed
     * @return session with given session name
     */
    public AbstractSession getSession(XMLSessionConfigLoader loader, String sessionName, ClassLoader objectClassLoader, boolean shouldLoginSession, boolean shouldRefreshSession) {
        if (loader == null){
            loader = new XMLSessionConfigLoader();
        }
        loader.setSessionName(sessionName);
        loader.setClassLoader(objectClassLoader);
        loader.setShouldLogin(shouldLoginSession);
        loader.setShouldRefresh(shouldRefreshSession);
        return getSession(loader);
    }

    /**
     * PUBLIC:
     * Return the session by name, loading the configuration from the file
     * specified in the loader. Provide the class loader for loading the
     * project, the configuration file and the deployed classes. Pass in true for
     * shouldLoginSession if the session returned should be logged in before
     * returned otherwise false. Pass in true for shouldRefreshSession if the
     * XMLSessionConfigLoader should reparse the configuration file for new
     * sessions. False, will cause the XMLSessionConfigLoader not to parse the
     * file again.
     * Pass true for shouldCheckClassLoader will cause the class loader to be compared with the classloader
     * used to load the original session of this name.
     * If they are not the same then the session will be refreshed, this can be used for re-deployment.
     *
     * @param loader {@link XMLSessionConfigLoader} containing session configuration
     * @param sessionName session name
     * @param objectClassLoader ClassLoader used to load the original session
     * @param shouldLoginSession whether the session should be logged in
     * @param shouldRefreshSession whether the session should be refreshed
     * @param shouldCheckClassLoader whether to compare class loaders used to load given session
     * @return session with given session name
     */
    public AbstractSession getSession(XMLSessionConfigLoader loader, String sessionName, ClassLoader objectClassLoader, boolean shouldLoginSession, boolean shouldRefreshSession, boolean shouldCheckClassLoader) {
        if (loader == null){
            loader = new XMLSessionConfigLoader();
        }
        loader.setSessionName(sessionName);
        loader.setClassLoader(objectClassLoader);
        loader.setShouldLogin(shouldLoginSession);
        loader.setShouldRefresh(shouldRefreshSession);
        loader.setShouldCheckClassLoader(shouldCheckClassLoader);
        return getSession(loader);
    }
        
    /**
     * PUBLIC:
     * Return the session by name, loading the configuration from the file
     * specified in the loader, using the loading options provided on the loader.
     *
     * @param loader {@link XMLSessionConfigLoader} containing session configuration
     * @return session with given session name
     */
    public AbstractSession getSession(XMLSessionConfigLoader loader) { 
        AbstractSession session = (AbstractSession)getSessions().get(loader.getSessionName());
        boolean shouldRefreshSession = loader.shouldRefresh();
        if (loader.shouldCheckClassLoader() && (session != null) && !session.getDatasourcePlatform().getConversionManager().getLoader().equals(loader.getClassLoader())) {
            //bug 3766808  if a different classloader is being used then a reload of the session should
            //be completed otherwise failures may occur
            shouldRefreshSession = true;
        }
        if ((session == null) || shouldRefreshSession) {
            // PERF: Avoid synchronization for normal get.
            synchronized (this) {
                // Must re-assert checks inside synchronization.
                session = (AbstractSession)getSessions().get(loader.getSessionName());
                if (loader.shouldCheckClassLoader() && (session != null) && !session.getDatasourcePlatform().getConversionManager().getLoader().equals(loader.getClassLoader())) {
                    //bug 3766808  if a different classloader is being used then a reload of the session should
                    //be completed otherwise failures may occur
                    shouldRefreshSession = true;
                }
                if ((session == null) || shouldRefreshSession) {
                    if (session != null) {
                        if (session.isDatabaseSession() && session.isConnected()) {
                            // Must handles errors from logout as session maybe hosed.
                            try {
                                ((DatabaseSession)session).logout();
                            } catch (Throwable ignore) {
                                AbstractSessionLog.getLog().logThrowable(SessionLog.WARNING, AbstractSessionLog.CONNECTION, ignore);
                            }
                        }        
                        getSessions().remove(loader.getSessionName());
                    }
        
                    if (loader.load(this, loader.getClassLoader())) {
                        session = (AbstractSession)getSessions().get(loader.getSessionName());
                    }
                }
            }
        }

        // Connect the session if specified.
        if (session == null) {
            logAndThrowException(SessionLog.WARNING, ValidationException.noSessionFound(loader.getSessionName(), loader.getResourcePath()));
        } else if (loader.shouldLogin() && !session.isConnected()) {
            // PERF: Avoid synchronization for normal get.
            synchronized (this) {
                // Must re-assert checks inside synchronization.
                if (loader.shouldLogin() && !session.isConnected()) {
                    ((DatabaseSession)session).login();
                }
            }
        }

        return session;
    }

    /**
     * INTERNAL:
     * Log exceptions to the default log then throw them.
     */
    private void logAndThrowException(int level, RuntimeException exception) throws RuntimeException {
        AbstractSessionLog.getLog().logThrowable(level, AbstractSessionLog.CONNECTION, exception);
        throw exception;
    }


    /**
     * INTERNAL:
     * Set a hashtable of all sessions
     *
     * @param sessions sessions for this session manager
     */
    public void setSessions(ConcurrentMap sessions) {
        this.sessions = sessions;
    }

    /**
     * INTERNAL:
     * Return a hashtable on all sessions.
     *
     * @return all sessions known by this session manager
     */
    public ConcurrentMap<String, Session> getSessions() {
        return sessions;
    }

    /**
     * PUBLIC:
     * Set the default session.
     * If not set the session configuration is stored in a sessions.xml 
     * file in a TopLink directory on your classpath.
     * Other sessions are supported through the getSession by name API.
     *
     * @param defaultSession default session
     */
    public void setDefaultSession(Session defaultSession) {
        this.defaultSession = (org.eclipse.persistence.internal.sessions.AbstractSession)defaultSession;
        addSession("default", defaultSession);
    }

    /**
     * INTERNAL:
     * Set the singleton session manager.
     * This allows global access to a set of named sessions.
     *
     * @param theManager session manager for current context
     */
    public static void setManager(SessionManager theManager) {
        if (ctxHelper == null) {
             manager = theManager;
        } else {
            if (theManager.context == null) {
                synchronized (lock) {
                    if (theManager.context == null) {
                        theManager.context = ctxHelper.getPartitionID();
                    }
                }
            }
            managers.put(theManager.context, theManager);
        }
    }

    private static class ContextHelper {

        /**
         * Instance of CIC (Component Invocation Context) or null if not
         * initialized.
         */
        private Object cicManagerInstance;

        private Method getCurrentCicMethod;
        private Method getPartitionIdMethod;

        private static final Class cicManagerClass;
        private static ContextHelper instance;
        
        private static final String CIC_MANAGER_RESOURCE_NAME = "META-INF/services/weblogic.invocation.ComponentInvocationContextManager";
        private static final String CIC_MANAGER_CLASS_NAME = "weblogic.invocation.ComponentInvocationContextManager";

        static {
            if (PrivilegedAccessHelper.shouldUsePrivilegedAccess()) {
                cicManagerClass = AccessController.doPrivileged(new PrivilegedAction<Class>() {
                    @Override
                    public Class run() {
                        return getCicManagerClass(CIC_MANAGER_RESOURCE_NAME, CIC_MANAGER_CLASS_NAME);
                    }
                });
            } else {
                cicManagerClass = getCicManagerClass(CIC_MANAGER_RESOURCE_NAME, CIC_MANAGER_CLASS_NAME);
            }
        }
        
        private static Class getCicManagerClass(String cicManagerResourceName, String cicManagerClassName) {
            try {
                if (SessionManager.class.getClassLoader().getResource(cicManagerResourceName) != null) {
                    return PrivilegedAccessHelper.getClassForName(cicManagerClassName);
                } else {
                    return null;
                }
            } catch (ClassNotFoundException cnfe) {
                return null;
            }
        }

        private ContextHelper(final Class managerClass, final String contextClassName) {
            if (PrivilegedAccessHelper.shouldUsePrivilegedAccess()) {
                AccessController.doPrivileged(new PrivilegedAction<Void>() {

                    @Override
                    public Void run() {
                        initialize(managerClass, contextClassName);
                        return null;
                    }
                });
            } else {
                initialize(managerClass, contextClassName);
            }
        }

        private void initialize(final Class managerClass, final String contextClassName) {
            try {
                // Get component invocation manager
                final Method getInstance = PrivilegedAccessHelper.getDeclaredMethod(managerClass, "getInstance", new Class[]{});
                cicManagerInstance = PrivilegedAccessHelper.invokeMethod(getInstance, managerClass);

                // Get component invocation context
                getCurrentCicMethod = PrivilegedAccessHelper.getMethod(managerClass, "getCurrentComponentInvocationContext", new Class[]{}, true);

                final Class cicClass = PrivilegedAccessHelper.getClassForName(contextClassName);
                getPartitionIdMethod = PrivilegedAccessHelper.getDeclaredMethod(cicClass, "getPartitionId", new Class[]{});
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | ClassNotFoundException ex) {
                AbstractSessionLog.getLog().logThrowable(SessionLog.WARNING, null, ex);
            }
        }

        static ContextHelper getContextHelper() {
            if (cicManagerClass == null) {
                return null;
            }
            if (instance == null) {
                synchronized (ContextHelper.class) {
                    if (instance == null) {
                        instance = new ContextHelper(cicManagerClass, "weblogic.invocation.ComponentInvocationContext");
                    }
                }
            }
            return instance;
        }

        /**
         * Gets partition ID. Calls cicInstance.getPartitionIdMethod().
         */
        String getPartitionID() {
            try {
                if (PrivilegedAccessHelper.shouldUsePrivilegedAccess()) {
                    return AccessController.doPrivileged(new PrivilegedAction<String>() {

                        @Override
                        public String run() {
                            try {
                                final Object cicInstance = PrivilegedAccessHelper.invokeMethod(getCurrentCicMethod, cicManagerInstance);
                                return (String) PrivilegedAccessHelper.invokeMethod(getPartitionIdMethod, cicInstance);
                            } catch (IllegalAccessException | InvocationTargetException ex) {
                                AbstractSessionLog.getLog().logThrowable(SessionLog.WARNING, null, ex);
                                return "UNKNOWN";
                            }
                        }
                    });
                } else {
                    final Object cicInstance = PrivilegedAccessHelper.invokeMethod(getCurrentCicMethod, cicManagerInstance);
                    return (String) PrivilegedAccessHelper.invokeMethod(getPartitionIdMethod, cicInstance);
                }
            } catch (IllegalAccessException | InvocationTargetException ex) {
                AbstractSessionLog.getLog().logThrowable(SessionLog.WARNING, null, ex);
                return "UNKNOWN";
            }
        }
    }
}
