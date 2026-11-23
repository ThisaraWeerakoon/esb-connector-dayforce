/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.esb.mediator;

import org.apache.synapse.MessageContext;
import org.apache.synapse.mediators.AbstractMediator;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Method implementation.
 */
public class CeridianDayforceMediator extends AbstractMediator {
    private String uriPath;
    private String clientNameSpaceKey;
    private String serviceUri;

    @Override
    public boolean mediate(MessageContext synCtx) {
        try {
            String basePath = synCtx.getProperty("uri.var.uriPath").toString();
            clientNameSpaceKey = synCtx.getProperty("uri.var.clientNamespace").toString();
            uriPath = basePath + clientNameSpaceKey;
            String normalizedBase = normalizeRegistryPath(basePath);
            String normalizedResolved = normalizeRegistryPath(uriPath);
            if (!normalizedResolved.equals(normalizedBase)
                    && !normalizedResolved.startsWith(normalizedBase + "/")) {
                log.error("CERIDIAN Dayforce: rejected clientNamespace '" + clientNameSpaceKey
                        + "' - resolved registry path '" + normalizedResolved
                        + "' escapes the base namespace '" + normalizedBase
                        + "'. Skipping registry write.");
                return true;
            }

            serviceUri = synCtx.getProperty("SERVICE_URI").toString() +
                    clientNameSpaceKey.substring(clientNameSpaceKey.lastIndexOf("/"));
            if (serviceUri.startsWith("http://")) {
                serviceUri = serviceUri.substring(7);
            } else if (serviceUri.startsWith("https://")) {
                serviceUri = serviceUri.substring(8);
            }
            synCtx.getConfiguration().getRegistry().newResource(uriPath, false);
            synCtx.getConfiguration().getRegistry().updateResource(uriPath, serviceUri);
            synCtx.setProperty("uri.var.clientNamespace", serviceUri);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return true;
    }

    /**
     * Resolves "." and ".." in a '/'-separated registry path using plain string ops, so it
     * behaves the same on every OS. Any root-escaping ".." is kept as a literal segment so
     * such paths can never look like they're inside a valid base.
     *
     * @param path the raw (possibly traversal-containing) registry path
     * @return the normalised path with redundant and traversal segments resolved
     */
    private static String normalizeRegistryPath(String path) {
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (!segments.isEmpty() && !"..".equals(segments.peekLast())) {
                    segments.removeLast();
                } else {
                    segments.addLast("..");
                }
            } else {
                segments.addLast(segment);
            }
        }
        return String.join("/", segments);
    }
}