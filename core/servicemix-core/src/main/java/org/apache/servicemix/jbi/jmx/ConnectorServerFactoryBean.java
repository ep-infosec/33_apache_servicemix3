/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.jbi.jmx;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.AccessController;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.security.auth.Subject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.Constants;
import org.springframework.jmx.support.JmxUtils;

/**
 * <code>FactoryBean</code> that creates a JSR-160 <code>JMXConnectorServer</code>,
 * optionally registers it with the <code>MBeanServer</code> and then starts it.
 *
 * <p>The <code>JMXConnectorServer</code> can be started in a separate thread by setting the
 * <code>threaded</code> property to <code>true</code>. You can configure this thread to be a
 * daemon thread by setting the <code>daemon</code> property to <code>true</code>.
 *
 * This xbean-enabled factory is a wrapper on top of the existing Spring
 * factory bean.  It also logs the serviceUrl when starting.
 *
 * @org.apache.xbean.XBean element="jmxConnector"
 */
public class ConnectorServerFactoryBean implements FactoryBean, InitializingBean, DisposableBean {

    /**
     * Constant indicating that registration should fail when
     * attempting to register an MBean under a name that already exists.
     * <p>This is the default registration behavior.
     */
    public static final int REGISTRATION_FAIL_ON_EXISTING = 0;

    /**
     * Constant indicating that registration should ignore the affected MBean
     * when attempting to register an MBean under a name that already exists.
     */
    public static final int REGISTRATION_IGNORE_EXISTING = 1;

    /**
     * Constant indicating that registration should replace the affected MBean
     * when attempting to register an MBean under a name that already exists.
     */
    public static final int REGISTRATION_REPLACE_EXISTING = 2;

    private static final Constants CONSTANTS = new Constants(ConnectorServerFactoryBean.class);

    private static final transient Logger LOGGER = LoggerFactory.getLogger(ConnectorServerFactoryBean.class);

    private org.springframework.jmx.support.ConnectorServerFactoryBean csfb = 
                    new org.springframework.jmx.support.ConnectorServerFactoryBean();
    private String serviceUrl = org.springframework.jmx.support.ConnectorServerFactoryBean.DEFAULT_SERVICE_URL;
    private boolean daemon;
    private boolean threaded;
    private Map environment;
    private Object objectName;
    private int registrationBehavior = REGISTRATION_FAIL_ON_EXISTING;
    private MBeanServer server;
    private Policy policy;


    /**
     * Set whether any threads started for the <code>JMXConnectorServer</code> should be
     * started as daemon threads.
     * @param daemon
     * @see org.springframework.jmx.support.ConnectorServerFactoryBean#setDaemon(boolean)
     */
    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    /**
     * Set the environment properties used to construct the <code>JMXConnector</code>
     * as a <code>Map</code> of String keys and arbitrary Object values.
     * @param environment
     * @see org.springframework.jmx.support.ConnectorServerFactoryBean#setEnvironmentMap(java.util.Map)
     */
    public void setEnvironment(Map environment) {
        this.environment = environment;
    }

    /**
     * Set the <code>ObjectName</code> used to register the <code>JMXConnectorServer</code>
     * itself with the <code>MBeanServer</code>.
     * @param objectName
     * @throws MalformedObjectNameException if the <code>ObjectName</code> is malformed
     */
    public void setObjectName(String objectName) throws MalformedObjectNameException {
        this.objectName = objectName;
    }
    
    /**
     * Specify  what action should be taken when attempting to register an MBean
     * under an {@link javax.management.ObjectName} that already exists.
     * <p>Default is REGISTRATION_FAIL_ON_EXISTING.
     * @see #setRegistrationBehaviorName(String)
     * @see #REGISTRATION_FAIL_ON_EXISTING
     * @see #REGISTRATION_IGNORE_EXISTING
     * @see #REGISTRATION_REPLACE_EXISTING
     * @param registrationBehavior
     * @see org.springframework.jmx.support.MBeanRegistrationSupport#setRegistrationBehavior(int)
     */
    public void setRegistrationBehavior(int registrationBehavior) {
        this.registrationBehavior = registrationBehavior;
    }

