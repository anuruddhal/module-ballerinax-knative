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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinax.knative.processors;

import org.ballerinalang.model.tree.AnnotationAttachmentNode;
import org.ballerinalang.model.tree.FunctionNode;
import org.ballerinalang.model.tree.ServiceNode;
import org.ballerinalang.model.tree.SimpleVariableNode;
import org.ballerinax.knative.exceptions.KnativePluginException;

/**
 * Annotation processor interface.
 */
public interface AnnotationProcessor {
    /**
     * Process annotations and create model object.
     *
     * @param serviceNode    Ballerina service node.
     * @param attachmentNode annotation attachment node.
     * @throws KnativePluginException if an error occurs while processing annotation.
     */
    void processAnnotation(ServiceNode serviceNode, AnnotationAttachmentNode attachmentNode)
            throws KnativePluginException;
    
    /**
     * Process annotations and create model object.
     *
     * @param variableNode Ballerina listener variable.
     * @param annotations  annotation attachment node.
     * @throws KnativePluginException if an error occurs while processing annotation.
     */
    void processAnnotation(SimpleVariableNode variableNode, AnnotationAttachmentNode annotations)
            throws KnativePluginException;


    /**
     * Process annotations and create model object.
     *
     * @param functionNode   Ballerina function node.
     * @param attachmentNode annotation attachment node.
     * @throws KnativePluginException if an error occurs while processing annotation.
     */
    void processAnnotation(FunctionNode functionNode, AnnotationAttachmentNode attachmentNode) throws
            KnativePluginException;

}
