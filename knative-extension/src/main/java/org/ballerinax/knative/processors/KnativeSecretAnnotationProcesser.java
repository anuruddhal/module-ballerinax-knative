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

import org.apache.commons.codec.binary.Base64;
import org.ballerinalang.model.tree.AnnotationAttachmentNode;
import org.ballerinalang.model.tree.FunctionNode;
import org.ballerinalang.model.tree.IdentifierNode;
import org.ballerinalang.model.tree.ServiceNode;
import org.ballerinax.knative.exceptions.KnativePluginException;
import org.ballerinax.knative.models.KnativeContext;
import org.ballerinax.knative.models.SecretModel;
import org.ballerinax.knative.utils.KnativeUtils;
import org.wso2.ballerinalang.compiler.tree.BLangAnnotationAttachment;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangListConstructorExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordLiteral;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.ballerinax.knative.KnativeConstants.MAIN_FUNCTION_NAME;
import static org.ballerinax.knative.KnativeConstants.SECRET_POSTFIX;
import static org.ballerinax.knative.utils.KnativeUtils.convertRecordFields;
import static org.ballerinax.knative.utils.KnativeUtils.getBooleanValue;
import static org.ballerinax.knative.utils.KnativeUtils.getMap;
import static org.ballerinax.knative.utils.KnativeUtils.getStringValue;
import static org.ballerinax.knative.utils.KnativeUtils.getValidName;
import static org.ballerinax.knative.utils.KnativeUtils.isBlank;

/**
 * Secrets annotation processor knative.
 */
public class KnativeSecretAnnotationProcesser extends AbstractAnnotationProcessor {

    @Override
    public void processAnnotation(ServiceNode serviceNode, AnnotationAttachmentNode attachmentNode) throws
            KnativePluginException {
        processSecret(serviceNode.getName(), attachmentNode);
    }

    @Override
    public void processAnnotation(FunctionNode functionNode, AnnotationAttachmentNode attachmentNode) throws
            KnativePluginException {
        if (!MAIN_FUNCTION_NAME.equals(functionNode.getName().getValue())) {
            throw new KnativePluginException("@kubernetes:Secret{} annotation cannot be attached to a non main " +
                    "function.");
        }
        processSecret(functionNode.getName(), attachmentNode);
    }

    private void processSecret(IdentifierNode nodeID, AnnotationAttachmentNode attachmentNode) throws
            KnativePluginException {
        Set<SecretModel> secrets = new HashSet<>();
        List<BLangRecordLiteral.BLangRecordKeyValueField> keyValues =
            convertRecordFields(((BLangRecordLiteral) ((BLangAnnotationAttachment) attachmentNode).expr).getFields());
        for (BLangRecordLiteral.BLangRecordKeyValueField keyValue : keyValues) {
            List<BLangExpression> secretAnnotation = ((BLangListConstructorExpr) keyValue.valueExpr).exprs;
            for (BLangExpression bLangExpression : secretAnnotation) {
                SecretModel secretModel = new SecretModel();
                List<BLangRecordLiteral.BLangRecordKeyValueField> annotationValues =
                        convertRecordFields(((BLangRecordLiteral) bLangExpression).getFields());
                for (BLangRecordLiteral.BLangRecordKeyValueField annotation : annotationValues) {
                    SecretMountConfig secretMountConfig =
                            SecretMountConfig.valueOf(annotation.getKey().toString());
                    switch (secretMountConfig) {
                        case name:
                            secretModel.setName(getValidName(getStringValue(annotation.getValue())));
                            break;
                        case labels:
                            secretModel.setLabels(getMap(keyValue.getValue()));
                            break;
                        case annotations:
                            secretModel.setAnnotations(getMap(keyValue.getValue()));
                            break;
                        case mountPath:
                            secretModel.setMountPath(getStringValue(annotation.getValue()));
                            break;
                        case data:
                            List<BLangExpression> data = ((BLangListConstructorExpr) annotation.valueExpr).exprs;
                            secretModel.setData(getDataForSecret(data));
                            break;
                        case readOnly:
                            secretModel.setReadOnly(getBooleanValue(annotation.getValue()));
                            break;
                        default:
                            break;
                    }
                }
                if (isBlank(secretModel.getName())) {
                    secretModel.setName(getValidName(nodeID.getValue()) + SECRET_POSTFIX);
                }
                secrets.add(secretModel);
            }
        }
        KnativeContext.getInstance().getDataHolder().addSecrets(secrets);
    }

    private Map<String, String> getDataForSecret(List<BLangExpression> data) throws KnativePluginException {
        Map<String, String> dataMap = new HashMap<>();
        for (BLangExpression bLangExpression : data) {
            Path dataFilePath = Paths.get(getStringValue(bLangExpression));
            String key = String.valueOf(dataFilePath.getFileName());
            String content = Base64.encodeBase64String(KnativeUtils.readFileContent(dataFilePath));
            dataMap.put(key, content);
        }
        return dataMap;
    }

    /**
     * Enum class for volume configurations.
     */
    private enum SecretMountConfig {
        name,
        labels,
        annotations,
        mountPath,
        readOnly,
        data
    }
}
