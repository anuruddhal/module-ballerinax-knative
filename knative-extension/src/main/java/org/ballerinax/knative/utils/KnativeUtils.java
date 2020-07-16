/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 *
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

package org.ballerinax.knative.utils;

import io.fabric8.kubernetes.api.model.ConfigMapKeySelector;
import io.fabric8.kubernetes.api.model.ConfigMapKeySelectorBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.ObjectFieldSelector;
import io.fabric8.kubernetes.api.model.ObjectFieldSelectorBuilder;
import io.fabric8.kubernetes.api.model.ResourceFieldSelector;
import io.fabric8.kubernetes.api.model.ResourceFieldSelectorBuilder;
import io.fabric8.kubernetes.api.model.SecretKeySelector;
import io.fabric8.kubernetes.api.model.SecretKeySelectorBuilder;
import org.ballerinalang.model.tree.NodeKind;
import org.ballerinax.docker.generator.models.CopyFileModel;
import org.ballerinax.knative.exceptions.KnativePluginException;
import org.ballerinax.knative.models.EnvVarValueModel;
import org.ballerinax.knative.models.KnativeContext;
import org.ballerinax.knative.models.KnativeDataHolder;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BConstantSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.types.BFiniteType;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangListConstructorExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangSimpleVarRef;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.ballerinax.docker.generator.utils.DockerGenUtils.extractJarName;
import static org.ballerinax.knative.KnativeConstants.KNATIVE;
import static org.ballerinax.knative.KnativeConstants.YAML;

/**
 * Util methods used for artifact generation.
 */
public class KnativeUtils {

    private static final PrintStream ERR = System.err;
    private static final PrintStream OUT = System.out;

    /**
     * Write content to a File. Create the required directories if they don't not exists.
     *
     * @param context context of the file
     * @throws IOException If an error occurs when writing to a file
     */
    public static void writeToFile(String context) throws IOException {
        KnativeDataHolder dataHolder = KnativeContext.getInstance().getDataHolder();
        writeToFile(dataHolder.getK8sArtifactOutputPath().resolve(KNATIVE), context);
    }

