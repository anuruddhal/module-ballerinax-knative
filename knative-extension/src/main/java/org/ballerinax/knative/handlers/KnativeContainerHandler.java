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

package org.ballerinax.knative.handlers;

import org.ballerinax.knative.KnativeConstants;
import org.ballerinax.knative.exceptions.KnativePluginException;
import org.ballerinax.knative.models.KnativeContainerModel;
import org.ballerinax.knative.models.KnativeContext;
import org.ballerinax.knative.models.ServiceModel;

import java.util.Map;

import static org.ballerinax.docker.generator.utils.DockerGenUtils.extractJarName;

/**
 * Generates kubernetes service from annotations.
 */
public class KnativeContainerHandler extends KnativeAbstractArtifactHandler {

    /**
     * Generate kubernetes service definition from annotation.
     *
     * @throws KnativePluginException If an error occurs while generating artifact.
     */
    private void generate(KnativeContainerModel serviceModel) throws KnativePluginException {
    }

    @Override
    public void createArtifacts() throws KnativePluginException {
        // Service
        ServiceModel deploymentModel = knativeDataHolder.getServiceModel();
        Map<String, KnativeContainerModel> serviceModels = knativeDataHolder.getbListenerToK8sServiceMap();
        int count = 0;
        for (KnativeContainerModel serviceModel : serviceModels.values()) {
            count++;
            String balxFileName = extractJarName(KnativeContext.getInstance().getDataHolder()
                    .getUberJarPath());
            serviceModel.addLabel(KnativeConstants.KUBERNETES_SELECTOR_KEY, balxFileName);
            serviceModel.setSelector(balxFileName);
            generate(serviceModel);
            deploymentModel.addPort(serviceModel.getTargetPort());
        }
    }
}
