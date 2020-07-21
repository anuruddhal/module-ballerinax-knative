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
import org.ballerinalang.model.tree.IdentifierNode;
import org.ballerinalang.model.tree.ServiceNode;
import org.ballerinax.knative.exceptions.KnativePluginException;
import org.ballerinax.knative.models.ConfigMapModel;
import org.ballerinax.knative.models.KnativeContext;
import org.ballerinax.knative.utils.KnativeUtils;
import org.wso2.ballerinalang.compiler.tree.BLangAnnotationAttachment;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangListConstructorExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordLiteral;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.ballerinax.knative.KnativeConstants.BALLERINA_CONF_FILE_NAME;
import static org.ballerinax.knative.KnativeConstants.BALLERINA_CONF_MOUNT_PATH;
import static org.ballerinax.knative.KnativeConstants.BALLERINA_HOME;
import static org.ballerinax.knative.KnativeConstants.BALLERINA_RUNTIME;
import static org.ballerinax.knative.KnativeConstants.CONFIG_MAP_POSTFIX;
import static org.ballerinax.knative.KnativeConstants.MAIN_FUNCTION_NAME;
import static org.ballerinax.knative.utils.KnativeUtils.convertRecordFields;
import static org.ballerinax.knative.utils.KnativeUtils.getBooleanValue;
import static org.ballerinax.knative.utils.KnativeUtils.getMap;
import static org.ballerinax.knative.utils.KnativeUtils.getStringValue;
import static org.ballerinax.knative.utils.KnativeUtils.getValidName;
import static org.ballerinax.knative.utils.KnativeUtils.isBlank;

/**
 *  Knative ConfigMap annotation processor.
 */
public class KnativeConfigMapAnnotationProcessor extends AbstractAnnotationProcessor {

    @Override
    public void processAnnotation(ServiceNode serviceNode, AnnotationAttachmentNode attachmentNode) throws
            KnativePluginException {
        processConfigMaps(serviceNode.getName(), attachmentNode);
    }

    @Override
    public void processAnnotation(FunctionNode functionNode, AnnotationAttachmentNode attachmentNode) throws
            KnativePluginException {
        if (!MAIN_FUNCTION_NAME.equals(functionNode.getName().getValue())) {
            throw new KnativePluginException("@kubernetes:ConfigMap{} annotation cannot be attached to a non main " +
                    "function.");
        }

        processConfigMaps(functionNode.getName(), attachmentNode);
    }