    /**
     * Write content to a File. Create the required directories if they don't not exists.
     *
     * @param outputDir Artifact output path.
     * @param context   Context of the file
     * @throws IOException If an error occurs when writing to a file
     */
    public static void writeToFile(Path outputDir, String context) throws IOException {
        KnativeDataHolder dataHolder = KnativeContext.getInstance().getDataHolder();
        // Priority given for job, then deployment.
        Path artifactFileName = outputDir.resolve(extractJarName(dataHolder.getUberJarPath()) + YAML);

        File newFile = artifactFileName.toFile();
        // append if file exists
        if (newFile.exists()) {
            Files.write(artifactFileName, context.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.APPEND);
            return;
        }
        //create required directories
        newFile.getParentFile().mkdirs();
        Files.write(artifactFileName, context.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Read contents of a File.
     *
     * @param targetFilePath target file path
     * @throws KnativePluginException If an error occurs when reading file
     */
    public static byte[] readFileContent(Path targetFilePath) throws KnativePluginException {
        File file = targetFilePath.toFile();
        // append if file exists
        if (file.exists() && !file.isDirectory()) {
            try {
                return Files.readAllBytes(targetFilePath);
            } catch (IOException e) {
                throw new KnativePluginException("unable to read contents of the file " + targetFilePath);
            }
        }
        throw new KnativePluginException("unable to read contents of the file " + targetFilePath);
    }


    /**
     * Prints an Error message.
     *
     * @param msg message to be printed
     */
    public static void printError(String msg) {
        ERR.println("error [k8s plugin]: " + msg);
    }

    /**
     * Prints an Instruction message.
     *
     * @param msg message to be printed
     */
    public static void printInstruction(String msg) {
        OUT.println(msg);
    }

    /**
     * Deletes a given directory.
     *
     * @param path path to directory
     * @throws KnativePluginException if an error occurs while deleting
     */
    public static void deleteDirectory(Path path) throws KnativePluginException {
        Path pathToBeDeleted = path.toAbsolutePath();
        if (!Files.exists(pathToBeDeleted)) {
            return;
        }
        try {
            Files.walk(pathToBeDeleted)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            throw new KnativePluginException("unable to delete directory: " + path, e);
        }
    }

    /* Checks if a String is empty ("") or null.
     *
     * @param str the String to check, may be null
     * @return true if the String is empty or null
     */
    public static boolean isBlank(String str) {
        int strLen;
        if (str != null && (strLen = str.length()) != 0) {
            for (int i = 0; i < strLen; ++i) {
                if (!Character.isWhitespace(str.charAt(i))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Resolve the given value by processing $env{} place-holders.
     *
     * @param value The user provided value
     * @return The resolved value
     */
    public static String resolveValue(String value) throws KnativePluginException {
        int startIndex;
        if ((startIndex = value.indexOf("$env{")) >= 0) {
            int endIndex = value.indexOf("}", startIndex);
            if (endIndex > 0) {
                String varName = value.substring(startIndex + 5, endIndex).trim();
                String resolvedVar = Optional.ofNullable(System.getenv(varName)).orElseThrow(() ->
                        new KnativePluginException("error resolving value: " + varName +
                                " is not set in the environment."));
                String rest = (value.length() > endIndex + 1) ? resolveValue(value.substring(endIndex + 1)) : "";
                return value.substring(0, startIndex) + resolvedVar + rest;
            }
        }
        return value;
    }

    /**
     * Get a map from a ballerina expression.
     *
     * @param expr Ballerina record value.
     * @return Map of key values.
     * @throws KnativePluginException When the expression cannot be parsed.
     */
    public static Map<String, String> getMap(BLangExpression expr) throws KnativePluginException {
        if (expr.getKind() != NodeKind.RECORD_LITERAL_EXPR) {
            throw new KnativePluginException("unable to parse value: " + expr.toString());
        } else {
            Map<String, String> map = new LinkedHashMap<>();
            if (expr instanceof BLangRecordLiteral) {
                BLangRecordLiteral fields = (BLangRecordLiteral) expr;
                for (BLangRecordLiteral.BLangRecordKeyValueField keyValue : convertRecordFields(fields.getFields())) {
                    map.put(keyValue.getKey().toString(), getStringValue(keyValue.getValue()));
                }
            }
            return map;
        }
    }

    /**
     * Get the boolean value from a ballerina expression.
     *
     * @param expr Ballerina boolean value.
     * @return Parsed boolean value.
     * @throws KnativePluginException When the expression cannot be parsed.
     */
    public static boolean getBooleanValue(BLangExpression expr) throws KnativePluginException {
        return Boolean.parseBoolean(getStringValue(expr));
    }

    /**
     * Get the integer value from a ballerina expression.
     *
     * @param expr Ballerina integer value.
     * @return Parsed integer value.
     * @throws KnativePluginException When the expression cannot be parsed.
     */
    public static int getIntValue(BLangExpression expr) throws KnativePluginException {
        return Integer.parseInt(getStringValue(expr));
    }

    /**
     * Get the string value from a ballerina expression.
     *
     * @param expr Ballerina string value.
     * @return Parsed string value.
     * @throws KnativePluginException When the expression cannot be parsed.
     */
    public static String getStringValue(BLangExpression expr) throws KnativePluginException {
        if (expr instanceof BLangSimpleVarRef) {
            BLangSimpleVarRef varRef = (BLangSimpleVarRef) expr;
            final BSymbol symbol = varRef.symbol;
            if (symbol instanceof BConstantSymbol) {
                BConstantSymbol constantSymbol = (BConstantSymbol) symbol;
                if (constantSymbol.type instanceof BFiniteType) {
                    // Parse compile time constant
                    BFiniteType compileConst = (BFiniteType) constantSymbol.type;
                    if (compileConst.getValueSpace().size() > 0) {
                        return resolveValue(compileConst.getValueSpace().iterator().next().toString());
                    }
                }
            }
        } else if (expr instanceof BLangLiteral) {
            return resolveValue(expr.toString());
        }
        throw new KnativePluginException("unable to parse value: " + expr.toString());
    }

    /**
     * Returns valid kubernetes name.
     *
     * @param name actual value
     * @return valid name
     */
    public static String getValidName(String name) {
        return name.toLowerCase(Locale.getDefault()).replace("_", "-").replace(".", "-");
    }

    /**
     * Convert environment variable values into a map for deployment model.
     *
     * @param envVarValues Value of env field of Deployment annotation.
     * @return A map of env var models.
     */
    public static Map<String, EnvVarValueModel> getEnvVarMap(BLangExpression envVarValues)
            throws KnativePluginException {
        Map<String, EnvVarValueModel> envVarMap = new LinkedHashMap<>();
        if (envVarValues.getKind() == NodeKind.RECORD_LITERAL_EXPR) {
            if (envVarValues instanceof BLangRecordLiteral) {
                for (BLangRecordLiteral.BLangRecordKeyValueField envVar
                        : convertRecordFields(((BLangRecordLiteral) envVarValues).getFields())) {
                    String envVarName = envVar.getKey().toString();
                    EnvVarValueModel envVarValue = null;
                    if (envVar.getValue().getKind() == NodeKind.LITERAL) {
                        // Value is a string
                        envVarValue = new EnvVarValueModel(getStringValue(envVar.getValue()));
                    } else if (envVar.getValue().getKind() == NodeKind.RECORD_LITERAL_EXPR) {
                        BLangRecordLiteral valueFrom = (BLangRecordLiteral) envVar.getValue();
                        BLangRecordLiteral.BLangRecordKeyValueField bRefType = convertRecordFields(
                                valueFrom.getFields()).get(0);
                        BLangSimpleVarRef refType = (BLangSimpleVarRef) bRefType.getKey();
                        switch (refType.variableName.toString()) {
                            case "fieldRef":
                                BLangRecordLiteral.BLangRecordKeyValueField fieldRefValue =
                                        convertRecordFields(((BLangRecordLiteral) bRefType.getValue()).getFields())
                                                .get(0);
                                EnvVarValueModel.FieldRef fieldRefModel = new EnvVarValueModel.FieldRef();
                                fieldRefModel.setFieldPath(getStringValue(fieldRefValue.getValue()));
                                envVarValue = new EnvVarValueModel(fieldRefModel);
                                break;
                            case "secretKeyRef":
                                EnvVarValueModel.SecretKeyRef secretKeyRefModel = new EnvVarValueModel.SecretKeyRef();
                                for (BLangRecordLiteral.BLangRecordKeyValueField secretKeyRefFields :
                                        convertRecordFields(((BLangRecordLiteral) bRefType.getValue()).getFields())) {
                                    if (secretKeyRefFields.getKey().toString().equals("key")) {
                                        secretKeyRefModel.setKey(getStringValue(secretKeyRefFields.getValue()));
                                    } else if (secretKeyRefFields.getKey().toString().equals("name")) {
                                        secretKeyRefModel.setName(getStringValue(secretKeyRefFields.getValue()));
                                    }
                                }
                                envVarValue = new EnvVarValueModel(secretKeyRefModel);
                                break;
                            case "resourceFieldRef":
                                EnvVarValueModel.ResourceFieldRef resourceFieldRefModel =
                                        new EnvVarValueModel.ResourceFieldRef();
                                for (BLangRecordLiteral.BLangRecordKeyValueField resourceFieldRefFields :
                                        convertRecordFields(((BLangRecordLiteral) bRefType.getValue()).getFields())) {
                                    if (resourceFieldRefFields.getKey().toString().equals("containerName")) {
                                        resourceFieldRefModel.setContainerName(
                                                getStringValue(resourceFieldRefFields.getValue()));
                                    } else if (resourceFieldRefFields.getKey().toString().equals("resource")) {
                                        resourceFieldRefModel.setResource(
                                                getStringValue(resourceFieldRefFields.getValue()));
                                    }
                                }
                                envVarValue = new EnvVarValueModel(resourceFieldRefModel);
                                break;
                            case "configMapKeyRef":
                                EnvVarValueModel.ConfigMapKeyValue configMapKeyRefModel =
                                        new EnvVarValueModel.ConfigMapKeyValue();
                                for (BLangRecordLiteral.BLangRecordKeyValueField configMapKeyRefFields :
                                        convertRecordFields(((BLangRecordLiteral) bRefType.getValue()).getFields())) {
                                    if (configMapKeyRefFields.getKey().toString().equals("key")) {
                                        configMapKeyRefModel.setKey(getStringValue(configMapKeyRefFields.getValue()));
                                    } else if (configMapKeyRefFields.getKey().toString().equals("name")) {
                                        configMapKeyRefModel.setName(getStringValue(configMapKeyRefFields.getValue()));
                                    }
                                }
                                envVarValue = new EnvVarValueModel(configMapKeyRefModel);
                                break;
                            default:
                                break;
                        }
                    }
                    envVarMap.put(envVarName, envVarValue);
                }
            }
        }
        return envVarMap;
    }

    /**
     * Get Image pull secrets.
     *
     * @param keyValue Value of imagePullSecret field of Job annotation.
     * @return A set of image pull secrets.
     */
    public static Set<String> getImagePullSecrets(BLangRecordLiteral.BLangRecordKeyValueField keyValue) throws
            KnativePluginException {
        Set<String> imagePullSecrets = new HashSet<>();
        List<BLangExpression> configAnnotation = ((BLangListConstructorExpr) keyValue.valueExpr).exprs;
        for (BLangExpression bLangExpression : configAnnotation) {
            imagePullSecrets.add(getStringValue(bLangExpression));
        }
        return imagePullSecrets;
    }

    /**
     * Get the set of external files to copy to docker image.
     *
     * @param keyValue Value of copyFiles field of Job annotation.
     * @return A set of external files
     * @throws KnativePluginException if an error occur while getting the paths
     */
    public static Set<CopyFileModel> getExternalFileMap(BLangRecordLiteral.BLangRecordKeyValueField keyValue) throws
            KnativePluginException {
        Set<CopyFileModel> externalFiles = new HashSet<>();
        List<BLangExpression> configAnnotation = ((BLangListConstructorExpr) keyValue.valueExpr).exprs;
        for (BLangExpression bLangExpression : configAnnotation) {
            List<BLangRecordLiteral.BLangRecordKeyValueField> annotationValues =
                    convertRecordFields(((BLangRecordLiteral) bLangExpression).getFields());
            CopyFileModel externalFileModel = new CopyFileModel();
            for (BLangRecordLiteral.BLangRecordKeyValueField annotation : annotationValues) {
                switch (annotation.getKey().toString()) {
                    case "sourceFile":
                        externalFileModel.setSource(getStringValue(annotation.getValue()));
                        break;
                    case "target":
                        externalFileModel.setTarget(getStringValue(annotation.getValue()));
                        break;
                    default:
                        break;
                }
            }
            if (isBlank(externalFileModel.getSource())) {
                throw new KnativePluginException("@kubernetes:Deployment copyFiles source cannot be empty.");
            }
            if (isBlank(externalFileModel.getTarget())) {
                throw new KnativePluginException("@kubernetes:Deployment copyFiles target cannot be empty.");
            }
            externalFiles.add(externalFileModel);
        }
        return externalFiles;
    }

    /**
     * Get a list of environment variables.
     *
     * @param envMap Map of Environment variables
     * @return List of env vars
     */
    public static List<EnvVar> populateEnvVar(Map<String, EnvVarValueModel> envMap) {
        List<EnvVar> envVars = new ArrayList<>();
        if (envMap == null) {
            return envVars;
        }
        envMap.forEach((k, v) -> {
            EnvVar envVar = null;
            if (v.getValue() != null) {
                envVar = new EnvVarBuilder().withName(k).withValue(v.getValue()).build();
            } else if (v.getValueFrom() instanceof EnvVarValueModel.FieldRef) {
                EnvVarValueModel.FieldRef fieldRefModel = (EnvVarValueModel.FieldRef) v.getValueFrom();

                ObjectFieldSelector fieldRef =
                        new ObjectFieldSelectorBuilder().withFieldPath(fieldRefModel.getFieldPath()).build();
                EnvVarSource envVarSource = new EnvVarSourceBuilder().withFieldRef(fieldRef).build();
                envVar = new EnvVarBuilder().withName(k).withValueFrom(envVarSource).build();
            } else if (v.getValueFrom() instanceof EnvVarValueModel.SecretKeyRef) {
                EnvVarValueModel.SecretKeyRef secretKeyRefModel = (EnvVarValueModel.SecretKeyRef) v.getValueFrom();

                SecretKeySelector secretRef = new SecretKeySelectorBuilder()
                        .withName(secretKeyRefModel.getName())
                        .withKey(secretKeyRefModel.getKey())
                        .build();
                EnvVarSource envVarSource = new EnvVarSourceBuilder().withSecretKeyRef(secretRef).build();
                envVar = new EnvVarBuilder().withName(k).withValueFrom(envVarSource).build();
            } else if (v.getValueFrom() instanceof EnvVarValueModel.ResourceFieldRef) {
                EnvVarValueModel.ResourceFieldRef resourceFieldRefModel =
                        (EnvVarValueModel.ResourceFieldRef) v.getValueFrom();

                ResourceFieldSelector resourceFieldRef = new ResourceFieldSelectorBuilder()
                        .withContainerName(resourceFieldRefModel.getContainerName())
                        .withResource(resourceFieldRefModel.getResource())
                        .build();
                EnvVarSource envVarSource = new EnvVarSourceBuilder().withResourceFieldRef(resourceFieldRef).build();
                envVar = new EnvVarBuilder().withName(k).withValueFrom(envVarSource).build();
            } else if (v.getValueFrom() instanceof EnvVarValueModel.ConfigMapKeyValue) {
                EnvVarValueModel.ConfigMapKeyValue configMapKeyValue =
                        (EnvVarValueModel.ConfigMapKeyValue) v.getValueFrom();

                ConfigMapKeySelector configMapKey = new ConfigMapKeySelectorBuilder()
                        .withKey(configMapKeyValue.getKey())
                        .withName(configMapKeyValue.getName())
                        .build();
                EnvVarSource envVarSource = new EnvVarSourceBuilder().withConfigMapKeyRef(configMapKey).build();
                envVar = new EnvVarBuilder().withName(k).withValueFrom(envVarSource).build();
            }

            if (envVar != null) {
                envVars.add(envVar);
            }
        });
        return envVars;
    }

    public static List<BLangRecordLiteral.BLangRecordKeyValueField> convertRecordFields(
            List<BLangRecordLiteral.RecordField> fields) {
        return fields.stream().map(f -> (BLangRecordLiteral.BLangRecordKeyValueField) f).collect(Collectors.toList());
    }
}
