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
package org.apache.servicemix.geronimo;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import javax.jbi.component.Component;
import javax.jbi.component.ServiceUnitManager;
import javax.management.MalformedObjectNameException;

import org.apache.geronimo.common.DeploymentException;
import org.apache.geronimo.deployment.ConfigurationBuilder;
import org.apache.geronimo.deployment.DeploymentContext;
import org.apache.geronimo.deployment.ModuleIDBuilder;
import org.apache.geronimo.deployment.service.EnvironmentBuilder;
import org.apache.geronimo.deployment.util.DeploymentUtil;
import org.apache.geronimo.deployment.xbeans.EnvironmentType;
import org.apache.geronimo.gbean.AbstractName;
import org.apache.geronimo.gbean.AbstractNameQuery;
import org.apache.geronimo.gbean.GBeanData;
import org.apache.geronimo.gbean.GBeanInfo;
import org.apache.geronimo.gbean.GBeanInfoBuilder;
import org.apache.geronimo.j2ee.j2eeobjectnames.NameFactory;
import org.apache.geronimo.kernel.GBeanNotFoundException;
import org.apache.geronimo.kernel.Kernel;
import org.apache.geronimo.kernel.config.ConfigurationAlreadyExistsException;
import org.apache.geronimo.kernel.config.ConfigurationModuleType;
import org.apache.geronimo.kernel.config.ConfigurationStore;
import org.apache.geronimo.kernel.config.ConfigurationUtil;
import org.apache.geronimo.kernel.repository.Artifact;
import org.apache.geronimo.kernel.repository.ArtifactResolver;
import org.apache.geronimo.kernel.repository.Environment;
import org.apache.geronimo.kernel.repository.ImportType;
import org.apache.geronimo.kernel.repository.Repository;
import org.apache.servicemix.geronimo.deployment.SMJbiDocument;
import org.apache.servicemix.jbi.deployment.Descriptor;
import org.apache.servicemix.jbi.deployment.DescriptorFactory;
import org.apache.servicemix.jbi.deployment.ServiceUnit;
import org.apache.servicemix.jbi.deployment.SharedLibraryList;
import org.apache.xmlbeans.XmlObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceMixConfigBuilder implements ConfigurationBuilder {

    private static final transient Logger LOGGER = LoggerFactory.getLogger(ServiceMixConfigBuilder.class);

    private final Environment defaultEnvironment;

    private final Collection repositories;

    private final AbstractNameQuery containerName;

    private final Kernel kernel;

    public static final GBeanInfo GBEAN_INFO;

    static {
        GBeanInfoBuilder infoFactory = new GBeanInfoBuilder(ServiceMixConfigBuilder.class, NameFactory.CONFIG_BUILDER);
        infoFactory.addInterface(ConfigurationBuilder.class);
        infoFactory.addAttribute("defaultEnvironment", Environment.class, true, true);
        infoFactory.addAttribute("kernel", Kernel.class, false);
        infoFactory.addAttribute("containerName", AbstractNameQuery.class, true, true);
        infoFactory.addReference("Repositories", Repository.class, "Repository");
        infoFactory.setConstructor(new String[] { "defaultEnvironment", "containerName", "Repositories", "kernel" });
        GBEAN_INFO = infoFactory.getBeanInfo();
    }

    public static GBeanInfo getGBeanInfo() {
        return GBEAN_INFO;
    }

    public ServiceMixConfigBuilder(Environment defaultEnvironment, AbstractNameQuery containerName, Collection repositories, Kernel kernel) {
        this.defaultEnvironment = defaultEnvironment;
        this.repositories = repositories;
        this.kernel = kernel;
        this.containerName = containerName;
    }

    /**
     * Builds a deployment plan specific to this builder from a planFile and/or
     * module if this builder can process it.
     * 
     * @param planFile
     *            the deployment plan to examine; can be null
     * @param module
     *            the URL of the module to examine; can be null
     * @return the deployment plan, or null if this builder can not handle the
     *         module
     * @throws org.apache.geronimo.common.DeploymentException
     *             if there was a problem with the configuration
     */
    public Object getDeploymentPlan(File planFile, JarFile module, ModuleIDBuilder idBuilder)
                    throws DeploymentException {
        LOGGER.debug("Checking for ServiceMix deployment.");
        if (module == null) {
            return null;
        }

        // Check that the jbi descriptor is present
        try {
            URL url = DeploymentUtil.createJarURL(module, "META-INF/jbi.xml");
            Descriptor descriptor = DescriptorFactory.buildDescriptor(url);
            if (descriptor == null) {
                return null;
            }
            DescriptorFactory.checkDescriptor(descriptor);
            
            XmlObject object = null;
			SMJbiDocument geronimoPlan = null;
			
			try {
				if (planFile != null) {
					object = XmlObject.Factory.parse(planFile);
				}
			} catch (Exception e) {
				LOGGER.info("Error", e);
			}
			
			if (object != null) {
				try {
					
					if (object instanceof SMJbiDocument) {
						geronimoPlan = (SMJbiDocument) object;	
					} else {
						geronimoPlan = (SMJbiDocument) object.changeType(SMJbiDocument.type);
					}
					
				} catch (Exception e) {
					throw new DeploymentException("Geronimo Plan found but wrong format!" + e.getMessage());
				}
			}
						
            return new DeploymentPlanWrapper(descriptor, geronimoPlan);
        } catch (Exception e) {
            LOGGER.debug("Not a ServiceMix deployment: no jbi.xml found.", e);
            // no jbi.xml, not for us
            return null;
        }
    }

    /**
     * Checks what configuration URL will be used for the provided module.
     * 
     * @param plan
     *            the deployment plan
     * @param module
     *            the module to build
     * @return the ID that will be used for the Configuration
     * @throws IOException
     *             if there was a problem reading or writing the files
     * @throws org.apache.geronimo.common.DeploymentException
     *             if there was a problem with the configuration
     */
    public Artifact getConfigurationID(Object plan, JarFile module, ModuleIDBuilder idBuilder) throws IOException,
                    DeploymentException {
        DeploymentPlanWrapper wrapper = (DeploymentPlanWrapper) plan;
        Descriptor descriptor = wrapper.getServicemixDescriptor();
        if (descriptor.getComponent() != null) {
            return new Artifact("servicemix-components", descriptor.getComponent().getIdentification().getName(),
                            "0.0", "car");
        } else if (descriptor.getServiceAssembly() != null) {
            return new Artifact("servicemix-assemblies", descriptor.getServiceAssembly().getIdentification().getName(),
                            "0.0", "car");
        } else if (descriptor.getSharedLibrary() != null) {
            return new Artifact("servicemix-libraries", descriptor.getSharedLibrary().getIdentification().getName(),
                            descriptor.getSharedLibrary().getVersion(), "car");
        } else {
            throw new DeploymentException("Unable to construct configuration ID " + module.getName()
                            + ": unrecognized jbi package. Should be a component, assembly or library.");
        }
    }

    /**
     * Build a configuration from a local file
     * 
     * @param inPlaceDeployment
     * @param configId
     * @param plan
     * @param jarFile
     * @param configurationStores
     * @param artifactResolver
     * @param targetConfigurationStore
     * @return the DeploymentContext information
     * @throws IOException
     *             if there was a problem reading or writing the files
     * @throws org.apache.geronimo.common.DeploymentException
     *             if there was a problem with the configuration
     */
    public DeploymentContext buildConfiguration(boolean inPlaceDeployment, Artifact configId, Object plan,
                    JarFile jarFile, Collection configurationStores, ArtifactResolver artifactResolver,
                    ConfigurationStore targetConfigurationStore) throws IOException, DeploymentException {
        if (plan == null) {
            LOGGER.warn("Expected a Descriptor but received null");
            return null;
        }
        if (plan instanceof DeploymentPlanWrapper == false) {
            LOGGER.warn("Expected a Descriptor but received a {}", plan.getClass().getName());
            return null;
        }
        if (((DeploymentPlanWrapper)plan).getServicemixDescriptor() == null) {
            LOGGER.warn("Expected a SM Descriptor but received null");
            return null;
        }
        File configurationDir;
        try {
            configurationDir = targetConfigurationStore.createNewConfigurationDir(configId);
        } catch (ConfigurationAlreadyExistsException e) {
            throw new DeploymentException(e);
        }

        Environment environment = new Environment();
        environment.setConfigId(configId);
        EnvironmentBuilder.mergeEnvironments(environment, defaultEnvironment);

        DeploymentPlanWrapper wrapper = (DeploymentPlanWrapper) plan;
        if (wrapper.getGeronimoPlan() != null) {
                	
        	if (wrapper.getGeronimoPlan().getJbi() != null) {
        		EnvironmentType environmentType = wrapper.getGeronimoPlan().getJbi().getEnvironment();
        		if (environmentType != null) {
        			LOGGER.debug("Environment found in Geronimo Plan for ServiceMix {}", environmentType);
        			Environment geronimoPlanEnvironment = EnvironmentBuilder.buildEnvironment(environmentType);
        			EnvironmentBuilder.mergeEnvironments(environment, geronimoPlanEnvironment);
        		} else {
        			LOGGER.debug("no additional environment entry found in deployment plan for JBI component");
        		}
        	}
        }

        DeploymentContext context = null;
        try {
            Descriptor descriptor = wrapper.getServicemixDescriptor();
            Map name = new HashMap();
            name.put("Config", configId.toString());
            context = new DeploymentContext(configurationDir,
                            inPlaceDeployment ? DeploymentUtil.toFile(jarFile) : null, environment,
                            new AbstractName(configId, name),
                            ConfigurationModuleType.SERVICE, kernel.getNaming(), ConfigurationUtil
                                            .getConfigurationManager(kernel), repositories);
            if (descriptor.getComponent() != null) {
                buildComponent(descriptor, context, jarFile);
            } else if (descriptor.getServiceAssembly() != null) {
                buildServiceAssembly(descriptor, context, jarFile);
            } else if (descriptor.getSharedLibrary() != null) {
                buildSharedLibrary(descriptor, context, jarFile);
            } else {
                throw new IllegalStateException("Invalid jbi descriptor");
            }
        } catch (Exception e) {
            if (context != null) {
                context.close();
            }
            DeploymentUtil.recursiveDelete(configurationDir);
            throw new DeploymentException("Unable to deploy", e);
        }

        return context;
    }

    protected void buildComponent(Descriptor descriptor, DeploymentContext context, JarFile module) throws Exception {
        Environment environment = context.getConfiguration().getEnvironment();
        // Unzip the component
        File targetDir = new File(context.getBaseDir(), "install");
        targetDir.mkdirs();
        unzip(context, module, new URI("install/"));
        // Create workspace dir
        File workDir = new File(context.getBaseDir(), "workspace");
        workDir.mkdirs();
        // Create the bootstrap and perform installation
        // TODO: Create the bootstrap and perform installation
        // Add classpath entries
        if ("self-first".equals(descriptor.getComponent().getComponentClassLoaderDelegation())) {
            context.getConfiguration().getEnvironment().setInverseClassLoading(true);
        }
        SharedLibraryList[] slList = descriptor.getComponent().getSharedLibraries();
        if (slList != null) {
            for (int i = 0; i < slList.length; i++) {
                Artifact sl = new Artifact("servicemix-libraries", slList[i].getName(), slList[i].getVersion(), "car");
                environment.addDependency(sl, ImportType.CLASSES);
            }
        }
        if (descriptor.getComponent().getComponentClassPath() != null) {
            String[] pathElements = descriptor.getComponent().getComponentClassPath().getPathElements();
            if (pathElements != null) {
                for (int i = 0; i < pathElements.length; i++) {
                    context.getConfiguration().addToClassPath(new URI("install/").resolve(pathElements[i]).toString());
                }
            }
        }
        // Create the JBI deployment managed object
        Properties props = new Properties();
        props.put("jbiType", "JBIComponent");
        props.put("name", descriptor.getComponent().getIdentification().getName());
        AbstractName name = new AbstractName(environment.getConfigId(), props);
        GBeanData gbeanData = new GBeanData(name, org.apache.servicemix.geronimo.Component.GBEAN_INFO);
        gbeanData.setAttribute("name", descriptor.getComponent().getIdentification().getName());
        gbeanData.setAttribute("description", descriptor.getComponent().getIdentification().getDescription());
        gbeanData.setAttribute("type", descriptor.getComponent().getType());
        gbeanData.setAttribute("className", descriptor.getComponent().getComponentClassName());
        gbeanData.setReferencePattern("container", containerName);
        context.addGBean(gbeanData);
    }

    protected void buildServiceAssembly(Descriptor descriptor, DeploymentContext context, JarFile module)
                    throws Exception {
        Environment environment = context.getConfiguration().getEnvironment();
        // Unzip the component
        File targetDir = new File(context.getBaseDir(), "install");
        targetDir.mkdirs();
        unzip(context, module, new URI("install/"));
        // Unzip SUs
        ServiceUnit[] sus = descriptor.getServiceAssembly().getServiceUnits();
        List<ServiceUnitReference> serviceUnitReferences = new LinkedList<ServiceUnitReference>();
        
        for (int i = 0; i < sus.length; i++) {
            String name = sus[i].getIdentification().getName();
            String zip = sus[i].getTarget().getArtifactsZip();
            String comp = sus[i].getTarget().getComponentName();
            URI installUri = new URI("sus/" + comp + "/" + name + "/");
            unzip(context, new JarFile(new File(targetDir, zip)), installUri);
            // Add component config as a dependency
            Artifact sl = new Artifact("servicemix-components", comp, "0.0", "car");
            environment.addDependency(sl, ImportType.ALL);
             // Deploy the SU on the component
             Component jbiServiceUnit = null;
             try {
                 jbiServiceUnit = getAssociatedJbiServiceUnit(comp, sl);
             } catch (GBeanNotFoundException e) {
                 throw new DeploymentException("Can not find the associated service unit for this service assembly. "
                         + "Check if it's deployed and started.", e);
             }
             ServiceUnitManager serviceUnitManager = jbiServiceUnit.getServiceUnitManager();
             File installDir = new File(context.getBaseDir(), installUri.toString());
             String deploy = serviceUnitManager.deploy(name, installDir.getAbsolutePath());  
             serviceUnitReferences.add(new ServiceUnitReference(sl, name, installDir.getAbsolutePath()));
             LOGGER.debug(deploy);
        }
        // Create the JBI deployment managed object
        Properties props = new Properties();
        props.put("jbiType", "JBIServiceAssembly");
        props.put("name", descriptor.getServiceAssembly().getIdentification().getName());
        AbstractName name = new AbstractName(environment.getConfigId(), props);
        GBeanData gbeanData = new GBeanData(name, ServiceAssembly.GBEAN_INFO);
        gbeanData.setAttribute("name", descriptor.getServiceAssembly().getIdentification().getName());
        gbeanData.setAttribute("serviceUnitReferences", serviceUnitReferences);
        gbeanData.setReferencePattern("container", containerName);
        for (int i = 0; i < sus.length; i++) {
            String comp = sus[i].getTarget().getComponentName();
            gbeanData.addDependency(getComponentName(comp));
        }
        context.addGBean(gbeanData);
    }

    /**
     * Returns the JBI ServiceUnit with the given 'ArtifactName' and
     * the given componentName. 
     * 
     * @param compName Name
     * @param artifactName Name
     * @return Component instance
     * @throws GBeanNotFoundException if the ServiceUnit cannot be found
     */
    private Component getAssociatedJbiServiceUnit(String compName, Artifact artifactName) throws GBeanNotFoundException {
        org.apache.servicemix.geronimo.Component serviceUnit = getComponentGBean(
				compName, artifactName);
        Component jbiServiceUnit = serviceUnit.getComponent();
        return jbiServiceUnit;
    }

    /**
     * Returns the sm.ger.Component with the given name
     * 
     * @param compName
     * @param artifactName
     * @return
     * @throws GBeanNotFoundException
     */
	private org.apache.servicemix.geronimo.Component getComponentGBean(
			String compName, Artifact artifactName)
			throws GBeanNotFoundException {
		Properties props = new Properties();
        props.put("jbiType", "JBIComponent");
        props.put("name", compName);
        org.apache.servicemix.geronimo.Component component = 
                (org.apache.servicemix.geronimo.Component) kernel.getGBean(new AbstractName(artifactName, props));
        return component;
    }

    protected void buildSharedLibrary(Descriptor descriptor, DeploymentContext context, JarFile module)
                    throws Exception {
        Environment environment = context.getConfiguration().getEnvironment();
        // Unzip the SL
        File targetDir = new File(context.getBaseDir(), "install");
        targetDir.mkdirs();
        unzip(context, module, new URI("install/"));
        // Create workspace dir
        File workDir = new File(context.getBaseDir(), "workspace");
        workDir.mkdirs();
        // Add classpath entries
        if ("self-first".equals(descriptor.getSharedLibrary().getClassLoaderDelegation())) {
            context.getConfiguration().getEnvironment().setInverseClassLoading(true);
        }
        if (descriptor.getSharedLibrary().getSharedLibraryClassPath() != null) {
            String[] pathElements = descriptor.getSharedLibrary().getSharedLibraryClassPath().getPathElements();
            if (pathElements != null) {
                for (int i = 0; i < pathElements.length; i++) {
                    LOGGER.debug("Processing pathElements[{}]: {}", i, pathElements[i]);
                    // We can not add includes directly, so move the file and
                    // include it
                    File include = new File(targetDir, pathElements[i]);
                    File temp = new File(workDir, pathElements[i]);
                    if (!include.isFile()) {
                        throw new Exception("Classpath element '" + pathElements[i] + "' not found");
                    }
                    temp.getParentFile().mkdirs();
                    include.renameTo(temp);
                    context.addInclude(new URI("install/").resolve(pathElements[i]), temp);
                    temp.delete();
                }
            } else {
                LOGGER.debug("SharedLibrary().getSharedLibraryClassPath().getPathElements() is null");
            }
        } else {
            LOGGER.debug("SharedLibrary().getSharedLibraryClassPath() is null");
        }
        // Create the JBI deployment managed object
        Properties props = new Properties();
        props.put("jbiType", "JBISharedLibrary");
        props.put("name", descriptor.getSharedLibrary().getIdentification().getName());
        AbstractName name = new AbstractName(environment.getConfigId(), props);
        GBeanData gbeanData = new GBeanData(name, SharedLibrary.GBEAN_INFO);
        gbeanData.setAttribute("name", descriptor.getSharedLibrary().getIdentification().getName());
        gbeanData.setAttribute("description", descriptor.getSharedLibrary().getIdentification().getDescription());
        gbeanData.setReferencePattern("container", containerName);
        context.addGBean(gbeanData);
    }

    protected void unzip(DeploymentContext context, JarFile module, URI targetUri) throws IOException {
        Enumeration entries = module.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) entries.nextElement();
            URI target = targetUri.resolve(entry.getName());
            context.addFile(target, module, entry);
        }
    }

    protected AbstractNameQuery getComponentName(String name) throws MalformedObjectNameException {
        URI uri = URI.create("servicemix-components/" + name + "//car?jbiType=JBIComponent");
        return new AbstractNameQuery(uri);
    }

}
