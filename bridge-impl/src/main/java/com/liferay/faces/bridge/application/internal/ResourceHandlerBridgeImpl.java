/**
 * Copyright (c) 2000-2016 Liferay, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.liferay.faces.bridge.application.internal;

import java.io.IOException;
import java.util.Map;

import javax.faces.application.Resource;
import javax.faces.application.ResourceHandler;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.portlet.PortalContext;
import javax.portlet.PortletConfig;
import javax.portlet.PortletRequest;

import com.liferay.faces.bridge.config.internal.PortletConfigParam;
import com.liferay.faces.bridge.context.BridgeContext;
import com.liferay.faces.bridge.context.BridgePortalContext;
import com.liferay.faces.util.application.ResourceHandlerWrapperBase;
import com.liferay.faces.util.logging.Logger;
import com.liferay.faces.util.logging.LoggerFactory;


/**
 * @author  Neil Griffin
 */
public class ResourceHandlerBridgeImpl extends ResourceHandlerWrapperBase {

	// Logger
	private static final Logger logger = LoggerFactory.getLogger(ResourceHandlerBridgeImpl.class);

	// Private Constants
	private static final String ENCODED_RESOURCE_TOKEN = "javax.faces.resource=";

	// Private Data Members
	private Integer bufferSize;

	public ResourceHandlerBridgeImpl(ResourceHandler resourceHandler) {
		super(resourceHandler);
	}

	/**
	 * Determines whether or not the specified URL has already been encoded.
	 *
	 * @param   url  The URL to check.
	 *
	 * @return  True if the specified URL is already encoded.
	 */
	public static boolean isEncodedFacesResourceURL(String url) {

		if ((url != null) && (url.indexOf(ENCODED_RESOURCE_TOKEN) > 0)) {
			return true;
		}
		else {
			return false;
		}
	}

	/**
	 * Determines whether or not the specified URL is a Faces URL.
	 *
	 * @param   url  The URL to check.
	 *
	 * @return  True if the specified URL is a Faces URL.
	 */
	public static boolean isFacesResourceURL(String url) {

		if ((url != null) && (url.indexOf("javax.faces.resource") >= 0)) {
			return true;
		}
		else {
			return false;
		}
	}

	@Override
	public Resource createResource(String resourceName) {
		Resource wrappableResource = getWrapped().createResource(resourceName);

		if (wrappableResource == null) {
			return new MissingResourceImpl(getWrapped(), resourceName);
		}
		else {
			return new ResourceImpl(wrappableResource);
		}
	}

	@Override
	public Resource createResource(String resourceName, String libraryName) {
		Resource wrappableResource = getWrapped().createResource(resourceName, libraryName);

		if (wrappableResource == null) {
			return new MissingResourceImpl(getWrapped(), resourceName, libraryName);
		}
		else {
			return new ResourceImpl(wrappableResource);
		}
	}

	@Override
	public Resource createResource(String resourceName, String libraryName, String contentType) {
		Resource wrappableResource = getWrapped().createResource(resourceName, libraryName, contentType);

		if (wrappableResource == null) {
			return new MissingResourceImpl(getWrapped(), resourceName, libraryName, contentType);
		}
		else {
			return new ResourceImpl(wrappableResource);
		}
	}

	/**
	 * This method handles the current request which is assumed to be a request for a {@link Resource}.
	 */
	@Override
	public void handleResourceRequest(FacesContext facesContext) throws IOException {

		ExternalContext externalContext = facesContext.getExternalContext();
		Map<String, String> requestParameterMap = externalContext.getRequestParameterMap();
		String resourceName = requestParameterMap.get("javax.faces.resource");

		// Assume that the resource  ExternalContext.encodeResourceURL(String) was properly called, and that
		// which adds the "javax.faces.resource" request parameter.
		// If the "javax.faces.resource" request parameter was found, then ask Faces to create the resource and
		// assume that calling resource.getInputStream() will provide the ability to send the contents of the
		// resource to the response.
		if (resourceName != null) {
			String libraryName = requestParameterMap.get("ln");

			if (logger.isTraceEnabled()) {

				// Surround with isTraceEnabled check in order to avoid unnecessary creation of object array.
				logger.trace("Handling - resourceName=[{0}], libraryName[{1}]",
					new Object[] { resourceName, libraryName });
			}

			// FACES-57: Provide the opportunity for applications to decorate the createResource methods of this
			// class by delegating creation of the resource to the chain-of-responsibility found in the application's
			// ResourceHandler.
			ResourceHandler resourceHandlerChain = facesContext.getApplication().getResourceHandler();
			Resource resource = null;

			if (libraryName == null) {
				resource = resourceHandlerChain.createResource(resourceName);
			}
			else {
				resource = resourceHandlerChain.createResource(resourceName, libraryName);
			}

			handleResource(facesContext, resource);
		}
		else {
			logger.debug("NOT HANDLED - Missing request parameter {0} so delegating handleResourceRequest to chain",
				"javax.faces.resource");
			getWrapped().handleResourceRequest(facesContext);
		}
	}

	/**
	 * Gets the size of the buffer (in bytes) that is to be used when loading contents of resources that are to be sent
	 * back via {@link ExternalContext#getResponseOutputStream()}. The default value is 1024 (1 kilobyte).
	 */
	@Override
	protected int getBufferSize(FacesContext facesContext) {

		if (bufferSize == null) {

			BridgeContext bridgeContext = BridgeContext.getCurrentInstance();
			PortletConfig portletConfig = bridgeContext.getPortletConfig();
			bufferSize = PortletConfigParam.ResourceBufferSize.getIntegerValue(portletConfig);
		}

		return bufferSize;
	}

	@Override
	protected boolean isAbleToSetHttpStatusCode(FacesContext facesContext) {

		BridgeContext bridgeContext = BridgeContext.getCurrentInstance();
		PortletRequest portletRequest = bridgeContext.getPortletRequest();
		PortalContext portalContext = portletRequest.getPortalContext();
		String setHttpStatusCodeSupport = portalContext.getProperty(BridgePortalContext.SET_HTTP_STATUS_CODE_SUPPORT);

		return (setHttpStatusCodeSupport != null);
	}

	@Override
	public boolean isResourceRequest(FacesContext facesContext) {

		// If the "javax.faces.resource" request parameter is present, then that means the resource's URL was
		// properly created with the ExternalContext.encodeResourceURL(String) method.
		ExternalContext externalContext = facesContext.getExternalContext();
		Map<String, String> requestParameterMap = externalContext.getRequestParameterMap();
		String resourceId = requestParameterMap.get("javax.faces.resource");

		if (resourceId != null) {
			logger.debug("Found {0} request parameter and recognized resourceId=[{1}] as a resource",
				new Object[] { "javax.faces.resource", resourceId });

			return true;
		}
		else {
			logger.debug("Did not find the {0} request parameter so delegating isResourceRequest to chain",
				"javax.faces.resource");

			return getWrapped().isResourceRequest(facesContext);
		}
	}
}