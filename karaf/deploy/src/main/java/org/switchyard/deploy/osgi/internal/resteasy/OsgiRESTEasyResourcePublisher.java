/*
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,  
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.switchyard.deploy.osgi.internal.resteasy;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.jboss.logging.Logger;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.osgi.service.http.HttpService;
import org.switchyard.ServiceDomain;
import org.switchyard.common.type.Classes;
import org.switchyard.component.common.Endpoint;
import org.switchyard.component.resteasy.resource.ResourcePublisher;

/**
 * Creates a RESTEasy Resource on karaf.
 */
public class OsgiRESTEasyResourcePublisher implements ResourcePublisher {

    private static final String KEY_SERVLET_REGISTRY = "org.switchyard.deploy.osgi.internal.resteasy.RESTEasyServletRegistry";
    private static final String KEY_SERVLET_MAPPING_PREFIX = "resteasy.servlet.mapping.prefix";
    private static final Logger _logger = Logger.getLogger(OsgiRESTEasyResourcePublisher.class);

    private HttpService _httpService;

    /**
     * Sets HttpService.
     * @param httpService HttpService
     */
    public void setHttpService(HttpService httpService) {
        _httpService = httpService;
    }

    @Override
    public Endpoint publish(ServiceDomain domain, String context, List<Object> instances) throws Exception {
        OsgiRESTEasyServletRegistry servletRegistry = (OsgiRESTEasyServletRegistry) domain.getProperty(KEY_SERVLET_REGISTRY);
        if (servletRegistry == null) {
            if (_logger.isDebugEnabled()) {
                _logger.debug("Creating OsgiRESTEasyServletRegistry");
            }
            servletRegistry = new OsgiRESTEasyServletRegistry().setOsgiHttpService(_httpService);
            domain.setProperty(KEY_SERVLET_REGISTRY, servletRegistry);
        }

        String alias = context.startsWith("/") ? context : "/" + context;
        OsgiRESTEasyServletWrapper servlet = servletRegistry.getRegisteredRESTEasyServlet(alias);
        if (servlet == null) {
            if (_logger.isDebugEnabled()) {
                _logger.debug("Registering RESTEasy servlet with an alias '" + alias + "'");
            }
            servlet = new OsgiRESTEasyServletWrapper().setClassLoader(Classes.getTCCL());
            Dictionary<String,String> initparams = new Hashtable<String,String>();
            initparams.put(KEY_SERVLET_MAPPING_PREFIX, alias);
            servletRegistry.registerRESTEasyServlet(alias, servlet, initparams, null);

            // A workaround for https://issues.jboss.org/browse/RESTEASY-640
            ResteasyProviderFactory repFactory = servlet.getDispatcher().getProviderFactory();
            for (Class<?> provider : RESTEasyProviders.PROVIDERS) {
                repFactory.registerProvider(provider);
            }
        }

        List<Class<?>> resourceClasses = servletRegistry.registerRESTEasyResources(alias, instances);
        return new OsgiRESTEasyResource()
                        .setServletRegistry(servletRegistry)
                        .setAlias(alias)
                        .setResourceClasses(resourceClasses);
    }

}