    /**
     * Set the registration behavior by the name of the corresponding constant,
     * e.g. "REGISTRATION_IGNORE_EXISTING".
     * @see #setRegistrationBehavior
     * @see #REGISTRATION_FAIL_ON_EXISTING
     * @see #REGISTRATION_IGNORE_EXISTING
     * @see #REGISTRATION_REPLACE_EXISTING
     * @param behavior
     * @see org.springframework.jmx.support.MBeanRegistrationSupport#setRegistrationBehaviorName(java.lang.String)
     */
    public void setRegistrationBehaviorName(String behavior) {
        setRegistrationBehavior(CONSTANTS.asNumber(behavior).intValue());
    }

    /**
     * Specify the <code>MBeanServer</code> instance with which all beans should
     * be registered. The <code>MBeanExporter</code> will attempt to locate an
     * existing <code>MBeanServer</code> if none is supplied.
     * @param server
     * @see org.springframework.jmx.support.MBeanRegistrationSupport#setServer(javax.management.MBeanServer)
     */
    public void setServer(MBeanServer server) {
        this.server = server;
    }

    /**
     * Set the service URL for the <code>JMXConnectorServer</code>.
     * @param serviceUrl
     * @see org.springframework.jmx.support.ConnectorServerFactoryBean#setServiceUrl(java.lang.String)
     */
    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    /**
     * Set whether the <code>JMXConnectorServer</code> should be started in a separate thread.
     * @param threaded
     * @see org.springframework.jmx.support.ConnectorServerFactoryBean#setThreaded(boolean)
     */
    public void setThreaded(boolean threaded) {
        csfb.setThreaded(threaded);
    }

    public Object getObject() throws Exception {
        return csfb.getObject();
    }

    public Class getObjectType() {
        return csfb.getObjectType();
    }

    public boolean isSingleton() {
        return csfb.isSingleton();
    }

    /**
     * 
     * @param policy
     */
    public void setPolicy(Policy policy) {
        this.policy = policy;
    }

    public void afterPropertiesSet() throws Exception {
        csfb = new org.springframework.jmx.support.ConnectorServerFactoryBean();
        csfb.setDaemon(daemon);
        csfb.setThreaded(threaded);
        csfb.setRegistrationBehavior(registrationBehavior);
        csfb.setEnvironmentMap(environment);
        csfb.setObjectName(objectName);
        serviceUrl = serviceUrl.replaceAll(" ", "");
        csfb.setServiceUrl(serviceUrl);
        
        MBeanServer mbs = server;
        if (policy != null) {
            LOGGER.info("Configuring JMX authorization policy: {}", policy);
            if (mbs == null) {
                mbs = createProxyForPolicy(JmxUtils.locateMBeanServer());
            } else {
                mbs = createProxyForPolicy(mbs);
            }
        } 
        csfb.setServer(mbs);
        
        csfb.afterPropertiesSet();
        LOGGER.info("JMX connector available at: {}", serviceUrl);
    }

    private MBeanServer createProxyForPolicy(final MBeanServer mbs) {
        final InvocationHandler handler = new PolicyAwareInvocationHandler(policy, mbs);

        Object proxy = Proxy.newProxyInstance(
                MBeanServer.class.getClassLoader(),
                new Class[] {MBeanServer.class},
                handler);

        return MBeanServer.class.cast(proxy);
    }

    public void destroy() throws Exception {
        if (csfb != null) {
            try {
                csfb.destroy();
            } finally {
                csfb = null;
            }
        }
    }

    public static class PolicyAwareInvocationHandler implements InvocationHandler {

        private final Policy policy;
        private final MBeanServer mbs;

        public PolicyAwareInvocationHandler(Policy policy, MBeanServer mbs) {
            this.policy = policy;
            this.mbs = mbs;
        }

        public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {

            // Retrieve Subject from current AccessControlContext
            Subject subject = Subject.getSubject(AccessController.getContext());

            if (subject == null)  {
                // running operation locally
                return method.invoke(mbs, args);
            } else {
                policy.checkAuthorization(subject, mbs, method, args);
                return method.invoke(mbs, args);
            }
        }
    }

}