    private void processConfigMaps(IdentifierNode nodeID, AnnotationAttachmentNode attachmentNode) throws
            KnativePluginException {
        Set<ConfigMapModel> configMapModels = new HashSet<>();
        List<BLangRecordLiteral.BLangRecordKeyValueField> keyValues =
            convertRecordFields(((BLangRecordLiteral) ((BLangAnnotationAttachment) attachmentNode).expr).getFields());
        for (BLangRecordLiteral.BLangRecordKeyValueField keyValue : keyValues) {
            String key = keyValue.getKey().toString();
            switch (key) {
                case "configMaps":
                    List<BLangExpression> configAnnotation = ((BLangListConstructorExpr) keyValue.valueExpr).exprs;
                    for (BLangExpression bLangExpression : configAnnotation) {
                        ConfigMapModel configMapModel = new ConfigMapModel();
                        List<BLangRecordLiteral.BLangRecordKeyValueField> annotationValues =
                                convertRecordFields(((BLangRecordLiteral) bLangExpression).getFields());
                        for (BLangRecordLiteral.BLangRecordKeyValueField annotation : annotationValues) {
                            ConfigMapMountConfig volumeMountConfig =
                                    ConfigMapMountConfig.
                                            valueOf(annotation.getKey().toString());
                            switch (volumeMountConfig) {
                                case name:
                                    configMapModel.setName(getValidName(getStringValue(annotation.getValue())));
                                    break;
                                case labels:
                                    configMapModel.setLabels(getMap(keyValue.getValue()));
                                    break;
                                case annotations:
                                    configMapModel.setAnnotations(getMap(keyValue.getValue()));
                                    break;
                                case mountPath:
                                    // validate mount path is not set to ballerina home or ballerina runtime
                                    final Path mountPath = Paths.get(getStringValue(annotation.getValue()));
                                    final Path homePath = Paths.get(BALLERINA_HOME);
                                    final Path runtimePath = Paths.get(BALLERINA_RUNTIME);
                                    final Path confPath = Paths.get(BALLERINA_CONF_MOUNT_PATH);
                                    if (mountPath.equals(homePath)) {
                                        throw new KnativePluginException("@kubernetes:ConfigMap{} mount path " +
                                                "cannot be ballerina home: " +
                                                BALLERINA_HOME);
                                    }
                                    if (mountPath.equals(runtimePath)) {
                                        throw new KnativePluginException("@kubernetes:ConfigMap{} mount path " +
                                                "cannot be ballerina runtime: " +
                                                BALLERINA_RUNTIME);
                                    }
                                    if (mountPath.equals(confPath)) {
                                        throw new KnativePluginException("@kubernetes:ConfigMap{} mount path " +
                                                "cannot be ballerina conf file mount " +
                                                "path: " + BALLERINA_CONF_MOUNT_PATH);
                                    }
                                    configMapModel.setMountPath(getStringValue(annotation.getValue()));
                                    break;
                                case data:
                                    List<BLangExpression> data =
                                            ((BLangListConstructorExpr) annotation.valueExpr).exprs;
                                    configMapModel.setData(getDataForConfigMap(data));
                                    break;
                                case readOnly:
                                    configMapModel.setReadOnly(getBooleanValue(annotation.getValue()));
                                    break;
                                default:
                                    break;
                            }
                        }
                        if (isBlank(configMapModel.getName())) {
                            configMapModel.setName(getValidName(nodeID.getValue()) + CONFIG_MAP_POSTFIX);
                        }
                        if (configMapModel.getData() != null && configMapModel.getData().size() > 0) {
                            configMapModels.add(configMapModel);
                        }
                    }
                    break;
                case "conf":
                    //create a new config map model with ballerina conf and add it to data holder.
                    configMapModels.add(getBallerinaConfConfigMap(keyValue.getValue().toString(), nodeID.getValue()));
                    break;
                default:
                    break;
            }
        }
        KnativeContext.getInstance().getDataHolder().addConfigMaps(configMapModels);
    }

    private Map<String, String> getDataForConfigMap(List<BLangExpression> data) throws KnativePluginException {
        Map<String, String> dataMap = new HashMap<>();
        for (BLangExpression bLangExpression : data) {
            Path dataFilePath = Paths.get(((BLangLiteral) bLangExpression).getValue().toString());
            if (!dataFilePath.isAbsolute()) {
                dataFilePath = KnativeContext.getInstance().getDataHolder().getSourceRoot().resolve(dataFilePath);
            }
            String key = String.valueOf(dataFilePath.getFileName());
            String content = new String(KnativeUtils.readFileContent(dataFilePath), StandardCharsets.UTF_8);
            dataMap.put(key, content);
        }
        return dataMap;
    }

    private ConfigMapModel getBallerinaConfConfigMap(String configFilePath, String serviceName) throws
            KnativePluginException {
        //create a new config map model with ballerina conf
        ConfigMapModel configMapModel = new ConfigMapModel();
        configMapModel.setName(getValidName(serviceName) + "-ballerina-conf" + CONFIG_MAP_POSTFIX);
        configMapModel.setMountPath(BALLERINA_CONF_MOUNT_PATH);
        Path dataFilePath = Paths.get(configFilePath);
        if (!dataFilePath.isAbsolute()) {
            dataFilePath = KnativeContext.getInstance().getDataHolder().getSourceRoot().resolve(dataFilePath)
                    .normalize();
        }
        String content = new String(KnativeUtils.readFileContent(dataFilePath), StandardCharsets.UTF_8);
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put(BALLERINA_CONF_FILE_NAME, content);
        configMapModel.setData(dataMap);
        configMapModel.setBallerinaConf(configFilePath);
        configMapModel.setReadOnly(false);
        return configMapModel;
    }

    /**
     * Enum class for volume configurations.
     */
    private enum ConfigMapMountConfig {
        name,
        labels,
        annotations,
        mountPath,
        readOnly,
        data
    }
}
